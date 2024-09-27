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

import org.graalvm.visualizer.filter.CustomFilter;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.filter.spi.GraphFilterLocator;
import org.netbeans.junit.RandomlyFails;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataFolder;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataShadow;
import org.openide.util.Lookup;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Marking as RandomlyFails: contains waits because of background filesystem refreshes.
 *
 * @author sdedic
 */
@RandomlyFails
public class FilterProfileAdapterTest extends FilterProfileTestBase {
    private FileObject defaultStorage;
    private FileObject profileStorage;

    FilterProfileServiceImpl service2;

    public FilterProfileAdapterTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
        String tmp = "_temp_storage" + getName();
        FileObject fo = FileUtil.getConfigFile(tmp);
        if (fo != null) {
            fo.delete();
        }
        fo = FileUtil.getConfigRoot().createFolder(tmp);

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
        service2 = new FilterProfileServiceImpl(profileStorage, defaultStorage, null);

        monitorFolder(profileStorage);
        monitorFolder(defaultStorage);
    }

    /**
     * Checks that profile will report localized name.
     *
     * @throws Exception
     */
    public void testDisplayName() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        folder.setAttribute("displayName", "Most important stuff");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        assertEquals("Most important stuff", ad.getName());
    }

    /**
     * Checks that profile will report localized name.
     *
     * @throws Exception
     */
    public void testDisplayNameAttributeChange() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        assertEquals("Graal Graph", ad.getName());
        folder.setAttribute("displayName", "Most important stuff");
        assertEquals("Most important stuff", ad.getName());
    }

    public void testSetDisplayName() {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        ad.setDisplayName("YYY");
        assertEquals("YYY", ad.getName());
    }

    /**
     * Checks that rename profile will rename it on disk
     *
     * @throws Exception
     */
    public void testRenameProfile() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        assertEquals(folder.getName(), ad.getName());
        ad.setName("Test profile");
        assertEquals("Test profile", folder.getName());
    }

    /**
     * Checks that if a filter is renamed, its position within profile
     * does not change, despite some explicit ordering recorded in
     * file attributes.
     *
     * @throws Exception
     */
    public void testRenameFilterNoChangePosition() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FileObject[] defaultOrder = folder.getChildren();
        FileObject a = defaultOrder[3];
        defaultOrder[3] = defaultOrder[1];
        defaultOrder[1] = a;

        // set a specific order by attributes.
        FileUtil.setOrder(Arrays.asList(defaultOrder));

        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);

        List<Filter> allFilters = ad.getAllFilters().getFilters();
        FileObject filterFile = allFilters.get(2).getLookup().lookup(FileObject.class);
        try (FileLock fl = filterFile.lock()) {
            filterFile.rename(fl, "UnknownFilter", filterFile.getExt());
        }
        waitRefresh();

        List<Filter> nFilters = ad.getAllFilters().getFilters();
        assertEquals(allFilters, nFilters);
        assertEquals("UnknownFilter", nFilters.get(2).getName());
    }

    public void testNoticeAddedFile() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> fl = ad.getAllFilters().getFilters();
        Optional<Filter> any = fl.stream().
                filter((Filter f) -> f.getName().equals("NewFilter")).
                findAny();
        assertFalse(any.isPresent());

        try (OutputStream os = folder.createAndOpen("NewFilter.js")) {
            // write nothing, just empty file.
        }
        waitRefresh();

        fl = ad.getAllFilters().getFilters();
        any = fl.stream().
                filter((Filter f) -> f.getName().equals("NewFilter")).
                findAny();
        assertTrue(any.isPresent());
    }

    public void testNoticeRemovedFile() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> fl = ad.getAllFilters().getFilters();

        FileObject toRemove = folder.getFileObject("Coloring.shadow");
        toRemove.delete();

        waitRefresh();
        List<Filter> fl2 = ad.getAllFilters().getFilters();

        assertTrue(fl.containsAll(fl2));
        Optional<Filter> opt = fl2.stream().filter(f -> f.getName().equals("Coloring")).findAny();
        assertFalse(opt.isPresent());
    }

    public void testSetFilterEnabled() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> flts = ad.getAllFilters().getFilters();

        Filter color = flts.stream().filter(f -> f.getName().equals("Coloring")).findAny().get();
        Filter prob = flts.stream().filter(f -> f.getName().equals("Probability Coloring")).findAny().get();

        ad.setEnabled(color, false);
        ad.setEnabled(prob, true);
        waitRefresh();

        List<Filter> sel = ad.getSelectedFilters().getFilters();

        assertFalse(sel.stream().filter(f -> f.getName().equals("Coloring")).findAny().isPresent());
        assertTrue(sel.stream().filter(f -> f.getName().equals("Probability Coloring")).findAny().isPresent());

        FilterProfileAdapter ad2 = new FilterProfileAdapter(
                folder,
                service2, service2);

        List<Filter> sel2 = ad2.getSelectedFilters().getFilters();

        assertFalse(sel.stream().filter(f -> f.getName().equals("Coloring")).findAny().isPresent());
        assertTrue(sel.stream().filter(f -> f.getName().equals("Probability Coloring")).findAny().isPresent());
    }

    public void testMoveFilterDown() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> flts = new ArrayList<>(ad.getAllFilters().getFilters());

        Filter color = flts.stream().filter(f -> f.getName().equals("Coloring")).findAny().get();

        ad.moveDown(color);
        waitRefresh();
        int index = flts.indexOf(color);
        flts.remove(index);
        flts.add(index + 1, color);
        List<Filter> selected = new ArrayList<>(flts);
        selected.retainAll(ad.getSelectedFilters().getFilters());

        List<Filter> check = ad.getAllFilters().getFilters();
        assertEquals(flts, check);


        check = ad.getSelectedFilters().getFilters();
        assertEquals(selected, check);

        FilterProfileAdapter ad2 = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> check2 = new ArrayList<>(ad2.getAllFilters().getFilters());
        assertEquals(flts, check2);
        check = ad2.getSelectedFilters().getFilters();
        assertEquals(selected, check);
    }

    public void testMoveLastFilterDown() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> flts = new ArrayList<>(ad.getAllFilters().getFilters());
        List<Filter> sel = new ArrayList<>(ad.getSelectedFilters().getFilters());
        Filter f = flts.get(flts.size() - 1);

        ad.moveDown(f);
        waitRefresh();

        assertEquals(flts, ad.getAllFilters().getFilters());
        assertEquals(sel, ad.getSelectedFilters().getFilters());
    }

    public void testMoveFilterUp() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> flts = new ArrayList<>(ad.getAllFilters().getFilters());

        Filter color = flts.stream().filter(f -> f.getName().equals("Reduce Edges")).findAny().get();

        ad.moveUp(color);
        waitRefresh();
        int index = flts.indexOf(color);
        flts.remove(index);
        flts.add(index - 1, color);
        List<Filter> selected = new ArrayList<>(flts);
        selected.retainAll(ad.getSelectedFilters().getFilters());

        List<Filter> check = ad.getAllFilters().getFilters();
        assertEquals(flts, check);


        check = ad.getSelectedFilters().getFilters();
        assertEquals(selected, check);

        FilterProfileAdapter ad2 = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> check2 = new ArrayList<>(ad2.getAllFilters().getFilters());
        assertEquals(flts, check2);
        check = ad2.getSelectedFilters().getFilters();
        assertEquals(selected, check);
    }

    public void testMoveFirstFilterUp() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        List<Filter> flts = new ArrayList<>(ad.getAllFilters().getFilters());
        List<Filter> sel = new ArrayList<>(ad.getSelectedFilters().getFilters());
        Filter f = flts.get(0);

        ad.moveUp(f);
        waitRefresh();

        assertEquals(flts, ad.getAllFilters().getFilters());
        assertEquals(sel, ad.getSelectedFilters().getFilters());
    }

    public void testCreateRootFilterFromFile() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        FileObject orig = FileUtil.getConfigFile("Filters/Coloring.js");
        FilterProvider p = Lookup.getDefault().lookup(GraphFilterLocator.class).findChain(orig.getLookup());
        Filter f = p.getFilter();
        ((CustomFilter) f).setName("Coloring2");
        ad.createRootFilter(f);

        waitRefresh();

        List<Filter> flts = service2.getDefaultProfile().getAllFilters().getFilters();
        assertTrue(flts.stream().filter(t -> t.getName().equals("Coloring2")).findAny().isPresent());
    }

    public void testCreateRootFilterFromMemory() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);
        InstanceContent ic = new InstanceContent();
        CustomFilter cf = new CustomFilter("Custom", "# Comment", "text/javascript", new AbstractLookup(ic));
        ic.add(cf);

        ad.createRootFilter(cf);

        waitRefresh();

        List<Filter> flts = service2.getDefaultProfile().getAllFilters().getFilters();
        assertTrue(flts.stream().filter(t -> t.getName().equals("Custom")).findAny().isPresent());
    }

    public void testAddProfileFilter() throws Exception {
        FileObject folder = profileStorage.getFileObject("Graal Graph");
        FilterProfileAdapter ad = new FilterProfileAdapter(
                folder,
                service2, service2);

        List<Filter> flts = service2.getDefaultProfile().getAllFilters().getFilters();
        Filter base = flts.stream().filter(
                f -> "Call Graph Coloring".equals(f.getName())
        ).findFirst().get();

        ad.addProfileFilter(base);
        waitRefresh();

        List<Filter> profFilters = ad.getAllFilters().getFilters();

        Optional<Filter> opt = profFilters.stream().filter(
                f -> "Call Graph Coloring".equals(f.getName())
        ).findFirst();
        assertTrue(opt.isPresent());

        Filter f = opt.get();
        assertNotSame(base, f);

    }
}
