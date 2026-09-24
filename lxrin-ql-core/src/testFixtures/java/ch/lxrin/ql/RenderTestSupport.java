package ch.lxrin.ql;

import ch.lxrin.ql.render.Bind;
import ch.lxrin.ql.render.QueryPart;
import ch.lxrin.ql.render.RenderContext;
import ch.lxrin.ql.render.RenderedSql;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Helpers to render parts and check SQL and bind values. */
public final class RenderTestSupport {

    private RenderTestSupport() {}

    public static RenderedSql render(QueryPart part) {
        RenderContext ctx = new RenderContext();
        ctx.visit(part);
        return ctx.result();
    }

    public static String sql(QueryPart part) {
        return render(part).sql();
    }

    public static List<Object> binds(QueryPart part) {
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : render(part).binds()) values.add(b.value());
        return values;
    }

    public static void assertSql(String expected, QueryPart part, Object... expectedBinds) {
        RenderedSql r = render(part);
        assertEquals(expected, r.sql());
        List<Object> values = new ArrayList<>();
        for (Bind<?> b : r.binds()) values.add(b.value() instanceof Object[] ? Arrays.asList((Object[]) b.value()) : b.value());
        List<Object> expectedValues = new ArrayList<>();
        for (Object o : expectedBinds) expectedValues.add(o instanceof Object[] ? Arrays.asList((Object[]) o) : o);
        assertEquals(expectedValues, values, "binds");
    }
}
