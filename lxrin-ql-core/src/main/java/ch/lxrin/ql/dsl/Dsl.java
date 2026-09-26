package ch.lxrin.ql.dsl;

import ch.lxrin.ql.runtime.QueryContext;

/**
 * The static entry point of LxrinQL 3.0 and 3.1. It has exactly the same static methods as
 * {@code ch.lxrin.ql.QL}, which is the primary entry point since 3.2; both inherit them from
 * {@link Statements}. {@code Dsl} stays for code written against 3.0 and 3.1:
 *
 * <pre>{@code
 * import static ch.lxrin.ql.dsl.Dsl.*;
 * import static com.example.db.Tables.*;
 *
 * List<UserSummary> gmail = select(USERS.ID, USERS.NAME, USERS.EMAIL)
 *         .from(USERS)
 *         .where(USERS.EMAIL.endsWith("@gmail.com"))
 *         .orderBy(USERS.NAME.asc())
 *         .fetch(UserSummary::new);
 * }</pre>
 *
 * <p>Statements created here run on {@link QueryContext#getDefault()};
 * statements created with {@code ctx.select(..)} run on {@code ctx}.</p>
 */
public final class Dsl extends Statements {

    private Dsl() {}
}
