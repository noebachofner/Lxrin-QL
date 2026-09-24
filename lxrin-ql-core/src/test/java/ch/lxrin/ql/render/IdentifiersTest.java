package ch.lxrin.ql.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IdentifiersTest {

    @Test
    void plainNamesAreNotQuoted() {
        assertEquals("users", Identifiers.quote("users"));
        assertEquals("created_at", Identifiers.quote("created_at"));
    }

    @Test
    void reservedMixedCaseAndSpecialNamesAreQuoted() {
        assertEquals("\"user\"", Identifiers.quote("user"));
        assertEquals("\"Order\"", Identifiers.quote("Order"));
        assertEquals("\"first name\"", Identifiers.quote("first name"));
        assertEquals("\"a\"\"b\"", Identifiers.quote("a\"b"));
    }

    @Test
    void invalidNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Identifiers.quote(""));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.quote(null));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.quote("a\0b"));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.quote("x".repeat(64)));
    }

    @Test
    void functionNames() {
        assertEquals("my_schema.calc", Identifiers.requireFunctionName("my_schema.calc"));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireFunctionName("f(); drop"));
        assertThrows(IllegalArgumentException.class, () -> Identifiers.requireFunctionName("\"quoted\""));
    }
}
