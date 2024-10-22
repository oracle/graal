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

import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerListener;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@NbBundle.Messages({
        "ACTION_SelectionMode=Selection mode",
        "DESC_SelectionMode=Switch mouse drag to selection mode"
})
@ActionID(category = "Diagram", id = SelectionModeAction.ID)
@ActionReference(path = "NodeGraphViewer/Actions", position = 6500, separatorAfter = 6600)
@ActionRegistration(displayName = "#ACTION_SelectionMode", iconBase = "org/graalvm/visualizer/view/images/selection_mode.png",
        checkedOn = @ActionState(
                property = "interactionMode", checkedValue = "SELECTION", listenOn = DiagramViewerListener.class,
                listenOnMethod = "interactionChanged"
        )
)
public final class SelectionModeAction implements ActionListener {
    static final String ID = "org.graalvm.visualizer.view.actions.SelectionModeAction"; // NOI18N

    private final DiagramViewer viewer;

    public SelectionModeAction(DiagramViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        viewer.setInteractionMode(DiagramViewer.InteractionMode.SELECTION);
    }
}
