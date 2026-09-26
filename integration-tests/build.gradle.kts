// Testcontainers-based integration tests over all modules. Not published.
plugins {
    id("lxrin.java-conventions")
}

val codegen: Configuration = configurations.create("codegen")

dependencies {
    testImplementation(project(":lxrin-ql-core"))
    testImplementation(testFixtures(project(":lxrin-ql-core")))
    testImplementation(project(":lxrin-ql-codegen"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)
    testImplementation(gradleTestKit())
    testImplementation(project(":lxrin-ql-spring"))
    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.jackson)
    testImplementation(libs.spring.boot.micrometer.observation)
    testImplementation(libs.spring.boot.test)
    testRuntimeOnly(libs.junit.launcher)
    codegen(project(":lxrin-ql-codegen"))
}

// Generates the typed tables, entities and repositories of the test schema with the code generator's CLI.
val generatedJava = layout.buildDirectory.dir("generated/sources/lxrinql/test/java")
val generatedResources = layout.buildDirectory.dir("generated/resources/lxrinql/test")
val migrations = layout.projectDirectory.dir("src/test/resources/db/migration")

val generateSchemaCode = tasks.register<JavaExec>("generateSchemaCode") {
    description = "Generates code for the integration test schema"
    classpath = codegen
    mainClass.set("ch.lxrin.ql.codegen.Main")
    inputs.dir(migrations)
    outputs.dir(generatedJava)
    outputs.dir(generatedResources)
    outputs.cacheIf { true }
    args(
        "--package", "ch.lxrin.ql.it.db",
        "--output", generatedJava.get().asFile.path,
        "--resources", generatedResources.get().asFile.path,
        "--stubs", layout.projectDirectory.dir("src/test/java").asFile.path,
        "--migrations", migrations.asFile.path,
        "--strip-prefixes", "app_",
        "--table-constants", "app_user=USERS",
        "--forced-types", "app_user|id|uuid|ch.lxrin.ql.it.types.UserId|ch.lxrin.ql.it.types.ItTypes.USER_ID;"
            + "asset|owner_id|uuid|ch.lxrin.ql.it.types.UserId|ch.lxrin.ql.it.types.ItTypes.USER_ID",
    )
}

sourceSets.test {
    java.srcDir(generateSchemaCode.map { generatedJava })
    resources.srcDir(generateSchemaCode.map { generatedResources })
}

// The consumer projects use the artifacts of this build from a file repository.
val publishedModules = listOf(":lxrin-ql-bom", ":lxrin-ql-core", ":lxrin-ql-codegen", ":lxrin-ql-gradle-plugin", ":lxrin-ql-maven-plugin")

tasks.test {
    // one PostgreSQL container is shared by all test classes of this JVM
    maxParallelForks = 1
    // the Gradle TestKit jar brings its own SLF4J binding, which Spring Boot's Logback setup rejects
    systemProperty("org.springframework.boot.logging.LoggingSystem", "none")
    publishedModules.forEach { dependsOn("$it:publishAllPublicationsToIntegrationTestRepository") }
    inputs.dir("consumers").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("lxrin.consumers", layout.projectDirectory.dir("consumers").asFile.absolutePath)
    systemProperty("lxrin.repo", rootProject.layout.buildDirectory.dir("it-repo").get().asFile.absolutePath)
    // outside build/ so that "clean" does not force Maven to download its plugins again
    systemProperty("lxrin.mavenLocalRepo", rootProject.layout.projectDirectory.dir(".gradle/it-m2").asFile.absolutePath)
}
