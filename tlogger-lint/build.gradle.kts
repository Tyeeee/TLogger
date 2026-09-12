plugins {
    id("com.android.lint")
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

// 说明：本模块只在构建期使用（写代码时检查），不打包进安装包。
// 安卓的检查工具自身要求 JVM 17，所以这里的目标版本是 17，而不是其他模块的 11。
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.lint.api)
    // 测试期需要 lint-api（Detector/Issue 等）与官方测试底座
    testImplementation(libs.lint.api)
    testImplementation(libs.lint.tests)
    testImplementation(libs.junit)
}

// 纯 JVM 模块不会自动生成发布配置，要自己声明一个（KMP 模块是自动的）
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
