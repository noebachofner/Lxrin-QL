# Entities and repositories

## Entities

Generated entities are plain data objects: one field, getter and setter per column,
no annotations and no SQL. They extend `ch.lxrin.ql.entity.Entity<K>`, which tracks
their state and changes.

| State | How an entity gets there |
|---|---|
| **new** | `new User()` |
| **persistent** | returned by `findById`, `findAll`, …, or after `save`/`insert`/`update` |
| **deleted** | after `delete` |

```java
User user = users.getById(id);
user.isPersistent();                       // true
user.setName("Ada Lovelace");
user.isChanged();                          // true
user.changes();                            // {name=Change[oldValue=Ada, newValue=Ada Lovelace]}
user.originalValue(USERS.NAME);            // "Ada"
user.setName("Ada");                       // back to the original value
user.isChanged();                          // false
```

- Setting a field to its current value is not a change. Numbers are compared with
  `compareTo` and arrays by content.
- For a **new** entity, the changes are the columns whose setters were called. Only
  these columns are inserted, so database defaults apply to all others and are read
  back afterwards.
- `toRow()` returns a `UserRow`, and `User.fromRow(row)` creates a persistent entity
  from a row, e.g. one read with `selectFrom(USERS)`.
- Entities use identity equality and are not thread-safe.

## Repositories

For every table with a primary key, the generator writes `UserRepositoryBase`
(generated, extends `TableRepository`) and, once, `UserRepository` (yours). Views and
tables without a primary key get a `ReadOnlyRepository` of row records.

```java
public class UserRepository extends UserRepositoryBase {

    public UserRepository(QueryContext context) {
        super(context);
    }

    public List<User> admins() {
        return findAll(USERS.ROLE.eq(Role.ADMIN), USERS.NAME.asc());
    }

    public List<UserSummary> summaries() {
        return ctx().select(USERS.ID, USERS.NAME, USERS.EMAIL).from(USERS).fetch(UserSummary::new);
    }

    public List<User> inactive() {
        return select().where(USERS.ACTIVE.not()).orderBy(USERS.CREATED_AT.asc()).fetch();   // entities
    }
}
```

`ctx()` is the repository's query context (statements report
`REPOSITORY:UserRepository` as their origin). `select()` returns a select of all
columns that maps rows to loaded entities.

### Keys

| Method | Result |
|---|---|
| `createKey()` | a new key according to the key strategy: a time-ordered UUID v7, or `nextval` of the key's sequence |
| `createKeys(n)` | `n` keys; for sequences in one round trip |

Mapped key types work too: `createKey()` of a table whose key is a `UserId` returns a
`UserId`.

### Writing

| Method | What it does |
|---|---|
| `save(e)` | inserts a **new** entity, updates a **persistent** one; nothing if nothing changed |
| `saveAll(list)` | new ones as multi-row `INSERT`s, changed ones as JDBC-batched `UPDATE`s, all in one transaction |
| `insert(e)` | always inserts (for a persistent entity: all columns) |
| `update(e)` | always updates (for a new entity: the set columns; e.g. a detached object built from a request) |
| `delete(e)` | deletes (or soft-deletes, with a policy); the entity becomes **deleted** |
| `deleteById(id)` | deletes by key; `EntityNotFoundException` if there is no row |
| `deleteAll(condition)` | bulk delete through listeners and policies; returns the count |

Details:

- **New or not** is decided by the tracked state, not by whether the id is set,
  because `createKey()` sets the id before the first save.
- Every entity write uses `RETURNING *` and loads the result into the entity.
  Defaults, identity values, generated columns, trigger changes, conventions and the
  new version all end up in the entity.
- An update writes only the changed columns, plus column conventions (e.g.
  `updated_at`) and the version increment.
- **`saveAll`**:
  - It runs in one transaction (it joins an existing one).
  - Inserts are chunked at `min(batchSize, 65535 / columns)` rows, because of
    PostgreSQL's limit of 65535 bind parameters. Returned rows are matched to the
    entities by primary key.
  - Updates with the same set of changed columns become one JDBC batch.
  - If anything fails, nothing is stored.
- **Rollback.** Entity state changes take effect at once but are undone when the
  surrounding transaction rolls back. An entity whose insert was rolled back is new
  again, with its old values.
- **Stale rows.** An `update` or `delete` that matches no row throws
  `StaleEntityException`. That happens when the row was deleted meanwhile or is hidden
  by a policy (another tenant, soft-deleted).
- **Optimistic locking.** With `versionColumn("version")` in the context (or
  `lxrin.ql.version-column` in Spring):
  - updates and deletes include `version = <loaded version>` in their `WHERE`;
  - updates increment the version;
  - a mismatch throws `OptimisticLockException`;
  - bulk DSL updates increment the version as well.

### Reading

| Method | Result |
|---|---|
| `findById(id)` | `Optional<E>` |
| `getById(id)` | `E`, or `EntityNotFoundException` |
| `findAllById(ids)` | `List<E>` |
| `findAll()`, `findAll(condition, orderBy…)` | `List<E>` |
| `findOne(condition)` | `Optional<E>`; `TooManyRowsException` if several match |
| `findBy<UniqueKey>(..)` | generated for every unique key, e.g. `findByEmail(email)` |
| `findPage(condition, cursor, limit, orderBy…)` | a keyset-paginated `Page<E>` |
| `exists(condition)`, `count(condition)`, `count()` | |

Table policies apply to all of them. To see soft-deleted rows or rows of another
tenant, bypass the policy explicitly:

```java
UserRepository all = users.bypassing(SoftDeletePolicy.class);
```

## `BEANS`

`BEANS.get(Class)` is a small bean lookup for applications without a DI framework:

```java
QueryContext.setDefault(ctx);
UserRepository users = BEANS.get(UserRepository.class);       // created on first use, then a singleton
BEANS.register(Clock.class, Clock.systemUTC());                 // register your own objects
```

The default `SimpleBeanRegistry` creates each bean through its public constructor
with the most parameters it can satisfy:

- a `QueryContext` parameter gets `QueryContext.getDefault()`;
- other parameters get beans of their type;
- cycles are reported.

Repositories therefore need no registration.

With `lxrin-ql-spring`, `BEANS` delegates to the Spring `ApplicationContext`:
`BEANS.get(UserRepository.class)` and constructor injection return the **same** bean.
`BEANS.reset()` restores a fresh registry, for example between tests.
