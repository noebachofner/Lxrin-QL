plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "Spring Boot 4 integration for LxrinQL: QueryContext bean, Spring-managed transactions, repositories as beans, BEANS backed by the ApplicationContext."

dependencies {
    api(project(":lxrin-ql-core"))
    // optional: the audit history is configured when lxrin-ql-audit is on the class path
    compileOnly(project(":lxrin-ql-audit"))
    // Spring is provided by the application (Spring Boot 4.x)
    compileOnly(platform(libs.spring.boot.dependencies))
    compileOnly(libs.spring.boot.autoconfigure)
    compileOnly(libs.spring.jdbc)
    compileOnly(libs.spring.tx)
    compileOnly("tools.jackson.core:jackson-databind")
    compileOnly("io.micrometer:micrometer-observation")
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.boot.starter)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.postgresql)
    testImplementation(libs.spring.jdbc)
    testImplementation("tools.jackson.core:jackson-databind")
    testImplementation("io.micrometer:micrometer-observation")
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(testFixtures(project(":lxrin-ql-core")))
    testImplementation(project(":lxrin-ql-audit"))
    testRuntimeOnly(libs.junit.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "ch.lxrin.ql.spring")
    }
}
