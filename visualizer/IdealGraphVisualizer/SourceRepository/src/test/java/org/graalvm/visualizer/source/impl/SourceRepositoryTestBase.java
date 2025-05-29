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

import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author sdedic
 */
public class SourceRepositoryTestBase extends NbTestCase {
    SourceRepositoryImpl repo;
    FileObject fsrc1;
    FileObject fsrc2;

    PropertyChangeEvent propEvent;
    ChangeEvent lastEvent;
    List<ChangeEvent> events = new ArrayList<>();
    ChangeEvent groupChange;
    Preferences prefs;

    public SourceRepositoryTestBase(String name) {
        super(name);
    }

    @Override
    protected void tearDown() throws Exception {
        SourceRepositoryImpl._testReset();
        super.tearDown();
    }

    protected void recycleRepository() {
        SourceRepositoryImpl._testReset();
        repo = new SourceRepositoryImpl(prefs);
    }

    protected void setUp() throws Exception {
        super.setUp();
        clearWorkDir();
        FileUtil.getConfigRoot().refresh(true);
        this.prefs = new TestMemPrefs(null, "");
        Thread.sleep(100);
        SourceRepositoryImpl._testReset();
        repo = new SourceRepositoryImpl(this.prefs);

        File dir = getWorkDir();

        File src1 = new File(dir, "src1");
        assertTrue(src1.mkdirs());
        fsrc1 = FileUtil.toFileObject(src1);

        File src2 = new File(dir, "src2");
        assertTrue(src2.mkdirs());
        fsrc2 = FileUtil.toFileObject(src2);

        lastEvent = null;
        events.clear();
    }

    class ChangeL implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            lastEvent = e;
            events.add(e);
        }

    }

    class GChangeL implements ChangeListener {
        @Override
        public void stateChanged(ChangeEvent e) {
            groupChange = e;
            events.add(e);
        }

    }

    class PropL implements PropertyChangeListener {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            propEvent = evt;
        }

    }

    private static class TestMemPrefs extends AbstractPreferences {
        private Map<String, String> values = new HashMap<>();
        private Map<String, AbstractPreferences> nodes = new HashMap<>();
        private final TestMemPrefs parent;

        public TestMemPrefs(TestMemPrefs parent, String name) {
            super(parent, name);
            this.parent = parent;
        }


        @Override
        protected void putSpi(String key, String value) {
            values.put(key, value);
        }

        @Override
        protected String getSpi(String key) {
            return values.get(key);
        }

        @Override
        protected void removeSpi(String key) {
            values.remove(key);
        }

        @Override
        protected void removeNodeSpi() throws BackingStoreException {
            parent.nodes.remove(name());
        }

        @Override
        protected String[] keysSpi() throws BackingStoreException {
            return values.keySet().toArray(new String[values.keySet().size()]);
        }

        @Override
        protected String[] childrenNamesSpi() throws BackingStoreException {
            return nodes.keySet().toArray(new String[nodes.keySet().size()]);
        }

        @Override
        protected AbstractPreferences childSpi(String name) {
            return nodes.computeIfAbsent(name, (n) -> new TestMemPrefs(this, n));
        }

        @Override
        protected void syncSpi() throws BackingStoreException {
        }

        @Override
        protected void flushSpi() throws BackingStoreException {
        }

    }

}
