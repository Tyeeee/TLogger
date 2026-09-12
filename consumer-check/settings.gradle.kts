pluginManagement {
    repositories {
        google()
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 库现在只发在本机；等发出去了，这一行删掉就能从中央仓库拉
        mavenLocal()
        google()
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        mavenCentral()
    }
}

rootProject.name = "TLoggerConsumer"
include(":app")
