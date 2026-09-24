package ch.lxrin.ql;

import ch.lxrin.ql.bind.Binds;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BindsTest {

    @Test
    void setLong_storesValue() {
        Binds b = new Binds().setLong("personNr", 42L);
        assertEquals(42L, b.get("personNr"));
    }

    @Test
    void setInt_storesValue() {
        Binds b = new Binds().setInt("age", 30);
        assertEquals(30, b.get("age"));
    }

    @Test
    void setDouble_storesValue() {
        Binds b = new Binds().setDouble("price", 9.99);
        assertEquals(9.99, b.get("price"));
    }

    @Test
    void setBigDecimal_storesValue() {
        BigDecimal bd = new BigDecimal("123.45");
        Binds b = new Binds().setBigDecimal("amount", bd);
        assertEquals(bd, b.get("amount"));
    }

    @Test
    void setString_storesValue() {
        Binds b = new Binds().setString("status", "ACTIVE");
        assertEquals("ACTIVE", b.get("status"));
    }

    @Test
    void setBoolean_storesValue() {
        Binds b = new Binds().setBoolean("active", true);
        assertEquals(true, b.get("active"));
    }

    @Test
    void setDate_storesValue() {
        LocalDate d = LocalDate.of(2024, 1, 15);
        Binds b = new Binds().setDate("startDate", d);
        assertEquals(d, b.get("startDate"));
    }

    @Test
    void setDateTime_storesValue() {
        LocalDateTime dt = LocalDateTime.of(2024, 1, 15, 10, 30);
        Binds b = new Binds().setDateTime("createdAt", dt);
        assertEquals(dt, b.get("createdAt"));
    }

    @Test
    void set_generic_storesValue() {
        Object obj = new Object();
        Binds b = new Binds().set("obj", obj);
        assertSame(obj, b.get("obj"));
    }

    @Test
    void chaining_storesMultipleValues() {
        Binds b = new Binds()
                .setLong("personNr", 1L)
                .setString("status", "ACTIVE")
                .setInt("age", 25);

        assertEquals(1L, b.get("personNr"));
        assertEquals("ACTIVE", b.get("status"));
        assertEquals(25, b.get("age"));
        assertEquals(3, b.asMap().size());
    }

    @Test
    void isEmpty_trueWhenNoValues() {
        assertTrue(new Binds().isEmpty());
    }

    @Test
    void isEmpty_falseAfterSet() {
        assertFalse(new Binds().setString("x", "y").isEmpty());
    }

    @Test
    void toBindMap_convertsAllEntries() {
        Binds b = new Binds().setLong("id", 10L).setString("name", "Alice");
        var map = b.toBindMap();
        assertEquals(10L, map.get("id"));
        assertEquals("Alice", map.get("name"));
    }

    @Test
    void set_throwsOnBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Binds().setString("", "value"));
        assertThrows(IllegalArgumentException.class, () -> new Binds().setLong(null, 1L));
    }

    // -------------------------------------------------------------------------
    // Value-only (auto-named) setters
    // -------------------------------------------------------------------------

    @Test
    void autoSetLong_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        String placeholder = b.setLong(42L);
        assertEquals(":p0", placeholder);
        assertEquals(42L, b.get("p0"));
    }

    @Test
    void autoSetInt_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        String placeholder = b.setInt(7);
        assertEquals(":p0", placeholder);
        assertEquals(7, b.get("p0"));
    }

    @Test
    void autoSetDouble_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        String placeholder = b.setDouble(3.14);
        assertEquals(":p0", placeholder);
        assertEquals(3.14, b.get("p0"));
    }

    @Test
    void autoSetBigDecimal_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        BigDecimal val = new BigDecimal("99.99");
        String placeholder = b.setBigDecimal(val);
        assertEquals(":p0", placeholder);
        assertEquals(val, b.get("p0"));
    }

    @Test
    void autoSetString_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        String placeholder = b.setString("ACTIVE");
        assertEquals(":p0", placeholder);
        assertEquals("ACTIVE", b.get("p0"));
    }

    @Test
    void autoSetBoolean_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        String placeholder = b.setBoolean(true);
        assertEquals(":p0", placeholder);
        assertEquals(true, b.get("p0"));
    }

    @Test
    void autoSetDate_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        LocalDate d = LocalDate.of(2024, 6, 1);
        String placeholder = b.setDate(d);
        assertEquals(":p0", placeholder);
        assertEquals(d, b.get("p0"));
    }

    @Test
    void autoSetDateTime_returnsPlaceholderAndStoresValue() {
        Binds b = new Binds();
        LocalDateTime dt = LocalDateTime.of(2024, 6, 1, 12, 0);
        String placeholder = b.setDateTime(dt);
        assertEquals(":p0", placeholder);
        assertEquals(dt, b.get("p0"));
    }

    @Test
    void autoSet_incrementsIndexAcrossMultipleCalls() {
        Binds b = new Binds();
        String p0 = b.setLong(1L);
        String p1 = b.setString("ACTIVE");
        String p2 = b.setInt(18);

        assertEquals(":p0", p0);
        assertEquals(":p1", p1);
        assertEquals(":p2", p2);

        assertEquals(1L,      b.get("p0"));
        assertEquals("ACTIVE", b.get("p1"));
        assertEquals(18,      b.get("p2"));
        assertEquals(3, b.asMap().size());
    }

    @Test
    void autoSet_canBeMixedWithNamedSetters() {
        Binds b = new Binds();
        String p0 = b.setString("ACTIVE");       // auto → p0
        b.setString("label", "manual");           // named
        String p1 = b.setLong(100L);             // auto → p1

        assertEquals(":p0", p0);
        assertEquals(":p1", p1);
        assertEquals("ACTIVE",  b.get("p0"));
        assertEquals("manual",  b.get("label"));
        assertEquals(100L,       b.get("p1"));
    }

    @Test
    void autoSet_eachBindsInstanceHasIndependentCounter() {
        Binds b1 = new Binds();
        Binds b2 = new Binds();
        assertEquals(":p0", b1.setLong(1L));
        assertEquals(":p0", b2.setLong(2L));   // fresh counter, not p1
        assertEquals(":p1", b1.setLong(3L));   // b1 counter continues
    }
}
