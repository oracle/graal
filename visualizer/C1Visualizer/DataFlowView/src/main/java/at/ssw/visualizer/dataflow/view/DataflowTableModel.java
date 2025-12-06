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

import at.ssw.visualizer.dataflow.graph.ClusterWidget;
import at.ssw.visualizer.dataflow.graph.InstructionNodeGraphScene;
import at.ssw.visualizer.dataflow.graph.InstructionNodeWidget;
import at.ssw.visualizer.dataflow.instructions.Instruction;
import java.util.Arrays;
import java.util.Comparator;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author Stefan Loidl
 * @author Christian Wimmer
 */
public class DataflowTableModel extends AbstractTableModel {
    public static final int COLUMN_VISIBLE = 0;
    public static final int COLUMN_NAME = 1;
    public static final int COLUMN_BLOCK = 2;
    public static final int COLUMN_TYPE = 3;
    public static final int COLUMN_STATEMENT = 4;
    public static final int COLUMN_SUCC = 5;
    public static final int COLUMN_PRED = 6;
    public static final int COLUMN_CLUSTER = 7;

    public static final String[] COLUMN_NAMES = {"Visible", "Name", "Block", "Type", "Statement", "Successors", "Predecessors", "Cluster"};
    public static final int[] COLUMN_WIDTHS = {60, 60, 60, 60, 200, 120, 120, 60};

    private InstructionNodeWidget[] widgets = null;

    /** Sets the data source for the tablemodel*/
    public void setDataSource(InstructionNodeGraphScene scene) {
        if (scene == null) {
            widgets = null;
        } else {
            widgets = scene.getNodeWidgets();
        }
        fireTableDataChanged();
    }


    /** Sorts the data source, according to the value within specified column */
    public void sort(final int column) {
        if (widgets == null) {
            return;
        }
        Arrays.sort(widgets, new Comparator<InstructionNodeWidget>() {
            public int compare(InstructionNodeWidget w1, InstructionNodeWidget w2) {
                String s1 = getValue(w1, column).toString();
                String s2 = getValue(w2, column).toString();
                if (column == COLUMN_NAME || column == COLUMN_BLOCK || column == COLUMN_CLUSTER) {
                    if (s1.length() > 1 && s2.length() > 1) {
                        s1 = s1.substring(1);
                        s2 = s2.substring(1);
                        try {
                            int i1 = Integer.valueOf(s1);
                            int i2 = Integer.valueOf(s2);
                            return i1 - i2;
                        } catch (Exception e) {
                        }
                    }
                }
                return s1.compareTo(s2);
            }
        });
        fireTableDataChanged();
    }


    public int getRowCount() {
        return widgets == null ? 0 : widgets.length;
    }

    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    public Object getValueAt(int row, int column) {
        return getValue(widgets[row], column);
    }

    protected Object getValue(InstructionNodeWidget nw, int column) {
        Instruction inst = nw.getInstruction();
        ClusterWidget cw = nw.getClusterWidget();
        if (inst == null) {
            return "";
        }

        switch (column) {
            case COLUMN_VISIBLE:
                return nw.isWidgetVisible();
            case COLUMN_NAME:
                return nw.getID();
            case COLUMN_BLOCK:
                return inst.getSourceBlock();
            case COLUMN_TYPE:
                return getTypeString(inst.getInstructionType());
            case COLUMN_STATEMENT:
                return inst.getInstructionString();
            case COLUMN_SUCC:
                return getIDString(inst.getSuccessors());
            case COLUMN_PRED:
                return getIDString(inst.getPredecessors());
            case COLUMN_CLUSTER:
                return cw == null ? "" : cw.getId();
            default:
                throw new Error("invalid column");
        }
    }

    private String getTypeString(Instruction.InstructionType type) {
        switch (type) {
            case CONSTANT:
                return "constant";
            case CONTROLFLOW:
                return "control flow";
            case OPERATION:
                return "operation";
            case PARAMETER:
                return "parameter";
            case PHI:
                return "phi";
            default:
                return "undefined";
        }
    }

    private String getIDString(Instruction[] instructions) {
        if (instructions == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Instruction instruction : instructions) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(instruction.getID());
        }
        return sb.toString();
    }

    /** Returns a Widget for the given row */
    public InstructionNodeWidget getWidgetAtRow(int row) {
        if (widgets != null && row < widgets.length) {
            return widgets[row];
        } else {
            return null;
        }
    }

    /** Sets the value of a cell- only visible state can be changed */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == COLUMN_VISIBLE && widgets != null && rowIndex < widgets.length) {
            InstructionNodeGraphScene s = (InstructionNodeGraphScene) widgets[rowIndex].getScene();
            s.handleToggleVisibility(widgets[rowIndex].getInstruction(), false);
        }
    }

    @Override
    public Class<?> getColumnClass(int column) {
        if (column == COLUMN_VISIBLE) {
            return Boolean.class;
        }
        return String.class;
    }

    /** Only visible state is editable */
    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == COLUMN_VISIBLE;
    }
}
