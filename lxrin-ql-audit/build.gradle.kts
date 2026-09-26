plugins {
    id("lxrin.java-conventions")
    id("lxrin.publish-conventions")
}

description = "Audit history for LxrinQL: every write to an audited table stored in <table>_aud with one revision per transaction, compatible with Hibernate Envers."

dependencies {
    api(project(":lxrin-ql-core"))

    testImplementation(testFixtures(project(":lxrin-ql-core")))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "ch.lxrin.ql.audit")
    }
}
