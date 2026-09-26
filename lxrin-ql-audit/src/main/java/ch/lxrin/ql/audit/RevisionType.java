package ch.lxrin.ql.audit;

/** The kind of change in an audit row, stored as in Hibernate Envers ({@code revtype} 0, 1, 2). */
public enum RevisionType {
    /** The row was inserted. */
    ADD((short) 0),
    /** The row was updated. */
    MOD((short) 1),
    /** The row was deleted, or soft-deleted. */
    DEL((short) 2);

    private final short code;

    RevisionType(short code) {
        this.code = code;
    }

    /** Returns the stored value: 0, 1 or 2. */
    public short code() {
        return code;
    }

    /** Returns the type of a stored value. */
    public static RevisionType of(int code) {
        for (RevisionType t : values()) if (t.code == code) return t;
        throw new IllegalArgumentException("unknown revision type " + code);
    }
}
