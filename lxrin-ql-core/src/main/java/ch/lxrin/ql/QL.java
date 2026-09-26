package ch.lxrin.ql;

import ch.lxrin.ql.dsl.Statements;
import ch.lxrin.ql.runtime.QueryContext;

/**
 * The entry point of LxrinQL. Type {@code QL.} to find every statement, condition and
 * function:
 *
 * <pre>{@code
 * String name = QL.createContribution(String.class, USERS, (c, b) -> c
 *         .select(col(USERS.USERNAME))
 *         .where(and(
 *                 eq(USERS.KEYCLOAK_ID, b.setString(keycloakId)),
 *                 in(USERS.LOCALE, "de", "en"),
 *                 isNull(USERS.DELETED_AT))))
 *         .fetchOne();
 * }</pre>
 *
 * <p>With {@code import static ch.lxrin.ql.QL.*} the prefix can be left out:
 * {@code createContribution(..)}, {@code eq(..)}, {@code count()}.</p>
 *
 * <p>Statements created here run on {@link QueryContext#getDefault()}, which
 * {@code lxrin-ql-spring} registers, so {@code QL.createContribution(..)} works in any
 * Spring bean without injection. {@code QL} has exactly the same static methods as
 * {@link ch.lxrin.ql.dsl.Dsl}, the entry point of 3.0 and 3.1; both inherit them from
 * {@link Statements}.</p>
 */
public final class QL extends Statements {

    private QL() {}
}
