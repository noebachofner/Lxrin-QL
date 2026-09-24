// The 2.x sources, kept only while 3.0 is being built. Not published; removed before the 3.0.0 release.
plugins {
    id("lxrin.java-conventions")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.launcher)
}
