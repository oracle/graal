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

import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.util.Collections;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * TableModel containing the blocks of the currently selected view
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public class BlockTableModel extends AbstractTableModel {

    public static final int BLOCK_TABLE_NAME_COL_IDX = 0;
    public static final int BLOCK_TABLE_BCI_COL_IDX = 1;
    public static final int BLOCK_TABLE_FLAGS_COL_IDX = 2;
    public static final int BLOCK_TABLE_LOOP_DEPTH_COL_IDX = 3;
    public static final int BLOCK_TABLE_LOOP_INDEX_COL_IDX = 4;
    public static final int BLOCK_TABLE_DOMINATOR_COL_IDX = 5;
    public static final int BLOCK_TABLE_PREDECESSORS_COL_IDX = 6;
    public static final int BLOCK_TABLE_SUCCESSORS_COL_IDX = 7;
    public static final int BLOCK_TABLE_XHANDLERS_COL_IDX = 8;
    public static final int BLOCK_TABLE_PROBABILITY_COL_IDX = 9;

    public static final String[] COLUMN_NAMES = new String[]{"Name", "BCI", "Flags", "Loop Depth", "Loop Index", "Dominator", "Predecessors", "Successors", "XHandlers", "Probability"};
    public static final int[] COLUMN_WIDTHS = new int[]{60, 60, 60, 80, 80, 60, 120, 120, 120};

    private List<BasicBlock> blocks = Collections.emptyList();

    public void setControlFlowGraph(ControlFlowGraph cfg) {
        if (cfg == null) {
            blocks = Collections.emptyList();
        } else {
            blocks = cfg.getBasicBlocks();
        }
        fireTableDataChanged();
    }

    public int getRowCount() {
        return blocks.size();
    }

    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    public Object getValueAt(int row, int column) {
        BasicBlock block = blocks.get(row);
        switch (column) {
            case BLOCK_TABLE_NAME_COL_IDX:
                return block.getName();
            case BLOCK_TABLE_BCI_COL_IDX:
                return "[" + block.getFromBci() + ", " + block.getToBci() + "]";
            case BLOCK_TABLE_FLAGS_COL_IDX:
                return formatFlags(block.getFlags());
            case BLOCK_TABLE_LOOP_DEPTH_COL_IDX:
                return Integer.toString(block.getLoopDepth());
            case BLOCK_TABLE_LOOP_INDEX_COL_IDX:
                return block.getLoopDepth() > 0 ? Integer.toString(block.getLoopIndex()) : "";
            case BLOCK_TABLE_DOMINATOR_COL_IDX:
                return block.getDominator() != null ? block.getDominator().getName() : "";
            case BLOCK_TABLE_PREDECESSORS_COL_IDX:
                return formatBlocks(block.getPredecessors());
            case BLOCK_TABLE_SUCCESSORS_COL_IDX:
                return formatBlocks(block.getSuccessors());
            case BLOCK_TABLE_XHANDLERS_COL_IDX:
                return formatBlocks(block.getXhandlers());
            case BLOCK_TABLE_PROBABILITY_COL_IDX:
                return Double.isNaN(block.getProbability()) ? "" : (Double)block.getProbability();
            default:
                throw new Error("invalid column");
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        if (blocks.isEmpty()) {
            return Object.class;
        }
        return getValueAt(0, columnIndex).getClass();
    }

    private String formatFlags(List<String> flags) {
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for (String flag : flags) {
            sb.append(prefix).append(flag);
            prefix = ", ";
        }
        return sb.toString();
    }

    private String formatBlocks(List<BasicBlock> blocks) {
        StringBuilder sb = new StringBuilder();
        String prefix = "";
        for (BasicBlock block : blocks) {
            sb.append(prefix).append(block.getName());
            prefix = ", ";
        }
        return sb.toString();
    }
}
