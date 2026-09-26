package ch.lxrin.ql.runtime;

/** How a transactional block relates to a transaction that is already active. */
public enum Propagation {
    /** Join the active transaction, or start one. */
    REQUIRED,
    /** Always start a new transaction; the active one is suspended. */
    REQUIRES_NEW,
    /** Run in a savepoint of the active transaction, or start one. */
    NESTED,
    /** Join the active transaction; fail if there is none. */
    MANDATORY
}
