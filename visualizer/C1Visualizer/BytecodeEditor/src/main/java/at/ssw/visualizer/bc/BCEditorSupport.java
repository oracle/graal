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
package at.ssw.visualizer.bc;

import at.ssw.visualizer.bc.icons.Icons;
import at.ssw.visualizer.bc.model.BCTextBuilder;
import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.texteditor.EditorSupport;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.openide.text.CloneableEditor;
import org.openide.util.ImageUtilities;

/**
 * Associates an Editor to a StyledDocument.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public class BCEditorSupport extends EditorSupport {

    public static final String MIME_TYPE = "text/x-compilation-bc";

    private Bytecodes bytecodes;

    public BCEditorSupport(Bytecodes bytecodes) {
        super(bytecodes.getControlFlowGraph());
        this.bytecodes = bytecodes;
        this.text = new BCTextBuilder().buildDocument(cfg);
    }

    public String getMimeType() {
        return MIME_TYPE;
    }
    
    public Bytecodes getBytecodes() {
        return bytecodes;
    }
    
    /**
     * Returns the Editor associated to the EditorSupport.
     *
     * @return      a new BCEditor
     */
    @Override
    protected CloneableEditor createCloneableEditor() {
        return new BCEditor(this);
    }

    /**
     * Returns the document associated to the EditorSupport and the EditorKit.
     *
     * @param   kit     the EditorKit
     * @return          the new created styled document
     */
    @Override
    protected StyledDocument createStyledDocument(EditorKit kit) {
        StyledDocument doc = super.createStyledDocument(kit);
        doc.putProperty(Bytecodes.class, bytecodes);
        doc.putProperty(ControlFlowGraph.class, bytecodes.getControlFlowGraph());
        return doc;
    }

    /**
     * Returns the name of the method which will be displayed on top of the
     * editor window.
     *
     * @return      the method name
     */
    @Override
    public String messageName() {
        return bytecodes.getControlFlowGraph().getShortName();
    }

    @Override
    protected String messageToolTip() {
        return bytecodes.getControlFlowGraph().getName();
    }
    
    @Override
    protected void initializeCloneableEditor(CloneableEditor editor) {
        super.initializeCloneableEditor(editor);
        editor.setIcon(ImageUtilities.loadImage(Icons.BYTECODE));
    }

}
