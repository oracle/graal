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
package org.graalvm.visualizer.filterwindow.actions;

import org.graalvm.visualizer.filterwindow.FilterTopComponent;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

import javax.swing.Action;
import java.io.IOException;

@NbBundle.Messages({
        "# {0} - error message",
        "MSG_FilterCreateFailed=Could not create new filter: {0}",
        "ACTION_NewFilterAction=New Filter..."
})
@ActionID(category = NewFilterAction.CATEGORY, id = NewFilterAction.ID)
@ActionRegistration(
        displayName = "#ACTION_NewFilterAction",
        iconBase = "org/graalvm/visualizer/filterwindow/images/plus.png",
        lazy = true
)
@ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 300),
        @ActionReference(path = "IGV/Toolbars/FilterProfileWindow", position = 300)
})
public final class NewFilterAction extends CallableSystemAction {
    public static final String CATEGORY = "Filters"; // NOI18N
    public static final String ID = "org.graalvm.visualizer.filterwindow.actions.NewFilterAction"; // NOI18N

    public NewFilterAction() {
        putValue(Action.SHORT_DESCRIPTION, "Create new filter");
    }

    @Override
    public void performAction() {
        try {
            FilterTopComponent.findInstance().newFilter();
        } catch (IOException ex) {
            NotifyDescriptor.Message msg = new NotifyDescriptor.Message(Bundle.MSG_FilterCreateFailed(
                    ex.toString()), NotifyDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(msg);
        }
    }

    @Override
    public String getName() {
        return NbBundle.getMessage(NewFilterAction.class, "CTL_NewFilterAction");
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/filterwindow/images/plus.png";
    }
}
