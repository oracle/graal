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
import org.graalvm.visualizer.filterwindow.impl.FilterProfileNode;
import org.graalvm.visualizer.util.swing.DropdownButton;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.awt.Actions;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.Presenter;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.beans.BeanInfo;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Action that adds an existing filter to a profile. The filter is selected
 * from a carte made out of the default profile.
 *
 * @author sdedic
 */
@ActionID(
        category = LinkExistingFilter.ID_CATEGORY,
        id = LinkExistingFilter.ID_LINK_EXISTING_FILTER
)
@ActionRegistration(
        displayName = "#ACTION_LinkExistingFilter",
        enabledOn = @ActionState(
                type = FilterProfile.class,
                useActionInstance = true
        ),
        lazy = false
)
@ActionReferences({
        @ActionReference(path = "Menu/Edit", position = 350),
        @ActionReference(path = "IGV/Toolbars/FilterProfileWindow", position = 300)
})
@NbBundle.Messages({
        "ACTION_LinkExistingFilter=Add filter..."
})
public class LinkExistingFilter extends AbstractAction implements Presenter.Toolbar, Presenter.Menu, ContextAwareAction, PropertyChangeListener {
    public static final String ID_CATEGORY = "Filters"; // NOI18N
    public static final String ID_LINK_EXISTING_FILTER = "org.graalvm.visualizer.filterwindow.actions.LinkExistingFilter"; // NOI18N

    private static final String ICON_ADD_EXISTING = "org/graalvm/visualizer/filterwindow/images/addExistingFilter.png"; // NOI18N

    private DropdownButton popButton;

    private final Lookup context;
    private final ProfileService profileService;
    private final Lookup.Result<FilterProfile> profileResult;

    // @GuardedBy(EDT)
    private FilterProfile target;
    private List<Filter> filterList;
    private PropertyChangeListener weakPL;

    public LinkExistingFilter() {
        this(Lookup.getDefault());
    }

    private LinkExistingFilter(Lookup context) {
        this(context.lookup(FilterProfile.class), Lookup.getDefault().lookup(ProfileService.class), context);
    }

    private LinkExistingFilter(FilterProfile target, ProfileService profileService, Lookup context) {
        super(Bundle.ACTION_LinkExistingFilter());
        this.context = context;
        this.target = target;
        this.profileService = profileService;
        this.profileResult = context.lookupResult(FilterProfile.class);

        profileResult.addLookupListener(this::lookupChanged);
        updateFilterProfileAndList();
    }

    void setTarget(FilterProfile target) {
        if (this.target == target) {
            return;
        }
        if (this.target != null && weakPL != null) {
            this.target.removePropertyChangeListener(weakPL);
        }
        this.target = target;
        if (target == null) {
            return;
        }
        weakPL = WeakListeners.propertyChange(this, target);
        target.addPropertyChangeListener(weakPL);
    }

    void updateFilterProfileAndList() {
        FilterProfile prof = context.lookup(FilterProfile.class);
        setTarget(prof);
        if (prof == null) {
            filterList = Collections.emptyList();
            setEnabled(false);
            return;
        }
        Set<Filter> presentDefFilters = new HashSet<>();
        for (Filter tf : target.getProfileFilters()) {
            Filter df = profileService.findDefaultFilter(tf);
            presentDefFilters.add(df);
        }
        List<Filter> allDefFilters = new ArrayList<>(profileService.getDefaultProfile().getProfileFilters());
        allDefFilters.removeAll(presentDefFilters);
        filterList = allDefFilters;
        setEnabled(!filterList.isEmpty());
        if (popButton != null) {
            popButton.setEnabled(isEnabled());
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName() == null || FilterProfile.PROP_FILTERS.equals(evt.getPropertyName())) {
            updateFilterProfileAndList();
        }
    }

    void lookupChanged(LookupEvent e) {
        SwingUtilities.invokeLater(this::updateFilterProfileAndList);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
    }

    @Override
    public Component getToolbarPresenter() {
        if (popButton != null) {
            return popButton;
        }
        DropdownButton button = new DropdownButton("", ImageUtilities.loadImageIcon(ICON_ADD_EXISTING, false), true,
                this::populateMenu, this);
        button.setPopupEnabled(true);
        button.setEnabled(isEnabled());
        button.setToolTipText(Bundle.ACTION_LinkExistingFilter());
        return popButton = button;
    }

    private void populateMenu(JPopupMenu menu) {
        if (target == null) {
            return;
        }
        Node orig = FilterProfileNode.create(profileService.getDefaultProfile());
        Node[] origCh = orig.getChildren().getNodes(true);
        menu.add(Actions.forID(NewFilterAction.CATEGORY, NewFilterAction.ID));
        for (Node n : origCh) {
            Filter f = n.getLookup().lookup(Filter.class);
            if (filterList.contains(f)) {
                menu.add(new AddOneFilterAction(target, n.cloneNode()));
            }
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new LinkExistingFilter(lkp);
    }

    @Override
    public JMenuItem getMenuPresenter() {
        return null;
    }

    private static class AddOneFilterAction extends AbstractAction {
        private final Node filterDelegate;
        private final FilterProfile profile;

        AddOneFilterAction(FilterProfile profile, Node filterDelegate) {
            super(filterDelegate.getDisplayName(),
                    ImageUtilities.image2Icon(filterDelegate.getIcon(BeanInfo.ICON_COLOR_16x16)));
            this.filterDelegate = filterDelegate;
            this.profile = profile;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                profile.addSharedFilter(filterDelegate.getLookup().lookup(Filter.class));
            } catch (IOException ex) {
                // FIXME: report
            }
        }
    }
}
