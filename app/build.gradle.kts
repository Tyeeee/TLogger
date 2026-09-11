plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.tlogger.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.tlogger.sample"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 示例应用依赖三个模块：核心（接口/逻辑）+ 安卓输出 + 打码
    implementation(project(":tlogger-core"))
    implementation(project(":tlogger-android"))
    implementation(project(":tlogger-redact"))
}
