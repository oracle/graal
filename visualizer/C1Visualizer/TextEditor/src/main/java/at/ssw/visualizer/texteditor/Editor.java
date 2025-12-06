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
package at.ssw.visualizer.texteditor;

import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.core.selection.SelectionProvider;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.texteditor.model.BlockRegion;
import at.ssw.visualizer.texteditor.model.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.openide.text.CloneableEditor;
import org.openide.windows.TopComponent;

/**
 * Abstract template class of a <code> Editor </code> class of the Visualizer.
 * 
 * Must be initialized with a custom <code> EditorSupport </code> class and the 
 * method <code> creatClonedObject </code> must be overwritten by 
 * the <code> Editor </code> implementation.
 * 
 * @author Alexander Reder
 */
public abstract class Editor extends CloneableEditor implements SelectionProvider {
    
    protected Selection selection;
    private boolean selectionUpdating;
    private BasicBlock[] curBlocks;
    private boolean initialized;
    
    protected Editor(EditorSupport support) {
        super(support);
        selection = new Selection();
        selection.put(support.getControlFlowGraph());        
        selection.addChangeListener(selectionListener);
    }
    
    public Selection getSelection() {
        return selection;
    }

    @Override
    protected void componentShowing() {
        super.componentShowing();
        if (!initialized) {
            getEditorPane().addCaretListener(caretListener);
            initialized = true;
        }
    }
    
    @Override
    protected void componentActivated() {
        super.componentActivated();
        SelectionManager.getDefault().setSelection(selection);
    }
    
    @Override
    protected void componentClosed() {
        super.componentClosed();
        SelectionManager.getDefault().removeSelection(selection);
    }
        
    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }
    
    private ChangeListener selectionListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            if (selectionUpdating) {
                return;
            }
            selectionUpdating = true;
            
            Text text = (Text) getEditorPane().getDocument().getProperty(Text.class);
            BasicBlock[] newBlocks = selection.get(BasicBlock[].class);
            
            if (newBlocks != null && newBlocks.length > 0 && !Arrays.equals(curBlocks, newBlocks)) {
                BlockRegion r = text.getBlocks().get(newBlocks[0]);
                int startOffset = r.getNameStart();
                int endOffset = r.getNameEnd();
                
                if (newBlocks.length > 1) {
                    for (BasicBlock b : newBlocks) {
                        r = text.getBlocks().get(b);
                        startOffset = Math.min(startOffset, r.getStart());
                        endOffset = Math.max(endOffset, r.getEnd());
                    }
                }
                
                getEditorPane().select(startOffset, endOffset);
            }
            curBlocks = newBlocks;
            selectionUpdating = false;
        }
    };
    
    
    private CaretListener caretListener = new CaretListener() {
        public void caretUpdate(CaretEvent event) {
            if (selectionUpdating) {
                return;
            }
            selectionUpdating = true;
            
            Text text = (Text) getEditorPane().getDocument().getProperty(Text.class);
            List<BasicBlock> newBlocks = new ArrayList<BasicBlock>();
            int startOffset = Math.min(event.getDot(), event.getMark());
            int endOffset = Math.max(event.getDot(), event.getMark());
            
            for (BlockRegion region : text.getBlocks().values()) {
                if (region.getStart() <= endOffset && region.getEnd() > startOffset) {
                    newBlocks.add(region.getBlock());
                }
            }
            
            curBlocks = newBlocks.toArray(new BasicBlock[newBlocks.size()]);
            selection.put(curBlocks);
            selectionUpdating = false;
        }
    };
    
}
