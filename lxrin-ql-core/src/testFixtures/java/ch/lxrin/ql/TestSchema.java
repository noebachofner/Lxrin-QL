package ch.lxrin.ql;

import ch.lxrin.ql.dsl.Row;
import ch.lxrin.ql.schema.ArrayColumn;
import ch.lxrin.ql.schema.BooleanColumn;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.ForeignKey;
import ch.lxrin.ql.schema.JsonColumn;
import ch.lxrin.ql.schema.KeyStrategy;
import ch.lxrin.ql.schema.NumberColumn;
import ch.lxrin.ql.schema.PrimaryKey;
import ch.lxrin.ql.schema.RangeColumn;
import ch.lxrin.ql.schema.StringColumn;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.schema.TemporalColumn;
import ch.lxrin.ql.schema.TsVectorColumn;
import ch.lxrin.ql.schema.UniqueKey;
import ch.lxrin.ql.types.DataType;
import ch.lxrin.ql.types.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Hand-written tables in the shape of generated code, used by the unit tests. */
public final class TestSchema {

    private TestSchema() {}

    public enum Role { ADMIN, USER }

    public static final DataType<Role> ROLE_TYPE = SqlTypes.pgEnum("role", Role.class);

    public record UserRow(UUID id, String name, String email, Role role, boolean active, Instant createdAt,
                          Instant deletedAt, String[] tags, String settings, Long version) {}

    public static final class UsersTable extends Table<UserRow> {
        public static final UsersTable USERS = new UsersTable(null);

        public final Column<UUID> ID = otherColumn("id", SqlTypes.UUID, Column.NOT_NULL | Column.PRIMARY_KEY);
        public final StringColumn NAME = stringColumn("name", SqlTypes.TEXT, Column.NOT_NULL);
        public final StringColumn EMAIL = stringColumn("email", SqlTypes.TEXT, Column.NOT_NULL);
        public final Column<Role> ROLE = otherColumn("role", ROLE_TYPE, Column.NOT_NULL | Column.DEFAULT);
        public final BooleanColumn ACTIVE = booleanColumn("active", SqlTypes.BOOL, Column.NOT_NULL | Column.DEFAULT);
        public final TemporalColumn<Instant> CREATED_AT = temporalColumn("created_at", SqlTypes.TIMESTAMPTZ, Column.NOT_NULL | Column.DEFAULT);
        public final TemporalColumn<Instant> DELETED_AT = temporalColumn("deleted_at", SqlTypes.TIMESTAMPTZ, 0);
        public final ArrayColumn<String> TAGS = arrayColumn("tags", SqlTypes.TEXT.array(), Column.NOT_NULL | Column.DEFAULT);
        public final JsonColumn<String> SETTINGS = jsonColumn("settings", SqlTypes.JSONB, 0);
        public final NumberColumn<Long> VERSION = numberColumn("version", SqlTypes.INT8, Column.NOT_NULL | Column.DEFAULT);

        public final PrimaryKey<UUID> PK = primaryKey("users_pkey", ID, KeyStrategy.uuidV7());
        public final UniqueKey UK_EMAIL = uniqueKey("users_email_key", EMAIL);

        public UsersTable(String alias) {
            super(null, "users", alias);
        }

        @Override
        public UsersTable as(String alias) {
            return new UsersTable(alias);
        }

        @Override
        public UserRow mapRow(Row r) {
            return new UserRow(r.get(ID), r.get(NAME), r.get(EMAIL), r.get(ROLE), r.get(ACTIVE), r.get(CREATED_AT),
                    r.get(DELETED_AT), r.get(TAGS), r.get(SETTINGS), r.get(VERSION));
        }
    }

    public record OrderRow(Long id, UUID userId, BigDecimal total, String status, LocalDate orderedOn) {}

    public static final class OrdersTable extends Table<OrderRow> {
        public static final OrdersTable ORDERS = new OrdersTable(null);

        public final NumberColumn<Long> ID = numberColumn("id", SqlTypes.INT8, Column.NOT_NULL | Column.PRIMARY_KEY | Column.IDENTITY);
        public final Column<UUID> USER_ID = otherColumn("user_id", SqlTypes.UUID, Column.NOT_NULL);
        public final NumberColumn<BigDecimal> TOTAL = numberColumn("total", SqlTypes.NUMERIC, Column.NOT_NULL);
        public final StringColumn STATUS = stringColumn("status", SqlTypes.TEXT, Column.NOT_NULL);
        public final TemporalColumn<LocalDate> ORDERED_ON = temporalColumn("ordered_on", SqlTypes.DATE, Column.NOT_NULL);

        public final PrimaryKey<Long> PK = primaryKey("orders_pkey", ID, KeyStrategy.sequence("orders_id_seq"));
        public final ForeignKey FK_USER = foreignKey("orders_user_id_fkey", List.of(USER_ID), null, "users", List.of("id"));

        public OrdersTable(String alias) {
            super(null, "orders", alias);
        }

        @Override
        public OrdersTable as(String alias) {
            return new OrdersTable(alias);
        }

        @Override
        public OrderRow mapRow(Row r) {
            return new OrderRow(r.get(ID), r.get(USER_ID), r.get(TOTAL), r.get(STATUS), r.get(ORDERED_ON));
        }
    }

    public record BookingRow(Long id, String period, String search, Integer[] slots) {}

    public static final class BookingsTable extends Table<BookingRow> {
        public static final BookingsTable BOOKINGS = new BookingsTable(null);

        public final NumberColumn<Long> ID = numberColumn("id", SqlTypes.INT8, Column.NOT_NULL | Column.PRIMARY_KEY);
        public final RangeColumn PERIOD = rangeColumn("period", SqlTypes.TSTZRANGE, Column.NOT_NULL);
        public final TsVectorColumn SEARCH = tsvectorColumn("search", SqlTypes.TSVECTOR, 0);
        public final ArrayColumn<Integer> SLOTS = arrayColumn("slots", SqlTypes.INT4.array(), 0);

        public final PrimaryKey<Long> PK = primaryKey("bookings_pkey", ID, KeyStrategy.none());

        public BookingsTable(String alias) {
            super(null, "bookings", alias);
        }

        @Override
        public BookingsTable as(String alias) {
            return new BookingsTable(alias);
        }

        @Override
        public BookingRow mapRow(Row r) {
            return new BookingRow(r.get(ID), r.get(PERIOD), r.get(SEARCH), r.get(SLOTS));
        }
    }
}
