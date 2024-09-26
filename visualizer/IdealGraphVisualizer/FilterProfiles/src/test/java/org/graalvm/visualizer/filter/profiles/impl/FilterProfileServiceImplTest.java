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

package org.graalvm.visualizer.filter.profiles.impl;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.junit.Assert;
import org.junit.Ignore;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.RequestProcessor;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author sdedic
 */
public class FilterProfileServiceImplTest extends FilterProfileTestBase {
    private FileObject defaultStorage;
    private FileObject profileStorage;

    FilterProfileServiceImpl service;
    FilterProfileServiceImpl service2;

    public FilterProfileServiceImplTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
        File f = getWorkDir();
        FileObject fo = FileUtil.toFileObject(f);

        FileObject def = FileUtil.getConfigFile(FilterProfileServiceImpl.DEFAULT_PROFILE_FOLDER);
        defaultStorage = def.copy(fo, "Filters", "");
        profileStorage = FileUtil.getConfigFile(FilterProfileServiceImpl.PROFILES_FOLDER).copy(fo, "Profiles", "");

        for (FileObject prof : profileStorage.getChildren()) {
            for (FileObject flt : prof.getChildren()) {
                if (!"shadow".equals(flt.getExt())) {
                    continue;
                }
                FileObject orig = DataShadow.findOriginal(flt);
                if (orig != null && orig.getParent() == def) {
                    DataObject.find(flt).delete();
                    FileObject origCopy = defaultStorage.getFileObject(orig.getName(), orig.getExt());
                    DataShadow.create(DataFolder.findFolder(prof), origCopy.getName(), DataObject.find(origCopy));
                }
            }
        }

        service = new FilterProfileServiceImpl(profileStorage, defaultStorage,
                this::createProfile);
        service2 = new FilterProfileServiceImpl(profileStorage, defaultStorage, null);

