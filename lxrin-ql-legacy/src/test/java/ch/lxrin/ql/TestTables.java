package ch.lxrin.ql;

import ch.lxrin.ql.table.Column;
import ch.lxrin.ql.table.TableDef;

/** Table definitions shared by the tests. */
final class TestTables {

    private TestTables() {}

    static class PersonTable extends TableDef {
        final Column personNr = column("PERSON_NR");
        final Column firstName = column("FIRST_NAME");
        final Column lastName = column("LAST_NAME");
        final Column status = column("STATUS");
        final Column age = column("AGE");

        PersonTable() {
            this("p");
        }

        PersonTable(String alias) {
            super("PERSON", alias);
        }
    }

    static class OrderTable extends TableDef {
        final Column orderId = column("ORDER_ID");
        final Column personNr = column("PERSON_NR");
        final Column total = column("TOTAL");
        final Column status = column("STATUS");
        final Column createdAt = column("CREATED_AT");
        final Column data = column("DATA");

        OrderTable() {
            super("ORDERS", "o");
        }
    }
}
