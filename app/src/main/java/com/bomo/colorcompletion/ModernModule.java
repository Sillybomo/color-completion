package com.bomo.colorcompletion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 通过 LSPosed 远程配置扩展媒体色彩名单与系统横屏布局名单。@author bomo */
public class ModernModule extends XposedModule {
    private String processName;
    private SharedPreferences remote;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private Object service;
    private List<String> baseline;
    private Class<?> managerClass;
    private Class<?> configClass;
    private Class<?> observerClass;
    private volatile boolean atlasInstalled;
    private volatile boolean settingsInstalled;

    /** 记录加载进程；参数由框架提供。@author bomo */
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) { processName = param.getProcessName(); }

    /** 最早回调：默认类加载器就绪、AppComponentFactory 之前，钩子在此安装以免错过首次注册。@author bomo */
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) { install(param, "onPackageLoaded"); }

    /** 备用回调：框架若先触发此时机仍可安装；重复调用由各目标标志位拦截。@author bomo */
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) { install(param, "onPackageReady"); }

    /** 按目标包分发安装；每个目标只安装一次，不兼容时记录错误并保留系统原有行为。@author bomo */
    private synchronized void install(XposedModuleInterface.PackageLoadedParam param, String stage) {
        String pkg = param.getPackageName();
        if ("com.oplus.atlas".equals(pkg) && "com.oplus.persist.multimedia".equals(processName) && !atlasInstalled) {
            installAtlas(param, stage);
        } else if ("com.android.settings".equals(pkg) && !settingsInstalled) {
            installSettings(param, stage);
        }
    }

    /** 在媒体进程安装色彩名单钩子；仅处理已核实的 V1 分支。@author bomo */
    private void installAtlas(XposedModuleInterface.PackageLoadedParam param, String stage) {
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> target = Class.forName("com.oplus.atlas.OplusVideoFeatureMode", false, loader);
            managerClass = Class.forName("com.oplus.app.OplusAppSwitchManager", false, loader);
            configClass = Class.forName("com.oplus.app.OplusAppSwitchConfig", false, loader);
            observerClass = Class.forName("com.oplus.app.OplusAppSwitchManager$OnAppSwitchObserver", false, loader);
            remote = getRemotePreferences("selection");
            Method register = target.getDeclaredMethod("registerAppSwitchObserver");
            register.setAccessible(true);
            hook(register).intercept(chain -> {
                Object instance = chain.getThisObject();
                try { prepare(instance); } catch (Throwable error) { logError("合并名单失败", error); }
                Object result = chain.proceed();
                try { watch(instance); } catch (Throwable error) { logError("监听配置失败", error); }
                return result;
            });
            atlasInstalled = true;
            log(Log.INFO, "功能补全", "媒体名单钩子已安装（" + stage + "）");
        } catch (Throwable error) { logError("媒体服务不兼容", error); }
    }

    /** 在设置进程安装横屏布局钩子；统一修改配置表读取结果，列表显示与弹窗选项共用此数据源。@author bomo */
    private void installSettings(XposedModuleInterface.PackageLoadedParam param, String stage) {
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> helper = Class.forName("com.oplus.settings.feature.display.compatible.FoldDatabaseHelper", false, loader);
            Method query = helper.getDeclaredMethod("getAllConfigInfo", android.content.ContentResolver.class);
            query.setAccessible(true);
            hook(query).intercept(chain -> {
                Object result = chain.proceed();
                try { applyLandscapeRoster(castMap(result)); }
                catch (Throwable error) { logError("横屏名单合并失败", error); }
                return result;
            });
            settingsInstalled = true;
            log(Log.INFO, "功能补全", "横屏布局钩子已安装（" + stage + "）");
        } catch (Throwable error) { logError("横屏布局不兼容", error); }
    }

    /** 合并远程横屏名单；名单内应用标记为双窗与全方向支持，与哔哩哔哩的原厂配置一致。@author bomo */
    private void applyLandscapeRoster(Map<String, Map<String, Object>> all) {
        if (all == null) return;
        try {
            if (remote == null) remote = getRemotePreferences("selection");
            String saved = remote.getString("landscape", "");
            if (saved == null || saved.isEmpty()) return;
            int merged = 0;
            for (String pkg : saved.split("\\n")) {
                if (!pkg.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) continue;
                Map<String, Object> config = all.get(pkg);
                if (config == null) { config = new java.util.HashMap<>(); all.put(pkg, config); }
                config.put("magicwindow", 1);
                config.put("fullorientation", 1);
                merged++;
            }
            if (merged > 0) log(Log.INFO, "功能补全", "横屏名单已合并：" + merged + " 项");
        } catch (Throwable error) { logError("横屏名单合并失败", error); }
    }

    /** 将返回值按配置表处理；非 Map 时返回空以避免类型异常。@author bomo */
    @SuppressWarnings("unchecked") private static Map<String, Map<String, Object>> castMap(Object value) {
        return value instanceof Map ? (Map<String, Map<String, Object>>) value : null;
    }

    /** 获取目标对象的私有字段；字段不存在或不可访问时抛出反射异常。@author bomo */
    private static Object read(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    /** 更新目标对象的私有字段；字段不存在或不可访问时抛出反射异常。@author bomo */
    private static void write(Object target, String name, Object value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    /** 合并原名单与远程设置；仅处理已核实的 V1 分支。@author bomo */
    private synchronized void prepare(Object target) throws ReflectiveOperationException {
        Object version = read(target, "mVppVersion");
        if ("V2".equals(version)) return;
        @SuppressWarnings("unchecked") List<String> current = (List<String>) read(target, "mConfigOsieSrAPP");
        if (current == null) throw new IllegalStateException("系统名单为空");
        if (service != target) { service = target; baseline = new ArrayList<>(current); }
        String saved = remote.getString("packages", "");
        List<String> merged = AppListMerge.merge(baseline, saved);
        write(target, "mConfigOsieSrAPP", merged);
        log(Log.INFO, "功能补全", "名单已合并：原有 " + baseline.size() + " 项，现有 " + merged.size() + " 项");
    }
    /** 注册远程配置变更监听；没有服务对象或版本不兼容时保持原行为。@author bomo */
    private synchronized void watch(Object target) throws ReflectiveOperationException {
        if (service != target || listener != null || "V2".equals(read(target, "mVppVersion"))) return;
        Handler handler = new Handler(Looper.getMainLooper());
        listener = (preferences, key) -> { if ("packages".equals(key)) handler.post(() -> refresh(target)); };
        remote.registerOnSharedPreferenceChangeListener(listener);
    }

    /** 构造前台应用监听配置；参数为包名列表，反射失败时抛出异常。@author bomo */
    private Object config(List<?> packages) throws ReflectiveOperationException {
        Object value = configClass.getDeclaredConstructor().newInstance();
        configClass.getMethod("addAppConfig", int.class, List.class).invoke(value, 2, packages);
        return value;
    }
    /** 更新监听并在失败时尽力恢复旧名单；参数为媒体服务实例。@author bomo */
    private void refresh(Object target) {
        List<?> oldList = null; Object callback = null; Object manager = null; boolean removed = false;
        try {
            Context context = (Context) read(target, "mContext");
            oldList = (List<?>) read(target, "mConfigOsieSrAPP");
            callback = read(target, "mOsieSrDynamicObserver");
            if (context == null || oldList == null || callback == null) throw new IllegalStateException("媒体服务状态不完整");
            prepare(target);
            Object next = config((List<?>) read(target, "mConfigOsieSrAPP"));
            Object previous = config(oldList);
            manager = managerClass.getMethod("getInstance").invoke(null);
            Method unregister = managerClass.getMethod("unregisterAppSwitchObserver", Context.class, observerClass);
            Method register = managerClass.getMethod("registerAppSwitchObserver", Context.class, observerClass, configClass);
            removed = Boolean.TRUE.equals(unregister.invoke(manager, context, callback));
            if (!removed) throw new IllegalStateException("注销原监听失败");
            if (!Boolean.TRUE.equals(register.invoke(manager, context, callback, next))) {
                write(target, "mConfigOsieSrAPP", oldList);
                removed = !Boolean.TRUE.equals(register.invoke(manager, context, callback, previous));
                throw new IllegalStateException("新监听注册失败");
            }
            removed = false;
            log(Log.INFO, "功能补全", "应用监听已更新");
            try { target.getClass().getMethod("updateOsieSrMode").invoke(target); }
            catch (Throwable error) { logError("更新增强状态失败", error); }
        } catch (Throwable error) {
            try {
                if (oldList != null) write(target, "mConfigOsieSrAPP", oldList);
                if (removed && manager != null && callback != null && oldList != null) {
                    Context context = (Context) read(target, "mContext");
                    Object result = managerClass.getMethod("registerAppSwitchObserver", Context.class, observerClass, configClass).invoke(manager, context, callback, config(oldList));
                    if (!Boolean.TRUE.equals(result)) log(Log.ERROR, "功能补全", "恢复旧监听失败");
                }
            } catch (Throwable restoreError) { logError("恢复旧监听失败", restoreError); }
            logError("刷新名单失败", error);
        }
    }
    /** 记录异常与上下文；参数为操作说明和异常。@author bomo */
    private void logError(String message, Throwable error) { log(Log.ERROR, "功能补全", message, error); }
}
