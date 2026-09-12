// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}
// 发布配置只写这一处：坐标、版本、POM 信息，模块不用各抄一遍
subprojects {
    plugins.withId("maven-publish") {
        group = providers.gradleProperty("GROUP").get()
        version = providers.gradleProperty("VERSION_NAME").get()

        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    name.set(project.name)
                    // 中文直接写在代码里：gradle.properties 是按 ISO-8859-1 解码的，中文放那里会乱码
                    description.set(
                        "给多模块安卓架构用的日志库：按来源过滤、隐私自动打码、业务链路串联",
                    )
                    url.set(providers.gradleProperty("POM_URL"))
                    licenses {
                        license {
                            name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                            url.set(providers.gradleProperty("POM_LICENSE_URL"))
                        }
                    }
                    developers {
                        developer {
                            id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                            name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                            url.set(providers.gradleProperty("POM_DEVELOPER_URL"))
                        }
                    }
                    scm {
                        url.set(providers.gradleProperty("POM_SCM_URL"))
                        connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
                    }
                }
            }
        }

        // 中央仓库强制要求每个包都有签名。密钥只从**本机**的配置里读
        // （~/.gradle/gradle.properties，不进仓库），没配就整段跳过——
        // 平时构建、发本机仓库完全不受影响。
        val signingKey = providers.gradleProperty("SIGNING_KEY").orNull
        val signingPassword = providers.gradleProperty("SIGNING_PASSWORD").orNull
        if (signingKey != null && signingPassword != null) {
            apply(plugin = "signing")
            configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }
    }
}
