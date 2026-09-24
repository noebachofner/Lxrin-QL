// The same artifact is built by Maven (pom.xml).
plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

description = "Fluent, type-safe SQL query builder for Java with broad PostgreSQL coverage. No runtime dependencies."

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(17)
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addStringOption("Xdoclint:all,-missing", "-quiet")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "LxrinQL",
            "Implementation-Version" to project.version,
        )
    }
}

// Publishes to Maven Central (central.sonatype.com) together with sources and javadoc jars.
// Credentials and the GPG key come from ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_* env vars.
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Maven Central requires signatures. Skipped without a key so publishToMavenLocal keeps working.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    coordinates(project.group.toString(), "lxrin-ql", project.version.toString())

    pom {
        name.set("LxrinQL")
        description.set(project.description)
        inceptionYear.set("2024")
        url.set("https://github.com/noebachofner/Lxrin-QL")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("noebachofner")
                name.set("Noé Bachofner")
                email.set("noebachofner@lxrin.ch")
                url.set("https://github.com/noebachofner")
            }
        }
        scm {
            url.set("https://github.com/noebachofner/Lxrin-QL")
            connection.set("scm:git:https://github.com/noebachofner/Lxrin-QL.git")
            developerConnection.set("scm:git:ssh://git@github.com/noebachofner/Lxrin-QL.git")
        }
    }
}
