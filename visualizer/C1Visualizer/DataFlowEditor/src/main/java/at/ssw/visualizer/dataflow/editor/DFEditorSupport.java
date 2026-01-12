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
package at.ssw.visualizer.dataflow.editor;

import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.IOException;
import org.openide.cookies.OpenCookie;
import org.openide.windows.CloneableOpenSupport;
import org.openide.windows.CloneableTopComponent;

/**
 * This class implements a cookie for opening the Data Flow Editor
 *
 * @author Stefan Loidl
 * @author Christian Wimmer
 */
public class DFEditorSupport extends CloneableOpenSupport implements OpenCookie {
    private ControlFlowGraph cfg;

    public DFEditorSupport(ControlFlowGraph cfg) {
        super(new Env());
        ((Env) env).editorSupport = this;
        this.cfg = cfg;
    }

    protected CloneableTopComponent createCloneableTopComponent() {
        return new DataFlowEditorTopComponent(cfg);
    }

    public String messageOpened() {
        return "Opened " + cfg.getCompilation().getMethod() + " - " + cfg.getName();
    }

    public String messageOpening() {
        return "Opening " + cfg.getCompilation().getMethod() + " - " + cfg.getName();
    }


    public static class Env implements CloneableOpenSupport.Env {
        private PropertyChangeSupport prop = new PropertyChangeSupport(this);
        private VetoableChangeSupport veto = new VetoableChangeSupport(this);
        private DFEditorSupport editorSupport;

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
