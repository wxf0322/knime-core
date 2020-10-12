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
 *   Oct 12, 2020 (wiswedel): created
 */
package org.knime.slf4j.binding;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author wiswedel
 */
@RunWith(Parameterized.class)
public final class LoggingTest {

    @Parameters(name="{0}")
    public static TestLevel[] createLevels() {
        return TestLevel.values();
    }

    @Parameter
    public TestLevel m_testLevel;

    @Rule
    public ExpectedLogMessage m_expectedLogMessage = ExpectedLogMessage.newInstance();

    private static final Logger KNIME_CLASS_LOGGER = LoggerFactory.getLogger(LoggingTest.class);

    @Test
    public void testMessageOnly() {
        String message = "Some simple message on " + m_testLevel.getNodeLoggerLevel().toString();
        m_expectedLogMessage.expect(m_testLevel.getNodeLoggerLevel(), message);
        m_testLevel.messageOnly().logMessage(KNIME_CLASS_LOGGER, message);
    }

    @Test
    public void testMessageAndException() {
        String message = "Message with Exception on " + m_testLevel.getNodeLoggerLevel().toString();
        Exception e = new RuntimeException("Throwable message: " + message);
        m_expectedLogMessage.expect(m_testLevel.getNodeLoggerLevel(), message, e);
        m_testLevel.messageAndException().logMessage(KNIME_CLASS_LOGGER, message, e);
    }

}
