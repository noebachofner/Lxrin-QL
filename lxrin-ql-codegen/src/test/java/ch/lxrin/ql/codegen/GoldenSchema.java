package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.ForeignKeyModel;
import ch.lxrin.ql.codegen.SchemaModel.KeyModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

import java.util.List;

/** A hand-made schema model with the edge cases the generator must handle. */
final class GoldenSchema {

    private GoldenSchema() {}

    static ColumnModel col(String name, String type, boolean notNull) {
        return new ColumnModel(name, type, "pg_catalog", "b", null, null, null, notNull, false, "", "", null, null);
    }

    static SchemaModel model() {
        EnumModel status = new EnumModel("public", "order_status", List.of("new", "in-progress", "done"));
        EnumModel mood = new EnumModel("public", "mood", List.of("happy", "sad"));
        TableModel users = new TableModel("public", "app_users", "r", "People <who> sign in", List.of(
                col("id", "uuid", true),
                new ColumnModel("display_name", "varchar", "pg_catalog", "b", null, null, null, true, false, "", "", "Shown in the UI", null),
                col("email", "citext", true),
                new ColumnModel("mood", "mood", "public", "e", null, null, null, false, false, "", "", null, null),
                new ColumnModel("tags", "_text", "pg_catalog", "b", "text", "b", "pg_catalog", true, true, "", "", null, null),
                col("class", "text", false),
                col("app_users", "int4", false),
                new ColumnModel("created_at", "timestamptz", "pg_catalog", "b", null, null, null, true, true, "", "", null, null)),
                new KeyModel("app_users_pkey", List.of("id")),
                List.of(new KeyModel("app_users_email_key", List.of("email"))), List.of());
        TableModel orders = new TableModel("sales", "orders", "r", null, List.of(
                new ColumnModel("id", "int8", "pg_catalog", "b", null, null, null, true, true, "d", "", null, "sales.orders_id_seq"),
                col("user_id", "uuid", true),
                col("total", "numeric", true),
                new ColumnModel("status", "order_status", "public", "e", null, null, null, true, true, "", "", null, null),
                new ColumnModel("statuses", "_order_status", "public", "b", "order_status", "e", "public", false, false, "", "", null, null),
                col("placed_on", "date", true),
                col("payload", "jsonb", false),
                col("ip", "inet", false),
                new ColumnModel("total_with_tax", "numeric", "pg_catalog", "b", null, null, null, false, true, "", "s", null, null)),
                new KeyModel("orders_pkey", List.of("id")), List.of(),
                List.of(new ForeignKeyModel("orders_user_id_fkey", List.of("user_id"), "public", "app_users", List.of("id"))));
        TableModel memberships = new TableModel("public", "memberships", "r", null, List.of(
                col("user_id", "uuid", true), col("group_name", "text", true), col("since", "date", false)),
                new KeyModel("memberships_pkey", List.of("user_id", "group_name")),
                List.of(new KeyModel("memberships_user_since_idx", List.of("user_id", "since"))),
                List.of(new ForeignKeyModel("memberships_user_id_fkey", List.of("user_id"), "public", "app_users", List.of("id"))));
        TableModel view = new TableModel("public", "active_users", "v", null, List.of(col("id", "uuid", false), col("email", "citext", false)),
                null, List.of(), List.of());
        TableModel log = new TableModel("public", "event_log", "r", null, List.of(col("at", "timestamptz", true), col("message", "text", true)),
                null, List.of(), List.of());
        return new SchemaModel(List.of(users, orders, memberships, view, log), List.of(status, mood));
    }

    static CodegenConfig config(java.nio.file.Path out) {
        return new CodegenConfig()
                .packageName("com.example.db")
                .schemas(List.of("public", "sales"))
                .stripTablePrefix("app_")
                .tableConstant("app_users", "USERS")
                .enumMapping("mood", "ch.lxrin.ql.codegen.Mood")
                .forcedType(new CodegenConfig.ForcedType("orders", "payload", "jsonb", "ch.lxrin.ql.codegen.Payload",
                        "ch.lxrin.ql.codegen.Payload.TYPE"))
                .outputDirectory(out.resolve("java"))
                .resourcesDirectory(out.resolve("resources"))
                .repositoryStubDirectory(out.resolve("stubs"));
    }
}
