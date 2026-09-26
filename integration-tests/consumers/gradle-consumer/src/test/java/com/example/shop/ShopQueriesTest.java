package com.example.shop;

import com.example.shop.db.Customer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopQueriesTest {

    @Test
    void rendersTypedSql() {
        assertEquals("SELECT shop_customers.name, sum(shop_orders.total) FROM shop_customers JOIN shop_orders "
                        + "ON shop_orders.customer_id = shop_customers.id GROUP BY shop_customers.name HAVING sum(shop_orders.total) > ?",
                ShopQueries.bigSpenders(BigDecimal.TEN).render().sql());
    }

    @Test
    void entitiesAreGenerated() {
        Customer customer = new Customer();
        customer.setName("Ada");
        assertTrue(customer.isNew());
        assertTrue(customer.isChanged());
    }
}
