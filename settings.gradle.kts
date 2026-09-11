pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // 国内网络下 repo.maven.apache.org 会出现「TLS 握手被中断」，加国内镜像优先；mavenCentral 保留兜底
        maven("https://maven.aliyun.com/repository/public")
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
        maven("https://maven.aliyun.com/repository/public")
        mavenCentral()
    }
}

rootProject.name = "TLoggerDemo"
include(":app")
include(":tlogger-core")
include(":tlogger-android")
include(":tlogger-redact")
include(":tlogger-lint")
 