import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 打码模块：把敏感信息在写出去之前换掉。
 *
 * ## 为什么它必须能独立使用
 *
 * 这是整个项目里**唯一一个别人不用换日志库就能用上**的能力——接到他现在用的日志库上就行。
 * 所以它只依赖核心模块（拿到"出口"的契约），**不许依赖任何具体的输出实现**。
 *
 * 用法是"套在出口外面"：
 * ```
 * LoggingConfig.builder()
 *     .sink(RedactingSink(AndroidLogSink(), PiiRedactor()))
 *     .build()
 * ```
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    explicitApi()

    android {
        namespace = "com.tlogger.redact"
        compileSdk = 37
        minSdk = 24

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        withHostTestBuilder {
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":tlogger-core"))
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}
