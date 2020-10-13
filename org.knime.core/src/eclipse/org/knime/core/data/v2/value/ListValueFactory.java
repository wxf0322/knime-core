package org.knime.core.data.v2.value;

import org.knime.core.data.collection.ListDataValue;
import org.knime.core.data.v2.ReadValue;
import org.knime.core.data.v2.ValueFactory;
import org.knime.core.data.v2.WriteValue;
import org.knime.core.data.v2.access.ListAccess.ListAccessSpec;
import org.knime.core.data.v2.access.ListAccess.ListReadAccess;
import org.knime.core.data.v2.access.ListAccess.ListWriteAccess;

public final class ListValueFactory implements ValueFactory<ListReadAccess, ListWriteAccess> {

    @Override
    public ListAccessSpec getSpec() {
        return ListAccessSpec.INSTANCE;
    }

    @Override
    public ListReadValue createReadValue(final ListReadAccess reader) {
        return reader;
    }

    @Override
    public ListWriteValue createWriteValue(final ListWriteAccess writer) {
        return writer;
    }

    public interface ListReadValue extends ReadValue, ListDataValue {
        // TODO which values does it extend
    }

    public interface ListWriteValue extends WriteValue<ListDataValue> {
    }
}