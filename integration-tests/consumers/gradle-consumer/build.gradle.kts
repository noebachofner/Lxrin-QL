plugins {
    java
    id("ch.lxrin.ql.codegen") version "3.2.0"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

lxrinQl {
    packageName = "com.example.shop.db"
    stripTablePrefixes.add("shop_")
    database {
        flywayMigrations.from("src/main/resources/db/migration")
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
