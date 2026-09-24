package ch.lxrin.ql.spi;

/**
 * The old and new value of a changed column.
 *
 * @param oldValue the value when the entity was loaded or last saved
 * @param newValue the current value
 * @param <T>      the column type
 */
public record Change<T>(T oldValue, T newValue) {
}
