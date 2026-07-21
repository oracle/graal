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
package jdk.graal.compiler.duplication.test;

import static jdk.graal.compiler.api.directives.GraalDirectives.injectBranchProbability;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.PullThroughPhiPhase;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.util.GraphOrder;

public class PullThroughPhiTest extends GraalCompilerTest {
    public static Object field;

    public static int s01(int a, int b, int c) {
        int phi1 = 0;
        int phi2 = 0;
        int phi3 = 0;
        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            phi1 = a;
        } else {
            field = null;
            phi1 = 4;
        }
        if (injectBranchProbability(0.1, b == 0)) {
            field = null;
            phi2 = b;
        } else {
            field = null;
            phi2 = 8;
        }
        if (injectBranchProbability(0.1, c == 0)) {
            field = null;
            phi3 = c;
        } else {
            field = null;
            phi3 = 16;
        }
        field = null;
        return phi1 * phi2 * phi3;
    }

    public static int s0101(int a, int b, int c) {
        int phi1 = 0;
        int related = 0;
        int phi2 = 0;
        int phi3 = 0;
        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            phi1 = a;
            related = b;
        } else {
            field = null;
            phi1 = 4;
            related = 8;
        }
        if (injectBranchProbability(0.1, b == 0)) {
            field = null;
            phi2 = b;
        } else {
            field = null;
            phi2 = 8;
        }
        if (injectBranchProbability(0.1, c == 0)) {
            field = null;
            phi3 = c;
        } else {
            field = null;
            phi3 = 16;
        }
        field = null;
        return (phi1 * related) * phi2 * phi3;
    }

    public static int s02(int a, int b, int c) {
        int phi1 = 0;
        int phi2 = 0;
        int phi3 = 0;

        int leafPhi = 0;
        if (injectBranchProbability(0.1, a + b + c == 0)) {
            field = null;
            leafPhi = 1;
        } else {
            field = null;
            leafPhi = 2;
        }

        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            phi1 = 2;
        } else {
            field = null;
            phi1 = leafPhi * 4;
        }
        if (injectBranchProbability(0.1, b == 0)) {
            field = null;
            phi2 = 16;
        } else {
            field = null;
            phi2 = leafPhi * 32;
        }
        if (injectBranchProbability(0.1, c == 0)) {
            field = null;
            phi3 = 8;
        } else {
            field = null;
            phi3 = leafPhi * 16;
        }
        field = null;
        return (phi1 + 1) * (phi2 + 2) * (phi3 + 3);
    }

    public static int phi01(int a) {
        int c = 0;
        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            c = 0;
        } else {
            field = null;
            c = a;
        }
        return c * c;
    }

    public static int phi02(int a) {
        int c1 = 0;
        int c2 = 0;
        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            c1 = 0;
            c2 = a;
        } else {
            field = null;
            c1 = a;
            c2 = 0;
        }
        return c1 * c2;
    }

    public static int phi03(int a) {
        int c1 = 0;
        int c2 = 0;
        if (injectBranchProbability(0.1, a == 100)) {
            field = null;
            c1 = 0;
        } else {
            field = null;
            c1 = a;
        }
        field = null;
        if (injectBranchProbability(0.1, a == 0)) {
            field = null;
            c2 = 0;
        } else {
            field = null;
            c2 = a;
        }
        return c1 + c2;
    }

    @Test
    public void test101() {
        testGraph("s0101", 0, 0, 0);
        testGraph("s0101", 0, 1, 0);
        testGraph("s0101", 0, 0, 1);
    }

    @Test
    public void test1() {
        testGraph("s01", 0, 0, 0);
        testGraph("s01", 0, 1, 0);
        testGraph("s01", 0, 0, 1);
    }

    @Test
    public void test2() {
        testGraph("s02", 0, 0, 0);
        testGraph("s02", 0, 1, 0);
        testGraph("s02", 0, 0, 1);
    }

    @Test
    public void testPhis1() {
        testGraph("phi01", 0);
        testGraph("phi01", 1);
    }

    @Test
    public void testPhis2() {
        testGraph("phi02", 0);
        testGraph("phi02", 1);
    }

    @Test
    public void testPhis3() {
        testGraph("phi03", 0);
        testGraph("phi03", 1);
    }

    private void testGraph(String snippet, Object... args) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        HighTierContext context = getDefaultHighTierContext();
        CanonicalizerPhase c = createCanonicalizerPhase();
        c.apply(graph, context);
        new PullThroughPhiPhase(c).apply(graph, context);
        c.apply(graph, context);
        GraphOrder.assertNonCyclicGraph(graph);
        GraphOrder.assertSchedulableGraph(graph);
        executeActual(getResolvedJavaMethod(snippet), null, args);
    }

    public static double regressionGR44224Snippet(long[] array, boolean condition) {
        double base;
        double exponent;
        if (GraalDirectives.injectBranchProbability(0.5, condition)) {
            GraalDirectives.sideEffect();
            long l = array[0];
            int i = (int) l;
            base = i;
            exponent = l;
        } else {
            GraalDirectives.sideEffect();
            long l = array[0];
            int i = (int) l;
            base = i;
            exponent = 1.0;
        }
        GraalDirectives.controlFlowAnchor();
        /*
         * GR-44224 regression test: We pull the pow through the phis and fold one of the versions
         * to Math.pow(base, 1.0) = base. This used to create nested phis at the merge, which
         * GraphOrder cannot deal with.
         */
        return Math.pow(base, exponent);
    }

    @Test
    public void regressionGR44224() {
        testGraph("regressionGR44224Snippet", new long[]{22}, false);
    }
}
