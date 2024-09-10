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

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * @author sdedic
 */
@ActionID(
        category = RenameFilterAction.CATEGORY,
        id = RenameFilterAction.ID
)
@ActionRegistration(
        displayName = "#ACTION_RenameFilter",
        iconBase = "org/graalvm/visualizer/filterwindow/images/minus.png",
        lazy = true
)
@ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 600),
        @ActionReference(path = "IGV/ContextActions/Filter", position = 300),
})
@NbBundle.Messages({
        "ACTION_RenameFilter=Rename filter...",
        "LABEL_RenameFilter=Filter Name:",
        "TITLE_RenameFilter=Rename Filter",
        "# {0} - new filter name",
        "# {1} - exception",
        "ERROR_CouldNotRenameFilter=Filter could not be renamed to {0}: {1}"
})
public class RenameFilterAction extends AbstractAction {
    public static final String CATEGORY = "Filters"; // NOI18N
    public static final String ID = "org.graalvm.visualizer.filterwindow.actions.RenameFilterAction"; // NOI18N

    private final Filter filter;
    private final ProfileService service;

    public RenameFilterAction(Filter filter) {
        this(filter, Lookup.getDefault().lookup(ProfileService.class));
    }

    RenameFilterAction(Filter filter, ProfileService service) {
        this.filter = filter;
        this.service = service;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DialogDescriptor.InputLine il = new DialogDescriptor.InputLine(Bundle.LABEL_RenameFilter(), Bundle.TITLE_RenameFilter());
        try {
            il.setInputText(filter.getName());

            if (DialogDisplayer.getDefault().notify(il) != DialogDescriptor.OK_OPTION) {
                return;
            }
            doRenameFilter(il.getInputText().trim());
        } catch (IOException ex) {
            NotifyDescriptor err = new DialogDescriptor.Message(
                    Bundle.ERROR_CouldNotRenameFilter(il.getInputText(), ex.toString()),
                    DialogDescriptor.ERROR_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(err);
        }
    }

    void doRenameFilter(String newName) throws IOException {
        Filter orig = service.findDefaultFilter(filter);
        if (orig != null && orig != filter) {
            if (orig.getName().equals(filter.getName())) {
                service.renameFilter(orig, newName);
            }
        }
        service.renameFilter(filter, newName);
    }
}
