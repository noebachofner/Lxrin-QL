package com.example.shop;

import ch.lxrin.ql.dsl.Select2;

import java.math.BigDecimal;

import static ch.lxrin.ql.dsl.Dsl.*;
import static com.example.shop.db.Tables.*;

/** Queries against the generated tables. */
public final class ShopQueries {

    private ShopQueries() {}

    /** Customers with orders above a total. */
    public static Select2<String, BigDecimal> bigSpenders(BigDecimal minimum) {
        return select(CUSTOMERS.NAME, sum(ORDERS.TOTAL))
                .from(CUSTOMERS)
                .join(ORDERS).onKey(ORDERS.FK_CUSTOMER)
                .groupBy(CUSTOMERS.NAME)
                .having(sum(ORDERS.TOTAL).gt(minimum));
    }
}
