package ch.lxrin.ql.exec;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResultMappingTest {

    enum Status { ACTIVE, INACTIVE }

    public static class Bean {
        private Long personNr;
        String firstName;
        private Status status;

        public void setPersonNr(Long personNr) { this.personNr = personNr; }
        public void setStatus(Status status) { this.status = status; }
    }

    record Rec(int id, String name, LocalDate day) {}

    @Test
    void objectArrayReturnsRawRow() {
        Object[] row = {1, "a"};
        assertSame(row, ResultMapping.forType(Object[].class, List.of()).map(row));
    }

    @Test
    void scalarTypesUseFirstColumn() {
        assertEquals(5L, ResultMapping.forType(Long.class, List.of()).map(new Object[]{5}));
        assertEquals(new BigDecimal("2.5"), ResultMapping.forType(BigDecimal.class, List.of()).map(new Object[]{2.5d}));
        assertEquals("7", ResultMapping.forType(String.class, List.of()).map(new Object[]{7}));
        assertEquals(Status.ACTIVE, ResultMapping.forType(Status.class, List.of()).map(new Object[]{"ACTIVE"}));
        assertNull(ResultMapping.forType(Integer.class, List.of()).map(new Object[]{null}));
    }

    @Test
    void beansAreFilledBySetterOrFieldIgnoringCaseAndUnderscores() {
        Bean b = ResultMapping.forType(Bean.class, Arrays.asList("personNr", "FIRST_NAME", "status", null))
                .map(new Object[]{3, "Ada", "INACTIVE", "ignored"});
        assertEquals(3L, b.personNr);
        assertEquals("Ada", b.firstName);
        assertEquals(Status.INACTIVE, b.status);
    }

    @Test
    void beanWithoutMatchingNamesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ResultMapping.forType(Bean.class, Arrays.asList("x", null)));
    }

    @Test
    void recordsAreBuiltByPosition() {
        Rec r = ResultMapping.forType(Rec.class, List.of()).map(new Object[]{1L, "a", java.sql.Date.valueOf("2024-01-02")});
        assertEquals(new Rec(1, "a", LocalDate.of(2024, 1, 2)), r);
        assertThrows(IllegalStateException.class, () -> ResultMapping.forType(Rec.class, List.of()).map(new Object[]{1}));
    }

    @Test
    void convertsTemporalAndOtherTypes() {
        LocalDateTime ldt = LocalDateTime.of(2024, 1, 2, 3, 4);
        assertEquals(ldt, ResultMapping.convert(Timestamp.valueOf(ldt), LocalDateTime.class));
        assertEquals(LocalDate.of(2024, 1, 2), ResultMapping.convert(ldt, LocalDate.class));
        OffsetDateTime odt = OffsetDateTime.of(ldt, ZoneOffset.UTC);
        assertEquals(odt.toInstant(), ResultMapping.convert(odt, java.time.Instant.class));
        UUID id = UUID.randomUUID();
        assertEquals(id, ResultMapping.convert(id.toString(), UUID.class));
        assertArrayEquals(new Long[]{1L, 2L}, ResultMapping.convert(new Integer[]{1, 2}, Long[].class));
        assertEquals(List.of("a", "b"), ResultMapping.convert(new String[]{"a", "b"}, List.class));
        assertEquals(0, (int) ResultMapping.convert(null, int.class));
        assertFalse(ResultMapping.convert(null, boolean.class));
    }
}
