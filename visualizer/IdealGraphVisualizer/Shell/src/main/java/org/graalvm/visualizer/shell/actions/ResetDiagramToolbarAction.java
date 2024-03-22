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
import org.graalvm.visualizer.view.api.DiagramEvent;
import org.graalvm.visualizer.view.api.DiagramListener;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.ActionPresenterProvider;
import org.openide.util.actions.Presenter;

import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;

/**
 * Action installed into graph's toolbar - reset
 *
 * @author sdedic
 */
@NbBundle.Messages({
        "ACTION_ResetCustomScript=Reset to Base"
})
public class ResetDiagramToolbarAction extends ViewerToolbarAction implements ContextAwareAction, Presenter.Toolbar, Presenter.Popup, DiagramListener {
    private final DiagramModel model;
    private final DiagramFilters filters;

    public ResetDiagramToolbarAction() {
        this(null);
    }

    @Override
    public String getName() {
        return "reset-custom-script"; // NOI8N
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/shell/resources/reset.png";
    }

    private ResetDiagramToolbarAction(Lookup lkp) {
        if (lkp != null) {
            this.model = lkp.lookup(DiagramModel.class);
        } else {
            model = null;
        }
        if (model != null) {
            filters = model.getLookup().lookup(DiagramFilters.class);
            model.addDiagramListener(WeakListeners.create(DiagramListener.class, this, model));
        } else {
            filters = null;
        }
        putValue(Action.SHORT_DESCRIPTION, Bundle.ACTION_ResetCustomScript());
        computeEnabled();
    }

    private void computeEnabled() {
        setEnabled(filters != null && !filters.getScriptFilters().isEmpty());
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ResetDiagramToolbarAction(actionContext);
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
    }

    public void refresh() {
        if (SwingUtilities.isEventDispatchThread()) {
            computeEnabled();
        } else {
            SwingUtilities.invokeLater(this::computeEnabled);
        }
    }

    @Override
    public Component getToolbarPresenter() {
        return ActionPresenterProvider.getDefault().createToolbarPresenter(this);
    }

    @Override
    public JMenuItem getPopupPresenter() {
        return ActionPresenterProvider.getDefault().createPopupPresenter(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (filters == null) {
            return;
        }
        filters.applyScriptFilter(null, null, false, null);
    }
}
