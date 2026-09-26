package ch.lxrin.ql.maven;

/**
 * A forced type in the Maven configuration:
 * <pre>
 * &lt;forcedTypes&gt;
 *   &lt;forcedType&gt;
 *     &lt;tables&gt;app_user&lt;/tables&gt;&lt;columns&gt;id&lt;/columns&gt;&lt;sqlTypes&gt;uuid&lt;/sqlTypes&gt;
 *     &lt;javaType&gt;com.example.UserId&lt;/javaType&gt;&lt;dataType&gt;com.example.Types.USER_ID&lt;/dataType&gt;
 *   &lt;/forcedType&gt;
 * &lt;/forcedTypes&gt;
 * </pre>
 */
public class ForcedType {

    /** Regular expression for table names. */
    public String tables = ".*";
    /** Regular expression for column names. */
    public String columns;
    /** Regular expression for SQL type names. */
    public String sqlTypes = ".*";
    /** Fully qualified Java type. */
    public String javaType;
    /** Java expression of its DataType. */
    public String dataType;
}
