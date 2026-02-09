/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.compilation.view;

import at.ssw.visualizer.compilation.view.action.OpenCompilationAction;
import at.ssw.visualizer.compilation.view.icons.Icons;
import at.ssw.visualizer.model.CompilationModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyVetoException;
import java.io.Serializable;
import java.util.Collection;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.openide.awt.Toolbar;
import org.openide.awt.ToolbarPool;
import org.openide.explorer.ExplorerManager;
import org.openide.explorer.ExplorerUtils;
import org.openide.explorer.view.BeanTreeView;
import org.openide.nodes.Node;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.Lookups;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Explorer-like view of all compiled methods.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class CompilationViewTopComponent extends TopComponent implements ExplorerManager.Provider {

    public static final String ACTIONS_COMPILATION = "at-ssw-visualizer-actions-compilation";
    public static final String ACTIONS_CFG = "at-ssw-visualizer-actions-cfg";
    public static final String ACTIONS_INTERVALS = "at-ssw-visualizer-actions-intervals";
    protected ExplorerManager manager;
    protected BeanTreeView view;
    protected CompilationModelNode rootNode;
    protected JToggleButton sortButton;
    protected JToggleButton filterButton;
    protected JToggleButton packageButton;

    private CompilationViewTopComponent() {
        setName("Compiled Methods");
        setToolTipText("List of Compiled Methods");
        setIcon(ImageUtilities.loadImage(Icons.COMPILATIONS));

        manager = new ExplorerManager();
        view = new BeanTreeView();
        view.setRootVisible(false);

        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.NORTH);
        add(view, BorderLayout.CENTER);

        CompilationModel model = Lookup.getDefault().lookup(CompilationModel.class);
        rootNode = new CompilationModelNode(model);
        manager.setRootContext(rootNode);
        associateLookup(ExplorerUtils.createLookup(manager, getActionMap()));

        configButtonListener.actionPerformed(null);
    }

    private Toolbar createToolbar() {
        ToolbarPool.getDefault().setPreferredIconSize(16);

        Toolbar toolBar = new Toolbar();
        toolBar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border"));

        toolBar.add(new OpenCompilationAction());
        toolBar.addSeparator();
        addElements(toolBar, Lookups.forPath(CompilationViewTopComponent.ACTIONS_COMPILATION).lookupAll(Action.class));
        toolBar.addSeparator();
        addElements(toolBar, Lookups.forPath(CompilationViewTopComponent.ACTIONS_CFG).lookupAll(Action.class));
        toolBar.addSeparator();
        addElements(toolBar, Lookups.forPath(CompilationViewTopComponent.ACTIONS_INTERVALS).lookupAll(Action.class));

        toolBar.addSeparator();
        sortButton = new JToggleButton(new ImageIcon(ImageUtilities.loadImage(Icons.SORT)), false);
        sortButton.setToolTipText("Sort List of Compilations");
        sortButton.addActionListener(configButtonListener);
        toolBar.add(sortButton);
        filterButton = new JToggleButton(new ImageIcon(ImageUtilities.loadImage(Icons.FILTER)), true);
        filterButton.addActionListener(configButtonListener);
        filterButton.setToolTipText("Filter Control Flow Graphs that have no IR (generated during bytecode parsing phase)");
        toolBar.add(filterButton);
        packageButton = new JToggleButton(new ImageIcon(ImageUtilities.loadImage(Icons.PACKAGE)), false);
        packageButton.addActionListener(configButtonListener);
        packageButton.setToolTipText("Show Package Names");
        toolBar.add(packageButton);
        toolBar.addSeparator();
        JButton collapseAllButton = new JButton(new ImageIcon(ImageUtilities.loadImage(Icons.COLLAPSE_ALL)));
        collapseAllButton.addActionListener(collapseAllListener);
        collapseAllButton.setToolTipText("Collapse All");
        toolBar.add(collapseAllButton);

        return toolBar;
    }

    private void addElements(JToolBar toolBar, Collection<? extends Action> actions) {
        for (Action action : actions) {
            if (action instanceof Presenter.Toolbar) {
                toolBar.add(((Presenter.Toolbar) action).getToolbarPresenter());
            } else {
                toolBar.add(action);
            }
        }
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, 0);
    }

    public ExplorerManager getExplorerManager() {
        return manager;
    }
    private ActionListener configButtonListener = new ActionListener() {

        public void actionPerformed(ActionEvent event) {
            org.openide.nodes.Node[] selectedNodes = manager.getSelectedNodes();
            rootNode.doRefresh(sortButton.isSelected(), filterButton.isSelected(), !packageButton.isSelected());
            try {
                manager.setSelectedNodes(selectedNodes);
            } catch (PropertyVetoException ex) {
                throw new Error(ex);
            }
        }
    };
    private ActionListener collapseAllListener = new ActionListener() {

        public void actionPerformed(ActionEvent event) {
            for (Node n : rootNode.getChildren().getNodes()) {
                view.collapseNode(n);
            }
        }
    };
    // <editor-fold defaultstate="collapsed" desc=" Singleton and Persistence Code ">
    private static final String PREFERRED_ID = "CompilationViewTopComponent";
    private static CompilationViewTopComponent instance;

    public static synchronized CompilationViewTopComponent getDefault() {
        if (instance == null) {
            instance = new CompilationViewTopComponent();
        }
        return instance;
    }

    public static synchronized CompilationViewTopComponent findInstance() {
        return (CompilationViewTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    @Override
    public int getPersistenceType() {
        return PERSISTENCE_ALWAYS;
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    @Override
    public Object writeReplace() {
        return new ResolvableHelper();
    }

    static final class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return CompilationViewTopComponent.getDefault();
        }
    }
    // </editor-fold>
}
