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
 *   Sep 21, 2020 (hornm): created
 */
package org.knime.core.node.workflow;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * TODO update
 *
 * Represents node properties that depend on other nodes in the workflow graph (i.e. successors or predecessors). This
 * class helps to be able to calculate those properties in one go and caches them for subsequent usage until they are
 * invalidated.
 *
 * Dependent node properties are {@link #hasExecutablePredecessors()} and {@link #hasExecutingSuccessors()}.
 *
 * TODO comment TEMPORARY!!
 *
 * @noreference This class is not intended to be referenced by clients.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @since 4.3
 */
public final class DependentNodeProperties {

    // all nodes including the nc parent (metanode or component; unless this is the project workflow)
    private final Map<NodeID, Properties> m_props = new HashMap<>();

    private final Properties m_parentProps = new Properties(null);

    private final WorkflowManager m_wfm;

    /**
     * @param wfm the 'dependent properties' of the nodes contained in the given workflow manager will be updated
     */
    DependentNodeProperties(final WorkflowManager wfm) {
        m_wfm = wfm;
        m_props.put(wfm.getID(), m_parentProps);
    }

    /**
     * TODO
     *
     * @param id
     * @return
     */
    public boolean canExecuteNode(final NodeID id) {
        return m_wfm.canExecuteNode(id, i -> m_props.get(i).hasExecutablePredecessors());
    }

    /**
     * TODO
     *
     * @param id
     * @return
     */
    public boolean canResetNode(final NodeID id) {
        return m_wfm.canResetNode(id, i -> m_props.get(i).hasExecutingSuccessors());
    }

    /**
     * TODO
     */
    void update() {
        try (WorkflowLock lock = m_wfm.lock()) {
            boolean reset = initParentProperties(m_wfm);
            Collection<NodeContainer> nodes = m_wfm.getNodeContainers();
            reset |= initAndResetNodeProperties(nodes, m_props);
            if (m_props.size() > nodes.size() + 1) {
                removeSurplusNodes(m_wfm, m_props);
            }

            if (reset) {
                updateHasExecutablePredecessorsProperties();
                updateHasExecutingSuccessorsProperties();
            }
        }
    }

    private boolean initParentProperties(final WorkflowManager wfm) {
        if (wfm.isProject() || wfm.isComponentProjectWFM()) {
            m_parentProps.setValid();
            return false;
        }

        NodeContainer parentNC = getParentNC(wfm);
        boolean parentHasExecutablePredecessors = parentNC.getParent().hasExecutablePredecessor(parentNC.getID());
        boolean parentHasExecutingSuccessors = parentNC.getParent().hasSuccessorInProgress(parentNC.getID());
        boolean reset =
            !m_parentProps.isValid() || parentHasExecutablePredecessors != m_parentProps.hasExecutablePredecessors()
                || parentHasExecutingSuccessors != m_parentProps.hasExecutingSuccessors();
        m_parentProps.setHasExecutablePredecessors(parentHasExecutablePredecessors);
        m_parentProps.setHasExecutingSuccessors(parentHasExecutingSuccessors);
        if (reset) {
            m_parentProps.invalidate(null);
        }
        return reset;
    }

    private static NodeContainer getParentNC(final WorkflowManager wfm) {
        if (isComponentWFM(wfm)) {
            return (SubNodeContainer)wfm.getDirectNCParent();
        } else {
            return wfm;
        }
    }

    private static boolean isComponentWFM(final WorkflowManager wfm) {
        return wfm.getDirectNCParent() instanceof SubNodeContainer;
    }

    private static boolean initAndResetNodeProperties(final Collection<NodeContainer> nodes,
        final Map<NodeID, Properties> props) {
        boolean reset = false;
        for (NodeContainer nc : nodes) {
            NodeID id = nc.getID();
            Properties p = props.get(id);
            NodeContainerState s = nc.getNodeContainerState();
            if (p == null) {
                p = new Properties(s);
                props.put(id, p);
                reset = true;
            } else if (p.getNodeState() != s) {
                p.invalidate(s);
                reset = true;
            } else {
                p.setValid();
            }
        }
        return reset;
    }

    private static void removeSurplusNodes(final WorkflowManager wfm, final Map<NodeID, Properties> props) {
        props.entrySet().removeIf(e -> !wfm.containsNodeContainer(e.getKey()) && !e.getKey().equals(wfm.getID()));
    }

    private void updateHasExecutablePredecessorsProperties() {
        PropertyProxy<NodeID> p = new HasExecutablePredecessorsPropertyProxy();
        propagate(p, collectUnknownPropertiesToStartPropagation(p));
    }

    private void updateHasExecutingSuccessorsProperties() {
        PropertyProxy<NodeID> p = new HasExecutingSuccessorsPropertyProxy();
        propagate(p, collectUnknownPropertiesToStartPropagation(p));
    }

    private static <T> Queue<T> collectUnknownPropertiesToStartPropagation(final PropertyProxy<T> a) {
        Queue<T> queue = new LinkedList<>();
        for (T t : a.listAllIncludingParent()) {
            if (!a.isValid(t)) {
                // property value needs to be re-calculated
                handleDirectDependees(a, queue, t);
            }
        }
        return queue;
    }

    /*
     *  Collect all unknown properties (i.e. properties without a value set) that are
     *  (i) independent (no dependees) and have an 'independent' property value set to 'true'
     *  (ii) dependent and at least one dependee has a property value set to 'true'
     */
    private static <T> void handleDirectDependees(final PropertyProxy<T> a, final Queue<T> queue, final T t) {
        Collection<T> directDependees = a.getDirectDependees(t);
        if (directDependees.isEmpty()) {
            if (a.getIndependentPropertyValue(t)) {
                queue.add(t);
            }
        } else if (directDependees.stream().anyMatch(a::getPropertyValue)) {
            // if there is at least one dependee with an actual property value set to true
            a.setPropertyValue(t, true);
            queue.add(t);
        } else {
            a.setPropertyValue(t, false);
        }
    }

    private static <T> void propagate(final PropertyProxy<T> a, final Queue<T> queue) {
        while (!queue.isEmpty()) {
            T next = queue.poll();
            Collection<T> directDependers = a.getDirectDependers(next);
            for (T depender : directDependers) {
                if (a.getPropertyValue(depender)) {
                    // if property is already true, propagation can be stopped at this point
                    break;
                } else {
                    a.setPropertyValue(depender, true);
                    // add depender to the queue to be further propagated
                    queue.add(depender);
                }
            }
        }
    }

    private interface PropertyProxy<T> {

        Collection<T> listAllIncludingParent();

        boolean getPropertyValue(T t); // NOSONAR

        boolean getIndependentPropertyValue(T t); // NOSONAR

        void setPropertyValue(T t, boolean value);

        Collection<T> getDirectDependees(T t);

        Collection<T> getDirectDependers(T t);

        boolean isValid(T t);
    }

    private abstract class AbstractPropertyProxy implements PropertyProxy<NodeID> {

        @Override
        public boolean isValid(final NodeID t) {
            return m_props.get(t).isValid();
        }

        @Override
        public Collection<NodeID> listAllIncludingParent() {
            List<NodeID> res =
                m_wfm.getNodeContainers().stream().map(NodeContainer::getID).collect(Collectors.toList());
            if (!m_wfm.isProject() && !m_wfm.isComponentProjectWFM()) {
                // also add this workflow
                res.add(m_wfm.getID());
            }
            return res;
        }

    }

    private final class HasExecutablePredecessorsPropertyProxy extends AbstractPropertyProxy {

        @Override
        public boolean getPropertyValue(final NodeID t) {
            return m_props.get(t).hasExecutablePredecessors();
        }

        @Override
        public boolean getIndependentPropertyValue(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return m_props.get(t).hasExecutablePredecessors();
            } else {
                return isExecutable(m_wfm.getNodeContainer(t));
            }
        }

        private boolean isExecutable(final NodeContainer nc) {
            return nc.getNodeContainerState().isConfigured();
        }

        @Override
        public void setPropertyValue(final NodeID t, final boolean value) {
            if (!t.equals(m_wfm.getID())) {
                m_props.get(t).setHasExecutablePredecessors(value);
            }
        }

        @Override
        public Collection<NodeID> getDirectDependees(final NodeID t) {
            return getDirectPredecessors(t, m_wfm);
        }

        @Override
        public Collection<NodeID> getDirectDependers(final NodeID t) {
            return getDirectSuccessors(t, m_wfm);
        }
    }

    private final class HasExecutingSuccessorsPropertyProxy extends AbstractPropertyProxy {

        @Override
        public boolean getPropertyValue(final NodeID t) {
            return m_props.get(t).hasExecutingSuccessors();
        }

        @Override
        public boolean getIndependentPropertyValue(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return m_props.get(t).hasExecutingSuccessors();
            } else {
                return isExecuting(m_wfm.getNodeContainer(t).getNodeContainerState());
            }
        }

        private boolean isExecuting(final NodeContainerState s) {
            return (s.isExecutionInProgress() && !s.isWaitingToBeExecuted()) || s.isExecutingRemotely();
        }

        @Override
        public void setPropertyValue(final NodeID t, final boolean value) {
            if (!t.equals(m_wfm.getID())) {
                m_props.get(t).setHasExecutingSuccessors(value);
            }
        }

        @Override
        public Collection<NodeID> getDirectDependees(final NodeID t) {
            return getDirectSuccessors(t, m_wfm);
        }

        @Override
        public Collection<NodeID> getDirectDependers(final NodeID t) {
            return getDirectPredecessors(t, m_wfm);
        }
    }

    private static Collection<NodeID> getDirectPredecessors(final NodeID t, final WorkflowManager wfm) {
        if (!wfm.containsNodeContainer(t)) {
            return Collections.emptyList();
        }
        return wfm.getIncomingConnectionsFor(t).stream().map(ConnectionContainer::getSource)
            .collect(Collectors.toSet());

    }

    private static Collection<NodeID> getDirectSuccessors(final NodeID t, final WorkflowManager wfm) {
        TODO
        parents are a dependees/dependers in one direction!!

        if (!wfm.containsNodeContainer(t)) {
            return Collections.emptyList();
        }
        Set<NodeID> successors =
            wfm.getOutgoingConnectionsFor(t).stream().map(ConnectionContainer::getDest).collect(Collectors.toSet());
        if (successors.isEmpty() && isComponentOutput(t, wfm)) {
            return Collections.singleton(wfm.getID());
        }
        return successors;
    }

    private static boolean isComponentOutput(final NodeID id, final WorkflowManager wfm) {
        SubNodeContainer snc = getParentComponent(wfm);
        return snc != null && snc.getVirtualOutNodeID().equals(id);
    }

    private static SubNodeContainer getParentComponent(final WorkflowManager wfm) {
        if (wfm.getDirectNCParent() instanceof SubNodeContainer && !wfm.isComponentProjectWFM()) {
            return (SubNodeContainer)wfm.getDirectNCParent();
        } else {
            return null;
        }
    }

    private static class Properties {

        private boolean m_hasExecutablePredecessors = false;

        private boolean m_hasExecutingSuccessors = false;

        private NodeContainerState m_nodeState = null;

        private boolean m_valid = false;

        Properties(final NodeContainerState s) {
            m_nodeState = s;
        }

        void invalidate(final NodeContainerState s) {
            m_nodeState = s;
            m_valid = false;
        }

        void setValid() {
            m_valid = true;
        }

        boolean isValid() {
            return m_valid;
        }

        boolean hasExecutablePredecessors() {
            return m_hasExecutablePredecessors;
        }

        boolean hasExecutingSuccessors() {
            return m_hasExecutingSuccessors;
        }

        void setHasExecutablePredecessors(final boolean b) {
            m_hasExecutablePredecessors = b;
        }

        void setHasExecutingSuccessors(final boolean b) {
            m_hasExecutingSuccessors = b;
        }

        NodeContainerState getNodeState() {
            return m_nodeState;
        }
    }

}
