package com.bomo.colorcompletion;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** OPD2515 媒体服务名单扩展；不更改厂商增强算法及温控。@author bomo */
public class Module implements IXposedHookLoadPackage {
    private static final Uri LIST_URI = Uri.parse("content://com.bomo.colorcompletion.apps/list");
    private static final String TAG = "功能补全: ";
    private static final WeakHashMap<Object, State> states = new WeakHashMap<>();
    private static Class<?> managerClass;
    private static Class<?> configClass;

    /** 保存设备原始名单及设置观察者，避免配置更新时覆盖厂商名单。@author bomo */
    private static class State {
        final List<String> base;
        ContentObserver observer;
        boolean initialMergeSucceeded;
        boolean ready;
        /** 参数为原始名单，不允许为空。@author bomo */
        State(List<String> base) { this.base = base; }
    }

    /** 仅挂接媒体服务进程。参数为加载包信息；不兼容时记录原因，不影响系统正常执行。 @author bomo */
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
        if (!"com.oplus.atlas".equals(p.packageName) || !"com.oplus.persist.multimedia".equals(p.processName)) return;
        try {
            Class<?> target = XposedHelpers.findClass("com.oplus.atlas.OplusVideoFeatureMode", p.classLoader);
            managerClass = XposedHelpers.findClass("com.oplus.app.OplusAppSwitchManager", p.classLoader);
            configClass = XposedHelpers.findClass("com.oplus.app.OplusAppSwitchConfig", p.classLoader);
            XposedHelpers.findAndHookMethod(target, "getVideoFeatureConfig", new XC_MethodHook() {
                /** 在系统读取能力后记录色彩与超清支持，不修改系统判断。@author bomo */
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        XposedBridge.log(TAG + "媒体能力：色彩=" + XposedHelpers.getObjectField(param.thisObject, "mIsOsieFeatureOn") +
                                "，超清=" + XposedHelpers.getObjectField(param.thisObject, "mIsSrFeatureOn"));
                    } catch (Throwable error) { XposedBridge.log(TAG + "读取媒体能力失败: " + error); }
                }
            });
            XposedHelpers.findAndHookMethod(target, "registerAppSwitchObserver", new XC_MethodHook() {
                /** 参数为钩子调用信息；失败不阻断原方法。@author bomo */
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    try { mergeList(param.thisObject); }
                    catch (Throwable error) { XposedBridge.log(TAG + "合并名单失败: " + error); }
                }
                /** 参数为钩子调用信息；仅在原方法成功时监听配置。@author bomo */
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.getThrowable() != null) return;
                    try { observeChanges(param.thisObject); }
                    catch (Throwable error) { XposedBridge.log(TAG + "监听名单失败: " + error); }
                }
            });
            XposedBridge.log(TAG + "媒体名单钩子已安装");
        } catch (Throwable error) { XposedBridge.log(TAG + "媒体服务不兼容: " + error); }
    }

    /** 合并厂商名单与用户名单。参数为媒体服务对象；仅处理已核实的 V1 分支。 @author bomo */
    private static void mergeList(Object service) {
        String version = (String) XposedHelpers.getObjectField(service, "mVppVersion");
        if ("V2".equals(version)) { XposedBridge.log(TAG + "V2 参数未验证，跳过自选名单"); return; }
        Context context = (Context) XposedHelpers.getObjectField(service, "mContext");
        @SuppressWarnings("unchecked") List<String> current = (List<String>) XposedHelpers.getObjectField(service, "mConfigOsieSrAPP");
        if (context == null || current == null) return;
        synchronized (states) {
            State state = states.get(service);
            if (state == null) { state = new State(new ArrayList<>(current)); states.put(service, state); }
            String saved = readList(context);
            List<String> combined = AppListMerge.merge(state.base, saved);
            // 替换引用而非修改原列表，避免系统回调并发遍历时发生异常。
            XposedHelpers.setObjectField(service, "mConfigOsieSrAPP", combined);
            state.initialMergeSucceeded = true;
            XposedBridge.log(TAG + "名单已合并，原有 " + state.base.size() + " 项，合并后 " + combined.size() + " 项");
        }
    }

    /** 读取模块私有名单；提供方不可用时抛出异常，保留系统原有名单。@author bomo */
    private static String readList(Context context) {
        try (Cursor cursor = context.getContentResolver().query(LIST_URI, null, null, null, null)) {
            if (cursor == null || !cursor.moveToFirst()) throw new IllegalStateException("名单提供方不可用");
            return cursor.getString(cursor.getColumnIndexOrThrow("packages"));
        }
    }

    /** 构建系统前台应用监听配置；参数为包名列表，返回厂商配置对象，反射失败抛出异常。 @author bomo */
    private static Object makeConfig(Object packages) throws ReflectiveOperationException {
        Object config = configClass.getDeclaredConstructor().newInstance();
        XposedHelpers.callMethod(config, "addAppConfig", 2, packages);
        return config;
    }

    /** 监听自选名单变更并重新注册前台应用监听；异常仅记录，不替代系统错误处理。 @author bomo */
    private static void observeChanges(final Object service) {
        final Context context = (Context) XposedHelpers.getObjectField(service, "mContext");
        if (context == null) return;
        synchronized (states) {
            State state = states.get(service);
            if (state == null || state.observer != null) return;
            ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                /** 参数表示变更来源；刷新失败尝试恢复原监听。@author bomo */
                @Override public void onChange(boolean selfChange) {
                    Object oldList = null;
                    Object callback = null;
                    Object manager = null;
                    boolean removed = false;
                    try {
                        oldList = XposedHelpers.getObjectField(service, "mConfigOsieSrAPP");
                        callback = XposedHelpers.getObjectField(service, "mOsieSrDynamicObserver");
                        if (oldList == null || callback == null) throw new IllegalStateException("系统名单或监听为空");
                        mergeList(service);
                        Object newList = XposedHelpers.getObjectField(service, "mConfigOsieSrAPP");
                        Object newConfig = makeConfig(newList);
                        Object oldConfig = makeConfig(oldList);
                        manager = XposedHelpers.callStaticMethod(managerClass, "getInstance");
                        removed = Boolean.TRUE.equals(XposedHelpers.callMethod(manager, "unregisterAppSwitchObserver", context, callback));
                        if (!removed) throw new IllegalStateException("注销旧监听失败");
                        if (!Boolean.TRUE.equals(XposedHelpers.callMethod(manager, "registerAppSwitchObserver", context, callback, newConfig))) {
                            XposedHelpers.setObjectField(service, "mConfigOsieSrAPP", oldList);
                            boolean restored = Boolean.TRUE.equals(XposedHelpers.callMethod(manager, "registerAppSwitchObserver", context, callback, oldConfig));
                            removed = !restored;
                            throw new IllegalStateException("注册新监听失败，旧监听恢复=" + restored);
                        }
                        removed = false;
                        synchronized (states) {
                            State state = states.get(service);
                            if (state != null) state.ready = true;
                        }
                        XposedBridge.log(TAG + "应用监听已更新");
                        try { XposedHelpers.callMethod(service, "updateOsieSrMode"); }
                        catch (Throwable error) { XposedBridge.log(TAG + "更新增强状态失败: " + error); }
                    } catch (Throwable error) {
                        if (oldList != null) {
                            try { XposedHelpers.setObjectField(service, "mConfigOsieSrAPP", oldList); }
                            catch (Throwable restoreError) { XposedBridge.log(TAG + "恢复原名单失败: " + restoreError); }
                        }
                        if (removed && manager != null && callback != null && oldList != null) {
                            try { XposedHelpers.callMethod(manager, "registerAppSwitchObserver", context, callback, makeConfig(oldList)); }
                            catch (Throwable restoreError) { XposedBridge.log(TAG + "恢复旧监听失败: " + restoreError); }
                        }
                        XposedBridge.log(TAG + "刷新名单失败: " + error);
                    }
                }
            };
            context.getContentResolver().registerContentObserver(LIST_URI, false, observer);
            state.observer = observer;
            state.ready = state.initialMergeSucceeded;
            if (!state.ready) {
                // 系统重建进程时提供方可能尚未就绪；限次重试，仍不可用则保留原名单。
                Handler handler = new Handler(Looper.getMainLooper());
                for (int attempt = 1; attempt <= 3; attempt++) {
                    handler.postDelayed(() -> {
                        synchronized (states) { if (state.ready) return; }
                        observer.onChange(false);
                    }, attempt * 3000L);
                }
            }
        }
    }
}
