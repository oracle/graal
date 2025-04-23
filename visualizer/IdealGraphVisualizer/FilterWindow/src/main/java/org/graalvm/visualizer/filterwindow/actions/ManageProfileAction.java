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
import org.graalvm.visualizer.filter.profiles.Profiles;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.SimpleProfileSelector;
import org.graalvm.visualizer.util.swing.ActionUtils;
import org.graalvm.visualizer.util.swing.DropdownButton;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotificationLineSupport;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.awt.Actions;
import org.openide.awt.Mnemonics;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.Presenter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

/**
 * @author sdedic
 */
@ActionID(
        category = ManageProfileAction.ID_CATEGORY,
        id = ManageProfileAction.ID_ACTION
)
@ActionRegistration(
        displayName = "#ACTON_ManageProfile_Edit",
        lazy = false
)
@ActionReferences({
        @ActionReference(path = "IGV/Toolbars/FilterProfileWindow", position = 100)
})
@NbBundle.Messages({
        "ACTON_ManageProfile_Edit=Edit profile...",
        "ACTON_ManageProfile_Add=Add profile..."
})
public class ManageProfileAction extends AbstractAction implements ActionListener,
        Presenter.Toolbar, ContextAwareAction, LookupListener {
    public static final String ID_CATEGORY = "Filters"; // NOI18N
    public static final String ID_ACTION = "org.graalvm.visualizer.filterwindow.actions.ManageProfileAction"; // NOI18N

    private static final String ICON_MANAGE_PROFILE = "org/graalvm/visualizer/filterwindow/images/customizer.png"; // NOI18N
    private static final String ICON_ADD_PROFILE = "org/graalvm/visualizer/filterwindow/images/add.png"; // NOI18N

    private final Lookup context;
    private final ProfileService profiles;
    private final Lookup.Result<FilterProfile> contextResult;
    /**
     * The currently selected profile
     */
    private FilterProfile target;

    private DropdownButton popButton;

    public ManageProfileAction() {
        this(null, Lookup.getDefault());
    }

    public ManageProfileAction(FilterProfile profile) {
        this(profile, Lookup.getDefault());
    }

    private ManageProfileAction(FilterProfile t, Lookup context) {
        this.context = context;
        this.profiles = Lookup.getDefault().lookup(ProfileService.class);

        contextResult = context.lookupResult(FilterProfile.class);
        contextResult.addLookupListener(WeakListeners.create(LookupListener.class, this, contextResult));
        updateUI();
    }

    protected void updateUI() {
        target = context.lookup(FilterProfile.class);
        if (target == null || target == profiles.getDefaultProfile()) {
            putValue(NAME, Bundle.ACTON_ManageProfile_Add());
        } else {
            putValue(NAME, Bundle.ACTON_ManageProfile_Edit());
        }
        if (popButton == null) {
            return;
        }
        popButton.setEnabled(true);
        popButton.setToolTipText((String) getValue(NAME));
        popButton.setIcon(ImageUtilities.loadImageIcon(mainIconResource(), false));
        popButton.setPopupEnabled(target != profiles.getDefaultProfile());
    }

    protected String mainIconResource() {
        boolean nonDefault = target != profiles.getDefaultProfile();
        return nonDefault ? ICON_MANAGE_PROFILE : ICON_ADD_PROFILE;
    }

    @Override
    public Component getToolbarPresenter() {
        if (popButton != null) {
            return popButton;
        }
        boolean nonDefault = target != profiles.getDefaultProfile();

        DropdownButton button = new DropdownButton("", ImageUtilities.loadImageIcon(
                mainIconResource(), false), true, this::populateMenu, this);
        if (nonDefault) {
            button.setPopupEnabled(true);
        }
        button.setEnabled(isEnabled());
        button.setToolTipText((String) getValue(NAME));
        return popButton = button;
    }

    private void populateMenu(JPopupMenu menu) {
        if (target == null || target == profiles.getDefaultProfile()) {
            return;
        }
        /*
        Action a;
        
        menu.add(this);
        a = action(NewProfileAction.ID);
        menu.add(a);
        a = action(RemoveProfileAction.ID);
        menu.add(a);
        */
        ActionUtils.populatePopupMenu(menu, null, "IGV/ContextActions/ManageProfiles", context);
    }

    private Action action(String id) {
        Action a = Actions.forID(RemoveFilterAction.CATEGORY, id);
        if (a instanceof ContextAwareAction) {
            a = ((ContextAwareAction) a).createContextAwareInstance(context);
        }
        return a;
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new ManageProfileAction(null, lkp);
    }

    @Override
    public void resultChanged(LookupEvent le) {
        SwingUtilities.invokeLater(this::updateUI);
    }

    /**
     * Provides an icon.
     * Must override, as this action directly appears in the popup menu, need
     * to provide an icon
     */
    @Override
    public final Object getValue(String name) {
        if ("iconBase".equals(name)) { // NOI18N
            return mainIconResource();
        }
        Object val = super.getValue(name);
        if (val == null) {
            if (SMALL_ICON.equals(name)) {
                val = ImageUtilities.loadImageIcon(mainIconResource(), false);
            }
        }

        return val;
    }

    @NbBundle.Messages({
            "TITLE_EditProfile=Edit a profile",
            "# {0} - underlying I/O error",
            "PROFILE_ErrorSavingProfile=Error saving profile definition: {0}",
            "BUTTON_ProfileSave=&Save"
    })

    static void manageProfile(ProfileService profiles, FilterProfile prof) {
        SimpleProfileSelector selector = Profiles.simpleSelector(prof);
        JButton saveButton = new JButton();
        Mnemonics.setLocalizedText(saveButton, Bundle.BUTTON_ProfileSave());

        EditProfilePanel panel = new EditProfilePanel(profiles);
        panel.setSelector(selector);

        Object[] opts = new Object[]{saveButton, DialogDescriptor.CANCEL_OPTION};
        DialogDescriptor dd = new DialogDescriptor(panel,
                Bundle.TITLE_EditProfile(), true,
                opts,
                DialogDescriptor.OK_OPTION, DialogDescriptor.BOTTOM_ALIGN, null, null
        );
        dd.setClosingOptions(opts);
        NotificationLineSupport nls = dd.createNotificationLineSupport();
        panel.setNotifier(nls);
        panel.setProfile(prof);

        panel.addPropertyChangeListener(x -> {
            if (x.getPropertyName() == null || "inputValid".equals(x.getPropertyName())) {
                saveButton.setEnabled(panel.isInputValid());
            }
        });

        if (DialogDisplayer.getDefault().notify(dd) != saveButton) {
            return;
        }
        String n = panel.getProfileName();
        try {
            if (!n.equals(prof.getName())) {
                profiles.renameProfile(prof, prof.getName());
            }
            if (panel.getSelector().isValid()) {
                panel.updateSelector();
                Profiles.saveSelector(panel.getSelector());
            }
        } catch (IOException ex) {
            NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
                    Bundle.PROFILE_ErrorSavingProfile(ex.toString())
            );
            DialogDisplayer.getDefault().notifyLater(msg);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        FilterProfile prof = context.lookup(FilterProfile.class);
        if (prof == profiles.getDefaultProfile()) {
            // act as new profile on default profile...
            Action a = ActionUtils.findAction(NewProfileAction.CATEGORY, NewProfileAction.ID, context);
            if (a != null) {
                a.actionPerformed(e);
            }
            return;
        }
        manageProfile(profiles, prof);
    }

    @ActionID(
            category = ManageProfileAction.ID_CATEGORY,
            id = ManageProfileAction.ID_ACTION + ".main"
    )
    @ActionRegistration(
            displayName = "#ACTON_ManageProfile_Edit",
            enabledOn = @ActionState(useActionInstance = true)
    )
    @ActionReferences({
            @ActionReference(path = "Menu/Edit", position = 850),
    })
    public static class ManageProfileMain extends AbstractAction {
        private final FilterProfile target;
        private final ProfileService profiles;

        public ManageProfileMain(FilterProfile profile) {
            this.target = profile;
            profiles = Lookup.getDefault().lookup(ProfileService.class);
        }

        @Override
        public boolean isEnabled() {
            return super.isEnabled() && profiles.getDefaultProfile() != target;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            manageProfile(profiles, target);
        }

    }
}
