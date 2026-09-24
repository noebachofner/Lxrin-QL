// Testcontainers-based integration tests over all modules. Not published.
plugins {
    id("lxrin.java-conventions")
}

dependencies {
    testImplementation(project(":lxrin-ql-core"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}
