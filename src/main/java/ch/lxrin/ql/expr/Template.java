package ch.lxrin.ql.expr;

import java.util.List;

/**
 * A SQL template with positional placeholders {@code {0}}, {@code {1}}, ...
 * that are replaced by rendered arguments.
 *
 * <pre>{@code
 * sql("EXTRACT({0} FROM {1})", "YEAR", o.createdAt)
 * sql("{0} AT TIME ZONE {1}", o.createdAt, val("Europe/Zurich"))
 * }</pre>
 *
 * <p>Arguments follow the usual operand rule: strings are SQL fragments, other
 * Java values are bound as parameters. Only a brace followed by digits and a
 * closing brace is a placeholder; every other brace is copied unchanged.</p>
 */
public final class Template implements Expression {

    private final String template;
    private final List<Object> args;

    /**
     * @param template SQL with {@code {n}} placeholders
     * @param args     placeholder values
     */
    public Template(String template, Object... args) {
        if (template == null) throw new IllegalArgumentException("template must not be null");
        this.template = template;
        this.args = java.util.Arrays.asList(args == null ? new Object[]{null} : args);
    }

    @Override
    public void render(RenderContext ctx) {
        int i = 0;
        int n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            int end = c == '{' ? placeholderEnd(i) : -1;
            if (end > 0) {
                int index = Integer.parseInt(template.substring(i + 1, end));
                if (index >= args.size()) {
                    throw new IllegalArgumentException("template placeholder {" + index + "} has no argument: " + template);
                }
                ctx.visit(args.get(index));
                i = end + 1;
            } else {
                ctx.append(c);
                i++;
            }
        }
    }

    /** Returns the index of the closing brace if a {n} placeholder starts at {@code start}, else -1. */
    private int placeholderEnd(int start) {
        int i = start + 1;
        while (i < template.length() && Character.isDigit(template.charAt(i))) i++;
        return i > start + 1 && i < template.length() && template.charAt(i) == '}' ? i : -1;
    }
}
