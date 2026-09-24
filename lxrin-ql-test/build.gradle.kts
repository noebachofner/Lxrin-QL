plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "Test support for LxrinQL: SQL assertions, a mock executor, a PostgreSQL JUnit extension (Testcontainers) and ArchUnit rules."

dependencies {
    api(project(":lxrin-ql-core"))
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter.api)
    // optional: add them to your test dependencies to use the PostgreSQL extension or the architecture rules
    compileOnly(libs.testcontainers.postgresql)
    compileOnly(libs.flyway.core)
    compileOnly(libs.archunit)
    compileOnly(libs.postgresql)

    testImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(project(":lxrin-ql-core")))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(libs.archunit)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}
