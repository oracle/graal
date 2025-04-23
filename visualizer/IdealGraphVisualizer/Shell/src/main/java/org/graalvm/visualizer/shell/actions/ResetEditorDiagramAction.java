/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.shell.actions;

import org.graalvm.visualizer.filter.DiagramFilters;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.view.api.DiagramEvent;
import org.graalvm.visualizer.view.api.DiagramListener;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.netbeans.api.editor.EditorActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * Will reset the diagram - remove the custom filter applied on it.
 *
 * @author sdedic
 */
@NbBundle.Messages({
        "reset-custom-script=Reset to Base"
})
public class ResetEditorDiagramAction extends AbstractGraphShellAction
        implements DiagramListener {

    private DiagramListener registeredL;

    public ResetEditorDiagramAction(Map<String, ?> attrs, Lookup lkp) {
        super(attrs, lkp);
    }

    @EditorActionRegistration(
            name = "reset-custom-script",
            // register for all mime types
            mimeType = "",
            iconResource = "org/graalvm/visualizer/shell/resources/reset.png",
            toolBarPosition = 120
    )
    public static ResetEditorDiagramAction create(Map<String, ?> params) {
        return new ResetEditorDiagramAction(params, null);
    }

    @Override
    protected void diagramModelChanged(DiagramModel previous, DiagramModel current) {
        synchronized (this) {
            if (previous != null && registeredL != null) {
                previous.removeDiagramListener(registeredL);
                registeredL = null;
            }
            if (current != null) {
                registeredL = WeakListeners.create(DiagramListener.class, this, current);
                current.addDiagramListener(registeredL);
            }
        }
    }

    @Override
    public void stateChanged(DiagramEvent ev) {
        refresh();
    }

    @Override
    public void diagramChanged(DiagramEvent ev) {
        refresh();
    }

    @Override
    public void diagramReady(DiagramEvent ev) {
        refresh();
    }


    @Override
    protected boolean computeEnabled(DiagramModel model, FilterProvider filterSource) {
        if (model == null) {
            return false;
        }
        DiagramFilters modelFilters = model.getLookup().lookup(DiagramFilters.class);
        return modelFilters != null && !modelFilters.getScriptFilters().isEmpty();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DiagramModel mdl = findDiagramModel();
        if (mdl == null) {
            return;
        }
        DiagramFilters modelFilters = mdl.getLookup().lookup(DiagramFilters.class);
        if (modelFilters == null) {
            return;
        }
        modelFilters.applyScriptFilter(null, null, false, null);
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ResetEditorDiagramAction(attributes, actionContext);
    }
}
