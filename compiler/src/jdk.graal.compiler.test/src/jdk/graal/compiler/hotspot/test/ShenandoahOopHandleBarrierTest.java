/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.hotspot.test;

import java.util.ListIterator;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.api.test.ModuleSupport;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.test.TestPhase;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntime.HotSpotGC;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.gc.ObjectWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahCardBarrierNode;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahSATBBarrierNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * Tests the barriers emitted by Shenandoah for a store into the contents of an {@code OopHandle},
 * which lives in a native OopStorage rather than in the Java heap.
 *
 * <p>
 * Such a store requires the SATB pre barrier but must <em>not</em> be card marked: the card address
 * is computed as {@code card_table_base + (store_address >> card_shift)}, which for a non-heap
 * address lands outside the card table. HotSpot draws the same distinction via
 * {@code ShenandoahBarrierSet::need_card_barrier} (gated on {@code IN_HEAP}) and its
 * {@code oop_store_in_heap} / {@code oop_store_not_in_heap} split.
 *
 * <p>
 * The store under test is the one emitted by the {@code Thread.setScopedValueCache} invocation
 * plugin, reached by compiling {@code java.lang.ScopedValue$Cache.put}. That method conveniently
 * contains both kinds of oop store: the off-heap handle write, and ordinary in-heap array element
 * writes which must keep their card barrier.
 */
public class ShenandoahOopHandleBarrierTest extends HotSpotGraalCompilerTest {

    private boolean sawOopHandleWrite;
    private boolean sawInHeapObjectWrite;

    public static class Holder {
        Object field;
    }

    public static void heapFieldStoreSnippet(Holder h, Object value) {
        h.field = value;
    }

    /**
     * An ordinary in-heap oop store must keep its card barrier. Guards against the off-heap fix
     * suppressing card marks too broadly.
     */
    @Test
    public void testInHeapFieldWriteIsCardMarked() {
        Assume.assumeTrue("Shenandoah specific test", runtime().getGarbageCollector() == HotSpotGC.Shenandoah);
        Assume.assumeTrue("card barriers are only enabled for generational Shenandoah",
                        runtime().getVMConfig().getFlag("ShenandoahCardBarrier", Boolean.class));
        test("heapFieldStoreSnippet", new Holder(), "value");
        Assert.assertTrue("expected to see an in-heap oop store", sawInHeapObjectWrite);
    }

    /**
     * A store into the contents of an {@code OopHandle} must keep the SATB pre barrier but must not
     * be card marked.
     */
    @Test
    public void testScopedValueCacheHandleWrite() {
        Assume.assumeTrue("Shenandoah specific test", runtime().getGarbageCollector() == HotSpotGC.Shenandoah);
        ModuleSupport.exportAndOpenAllPackagesToUnnamed("java.base");

        // Compiles the graph produced by the Thread.setScopedValueCache invocation plugin, which
        // writes the Object[] into the JavaThread::_scopedValueCache OopHandle.
        compileAndInstallSubstitution(Thread.class, "setScopedValueCache");

        Assert.assertTrue("expected to compile a write to the scoped value cache OopHandle",
                        sawOopHandleWrite);
    }

    private void verifyBarriers(StructuredGraph graph) {
        for (WriteNode write : graph.getNodes().filter(WriteNode.class)) {
            boolean isOopHandle = write.getLocationIdentity() instanceof HotSpotReplacementsUtil.OopHandleLocationIdentity;
            if (isOopHandle) {
                sawOopHandleWrite = true;

                Assert.assertEquals("OopHandle oop store should use a write barrier type",
                                BarrierType.FIELD, write.getBarrierType());

                // The SATB pre barrier is still required for an off-heap oop store.
                Assert.assertTrue("OopHandle oop store must keep its SATB pre barrier",
                                hasBarrierFor(graph, ShenandoahSATBBarrierNode.class, write));

                // The card barrier must not be applied to a non-heap address.
                Assert.assertFalse("OopHandle oop store must not be card marked",
                                hasBarrierFor(graph, ShenandoahCardBarrierNode.class, write));
            } else if (write.getBarrierType() == BarrierType.FIELD || write.getBarrierType() == BarrierType.ARRAY) {
                // Ordinary in-heap oop stores must still be card marked.
                sawInHeapObjectWrite = true;
                Assert.assertTrue("in-heap oop store must be card marked: " + write,
                                hasBarrierFor(graph, ShenandoahCardBarrierNode.class, write));
            }
        }
    }

    /**
     * Determines whether {@code graph} contains a barrier of the given type operating on the same
     * address as {@code write}. Matching on the address rather than on graph adjacency keeps this
     * robust against unrelated nodes being scheduled between the write and its barriers.
     */
    private static boolean hasBarrierFor(StructuredGraph graph, Class<? extends ObjectWriteBarrierNode> barrierClass, WriteNode write) {
        for (ObjectWriteBarrierNode barrier : graph.getNodes().filter(barrierClass)) {
            if (barrier.getAddress() == write.getAddress()) {
                return true;
            }
        }
        return false;
    }

    /*
     * Check the state of the barriers immediately after insertion. Shenandoah inserts barriers in
     * the low tier (LOW_TIER_BARRIER_ADDITION), unlike the card table collectors which use the mid
     * tier.
     */
    @Override
    protected Suites createSuites(OptionValues opts) {
        Suites ret = super.createSuites(opts);
        ListIterator<BasePhase<? super LowTierContext>> iter = ret.getLowTier().findPhase(WriteBarrierAdditionPhase.class, true);
        iter.add(new TestPhase() {
            @Override
            protected void run(StructuredGraph graph) {
                verifyBarriers(graph);
            }

            @Override
            public float codeSizeIncrease() {
                return NodeSize.IGNORE_SIZE_CONTRACT_FACTOR;
            }

            @Override
            public CharSequence getName() {
                return "VerifyShenandoahOopHandleBarriersPhase";
            }
        });
        return ret;
    }
}
