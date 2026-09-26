// Gradle plugins are published to the Gradle Plugin Portal (in addition to Maven Central, see lxrin.publish-conventions).
// Portal credentials: gradle.publish.key / gradle.publish.secret.
plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
}
