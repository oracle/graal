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

import org.graalvm.visualizer.view.EditorTopComponent;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@NbBundle.Messages({
        "ACTION_ShowAll=Show all nodes",
        "DESC_ShowAllAction=Shows all nodes",
})
@ActionID(category = "Diagram", id = ShowAllAction.ID)
@ActionRegistration(displayName = "#ACTION_ShowAll",
        key = "O-X", lazy = true,
        iconBase = "org/graalvm/visualizer/view/images/expand.gif")
@ActionReferences({
        @ActionReference(path = "NodeGraphViewer/Actions", position = 3000, separatorAfter = 3100),
        @ActionReference(path = "NodeGraphViewer/ContextActions", position = 3000, separatorAfter = 3100),
        @ActionReference(path = "Menu/View", position = 2020),
        @ActionReference(path = "NodeGraphViewer/ContextActions", position = 2000, separatorBefore = 1950)
})
public final class ShowAllAction implements ActionListener {
    static final String ID = "org.graalvm.visualizer.view.actions.ShowAllAction"; // NOI18N

    @Override
    public void actionPerformed(ActionEvent e) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            editor.showAll();
        }
    }
}
