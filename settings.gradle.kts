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
    "lxrin-ql-legacy",
    "integration-tests",
)
