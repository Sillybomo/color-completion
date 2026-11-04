package de.robv.android.xposed;

/* 编译桩：运行时由 LSPosed 框架提供真实类，勿打进 dex */
public final class XposedBridge {
    public static Object invokeOriginalMethod(java.lang.reflect.Member method, Object thisObject,
            Object... args) throws Throwable { return null; }
    public static void log(String text) {}
    public static void log(Throwable t) {}
}
