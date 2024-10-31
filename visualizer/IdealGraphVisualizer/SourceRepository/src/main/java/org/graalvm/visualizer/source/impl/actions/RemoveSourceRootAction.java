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

package org.graalvm.visualizer.source.impl.actions;

import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.explorer.ExplorerManager;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.HelpCtx;
import org.openide.util.Mutex;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallbackSystemAction;

import javax.swing.AbstractAction;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.beans.PropertyVetoException;
import java.io.IOException;


@NbBundle.Messages({
        "ACTION_RemoveSourceRoot=Remove"
})
public class RemoveSourceRootAction extends CallbackSystemAction {
    public RemoveSourceRootAction() {
        putValue("noIconInMenu", Boolean.TRUE); //NOI18N
    }

    protected void initialize() {
        super.initialize();
    }

    public Object getActionMapKey() {
        return "delete"; // NOI18N
    }

    public String getName() {
        return Bundle.ACTION_RemoveSourceRoot();
    }

    public HelpCtx getHelpCtx() {
        return new HelpCtx(RemoveSourceRootAction.class);
    }

    protected String iconResource() {
        return "org/openide/resources/actions/delete.gif"; // NOI18N
    }

    protected boolean asynchronous() {
        return true;
    }

    public static class Perfomer extends AbstractAction implements Runnable {
        private boolean confirmDelete;
        private final ExplorerManager manager;

        public Perfomer(ExplorerManager manager, boolean confirmDelete) {
            this.confirmDelete = confirmDelete;
            this.manager = manager;
        }

        @Override
        public boolean isEnabled() {
            return super.isEnabled();
        }

        @Override
        public void actionPerformed(ActionEvent ev) {
            ExplorerManager em = manager;
            if (em == null) {
                return;
            }

            final Node[] sel = em.getSelectedNodes();
            if ((sel == null) || (sel.length == 0)) {
                return;
            }

            // perform action if confirmed
            if (!confirmDelete || doConfirm(sel)) {
                // clear selected nodes
                try {
                    em.setSelectedNodes(new Node[]{});
                } catch (PropertyVetoException e) {
                    // never thrown, setting empty selected nodes cannot be vetoed
                }

                doDestroy(sel);

                // disables the action in AWT thread
                Mutex.EVENT.readAccess(this);
            }
        }

        /**
         * Disables the action.
         */
        @Override
        public void run() {
            assert EventQueue.isDispatchThread();
            setEnabled(false);
        }

        // ExplorerActionsImpl and openide.compat/src/org/openide/explorer/ExplorerActions.java
        @NbBundle.Messages({
                "# {0} - name", "MSG_ConfirmRemoveObject=Are you sure you want to remove {0} from the configuration ? The contained files WILL NOT be removed from the disk.",
                "MSG_ConfirmRemoveObjectTitle=Confirm Object Removal",
                "# {0} - number of objects", "MSG_ConfirmRemoveObjects=Are you sure you want to remove these {0} items from the configuration ? The contained files WILL NOT be removed from the disk.",
                "MSG_ConfirmRemoveObjectsTitle=Confirm Multiple Object Removal"
        })
        private boolean doConfirm(Node[] sel) {
            String message;
            String title;
            boolean customDelete = true;

            for (int i = 0; i < sel.length; i++) {
                if (!Boolean.TRUE.equals(sel[i].getValue("customDelete"))) { // NOI18N
                    customDelete = false;

                    break;
                }
            }

            if (customDelete) {
                return true;
            }

            if (sel.length == 1) {
                message = Bundle.MSG_ConfirmRemoveObject(
                        sel[0].getDisplayName()
                );
                title = Bundle.MSG_ConfirmRemoveObjectTitle();
            } else {
                message = Bundle.MSG_ConfirmRemoveObjects(
                        Integer.valueOf(sel.length)
                );
                title = Bundle.MSG_ConfirmRemoveObjectsTitle();
            }

            NotifyDescriptor desc = new NotifyDescriptor.Confirmation(message, title, NotifyDescriptor.YES_NO_OPTION);

            return NotifyDescriptor.YES_OPTION.equals(DialogDisplayer.getDefault().notify(desc));
        }

        private void doDestroy(final Node[] sel) {
            for (int i = 0; i < sel.length; i++) {
                try {
                    sel[i].destroy();
                } catch (IOException e) {
                    Exceptions.printStackTrace(e);
                }
            }
        }

    }
}
