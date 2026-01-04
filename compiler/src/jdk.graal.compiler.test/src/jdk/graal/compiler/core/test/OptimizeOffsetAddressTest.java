/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeoptimizationGroupingPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.OptimizeOffsetAddressPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * Test {@link jdk.graal.compiler.phases.common.OptimizeOffsetAddressPhase}.
 */
public class OptimizeOffsetAddressTest extends GraalCompilerTest {

    static int snippet0(byte[] a) {
        int sum = 0;
        for (int i = 16; i < a.length; i++) {
            sum += a[i - 16];
        }
        return sum;
    }

    @Test
    public void testSnippet0() {
        Assume.assumeTrue(UNSAFE.arrayBaseOffset(byte[].class) == 16);

        StructuredGraph graph = parseEager("snippet0", StructuredGraph.AllowAssumptions.YES);

        // resembling an economy phase plan with floating reads

        CanonicalizerPhase canonicalizer = CanonicalizerPhase.createSingleShot();
        canonicalizer.apply(graph, getDefaultHighTierContext());
        new HighTierLoweringPhase(canonicalizer, true).apply(graph, getDefaultHighTierContext());

        new FloatingReadPhase(createCanonicalizerPhase()).apply(graph, getDefaultMidTierContext());

        new RemoveValueProxyPhase(canonicalizer).apply(graph, getDefaultMidTierContext());
        new LoopSafepointInsertionPhase().apply(graph, getDefaultMidTierContext());
        new GuardLoweringPhase().apply(graph, getDefaultMidTierContext());
        new MidTierLoweringPhase(canonicalizer).apply(graph, getDefaultMidTierContext());
        new FrameStateAssignmentPhase().apply(graph, getDefaultMidTierContext());
        new DeoptimizationGroupingPhase().apply(graph, getDefaultMidTierContext());
        canonicalizer.apply(graph, getDefaultMidTierContext());
        new WriteBarrierAdditionPhase().apply(graph, getDefaultMidTierContext());

        assertTrue(graph.getNodes().filter(AddNode.class).count() == 4);
        new OptimizeOffsetAddressPhase(createCanonicalizerPhase()).apply(graph, getDefaultLowTierContext());
        // We turn Add(16, ZeroExtend(Add(i, -16))) into Add(16, Add(ZeroExtend(i), -16)),
        // and expect both Adds to be folded.
        // The inner Add has other dependency and won't be eliminated.
        assertTrue(graph.getNodes().filter(AddNode.class).count() == 3);
    }

    static int snippet1(byte[] a) {
        int sum = 0;
        int i = -2147483646;
        do {
            int index = -2147483647 * i + -2147483647;
            sum += a[index];
            i += -2;
        } while (i <= -2147483646);
        return sum;
    }

    @Test
    public void testSnippet1() {
        StructuredGraph graph = parseEager("snippet1", StructuredGraph.AllowAssumptions.YES);

        Suites suites = super.createSuites(getInitialOptions());
        suites.getHighTier().apply(graph, getDefaultHighTierContext());
        suites.getMidTier().apply(graph, getDefaultMidTierContext());

        Graph.Mark mark = graph.getMark();
        new OptimizeOffsetAddressPhase(createCanonicalizerPhase()).apply(graph, getDefaultLowTierContext());
        // OptimizeOffsetAddressPhase is not applicable
        assertTrue(graph.getNewNodes(mark).isEmpty());
    }
}
