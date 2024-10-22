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
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.Event;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

@NbBundle.Messages({
        "ACTION_ZoomIn=Zoom in"
})
@ActionID(category = "Diagram", id = ZoomInAction.ID)
@ActionRegistration(displayName = "#ACTION_ZoomIn",
        iconBase = "org/graalvm/visualizer/view/images/zoom_in.png", lazy = true)
@ActionReferences({
        @ActionReference(path = "NodeGraphViewer/Actions", position = 3500, separatorBefore = 3400),
        @ActionReference(path = "NodeGraphViewer/ContextActions", position = 4000, separatorBefore = 3950),
        @ActionReference(path = "Menu/View", position = 2070),
})
public final class ZoomInAction extends CallableSystemAction {
    public static final String ID = "org.graalvm.visualizer.view.actions.ZoomInAction"; // NOI18N

    @Override
    public void actionPerformed(ActionEvent ev) {
        if (isEnabled()) {
            org.openide.util.actions.ActionInvoker.invokeAction(
                    this, ev, asynchronous(), new Runnable() {
                        @Override
                        public void run() {
                            if ((ev.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                                performZoom(true);
                            } else {
                                performZoom(false);
                            }
                        }
                    }
            );
        } else {
            super.actionPerformed(ev);
        }
    }

    void performZoom(boolean origSize) {
        EditorTopComponent editor = EditorTopComponent.getActive();
        if (editor != null) {
            if (origSize) {
                editor.zoomTo(1.0f);
            } else {
                editor.zoomIn();
            }
        }
    }

    @Override
    public void performAction() {
        performZoom(false);
    }

    @Override
    public String getName() {
        return Bundle.ACTION_ZoomIn();
    }

    public ZoomInAction() {
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, Event.CTRL_MASK, false));
        putValue(Action.SHORT_DESCRIPTION, Bundle.ACTION_ZoomIn());
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
        return "org/graalvm/visualizer/view/images/zoom_in.png"; // NOI18N
    }
}
