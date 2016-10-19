/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.nodes.extended.UnsafeAccessNode;
import com.oracle.graal.nodes.memory.ReadNode;
import com.oracle.graal.nodes.memory.WriteNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.LoweringPhase;
import com.oracle.graal.phases.tiers.PhaseContext;
import com.oracle.graal.virtual.phases.ea.EarlyReadEliminationPhase;
import com.oracle.graal.virtual.phases.ea.PartialEscapePhase;

import sun.misc.Unsafe;

public class UnsafeReadEliminationTest extends GraalCompilerTest {

    public static final Unsafe UNSAFE;
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("Exception while trying to get Unsafe", e);
        }
    }

    public static long[] Memory = new long[]{1, 2};
    public static double SideEffect;

    public static long test1Snippet(double a) {
        final Object m = Memory;
        if (a > 0) {
            UNSAFE.putDouble(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET, a);
        } else {
            SideEffect = UNSAFE.getLong(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET);
        }
        return UNSAFE.getLong(m, (long) Unsafe.ARRAY_LONG_BASE_OFFSET);
    }

    @Test
    public void testEarlyReadElimination() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.NO);
        PhaseContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        new EarlyReadEliminationPhase(canonicalizer).apply(graph, context);
        Assert.assertEquals(3, graph.getNodes().filter(UnsafeAccessNode.class).count());
        // after lowering the same applies for reads and writes
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new EarlyReadEliminationPhase(canonicalizer).apply(graph, context);
        Assert.assertEquals(3/* 2 x unsafe + "Memory" load */, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals(2/* 1 x unsafe + "SideEffect" write */, graph.getNodes().filter(WriteNode.class).count());
    }

    @Test
    public void testPartialEscapeReadElimination() {
        StructuredGraph graph = parseEager("test1Snippet", AllowAssumptions.NO);
        PhaseContext context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(true, true, canonicalizer, null).apply(graph, context);
        Assert.assertEquals(3, graph.getNodes().filter(UnsafeAccessNode.class).count());
        // after lowering the same applies for reads and writes
        new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        canonicalizer.apply(graph, context);
        new PartialEscapePhase(true, true, canonicalizer, null).apply(graph, context);
        Assert.assertEquals(3/* 2 x unsafe + "Memory" load */, graph.getNodes().filter(ReadNode.class).count());
        Assert.assertEquals(2/* 1 x unsafe + "SideEffect" write */, graph.getNodes().filter(WriteNode.class).count());
    }

}
