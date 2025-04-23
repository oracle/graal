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

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.visualizer.data.Pair;
import org.netbeans.junit.RandomlyFails;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor.Task;

import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * @author sdedic
 */
@RandomlyFails
public class DelayedLoadTest extends BinaryDataTestBase {
    public DelayedLoadTest(String name) {
        super(name);
    }

    /**
     * All references must be GCed, if noone holds them
     *
     * @throws Exception
     */
    public void testLazyContentsReleased() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        reader.parse();
        GraphDocument doc = mb.rootDocument();
        List<Reference> refs = new ArrayList<>();

        for (FolderElement tl : doc.getElements()) {
            if (tl instanceof LazyGroup) {
                Group g = (Group) tl;
                List els = g.getElements();
                for (Object o : els) {
                    refs.add(new WeakReference(o));
                }
            } else if (tl instanceof LazyGraph) {
                InputGraph gr = (InputGraph) tl;
                InputNode node = gr.getNodes().iterator().next();
                refs.add(new WeakReference(node));
            }
        }
        for (Reference r : refs) {
            assertGC("Must GC", r);
        }
    }

    /**
     * If an item is kept from a group, then whole contents (all items) must be
     * kept in memory; the group itself MUST be kept as well.
     */
    public void testItemKeepsGroup() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        reader.parse();
        GraphDocument doc = mb.rootDocument();

        class K {
            Object keepRef;
            final List<Reference> refSiblings = new ArrayList<>();
            Reference container;
        }
        Reference bait = null;

        List<K> keepData = new ArrayList<>();

        for (FolderElement tl : doc.getElements()) {
            if (tl instanceof LazyGroup) {
                Group g = (Group) tl;
                List els = g.getElements();
                if (els.isEmpty()) {
                    continue;
                }
                if (bait == null) {
                    bait = new WeakReference<>(els.get(0));
                    continue;
                }
                K keep = new K();
                keep.container = new WeakReference<>(g);
                keep.keepRef = els.get(0);

                for (Object o : els) {
                    keep.refSiblings.add(new WeakReference(o));
                }
                keepData.add(keep);
            } else if (tl instanceof LazyGraph) {
            }
        }
        // wait till at least something GCes
        assertGC("", bait);
        for (K k : keepData) {
            LazyGroup lg = (LazyGroup) k.container.get();
            assertNotNull("Container must be alive", lg);
            assertTrue("group must be still complete", lg.isComplete());
            List<? extends FolderElement> els = lg.getElements();
            List<FolderElement> cached = new ArrayList<>();
            for (Reference r : k.refSiblings) {
                cached.add((FolderElement) r.get());
            }
            assertEquals("Contents must not change", els, cached);
        }
    }

    public void testFirstGetElementsBlocks() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        reader.parse();
        GraphDocument doc = mb.rootDocument();
        file.playSemaphores = true;
        class W implements Runnable {
            final LazyGroup lg;
            Future<List<? extends FolderElement>> contentFuture;
            List<? extends FolderElement> items;
            boolean futureComplete;
            final Semaphore inc;
            final Semaphore running = new Semaphore(0);

            public W(LazyGroup lg) {
                this.lg = lg;
                this.inc = null;
            }

            public W(LazyGroup lg, Semaphore inc) {
                this.lg = lg;
                this.inc = inc;
            }

            public void run() {
                running.release();
                List<? extends FolderElement> x = lg.getElements();
                synchronized (this) {
                    items = x;
                    if (contentFuture != null) {
                        boolean d = contentFuture.isDone();
                        futureComplete = d;
                    }
                    if (inc != null) {
                        inc.release();
                    }
                }
            }
        }

        CompletionService<W> cs = new ExecutorCompletionService(PARALLEL_LOAD);
        int count = 0;
        Semaphore immediate = new Semaphore(0);
        for (FolderElement tl : doc.getElements()) {
            if (tl instanceof LazyGroup) {
                LazyGroup lg = (LazyGroup) tl;
                count++;

                W work = new W(lg);
                // no permit to open the subchannel, the work will block
                cs.submit(work, work);
                work.running.acquire();
                Thread.sleep(100);
                synchronized (work) {
                    work.contentFuture = lg.completeContents(null);
                }
                assertFalse(work.contentFuture.isDone());

                // wake up the thread
                file.permitSubchannelAndWait();

                immediate.drainPermits();
                // attempt to call getElements() for the second time, should succeeed "immediately"
                W work2 = new W(lg, immediate);
                Future f = PARALLEL_LOAD.submit(work2, Boolean.TRUE);
                // wait for the job to start
                work2.running.acquire();
                try {
                    f.get(50, TimeUnit.MILLISECONDS);
                } catch (TimeoutException ex) {
                    fail("Second request to getElements() must not block");
                }
            }
        }
        for (int i = 0; i < count; i++) {
            Future<W> workF = cs.take();
            W work = workF.get();
            synchronized (work) {
                assertTrue("Future was not completed immediately after getElements()", work.futureComplete);
                assertEquals(work.contentFuture.get(), work.items);
            }
        }
    }

    public void testRootGroupsCompletion() throws Exception {
        loadData("bigv-1.0.bgv"); // NOI18N
        reader.parse();

        CompletionService<Pair<LazyGroup, Future>> cs = new ExecutorCompletionService(RP);
        GraphDocument doc = mb.rootDocument();
        int count = 0;
        for (FolderElement tl : doc.getElements()) {
            if (tl instanceof Group) {
                assertTrue(tl instanceof LazyGroup);
                LazyGroup lg = (LazyGroup) tl;
                assertFalse(lg.isComplete());
                Future<List<? extends FolderElement>> cF = lg.completeContents(null);
                cs.submit(() -> {
                    try {
                        cF.get();
                    } catch (Exception ex) {
                    }
                }, new Pair(lg, cF));
                count++;
            }
        }
        for (int i = 0; i < count; i++) {
            Future<Pair<LazyGroup, Future>> r = cs.take();
            Pair<LazyGroup, Future> pair = r.get();
            Future<List<? extends FolderElement>> itemsF = pair.getRight();
            LazyGroup gr = pair.getLeft();

            assertSame("Upon completion the future must be the same for all clients", itemsF, gr.completeContents(null));
            assertTrue("Group must report the same completion as future", gr.isComplete());
            assertTrue(itemsF.isDone());

            List<? extends FolderElement> items = itemsF.get();
            assertEquals("Group elements must be the same as completed", gr.getElements(), items);
        }
    }

    public void testRootGroupsAreLazy() throws Exception {
        loadData("bigv-3.0.bgv");
        reader.parse();
        for (FolderElement fe : checkDocument.getElements()) {
            assertTrue(fe instanceof LazyGroup);
        }
    }

    /**
     * Checks that the root document populates lazily, groups are known before the stream finishes
     */
    public void testPartialDocumentLoad() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        // load first 2 groups, then stop
        mb.groupCount = 2;
        AtomicReference<Error> threadErr = new AtomicReference<>();
        AtomicInteger reportedCnt = new AtomicInteger(0);
        AtomicReference<List> contents = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);
        checkDocument.getChangedEvent().addListener(new ChangedListener<GraphDocument>() {
            @Override
            public void changed(GraphDocument source) {
                // got event
                reportedCnt.incrementAndGet();
                contents.set(source.getElements());
                latch.countDown();
            }
        });

        mb.groupConsumer = (g) -> {
            try {
                // this group SHOULD be already reported AND present, but must wait
                // until all events arrive
                latch.await(1000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                fail("interrupted");
            }
            assertEquals(2, reportedCnt.get());
            assertTrue(contents.get().contains(g));
        };

        PARALLEL_LOAD.post(() -> {
            try {
                reader.parse();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } catch (Error e) {
                threadErr.set(e);
            }
        }).waitFinished();

        if (threadErr.get() != null) {
            throw threadErr.get();
        }
    }

    /**
     * Checks that a group contains observable content before it finishes loading
     */
    public void testPartialGraphSeriesLoad() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        reader.parse();
        int[] stopAtOffsets = {2800, 6000, 7600, 9000, 10000, -1};
        int[] readCounts = {0, 1, 4, 6, 7, 9};

        LazyGroup parent = (LazyGroup) checkDocument.getElements().get(0);
        assertFalse(parent.isComplete());
        CountDownLatch readingStarted = new CountDownLatch(1);
        // start load from the group, but stop after first few graphs;
        file.channelFactory = (long[] bounds) -> {
            try {
                freezeChannel = new FreezeChannel(bounds[0], bounds[1], 1000);
                freezeChannel.freezeAt = stopAtOffsets[0];
                readingStarted.countDown();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            return freezeChannel;
        };
        AtomicInteger itemCount = new AtomicInteger(0);
        Semaphore sem = new Semaphore(0);
        parent.getChangedEvent().addListener((g) -> {
            // should get some change events
            itemCount.set(g.getElements().size());
            sem.release();
        });

        // trigger load, the parallel loader will block on the freezeChannel semaphore.
        Future loadF = parent.completeContents(null);
        assertFalse(loadF.isDone());

        // 1386 - 2676
        CountDownLatch readingComplete = new CountDownLatch(1);
        AtomicReference<List> loadedContents = new AtomicReference<>(null);
        PARALLEL_LOAD.post(() -> {
            List ll = parent.getElements();
            loadedContents.set(ll);
            readingComplete.countDown();
        });

        int cnt = 0;

        class MD {
            int nodes;
            int edges;
            LazyGraph graph;
            Set nodeIds;
        }

        List<MD> metas = new ArrayList<>();

        readingStarted.await();
        Thread.sleep(200);
        // stop several graph loads in the middle; observe that 
        // the graphs read so far fully were reported. 
        for (int idx = 1; idx < stopAtOffsets.length; idx++) {
            // wait until reading freezes
            int expectEvents = readCounts[idx] - cnt;
            freezeChannel.frozen.acquire();
            sem.acquire(expectEvents);
            if (itemCount.get() == cnt) {
                fail("Graph properties not loaded");
            }
            cnt = itemCount.get();
            List<? extends FolderElement> l = parent.getElements();
            assertTrue("Size now must be no smaller than size reported to event", cnt <= l.size());
            assertSame("Incorrect number of items read before input freeze", cnt, readCounts[idx]);
            FolderElement elem = l.get(cnt - 1);
            //assertTrue(elem instanceof LazyGraph);
            InputGraph lg = (InputGraph) elem;
            assertNotNull("No properties read", lg.getName());
            assertSame("Invalid parent", parent, lg.getParent());
            assertTrue("Parent does not contain item", parent.getElements().contains(lg));

            // release further reading
            freezeChannel.freezeAt = stopAtOffsets[idx];
            freezeChannel.condition.release();
        }
        readingComplete.await();
    }

    class MD {
        int nodes;
        int edges;
        InputGraph graph;
        Set nodeIds;
    }

    /**
     * Checks that a group contains observable content before it finishes loading.
     * Checks consistency of LazyGraph's metadata provided during the load with graph
     * contents after loading completes.
     */
    public void testPartialLazyGraphSeriesLoad() throws Exception {
        loadData("bigv-2.0.bgv"); // NOI18N
        // make all graphs lazy
        SingleGroupBuilder.setLargeEntryThreshold(300);
        reader.parse();

        int[] stopAtOffsets = {2800, 6000, 7600, 9000, 10000, 0};
        int[] readCounts = {0, 1, 4, 6, 7, 9};

        LazyGroup parent = (LazyGroup) checkDocument.getElements().get(0);
        assertFalse(parent.isComplete());
        CountDownLatch readingStarted = new CountDownLatch(1);
        // start load from the group, but stop after first few graphs;
        file.channelFactory = (long[] bounds) -> {
            try {
                freezeChannel = new FreezeChannel(bounds[0], bounds[1], 1000);
                freezeChannel.freezeAt = stopAtOffsets[0];
                readingStarted.countDown();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            return freezeChannel;
        };

        AtomicInteger itemCount = new AtomicInteger(0);
        Semaphore sem = new Semaphore(0);
        parent.getChangedEvent().addListener((g) -> {
            // should get some change events
            itemCount.set(g.getElements().size());
            sem.release();
        });

        // trigger load, the parallel loader will block on the freezeChannel semaphore.
        Future loadF = parent.completeContents(null);
        assertFalse(loadF.isDone());

        // 1386 - 2676

        CountDownLatch readingComplete = new CountDownLatch(1);
        AtomicReference<List> loadedContents = new AtomicReference<>(null);
        PARALLEL_LOAD.post(() -> {
            List ll = parent.getElements();
            loadedContents.set(ll);
            synchronized (loadedContents) {
                loadedContents.notifyAll();
            }
            readingComplete.countDown();
        });

        int cnt = 0;

        List<MD> metas = new ArrayList<>();

        readingStarted.await();
        Thread.sleep(200);
        // stop several graph loads in the middle; observe that 
        // the graphs read so far fully were reported. 
        for (int idx = 1; idx < stopAtOffsets.length; idx++) {
            // wait until reading freezes
            freezeChannel.frozen.acquire();
            int expectEvents = readCounts[idx] - cnt;
            sem.acquire(expectEvents);
            synchronized (itemCount) {
                if (itemCount.get() == cnt) {
                    fail("Graph properties not loaded");
                }
                cnt = itemCount.get();
                List<? extends FolderElement> l = parent.getElements();
                assertTrue("Size now must be no smaller than size reported to event", cnt <= l.size());
                assertSame("Incorrect number of items read before input freeze", cnt, readCounts[idx]);
                FolderElement elem = l.get(cnt - 1);

                assertTrue(elem instanceof LazyGraph);
                InputGraph lg = (InputGraph) elem;
                assertNotNull("No properties read", lg.getName());
                assertSame("Invalid parent", parent, lg.getParent());
                assertTrue("Parent does not contain item", parent.getElements().contains(lg));
                // check some basic properties:
                MD md = new MD();
                md.graph = lg;
                md.nodes = lg.getNodeCount();
                md.edges = lg.getEdgeCount();
                md.nodeIds = lg.getNodeIds();
                metas.add(md);
            }
            // release further reading
            freezeChannel.freezeAt = stopAtOffsets[idx];
            freezeChannel.condition.release();
        }

        readingComplete.await();
        for (MD md : metas) {
            checkGraphMetadata(md);
        }
    }

    private void checkGraphMetadata(MD md) {
        InputGraph g = md.graph;

        assertEquals(g.getNodes().size(), g.getNodeCount());
        assertEquals(g.getEdges().size(), g.getEdgeCount());
        assertEquals(g.getNodes().size(), g.getNodeIds().size());
        Group gr = (Group) g.getParent();

        InputGraph prev = g.getPrev();
        for (InputNode n : g.getNodes()) {
            int id = n.getId();
            // nodeIds are extracted by scanning builder
            assertTrue(g.getNodeIds().contains(id));
            if (prev == null) {
                continue;
            }
            InputNode pn = prev.getNode(id);
            if (pn == null) {
                assertFalse(prev.getNodeIds().contains(id));
                continue;
            }
            // property changes are scanned by single group builder
            if (pn.getProperties().equals(n.getProperties())) {
                assertFalse(gr.isNodeChanged(prev, g, id));
            } else {
                assertTrue(gr.isNodeChanged(prev, g, id));
            }
        }
    }

    /**
     * If a graph (group) starts to load before the entry is fully scanned,
     * its stream is "till the end of stream".
     *
     * @throws Exception
     */
    public void testTailLoadFinishesAtEntryEnd() throws Exception {
        /*
        Stop the main scanner at ~1400, which is inside the 1st graph. Then 
        attempt to expand its parent and wait - the expansion process will block
        */
        loadData("bigv-2.0.bgv"); // NOI18N

        Semaphore cont = new Semaphore(0);
        file.freezeAt = 1700;
        file.channelFactory = (long[] bounds) -> {
            try {
                freezeChannel = new FreezeChannel(bounds[0], bounds[1], 1400 - bounds[0]);
                cont.release();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            return freezeChannel;
        };
        Semaphore start = new Semaphore(0);
        checkDocument.getChangedEvent().addListener((e) -> start.release());

        SingleGroupBuilder.setLargeEntryThreshold(300);
        Task t = PARALLEL_LOAD.post(() -> {
            try {
                reader.parse();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        });

        do {
            start.acquire();
        } while (checkDocument.getElements().isEmpty());

        file.frozen.acquire();

        LazyGroup parent = (LazyGroup) checkDocument.getElements().get(0);
        assertFalse(parent.isComplete());

        // trigger load, the parallel loader will block on the freezeChannel semaphore.
        Future loadF = parent.completeContents(null);

        cont.acquire();
        file.condition.release();
        freezeChannel.condition.release();

        assertFalse(loadF.isDone());
        List l = (List) loadF.get();
        assertFalse(l.isEmpty());

    }
}
