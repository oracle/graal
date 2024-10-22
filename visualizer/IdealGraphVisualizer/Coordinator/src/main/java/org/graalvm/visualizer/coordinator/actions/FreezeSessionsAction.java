/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.coordinator.actions;

import org.graalvm.visualizer.coordinator.impl.SessionManagerImpl;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.util.ImageUtilities;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.event.ActionEvent;

/**
 * @author sdedic
 */
@ActionID(category = "IGV", id = FreezeSessionsAction.ID)
@ActionRegistration(
        displayName = "#ACTION_FreezeSessions",
        popupText = "#ACTION_FreezeSessions",
        lazy = false
)
@NbBundle.Messages({
        "ACTION_FreezeSessions=Freeze network sessions"
})
public class FreezeSessionsAction extends AbstractAction implements Presenter.Toolbar {
    public static final String ID = "org.graalvm.visualizer.coordinator.actions.FreezeSessionsAction";

    public FreezeSessionsAction() {
        putValue(SHORT_DESCRIPTION, Bundle.ACTION_FreezeSessions());
        putValue(AbstractAction.SMALL_ICON, new ImageIcon(ImageUtilities.loadImage(iconResource())));
    }

    protected String iconResource() {
        return "org/graalvm/visualizer/coordinator/images/freezeSessions.png";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        SessionManagerImpl.getInstance().freezeCurrentDocuments();
    }


    @Override
    public Component getToolbarPresenter() {
        JToggleButton b = new JToggleButton();
        Actions.connect(b, this);
        return b;
    }
}
