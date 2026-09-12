plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.consumer"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.example.consumer"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release { optimization { enable = false } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // 关键点：这里全是发布出来的坐标，一行 project(":...") 都没有。
    // 这是一个跟 TLogger 源码毫无关系的独立工程。
    implementation("io.github.tyeeee:tlogger-core:0.1.0")
    implementation("io.github.tyeeee:tlogger-android:0.1.0")
    implementation("io.github.tyeeee:tlogger-redact:0.1.0")
    implementation("io.github.tyeeee:tlogger-context:0.1.0")
    implementation("io.github.tyeeee:tlogger-ring:0.1.0")

    // 顺手验证：编译期隐私检查能不能从仓库坐标吃进来
    lintChecks("io.github.tyeeee:tlogger-lint:0.1.0")
}
