package ch.lxrin.ql.table;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ch.lxrin.ql.LxrinQL.*;
import static org.junit.jupiter.api.Assertions.*;

class TableDefTest {

    static class ProductTable extends TableDef {
        public final Column productNr = column("PRODUCT_NR");
        public final Column name = column("NAME");
        public final Column price = column("PRICE");
        public final Column statusCode = column("STATUS_CD", "statusCode");

        public ProductTable() {
            super("PRODUCT", "p");
        }
    }

    private final ProductTable products = new ProductTable();

    @Test
    void tableDef_rendersFromFragment() {
        assertEquals("PRODUCT p", products.toFromSql());
        assertEquals("PRODUCT", products.getTableName());
        assertEquals("p", products.getAlias());
    }

    @Test
    void tableDef_rejectsBlankNames() {
        assertThrows(IllegalArgumentException.class, () -> new TableDef("", "p") {});
        assertThrows(IllegalArgumentException.class, () -> new TableDef("PRODUCT", "") {});
    }

    @Test
    void column_knowsQualifiedNameUnqualifiedNameAndAlias() {
        assertEquals("p.PRODUCT_NR", products.productNr.toSql());
        assertEquals("PRODUCT_NR", products.productNr.getName());
        assertEquals("productNr", products.productNr.getAlias());
        assertSame(products, products.productNr.getTable());
        assertEquals("p.PRODUCT_NR", products.productNr.toString());
    }

    @Test
    void column_explicitAlias() {
        assertEquals("p.STATUS_CD", products.statusCode.toSql());
        assertEquals("statusCode", products.statusCode.getAlias());
    }

    @Test
    void columns_areListedInDeclarationOrder() {
        assertEquals(List.of(products.productNr, products.name, products.price, products.statusCode), products.columns());
    }

    @Test
    void all_rendersAliasAsterisk() {
        assertEquals("SELECT p.* FROM PRODUCT p", select(products.all()).from(products).buildSql());
    }

    @Test
    void adHocTable_reusesColumns() {
        Table a = table("ADDRESS", "a");
        assertSame(a.col("CITY"), a.col("CITY"));
        assertEquals("a.ZIP_CODE", a.col("ZIP_CODE").toSql());
        assertEquals("zipCode", a.col("ZIP_CODE").getAlias());
    }

    @Test
    void toCamelCase_convertsUpperSnakeCase() {
        assertEquals("productNr", TableDef.toCamelCase("PRODUCT_NR"));
        assertEquals("firstName", TableDef.toCamelCase("FIRST_NAME"));
        assertEquals("id", TableDef.toCamelCase("ID"));
        assertEquals("a", TableDef.toCamelCase("A_"));
    }
}
