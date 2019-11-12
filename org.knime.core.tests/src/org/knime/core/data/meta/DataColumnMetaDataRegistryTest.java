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
 *   Oct 29, 2019 (Adrian Nembach, KNIME GmbH, Konstanz, Germany): created
 */
package org.knime.core.data.meta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.Test;
import org.knime.core.data.DataCell;
import org.knime.core.data.DataType;
import org.knime.core.data.def.IntCell;
import org.knime.core.data.def.StringCell;
import org.knime.core.data.meta.TestDataColumnMetaData.TestMetaDataCreator;
import org.knime.core.data.meta.TestDataColumnMetaData.TestMetaDataSerializer;
import org.knime.core.data.probability.nominal.NominalDistributionCellFactory;
import org.knime.core.data.probability.nominal.NominalDistributionValueMetaDataCreator;

/**
 * Unit tests for {@link DataColumnMetaDataRegistry}.
 *
 * @author Adrian Nembach, KNIME GmbH, Konstanz, Germany
 */
public class DataColumnMetaDataRegistryTest {

    /**
     * Tests the functionality of {@link DataColumnMetaDataRegistry#hasMetaData(org.knime.core.data.DataType)}.
     */
    @Test
    public void testHasMetaData() {
        assertTrue(
            "The registered TestMetaData applies to StringValue therefore "
                + "hasMetaData must return true for StringCell.TYPE.",
            DataColumnMetaDataRegistry.INSTANCE.hasMetaData(StringCell.TYPE));
        assertFalse("As of now there is no meta data registered for DataCell",
            DataColumnMetaDataRegistry.INSTANCE.hasMetaData(DataType.getType(DataCell.class)));
        assertFalse("As of now there is no meta data registered for the super types of IntCell",
            DataColumnMetaDataRegistry.INSTANCE.hasMetaData(IntCell.TYPE));
    }

    /**
     * Tests the functionality of {@link DataColumnMetaDataRegistry#getCreators(org.knime.core.data.DataType)}.
     */
    @Test
    public void testGetCreators() {
        final Collection<DataColumnMetaDataCreator<?>> creators =
            DataColumnMetaDataRegistry.INSTANCE.getCreators(StringCell.TYPE);
        assertEquals("There should be exactly one instance of TestMetaDataCreator", 1L,
            creators.stream().filter(c -> c instanceof TestMetaDataCreator).count());
        final Collection<DataColumnMetaDataCreator<?>> nomDistrCreators =
            DataColumnMetaDataRegistry.INSTANCE.getCreators(NominalDistributionCellFactory.TYPE);
        assertEquals("There should be exactly on instance of NominalDistributionValueMetaDataCreator", 1L,
            nomDistrCreators.stream().filter(c -> c instanceof NominalDistributionValueMetaDataCreator).count());
    }

    /**
     * Tests {@link DataColumnMetaDataRegistry#getSerializer(Class)}.
     */
    @Test
    public void testGetSerializerWithClass() {
        final Optional<DataColumnMetaDataSerializer<TestDataColumnMetaData>> serializer =
            DataColumnMetaDataRegistry.INSTANCE.getSerializer(TestDataColumnMetaData.class);
        assertTrue(serializer.isPresent());
        assertThat(serializer.get(), Matchers.instanceOf(TestMetaDataSerializer.class));
    }

    /**
     * Tests {@link DataColumnMetaDataRegistry#getSerializer(String)}.
     */
    @Test
    public void testGetSerializerWithClassName() {
        final Optional<DataColumnMetaDataSerializer<?>> serializer =
            DataColumnMetaDataRegistry.INSTANCE.getSerializer(TestDataColumnMetaData.class.getName());
        assertTrue(serializer.isPresent());
        assertThat(serializer.get(), Matchers.instanceOf(TestMetaDataSerializer.class));
    }
}