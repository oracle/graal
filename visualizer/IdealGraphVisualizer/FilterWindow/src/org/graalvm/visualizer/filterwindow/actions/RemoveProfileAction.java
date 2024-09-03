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
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import java.awt.event.ActionEvent;
import java.io.IOException;
import javax.swing.AbstractAction;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.openide.awt.ActionReference;

/**
 *
 * @author sdedic
 */
@ActionID(category = RemoveProfileAction.CATEGORY, id = RemoveProfileAction.ID)
@ActionRegistration(
        displayName = "#ACTION_RemoveProfile",
        iconBase = "org/graalvm/visualizer/filterwindow/images/delete.png",
        lazy = true,
        enabledOn = @ActionState(type = FilterProfile.class, useActionInstance = true)
)
@ActionReference(path = "IGV/ContextActions/ManageProfiles", position = 100)
@NbBundle.Messages({
    "TITLE_RemoveProfile=Delete Profile Confirmation",
    "# {0} - profile name",
    "MSG_RemoveProfileConfirm=Do you want to delete filter profile {0} ?",
    "# {0} - error message",
    "ERR_CannotDeleteProfile=Could not delete profile: {0}",
    "ACTION_RemoveProfile=Delete Profile"
})
public class RemoveProfileAction extends AbstractAction {
    public static final String CATEGORY = "Filters"; // NOI18N
    public static final String ID = "org.graalvm.visualizer.filterwindow.actions.RemoveProfileAction"; // NOI18N
    
    private final ProfileService profileService;
    private final FilterProfile profile;

    public RemoveProfileAction(FilterProfile profile) {
        this.profile = profile;
        profileService = Lookup.getDefault().lookup(ProfileService.class);
    }

    @Override
    public boolean isEnabled() {
        return profile != profileService.getDefaultProfile();
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        NotifyDescriptor.Confirmation conf = new NotifyDescriptor.Confirmation(
                    Bundle.MSG_RemoveProfileConfirm(profile.getName()),
                    Bundle.TITLE_RemoveProfile(),
                    NotifyDescriptor.YES_NO_OPTION);
        if (DialogDisplayer.getDefault().notify(conf) != NotifyDescriptor.YES_OPTION) {
            return;
        }
        try {
            profileService.deleteProfile(profile);
        } catch (IOException ex) {
            NotifyDescriptor.Message err = new NotifyDescriptor.Message(
                            Bundle.ERR_CannotDeleteProfile(ex.toString()),
                            NotifyDescriptor.WARNING_MESSAGE);
            DialogDisplayer.getDefault().notifyLater(err);
        }
    }
                    
}
