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
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NbBundle.Messages({
        "ACTION_RemoveFilterAction=Remove filter"
})
@ActionID(
        category = "Filters",
        id = RemoveFilterAction.ID
)
@ActionRegistration(
        displayName = "#ACTION_RemoveFilterAction",
        iconBase = "org/graalvm/visualizer/filterwindow/images/minus.png",
        lazy = true
)
@ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 300),
        @ActionReference(path = "IGV/ContextActions/Filter", position = 300),
        @ActionReference(path = "IGV/Toolbars/FilterProfileWindow", position = 300)
})
public final class RemoveFilterAction extends AbstractAction {
    public static final String CATEGORY = "Filters"; // NOI18N
    public static final String ID = "org.graalvm.visualizer.filterwindow.actions.RemoveFilterAction"; // NOI18N

    private final List<Filter> filters;
    private final ProfileService profileService;

    public RemoveFilterAction(List<Filter> filters) {
        this.filters = new ArrayList<>(filters);
        profileService = Lookup.getDefault().lookup(ProfileService.class);
    }

    private FilterProfile parentProfile;

    @NbBundle.Messages({
            "TITLE_RemoveFilterConfirmation1=Confirm Filter Delete",
            "# {0} - filter name",
            "CONFIRM_FilterRemove1=Do you really want to delete Filter {0} ?",
            "# {0} - number of filters to delete",
            "CONFIRM_FilterRemove2=Do you really want to delete {0} Filters ?",
            "TITLE_ConfirmRemoveFilterIsUsed=Confirm Delete of filters in use",
            "# {0} - list of affected profiles, one per line.",
            "CONFIRM_FilterIsUsed=The selected filter(s) are used in the following profiles: {0}. "
                    + "The filter(s) will be deleted from those profiles as well.",
            "# {0} - filter name",
            "CONFIRM_FilterIsUsedListItem=- {0}\n",
            "TITLE_ErrorDeletingFilter=Could not delete filter",
            "# {0} - profile name",
            "# {1} - exception message",
            "ERR_ErrorDeletingAssociatedFilter=Associated filter in profile {0} could not be deleted: {1}",
            "# {0} - filter name",
            "# {1} - exception message",
            "ERR_ErrorDeletingFilter=Filter {0} could not be deleted: {1}",
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        if (profileService == null) {
            return;
        }
        FilterProfile candidate = null;
        for (Filter f : filters) {
            FilterProfile parent = f.getLookup().lookup(FilterProfile.class);
            if (candidate == null) {
                candidate = parent;
            } else if (candidate != parent) {
                candidate = null;
                break;
            }
        }
        if (candidate == null) {
            Toolkit.getDefaultToolkit().beep();
            return;
        }
        parentProfile = candidate;
        filters.retainAll(parentProfile.getProfileFilters());

        boolean warn = true;
        if (parentProfile == profileService.getDefaultProfile()) {
            Set<FilterProfile> allLocations = new HashSet<>();
            Set<String> profileNames = new HashSet<>();
            for (Filter f : filters) {
                Set<FilterProfile> locations = profileService.findLocations(f);
                allLocations.addAll(locations);
            }
            allLocations.stream().
                    map(FilterProfile::getName).
                    sorted().
                    forEach(profileNames::add);
            if (!profileNames.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String s : profileNames) {
                    sb.append(Bundle.CONFIRM_FilterIsUsedListItem(s));
                }
                NotifyDescriptor.Confirmation conf = new NotifyDescriptor.Confirmation(
                        Bundle.CONFIRM_FilterIsUsed(sb.toString()),
                        Bundle.TITLE_ConfirmRemoveFilterIsUsed(),
                        NotifyDescriptor.YES_NO_OPTION
                );

                if (DialogDisplayer.getDefault().notify(conf) != NotifyDescriptor.YES_OPTION) {
                    return;
                }
                // first remove the filters from the identified profiles:
                String pname = "";
                try {
                    for (FilterProfile p : allLocations) {
                        pname = p.getName();
                        for (Filter f : filters) {
                            profileService.deleteFromAllProfiles(f);
                        }
                    }
                } catch (IOException ex) {
                    NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
                            Bundle.ERR_ErrorDeletingAssociatedFilter(pname, ex.toString()),
                            NotifyDescriptor.ERROR_MESSAGE
                    );
                    DialogDisplayer.getDefault().notifyLater(msg);
                }
                warn = false;
            }
        }
        if (warn) {
            NotifyDescriptor.Confirmation conf;

            if (filters.size() == 1) {
                conf = new NotifyDescriptor.Confirmation(
                        Bundle.CONFIRM_FilterRemove1(filters.get(0).getName()),
                        Bundle.TITLE_RemoveFilterConfirmation1(),
                        NotifyDescriptor.YES_NO_OPTION);
            } else {
                conf = new NotifyDescriptor.Confirmation(
                        Bundle.CONFIRM_FilterRemove2(filters.size()),
                        Bundle.TITLE_RemoveFilterConfirmation1(),
                        NotifyDescriptor.YES_NO_OPTION);
            }
            if (DialogDisplayer.getDefault().notify(conf) != NotifyDescriptor.YES_OPTION) {
                return;
            }
            String fname = "";
            try {
                for (Filter f : filters) {
                    fname = f.getName();
                    profileService.deleteFilter(f);
                }
            } catch (IOException ex) {
                NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
                        Bundle.ERR_ErrorDeletingFilter(fname, ex.toString()),
                        NotifyDescriptor.ERROR_MESSAGE
                );
                DialogDisplayer.getDefault().notifyLater(msg);
            }
        }
    }
}
