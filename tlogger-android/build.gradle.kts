import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 安卓输出模块：**只有安卓能用**的东西。
 *
 * 里面是三样：
 * 1. 输出到系统日志的出口（含长日志分段）；
 * 2. 当前进程名的读取，用来区分多进程；
 * 3. 安卓上的一行安装。
 *
 * 它只依赖核心模块，反过来核心模块**不认识**它。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    explicitApi()

    android {
        namespace = "com.tlogger.android"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // 分段逻辑的测试跑在这里（不需要真机）
        withHostTestBuilder {
        }
    }

    sourceSets {
        androidMain {
            dependencies {
                // 用 api 而不是 implementation：别人依赖本模块时也应该能看到核心的接口
                api(project(":tlogger-core"))
            }
        }
        // 宿主测试的源集由 withHostTestBuilder 建出来，DSL 里没有现成的名字，用 getByName 拿
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
