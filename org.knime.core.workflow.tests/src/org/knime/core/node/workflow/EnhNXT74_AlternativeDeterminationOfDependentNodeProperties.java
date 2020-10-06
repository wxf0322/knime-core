/*
 * ------------------------------------------------------------------------
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
 * ------------------------------------------------------------------------
 */
package org.knime.core.node.workflow;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.Test;

/**
 * Tests the correctness of the alternative way to determine node properties
 * that depend on other nodes in the workflow graph.
 * 
 * @author Martin Horn, KNIME GmbH, Konstanz, Germany
 */
public class EnhNXT74_AlternativeDeterminationOfDependentNodeProperties extends WorkflowTestCase {

	/**
	 * Mainly tests the correctness of the dependent node properties as determined
	 * in the alternative way. It is done by comparing the results of the
	 * {@link WorkflowManager#canExecuteNode(NodeID)} and
	 * {@link WorkflowManager#canResetNode(NodeID)} obtained in the 'classic' way to
	 * the results as obtained by the alternative way (where the dependent node
	 * properties are determined in one rush and cashed).
	 *
	 * @throws Exception
	 */
	@Test
	public void testCorrectnessOfDependentNodeProperties() throws Exception {
		loadAndSetWorkflow();
		WorkflowManager wfm = getManager();
		NodeID parentId = wfm.getID();
		WorkflowManager metanode = (WorkflowManager) wfm.getNodeContainer(parentId.createChild(209));
		WorkflowManager componentWfm = ((SubNodeContainer) wfm.getNodeContainer(parentId.createChild(212)))
				.getWorkflowManager();

		checkCanExecuteAndCanResetFlagsForAllNodes(wfm);
		checkCanExecuteAndCanResetFlagsForAllNodes(metanode);
		checkCanExecuteAndCanResetFlagsForAllNodes(componentWfm);

		// execute 'Wait ...' nodes
		wfm.executeUpToHere(parentId.createChild(203), parentId.createChild(195));
		await().atMost(5, TimeUnit.SECONDS).pollInterval(100, TimeUnit.MILLISECONDS).untilAsserted(() -> {
			assertTrue(wfm.getNodeContainerState().isExecutionInProgress());
		});
		checkCanExecuteAndCanResetFlagsForAllNodes(wfm);
		checkCanExecuteAndCanResetFlagsForAllNodes(metanode);

		// add a connection within the component to make sure that the
		// 'hasExecutablePredeccessor' property
		// of the contained nodes changes
		componentWfm.addConnection(componentWfm.getID().createChild(212), 1, componentWfm.getID().createChild(214), 1);
		checkCanExecuteAndCanResetFlagsForAllNodes(componentWfm);

		wfm.cancelExecution();
	}

	/**
	 * Makes sure that the 'dependent node properties' are not invalidated while the
	 * respective {@link DependentNodePropertiesUpdateLock} is set.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNodeStateChangeWhileUpdateOfDependentNodePropertiesIsLocked() throws Exception {
		loadAndSetWorkflow();
		WorkflowManager wfm = getManager();
		try (DependentNodePropertiesUpdateLock lock = wfm.determineDependentNodeProperties()) {
			assertTrue("dependent node properties of workflow expected to be valid",
					wfm.getDependentNodeProperties().isValid());
			wfm.canExecuteNode(wfm.getID().createChild(210));
			assertTrue("dependent node properties of workflow expected to be still valid",
					wfm.getDependentNodeProperties().isValid());
		}
		assertFalse("dependent node properties of workflow not expected to be valid anymore",
				wfm.getDependentNodeProperties().isValid());
	}

	private void checkCanExecuteAndCanResetFlagsForAllNodes(WorkflowManager wfm) {
		assertFalse("dependent node properties are not valid", wfm.getDependentNodeProperties().isValid());

		List<NodeID> nodes = getNodeIds(wfm);

		List<Boolean> canExecute = nodes.stream().map(wfm::canExecuteNode).collect(Collectors.toList());
		List<Boolean> canReset = nodes.stream().map(wfm::canResetNode).collect(Collectors.toList());

		try (DependentNodePropertiesUpdateLock lock = wfm.determineDependentNodeProperties()) {
			assertNotNull("node properties must not be null", wfm.getDependentNodeProperties());
			assertTrue("dependent node properties expected to be valid", wfm.getDependentNodeProperties().isValid());

			for (int i = 0; i < nodes.size(); i++) {
				NodeID id = nodes.get(i);
				NodeContainer nc = wfm.getNodeContainer(id);

				assertTrue("dependent node properties expected to be valid", nc.getDependentNodeProperties().isValid());
				assertThat("'canExecute' flag differs", wfm.canExecuteNode(id), is(canExecute.get(i)));
				assertThat("'canReset' flag differs", wfm.canResetNode(id), is(canReset.get(i)));

				assertThatTheRightMethodsHaveBeenCalled(wfm, id, nc);
			}
		}
		assertFalse("dependent node properties are not expected to be valid",
				wfm.getDependentNodeProperties().isValid());
		assertFalse("dependent node properties are not expected to be locked anymore",
				wfm.getDependentNodeProperties().isLocked());
	}

	private void assertThatTheRightMethodsHaveBeenCalled(WorkflowManager wfm, NodeID id, NodeContainer nc) {
		DependentNodeProperties dnp = nc.getDependentNodeProperties();
		DependentNodePropertiesForTesting test = new DependentNodePropertiesForTesting();
		nc.setDependentNodeProperties(test);

		wfm.canExecuteNode(id);
		wfm.canResetNode(id);

		// make sure that the node properties have been used for real (by checking that
		// the respective methods have been called)
		assertThat(test.m_hasExecutablePredecessorsMethodCalls, is(wfm.canExecuteNodeDirectly(id) ? 0 : 1));
		assertThat(test.m_hasExecutingSuccessorsMethodCalls, is(nc.canPerformReset() ? 1 : 0));

		test.m_isValid = false;
		test.m_hasExecutablePredecessorsMethodCalls = 0;
		test.m_hasExecutingSuccessorsMethodCalls = 0;
		assertThat(test.m_hasExecutablePredecessorsMethodCalls, is(0));
		assertThat(test.m_hasExecutingSuccessorsMethodCalls, is(0));

		nc.setDependentNodeProperties(dnp);
	}

	private static List<NodeID> getNodeIds(WorkflowManager wfm) {
		return wfm.getNodeContainers().stream().map(nc -> nc.getID()).collect(Collectors.toList());
	}

	private static class DependentNodePropertiesForTesting extends DependentNodeProperties {

		private int m_hasExecutablePredecessorsMethodCalls = 0;
		private int m_hasExecutingSuccessorsMethodCalls = 0;

		private boolean m_isValid = true;

		@Override
		boolean isValid() {
			return m_isValid;
		}

		@Override
		boolean hasExecutablePredecessors() {
			m_hasExecutablePredecessorsMethodCalls++;
			return true;
		}

		@Override
		boolean hasExecutingSuccessors() {
			m_hasExecutingSuccessorsMethodCalls++;
			return true;
		}

	}
}
