import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 核心模块：**跟具体平台无关**的日志接口与逻辑。
 *
 * 这里不许出现任何安卓专有的东西（`android.util.Log`、`Context` 等），
 * 因为这一层将来要原样给苹果端用。出口（往哪写）由别的模块提供，
 * 核心只定义"日志长什么样"和"往哪送"。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    // 对外暴露的东西必须显式声明可见性，避免不小心把内部实现暴露出去
    explicitApi()

    android {
        namespace = "com.tlogger.core"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            // 与 TRouter 各模块保持一致
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // 不需要真机的单元测试跑在这里
        withHostTestBuilder {
        }
    }

    // 苹果端目标等装了 Xcode 再加（补 iosX64() / iosArm64() / iosSimulatorArm64()）

    sourceSets {
        commonMain {
            dependencies {
                // 核心层刻意不引入任何依赖，只用 Kotlin 标准库
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
