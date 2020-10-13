package org.knime.core.data.v2.value;

import org.knime.core.data.collection.ListDataValue;
import org.knime.core.data.v2.ReadValue;
import org.knime.core.data.v2.ValueFactory;
import org.knime.core.data.v2.WriteValue;
import org.knime.core.data.v2.access.DoubleListAccess.DoubleListAccessSpec;
import org.knime.core.data.v2.access.DoubleListAccess.DoubleListReadAccess;
import org.knime.core.data.v2.access.DoubleListAccess.DoubleListWriteAccess;

public final class DoubleListValueFactory implements ValueFactory<DoubleListReadAccess, DoubleListWriteAccess> {

    @Override
    public DoubleListAccessSpec getSpec() {
        return DoubleListAccessSpec.INSTANCE;
    }

    @Override
    public DoubleListReadValue createReadValue(final DoubleListReadAccess reader) {
        return reader;
    }

    @Override
    public DoubleListWriteValue createWriteValue(final DoubleListWriteAccess writer) {
        return writer;
    }

    public interface DoubleListReadValue extends ReadValue, ListDataValue {
        // TODO which values does it extend
    }

    public interface DoubleListWriteValue extends WriteValue<ListDataValue> {
        void setDoubleListValue(double[] value);
    }
}