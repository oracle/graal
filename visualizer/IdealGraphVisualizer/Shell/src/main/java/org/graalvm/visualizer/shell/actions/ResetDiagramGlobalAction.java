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

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.graalvm.visualizer.filter.DiagramFilters;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;
import org.openide.util.actions.ActionPresenterProvider;
import org.openide.util.actions.Presenter;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * Global action in main menu
 *
 * @author sdedic
 */
@NbBundle.Messages({
        "ACTION_ResetDiagram=Reset to base"
})
@ActionRegistration(
        displayName = "#ACTION_ResetDiagram",
        key = "reset-diagram",
        iconBase = "org/graalvm/visualizer/shell/resources/reset.png",
        lazy = true
)
@ActionID(
        category = "IGV", id = "org.graalvm.visualizer.shell.actions.resetDiagram"
)
@ActionReferences({
        @ActionReference(path = "Shortcuts", name = "O-R")
})
public class ResetDiagramGlobalAction extends AbstractAction implements ActionListener, Presenter.Menu, Presenter.Toolbar, ChangedListener<RangeSliderModel> {
    private final Reference<DiagramFilters> refModel;

    public ResetDiagramGlobalAction(DiagramModel model) {
        refModel = new WeakReference<>(model.getLookup().lookup(DiagramFilters.class));
        /*
        ListenerSupport.addWeakListener(this, model.getDiagramChangedEvent());
        setEnabled(model.getCustomFilter() != null);
         */
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DiagramFilters m = refModel.get();
        if (m != null) {
            m.applyScriptFilter(null, null, false, null);
        }
    }

    @Override
    public void changed(RangeSliderModel source) {
        DiagramFilters m = refModel.get();
        if (m != null) {
            SwingUtilities.invokeLater(() -> setEnabled(m.getScriptFilters().isEmpty()));
        }
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return ActionPresenterProvider.getDefault().createMenuPresenter(this);
    }

    @Override
    public Component getToolbarPresenter() {
        return ActionPresenterProvider.getDefault().createToolbarPresenter(this);
    }

}
