/*
 * ------------------------------------------------------------------------
 *
 *  Copyright by KNIME AG, Zurich, Switzerland
 *  Website: http://www.knime.com; Email: contact@knime.com
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License, Version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 *  Additional permission under GNU GPL version 3 section 7:
 *
 *  KNIME interoperates with ECLIPSE solely via ECLIPSE's plug-in APIs.
 *  Hence, KNIME and ECLIPSE are both independent programs and are not
 *  derived from each other. Should, however, the interpretation of the
 *  GNU GPL Version 3 ("License") under any applicable laws result in
 *  KNIME and ECLIPSE being a combined program, KNIME AG herewith grants
 *  you the additional permission to use and propagate KNIME together with
 *  ECLIPSE with only the license terms in place for ECLIPSE applying to
 *  ECLIPSE and the GNU GPL Version 3 applying for KNIME, provided the
 *  license terms of ECLIPSE themselves allow for the respective use and
 *  propagation of ECLIPSE together with KNIME.
 *
 *  Additional permission relating to nodes for KNIME that extend the Node
 *  Extension (and in particular that are based on subclasses of NodeModel,
 *  NodeDialog, and NodeView) and that only interoperate with KNIME through
 *  standard APIs ("Nodes"):
 *  Nodes are deemed to be separate and independent programs and to not be
 *  covered works.  Notwithstanding anything to the contrary in the
 *  License, the License does not apply to Nodes, you are not required to
 *  license Nodes under the License, and you are granted a license to
 *  prepare and propagate Nodes, in each case even if such Nodes are
 *  propagated with or for interoperation with KNIME.  The owner of a Node
 *  may freely choose the license terms applicable to such Node, including
 *  when such Node is propagated with or for interoperation with KNIME.
 * ---------------------------------------------------------------------
 *
 * History
 *   May 31, 2020 (dietzc): created
 */
package org.knime.core.data.container;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.knime.core.data.DataCell;
import org.knime.core.data.DataRow;
import org.knime.core.data.DataTableSpec;
import org.knime.core.data.DataType;
import org.knime.core.data.DataValue;
import org.knime.core.data.DoubleValue;
import org.knime.core.data.IntValue;
import org.knime.core.data.LongValue;
import org.knime.core.data.NominalValue;
import org.knime.core.data.RowCursor;
import org.knime.core.data.StringValue;
import org.knime.core.data.def.DoubleCell;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.LongCell;
import org.knime.core.data.def.StringCell;

/**
 * Fallback implementation of {@link RowCursor} based on {@link DataCell}s.
 *
 * @author Christian Dietz, KNIME GmbH, Konstanz
 * @since 4.2
 */
final class FallbackRowCursor implements RowCursor {

    private final CloseableRowIterator m_delegate;

    private final DataValue[] m_proxies;

    private DataRow m_currentRow;

   FallbackRowCursor(final CloseableRowIterator delegate, final DataTableSpec spec) {
        m_delegate = delegate;
        m_proxies = new DataValue[spec.getNumColumns()];
        for (int i = 0; i < m_proxies.length; i++) {
            m_proxies[i] = createProxyForType(spec.getColumnSpec(i).getType(), i);
        }
    }

    @Override
    public boolean canFwd() {
        return m_delegate.hasNext();
    }

    @Override
    public void fwd() {
        m_currentRow = m_delegate.next();
    }

    @Override
    public void close() throws Exception {
        m_delegate.close();
    }

    @Override
    public String getRowKey() {
        return m_currentRow.getKey().getString();
    }

    @Override
    public <D extends DataValue> D getValue(final int index) {
        @SuppressWarnings("unchecked")
        final D value = (D)m_proxies[index];
        return value;
    }

    @Override
    public boolean isMissing(final int index) {
        return m_currentRow.getCell(index).isMissing();
    }

    @Override
    public int getNumColumns() {
        return m_proxies.length;
    }

    private DataValue createProxyForType(final DataType type, final int index) {
        // TODO more extensible implementation
        if (IntCell.TYPE == type) {
            return new ProxyIntCellType(index, this);
        } else if (DoubleCell.TYPE == type) {
            return new ProxyDoubleCellType(index, this);
        } else if (StringCell.TYPE == type) {
            return new ProxyStringCellType(index, this);
        } else if (LongCell.TYPE == type) {
            return new ProxyLongCellType(index, this);
        } else {
            final Class<?>[] valueClasses = type.getValueClasses().toArray(new Class[0]);
            return (DataValue)Proxy.newProxyInstance(type.getClass().getClassLoader(), valueClasses,
                new DataCellProxyInvocationHandler(index, this));
        }
    }

    /*
     *
     * Proxy type implementations
     *
     * TODO add all missing interfaces for CellImplementations.
     * TODO abstract class for m_index / m_ref etc.
     *
     */
    private final static class DataCellProxyInvocationHandler implements InvocationHandler {
        private final FallbackRowCursor m_ref;

        private final int m_index;

        public DataCellProxyInvocationHandler(final int index, final FallbackRowCursor ref) {
            m_index = index;
            m_ref = ref;
        }

        @Override
        public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
            // TODO: Exception handling. Reflective access may throw more types of exceptions than users of the proxy
            // would expect.
            return method.invoke(m_ref.m_currentRow.getCell(m_index), args);
        }
    }

    private final static class ProxyDoubleCellType implements DoubleValue {
        private final FallbackRowCursor m_ref;

        private final int m_index;

        ProxyDoubleCellType(final int index, final FallbackRowCursor ref) {
            m_index = index;
            m_ref = ref;
        }

        @Override
        public double getDoubleValue() {
            return ((DoubleValue)m_ref.m_currentRow.getCell(m_index)).getDoubleValue();
        }
    }

    private final static class ProxyIntCellType implements IntValue, DoubleValue {
        private final FallbackRowCursor m_ref;

        private final int m_index;

        ProxyIntCellType(final int index, final FallbackRowCursor ref) {
            m_index = index;
            m_ref = ref;
        }

        @Override
        public int getIntValue() {
            return ((IntValue)m_ref.m_currentRow.getCell(m_index)).getIntValue();
        }

        @Override
        public double getDoubleValue() {
            return ((DoubleValue)m_ref.m_currentRow.getCell(m_index)).getDoubleValue();
        }
    }

    private final static class ProxyStringCellType implements StringValue, NominalValue {
        private final FallbackRowCursor m_ref;

        private final int m_index;

        ProxyStringCellType(final int index, final FallbackRowCursor ref) {
            m_index = index;
            m_ref = ref;
        }

        @Override
        public String getStringValue() {
            return ((StringValue)m_ref.m_currentRow.getCell(m_index)).getStringValue();
        }
    }

    private final static class ProxyLongCellType implements LongValue, DoubleValue {
        private final FallbackRowCursor m_ref;

        private final int m_index;

        ProxyLongCellType(final int index, final FallbackRowCursor ref) {
            m_index = index;
            m_ref = ref;
        }

        @Override
        public double getDoubleValue() {
            return ((DoubleValue)m_ref.m_currentRow.getCell(m_index)).getDoubleValue();
        }

        @Override
        public long getLongValue() {
            return ((LongValue)m_ref.m_currentRow.getCell(m_index)).getLongValue();
        }
    }

}
