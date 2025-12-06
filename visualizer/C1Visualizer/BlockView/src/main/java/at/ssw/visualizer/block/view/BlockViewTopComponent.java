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
package at.ssw.visualizer.block.view;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.awt.BorderLayout;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * TopComponent which displays the BlockView.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
final class BlockViewTopComponent extends TopComponent {
    private ControlFlowGraph curCFG;
    private BasicBlock[] curBlocks;

    private JTable blockTable;
    private BlockTableModel tableModel;
    private boolean selectionUpdating;

    private BlockViewTopComponent() {
        setName("Blocks");
        setToolTipText("List of Blocks");

        tableModel = new BlockTableModel();
        blockTable = new JTable(tableModel);
        blockTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        blockTable.setRowMargin(0);
        blockTable.getColumnModel().setColumnMargin(0);
        blockTable.setShowGrid(false);
        blockTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < BlockTableModel.COLUMN_WIDTHS.length; i++) {
            blockTable.getColumnModel().getColumn(i).setPreferredWidth(BlockTableModel.COLUMN_WIDTHS[i]);
        }
        blockTable.getSelectionModel().addListSelectionListener(listSelectionListener);
        
        // sorting
        TableRowSorter<TableModel> sorter = new TableRowSorter<TableModel>(blockTable.getModel());
        sorter.setComparator(BlockTableModel.BLOCK_TABLE_NAME_COL_IDX, new Comparator<String>() {

            @Override
            public int compare(String block1, String block2) {
                if (block1.charAt(0) == 'B' && block2.charAt(0)=='B') {
                    try {
                        return Integer.parseUnsignedInt(block1.substring(1)) - Integer.parseUnsignedInt(block2.substring(1));
                    } catch(NumberFormatException e) {
                        // fall-back to string
                    }
                }
                return block1.compareTo(block2);
            }
        });
        blockTable.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(blockTable);
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
        selectionUpdating = true;
        curCFG = null;
        curBlocks = null;
        tableModel.setControlFlowGraph(null);
        selectionUpdating = false;
    }


    private ChangeListener selectionChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            updateContent();
        }
    };

    protected void updateContent() {
        if (selectionUpdating) {
            return;
        }
        selectionUpdating = true;
        Selection selection = SelectionManager.getDefault().getCurSelection();
        ControlFlowGraph newCFG = selection.get(ControlFlowGraph.class);
        BasicBlock[] newBlocks = selection.get(BasicBlock[].class);

        if (curCFG != newCFG) {
            // This resets a user-defined sorting.
            tableModel.setControlFlowGraph(newCFG);
            curBlocks = null;
        }
        
        if (newBlocks != null) { 
            if(newBlocks.length == 0) {
                blockTable.clearSelection();
            } else if (!Arrays.equals(curBlocks, newBlocks)) {
                Map<Object, BasicBlock> blockNames = new HashMap<Object, BasicBlock>();
                for (BasicBlock block : newBlocks) {
                    blockNames.put(block.getName(), block);
                }

                blockTable.clearSelection();
                for (int i = blockTable.getModel().getRowCount() - 1; i >= 0; i--) {
                    BasicBlock block = blockNames.get(blockTable.getValueAt(i, 0));
                    if (block != null) {
                        blockTable.addRowSelectionInterval(i, i);
                        blockTable.scrollRectToVisible(blockTable.getCellRect(i, 0, true));
                    }
                }
            }
        }
        curCFG = newCFG;
        curBlocks = newBlocks;
        selectionUpdating = false;
    }


    private ListSelectionListener listSelectionListener = new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent event) {
            updateSelection();
        }
    };

    private void updateSelection() {
        if (selectionUpdating) {
            return;
        }
        selectionUpdating = true;

        List<BasicBlock> blocks = new ArrayList<BasicBlock>();
        for (int i = 0; i < blockTable.getModel().getRowCount(); i++) {
            if (blockTable.getSelectionModel().isSelectedIndex(i)) {
                blocks.add(curCFG.getBasicBlockByName((String) blockTable.getValueAt(i, 0)));
            }
        }

        curBlocks = blocks.toArray(new BasicBlock[blocks.size()]);
        Selection selection = SelectionManager.getDefault().getCurSelection();
        selection.put(curBlocks);
        
        selectionUpdating = false;
    }

    // <editor-fold defaultstate="collapsed" desc=" Singleton and Persistence Code ">
    private static final String PREFERRED_ID = "BlockViewTopComponent";
    private static BlockViewTopComponent instance;

    public static synchronized BlockViewTopComponent getDefault() {
        if (instance == null) {
            instance = new BlockViewTopComponent();
        }
        return instance;
    }

    public static synchronized BlockViewTopComponent findInstance() {
        return (BlockViewTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
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
            return BlockViewTopComponent.getDefault();
        }
    }
    // </editor-fold>
}
