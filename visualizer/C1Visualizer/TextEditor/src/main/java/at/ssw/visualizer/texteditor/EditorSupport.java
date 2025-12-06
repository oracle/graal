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

import at.ssw.visualizer.model.Compilation;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.texteditor.model.Text;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import javax.swing.text.EditorKit;
import javax.swing.text.StyledDocument;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.EditCookie;
import org.openide.text.CloneableEditorSupport;
import org.openide.windows.CloneableOpenSupport;

/**
 * Abstract template class of a <code> EditorSupport </code> class of the 
 * Visulizer.
 * 
 * The <code> text </code> field must be initialized by the implementing class
 * and the methods <code> createCloneableEditor </code> and <code> 
 * initializeCloneableEditor </code> must be overwritten by the implenting class.
 * <code> createCloneableEditor </code> must return a custom implementation
 * of the <code> Editor </code> class.
 * <code> initializeClonableEditor </code> is used to set the icon, e.g.
 * <code> editor.setIcon(Utilities.loadImage(IconsImage)); </code>.
 * 
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 * @author Alexander Reder
 */
public abstract class EditorSupport extends CloneableEditorSupport implements EditCookie, EditorCookie, EditorCookie.Observable {
   
    protected ControlFlowGraph cfg;
    protected Text text;

    protected EditorSupport(ControlFlowGraph cfg) {
        super(new Env());
        ((Env) this.env).editorSupport = this;
        this.cfg = cfg;
    }
    
    public ControlFlowGraph getControlFlowGraph() {
        return cfg;
    }
    
    @Override
    protected StyledDocument createStyledDocument(EditorKit kit) {
        StyledDocument doc = super.createStyledDocument(kit);

        // Back-link from Document to our internal data model.
        doc.putProperty(Text.class, text);
        doc.putProperty(Compilation.class, cfg.getCompilation());
        doc.putProperty(ControlFlowGraph.class, cfg);

        return doc;
    }
    
    public abstract String getMimeType();
    
    protected String messageOpening() {
        return "Opening " + messageToolTip();
    }

    protected String messageOpened() {
        return "Opened " + messageToolTip();
    }
    
    protected String messageSave() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    protected String messageName() {
        return cfg.getCompilation().getShortName();
    }

    protected String messageToolTip() {
        return cfg.getCompilation().getMethod() + " - " + cfg.getName();
    }

    public static class Env implements CloneableEditorSupport.Env {

        private PropertyChangeSupport prop = new PropertyChangeSupport(this);
        private VetoableChangeSupport veto = new VetoableChangeSupport(this);

        /**
         * Back-link to outer class EditorSupport. Env must be a static class
         * because it is passed to super constructor of EditorSupport.
         */
        private EditorSupport editorSupport;

        public InputStream inputStream() throws IOException {
            return new ByteArrayInputStream(editorSupport.text.getText().getBytes());
        }

        public OutputStream outputStream() throws IOException {
            throw new IOException("Editor is readonly");
        }

        public Date getTime() {
            return editorSupport.cfg.getCompilation().getDate();
        }

        public String getMimeType() {
            return editorSupport.getMimeType();
        }

        public boolean isValid() {
            return true;
        }

        public boolean isModified() {
            return false;
        }

        public void markModified() throws IOException {
            throw new IOException("Editor is readonly");
        }

        public void unmarkModified() {
            // Nothing to do.
        }

        public CloneableOpenSupport findCloneableOpenSupport() {
            return editorSupport;
        }

        public void addPropertyChangeListener(PropertyChangeListener l) {
            prop.addPropertyChangeListener(l);
        }

        public void removePropertyChangeListener(PropertyChangeListener l) {
            prop.removePropertyChangeListener(l);
        }

        public void addVetoableChangeListener(VetoableChangeListener l) {
            veto.addVetoableChangeListener(l);
        }

        public void removeVetoableChangeListener(VetoableChangeListener l) {
            veto.removeVetoableChangeListener(l);
        }
    }

}
