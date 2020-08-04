package org.knime.core.data.convert.map;

/**
 * A cell value consumer accepts a value and writes it to a certain destination.
 *
 * @author Jonathan Hale, KNIME, Konstanz, Germany
 * @author Marcel Wiedenmann, KNIME, Konstanz, Germany
 * @param <T> Type of value the consumer accepts
 * @since 3.6
 * @see CellValueProducer
 */
@FunctionalInterface
public interface CellValueConsumer<T> {

    /**
     * Consumes the given value and writes it to the destination.
     *
     * @param value The value to consume.
     * @throws MappingException If an exception occurs while consuming the cell value.
     */
    public void consumeCellValue(final T value) throws MappingException;
}