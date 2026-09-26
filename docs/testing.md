# Testing

`ch.lxrin:lxrin-ql-test` contains helpers for testing code that uses LxrinQL.

```kotlin
dependencies {
    testImplementation("ch.lxrin:lxrin-ql-test:3.0.1")
    // optional, for @LxrinPostgresTest
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.5")
    testImplementation("org.flywaydb:flyway-database-postgresql:13.8.0")
    testRuntimeOnly("org.postgresql:postgresql:42.7.13")
    // optional, for the architecture rules
    testImplementation("com.tngtech.archunit:archunit:1.5.0")       // or archunit-junit5 for @ArchTest
}
```

## SQL assertions

```java
import static ch.lxrin.ql.test.SqlAssertions.assertThatSql;

assertThatSql(select(USERS.NAME).from(USERS).where(USERS.EMAIL.endsWith("@example.org")))
        .isEqualTo("SELECT app_user.name FROM app_user WHERE app_user.email LIKE ?")
        .hasBinds("%@example.org")
        .isConsistent();                     // as many ? as binds

assertThatSql(update(USERS).set(USERS.ACTIVE, false).allRows()).contains("SET active = ?");
```

Statements are rendered without table policies. To see what a `QueryContext` really
sends, use the `MockExecutor`.

## MockExecutor

```java
MockExecutor db = new MockExecutor();
db.whenSqlContains("FROM app_user").thenReturn(row(id, "Ada"));
db.whenSqlContains("DELETE").thenAffect(3);
db.whenSqlContains("INSERT").times(1).thenThrow(new UniqueViolationException(..));
QueryContext ctx = db.context();            // or QueryContext.builder().executor(db)…

service(ctx).doSomething();

assertThatSql(db.lastStatement()).isEqualTo("…");
assertEquals(2, db.statements().size());
```

The pipeline (policies, conventions, listeners) runs as usual. Rows must contain the
values in select-list order, already of the right Java types.

## @LxrinPostgresTest

```java
@LxrinPostgresTest                                   // migrations from classpath:db/migration
class UserRepositoryTest {

    @Test
    void savesUsers(QueryContext ctx) {
        UserRepository users = new UserRepository(ctx);
        users.save(newUser("Ada"));
        assertEquals(1, users.count());
    }                                                // rolled back after the test
}
```

- One PostgreSQL container (Testcontainers) is shared by all test classes with the
  same image and migrations.
- Flyway migrations are applied once.
- Test methods can take a `QueryContext` or a `DataSource` parameter. The context is
  also `QueryContext.getDefault()` during the test (`setDefault = false` turns that off).
- With `rollback = true` (the default) each test runs on one connection whose changes
  are rolled back. Transactions inside the test become savepoints then; use
  `rollback = false` to test real commits and `REQUIRES_NEW`.

## Architecture rules

```java
@AnalyzeClasses(packages = "com.example")
class ArchitectureTest {
    @ArchTest static final ArchRule noRawSql = LxrinArchRules.noRawSqlOutside("com.example.migration..");
    @ArchTest static final ArchRule noQueryParts = LxrinArchRules.noHandWrittenQueryParts();
    @ArchTest static final ArchRule bypass = LxrinArchRules.policyBypassOnlyIn("com.example.admin..");
    @ArchTest static final ArchRule jdbc = LxrinArchRules.noJdbcOutside("com.example.infrastructure..");
}
```

| Rule | Forbids |
|---|---|
| `noRawSql()` / `noRawSqlOutside(packages…)` | using `ch.lxrin.ql.dsl.Sql` (raw SQL) |
| `noHandWrittenQueryParts()` | implementing `QueryPart` or calling `RenderContext.append` (SQL text written by hand) |
| `policyBypassOnlyIn(packages…)` | calling `bypassing(..)` elsewhere |
| `noJdbcOutside(packages…)` | using `java.sql` elsewhere |
