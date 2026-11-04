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

/** 通过 LSPosed 远程配置扩展媒体服务的应用名单。@author bomo */
public class ModernModule extends XposedModule {
    private String processName;
    private SharedPreferences remote;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    private Object service;
    private List<String> baseline;
    private Class<?> managerClass;
    private Class<?> configClass;
    private Class<?> observerClass;
    private volatile boolean installed;

    /** 记录加载进程；参数由框架提供。@author bomo */
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) { processName = param.getProcessName(); }

    /** 最早回调：默认类加载器就绪、AppComponentFactory 之前，钩子在此安装以免错过首次注册。@author bomo */
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) { install(param, "onPackageLoaded"); }

    /** 备用回调：框架若先触发此时机仍可安装；重复调用由标志位拦截。@author bomo */
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam param) { install(param, "onPackageReady"); }

    /** 在目标媒体进程安装钩子；仅首次生效，不兼容时记录错误并保留系统原有行为。@author bomo */
    private synchronized void install(XposedModuleInterface.PackageLoadedParam param, String stage) {
        if (installed) return;
        if (!"com.oplus.atlas".equals(param.getPackageName()) || !"com.oplus.persist.multimedia".equals(processName)) return;
        try {
            ClassLoader loader = param.getDefaultClassLoader();
            Class<?> target = Class.forName("com.oplus.atlas.OplusVideoFeatureMode", false, loader);
            managerClass = Class.forName("com.oplus.app.OplusAppSwitchManager", false, loader);
            configClass = Class.forName("com.oplus.app.OplusAppSwitchConfig", false, loader);
            observerClass = Class.forName("com.oplus.app.OplusAppSwitchManager$OnAppSwitchObserver", false, loader);
            remote = getRemotePreferences("selection");
            // 诊断：确认目标类与进程默认加载器是否同源，排除钩子挂在另一份类上的可能。@author bomo
            log(Log.INFO, "功能补全", "诊断：目标类加载器同源=" + (target.getClassLoader() == loader) + "，安装时机=" + stage);
            // 诊断：输出关键方法签名，确认反射目标与系统实际调用一致。@author bomo
            try {
                StringBuilder table = new StringBuilder();
                for (Method m : target.getDeclaredMethods()) {
                    String name = m.getName();
                    if (name.contains("AppSwitch") || name.contains("Osie") || name.contains("VideoFeature")) {
                        table.append(name).append('(').append(m.getParameterCount()).append(") ");
                    }
                }
                log(Log.INFO, "功能补全", "诊断：方法表 " + table);
            } catch (Throwable error) { logError("诊断方法表失败", error); }
            // 诊断：getVideoFeatureConfig 在旧版中启动后必被调用，用于判断钩子通道本身是否生效。@author bomo
            try {
                Method feature = target.getDeclaredMethod("getVideoFeatureConfig");
                feature.setAccessible(true);
                hook(feature).intercept(chain -> {
                    Object value = chain.proceed();
                    log(Log.INFO, "功能补全", "诊断：getVideoFeatureConfig 已触发");
                    return value;
                });
            } catch (Throwable error) { logError("诊断钩子安装失败", error); }
            // 诊断：服务 onCreate 必然执行，用于判定钩子通道在该进程是否生效。@author bomo
            try {
                Class<?> service = Class.forName("com.oplus.atlas.OplusAtlasService", false, loader);
                Method onCreate = service.getDeclaredMethod("onCreate");
                onCreate.setAccessible(true);
                hook(onCreate).intercept(chain -> {
                    log(Log.INFO, "功能补全", "诊断：OplusAtlasService.onCreate 已触发");
                    return chain.proceed();
                });
            } catch (Throwable error) { logError("诊断服务钩子失败", error); }
            Method register = target.getDeclaredMethod("registerAppSwitchObserver");
            register.setAccessible(true);
            hook(register).intercept(chain -> {
                Object instance = chain.getThisObject();
                log(Log.INFO, "功能补全", "诊断：registerAppSwitchObserver 已拦截");
                try { prepare(instance); } catch (Throwable error) { logError("合并名单失败", error); }
                Object result = chain.proceed();
                try { watch(instance); } catch (Throwable error) { logError("监听配置失败", error); }
                return result;
            });
            log(Log.INFO, "功能补全", "媒体名单钩子已安装（" + stage + "）");
            installed = true;
        } catch (Throwable error) { logError("媒体服务不兼容", error); }
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
        log(Log.INFO, "功能补全", "诊断：mVppVersion=" + version);
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
