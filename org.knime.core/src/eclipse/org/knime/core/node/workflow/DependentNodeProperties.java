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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

/**
 * Represents node properties that depend on other nodes in the workflow graph (i.e. successors or predecessors). This
 * class helps to be able to calculate those properties in one go and caches them for subsequent usage until they are
 * invalidated.
 *
 * Dependent node properties are {@link #hasExecutablePredecessors()} and {@link #hasExecutingSuccessors()}.
 *
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 * @since 4.3
 */
class DependentNodeProperties {

    /**
     * See {@link WorkflowManager#determineDependentNodeProperties()}.
     *
     * @param wfm the 'dependent properties' of the nodes contained in the given workflow manager will be updated
     * @return lock that prevent the properties from being invalidated until it is released (i.e. closed)
     */
    static DependentNodePropertiesUpdateLock update(final WorkflowManager wfm) {
        final DependentNodeProperties parentProp;
        try (WorkflowLock lock = wfm.lock()) {

            // since the nodes in the given workflow are going to be set to valid after the update
            // we need to make sure that the children of those in turn are still invalid
            // (because they are not touched by this update)
            for (NodeContainer nc : wfm.getNodeContainers()) {
                WorkflowManager subWorkflow;
                if (nc instanceof WorkflowManager) {
                    subWorkflow = (WorkflowManager)nc;
                } else if (nc instanceof SubNodeContainer) {
                    subWorkflow = ((SubNodeContainer)nc).getWorkflowManager();
                } else {
                    subWorkflow = null;
                }
                if (subWorkflow != null && !subWorkflow.getDependentNodeProperties().isValid()) {
                    subWorkflow.getNodeContainers().forEach(n -> n.getDependentNodeProperties().invalidate());
                }
            }

            parentProp = wfm.getDependentNodeProperties();
            parentProp.lock();
            propagateHasExecutablePredecessorsProperty(wfm);
            propagateHasExecutingSuccessorsProperty(wfm);
            parentProp.setValid();
        }

        return parentProp::unlockAndInvalidate;
    }

    private Boolean m_hasExecutablePredecessors = null;

    private Boolean m_hasExecutingSuccessors = null;

    private boolean m_isValid = false;

    private boolean m_isLocked = false;

    private DependentNodeProperties m_parent = null;

    /**
     * Invalidates the dependent properties of this node. If the dependent properties of one single node has been
     * invalidated, the 'dependent properties' of all other nodes in the very same workflow are regarded as invalid, too
     * (mediated by the parent workflow' node property object).
     *
     * Invalidation happens when one node changes its state.
     *
     * Method partly (i.e. the call to the parent's node properties) synchronized because called from
     * {@link NodeContainer#notifyStateChangeListeners(NodeStateEvent)} which can be called from different threads.
     */
    void invalidate() {
        if (m_isLocked || (m_parent != null && m_parent.m_isLocked)) {
            return;
        }
        if (m_parent != null) {
            synchronized (m_parent) {
                if (m_parent.isValid()) {
                    m_parent.invalidate();
                }
            }
        }
        m_isValid = false;
        m_hasExecutablePredecessors = null;
        m_hasExecutingSuccessors = null;
    }

    /**
     * Tells whether the state of the 'dependent properties' of a node is valid and can be used. If <code>false</code>
     * (i.e. not valid), subsequent calls of {@link #hasExecutablePredecessors()} or {@link #hasExecutingSuccessors()}
     * will throw an exception.
     *
     * Method partly (i.e. the call to the parent's node properties) synchronized because called from
     * {@link NodeContainer#notifyStateChangeListeners(NodeStateEvent)} which can be called from different threads.
     *
     * @return whether the states of this 'dependent properties' of a node are valid or not
     */
    boolean isValid() {
        if (m_parent != null) {
            synchronized (m_parent) {
                if (!m_parent.isValid()) {
                    return false;
                }
            }
        }
        return m_isValid;
    }

    /**
     * Whether the 'dependent node properties' are locked from being updated.
     *
     * @return <code>true</code> if locked, otherwise <code>false</code>
     */
    boolean isLocked() {
        if (m_parent != null && m_parent.m_isLocked) {
            return true;
        }
        return m_isLocked;
    }

