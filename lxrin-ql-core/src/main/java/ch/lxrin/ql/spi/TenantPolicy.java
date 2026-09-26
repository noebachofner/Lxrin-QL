package ch.lxrin.ql.spi;

import ch.lxrin.ql.dsl.Condition;
import ch.lxrin.ql.schema.Column;
import ch.lxrin.ql.schema.Table;
import ch.lxrin.ql.types.DataType;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Tenant isolation through a column such as {@code organization_id}: reads,
 * updates and deletes only see rows of the current tenant, inserts always
 * get the current tenant, changing the tenant column is rejected, and so is
 * {@code TRUNCATE}.
 *
 * @param <T> the tenant id type
 */
public class TenantPolicy<T> implements TablePolicy {

    private final String column;
    private final DataType<T> type;
    private final Supplier<T> currentTenant;

    /**
     * @param column        the tenant column name
     * @param type          its type
     * @param currentTenant supplies the tenant of the current request; must not return {@code null}
     */
    public TenantPolicy(String column, DataType<T> type, Supplier<T> currentTenant) {
        this.column = Objects.requireNonNull(column, "column");
        this.type = Objects.requireNonNull(type, "type");
        this.currentTenant = Objects.requireNonNull(currentTenant, "currentTenant");
    }

    @Override
    public boolean appliesTo(Table<?> table) {
        return table.column(column).filter(c -> c.type().javaType() == type.javaType()).isPresent();
    }

    @Override
    public Condition filter(Table<?> table, PolicyContext context) {
        return table.column(column, type).eq(tenant());
    }

    @Override
    public void onInsert(InsertContext context) {
        context.forceValue(context.table().column(column, type), tenant());
    }

    @Override
    public void onUpdate(UpdateContext context) {
        Column<T> c = context.table().column(column, type);
        if (context.statement().assignments().containsKey(c)) {
            context.reject("the tenant column " + column + " of " + context.table().qualifiedName() + " cannot be changed");
        }
    }

    @Override
    public void onTruncate(TruncateContext context) {
        context.reject("TRUNCATE is not allowed on tenant tables; it would remove the rows of all tenants");
    }

    private T tenant() {
        T tenant = currentTenant.get();
        if (tenant == null) throw new IllegalStateException("no current tenant for TenantPolicy(" + column + ")");
        return tenant;
    }
}
