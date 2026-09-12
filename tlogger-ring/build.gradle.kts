import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * 环形缓冲模块：在内存里留住**最近 N 条**日志。
 *
 * 它回答的是"出事之前发生了什么"——用户说"刚才闪了一下"，日志窗口里已经翻不到了，
 * 但缓冲区里还留着最后那几百条。
 *
 * **注意它的边界**：只在内存里，**进程被杀就一起没了**。要撑过被杀得等第二版的落盘。
 * 现在它的定位是"调试期够用"，这一点写在文档里，不让人误会。
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

kotlin {
    explicitApi()

    compilerOptions {
        // 平台锁要用 expect/actual 类，这个特性目前是 Beta，官方建议加这个开关
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "com.tlogger.ring"
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
