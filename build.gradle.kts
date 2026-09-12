// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
}

// 要传去中央仓库的东西，先在这里按仓库要求的目录结构摆好，再打成一个压缩包
val centralStaging = layout.buildDirectory.dir("central-staging")

/**
 * 清掉上次留下的暂存目录。
 *
 * 发布任务会依赖它——顺序是"先清空、再发布、最后打包"。
 * 不清的话，改了版本号以后新旧的包会一起被打进上传的压缩包里，中央仓库会当成两个版本收。
 */
val cleanCentralStaging = tasks.register<Delete>("cleanCentralStaging") {
    group = "publishing"
    description = "清掉上次打包留下的暂存目录"
    delete(centralStaging)
}

/**
 * 把六个库模块打成**一个**压缩包，拿去中央仓库网页上传就行。
 *
 * 中央仓库不收"直接推"，只收一个压缩包：包里必须按仓库的目录规矩摆好
 * （`io/github/tyeeee/模块名/版本号/` 下面放各种文件 + 签名 + 校验和）。
 * 这些 Gradle 会自己摆好，你不用手工拼目录。
 *
 * 压缩包在 `build/central-bundle/tlogger-central-bundle.zip`，限制 1GB 以内。
 *
 * 写成 `run { ... }` 是有原因的：`doFirst` 里只允许捕获普通变量（目录、文字），
 * 一旦碰到构建脚本上的东西，配置缓存就没法用了——Gradle 9 默认开着它。
 */
val bundleForCentral: TaskProvider<Zip> = run {
    val stagingDir = centralStaging.get().asFile
    val unsignedHint =
        "暂存目录里一个签名文件都没有，这个包传上去会被中央仓库拒收。\n" +
            "先在 ~/.gradle/gradle.properties 里配上签名（SIGNING_KEY_FILE 或 SIGNING_KEY，加上 SIGNING_PASSWORD）。" +
            "步骤见 README 的「发到中央仓库：一步步来」。"

    tasks.register<Zip>("bundleForCentral") {
        group = "publishing"
        description = "打成可以直接上传到中央仓库的压缩包"
        from(stagingDir) {
            // maven-metadata.xml 是给仓库管理器列版本用的，正式版本上传不需要它，
            // 官方给的示例包里也没有。排除掉，少一样可能被判为多余的东西。
            exclude("**/maven-metadata.xml*")
        }
        archiveFileName.set("tlogger-central-bundle.zip")
        destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))
        doFirst {
            // 没签名就白忙一趟：中央仓库见到没签名的包直接拒收。
            // 这里直接看暂存目录里签名的东西在不在——比看配置更实在，万一批量跳过签名也能发现。
            val signed = stagingDir.walkTopDown().count { it.isFile && it.name.endsWith(".asc") }
            require(signed > 0) { unsignedHint }
        }
    }
}

// 签名用的密钥。它是全局配置，六个模块共用同一份，所以在这里读一次就够了。
// 只从**本机**的配置里读（~/.gradle/gradle.properties，不进仓库）。
//
// 两种给法，挑一种：
//   SIGNING_KEY_FILE=/Users/你/.gnupg/tlogger-secret.asc   ← 推荐，路径写全，省事
//   SIGNING_KEY=<整份密钥压成一行>                          ← 要塞进属性文件，得把换行写成 \n
val signingPassword = providers.gradleProperty("SIGNING_PASSWORD").orNull
val signingKey: String? = providers.gradleProperty("SIGNING_KEY").orNull
    ?: providers.gradleProperty("SIGNING_KEY_FILE").orNull?.let { path ->
        val keyFile = rootProject.layout.projectDirectory.file(path)
        if (!keyFile.asFile.exists()) {
            throw GradleException(
                "SIGNING_KEY_FILE 指的密钥文件不存在：${keyFile.asFile.absolutePath}\n" +
                    "检查 ~/.gradle/gradle.properties 里那一行的路径写对没有（建议写全路径，从 /Users 开始）。",
            )
        }
        // 用 providers.fileContents 而不是直接读文件：这样 Gradle 知道它是配置的输入，
        // 换了密钥会自动重算。直接 File.readText 的话 Gradle 看不见，配置缓存里可能
        // 还留着旧密钥，换了密钥却发现签名没变，很难查。
        providers.fileContents(keyFile).asText.orNull
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

            // 中央仓库不接受"直接推"，只接受一个压缩包。所以先发到这个本地目录，
            // 它按仓库要求的目录结构摆好，再由上面的 bundleForCentral 打成压缩包。
            repositories {
                maven {
                    name = "centralStaging"
                    url = uri(centralStaging)
                }
            }
        }

        // 配了密钥就打开签名。没配就整段跳过——平时构建、发本机仓库完全不受影响。
        if (signingKey != null && signingPassword != null) {
            apply(plugin = "signing")
            configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications)
            }
        }

        // 把自己挂到打包任务上：有几个库模块，压缩包里就有几个模块。
        // 用任务对象（不是字符串、也不是脚本里的变量）来挂，配置缓存才存得下。
        val publishToStaging = tasks.named("publishAllPublicationsToCentralStagingRepository")
        bundleForCentral.configure { dependsOn(publishToStaging) }

        // 清空必须排在**每一个**发布任务前面。只挂在汇总任务上是不够的：
        // 各个模块的具体发布任务是并行跑的，它们不会等汇总任务，会跟清空抢同一个目录，
        // 结果是"目录删不掉"（实测踩到过）。所以按名字把每一个都挂上。
        tasks.matching { it.name.endsWith("PublicationToCentralStagingRepository") }
            .configureEach { dependsOn(cleanCentralStaging) }

        // KMP 的根发布（那份"说明书"）源码包默认是**空的**，里面只有 MANIFEST——
        // 实测：别人在 IDE 里点"下载源码"就拿到一个空包。真源码全在 -android 那份里
        // （commonMain + androidMain 都在），这里给根发布补上 commonMain，
        // 让"跨平台共用那部分"在哪都能看到。
        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kmp = extensions.getByType(
                org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
            )
            // 取成 List<File> 而不是 SourceDirectorySet 对象——后者配置缓存存不下（实测报过错）
            val commonMainDirs =
                kmp.sourceSets.getByName("commonMain").kotlin.srcDirs.toList()
            // 注意类型是 org.gradle.jvm.tasks.Jar：Gradle 9 把 Jar 挪了包，
            // 写成老的那个（org.gradle.api.tasks.bundling.Jar）会报"不是它的子类"（实测踩到）
            // 另外名字是**小写开头的 sourcesJar**，用 endsWith("SourcesJar") 会漏掉它
            // （androidSourcesJar / metadataSourcesJar 才是大写 S），实测栽在这个大小写上。
            tasks.withType<org.gradle.jvm.tasks.Jar>()
                .matching { it.name.equals("sourcesJar", ignoreCase = true) }
                .configureEach { from(commonMainDirs) }
        }
    }
}
