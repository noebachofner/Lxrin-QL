package ch.lxrin.ql.types;

/** Helpers for writing escaped SQL literals. */
public final class Literals {

    private Literals() {}

    /** Wraps text in single quotes and doubles embedded quotes. NUL characters are rejected. */
    public static String quote(String text) {
        if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("NUL character is not allowed in SQL literals");
        return "'" + text.replace("'", "''") + "'";
    }

    /** Returns a number as a literal after checking that it is a plain finite number. */
    public static String number(Number value) {
        String s = value.toString();
        if (value instanceof java.math.BigDecimal) s = ((java.math.BigDecimal) value).toPlainString();
        if (!s.matches("-?[0-9]+(\\.[0-9]+)?([eE][-+]?[0-9]+)?")) {
            throw new IllegalArgumentException("not a finite number: " + s);
        }
        return s;
    }
}
