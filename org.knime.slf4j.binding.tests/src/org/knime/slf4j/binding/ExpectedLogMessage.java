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

import java.io.Writer;
import java.util.Objects;

import org.apache.commons.io.output.NullWriter;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.knime.core.node.NodeLogger;
import org.knime.core.node.NodeLogger.LEVEL;

/**
 *
 * @author wiswedel
 */
final class ExpectedLogMessage implements TestRule {

    private LEVEL m_level;
    private String m_message;
    private Throwable m_throwable;
    private final Writer m_writer;

    private boolean m_hasSeenLogEvent;

    private ExpectedLogMessage() {
        m_writer = new NullWriter();
        NodeLogger.addWriter(m_writer, new LayoutExtension(), LEVEL.DEBUG, LEVEL.FATAL);
    }

    void expect(final LEVEL level, final String message) {
        expect(level, message, null);
    }

    void expect(final LEVEL level, final String message, final Throwable throwable) {
        m_level = Objects.requireNonNull(level);
        m_message = Objects.requireNonNull(message);
        m_throwable = throwable;
    }

    static ExpectedLogMessage newInstance() {
        return new ExpectedLogMessage();
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        return new ExpectedLogMessagesStatement(base);
    }

    private final class LayoutExtension extends Layout {

        @Override
        public void activateOptions() {
            // no op
        }

        @Override
        public boolean ignoresThrowable() {
            return false;
        }

        @Override
        public String format(final LoggingEvent log) {
            LEVEL nodeLoggerLevel;
            switch (log.getLevel().toInt()) {
                case Level.TRACE_INT:
                    nodeLoggerLevel = LEVEL.DEBUG;
                    break;
                case Priority.DEBUG_INT:
                    nodeLoggerLevel = LEVEL.DEBUG;
                    break;
                case Priority.INFO_INT:
                    nodeLoggerLevel = LEVEL.INFO;
                    break;
                case Priority.WARN_INT:
                    nodeLoggerLevel = LEVEL.WARN;
                    break;
                case Priority.ERROR_INT:
                    nodeLoggerLevel = LEVEL.ERROR;
                    break;
                case Priority.FATAL_INT:
                    nodeLoggerLevel = LEVEL.FATAL;
                    break;
                default:
                    throw new IllegalStateException("unsupported level :" + log.getLevel());
            }
            Throwable throwableFromLog =
                log.getThrowableInformation() != null ? log.getThrowableInformation().getThrowable() : null;
            if (nodeLoggerLevel == m_level && Objects.equals(m_message, Objects.toString(log.getMessage()))
                && Objects.equals(throwableFromLog, m_throwable)) {
                m_hasSeenLogEvent = true;
            }
            return String.format("%s: %s", nodeLoggerLevel, log.getMessage());
        }
    }

    private class ExpectedLogMessagesStatement extends Statement {
        private final Statement m_next;

        public ExpectedLogMessagesStatement(final Statement base) {
            m_next = base;
        }

        @Override
        public void evaluate() throws Throwable {
            try {
                m_next.evaluate();
                if (m_message != null && !m_hasSeenLogEvent) {
                    Assert.fail(String.format("Expected %s log message with message text \"%s\" was not logged",
                        m_level, m_message));
                }
            } finally {
                NodeLogger.removeWriter(m_writer);
            }

        }
    }


}
