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

import org.graalvm.visualizer.view.api.TimelineModel;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@NbBundle.Messages({
        "ACTION_HideDuplicates=Hide Duplicates",
        "DESC_HideDuplicates=Hide graphs which are the same as the previous graph"
})
@ActionID(category = "Diagram", id = HideDuplicatesAction.ID)
@ActionRegistration(displayName = "#ACTION_HideDuplicates", iconBase = "org/graalvm/visualizer/view/images/hideDuplicates.png",
        checkedOn = @ActionState(property = "hideDuplicates"))
@ActionReference(path = "NodeGraphViewer/Actions", position = 5000)
public final class HideDuplicatesAction implements ActionListener {

    static final String ID = "org.graalvm.visualizer.view.actions.HideDuplicatesAction";

    private final TimelineModel model;

    public HideDuplicatesAction(TimelineModel model) {
        this.model = model;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        model.setHideDuplicates(!model.isHideDuplicates());
    }
}
