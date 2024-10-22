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

package org.graalvm.visualizer.filterwindow.impl;

import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.ImageDecorator;
import org.openide.filesystems.StatusDecorator;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import java.awt.Image;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collections;

/**
 * @author sdedic
 */
public class FilterProfileNode extends AbstractNode {
    private final FilterProfile profile;
    private final ProfileService profileService;
    private final InstanceContent lookupContent;
    private volatile Image cachedIcon;

    public FilterProfileNode(FilterProfile profile, ProfileService profileService) {
        this(profile, profileService, new InstanceContent());
    }

    private FilterProfileNode(FilterProfile profile, ProfileService profileService, InstanceContent lkpContent) {
        super(new FilterChildren(profile, profileService), new AbstractLookup(lkpContent));
        this.profile = profile;
        this.profileService = profileService;
        this.lookupContent = lkpContent;

        lkpContent.add(profile);
        setName(profile.getName());
    }

    @Override
    public Image getOpenedIcon(int type) {
        return getIcon(type);
    }

    @Override
    public Image getIcon(int type) {
        if (cachedIcon == null) {
            Image img = ImageUtilities.loadImage("org/graalvm/visualizer/filterwindow/images/filter-profile.png"); // NOI18N
            ProfileStorage storage = profileService.getLookup().lookup(ProfileStorage.class);
            FileObject fld = storage.getProfileFolder(profile);
            try {
                StatusDecorator deco = fld.getFileSystem().getDecorator();
                if (deco instanceof ImageDecorator) {
                    img = ((ImageDecorator) deco).annotateIcon(img, type, Collections.singleton(fld));
                }
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            cachedIcon = img;
        }
        return cachedIcon;
    }

    static class Ch extends Children.Keys<FilterProfile> implements PropertyChangeListener {
        private final ProfileService profileService;

        public Ch(ProfileService profileService) {
            this.profileService = profileService;
        }

        @Override
        protected void removeNotify() {
            profileService.removePropertyChangeListener(this);
            super.removeNotify();
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            profileService.addPropertyChangeListener(this);
            setKeys(profileService.getProfiles());
        }


        @Override
        protected Node[] createNodes(FilterProfile t) {
            return new Node[]{new FilterProfileNode(t, profileService)};
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (ProfileService.PROP_PROFILES.equals(evt.getPropertyName())) {
                setKeys(profileService.getProfiles());
            }
        }
    }

    public static Node create(FilterProfile f) {
        return new FilterProfileNode(f,
                Lookup.getDefault().lookup(ProfileService.class));
    }

    public static Node createProfileParent() {
        return new AbstractNode(new Ch(Lookup.getDefault().lookup(ProfileService.class)));
    }
}
