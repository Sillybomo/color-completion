# @author bomo
# 用途：release 变体的 R8 保留规则。
# 边界：仅约束本模块；不假设系统侧类可被混淆或裁剪（系统类不在本 APK 内）。

# Modern Xposed 入口：META-INF/xposed/java_init.list 按全名 "com.bomo.colorcompletion.ModernModule"
# 反射加载，且父类 XposedModule 来自 compileOnly 依赖（运行时由框架提供），
# R8 会误判 onPackageLoaded/onPackageReady 无人调用而删除，必须整类保留。
-keep class com.bomo.colorcompletion.ModernModule { *; }

# 清单组件：Application / Activity / ContentProvider 由系统按名实例化。
# AGP 会自动保留，此处显式声明以免构建配置变化后失效。
-keep class com.bomo.colorcompletion.ModuleApplication { *; }
-keep class com.bomo.colorcompletion.MainActivity { *; }
-keep class com.bomo.colorcompletion.AppListProvider { *; }

# 传统 Xposed 入口（当前为死代码，框架只读 java_init.list）；
# 保留类名以防回退到 de.robv 传统接口时找不到入口。
-keep class com.bomo.colorcompletion.Module { *; }

# libxposed：service 为 implementation（在包内），API 由框架在目标进程注入。
# 模块侧对 getRemotePreferences 等 API 的调用点需保持签名可解析。
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }
-dontwarn io.github.libxposed.**

# 反射与序列化所需的元数据；Compose 的 Lambda 分组也依赖 InnerClasses/EnclosingMethod。
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# 保留行号便于线上崩溃栈对照 mapping 还原（不影响体积上限）。
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Compose 运行时的可选项在精简构建中缺失，属预期。
-dontwarn androidx.compose.**
-dontwarn kotlinx.coroutines.**
