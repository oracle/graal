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
package jdk.graal.compiler.loop.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.vector.phases.RemoveEmptyLoopsPhase;

/// Tests loop entry at signed and unsigned endpoints and preservation of entered-loop stores.
public class CountedLoopEntryTest extends GraalCompilerTest {

    /// Makes removal of an entered loop observable in its graph.
    public static int sink;

    /// Supplies an inclusive signed loop with an observable body store.
    public static void signedIntDown(int start, int limit) {
        for (int i = start; i >= limit; i--) {
            sink++;
        }
    }

    /// Supplies an inclusive signed loop with an observable body store.
    public static void signedIntUp(int start, int limit) {
        for (int i = start; i <= limit; i++) {
            sink++;
        }
    }

    /// Supplies an inclusive unsigned loop with an observable body store.
    public static void unsignedIntDown(int start, int limit) {
        for (int i = start; Integer.compareUnsigned(i, limit) >= 0; i--) {
            sink++;
        }
    }

    /// Supplies an inclusive unsigned loop with an observable body store.
    public static void unsignedIntUp(int start, int limit) {
        for (int i = start; Integer.compareUnsigned(i, limit) <= 0; i++) {
            sink++;
        }
    }

    /// Supplies an inclusive signed loop with an observable body store.
    public static void signedLongDown(long start, long limit) {
        for (long i = start; i >= limit; i--) {
            sink++;
        }
    }

    /// Supplies an inclusive signed loop with an observable body store.
    public static void signedLongUp(long start, long limit) {
        for (long i = start; i <= limit; i++) {
            sink++;
        }
    }

    /// Supplies an inclusive unsigned loop with an observable body store.
    public static void unsignedLongDown(long start, long limit) {
        for (long i = start; Long.compareUnsigned(i, limit) >= 0; i--) {
            sink++;
        }
    }

    /// Supplies an inclusive unsigned loop with an observable body store.
    public static void unsignedLongUp(long start, long limit) {
        for (long i = start; Long.compareUnsigned(i, limit) <= 0; i++) {
            sink++;
        }
    }

    /// Supplies an exclusive loop that is never entered.
    public static void exclusiveInt(int start) {
        for (int i = start; i < start; i++) {
            sink++;
        }
    }

    /// Supplies an exclusive loop that is never entered.
    public static void exclusiveLong(long start) {
        for (long i = start; i < start; i++) {
            sink++;
        }
    }

    /// Covers signed int entry at maximum values and genuinely unentered controls.
    @Test
    public void testSignedIntEntry() {
        checkEntry("signedIntDown", true, Integer.MAX_VALUE, Integer.MAX_VALUE - 2);
        checkEntry("signedIntDown", true, Integer.MAX_VALUE, Integer.MAX_VALUE);
        checkEntry("signedIntDown", false, Integer.MIN_VALUE, Integer.MIN_VALUE + 1);
        checkEntry("signedIntUp", true, Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1);
        checkEntry("signedIntUp", true, Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        checkEntry("signedIntUp", false, Integer.MAX_VALUE, Integer.MAX_VALUE - 2);
    }

    /// Covers unsigned int entry at maximum values and genuinely unentered controls.
    @Test
    public void testUnsignedIntEntry() {
        checkEntry("unsignedIntDown", true, -1, -3);
        checkEntry("unsignedIntDown", true, -1, -1);
        checkEntry("unsignedIntDown", false, 0, 1);
        checkEntry("unsignedIntUp", true, -4, -2);
        checkEntry("unsignedIntUp", true, -2, -2);
        checkEntry("unsignedIntUp", false, -1, 1);
    }

    /// Covers signed long entry at maximum values and genuinely unentered controls.
    @Test
    public void testSignedLongEntry() {
        checkEntry("signedLongDown", true, Long.MAX_VALUE, Long.MAX_VALUE - 2);
        checkEntry("signedLongDown", true, Long.MAX_VALUE, Long.MAX_VALUE);
        checkEntry("signedLongDown", false, Long.MIN_VALUE, Long.MIN_VALUE + 1);
        checkEntry("signedLongUp", true, Long.MAX_VALUE - 2, Long.MAX_VALUE - 1);
        checkEntry("signedLongUp", true, Long.MAX_VALUE - 1, Long.MAX_VALUE - 1);
        checkEntry("signedLongUp", false, Long.MAX_VALUE, Long.MAX_VALUE - 2);
    }

    /// Covers unsigned long entry at maximum values and genuinely unentered controls.
    @Test
    public void testUnsignedLongEntry() {
        checkEntry("unsignedLongDown", true, -1L, -3L);
        checkEntry("unsignedLongDown", true, -1L, -1L);
        checkEntry("unsignedLongDown", false, 0L, 1L);
        checkEntry("unsignedLongUp", true, -4L, -2L);
        checkEntry("unsignedLongUp", true, -2L, -2L);
        checkEntry("unsignedLongUp", false, -1L, 1L);
    }

    /// Verifies that exclusive loops with identical start and limit still lose their stores.
    @Test
    public void testExclusiveLoopsAreRemoved() {
        checkEntry("exclusiveInt", false, 42);
        checkEntry("exclusiveLong", false, 42L);
    }

    /// Checks the entry analysis and then applies the real removal phase to the store-bearing loop.
    private void checkEntry(String snippet, boolean entered, Object... arguments) {
        StructuredGraph graph = parseEager(snippet, StructuredGraph.AllowAssumptions.NO);
        bindArguments(graph, arguments);
        createCanonicalizerPhase().apply(graph, getDefaultMidTierContext());
        LoopsData loops = getDefaultMidTierContext().getLoopsDataProvider().getLoopsData(graph);
        Assert.assertEquals(snippet, 1, loops.loops().size());
        Assert.assertTrue(snippet, loops.loops().get(0).detectCounted());
        CountedLoopInfo counted = loops.loops().get(0).counted();
        Assert.assertEquals(snippet, entered, counted.loopMightBeEntered());
        Assert.assertEquals(snippet, 1, graph.getNodes().filter(StoreFieldNode.class).count());

        new RemoveEmptyLoopsPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());
        Assert.assertEquals(snippet, entered ? 1 : 0, graph.getNodes().filter(StoreFieldNode.class).count());
    }
}
