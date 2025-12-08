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
package at.ssw.visualizer.dataflow.view;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.dataflow.graph.InstructionNodeGraphScene;
import at.ssw.visualizer.dataflow.graph.InstructionNodeWidget;
import at.ssw.visualizer.dataflow.graph.InstructionSceneListener;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.Serializable;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * Top component which displays something.
 *
 * @author Stefan Loidl
 * @author Christian Wimmer
 */
public final class DataflowViewTopComponent extends TopComponent {
    private InstructionNodeGraphScene curScene;

    private JTable nodeTable;
    private DataflowTableModel tableModel;

    private DataflowViewTopComponent() {
        setName("Data Flow");
        setToolTipText("Data Flow");

        tableModel = new DataflowTableModel();
        nodeTable = new javax.swing.JTable(tableModel);
        nodeTable.setRowMargin(0);
        nodeTable.getColumnModel().setColumnMargin(0);
        nodeTable.setShowGrid(false);
        nodeTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < DataflowTableModel.COLUMN_WIDTHS.length; i++) {
            nodeTable.getColumnModel().getColumn(i).setPreferredWidth(DataflowTableModel.COLUMN_WIDTHS[i]);
        }
        nodeTable.addMouseListener(tableMouseListener);
        nodeTable.getTableHeader().addMouseListener(headerMouseListener);

        JScrollPane scrollPane = new JScrollPane(nodeTable);
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        setLayout(new BorderLayout());
        add(scrollPane);
    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        SelectionManager.getDefault().addChangeListener(selectionChangeListener);
        updateContent();
    }

    @Override
    protected void componentHidden() {
        super.componentHidden();
        SelectionManager.getDefault().removeChangeListener(selectionChangeListener);
        if (curScene != null) {
            curScene.removeInstructionSceneListener(instructionSceneListener);
        }
        curScene = null;
        tableModel.setDataSource(null);
    }


    private MouseListener tableMouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
            if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
                // Selects the node in the scene on doubleclick of an item
                String s = nodeTable.getValueAt(nodeTable.getSelectedRow(), DataflowTableModel.COLUMN_NAME).toString();
                if (curScene != null) {
                    curScene.setSingleSelectedWidget(s, true);
                }
            } else if (event.getButton() == MouseEvent.BUTTON3 && event.getClickCount() == 1) {
                // Show popup menu of the node in table
                InstructionNodeWidget w = tableModel.getWidgetAtRow(nodeTable.rowAtPoint(event.getPoint()));
                if (w != null) {
                    JPopupMenu pop = w.getPopup();
                    if (pop != null) {
                        pop.show(nodeTable, event.getX(), event.getY());
                    }
                }
            }
        }
    };

    private MouseListener headerMouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent event) {
            tableModel.sort(nodeTable.columnAtPoint(event.getPoint()));
        }
    };


    private ChangeListener selectionChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            updateContent();
        }
    };

    private void updateContent() {
        Selection selection = SelectionManager.getDefault().getCurSelection();
        InstructionNodeGraphScene newScene = selection.get(InstructionNodeGraphScene.class);

        if (newScene != curScene) {
            if (curScene != null) {
                curScene.removeInstructionSceneListener(instructionSceneListener);
            }
            tableModel.setDataSource(newScene);
            if (newScene != null) {
                newScene.addInstructionSceneListener(instructionSceneListener);
            }
            curScene = newScene;
        }
    }

    private InstructionSceneListener instructionSceneListener = new InstructionSceneListener() {
        public void doubleClicked(InstructionNodeWidget w) {
            if (w == null) {
                return;
            }
            String id = w.getID();

            for (int i = 0; i < nodeTable.getRowCount(); i++) {
                if (nodeTable.getValueAt(i, DataflowTableModel.COLUMN_NAME).equals(id)) {
                    nodeTable.changeSelection(i, 0, false, false);
                    break;
                }
            }
        }

        /** Data in InstructionNodeScene changed */
        public void updateNodeData() {
            nodeTable.repaint();
        }

        /** Selection in editor has changes */
        public void selectionChanged(Set<InstructionNodeWidget> w) {
        }
    };

    // <editor-fold defaultstate="collapsed" desc=" Singleton and Persistence Code ">
    private static final String PREFERRED_ID = "DataflowViewTopComponent";
    private static DataflowViewTopComponent instance;

    public static synchronized DataflowViewTopComponent getDefault() {
        if (instance == null) {
            instance = new DataflowViewTopComponent();
        }
        return instance;
    }

    public static synchronized DataflowViewTopComponent findInstance() {
        return (DataflowViewTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
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
            return DataflowViewTopComponent.getDefault();
        }
    }
    // </editor-fold>
}
