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
package at.ssw.visualizer.nc.view;

import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.nc.NCEditorKit;
import at.ssw.visualizer.nc.model.NCTextBuilder;
import at.ssw.visualizer.texteditor.view.AbstractTextViewTopComponent;
import java.io.Serializable;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Alexander Reder
 */
final class NCViewTopComponent extends AbstractTextViewTopComponent {

    private static NCViewTopComponent instance;
    private static final String PREFERRED_ID = "NCViewTopComponent";

    private NCViewTopComponent() {
        super(new NCEditorKit());
        setName("Native Code");
        setToolTipText("Native Code");

    }

    @Override
    protected String getContent(ControlFlowGraph cfg, BasicBlock[] blocks) {
        NCTextBuilder builder = new NCTextBuilder();
        return builder.buildView(cfg, blocks);
    }
    
    // <editor-fold defaultstate="collapsed" desc=" Singleton and Persistence Code ">
    public static synchronized NCViewTopComponent getDefault() {
        if (instance == null) {
            instance = new NCViewTopComponent();
        }
        return instance;
    }

    public static synchronized NCViewTopComponent findInstance() {
         return (NCViewTopComponent) WindowManager.getDefault().findTopComponent(PREFERRED_ID);
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_ALWAYS;
    }

    @Override
    public void componentOpened() {
    // TODO add custom code on component opening
    }

    @Override
    public void componentClosed() {
    // TODO add custom code on component closing
    }

    /** replaces this in object stream */
    @Override
    public Object writeReplace() {
        return new ResolvableHelper();
    }

    @Override
    protected String preferredID() {
        return PREFERRED_ID;
    }

    final static class ResolvableHelper implements Serializable {

        private static final long serialVersionUID = 1L;

        public Object readResolve() {
            return NCViewTopComponent.getDefault();
        }
    }
    //</editor-fold>
        
}
