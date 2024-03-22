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

import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.RequestProcessor;
import org.openide.util.WeakListeners;

/**
 * Delays execution until after 500ms after the last file change event
 * from the watched folder.
 * The delay is because some FileSystem event dispatches may be delayed
 * up to 300ms by the FS library.
 *
 * @author sdedic
 */
public class FilterProfileTestBase extends NbTestCase {
    private final Runnable r = this::doTask;
    protected RequestProcessor.Task waitTask;

    private final FileChangeListener fl = new FileChangeAdapter() {
        @Override
        public void fileAttributeChanged(FileAttributeEvent fe) {
            change();
        }

        @Override
        public void fileRenamed(FileRenameEvent fe) {
            change();
        }

        @Override
        public void fileDeleted(FileEvent fe) {
            change();
        }

        @Override
        public void fileChanged(FileEvent fe) {
            change();
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            change();
        }

        @Override
        public void fileFolderCreated(FileEvent fe) {
            change();
        }
    };

    public FilterProfileTestBase(String name) {
        super(name);

        FileObject fo = FileUtil.getConfigFile(ProfileStorage.PROFILES_FOLDER);
        fo.addRecursiveListener(WeakListeners.create(FileChangeListener.class, fl, fo));
    }

    protected void monitorFolder(FileObject fo) {
        fo.addRecursiveListener(WeakListeners.create(FileChangeListener.class, fl, fo));
    }

    protected void waitRefresh() {
        RequestProcessor.Task t;
        synchronized (this) {
            if (waitTask == null) {
                t = FilterProfileAdapter.REFRESH_RP.create(r);
                waitTask = t;
                t.schedule(2000);
            } else {
                t = waitTask;
            }
        }
        t.waitFinished();
        waitTask = null;
    }

    private void doTask() {
    }

    private void change() {
        RequestProcessor.Task t;
        synchronized (this) {
            if (waitTask == null) {
                t = FilterProfileAdapter.REFRESH_RP.create(r);
                waitTask = t;
            } else {
                t = waitTask;
            }
            t.schedule(500);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        waitTask = null;
        super.tearDown();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

}