    /**
     * @return whether there are executable predecessors
     * @throws IllegalStateException if this dependent property is not valid
     */
    boolean hasExecutablePredecessors() {
        if (!isValid()) {
            throw new IllegalStateException("Property not valid anymore");
        }
        return m_hasExecutablePredecessors;
    }

    /**
     * @return whether there are executing successors
     * @throws IllegalStateException if this dependent property is not valid
     */
    boolean hasExecutingSuccessors() {
        if (!isValid()) {
            throw new IllegalStateException("Property not valid anymore");
        }
        return m_hasExecutingSuccessors;

    }

    private static void propagateHasExecutablePredecessorsProperty(final WorkflowManager wfm) {
        Property<NodeID> p = new HasExecutablePredecessorsProperty(wfm);
        propagate(p, collectUnknownPropertiesToStartPropagation(p));
    }

    private static void propagateHasExecutingSuccessorsProperty(final WorkflowManager wfm) {
        Property<NodeID> p = new HasExecutingSuccessorsProperty(wfm);
        propagate(p, collectUnknownPropertiesToStartPropagation(p));
    }

    private void unlockAndInvalidate() {
        if (m_parent != null) {
            m_parent.unlockAndInvalidate();
        }
        m_isLocked = false;
        m_isValid = false;
    }

    private void lock() {
        if (m_parent != null) {
            m_parent.lock();
        }
        m_isLocked = true;
    }

    private void setValid() {
        if (m_parent != null) {
            m_parent.setValid();
        }
        m_isValid = true;
    }

    /*
     *  Collect all unknown properties (i.e. properties without a value set) that are
     *  (i) independent (no dependees) and have a 'independent' property value set to 'true'
     *  (ii) dependent and at least one dependee has a property value set to 'true'
     */
    private static <T> Queue<T> collectUnknownPropertiesToStartPropagation(final Property<T> a) {
        Queue<T> queue = new LinkedList<>();
        for (T t : a.listAll()) {
            if (a.getPropertyValue(t) == null) {
                // property value needs to be re-calculated
                handleDirectDependees(a, queue, t);
            }
        }
        return queue;
    }

    private static <T> void handleDirectDependees(final Property<T> a, final Queue<T> queue, final T t) {
        Collection<T> directDependees = a.getDirectDependees(t);
        if (directDependees.isEmpty()) {
            a.setPropertyValue(t, Boolean.FALSE);
            if (a.getIndependentPropertyValue(t)) {
                queue.add(t);
            }
        } else if (directDependees.stream().anyMatch(d -> Boolean.TRUE.equals(a.getPropertyValue(d)))) {
            // if there is at least one dependee with an actual property value set to true
            a.setPropertyValue(t, Boolean.TRUE);
            queue.add(t);
        } else {
            a.setPropertyValue(t, Boolean.FALSE);
        }
    }

    private static <T> void propagate(final Property<T> a, final Queue<T> queue) {
        while (!queue.isEmpty()) {
            T next = queue.poll();
            Collection<T> directDependers = a.getDirectDependers(next);
            for (T depender : directDependers) {
                Boolean val = a.getPropertyValue(depender);
                if (Boolean.TRUE.equals(val)) {
                    // if property is already true, propagation can be stopped at this point
                    break;
                } else {
                    a.setPropertyValue(depender, Boolean.TRUE);
                    // add depender to the queue to be further propagated
                    queue.add(depender);
                }
            }
        }
    }

    private static void updateIsValidState(final DependentNodeProperties np) {
        np.m_isValid = np.m_hasExecutablePredecessors != null && np.m_hasExecutingSuccessors != null;
    }

    private static DependentNodeProperties getNodePropsForNode(final NodeID id, final WorkflowManager wfm) {
        DependentNodeProperties p;
        DependentNodeProperties parentProps = wfm.getDependentNodeProperties();
        if (id.equals(wfm.getID())) {
            p = parentProps;
        } else {
            p = wfm.getNodeContainer(id).getDependentNodeProperties();
            p.m_parent = parentProps;
        }
        return p;
    }

    private static Collection<NodeID> listNodesIdsIncludingParent(final WorkflowManager wfm) {
        List<NodeID> res = wfm.getNodeContainers().stream().map(NodeContainer::getID).collect(Collectors.toList());
        if (!wfm.isProject()) {
            // also add this workflow
            res.add(wfm.getID());
        }
        return res;
    }

