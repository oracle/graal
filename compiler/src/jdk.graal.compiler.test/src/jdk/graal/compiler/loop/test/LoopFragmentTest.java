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
package jdk.graal.compiler.loop.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.loop.phases.LoopPartialUnrollPhase;
import jdk.graal.compiler.loop.phases.LoopUnswitchingPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.loop.DefaultLoopPolicies;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DisableOverflownCountedLoopsPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoopSafepointInsertionPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;

public class LoopFragmentTest extends GraalCompilerTest {

    boolean check = true;

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        if (!check) {
            return;
        }
        NodeIterable<LoopBeginNode> loops = graph.getNodes().filter(LoopBeginNode.class);
        // Loops might be optimizable after partial unrolling
        boolean seenLoop = false;
        for (LoopBeginNode loop : loops) {
            if (loop.isAnyStripMinedOuter()) {
                continue;
            }
            seenLoop = true;
            if (loop.isMainLoop()) {
                return;
            }
        }
        if (seenLoop) {
            fail("expected a main loop");
        }
    }

    static volatile int volatileInt = 3;

    public static int testUnswitchPattern1(int iterations) {
        Integer sum = 0;
        for (int i = 0; injectBranchProbability(0.99, i < iterations); i++) {
            if (sum == null) {
                sum = null;
            } else {
                sum += i;
            }
            int t1 = volatileInt;
            if (iterations == 1) {
                GraalDirectives.sideEffect(t1);
            } else {
                GraalDirectives.sideEffect(i);
            }
        }
        return sum.intValue();
    }

    public static int testUnswitchPattern2(int iterations) {
        Integer sum = 0;
        for (int i = 0; injectBranchProbability(0.99, i < iterations); i++) {
            if (sum == null) {
                sum = null;
            } else {
                sum += i;
            }
        }
        return sum.intValue();
    }

    @Test
    public void testUnswitch() {
        StructuredGraph g = parseEager(getResolvedJavaMethod("testUnswitchPattern1"), AllowAssumptions.NO);
        new DisableOverflownCountedLoopsPhase().apply(g);

        CanonicalizerPhase c = CanonicalizerPhase.create();
        c.apply(g, getDefaultHighTierContext());
        new PartialEscapePhase(true, c, getInitialOptions()).apply(g, getDefaultHighTierContext());
        new LoopUnswitchingPhase(new DefaultLoopPolicies(), c).apply(g, getDefaultHighTierContext());
        assert g.getNodes(LoopBeginNode.TYPE).count() == 2;
        assert g.verify();

        resetCache();

        g = parseEager(getResolvedJavaMethod("testUnswitchPattern2"), AllowAssumptions.NO);
        new DisableOverflownCountedLoopsPhase().apply(g);
        c = CanonicalizerPhase.create();
        c.apply(g, getDefaultHighTierContext());
        new PartialEscapePhase(true, c, getInitialOptions()).apply(g, getDefaultHighTierContext());
        c.apply(g, getDefaultHighTierContext());
        new HighTierLoweringPhase(c, true).apply(g, getDefaultHighTierContext());
        new RemoveValueProxyPhase(c).apply(g, getDefaultMidTierContext());
        new LoopSafepointInsertionPhase().apply(g, getDefaultMidTierContext());
        new GuardLoweringPhase().apply(g, getDefaultMidTierContext());
        new MidTierLoweringPhase(c).apply(g, getDefaultMidTierContext());
        new FrameStateAssignmentPhase().apply(g);
        new LoopPartialUnrollPhase(new DefaultLoopPolicies(), c).apply(g, getDefaultHighTierContext());
        assert g.verify();
        assert GraphOrder.assertSchedulableGraph(g);

        resetCache();
        test("testUnswitchPattern1", 100);
        test("testUnswitchPattern2", 100);
    }

}
