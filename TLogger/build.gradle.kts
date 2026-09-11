import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    // 强迫所有对外暴露的东西显式声明可见性（约束 17：对外接口兼容性门槛）。
    // 从第一行代码开始就开着，比事后补便宜得多。
    explicitApi()

    // AGP 9.3 起这个块从 androidLibrary 改名为 android（旧名仍可用但已弃用，会警告）
    android {
        namespace = "com.tlogger"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            // 与 TRouter 各模块保持一致（app 与 trouter-core 都是 11）。
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // 不需要真机的单元测试跑在这里（对应 kmp-logcat 的 androidHostTest）。
        withHostTestBuilder {
        }
    }

    // iOS 目标当前未声明：本机只装了 Command Line Tools，没有 Xcode，编不了苹果产物。
    // 装上 Xcode 之后，在这里补上三行即可（详见本模块 README）：
    //   iosX64()
    //   iosArm64()
    //   iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                // 第一版刻意不引入任何依赖：核心接口层应当只依赖 Kotlin 标准库。
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
