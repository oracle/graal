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
package org.graalvm.visualizer.view.actions;

import org.graalvm.visualizer.view.DiagramViewModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;

@NbBundle.Messages({
        "ACTION_NextDiagramAction=Show next graph",
        "DESC_SelectNextDiagram=Show next graph of current group"
})
@ActionID(category = "Diagram", id = NextDiagramAction.ID)
@ActionRegistration(
        displayName = "#ACTION_NextDiagramAction",
        iconBase = "org/graalvm/visualizer/view/images/next_diagram.png",
        enabledOn = @ActionState(property = "positions",
                checkedValue = ActionState.NON_NULL_VALUE,
                useActionInstance = true)
)
@ActionReferences({
        @ActionReference(path = "NodeGraphViewer/Actions", position = 1500),
        @ActionReference(path = "NodeGraphViewer/ContextActions", position = 1500, separatorAfter = 1550),
        @ActionReference(path = "Menu/View", position = 2010)
})
public final class NextDiagramAction extends AbstractAction {
    static final String ID = "org.graalvm.visualizer.view.actions.NextDiagramAction"; // NOI18N

    private final DiagramViewModel model;

    public NextDiagramAction(DiagramViewModel model) {
        this.model = model;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        int fp = model.getDiagramPeers().getFirstPosition();
        int sp = model.getDiagramPeers().getSecondPosition();
        if (sp != model.getPositions().size() - 1) {
            int nfp = fp + 1;
            int nsp = sp + 1;
            model.getDiagramPeers().setPositions(nfp, nsp);
        }
    }

    @Override
    public boolean isEnabled() {
        return model.getDiagramPeers().getSecondPosition() < model.getPositions().size() - 1;
    }
}