    private interface Property<T> {

        Collection<T> listAll();

        Boolean getPropertyValue(T t);

        boolean getIndependentPropertyValue(T t); // NOSONAR

        void setPropertyValue(T t, Boolean value);

        Collection<T> getDirectDependees(T t);

        Collection<T> getDirectDependers(T t);
    }

    private static final class HasExecutablePredecessorsProperty implements Property<NodeID> {

        private WorkflowManager m_wfm;

        private HasExecutablePredecessorsProperty(final WorkflowManager wfm) {
            assert wfm.getDependentNodeProperties() != null;
            m_wfm = wfm;
        }

        @Override
        public Collection<NodeID> listAll() {
            return listNodesIdsIncludingParent(m_wfm);
        }

        @Override
        public Boolean getPropertyValue(final NodeID t) {
            return getNodePropsForNode(t, m_wfm).m_hasExecutablePredecessors;
        }

        @Override
        public boolean getIndependentPropertyValue(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                DependentNodeProperties p = m_wfm.getDependentNodeProperties();
                if (p.m_hasExecutablePredecessors == null) {
                    update(m_wfm.getParent());
                }
                return p.m_hasExecutablePredecessors;
            } else {
                return isExecutable(m_wfm.getNodeContainer(t));
            }
        }

        private static boolean isExecutable(final NodeContainer nc) {
            return nc.getNodeContainerState().isConfigured();
        }

        @Override
        public void setPropertyValue(final NodeID t, final Boolean value) {
            DependentNodeProperties p = getNodePropsForNode(t, m_wfm);
            p.m_hasExecutablePredecessors = value;
            updateIsValidState(p);
        }

        @Override
        public Collection<NodeID> getDirectDependees(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return Collections.emptyList();
            }
            // get all direct predecessors
            return m_wfm.getIncomingConnectionsFor(t).stream().map(ConnectionContainer::getSource)
                .collect(Collectors.toSet());
        }

        @Override
        public Collection<NodeID> getDirectDependers(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return Collections.emptyList();
            }
            // get all direct successors
            return m_wfm.getOutgoingConnectionsFor(t).stream().map(ConnectionContainer::getDest)
                .collect(Collectors.toSet());
        }
    }

    private static final class HasExecutingSuccessorsProperty implements Property<NodeID> {

        private WorkflowManager m_wfm;

        private HasExecutingSuccessorsProperty(final WorkflowManager wfm) {
            assert wfm.getDependentNodeProperties() != null;
            m_wfm = wfm;
        }

        @Override
        public Collection<NodeID> listAll() {
            return listNodesIdsIncludingParent(m_wfm);
        }

        @Override
        public Boolean getPropertyValue(final NodeID t) {
            return getNodePropsForNode(t, m_wfm).m_hasExecutingSuccessors;
        }

        @Override
        public boolean getIndependentPropertyValue(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                DependentNodeProperties p = m_wfm.getDependentNodeProperties();
                if (p.m_hasExecutingSuccessors == null) {
                    update(m_wfm.getParent());
                }
                return p.m_hasExecutingSuccessors;
            } else {
                return isExecuting(m_wfm.getNodeContainer(t));
            }
        }

        private static boolean isExecuting(final NodeContainer nc) {
            return nc.getNodeContainerState().isExecutionInProgress()
                || nc.getNodeContainerState().isExecutingRemotely();
        }

        @Override
        public void setPropertyValue(final NodeID t, final Boolean value) {
            DependentNodeProperties p = getNodePropsForNode(t, m_wfm);
            p.m_hasExecutingSuccessors = value;
            updateIsValidState(p);
        }

        @Override
        public Collection<NodeID> getDirectDependees(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return Collections.emptyList();
            }
            // get all direct successors
            return m_wfm.getOutgoingConnectionsFor(t).stream().map(ConnectionContainer::getDest)
                .collect(Collectors.toSet());

        }

        @Override
        public Collection<NodeID> getDirectDependers(final NodeID t) {
            if (t.equals(m_wfm.getID())) {
                return Collections.emptyList();
            }
            // get all direct predecessors
            return m_wfm.getIncomingConnectionsFor(t).stream().map(ConnectionContainer::getSource)
                .collect(Collectors.toSet());
        }
    }

}
