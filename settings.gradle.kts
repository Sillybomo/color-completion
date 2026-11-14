pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "color-completion"
include(":app")

// COUI（suqi8/coui，Apache-2.0）内联源码模块，取自上游 ONextBox 工程的 third_party/coui。
listOf("core", "shader", "squircle", "ui", "preference", "icons", "blur")
    .forEach { component ->
        val module = "coui-$component"
        include(":$module")
        project(":$module").projectDir = file("third_party/coui/$module")
    }
