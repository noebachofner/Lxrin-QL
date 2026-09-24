plugins {
    id("lxrin.java-conventions")
    id("lxrin.gradle-plugin-conventions")
    id("lxrin.publish-conventions")
}

description = "Gradle plugin that generates LxrinQL tables, rows, entities and repositories from a PostgreSQL schema."

dependencies {
    implementation(project(":lxrin-ql-codegen"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(gradleTestKit())
    testRuntimeOnly(libs.junit.launcher)
}

gradlePlugin {
    website.set("https://github.com/noebachofner/Lxrin-QL")
    vcsUrl.set("https://github.com/noebachofner/Lxrin-QL.git")
    plugins {
        create("lxrinQl") {
            id = "ch.lxrin.ql.codegen"
            implementationClass = "ch.lxrin.ql.gradle.LxrinQlPlugin"
            displayName = "LxrinQL code generator"
            description = project.description
            tags.set(listOf("postgresql", "sql", "codegen", "jdbc", "orm"))
        }
    }
}

// The plugin adds lxrin-ql-core of its own version to the consumer project.
tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("lxrin-ql-gradle-plugin.properties") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("version" to version))
    }
}
