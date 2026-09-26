package ch.lxrin.ql.dsl;

import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.schema.AdHocTable;
import ch.lxrin.ql.types.DataType;

import java.util.List;
import java.util.Objects;

/**
 * The <strong>only</strong> way to put SQL text into a statement.
 *
 * <p>Everywhere else in LxrinQL a Java value is a bind parameter. The
 * methods of this class take a template with positional placeholders
 * {@code {0}}, {@code {1}}, ... whose arguments are typed query parts
 * (fields, {@code Dsl.param(..)}), never strings, so even a raw template
 * cannot turn user input into SQL:</p>
 * <pre>{@code
 * Field<Double> score = Sql.raw("similarity({0}, {1})", SqlTypes.FLOAT8, USERS.NAME, param(text));
 * Condition fts      = Sql.condition("{0} @@ websearch_to_tsquery('simple', {1})", DOCS.TSV, param(q));
 * }</pre>
 *
 * <p>Calls to this class are easy to find, and the {@code lxrin-ql-test}
 * architecture rules can forbid them outside selected packages.</p>
 */
public final class Sql {

    private Sql() {}

    /**
     * A typed SQL expression from a template.
     *
     * @param template SQL text with {@code {n}} placeholders
     * @param type     the SQL type of the result
     * @param args     the placeholder arguments
     */
    public static <T> Field<T> raw(String template, DataType<T> type, QueryPart... args) {
        return Fields.of(type, template(template, args));
    }

    /** A condition from a template, e.g. {@code Sql.condition("{0} @@ {1}", doc, query)}. */
    public static Condition condition(String template, QueryPart... args) {
        return Fields.condition(ctx -> ctx.append('(').visit(template(template, args)).append(')'), true);
    }

    /** A table that is not generated from the schema; declare its columns with {@code field(name, type)}. */
    public static AdHocTable table(String name) {
        return new AdHocTable(null, name, null);
    }

    /** A schema-qualified table that is not generated from the schema. */
    public static AdHocTable table(String schema, String name) {
        return new AdHocTable(schema, name, null);
    }

    /**
     * A complete statement from a template, for statements the DSL does not
     * cover (DDL, {@code VACUUM}, {@code SET}, ...). Run it with
     * {@code ctx.execute(statement)}.
     */
    public static RawStatement statement(String template, QueryPart... args) {
        return new RawStatement(template(template, args));
    }

    /** A complete statement from a template (see {@link #statement(String, QueryPart...)}). */
    public static final class RawStatement implements QueryPart {
        private final QueryPart body;

        RawStatement(QueryPart body) {
            this.body = body;
        }

        @Override
        public void render(RenderContext ctx) {
            ctx.visit(body);
        }
    }

    static QueryPart template(String template, QueryPart... args) {
        Objects.requireNonNull(template, "template");
        List<QueryPart> list = List.of(args);
        return ctx -> {
            int i = 0;
            int n = template.length();
            while (i < n) {
                char c = template.charAt(i);
                int end = c == '{' ? placeholderEnd(template, i) : -1;
                if (end > 0) {
                    int index = Integer.parseInt(template.substring(i + 1, end));
                    if (index >= list.size()) {
                        throw new IllegalArgumentException("placeholder {" + index + "} has no argument: " + template);
                    }
                    ctx.visit(list.get(index));
                    i = end + 1;
                } else {
                    ctx.append(c);
                    i++;
                }
            }
        };
    }

    private static int placeholderEnd(String template, int start) {
        int i = start + 1;
        while (i < template.length() && Character.isDigit(template.charAt(i))) i++;
        return i > start + 1 && i < template.length() && template.charAt(i) == '}' ? i : -1;
    }
}
