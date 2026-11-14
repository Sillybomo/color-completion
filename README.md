# 功能补全（Color Completion）

作者：bomo

LSPosed 模块，面向 OPPO Pad（ColorOS 17 / Android 17），用名单制补全系统缺失的两处能力：**视频色彩增强的应用白名单**与**「应用横屏显示」的四档布局选项**。

![Modern Xposed](https://img.shields.io/badge/Modern_Xposed-API_102-0066FF?style=flat-square)
![ColorOS](https://img.shields.io/badge/ColorOS-17-0066FF?style=flat-square)
![minSdk](https://img.shields.io/badge/minSdk-33-4B5563?style=flat-square)
![Release](https://img.shields.io/badge/Release-2.31_MiB-16A34A?style=flat-square)
[![License](https://img.shields.io/badge/License-MIT-16A34A?style=flat-square)](LICENSE)

> 系统级修改存在稳定性风险。本模块只改运行时数据，不触碰系统分区、不覆盖系统文件；但仍建议先在可回滚的环境验证。

## 1. 功能

### 1.1 视频色彩增强 · 应用名单

系统只对厂商白名单内的应用开放色彩增强，名单外应用在设置里根本看不到开关。本模块接管这份名单：

- 在模块界面内自由勾选应用，写入后即时生效，无需重启设备。
- 名单通过 LSPosed 框架远程配置同步到目标进程，媒体服务会注销并重新注册应用监听。
- 增删应用后名单实时刷新，不依赖重启媒体进程。

### 1.2 应用横屏布局 · 四选项补全

系统「设置 → 显示与亮度 → 应用横屏显示」按厂商数据库下发布局选项，多数应用只有 1～2 项，而哔哩哔哩这类预置应用有四项。本模块让名单内应用统一获得四项：

| 选项 | 对应字段 |
|---|---|
| 自适应布局 | `autolayout` |
| 双窗布局 | `magicwindow` |
| 居中布局 | `compactwindow` |
| 原始布局 | 恒有，无需字段 |

### 1.3 界面

- COUI 组件库 + Jetpack Compose 重写，浅灰底 + 白色拼接卡片，三个可折叠分组。
- 应用列表支持按**名称或包名子串**搜索，图标异步加载并缓存。
- 名单与色彩增强互不干扰，各自独立。

## 2. 原理

### 2.1 色彩增强

目标进程 `com.oplus.persist.multimedia`，钩 `com.oplus.atlas.OplusVideoFeatureMode`：

- `getVideoFeatureConfig` —— 读取厂商配置，返回前把名单内应用合并进去；
- `registerAppSwitchObserver` —— 应用切换监听，名单变化时重新注册以触发刷新。

名单来源为框架远程配置（`XposedService.getRemotePreferences("selection")`），键 `packages`，换行分隔包名。

### 2.2 横屏布局

目标进程 `com.android.settings`，钩 `com.oplus.settings.feature.display.applandscapemode.FoldDatabaseHelper.getAllConfigInfo(Context)`。

该方法被 `AppLandscapeManager.queryFullConfig` 与 `getFunctionMap` 共用，是列表与弹窗的**公共数据源**，所以在此处改一次即可同时影响「列表显示的布局文字」和「点开后弹窗里的选项」——只钩 `queryFullConfig` 会出现"列表显示四项、弹窗只有两项"的错位。

`AppLandscapeManager.handleFunctionStatus` 对 `autolayout` / `magicwindow` / `compactwindow` / `fullorientation` 四个字段**各自独立判定：字段存在且值不为 2 即产出一个对应选项**。因此名单内应用统一写入 `1`。

## 3. 兼容性

- 实测设备：OPPO Pad Mini（OPD2515），`OPD2515_17.0.0.104(CN01)`，Android 17 / SDK 37。
- 运行环境：KernelSU + LSPosed 2.2.0，Modern Xposed API 102。
- 模块作用域：`com.oplus.atlas`（色彩增强）、`com.android.settings`（横屏布局）。
- 其他 ColorOS 机型未验证；换 ROM 或大版本升级后必须重新确认钩子类名与方法签名。

## 4. 构建

### 4.1 环境

| 项 | 版本 |
|---|---|
| JDK | 21 |
| Android SDK Platform | `android-37.0`（compileSdk 37，minor 0） |
| Build Tools | 37.0.0 |
| Gradle / AGP / Kotlin | 9.3.1 / 9.1.0 / 2.4.10 |
| Compose BOM | 2026.08.00 |

### 4.2 命令

```bash
# 装机包（R8 裁剪，约 2.3 MiB）
JAVA_HOME=/path/to/jdk21 ./gradlew :app:assembleRelease
# 调试包（不裁剪，约 37.5 MiB，仅用于开发）
JAVA_HOME=/path/to/jdk21 ./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/{release,debug}/`。

### 4.3 签名

`app/build.gradle.kts` 使用工程根目录的 `dev.keystore`（`CN=Development`，alias `dev`）。口令从 `local.properties` 读取，未配置时回落到 Android 开发密钥默认口令：

```properties
dev.storePassword=你的口令
dev.keyAlias=dev
dev.keyPassword=你的口令
```

`local.properties` 与 `dev.keystore` 均已在 `.gitignore` 中，不入库。

### 4.4 关键构建约束

`compileSdk` 必须显式指定 minor 版本，否则 AGP 会去找裸 `android-37` 目录而本机 SDK 包名是 `android-37.0`：

```kotlin
compileSdk {
    version = release(37) { minorApiLevel = 0 }
}
```

复制作业系统里 `platforms/android-37` 目录绕过无效——AGP 会读包内 `package.xml` 校验 location。

R8 的保留规则见 `app/proguard-rules.pro`。**入口类 `ModernModule` 必须整类保留**：它由 `META-INF/xposed/java_init.list` 按全名反射加载，父类 `XposedModule` 来自 `compileOnly` 依赖（运行时才由框架注入），不 keep 会被 R8 判定为"无人调用"而连类带方法一起删除，模块静默失效。

## 5. 安装

```bash
adb install -r app-release.apk
```

1. 在 LSPosed 管理器中启用「功能补全」。
2. 作用域勾选 `com.oplus.atlas` 与 `com.android.settings`（模块已通过 `scope.list` 声明静态作用域）。
3. 重启设备，或重启上述两个目标进程，使钩子生效。
4. 横屏布局改动后需**重新打开系统设置页**才会重新读取配置。

## 6. 使用

- **色彩增强**：打开模块 → 展开「视频色彩增强」→ 搜索并勾选应用。勾选即时写入，媒体进程会自动刷新监听。
- **横屏布局**：打开模块 → 展开「应用横屏布局」→ 勾选应用 → 进入系统「设置 → 显示与亮度 → 应用横屏显示」，点开该应用即可看到四个选项。

## 7. 验证

查看 LSPosed 模块日志：

```bash
adb shell "su -c 'grep 功能补全 /data/adb/lspd/log/modules_*.log | tail -20'"
```

正常应出现：

```text
媒体名单钩子已安装（onPackageLoaded）
名单已合并：原有 18 项，现有 20 项
横屏布局钩子已安装（onPackageLoaded）
横屏名单已合并：2 项
```

验证钩子通道是否生效，可先钩 `com.oplus.atlas.OplusAtlasService.onCreate` 这类必然执行的方法作探针。

**验证方法教训**：`kill` 强杀 `com.oplus.persist.multimedia` 后的重启进程不会重跑 `registerAppSwitchObserver` 初始化，日志零调用——该方式不能用于验证，必须开机或让系统自然启动进程。

## 8. 已知边界

- **画质超清增强在本平台不可实现**。系统 `IComponentStore/default` 未注册任何 filter/vpp 组件，超清所依附的滤镜在本机不存在；从同平台机型移植库、声明 `sr_support` 特性、写入属性、补齐白名单四项均实验过，仍无法触发，实验已回滚。界面如实标注为"平台不支持"，不提供虚假开关。
- **横屏布局的运行时效果未验证**：四选项可以正常下发与展示，但"选定双窗/居中后，应用是否真按该布局呈现"尚未确认。Launcher 与 SystemUI 直查提供方、不经过设置进程，若布局不生效，需在 `com.oplus.exsystemservice` 进程内钩提供方查询。
- **色彩增强的实际画面效果未做人眼对比验证**。已确认的是名单写入、远程配置同步、媒体进程注销/重注册链路；画面差异需自行对比同一段视频开关模块前后的表现。
- `refresh()` 的失败回滚分支（注销失败或注册失败）未构造验证。
- 全屏/分屏、温控、省电策略下的行为未验证。
- 模块只改运行时数据，不修改系统分区、不写入系统数据库；卸载或禁用后相关表现回到系统默认。

## 9. 目录结构

```text
color-completion/
├── app/
│   ├── src/main/java/com/bomo/colorcompletion/
│   │   ├── ModernModule.java        # Modern Xposed 入口，钩子实现
│   │   ├── ModuleApplication.java   # 应用入口，名单同步到远程配置
│   │   ├── MainActivity.kt          # Compose 界面
│   │   ├── ui/                      # 主题与名单数据层
│   │   └── AppListMerge.java        # 名单合并逻辑
│   ├── src/main/resources/META-INF/xposed/
│   │   ├── module.prop              # 模块元信息
│   │   ├── java_init.list           # 入口类全名
│   │   └── scope.list               # 默认作用域
│   ├── proguard-rules.pro           # R8 保留规则
│   └── build.gradle.kts
├── third_party/coui/                # 内联的 COUI 组件库（Apache-2.0）
├── modern/  src/  res/  libs/       # 早期 build.py 构建链遗留资产，不参与 Gradle 构建
└── build.py                         # 遗留构建脚本，现行构建请用 Gradle
```

## 10. 第三方组件

| 组件 | 来源 | 许可 |
|---|---|---|
| COUI（`third_party/coui/`，7 个模块） | [suqi8/coui](https://github.com/suqi8/coui) | Apache-2.0 |
| Jetpack Compose、AndroidX | Google | Apache-2.0 |
| libxposed API / Service | [libxposed/api](https://github.com/libxposed/api) | Apache-2.0 |

COUI 以源码形式内联，其 `LICENSE`、`UPSTREAM.txt`、`sources.json` 保留在 `third_party/coui/` 目录内。

界面实现参考了 [ONextBox](https://github.com/MiToverG422/ONextBox)（GPL-3.0）的视觉语言与拼接卡片手法，**未复制其源码**，仅使用其内联的 Apache-2.0 组件库并自行实现界面。

## 11. 许可

[MIT License](LICENSE) © 2026 bomo

可自由使用、修改、分发与商用，保留版权声明与许可声明即可。

内联的第三方组件各自遵循其原有许可（见上一节）：COUI 为 Apache-2.0，随源码保留其 `LICENSE` 与来源说明。
