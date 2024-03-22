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

package org.graalvm.visualizer.source.impl;

import org.junit.Ignore;
import org.netbeans.api.java.classpath.GlobalPathRegistry;
import org.openide.filesystems.FileObject;

import java.net.URI;
import java.net.URL;
import java.util.Arrays;

/**
 * @author sdedic
 */
public class SourceRepositoryImplTest extends SourceRepositoryTestBase {
    public SourceRepositoryImplTest(String name) {
        super(name);
    }

    void reset() {
        recycleRepository();
    }

    public void testDefaultGroup() throws Exception {
        assertNotNull(repo.getDefaultGroup());
        assertFalse(repo.getGroups().contains(repo.getDefaultGroup()));
    }

    public void testAddRootDefault() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName(), null);

        // check that the root was added:
        FileGroup g = repo.getDefaultGroup();
        assertNotNull(g);
        assertTrue(g.contains(r.getLocation()));

        FileObject[] roots = g.getSourcePath().getRoots();
        assertTrue(Arrays.asList(roots).contains(fsrc1));

        // check that the global path lists the root:
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));

        // check persistence of the setting
        reset();

        FileGroup g2 = repo.getDefaultGroup();
        assertNotSame(g, g2);
        roots = g2.getSourcePath().getRoots();
        assertTrue(Arrays.asList(roots).contains(fsrc1));
        FileRoot nr = findRoot(g2, r.getLocation());
        assertNotNull(nr);
        assertEquals(r.getDisplayName(), nr.getDisplayName());
    }

    @Ignore("test has been broken for a long time")
    public void testAddRootTwice() throws Exception {
        FileRoot r = repo.addLocation(fsrc1, getName(), null);

        // check that the root was added:
        FileGroup g = repo.getDefaultGroup();
        assertNotNull(g);
        assertTrue(g.contains(r.getLocation()));
        FileObject[] roots = g.getSourcePath().getRoots();
        assertTrue(Arrays.asList(roots).contains(fsrc1));

        // attempt to add 2nd time:
        FileRoot r2 = repo.addLocation(fsrc1, getName() + "-2", null);

        assertSame(r, r2);
        assertTrue(g.contains(r.getLocation()));
        FileObject[] roots2 = g.getSourcePath().getRoots();
        assertEquals(roots, roots2);
    }

    FileGroup findGroup(URI uri) {
        for (FileGroup g : repo.getGroups()) {
            if (g.getURI().equals(uri)) {
                return g;
            }
        }
        return null;
    }

    FileRoot findRoot(FileGroup g, URL l) {
        for (FileRoot fr : g.getFileRoots()) {
            if (fr.getLocation().equals(l)) {
                return fr;
            }
        }
        return null;
    }

    public void testAddFileGroup() throws Exception {
        FileGroup g = repo.createGroup(getName());
        assertEquals(getName(), g.getDisplayName());
        assertTrue(repo.getGroups().contains(g));

        reset();

        FileGroup ng = findGroup(g.getURI());
        assertNotNull(ng);
        assertEquals(g.getDisplayName(), ng.getDisplayName());
    }

    public void testAddRootIntoGroup() throws Exception {
        FileGroup g = repo.createGroup(getName());
        FileRoot root1 = repo.addLocation(fsrc1, getName(), g);

        assertSame(g, root1.getParent());

        assertFalse(repo.getDefaultGroup().getFileRoots().contains(root1));
        assertFalse(repo.getDefaultGroup().contains(root1.getLocation()));

        assertTrue(g.getFileRoots().contains(root1));
        assertTrue(g.contains(root1.getLocation()));
        assertTrue(g.getSourcePath().contains(fsrc1));

        reset();

        FileGroup ng = findGroup(g.getURI());

        assertFalse(ng.getFileRoots().contains(root1));
        assertTrue(ng.contains(root1.getLocation()));
        assertTrue(ng.getSourcePath().contains(fsrc1));

        FileRoot nr = findRoot(ng, root1.getLocation());
        assertNotNull(nr);
        assertSame(ng, nr.getParent());
        assertNotSame(nr, root1);
        assertEquals(root1.getDisplayName(), nr.getDisplayName());
    }

    public void testRemoveFileGroup() throws Exception {
        FileGroup g = repo.createGroup(getName());
        assertEquals(getName(), g.getDisplayName());
        assertTrue(repo.getGroups().contains(g));

        repo.deleteGroup(g);
        assertFalse(repo.getGroups().contains(g));

        reset();
        assertNull(findGroup(g.getURI()));
    }

    public void testRemoveRootDefault() throws Exception {
        FileRoot fr = repo.addLocation(fsrc1, getName() + "1", null);
        FileRoot fr2 = repo.addLocation(fsrc2, getName() + "2", null);
        // precondition
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));

        fr.discard();

        FileObject[] roots = repo.getDefaultGroup().getSourcePath().getRoots();
        assertFalse(Arrays.asList(roots).contains(fsrc1));
        assertFalse(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));

        // check that the other root still exists
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc2));
        assertTrue(Arrays.asList(roots).contains(fsrc2));

        reset();
        roots = repo.getDefaultGroup().getSourcePath().getRoots();
        assertFalse(Arrays.asList(roots).contains(fsrc1));
        assertFalse(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc1));

        // check that the other root still exists
        assertTrue(GlobalPathRegistry.getDefault().getSourceRoots().contains(fsrc2));
        assertTrue(Arrays.asList(roots).contains(fsrc2));
    }

    public void testAddRemoveGroupChangeEvents() throws Exception {
        repo.addChangeListener(new ChangeL());
        FileGroup g = repo.createGroup(getName());
        assertNotNull(lastEvent);

        lastEvent = null;
        repo.deleteGroup(g);
        assertNotNull(lastEvent);
    }

    public void testAddRemoveRootChangeEvents() throws Exception {
        FileGroup g = repo.createGroup(getName());
        repo.addChangeListener(new ChangeL());

        g.addChangeListener(new GChangeL());
        g.addPropertyChangeListener(new PropL());
        FileRoot fr = repo.addLocation(fsrc1, getName(), g);
        assertNotNull(groupChange);
        assertNotNull(lastEvent);
        assertNotNull(propEvent);
        assertEquals(FileGroup.PROP_ROOTS, propEvent.getPropertyName());

        lastEvent = null;
        propEvent = null;

        fr.discard();
        assertNotNull(lastEvent);
        assertNotNull(propEvent);
        assertEquals(FileGroup.PROP_ROOTS, propEvent.getPropertyName());
    }

    public void testGroupNameChange() throws Exception {
        FileGroup g = repo.createGroup(getName());
        g.addChangeListener(new GChangeL());
        g.addPropertyChangeListener(new PropL());

        g.setDisplayName(getName() + "_disp");
        assertNotNull(propEvent);
        assertEquals(FileGroup.PROP_DISPLAY_NAME, propEvent.getPropertyName());
    }

    public void testFileRootNameChange() throws Exception {
        FileRoot root1 = repo.addLocation(fsrc1, getName(), null);
        root1.addPropertyChangeListener(new PropL());
        root1.setDisplayName(getName() + "_sss");
        assertNotNull(propEvent);
        reset();
        FileRoot nr = findRoot(repo.getDefaultGroup(), root1.getLocation());
        assertNotNull(nr);
        assertEquals(root1.getDisplayName(), nr.getDisplayName());
    }

    public void testMoveFileRoot() throws Exception {
        FileGroup g = repo.createGroup(getName() + "1");
        FileGroup g2 = repo.createGroup(getName() + "2");

        FileRoot r = repo.addLocation(fsrc1, getName() + "S1", g);
        FileRoot r2 = repo.addLocation(fsrc2, getName() + "S2", g2);

        r.moveTo(g2);
        r2.moveTo(g);

        assertTrue(g.getSourcePath().contains(fsrc2));
        assertTrue(g2.getSourcePath().contains(fsrc1));

        reset();

        FileGroup ng = findGroup(g.getURI());
        FileGroup ng2 = findGroup(g2.getURI());
        assertTrue(ng.getSourcePath().contains(fsrc2));
        assertTrue(ng2.getSourcePath().contains(fsrc1));
    }

}
