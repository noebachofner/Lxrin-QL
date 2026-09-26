package ch.lxrin.ql.codegen;

import ch.lxrin.ql.codegen.SchemaModel.ColumnModel;
import ch.lxrin.ql.codegen.SchemaModel.EnumModel;
import ch.lxrin.ql.codegen.SchemaModel.TableModel;

/**
 * The default naming rules:
 * <ul>
 *   <li>configured table prefixes are stripped ({@code app_user} → {@code user});</li>
 *   <li>entity names are singular and PascalCase ({@code users} → {@code User});</li>
 *   <li>table constants are the UPPER_SNAKE table name ({@code users} → {@code USERS});</li>
 *   <li>column constants are UPPER_SNAKE, properties camelCase;</li>
 *   <li>per-table overrides from the configuration win.</li>
 * </ul>
 */
public class DefaultNamingStrategy implements NamingStrategy {

    private final CodegenConfig config;

    /** Creates the strategy for a configuration. */
    public DefaultNamingStrategy(CodegenConfig config) {
        this.config = config;
    }

    @Override
    public String entityName(TableModel table) {
        String override = config.entityNames().get(table.name());
        if (override != null) return override;
        String base = stripped(table.name());
        return Names.pascal(config.singularize() ? Names.singular(base) : base);
    }

    @Override
    public String tableConstant(TableModel table) {
        String override = config.tableConstants().get(table.name());
        return override != null ? override : Names.upperSnake(stripped(table.name()));
    }

    @Override
    public String columnConstant(ColumnModel column) {
        return Names.upperSnake(column.name());
    }

    @Override
    public String property(ColumnModel column) {
        return Names.camel(column.name());
    }

    @Override
    public String enumName(EnumModel type) {
        return Names.pascal(type.name());
    }

    @Override
    public String enumConstant(String label) {
        return Names.upperSnake(label);
    }

    /** Returns the table name without a configured prefix. */
    protected String stripped(String table) {
        for (String prefix : config.stripTablePrefixes()) {
            if (table.startsWith(prefix) && table.length() > prefix.length()) return table.substring(prefix.length());
        }
        return table;
    }
}
