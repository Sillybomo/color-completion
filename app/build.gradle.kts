import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// @author bomo
// 功能补全模块应用构建脚本：Modern Xposed API 102 模块 + COUI Compose 界面。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bomo.colorcompletion"
    compileSdk {
        // AGP 9：release(37) 不指定 minor 时找裸 'android-37' 目录；本机 SDK 包为 android-37.0，显式 minor 0。
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.bomo.colorcompletion"
        // coui-blur 要求 minSdk 33；build.py 时代为 26，此处随框架上调（设备实际 API 36+）。
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.2-compose"
    }

    signingConfigs {
        // 复用 build.py 时代的开发密钥（CN=Development, alias=dev），覆盖安装不断签。
        create("dev") {
            storeFile = file("../dev.keystore")
            storePassword = "android"
            keyAlias = "dev"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("dev")
        }
        release {
            signingConfig = signingConfigs.getByName("dev")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // material3 不再传递图标库；Search / KeyboardArrowDown 取自 icons-core。
    implementation("androidx.compose.material:material-icons-core")

    // COUI（suqi8/coui，Apache-2.0）内联源码模块。
    implementation(project(":coui-ui"))
    implementation(project(":coui-preference"))
    implementation(project(":coui-icons"))
    implementation(project(":coui-blur"))

    // Modern Xposed：API 由框架在目标进程提供，仅编译期可见；service 用于模块侧远程配置。
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}
