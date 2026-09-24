package ch.lxrin.ql.codegen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Command line entry point, e.g. for Maven's {@code exec-maven-plugin}:
 *
 * <pre>
 * java -jar lxrin-ql-codegen.jar --package com.example.db --output target/generated-sources/lxrinql \
 *      --migrations src/main/resources/db/migration --stubs src/main/java
 * java -jar lxrin-ql-codegen.jar --config codegen.properties
 * </pre>
 *
 * <p>Options (also as keys of a properties file): {@code package}, {@code output},
 * {@code resources}, {@code stubs}, {@code schemas} (comma-separated),
 * {@code default-schema}, {@code strip-prefixes}, {@code exclude}, {@code singularize},
 * {@code table-constants} and {@code entity-names} ({@code table=NAME,...}),
 * {@code enum-mappings} ({@code pg_enum=com.example.Enum,...}),
 * {@code forced-types} ({@code tableRegex|columnRegex|sqlTypeRegex|javaType|dataTypeExpression;...}),
 * {@code migrations} and {@code scripts} (comma-separated directories),
 * {@code image}, or {@code jdbc-url}, {@code user} and {@code password} for an
 * existing database.</p>
 */
public final class Main {

    private Main() {}

    /** Runs the generator. */
    public static void main(String[] args) throws Exception {
        Properties options = parse(args);
        CodegenConfig config = configure(options);
        try (DatabaseProvisioner db = provision(options); Connection con = db.connect()) {
            CodeGenerator.Result result = CodeGenerator.generate(con, config);
            System.out.println("LxrinQL: generated " + result.generated().size() + " files, created " + result.stubs().size()
                    + " repository stubs");
        }
    }

    static Properties parse(String[] args) throws IOException {
        Properties p = new Properties();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--") || i + 1 >= args.length) throw new IllegalArgumentException("expected --option value: " + a);
            String key = a.substring(2);
            String value = args[++i];
            if (key.equals("config")) {
                try (InputStream in = Files.newInputStream(Path.of(value))) {
                    Properties file = new Properties();
                    file.load(in);
                    file.forEach(p::putIfAbsent);
                }
            } else {
                p.setProperty(key, value);
            }
        }
        return p;
    }

    static CodegenConfig configure(Properties p) {
        CodegenConfig config = new CodegenConfig()
                .packageName(required(p, "package"))
                .outputDirectory(Path.of(required(p, "output")));
        if (p.containsKey("resources")) config.resourcesDirectory(Path.of(p.getProperty("resources")));
        if (p.containsKey("stubs")) config.repositoryStubDirectory(Path.of(p.getProperty("stubs")));
        if (p.containsKey("schemas")) config.schemas(list(p.getProperty("schemas")));
        if (p.containsKey("default-schema")) config.defaultSchema(p.getProperty("default-schema"));
        if (p.containsKey("strip-prefixes")) config.stripTablePrefix(list(p.getProperty("strip-prefixes")).toArray(new String[0]));
        if (p.containsKey("exclude")) config.exclude(list(p.getProperty("exclude")).toArray(new String[0]));
        if (p.containsKey("singularize")) config.singularize(Boolean.parseBoolean(p.getProperty("singularize")));
        for (String[] pair : pairs(p.getProperty("table-constants"))) config.tableConstant(pair[0], pair[1]);
        for (String[] pair : pairs(p.getProperty("entity-names"))) config.entityName(pair[0], pair[1]);
        for (String[] pair : pairs(p.getProperty("enum-mappings"))) config.enumMapping(pair[0], pair[1]);
        String forced = p.getProperty("forced-types", "");
        for (String spec : forced.split(";")) {
            if (spec.isBlank()) continue;
            String[] parts = spec.trim().split("\\|");
            if (parts.length != 5) throw new IllegalArgumentException("forced type needs 5 parts separated by '|': " + spec);
            config.forcedType(new CodegenConfig.ForcedType(parts[0], parts[1], parts[2], parts[3], parts[4]));
        }
        return config.validate();
    }

    static DatabaseProvisioner provision(Properties p) {
        if (p.containsKey("jdbc-url")) {
            return DatabaseProvisioner.jdbc(p.getProperty("jdbc-url"), p.getProperty("user"), p.getProperty("password"));
        }
        List<Path> migrations = new ArrayList<>();
        for (String s : list(p.getProperty("migrations", ""))) migrations.add(Path.of(s));
        List<Path> scripts = new ArrayList<>();
        for (String s : list(p.getProperty("scripts", ""))) scripts.add(Path.of(s));
        return DatabaseProvisioner.testcontainer(p.getProperty("image", "postgres:17-alpine"), migrations, scripts);
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("--" + key + " is required");
        return v;
    }

    private static List<String[]> pairs(String value) {
        List<String[]> result = new ArrayList<>();
        for (String item : list(value)) {
            int eq = item.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("expected name=value: " + item);
            result.add(new String[]{item.substring(0, eq).trim(), item.substring(eq + 1).trim()});
        }
        return result;
    }

    private static List<String> list(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
