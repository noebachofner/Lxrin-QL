pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "lxrin-ql"

include(
    "lxrin-ql-bom",
    "lxrin-ql-core",
    "lxrin-ql-codegen",
    "lxrin-ql-gradle-plugin",
    "lxrin-ql-maven-plugin",
    "lxrin-ql-spring",
    "lxrin-ql-test",
    "integration-tests",
)
