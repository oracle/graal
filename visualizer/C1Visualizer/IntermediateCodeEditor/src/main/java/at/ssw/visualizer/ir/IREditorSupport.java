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
package at.ssw.visualizer.ir;

import at.ssw.visualizer.ir.icons.Icons;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.ir.model.IRTextBuilder;
import at.ssw.visualizer.texteditor.EditorSupport;
import org.openide.text.CloneableEditor;
import org.openide.util.ImageUtilities;

/**
 * Connects a ControlFlowGraph to a NetBeans text editor.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 * @author Alexander Reder
 */
public class IREditorSupport extends EditorSupport {
    
    public static final String MIME_TYPE = "text/x-compilation-ir";

    public IREditorSupport(ControlFlowGraph cfg) {
        super(cfg);
        this.text = new IRTextBuilder().buildDocument(cfg);
    }

    @Override
    public String getMimeType() {
        return MIME_TYPE;
    }
    
    @Override
    protected CloneableEditor createCloneableEditor() {
        return new IREditor(this);
    }

    
    @Override
    protected void initializeCloneableEditor(CloneableEditor editor) {
        super.initializeCloneableEditor(editor);
        editor.setIcon(ImageUtilities.loadImage(Icons.IR));
    }

}
