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

package org.graalvm.visualizer.data.serialization.lazy;

import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * @author sdedic
 */
public class LazyGroupTest extends BinaryDataTestBase {
    private Group parent;
    private LazyGroup lazyParent;
    private final long freezeAllChannels = 399038L;

    public LazyGroupTest(String name) {
        super(name);
    }

    private void prepareData() throws Exception {
        loadExecutor = PARALLEL_LOAD;
        loadData("mega2.bgv");
        reader.parse();
        this.frozen = null;
        parent = findGroup("Truffle.root_new()/org.graalvm.compiler.truffle.OptimizedCallTarget.callRoot(Object[])");
        assertNotNull(parent);
        lazyParent = (LazyGroup) parent;
    }

    private void loadGroupStart() throws Exception {
        loadExecutor = PARALLEL_LOAD;
        loadData("mega2.bgv");
        // let the scanning reader freeze in the middle of the group as if 
        // the producer did not supply enough data
        file.freezeAt = freezeAllChannels;
        new Thread(() -> {
            try {
                reader.parse();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }).start();

        // wait till the group opens
        file.frozen.acquire();

        file.channelFactory = (long[] bounds) -> {
            try {
                freezeChannel = new FreezeChannel(bounds[0], bounds[1], freezeAllChannels);
                freezeChannel.frozen = this.frozen;
                freezeChannel.freezeAt = freezeAllChannels;
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            return freezeChannel;
        };

        parent = findGroup("Truffle.root_new()/org.graalvm.compiler.truffle.OptimizedCallTarget.callRoot(Object[])");
        assertNotNull(parent);
        lazyParent = (LazyGroup) parent;

        // trigger load of the lazy parent, wait for the current part to load, use different semaphore inst
        this.frozen = new Semaphore(0);
        lazyParent.completeContents(null);
        frozen.acquire();
    }

    private Group findGroup(String path) throws Exception {
        Folder f = checkDocument;
        Folder next;
        for (String s : path.split("/")) {
            next = null;
            List<? extends FolderElement> children;
            if (f instanceof LazyGroup) {
                LazyGroup lg = (LazyGroup) f;
                Future<List<? extends FolderElement>> fut = lg.completeContents(null);
                if (frozen != null) {
                    frozen.acquire();
                    children = lg.partialData();
                } else {
                    children = fut.get();
                }
            } else {
                children = f.getElements();
            }
            for (FolderElement el : children) {
                if (s.equals(el.getName())) {
                    if (el instanceof Folder) {
                        next = (Folder) el;
                        break;
                    } else {
                        return null;
                    }
                }
            }
            if (next == null) {
                return null;
            }
            f = next;
        }
        return (Group) f;
    }

    Semaphore frozen = new Semaphore(0);

    public void testGroupAppearsEarly() throws Exception {
        loadGroupStart();
        // assert that the group appeared eearly
        assertNotNull(parent);
        assertFalse(lazyParent.isComplete());
    }

    /**
     * Checks that partial content is visible when reading is blocked
     * and is identical to getElements().
     */
    public void testPartialContent() throws Exception {
        loadGroupStart();
        assertNotNull(lazyParent);
        assertFalse(lazyParent.getElements().isEmpty());
        assertEquals(lazyParent.getElements(), lazyParent.partialData());
    }

    private volatile int changed;

    public void testPartialContentUpdates() throws Exception {
        loadGroupStart();
        assertNotNull(lazyParent);
        List<? extends FolderElement> initialEls = lazyParent.getElements();

        file.freezeAt = -1;
        freezeChannel.freezeAt = 4326265l;
        file.condition.release();
        freezeChannel.condition.release();
    }

    /**
     * Checks that the initial partial content contains graphs
     */
    public void testPartialGraphs() throws Exception {
        loadGroupStart();
        List<InputGraph> graphs = lazyParent.getGraphs();
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());

        // continue reading
        file.freezeAt = -1;
        freezeChannel.freezeAt = 4326265l;
        file.condition.release();
        freezeChannel.condition.release();
        freezeChannel.frozen.acquire();

        List<? extends FolderElement> newGraphs = lazyParent.getGraphs();

        // check that th new elements just add items at the end of the list,
        // and that all former items are present at list head
        assertTrue(newGraphs.size() > graphs.size());
        assertTrue(newGraphs.containsAll(graphs));
        int i = newGraphs.indexOf(graphs.get(graphs.size() - 1));
        assertEquals(graphs.size() - 1, i);
        assertEquals(graphs, newGraphs.subList(0, i + 1));
    }

    /**
     * Checks that graph content updates as the partial content is read further
     */
    public void testPartialGraphsUpdate() throws Exception {
        loadGroupStart();
        List<InputGraph> graphs = lazyParent.getGraphs();
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());

        // continue reading
        file.freezeAt = -1;
        freezeChannel.freezeAt = 4326265l;
        file.condition.release();
        freezeChannel.condition.release();
        freezeChannel.frozen.acquire();

        List<? extends FolderElement> newGraphs = lazyParent.getGraphs();

        // check that th new elements just add items at the end of the list,
        // and that all former items are present at list head
        assertTrue(newGraphs.size() > graphs.size());
        assertTrue(newGraphs.containsAll(graphs));
        int i = newGraphs.indexOf(graphs.get(graphs.size() - 1));
        assertEquals(graphs.size() - 1, i);
        assertEquals(graphs, newGraphs.subList(0, i + 1));

        assertSame(newGraphs, lazyParent.getGraphs());
    }

    /**
     * Checks that properties that have been modified are preserved. The properties
     * are loaded but the content is skipped initially. during initial load,
     * the modified properties must not be overwritten.
     *
     * @throws Exception
     */
    public void testModifiedPropertiesNotReloaded() throws Exception {
        prepareData();

        Properties.MutableOwner owner = (Properties.MutableOwner) lazyParent;
        Properties wrProps = owner.writableProperties();
        wrProps.setProperty("name", "1");
        owner.updateProperties(wrProps);


        List<InputGraph> graphs = lazyParent.getGraphs();
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());

        assertEquals("1", lazyParent.getProperties().getString("name", null));
    }

    /**
     * Checks that properties that have been modified are preserved.
     * Group is reloaded as a whole when its children are GCed and are asked
     * for again
     *
     * @throws Exception
     */
    public void testModifiedPropertiesNotReloaded2() throws Exception {
        prepareData();

        List<InputGraph> graphs = lazyParent.getGraphs();
        Reference weakData = new WeakReference<>(graphs.get(0));

        Properties.MutableOwner owner = (Properties.MutableOwner) lazyParent;
        Properties wrProps = owner.writableProperties();
        wrProps.setProperty("name", "1");
        owner.updateProperties(wrProps);

        graphs = null;
        assertGC("must be freed", weakData);

        assertEquals("1", lazyParent.getProperties().getString("name", null));

        graphs = lazyParent.getGraphs();
        assertFalse(graphs.isEmpty());
        assertEquals("1", lazyParent.getProperties().getString("name", null));
    }
}

