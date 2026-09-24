plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "LxrinQL code generator: reads a PostgreSQL schema and generates typed tables, rows, entities and repositories."

dependencies {
    api(project(":lxrin-ql-core"))
    implementation(libs.postgresql)
    implementation(libs.testcontainers.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test {
    // golden files are read relative to the module directory
    workingDir = projectDir
    systemProperty("golden.update", System.getProperty("golden.update", "false"))
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "ch.lxrin.ql.codegen.Main", "Automatic-Module-Name" to "ch.lxrin.ql.codegen")
    }
}
