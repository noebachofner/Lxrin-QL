# Changelog

## 2.0.0

A complete rework. LxrinQL is now a framework-independent library with
broad PostgreSQL coverage and builds with both Gradle and Maven.

### Build

- Gradle build (Kotlin DSL, version catalog, wrapper) added next to the Maven
  build. Both produce `ch.lxrin:lxrin-ql` with sources and javadoc jars.
- No runtime dependencies. The Eclipse Scout dependency has been removed.

### New

- Expression model: every column, function, parameter, condition and
  statement is an `Expression` and can be nested anywhere.
- Automatic bind parameters with `val(..)`. Any non-`String` Java value is
  bound automatically.
- Complete `SELECT`: `DISTINCT ON`, all join types (`LATERAL`, `USING`,
  `NATURAL`), `GROUP BY ROLLUP/CUBE/GROUPING SETS`, `HAVING`, `WINDOW`,
  `UNION/INTERSECT/EXCEPT [ALL]`, `ORDER BY … NULLS FIRST/LAST`,
  `LIMIT/OFFSET`, `page(..)`, `FETCH … WITH TIES`, row locking, CTEs
  (recursive, materialized, data-modifying), sub-queries everywhere.
- Data modification: `insertInto`, `update`, `deleteFrom`, `truncate`,
  upserts (`ON CONFLICT`), `RETURNING`, `UPDATE … FROM`, `DELETE … USING`.
- About 250 PostgreSQL functions and operators (aggregates with `FILTER`,
  `WITHIN GROUP`, window functions, string/regex, math, date/time, ranges,
  JSON/JSONB/jsonpath, arrays, full-text search), plus `function(..)` for
  anything else.
- More conditions: `ilike`, `similarTo`, regex matching, `isDistinctFrom`,
  `exists`, `any`/`all`, `contains`/`containedBy`/`overlaps`, JSON key tests,
  `tsMatches`, `between symmetric`, boolean tests, `and(..)`/`or(..)` with
  null skipping, and a fluent API on expressions (`p.age.ge(18).and(..)`).
- `whereIf(..)` for optional filters (also lazy with a `Supplier`).
- `JdbcSqlExecutor` for any `DataSource` or `Connection`: named-parameter
  parsing, collection expansion, SQL arrays and `java.time` conversion.
- Automatic result mapping to records, beans and scalars. `optional()`,
  `fetchCount()` and `fetchExists()`.
- Safety: `UPDATE`/`DELETE` without `WHERE` is rejected (unless you call
  `allRows()`), `eq(x, null)` is rejected, `cast(..)` validates type names,
  and SQL errors carry the statement and the SQLSTATE.
- A PostgreSQL integration test that runs the catalog against a real
  database (enabled by `LXRIN_QL_PG_URL`).

### Breaking changes and migration from 1.x

| 1.x | 2.0 |
|---|---|
| Maven only, Scout `provided` dependency | Gradle and Maven, no dependencies |
| `ch.lxrin.ql.QueryBuilder` | `ch.lxrin.ql.query.SelectQuery` (still created by `createContribution(..)`) |
| `ch.lxrin.ql.sql.ISqlExecutor` (`select`, `selectInto`, `execute`) | `ch.lxrin.ql.exec.SqlExecutor` (`select`, `execute`) |
| `ScoutSqlExecutor` as the implicit default | configure an executor: `LxrinQL.setDefaultExecutor(new JdbcSqlExecutor(dataSource))` or implement `SqlExecutor` for your framework |
| `selectInto(..)` / `SelectIntoBuilder` | removed; map rows with `select(Type.class, ..)`, a bean class or `mapWith(..)` |
| `.select("t.ID", "id")` (expression, alias) | `.select(as("t.ID", "id"))` or `.select(raw("t.ID").as("id"))`; two strings are now two SQL fragments |
| `Condition.toSql()` was the abstract method (lambdas returned a `String`) | `render(RenderContext)` is abstract; `toSql()` still exists. Custom lambdas: `ctx -> ctx.append("...")` |
| adjacent conditions needed an explicit `and()` | adjacent conditions are joined with `AND` automatically; explicit `and()`/`or()` still work |
| `eq(col, null)` rendered `= null` / threw late | throws immediately; use `isNull(col)` |
| `FROM` was mandatory | `SELECT` without `FROM` is allowed (`select(now())`) |
| `SimpleCondition`, `InCondition`, `BetweenCondition`, `NullCondition` | removed; use the factory methods |

Unchanged: `TableDef`/`Column` (plus `getName()`, `columns()`, `all()`),
`Binds`, `BindMap`, `RowMapper`, `createContribution(..)`, `.single()`,
`.multiple()`, `.bind(..)`, `.mapWith(..)`, `.executor(..)` and the
`qlid` live template.

## 1.0.0

- Initial release: `QueryBuilder`, `SelectIntoBuilder`, basic conditions,
  `TableDef`/`Column`, `Binds`, Eclipse Scout executor.
