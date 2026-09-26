// A Maven plugin built with Gradle: the plugin descriptor (META-INF/maven/plugin.xml) is written by hand
// and the published POM has packaging "maven-plugin".
plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "Maven plugin that generates LxrinQL tables, rows, entities and repositories from a PostgreSQL schema."

dependencies {
    implementation(project(":lxrin-ql-codegen"))
    compileOnly(libs.maven.plugin.api)
    compileOnly(libs.maven.core)
    testImplementation(libs.maven.plugin.api)
    testImplementation(libs.maven.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

mavenPublishing {
    pom {
        packaging = "maven-plugin"
    }
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("META-INF/maven/plugin.xml") {
        filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("version" to version))
    }
}
