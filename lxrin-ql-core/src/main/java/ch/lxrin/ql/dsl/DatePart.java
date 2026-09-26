package ch.lxrin.ql.dsl;

/** Fields for {@code date_trunc} and {@code EXTRACT}. */
public enum DatePart {
    /** Millennium. */
    MILLENNIUM,
    /** Century. */
    CENTURY,
    /** Decade. */
    DECADE,
    /** Year. */
    YEAR,
    /** ISO 8601 week-numbering year ({@code EXTRACT} only). */
    ISOYEAR,
    /** Quarter. */
    QUARTER,
    /** Month. */
    MONTH,
    /** ISO week. */
    WEEK,
    /** Day. */
    DAY,
    /** Day of week, Sunday = 0 ({@code EXTRACT} only). */
    DOW,
    /** Day of week, Monday = 1 ({@code EXTRACT} only). */
    ISODOW,
    /** Day of year ({@code EXTRACT} only). */
    DOY,
    /** Hour. */
    HOUR,
    /** Minute. */
    MINUTE,
    /** Second (with fractions for {@code EXTRACT}). */
    SECOND,
    /** Milliseconds. */
    MILLISECONDS,
    /** Microseconds. */
    MICROSECONDS,
    /** Seconds since 1970-01-01 00:00 UTC ({@code EXTRACT} only). */
    EPOCH;

    /** Returns the SQL name, e.g. {@code month}. */
    public String sqlName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
