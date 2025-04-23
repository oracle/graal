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

import org.graalvm.visualizer.search.NodeResultItem;
import org.graalvm.visualizer.search.SearchController;
import org.graalvm.visualizer.search.SearchEvent;
import org.graalvm.visualizer.search.SearchListener;
import org.graalvm.visualizer.search.SearchResultColumns;
import org.graalvm.visualizer.search.SearchResultsEvent;
import org.graalvm.visualizer.search.SearchResultsListener;
import org.graalvm.visualizer.search.SearchResultsModel;
import org.graalvm.visualizer.search.ui.actions.ExtendSearchAction;
import org.graalvm.visualizer.util.swing.ActionUtils;
import org.netbeans.api.actions.Openable;
import org.netbeans.swing.etable.ETableColumn;
import org.netbeans.swing.etable.ETableColumnModel;
import org.netbeans.swing.outline.Outline;
import org.netbeans.swing.outline.OutlineModel;
import org.netbeans.swing.outline.RowModel;
import org.openide.awt.Actions;
import org.openide.explorer.view.NodeTreeModel;
import org.openide.explorer.view.OutlineView;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.BaseUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.TopologicalSortException;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;

import javax.swing.Action;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author sdedic
 */
public class ResultViewPanel extends javax.swing.JPanel
        implements SearchListener, SearchResultsListener, PropertyChangeListener {
    private final OutlineView outlineView;
    private final Results resultsPane;
    private final SearchController controller;
    private SearchResultsModel currentModel;
    private boolean init;
    private SearchResultsListener wsrl;
    private SearchListener wsl;
    private final SearchResultColumns columnsConfig;
    private final PL tcLookup;
    private final InstanceContent viewContent = new InstanceContent();
    private final SearchResultOptions resultOptions = new SearchResultOptions();
    private NodeTreeModel capturedNodeModel;

    /**
     * Creates new form ResultViewPane
     */
    @NbBundle.Messages({
            "LABEL_GraphNodeIdColumn=Id and Name"
    })
    public ResultViewPanel(SearchController controller) {
        initComponents();

        tcLookup = new PL();

        viewContent.set(Arrays.asList(controller, resultOptions), null);

        columnsConfig = Lookup.getDefault().lookup(SearchResultColumns.class);
        outlineView = new OutlineView(Bundle.LABEL_GraphNodeIdColumn()) {
            @Override
            protected OutlineModel createOutlineModel(NodeTreeModel treeModel, RowModel rowModel, String label) {
                capturedNodeModel = treeModel;
                return super.createOutlineModel(treeModel, rowModel, label);
            }
        };

        outlineView.getOutline().setRootVisible(false);
        outlineView.getOutline().setAutoCreateColumnsFromModel(false);
        TableColumn tc = outlineView.getOutline().getColumnModel().getColumn(0);

        tc.setIdentifier("0");
        tc.setPreferredWidth((int) columnsConfig.getColumnRelativeSize("0") * 10000);

        Lookup c = new AbstractLookup(viewContent);
        tcLookup.doSetLookups(c);

        resultsPane = new Results();
        add(resultsPane, BorderLayout.CENTER);

        tcLookup.doSetLookups(resultsPane.getLookup(), c);
        this.controller = controller;
        // make room for the column config button:
        outlineView.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        this.outlineView.setPopupAllowed(true);
        this.outlineView.getOutline().setColumnHidingAllowed(true);
        setModel(controller.getResults());
        resultsPane.getExplorerManager().addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
    }

    static class PL extends ProxyLookup {
        public void doSetLookups(Lookup... proxies) {
            super.setLookups(proxies);
        }
    }

    public Lookup getLookup() {
        return tcLookup;
    }

    private void updateTableColumns() {
        Set<String> allProperties = new HashSet<>(currentModel.getAllPropertyNames());
        allProperties.add("graph");

        // merge in newly discovered columns, but insert them at relative positions
        // defined by visible column configuration.
        ETableColumnModel colModel = (ETableColumnModel) outlineView.getOutline().getColumnModel();
        List<String> existingColumns = new ArrayList<>();

        for (TableColumn tc : getAllColumns(colModel)) {
            existingColumns.add(tc.getIdentifier().toString());
        }
        allProperties.removeAll(existingColumns);

        Map<String, Collection<String>> sortEdges = new HashMap<>(existingColumns.size());
        for (int i = 1; i < existingColumns.size(); i++) {
            List<String> followers = new ArrayList<>();
            followers.add(existingColumns.get(i));
            sortEdges.put(existingColumns.get(i - 1), followers);
        }

        List<String> defaultVisible = new ArrayList<>(columnsConfig.getDefaultVisibleColumns());
        List<String> visibleCurrentOrder;
        try {
            visibleCurrentOrder = BaseUtilities.topologicalSort(defaultVisible, sortEdges);
        } catch (TopologicalSortException ex) {
            visibleCurrentOrder = (List<String>) ex.partialSort();
        }
        for (int i = 1; i < visibleCurrentOrder.size(); i++) {
            String preceding = visibleCurrentOrder.get(i - 1);
            String next = visibleCurrentOrder.get(i);
            List<String> followers = (List<String>) sortEdges.get(preceding);
            if (followers != null) {
                if (!followers.contains(next)) {
                    followers.add(next);
                }
            } else {
                sortEdges.put(preceding, Collections.singleton(next));
            }
        }

        if (existingColumns.containsAll(allProperties)) {
            return;
        }

        List<String> totalNewOrdering = new ArrayList<>(existingColumns);
        totalNewOrdering.addAll(allProperties);
        try {
            totalNewOrdering = new ArrayList<>(BaseUtilities.topologicalSort(totalNewOrdering, sortEdges));
        } catch (TopologicalSortException ex) {
            totalNewOrdering = new ArrayList<>((List<String>) ex.partialSort());
        }
        List<String> toHide = new ArrayList<>();

        for (TableColumn c : getAllColumns(colModel)) {
            if (colModel.isColumnHidden(c)) {
                colModel.setColumnHidden(c, false);
                toHide.add(c.getIdentifier().toString());
            }
        }
        // create new columns:
        for (int idx = 0; idx < totalNewOrdering.size(); idx++) {
            String cn = totalNewOrdering.get(idx);
            int curIndex;

            if (!existingColumns.contains(cn)) {
                // this will always make a new columns set...
                outlineView.addPropertyColumn(cn, cn);
                int modelIndex = outlineView.getOutline().getModel().getColumnCount() - 1;
                ETableColumn nc = createColumn(modelIndex, outlineView.getOutline());
                nc.setIdentifier(cn);
                nc.setHeaderValue(cn);
                outlineView.getOutline().getColumnModel().addColumn(nc);
                curIndex = colModel.getColumnIndex(cn);
                if (curIndex == -1) {
                    continue;
                }
                if (!defaultVisible.contains(cn)) {
                    toHide.add(cn);
                }
            } else {
                curIndex = colModel.getColumnIndex(cn);
            }
            if (curIndex != idx) {
                colModel.moveColumn(curIndex, idx);
            }
        }
        for (String cn : toHide) {
            int idx = colModel.getColumnIndex(cn);
            if (idx != -1) {
                colModel.setColumnHidden(colModel.getColumn(idx), true);
            }
        }
        // Uff..
        seInitialColumnSizes(allProperties);
    }

    private void seInitialColumnSizes(Collection<String> columnIds) {
        ETableColumnModel colModel = (ETableColumnModel) outlineView.getOutline().getColumnModel();

        float totalWidth = 0;
        for (int i = 0; i < colModel.getColumnCount(); i++) {
            ETableColumn ecol = (ETableColumn) colModel.getColumn(i);
            String id = ecol.getIdentifier().toString();
            if (!columnIds.contains(id)) {
                totalWidth += ecol.getPreferredWidth();
            }
        }

        for (int i = 0; i < colModel.getColumnCount(); i++) {
            ETableColumn ecol = (ETableColumn) colModel.getColumn(i);
            String id = ecol.getIdentifier().toString();
            if (columnIds.contains(id)) {
                float w = columnsConfig.getColumnRelativeSize(id);
                ecol.setPreferredWidth((int) w * 10000);
            }
        }
    }

    private Method refAllColumns;
    private Method refCreateColumn;

    private ETableColumn createColumn(int index, Outline out) {
        try {
            if (refCreateColumn == null) {
                refCreateColumn = Outline.class.getDeclaredMethod("createColumn", Integer.TYPE);
                refCreateColumn.setAccessible(true);
            }
            return (ETableColumn) refCreateColumn.invoke(out, index);
        } catch (ReflectiveOperationException ex) {
            return new ETableColumn(index, out);
        }
    }

    private List<TableColumn> getAllColumns(ETableColumnModel model) {
        try {
            if (refAllColumns == null) {
                refAllColumns = ETableColumnModel.class.getDeclaredMethod("getAllColumns");
                refAllColumns.setAccessible(true);
            }
            return (List<TableColumn>) refAllColumns.invoke(model);
        } catch (ReflectiveOperationException ex) {
            return Collections.list(model.getColumns());
        }
    }

    private void updateNameAndTooltip() {
        // change the name and tooltip text:
        setName(controller.getTitle());
        setToolTipText(controller.getCriteria().toDisplayString(false));
        scopeName.setText(controller.getGraphContainer().getName());
        graphName.setText(controller.getInitialGraph().getName());

        if (controller.pendingSearch().isFinished()) {
            resultsPane.enableStop(false);
        }
    }

    @Override
    public void searchStarted(SearchEvent event) {
        setModel(event.getModel());
        resultsPane.enableStop(true);
    }

    @Override
    public void searchFinished(SearchEvent event) {
        resultsPane.enableStop(false);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        if (init) {
            return;
        }
        setModel(controller.getResults());
        wsl = WeakListeners.create(SearchListener.class, this, controller);
        controller.addSearchListener(wsl);

        SwingUtilities.invokeLater(this::updateNameAndTooltip);
    }

    @Override
    public void propertiesChanged(SearchResultsEvent event) {
        SwingUtilities.invokeLater(this::updateTableColumns);
    }

    private void setModel(SearchResultsModel model) {
        if (model == currentModel) {
            updateNameAndTooltip();
            return;
        }
        if (currentModel != null) {
            if (wsrl != null) {
                currentModel.removeSearchResultsListener(wsrl);
            }
        }
        Node root;

        if (model != null) {
            wsrl = WeakListeners.create(SearchResultsListener.class, this, model);
            model.addSearchResultsListener(wsrl);
            root = new RootSwitchNode(
                    outlineView.getOutline(),
                    resultsPane.getExplorerManager(),
                    capturedNodeModel,
                    controller.getGraphContainer(), model);
        } else {
            root = new AbstractNode(Children.LEAF);
        }
        resultsPane.getExplorerManager().setRootContext(root);
        this.currentModel = model;
        updateTableColumns();
        updateNameAndTooltip();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        headerPanel = new javax.swing.JPanel();
        lScope = new javax.swing.JLabel();
        scopeName = new javax.swing.JTextField();
        lGraph = new javax.swing.JLabel();
        graphName = new javax.swing.JTextField();

        setLayout(new java.awt.BorderLayout());

        org.openide.awt.Mnemonics.setLocalizedText(lScope, org.openide.util.NbBundle.getMessage(ResultViewPanel.class, "ResultViewPanel.lScope.text")); // NOI18N

        scopeName.setEditable(false);
        scopeName.setText(org.openide.util.NbBundle.getMessage(ResultViewPanel.class, "ResultViewPanel.scopeName.text")); // NOI18N

        org.openide.awt.Mnemonics.setLocalizedText(lGraph, org.openide.util.NbBundle.getMessage(ResultViewPanel.class, "ResultViewPanel.lGraph.text")); // NOI18N

        graphName.setEditable(false);
        graphName.setText(org.openide.util.NbBundle.getMessage(ResultViewPanel.class, "ResultViewPanel.graphName.text")); // NOI18N

        javax.swing.GroupLayout headerPanelLayout = new javax.swing.GroupLayout(headerPanel);
        headerPanel.setLayout(headerPanelLayout);
        headerPanelLayout.setHorizontalGroup(
                headerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(headerPanelLayout.createSequentialGroup()
                                .addGap(4, 4, 4)
                                .addComponent(lScope)
                                .addGap(4, 4, 4)
                                .addComponent(scopeName, javax.swing.GroupLayout.DEFAULT_SIZE, 223, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(lGraph)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(graphName, javax.swing.GroupLayout.DEFAULT_SIZE, 259, Short.MAX_VALUE)
                                .addContainerGap())
        );
        headerPanelLayout.setVerticalGroup(
                headerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(headerPanelLayout.createSequentialGroup()
                                .addGap(3, 3, 3)
                                .addGroup(headerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                        .addComponent(lScope)
                                        .addComponent(scopeName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addComponent(lGraph)
                                        .addComponent(graphName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        add(headerPanel, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JTextField graphName;
    private javax.swing.JPanel headerPanel;
    private javax.swing.JLabel lGraph;
    private javax.swing.JLabel lScope;
    private javax.swing.JTextField scopeName;
    // End of variables declaration//GEN-END:variables

    class Results extends AbstractSearchResultsPanelBase {

        @SuppressWarnings("OverridableMethodCallInConstructor")
        public Results() {
            getContentPanel().add(getOutlineView());
            initActions();
        }

        protected void initActions() {
            Action open = ActionUtils.findAction("Diagram", "org.graalvm.visualizer.search.gotonode", getLookup());
            getOutlineView().getOutline().getActionMap().put("select-node", open);
            getOutlineView().setDefaultActionAllowed(true);
        }

        @Override
        protected void initToolbar() {
            super.initToolbar();
            ActionUtils.addToolbarAction(getToolBar(),
                    Actions.forID("Diagram", "org.graalvm.visualizer.search.extractnodes"),
                    getActionMap(), tcLookup, false);
            ActionUtils.addToolbarAction(getToolBar(),
                    Actions.forID("Diagram", "org.graalvm.visualizer.search.gotonode"),
                    getActionMap(), tcLookup, false);
            ActionUtils.addToolbarAction(getToolBar(),
                    Actions.forID("Edit", "org.openide.actions.DeleteAction"),
                    getActionMap(), tcLookup, false);
            ActionUtils.addToolbarAction(getToolBar(),
                    Actions.forID("Search", ExtendSearchAction.Forward.ID),
                    getActionMap(), tcLookup, false);
            ActionUtils.addToolbarAction(getToolBar(),
                    Actions.forID("Search", ExtendSearchAction.Backward.ID),
                    getActionMap(), tcLookup, false);
        }

        @Override
        protected OutlineView getOutlineView() {
            return outlineView;
        }

        @Override
        protected boolean isDetailNode(Node n) {
            return n.getLookup().lookup(NodeResultItem.class) != null;
        }

        @Override
        protected void onDetailShift(Node n) {
            super.onDetailShift(n);
            Openable oo = n.getLookup().lookup(Openable.class);
            if (oo != null) {
                oo.open();
            }
        }

        void enableStop(boolean enable) {
            if (enable) {
                showStopButton();
                btnStopRefresh.setEnabled(true);
            } else {
                btnStopRefresh.setEnabled(false);
            }
        }

        @Override
        protected void terminateSearch() {
            controller.pendingSearch().cancel();
        }
    }
}
