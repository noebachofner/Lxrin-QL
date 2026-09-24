package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

/**
 * Derives Java names from database names. {@link DefaultNamingStrategy}
 * implements the default rules; implement this interface to replace them
 * completely.
 */
public interface NamingStrategy {

    /** The entity class name, e.g. {@code app_user} → {@code User}. Row, table and repository names derive from it. */
    String entityName(TableModel table);

    /** The table constant, e.g. {@code users} → {@code USERS}. */
    String tableConstant(TableModel table);

    /** The column constant in the table class, e.g. {@code display_name} → {@code DISPLAY_NAME}. */
    String columnConstant(ColumnModel column);

    /** The property name in rows and entities, e.g. {@code display_name} → {@code displayName}. */
    String property(ColumnModel column);

    /** The Java enum name of a PostgreSQL enum, e.g. {@code order_status} → {@code OrderStatus}. */
    String enumName(EnumModel type);

    /** The constant of an enum label, e.g. {@code in-progress} → {@code IN_PROGRESS}. */
    String enumConstant(String label);
}
