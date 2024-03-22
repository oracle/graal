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

import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.IOException;

/**
 * @author sdedic
 */
@ActionID(category = "Filters", id = NewProfileAction.ID)
@ActionRegistration(
        displayName = "#ACTION_NewProfile",
        iconBase = "org/graalvm/visualizer/filterwindow/images/add.png",
        lazy = true
)
@ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 800),
        @ActionReference(path = "IGV/Toolbars/FilterProfileWindow", position = 300),
        @ActionReference(path = "IGV/ContextActions/ManageProfiles", position = 100)
})
@NbBundle.Messages({
        "LBL_NewProfileName=Name of the new profile:",
        "TITLE_NewProfile=New Filter Profile",
        "# {0} - profile name",
        "ERR_FilterProfileExists=Filter profile ''{0}'' already exists. A new name will be created.",
        "# {0} - error message",
        "ERR_CannotCreateProfile=Could not create profile: {0}",
        "ACTION_NewProfile=New Profile..."
})
public class NewProfileAction extends AbstractAction {
    private final ProfileService profileService;
    private final ProfileStorage storageService;

    public static final String CATEGORY = "Filters"; // NOI18N
    public static final String ID = "org.graalvm.visualizer.filterwindow.actions.NewProfileAction"; // NOI18N

    public NewProfileAction() {
        this(Lookup.getDefault());
    }

    public NewProfileAction(Lookup lkp) {
        this.profileService = lkp.lookup(ProfileService.class);
        this.storageService = profileService.getLookup().lookup(ProfileStorage.class);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        NotifyDescriptor.InputLine l = new NotifyDescriptor.InputLine(
                Bundle.LBL_NewProfileName(),
                Bundle.TITLE_NewProfile());
        if (DialogDisplayer.getDefault().notify(l) != NotifyDescriptor.OK_OPTION) {
            return;
        }
        String name = l.getInputText();

        FileObject parent = storageService.getProfileFolder(profileService.getDefaultProfile());
        String fn = FileUtil.findFreeFileName(parent, name, ""); // NOI18N
        for (FilterProfile p : profileService.getProfiles()) {
            if (p.getName().equalsIgnoreCase(name)) {
                NotifyDescriptor.Message conf = new NotifyDescriptor.Message(
                        Bundle.ERR_FilterProfileExists(name),
                        NotifyDescriptor.INFORMATION_MESSAGE);
                DialogDisplayer.getDefault().notifyLater(conf);
                name = fn;
                break;
            }
        }
        FilterProfile p;
        try {
            p = profileService.createProfile(name, profileService.getSelectedProfile());
        } catch (IOException ex) {
            NotifyDescriptor.Message err = new NotifyDescriptor.Message(
                    Bundle.ERR_CannotCreateProfile(ex.toString()),
                    NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(err);
            return;
        }
        profileService.setSelectedProfile(p);
    }
}
