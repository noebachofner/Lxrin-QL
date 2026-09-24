// An example Gradle project that uses the LxrinQL Gradle plugin. The integration tests run it with
// -PlxrinRepo=<file repository with the freshly built LxrinQL artifacts>.
pluginManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("lxrinRepo").get()) }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri(providers.gradleProperty("lxrinRepo").get()) }
        mavenCentral()
    }
}

rootProject.name = "gradle-consumer"
