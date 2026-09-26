// Multi-module build. Shared configuration lives in build-logic (convention plugins).
allprojects {
    group = "ch.lxrin"
    version = providers.gradleProperty("version").get()
}
