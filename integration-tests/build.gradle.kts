// Testcontainers-based integration tests over all modules. Not published.
plugins {
    id("lxrin.java-conventions")
}

dependencies {
    testImplementation(project(":lxrin-ql-core"))
    testImplementation(testFixtures(project(":lxrin-ql-core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    // one PostgreSQL container is shared by all test classes of this JVM
    maxParallelForks = 1
}
