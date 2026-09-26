package ch.lxrin.ql.codegen;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Collects the imports of one generated source file and avoids name clashes. */
final class Imports {

    private final String packageName;
    private final Set<String> localNames;
    private final Map<String, String> bySimpleName = new TreeMap<>();

    /**
     * @param packageName the file's package
     * @param localNames  simple names of the classes generated into the same package
     */
    Imports(String packageName, Set<String> localNames) {
        this.packageName = packageName;
        this.localNames = localNames;
    }

    /** Returns the name to use in code for a fully qualified type, importing it if possible. */
    String use(String fqn) {
        if (fqn.endsWith("[]")) return use(fqn.substring(0, fqn.length() - 2)) + "[]";
        int dot = fqn.lastIndexOf('.');
        if (dot < 0) return fqn;
        String pkg = fqn.substring(0, dot);
        String simple = fqn.substring(dot + 1);
        if (pkg.equals("java.lang")) return localNames.contains(simple) ? fqn : simple;
        if (pkg.equals(packageName)) return simple;
        if (localNames.contains(simple)) return fqn;
        String existing = bySimpleName.get(simple);
        if (existing == null) {
            bySimpleName.put(simple, fqn);
            return simple;
        }
        return existing.equals(fqn) ? simple : fqn;
    }

    /** Returns the import block. */
    String block() {
        Set<String> sorted = new TreeSet<>(bySimpleName.values());
        StringBuilder sb = new StringBuilder();
        for (String i : sorted) sb.append("import ").append(i).append(";\n");
        return sb.length() == 0 ? "" : sb.append('\n').toString();
    }
}
