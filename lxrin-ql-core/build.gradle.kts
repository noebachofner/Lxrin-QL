plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "LxrinQL core: a strongly typed query language and data access layer for PostgreSQL. No runtime dependencies."

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "ch.lxrin.ql.core")
    }
}
