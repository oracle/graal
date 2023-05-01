/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.util.GraphOrder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class LoopPhiCanonicalizerTest extends GraalCompilerTest {

    private static int[] array = new int[1000];

    @BeforeClass
    public static void before() {
        for (int i = 0; i < array.length; i++) {
            array[i] = i;
        }
    }

    public static long loopSnippet() {
        int a = 0;
        int b = 0;
        int c = 0;
        int d = 0;

        long sum = 0;
        while (d < 1000) {
            sum += array[a++] + array[b++] + array[c++] + array[d++];
        }
        return sum;
    }

    @Test
    public void test() {
        StructuredGraph graph = parseEager("loopSnippet", AllowAssumptions.YES);
        NodePredicate loopPhis = node -> node instanceof PhiNode && ((PhiNode) node).merge() instanceof LoopBeginNode;

        CoreProviders context = getProviders();
        Assert.assertEquals(5, graph.getNodes().filter(loopPhis).count());
        createCanonicalizerPhase().apply(graph, context);
        Assert.assertEquals(2, graph.getNodes().filter(loopPhis).count());

        test("loopSnippet");
    }

    public static int loopSnippet01() {
        int i = 0;
        int emptyPhi1 = 100;
        int emptyPhi2 = 32750;
        while (true) {
            if (i >= 10000) {
                break;
            }
            emptyPhi1 = (byte) (emptyPhi1 + 1);
            emptyPhi2 = (short) (emptyPhi2 + 1);
            if (foo(1) != 1) {
                GraalDirectives.sideEffect(emptyPhi1);
                GraalDirectives.sideEffect(emptyPhi2);
            }
            i++;
        }
        return i;
    }

    public static int foo(int i) {
        return i;
    }

    @Test
    public void test01() {
        testSchedulable("loopSnippet01", 1);
    }

    static int S;

    public static int loopSnippet02() {
        int phi1;
        if (S == 123) {
            GraalDirectives.sideEffect(1);
            phi1 = 12345;
        } else {
            GraalDirectives.sideEffect(123);
            phi1 = 1222;
        }
        int tmp1 = (char) phi1;
        int tmp2 = tmp1 & 127;
        int i = 0;
        int emptyPhi1 = 100;
        while (true) {
            if (i >= 10000) {
                break;
            }
            emptyPhi1 = (byte) (emptyPhi1 + tmp2);
            if (foo(1) != 1) {
                GraalDirectives.sideEffect(emptyPhi1);
            }
            i++;
        }
        return i;
    }

    @Test
    public void test02() {
        testSchedulable("loopSnippet02", 1);
    }

    public static int loopSnippet03() {
        int phi1;
        if (S == 123) {
            GraalDirectives.sideEffect(1);
            phi1 = 12345;
        } else {
            GraalDirectives.sideEffect(123);
            phi1 = 1222;
        }
        int tmp1 = (char) phi1;
        int tmp2 = tmp1 & 127;
        int i = 0;
        int emptyPhi1 = 100;
        while (true) {
            if (i >= 10000) {
                break;
            }
            if (GraalDirectives.sideEffect(i) > 12) {
                GraalDirectives.sideEffect(i);
                emptyPhi1 = 13;
            } else {
                emptyPhi1 = (byte) (emptyPhi1 + tmp2);
            }
            i++;
        }
        return i;
    }

    @Test
    public void test03() {
        testSchedulable("loopSnippet03", 1);
    }

    public static int loopSnippet04() {
        int phi1;
        if (S == 123) {
            GraalDirectives.sideEffect(1);
            phi1 = 12345;
        } else {
            GraalDirectives.sideEffect(123);
            phi1 = 1222;
        }
        int tmp1 = (char) phi1;
        int tmp2 = tmp1 & 127;
        int i = 0;
        int emptyPhi1 = 100;
        while (true) {
            if (i >= 10000) {
                break;
            }
            if (GraalDirectives.sideEffect(i) > 12) {
                GraalDirectives.sideEffect(i);
                emptyPhi1 = 13;
            } else if (GraalDirectives.sideEffect(i) < 1235) {
                GraalDirectives.sideEffect(12);
                emptyPhi1 = (byte) (emptyPhi1 + tmp2);
            } else {
                GraalDirectives.sideEffect(1245);
                emptyPhi1 = (byte) (emptyPhi1 + 1);
            }
            i++;
        }
        return i;
    }

    @Test
    public void test04() {
        testSchedulable("loopSnippet04", 1);
    }

    public static int loopSnippet05() {
        int phi1;
        if (S == 123) {
            GraalDirectives.sideEffect(1);
            phi1 = 12345;
        } else {
            GraalDirectives.sideEffect(123);
            phi1 = 1222;
        }
        int tmp1 = (char) phi1;
        int tmp2 = tmp1 & 127;
        int i = 0;
        int emptyPhi1 = 100;
        int phi4 = 0;
        while (true) {
            if (i >= 10000) {
                break;
            }
            if (GraalDirectives.sideEffect(i) > 12) {
                GraalDirectives.sideEffect(i);
                emptyPhi1 = 13;
            } else {
                GraalDirectives.sideEffect(12);
                int tmp3 = (byte) (emptyPhi1 + tmp2);
                emptyPhi1 = tmp3;
                phi4 = tmp3; // usage of the intermediate cycle node
            }
            i++;
        }
        return i + phi4;

    }

    @Test
    public void test05() {
        testSchedulable("loopSnippet05", 3);
    }

    private void testSchedulable(String snippet, int loopPhisAfter) {
        StructuredGraph g = parseEager(getResolvedJavaMethod(snippet), AllowAssumptions.NO);
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        canonicalizer.apply(g, getDefaultHighTierContext());
        g.clearAllStateAfterForTestingOnly();
        canonicalizer.apply(g, getDefaultHighTierContext());
        canonicalizer.apply(g, getDefaultHighTierContext());
        /*
         * Now the only values holding the emptyPhis alive is a chain of indirect usages, i.e., a
         * cycle. The only part of the codebase that could cleanup this cycle is a proper latest
         * schedule that will not visit the phi and its usages and consider them dead and will
         * delete them. We have to run a canon before that detects these patterns, else verification
         * of the schedule would fail.
         */
        GraphOrder.assertSchedulableGraph(g);
        Assert.assertEquals(loopPhisAfter, g.getNodes().filter(PhiNode.class).filter(x -> ((PhiNode) x).isLoopPhi()).count());
    }
}
