// Publishes the module to Maven Central (central.sonatype.com) with sources and javadoc jars.
// Credentials and the GPG key come from ~/.gradle/gradle.properties or ORG_GRADLE_PROJECT_* env vars.
plugins {
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    // Maven Central requires signatures. Skipped without a key so local publishing keeps working.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    coordinates(project.group.toString(), project.name, project.version.toString())

    pom {
        name.set(project.name)
        description.set(provider { project.description })
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

// A file repository inside the build, used by the consumer integration tests (Maven and Gradle example projects).
publishing {
    repositories {
        maven {
            name = "integrationTest"
            url = uri(rootProject.layout.buildDirectory.dir("it-repo"))
        }
    }
}
