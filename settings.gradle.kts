pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 国内镜像优先，官方仓库保留兜底。统一用腾讯云：Gradle 发行包和 Maven 依赖它都有
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // 同上：国内镜像优先，官方仓库兜底
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "TLoggerDemo"
include(":app")
include(":tlogger-core")
include(":tlogger-android")
include(":tlogger-redact")
include(":tlogger-context")
include(":tlogger-ring")
include(":tlogger-lint")
 