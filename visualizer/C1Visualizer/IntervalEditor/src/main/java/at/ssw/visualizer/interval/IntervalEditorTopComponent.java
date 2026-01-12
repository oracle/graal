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
package at.ssw.visualizer.interval;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.core.selection.SelectionProvider;
import at.ssw.visualizer.interval.icons.Icons;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.model.interval.ChildInterval;
import at.ssw.visualizer.model.interval.IntervalList;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.awt.Toolbar;
import org.openide.util.ImageUtilities;
import org.openide.windows.CloneableTopComponent;
import org.openide.windows.TopComponent;

/**
 * TopComponent for displaying interval diagrams.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class IntervalEditorTopComponent extends CloneableTopComponent implements SelectionProvider {
    private static final String HSIZE_PROP = "visualizer.hsize";
    private static final String VSIZE_PROP = "visualizer.vsize";

    private IntervalCanvas canvas;
    private ViewSettings viewSettings;
    private JToggleButton[] hsizeButtons;
    private JToggleButton[] vsizeButtons;
    private JLabel intervalStatusLabel;
    private JLabel blockStatusLabel;
    private JLabel instructionStatusLabel;

    private IntervalList intervals;
    private Selection selection;
    private boolean selectionUpdating;

    protected IntervalEditorTopComponent(IntervalList intervals) {
        setName(intervals.getCompilation().getShortName());
        setToolTipText(intervals.getCompilation().getMethod() + " - " + intervals.getName());
        setIcon(ImageUtilities.loadImage(Icons.INTERVALS));

        this.intervals = intervals;
        selection = new Selection();
        selection.put(intervals);
        selection.put(intervals.getControlFlowGraph());
        selection.addChangeListener(selectionChangeListener);

        viewSettings = new ViewSettings();
        canvas = new IntervalCanvas(this, viewSettings, intervals);
        JScrollPane scrollPane = new JScrollPane(canvas);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder());

        hsizeButtons = new JToggleButton[3];
        hsizeButtons[0] = createSizeButton(Icons.HSIZE_SMALL, "Horizontal Size: Small", HSIZE_PROP, ViewSettings.SMALL);
        hsizeButtons[1] = createSizeButton(Icons.HSIZE_MEDIUM, "Horizontal Size: Medium", HSIZE_PROP, ViewSettings.MEDIUM);
        hsizeButtons[2] = createSizeButton(Icons.HSIZE_LARGE, "Horizontal Size: Large", HSIZE_PROP, ViewSettings.LARGE);
        vsizeButtons = new JToggleButton[3];
        vsizeButtons[0] = createSizeButton(Icons.VSIZE_SMALL, "Vertical Size: Small", VSIZE_PROP, ViewSettings.SMALL);
        vsizeButtons[1] = createSizeButton(Icons.VSIZE_MEDIUM, "Vertical Size: Medium", VSIZE_PROP, ViewSettings.MEDIUM);
        vsizeButtons[2] = createSizeButton(Icons.VSIZE_LARGE, "Vertical Size: Large", VSIZE_PROP, ViewSettings.LARGE);
        Toolbar toolbar = new Toolbar();
        toolbar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border"));
        toolbar.add(hsizeButtons[0]);
        toolbar.add(hsizeButtons[1]);
        toolbar.add(hsizeButtons[2]);
        toolbar.addSeparator();
        toolbar.add(vsizeButtons[0]);
        toolbar.add(vsizeButtons[1]);
        toolbar.add(vsizeButtons[2]);

        intervalStatusLabel = createStatusLabel("Nb.Editor.Status.leftBorder", new Dimension(200, 18));
        instructionStatusLabel = createStatusLabel("Nb.Editor.Status.innerBorder", null);
        blockStatusLabel = createStatusLabel("Nb.Editor.Status.rightBorder", new Dimension(200, 18));
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.add(intervalStatusLabel, BorderLayout.WEST);
        statusBar.add(blockStatusLabel, BorderLayout.EAST);
        statusBar.add(instructionStatusLabel, BorderLayout.CENTER);

        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        hsizeButtons[2].doClick();
        vsizeButtons[2].doClick();
    }

    public Selection getSelection() {
        return selection;
    }

    private JToggleButton createSizeButton(String icon, String tooltip, String propName, int propValue) {
        JToggleButton button = new JToggleButton(new ImageIcon(ImageUtilities.loadImage(icon)));
        button.setToolTipText(tooltip);
        button.putClientProperty(propName, propValue);
        button.addActionListener(sizeButtonListener);
        return button;
    }

    private JLabel createStatusLabel(String border, Dimension dimension) {
        JLabel label = new JLabel(" ");
        label.setOpaque(true);
        label.setBorder((Border) UIManager.get(border));
        if (dimension != null) {
            label.setPreferredSize(dimension);
        }
        return label;
    }

    @Override
    public void componentActivated() {
        super.componentActivated();
        canvas.requestFocus();
        SelectionManager.getDefault().setSelection(selection);
    }

    @Override
    public void componentClosed() {
        super.componentClosed();
        SelectionManager.getDefault().removeSelection(selection);
    }


    private ActionListener sizeButtonListener = new ActionListener() {
        public void actionPerformed(ActionEvent event) {
            Object hsize = ((JComponent) event.getSource()).getClientProperty(HSIZE_PROP);
            if (hsize instanceof Integer) {
                viewSettings.setHorizontalSize((Integer) hsize);
            }
            Object vsize = ((JComponent) event.getSource()).getClientProperty(VSIZE_PROP);
            if (vsize instanceof Integer) {
                viewSettings.setVerticalSize((Integer) vsize);
            }

            canvas.calcViewData();
            
            for (int i = 0; i < hsizeButtons.length; i++) {
                hsizeButtons[i].setSelected(viewSettings.hsize == i);
            }
            for (int i = 0; i < vsizeButtons.length; i++) {
                vsizeButtons[i].setSelected(viewSettings.vsize == i);
            }
        }
    };


    private ChangeListener selectionChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            if (selectionUpdating) {
                return;
            }
            selectionUpdating = true;
            updateBlockSelection();
            updateIntervalSelection();
            selectionUpdating = false;
        }
    };

    private void updateBlockSelection() {
        assert selection.get(ControlFlowGraph.class) == intervals.getControlFlowGraph();
        BasicBlock[] blocks = selection.get(BasicBlock[].class);
        if (blocks == null || blocks.length == 0 || (blocks.length == 1 && blocks[0] == canvas.getSelectedBlock())) {
            return;
        }

        int leftCol = Integer.MAX_VALUE;
        int rightCol = Integer.MIN_VALUE;
        for (BasicBlock block : blocks) {
            leftCol = Math.min(leftCol, block.getFirstLirId());
            rightCol = Math.max(rightCol, block.getLastLirId());
        }
        canvas.ensureColumnVisible(rightCol);
        canvas.ensureColumnVisible(leftCol);
    }

    private void updateIntervalSelection() {
        assert selection.get(IntervalList.class) == intervals;
        ChildInterval child = selection.get(ChildInterval.class);
        if (child == null || child == canvas.getSelectedInterval()) {
            return;
        }

        canvas.ensureColumnVisible(child.getFrom());
        canvas.ensureRowVisible(intervals.getIntervals().indexOf(child.getParent()));
    }

    protected void updateCanvasSelection() {
        if (selectionUpdating) {
            return;
        }
        selectionUpdating = true;
        if (canvas.getSelectedBlock() != null) {
            selection.put(new BasicBlock[]{canvas.getSelectedBlock()});
        }
        if (canvas.getSelectedInterval() != null) {
            selection.put(canvas.getSelectedInterval());
        }
        selectionUpdating = false;
    }


    protected void setIntervalStatusText(String text) {
        intervalStatusLabel.setText(" " + text);
    }

    protected void setBlockStatusText(String text) {
        blockStatusLabel.setText(" " + text);
    }
    protected void setInstructionStatusText(String text) {
        instructionStatusLabel.setText(" " + text);
    }


    @Override
    protected CloneableTopComponent createClonedObject() {
        return new IntervalEditorTopComponent(intervals);
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }
}
