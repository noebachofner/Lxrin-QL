plugins {
    `java-platform`
    id("lxrin.publish-conventions")
}

description = "Bill of materials that aligns the versions of all LxrinQL modules."

dependencies {
    constraints {
        rootProject.subprojects
            .filter { it.name.startsWith("lxrin-ql-") && it.name != "lxrin-ql-bom" && it.name != "lxrin-ql-legacy" }
            .forEach { api("${it.group}:${it.name}:${it.version}") }
    }
}
