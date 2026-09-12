import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 上下文模块：给一次业务操作带上"随身便签"。
 *
 * 解决的是这件事：一次下单从界面打到库存，日志散在四个模块里，事后你只能按时间猜哪几条是一伙的。
 * 有了它，**入口标记一次**，中间所有代码一行都不用改，日志里自动带上链路号。
 *
 * 靠协程自带的上下文机制传递，所以**跨协程、跨线程都不会丢**。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    explicitApi()

    android {
        namespace = "com.tlogger.context"
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
                // 跨协程传递要用协程自带的机制（ThreadContextElement）
                // 用 api：公开接口（LogContextElement）本身就实现了协程的类型，消费方要能看到它
                api(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
