package ch.lxrin.ql.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NamesTest {

    @Test
    void caseConversions() {
        assertEquals("displayName", Names.camel("display_name"));
        assertEquals("OrderStatus", Names.pascal("order_status"));
        assertEquals("DISPLAY_NAME", Names.upperSnake("display_name"));
        assertEquals("DISPLAY_NAME", Names.upperSnake("displayName"));
        assertEquals("IN_PROGRESS", Names.upperSnake("in-progress"));
        assertEquals("_2FA", Names.upperSnake("2fa"));
        assertEquals("_2fa", Names.camel("2fa"));
        assertEquals("class_", Names.camel("class"));
        assertEquals("default_", Names.javaIdentifier("default"));
    }

    @Test
    void singularization() {
        assertEquals("user", Names.singular("users"));
        assertEquals("app_user", Names.singular("app_users"));
        assertEquals("category", Names.singular("categories"));
        assertEquals("address", Names.singular("addresses"));
        assertEquals("box", Names.singular("boxes"));
        assertEquals("match", Names.singular("matches"));
        assertEquals("status", Names.singular("statuses"));
        assertEquals("status", Names.singular("status"));
        assertEquals("person", Names.singular("people"));
        assertEquals("audit_data", Names.singular("audit_data"));
        assertEquals("asset", Names.singular("asset"));
        assertEquals("analysis", Names.singular("analysis"));
    }
}
