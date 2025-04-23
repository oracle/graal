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

package org.graalvm.visualizer.selectioncoordinator;

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import jdk.graal.compiler.graphio.parsing.model.Group;
import org.netbeans.junit.NbTestCase;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author odouda
 */
public class SelectionCoordinatorTest extends NbTestCase {
    protected final Group g1;
    protected final Group g2;

    public SelectionCoordinatorTest(String name) {
        super(name);
        g1 = new Group(null, null);
        g2 = new Group(null, null);
    }

    public void testIdentity() {
        SelectionCoordinator sc1 = SelectionCoordinator.getInstanceForContainer(g1);
        SelectionCoordinator sc2 = SelectionCoordinator.getInstanceForContainer(g1);

        assertSame(sc1, sc2);

        SelectionCoordinator sc3 = SelectionCoordinator.getInstanceForContainer(g2);

        assertNotSame(sc1, sc3);
    }

    int selectedEvents = 0;
    ChangedListener<SelectionCoordinator> selectionListener = (SelectionCoordinator s) -> selectedEvents++;

    int highlightedEvents = 0;
    ChangedListener<SelectionCoordinator> highlightedListener = (SelectionCoordinator s) -> highlightedEvents++;

    int misfireEvents = 0;
    ChangedListener<SelectionCoordinator> misfireListener = (SelectionCoordinator s) -> misfireEvents++;

    public void testEventsAndManipulation() {
        Set<Integer> objs0 = new HashSet<>();
        objs0.add(0);
        Set<Integer> objs1 = new HashSet<>();
        objs1.add(1);
        assertEquals(0, selectedEvents);
        assertEquals(0, highlightedEvents);
        SelectionCoordinator sc1 = SelectionCoordinator.getInstanceForContainer(g1);
        sc1.getHighlightedChangedEvent().addListener(highlightedListener);
        sc1.getSelectedChangedEvent().addListener(selectionListener);
        SelectionCoordinator sc2 = SelectionCoordinator.getInstanceForContainer(g2);
        sc2.getSelectedChangedEvent().addListener(misfireListener);
        assertEquals(0, selectedEvents);
        assertEquals(0, highlightedEvents);

        sc1.addSelected(0);
        sc1.addHighlighted(0);
        assertEquals(1, selectedEvents);
        assertEquals(1, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.addSelected(0);
        sc1.addHighlighted(0);
        assertEquals(1, selectedEvents);
        assertEquals(1, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.removeAllSelected(objs0);
        sc1.removeAllHighlighted(objs0);
        assertEquals(2, selectedEvents);
        assertEquals(2, selectedEvents);
        assertEquals(Collections.EMPTY_SET, sc1.getSelectedObjects());
        assertEquals(Collections.EMPTY_SET, sc1.getHighlightedObjects());

        sc1.addAllSelected(objs0);
        sc1.addAllHighlighted(objs0);
        assertEquals(3, selectedEvents);
        assertEquals(3, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.removeAllSelected(objs1);
        sc1.removeAllHighlighted(objs1);
        assertEquals(3, selectedEvents);
        assertEquals(3, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.removeSelected(1);
        sc1.removeHighlighted(1);
        assertEquals(3, selectedEvents);
        assertEquals(3, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.addAllSelected(objs0);
        sc1.addAllHighlighted(objs0);
        assertEquals(3, selectedEvents);
        assertEquals(3, selectedEvents);
        assertEquals(objs0, sc1.getSelectedObjects());
        assertEquals(objs0, sc1.getHighlightedObjects());

        sc1.setSelectedObjects(objs1);
        sc1.setHighlightedObjects(objs1);
        assertEquals(4, selectedEvents);
        assertEquals(4, selectedEvents);
        assertEquals(objs1, sc1.getSelectedObjects());
        assertEquals(objs1, sc1.getHighlightedObjects());

        sc1.removeSelected(1);
        sc1.removeHighlighted(1);
        assertEquals(5, selectedEvents);
        assertEquals(5, selectedEvents);
        assertEquals(Collections.EMPTY_SET, sc1.getSelectedObjects());
        assertEquals(Collections.EMPTY_SET, sc1.getHighlightedObjects());

        assertEquals(0, misfireEvents);
    }
}
