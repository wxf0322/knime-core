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
 * ---------------------------------------------------------------------
 *
 * History
 *   Apr 4, 2008 (wiswedel): created
 */
package org.knime.core.node.config;

import java.awt.Component;
import java.util.EventObject;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeNode;

import org.knime.core.node.config.ConfigEditTreeModel.ConfigEditTreeNode;
import org.knime.core.node.workflow.FlowObjectStack;

/** Editor component for {@link ConfigEditJTree} implementation.
 * @author Bernd Wiswedel, University of Konstanz
 */
//TODO: consider making this class package-scope
public class ConfigEditTreeEditor extends DefaultTreeCellEditor {

    /** Constructs new tree editor.
     * @param myTree associated tree.
     * @param myRenderer associated renderer.
     * @see DefaultTreeCellEditor#DefaultTreeCellEditor(
     * JTree, DefaultTreeCellRenderer)
     */
    public ConfigEditTreeEditor(final ConfigEditJTree myTree,
            final ConfigEditTreeRenderer myRenderer) {
        super(myTree, myRenderer);
    }

    /** {@inheritDoc} */
    @Override
    public Component getTreeCellEditorComponent(final JTree myTree, final Object value, final boolean isSelected,
                                                final boolean expanded, final boolean leaf, final int row) {
        final int nodeDepth;
        if (value instanceof TreeNode) {
            nodeDepth = ((ConfigEditTreeModel)myTree.getModel()).getPathToRoot((TreeNode)value).length;
        } else {
            nodeDepth = 0;
        }
        ((ConfigEditTreeRenderer)super.renderer).setValue(tree, value, nodeDepth);

        return super.getTreeCellEditorComponent(myTree, value, isSelected, expanded, leaf, row);
    }

    /** {@inheritDoc} */
    @Override
    protected TreeCellEditor createTreeCellEditor() {
        return new ComponentCreator();
    }

    /** {@inheritDoc} */
    @Override
    protected boolean canEditImmediately(final EventObject event) {
        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCellEditable(final EventObject event) {
        return true;
    }

    /**
     * Factory editor to create the {@link ConfigEditTreeNodePanel}. See class description of
     * {@link DefaultTreeCellEditor} for details.
     */
    private final class ComponentCreator extends DefaultTreeCellEditor {
        private final ConfigEditTreeNodePanel m_panelPlain;
        private final ConfigEditTreeNodePanel m_panelFull;
        private ConfigEditTreeNodePanel m_active;

        private ComponentCreator() {
            super(new JTree(), new DefaultTreeCellRenderer());
            m_panelPlain = new ConfigEditTreeNodePanel(false, (ConfigEditTreeRenderer)ConfigEditTreeEditor.this.renderer, true);
            m_panelFull = new ConfigEditTreeNodePanel(true, (ConfigEditTreeRenderer)ConfigEditTreeEditor.this.renderer, true);
            m_active = m_panelPlain;
        }

        /** {@inheritDoc} */
        @Override
        public Component getTreeCellEditorComponent(final JTree myTree, final Object value, final boolean isSelected,
                                                    final boolean expanded, final boolean leaf, final int row) {
            final ConfigEditJTree ceJT = (ConfigEditJTree)ConfigEditTreeEditor.this.tree;
            final int depth;
            if (value instanceof ConfigEditTreeNode) {
                final ConfigEditTreeNode node = (ConfigEditTreeNode)value;
                m_active = node.isLeaf() ? m_panelFull : m_panelPlain;
                FlowObjectStack stack = null;
                final JTree outerTree = ConfigEditTreeEditor.this.tree;
                if (outerTree instanceof ConfigEditJTree) {
                    stack = ((ConfigEditJTree)outerTree).getFlowObjectStack();
                }
                depth = ceJT.getModel().getPathToRoot((TreeNode)value).length;
                m_active.setFlowObjectStack(stack);
                m_active.setTreeNode(node);
            } else {
                if (value instanceof TreeNode) {
                    depth = ceJT.getModel().getPathToRoot((TreeNode)value).length;
                } else {
                    depth = 0;
                }

                m_active = m_panelPlain;
                m_active.setTreeNode(null);
            }
            final int indent = depth * ConfigEditTreeRenderer.PIXEL_INDENT_PER_PATH_DEPTH;
            final int visibleWidth = ceJT.getVisibleWidth() - indent;
            m_active.setVisibleWidth(visibleWidth);
            m_active.setTreePathDepth(depth);

            return m_active;
        }

        /** {@inheritDoc} */
        @Override
        public void cancelCellEditing() {
            m_active.commit();
            super.cancelCellEditing();
        }

        /** {@inheritDoc} */
        @Override
        public boolean stopCellEditing() {
            m_active.commit();
            return super.stopCellEditing();
        }
    }
}
