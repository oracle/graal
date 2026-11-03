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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.loop.phases.AggressivePartialUnrollPhase;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.phases.VectorLoopUtility;

/**
 * Regression test for late empty-loop removal (GR-80584).
 */
public class LateRemoveEmptyLoopsTest extends GraalCompilerTest {

    /**
     * Checks that the mid-tier graph contains a main loop.
     */
    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        super.checkMidTierGraph(graph);
        boolean hasMainLoop = false;
        for (LoopBeginNode loop : graph.getNodes(LoopBeginNode.TYPE)) {
            hasMainLoop |= loop.isMainLoop();
        }
        Assert.assertTrue("expected main loop in mid-tier graph", hasMainLoop);
    }

    /**
     * Checks that the low-tier graph contains no main loop.
     */
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        super.checkLowTierGraph(graph);
        for (LoopBeginNode loop : graph.getNodes(LoopBeginNode.TYPE)) {
            Assert.assertFalse("unexpected main loop: " + loop, loop.isMainLoop());
        }
    }

    /**
     * Sets {@code state} to 1 on odd iterations.
     *
     * @param limit the exclusive loop bound
     * @param initial the initial state
     * @return {@code initial} if {@code limit <= 1}, otherwise 1
     */
    public static int alternatingState(int limit, int initial) {
        int state = initial;
        for (int i = 0; GraalDirectives.injectIterationCount(1_000_000, i < limit); i++) {
            if (GraalDirectives.injectBranchProbability(0.5, (i & 1) != 0)) {
                state = 1;
            }
        }
        return state;
    }

    /**
     * Checks the result and absence of main loops.
     */
    @Test
    public void testAlternatingState() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        AggressivePartialUnrollPhase.Options.HighTierPartialUnrolling, false,
                        VectorLoopUtility.Options.RespectVectorization, false);
        test(options, "alternatingState", 20, 23);
    }
}
