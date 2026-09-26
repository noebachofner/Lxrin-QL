package ch.lxrin.ql.types;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqlTypesTest {

    record UserId(UUID value) {}

    @Test
    void forClassReturnsDefaultTypes() {
        assertSame(SqlTypes.TEXT, SqlTypes.forClass(String.class));
        assertSame(SqlTypes.INT8, SqlTypes.forClass(Long.class));
        assertSame(SqlTypes.TIMESTAMPTZ, SqlTypes.forClass(Instant.class));
        assertEquals("text[]", SqlTypes.forClass(String[].class).sqlName());
        assertThrows(IllegalArgumentException.class, () -> SqlTypes.forClass(Object.class));
    }

    @Test
    void mappedTypesKeepSqlNameAndChangeJavaType() {
        DataType<UserId> userId = SqlTypes.UUID.map(UserId.class, UserId::new, UserId::value);
        assertEquals("uuid", userId.sqlName());
        assertEquals(UserId.class, userId.javaType());
        assertEquals(Kind.OTHER, userId.kind());
        UUID uuid = UUID.randomUUID();
        assertEquals("UUID '" + uuid + "'", userId.access().literal(new UserId(uuid)));
    }

    @Test
    void arrayTypes() {
        DataType<String[]> tags = SqlTypes.TEXT.array();
        assertEquals("text[]", tags.sqlName());
        assertEquals(Kind.ARRAY, tags.kind());
        assertSame(SqlTypes.TEXT, tags.elementType());
        assertEquals("ARRAY['a', 'b''c']::text[]", tags.access().literal(new String[]{"a", "b'c"}));
        assertThrows(UnsupportedOperationException.class, tags::array);
    }

    @Test
    void castChecksJavaType() {
        assertEquals("x", SqlTypes.TEXT.cast("x"));
        assertNull(SqlTypes.TEXT.cast(null));
        assertThrows(IllegalArgumentException.class, () -> SqlTypes.TEXT.cast(1));
    }

    @Test
    void sensitiveCopy() {
        assertFalse(SqlTypes.TEXT.sensitive());
        assertTrue(SqlTypes.TEXT.asSensitive().sensitive());
    }

    @Test
    void literals() {
        assertEquals("'O''Brien'", SqlTypes.TEXT.access().literal("O'Brien"));
        assertEquals("12.50", SqlTypes.NUMERIC.access().literal(new BigDecimal("12.50")));
        assertEquals("DATE '2024-01-31'", SqlTypes.DATE.access().literal(LocalDate.of(2024, 1, 31)));
        assertEquals("TIMESTAMPTZ '1970-01-01T00:00Z'", SqlTypes.TIMESTAMPTZ.access().literal(Instant.EPOCH));
        assertEquals("TRUE", SqlTypes.BOOL.access().literal(true));
        assertThrows(IllegalArgumentException.class, () -> SqlTypes.FLOAT8.access().literal(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Literals.quote("a\0b"));
    }

    @Test
    void invalidTypeNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SqlTypes.pgEnum("role; drop table x", Kind.class));
        assertEquals("app.role", SqlTypes.pgEnum("app.role", Kind.class).sqlName());
    }

    @Test
    void intervalParsing() {
        assertEquals(Duration.ofDays(3), Intervals.parse("3 days"));
        assertEquals(Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusMillis(500),
                Intervals.parse("1 day 02:03:04.5"));
        assertEquals(Duration.ofSeconds(-1), Intervals.parse("-00:00:01"));
        assertEquals(Duration.ofMinutes(90), Intervals.parse("PT1H30M"));
        assertEquals(Duration.ZERO, Intervals.parse("00:00:00"));
        assertThrows(IllegalArgumentException.class, () -> Intervals.parse("1 mon"));
        assertThrows(IllegalArgumentException.class, () -> Intervals.parse("banana"));
    }

    @Test
    void uuidV7IsTimeOrderedAndUnique() {
        long before = System.currentTimeMillis();
        UUID previous = UuidV7.generate();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            UUID next = UuidV7.generate();
            assertEquals(7, next.version());
            assertEquals(2, next.variant());
            assertTrue(next.toString().compareTo(previous.toString()) > 0, "monotonic");
            assertTrue(seen.add(next));
            previous = next;
        }
        long ts = UuidV7.timestamp(previous);
        assertTrue(ts >= before && ts <= System.currentTimeMillis() + 5);
        assertThrows(IllegalArgumentException.class, () -> UuidV7.timestamp(UUID.randomUUID()));
    }
}
