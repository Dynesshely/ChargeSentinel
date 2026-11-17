pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    // 默认 PREFER_PROJECT：本机 ~/.gradle/init.gradle 会注入阿里云镜像仓库，
    // FAIL_ON_PROJECT_REPOS 会与之冲突导致构建直接失败。
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChargeSentinel"
include(":app")
