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
package at.ssw.visualizer.texteditor.view;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.texteditor.EditorKit;
import java.awt.BorderLayout;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.windows.TopComponent;

/**
 *
 * @author Alexander Reder
 */
public abstract class AbstractTextViewTopComponent extends TopComponent {
    
    protected ControlFlowGraph curCFG;
    protected BasicBlock[] curBlocks;

    private JEditorPane editorPane;
    
    // EditorKit must be set in the imlementation class
    public AbstractTextViewTopComponent(EditorKit kit) {
        editorPane = new JEditorPane();
        editorPane.setEditorKit(kit);
        editorPane.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(editorPane);
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
        curCFG = null;
        curBlocks = null;
    }


    private ChangeListener selectionChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            updateContent();
        }
    };

    protected void updateContent() {
        Selection selection = SelectionManager.getDefault().getCurSelection();
        ControlFlowGraph newCFG = selection.get(ControlFlowGraph.class);
        BasicBlock[] newBlocks = selection.get(BasicBlock[].class);

        if (newCFG == null || newBlocks == null || newBlocks.length == 0) {
            editorPane.setText("No block selected\n");
        } else if (curCFG != newCFG || !Arrays.equals(curBlocks, newBlocks)) {
            editorPane.setText(getContent(newCFG, newBlocks));
        }
        curCFG = newCFG;
        curBlocks = newBlocks;
    }

    protected abstract String getContent(ControlFlowGraph cfg, BasicBlock[] blocks);
    
}
