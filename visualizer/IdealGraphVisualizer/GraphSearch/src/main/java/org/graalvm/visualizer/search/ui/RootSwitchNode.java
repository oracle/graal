/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.visualizer.search.ui;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import org.graalvm.visualizer.search.GraphItem;
import org.graalvm.visualizer.search.NodeResultItem;
import org.graalvm.visualizer.search.ResultItem;
import org.graalvm.visualizer.search.SearchResultsEvent;
import org.graalvm.visualizer.search.SearchResultsListener;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.netbeans.swing.outline.Outline;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.view.NodeTreeModel;
import org.openide.explorer.view.Visualizer;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.FilterNode;
import org.openide.nodes.Node;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

import javax.swing.SwingUtilities;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author sdedic
 */
public class RootSwitchNode extends FilterNode implements SearchResultsListener {
    private static final RequestProcessor RESELECT_RP = new RequestProcessor(RootSwitchNode.class);
    private final GraphContainer container;
    private final SearchResultsModel model;
    private final Node flatNode;
    private final Outline theOutline;
    private final ExplorerManager manager;
    private final NodeTreeModel nodeOutlineModel;

    private Node treeNode;
    private GraphItem singleParent;

    public RootSwitchNode(
            Outline theOutline,
            ExplorerManager manager,
            NodeTreeModel nodeOutlineModel,
            GraphContainer container, SearchResultsModel model) {
        super(new AbstractNode(new ResultsFlatChildren(model)));

        this.model = model;
        this.flatNode = getOriginal();
        this.container = container;

        this.nodeOutlineModel = nodeOutlineModel;
        this.manager = manager;
        this.theOutline = theOutline;

        model.addSearchResultsListener(WeakListeners.create(SearchResultsListener.class, this, model));
    }

    @Override
    public void parentsChanged(SearchResultsEvent event) {
        Collection<GraphItem> graphs = model.getParents();
        Node nextNode = flatNode;

        if (graphs.size() > 1) {
            if (treeNode == null) {
                treeNode = new AbstractNode(new ResultsGraphChildren(container, model));
            }
            nextNode = treeNode;
        } else if (graphs.size() == 1) {
            singleParent = graphs.iterator().next();
        }
        Node fNextNode = nextNode;
        if (nextNode != getOriginal()) {
            SwingUtilities.invokeLater(() -> {
                if (fNextNode != flatNode) {
                    postSynchronize();
                }
                changeOriginal(fNextNode, true);
            });
        }
    }

    private void postSynchronize() {
        if (singleParent == null) {
            return;
        }
        Node[] sel = manager.getSelectedNodes();
        List<NodeResultItem> items = new ArrayList<>();
        boolean visible = false;
        for (Node n : sel) {
            NodeResultItem r = n.getLookup().lookup(NodeResultItem.class);
            if (r == null) {
                continue;
            }
            items.add(r);
            TreeNode tn = Visualizer.findVisualizer(n);
            TreePath p = new TreePath(nodeOutlineModel.getPathToRoot(tn));
            if (theOutline.isVisible(p)) {
                if (!visible) {
                    visible = true;
                    if (items.size() > 1) {
                        items.add(0, items.remove(items.size() - 1));
                    }
                }
            }
        }
        Runnable r = new SynchronizeViewAndSelection(items, singleParent, visible);
        RESELECT_RP.post(() -> SwingUtilities.invokeLater(r),
                100);
    }

    private class SynchronizeViewAndSelection implements Runnable {
        private final List<NodeResultItem> selectedGraphNodes;
        private final GraphItem currentGraph;
        private final boolean scrollToView;

        public SynchronizeViewAndSelection(List<NodeResultItem> selectedGraphNodes, GraphItem currentGraph, boolean scrollToView) {
            this.selectedGraphNodes = selectedGraphNodes;
            this.currentGraph = currentGraph;
            this.scrollToView = scrollToView;
        }

        public void run() {
            expandAndSelect();
        }

        public void expandAndSelect() {
            Node graphNode = null;
            List<Node> toSelect = new ArrayList<>();
            Node root = manager.getRootContext();
            if (root == null) {
                return;
            }
            for (Node n : root.getChildren().getNodes()) {
                if (n.getLookup().lookup(GraphItem.class) == currentGraph) {
                    graphNode = n;
                    break;
                }
            }
            if (graphNode == null) {
                return;
            }
            for (Node n : graphNode.getChildren().getNodes()) {
                if (selectedGraphNodes.contains(n.getLookup().lookup(ResultItem.class))) {
                    toSelect.add(n);
                }
            }

            TreeNode n = Visualizer.findVisualizer(graphNode);
            TreePath expPath = new TreePath(nodeOutlineModel.getPathToRoot(n));
            theOutline.expandPath(expPath);
            Node[] nodeArr = toSelect.toArray(new Node[toSelect.size()]);
            try {
                manager.setExploredContext(graphNode);
                manager.setSelectedNodes(nodeArr);
            } catch (PropertyVetoException ex) {
            }
            if (scrollToView && nodeArr.length > 0) {
                TreeNode selNode = Visualizer.findVisualizer(nodeArr[0]);
                TreePath visiblePath = new TreePath(nodeOutlineModel.getPathToRoot(selNode));
                theOutline.scrollRectToVisible(theOutline.getPathBounds(visiblePath));
            }
        }
    }
}