        monitorFolder(profileStorage);
        monitorFolder(defaultStorage);
    }

    private FilterProfile createProfile(ProfileService srv, FileObject f) {
        return new MockProfile(f);
    }

    static class MockProfile implements FilterProfile {
        FileObject storage;
        FilterChain selected = new FilterChain();
        FilterChain filters = new FilterChain();

        public MockProfile(FileObject storage) {
            this.storage = storage;
        }

        @Override
        public String getName() {
            return storage.getName();
        }

        @Override
        public void setName(String profileName) throws IOException {
        }

        @Override
        public void moveDown(Filter f) throws IOException {
        }

        @Override
        public void setEnabled(Filter f, boolean status) throws IOException {
        }

        @Override
        public void moveUp(Filter f) throws IOException {
        }

        @Override
        public Filter addSharedFilter(Filter f) throws IOException {
            return null;
        }

        @Override
        public List<Filter> getProfileFilters() {
            return Collections.emptyList();
        }

        @Override
        public FilterSequence getSelectedFilters() {
            return selected;
        }

        @Override
        public FilterSequence getAllFilters() {
            return filters;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
        }
    }

    static class PCL implements PropertyChangeListener {
        Set<String> props = new HashSet<>();

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            props.add(evt.getPropertyName());
        }
    }

    public void testGetSetSelectedProfile() throws Exception {
        assertSame("Initially a default profile must be selected", service.getDefaultProfile(), service.getSelectedProfile());

        FilterProfile nonDefault = null;

        for (FilterProfile p : service.getProfiles()) {
            if (p != service.getDefaultProfile()) {
                nonDefault = p;
                break;
            }
        }

        assertNotNull("Non-default profile must exist", nonDefault);

        PCL pcl = new PCL();
        service.addPropertyChangeListener(pcl);
        service.setSelectedProfile(nonDefault);

        assertTrue(pcl.props.contains(ProfileService.PROP_SELECTED_PROFILE));
    }

    public void testGetProfileStorage() {
        assertSame(service, service.getLookup().lookup(ProfileStorage.class));
    }

    public void testGetProfileFolder() {
        FileObject folder = service.getProfileFolder(service.getDefaultProfile());
        assertSame(service.getDefaultProfileFolder(), folder);

        for (FilterProfile p : service.getProfiles()) {
            if (p == service.getDefaultProfile()) {
                continue;
            }
            folder = service.getProfileFolder(p);
            assertNotNull(folder);
            assertEquals(p.getName(), folder.getName());
        }
    }


    public void testListProfiles() {
        List<FilterProfile> profiles = service.getProfiles();
        assertNotNull(profiles);
        assertFalse(profiles.isEmpty());
    }

    public void testNewProfileAppears() throws IOException {
        List<FilterProfile> profiles = service.getProfiles();

        profileStorage.createFolder("New Profile");
        RequestProcessor.Task t = service.delayedRefresh;
        if (t != null) {
            t.waitFinished();
        }
        List<FilterProfile> newProfiles = service.getProfiles();

        Assert.assertNotEquals(profiles, newProfiles);
        assertTrue(newProfiles.containsAll(profiles));

    }

    public void testCreateProfileFailExisting() throws Exception {
        String existingName = profileStorage.getChildren()[0].getName();
        try {
            service.createProfile(existingName, service.getDefaultProfile());
            fail("IOException expected");
        } catch (IOException ex) {
            // expected
        }
    }

    public void testCreateProfile() throws Exception {
        List<FilterProfile> profiles = service.getProfiles();
        FilterProfile def = service.getDefaultProfile();
        FilterProfile newProfile = service.createProfile("New Profile", def);

        assertNotNull(newProfile);
        assertNotSame(newProfile, def);

        List<FilterProfile> newProfiles = service.getProfiles();
        assertTrue(newProfiles.containsAll(profiles));
        assertTrue(newProfiles.contains(newProfile));
        assertTrue("New Profile".equals(newProfile.getName()));

        FileObject profStorage = profileStorage.getFileObject(newProfile.getName());
        assertSame(profStorage, service.getProfileFolder(newProfile));
    }

    public void testFindDefaultFilter() throws Exception {
        FilterProfile nonDefault = service.getProfiles().get(1);
        FilterProfile allFilters = service.getDefaultProfile();

        for (Filter f : nonDefault.getProfileFilters()) {
            Filter df = service.findDefaultFilter(f);
            assertNotNull(df);
            assertTrue(allFilters.getProfileFilters().contains(df));
        }

        for (Filter f : allFilters.getProfileFilters()) {
            Filter df = service.findDefaultFilter(f);
            assertSame(f, df);
        }
    }

    private Filter findFilter(List<Filter> filters, String name) {
        return filters.stream().filter((f) -> f.getName().equals(name)).findFirst().orElse(null);
    }

    private FilterProfile findProfile(List<FilterProfile> profiles, String name) {
        return profiles.stream().filter((f) -> f.getName().equals(name)).findFirst().orElse(null);
    }

    @Ignore
    public void testFindLocations() throws IOException {
        FilterProfile allFilters = service2.getDefaultProfile();
        Filter f = findFilter(allFilters.getProfileFilters(), "Coloring");
        Set<FilterProfile> profs = service2.findLocations(f);
        assertFalse(profs.contains(service2.getDefaultProfile()));
        assertEquals(1, profs.size());

        FilterProfile p = profs.iterator().next();
        assertNotNull(p);
        assertEquals("Graal Graph", p.getName());

        FilterProfile p2 = findProfile(service2.getProfiles(), "Call Graph");
        assertNotNull(p2);

        p2.addSharedFilter(f);

        Set<FilterProfile> profs2 = service2.findLocations(f);
        assertTrue(profs2.contains(p2));
    }

    @Ignore
    public void testDeleteFromAllProfiles() throws Exception {
        FilterProfile allFilters = service2.getDefaultProfile();
        Filter f = findFilter(allFilters.getProfileFilters(), "Coloring");
        FilterProfile p = findProfile(service2.getProfiles(), "Graal Graph");
        FilterProfile p2 = findProfile(service2.getProfiles(), "Call Graph");
        p2.addSharedFilter(f);

        assertNotNull(findFilter(p.getProfileFilters(), "Coloring"));
        assertNotNull(findFilter(p2.getProfileFilters(), "Coloring"));

        service2.deleteFromAllProfiles(f);

        // wait for refreshes to finish.
        Filter fp = null;
        Filter fp2 = null;

        for (int tries = 0; tries < 5; tries++) {
            FilterProfileAdapter.REFRESH_RP.post(() -> {
            }, 200).waitFinished();

            fp = findFilter(p.getProfileFilters(), "Coloring");
            fp2 = findFilter(p2.getProfileFilters(), "Coloring");
            if ((fp == null) && (fp2 == null)) {
                break;
            }
        }
        assertNull(fp);
        assertNull(fp2);
    }

    public void testDeleteProfile() throws Exception {
        List<FilterProfile> profiles = service2.getProfiles();
        FilterProfile p = profiles.get(1);
        FileObject storage = service2.getProfileFolder(p);
        service2.deleteProfile(p);
        assertFalse(storage.isValid());
    }

    public void testRenameProfile() throws Exception {
        List<FilterProfile> profiles = service2.getProfiles();
        FilterProfile p = profiles.get(1);
        FileObject storage = service2.getProfileFolder(p);

        service2.renameProfile(p, "Hastrman");
        assertEquals("Hastrman", p.getName());
        assertEquals(p.getName(), storage.getName());
    }

    @Ignore
    public void testDeleteUsedFilterFails() throws Exception {
        Filter f = findFilter(service2.getDefaultProfile().getAllFilters().getFilters(), "Coloring");
        try {
            service2.deleteFilter(f);
            fail("Should fail as the filter is shared");
        } catch (IOException ex) {
            // OK
        }
    }

    public void disabletestDeleteFilterLink() throws Exception {
        FilterProfile p = findProfile(service2.getProfiles(), "Graal Graph");
        Filter f = findFilter(p.getAllFilters().getFilters(), "Coloring");

        service2.deleteFilter(f);
        waitRefresh();
        assertFalse(p.getAllFilters().getFilters().contains(f));
    }

}
