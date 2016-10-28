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

import org.junit.Test;

import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.StructuredGraph.AllowAssumptions;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.DominatorConditionalEliminationPhase;
import com.oracle.graal.phases.tiers.HighTierContext;

public class ConditionalEliminationMulTest extends GraalCompilerTest {

    public static void snippet01(int a) {
        if (a == 2) {
            if (a * 3 != 6) {
                shouldBeOptimizedAway();
            }
        }
    }

    public static void snippet02(int a) {
        if (a == 0) {
            if (a * 3 != 0) {
                shouldBeOptimizedAway();
            }
        }
    }

    public static void snippet03(int a) {
        if (a * 0 == 6) {
            shouldBeOptimizedAway();
        }
    }

    @Test
    public void testConditionalEliminated01() {
        assertOptimized("snippet01");
    }

    @Test
    public void testConditionalEliminated02() {
        assertOptimized("snippet02");
    }

    @Test
    public void testConditionalEliminated03() {
        assertOptimized("snippet03");
    }

    private void assertOptimized(String snippet) {
        assertOptimizedAway(prepareGraph(snippet));
    }

    private StructuredGraph prepareGraph(String snippet) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase c = new CanonicalizerPhase();
        c.apply(graph, context);
        new DominatorConditionalEliminationPhase(false).apply(graph, context);
        c.apply(graph, context);
        return graph;
    }

}
