/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

/**
 * In the following tests, the usages of local variable "a" are replaced with the integer constant
 * 0. Then canonicalization is applied and it is verified that the resulting graph is equal to the
 * graph of the method that just has a "return 1" statement in it.
 */
public class IfCanonicalizerTest extends GraalCompilerTest {

    private static final String REFERENCE_SNIPPET = "referenceSnippet";

    @SuppressWarnings("all")
    public static int referenceSnippet(int a) {
        return 1;
    }

    @Test
    public void test1() {
        test("test1Snippet");
    }

    @SuppressWarnings("all")
    public static int test1Snippet(int a) {
        if (a == 0) {
            return 1;
        } else {
            return 2;
        }
    }

    @Test
    public void test2() {
        test("test2Snippet");
    }

    @SuppressWarnings("all")
    public static int test2Snippet(int a) {
        if (a == 0) {
            if (a == 0) {
                if (a == 0) {
                    return 1;
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test3() {
        test("test3Snippet");
    }

    @SuppressWarnings("all")
    public static int test3Snippet(int a) {
        if (a == 0) {
            if (a != 1) {
                if (a == 1) {
                    return 3;
                } else {
                    if (a >= 0) {
                        if (a <= 0) {
                            if (a > -1) {
                                if (a < 1) {
                                    return 1;
                                }
                            }
                        }
                    }
                }
            }
        } else {
            return 2;
        }
        return 3;
    }

    @Test
    public void test4() {
        test("test4Snippet");
    }

    public static int test4Snippet(int a) {
        if (a == 0) {
            return 1;
        }
        return 1;
    }

    @Test
    public void test5() {
        test("test5Snippet");
    }

    public static int test5Snippet(int a) {
        int val = 2;
        if (a == 0) {
            val = 1;
        }
        if (a * (3 + val) == 0) {
            return 1;
        }
        return 1;
    }

    @Test
    public void test6() {
        testCombinedIf("test6Snippet", 3);
        test("test6Snippet", new int[]{0});
    }

    public static int test6Snippet(int[] a) {
        int i = a[0];
        if (i >= 0 && i < a.length) {
            return a[i];
        }
        return 1;
    }

    @Test
    public void test7() {
        testCombinedIf("test7Snippet", 1);
        test("test7Snippet", -1);
    }

    public static int test7Snippet(int v) {
        if (v >= 0 && v < 1024) {
            return v + 1;
        }
        return v - 1;
    }

    @Test
    public void test8() {
        testCombinedIf("test8Snippet", 1);
        test("test8Snippet", -1);
    }

    public static int test8Snippet(int v) {
        if (v >= 0 && v <= 1024) {
            return v + 1;
        }
        return v - 1;
    }

    private void testCombinedIf(String snippet, int count) {
        StructuredGraph graph = parse(snippet);
        PhaseContext context = new PhaseContext(getProviders(), new Assumptions(false));
        new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        new FloatingReadPhase().apply(graph);
        MidTierContext midContext = new MidTierContext(getProviders(), new Assumptions(false), getCodeCache().getTarget(), OptimisticOptimizations.ALL, graph.method().getProfilingInfo(), null);
        new GuardLoweringPhase().apply(graph, midContext);
        new LoweringPhase(new CanonicalizerPhase(true), LoweringTool.StandardLoweringStage.MID_TIER).apply(graph, midContext);
        new ValueAnchorCleanupPhase().apply(graph);
        new CanonicalizerPhase(true).apply(graph, context);
        assertDeepEquals(count, graph.getNodes().filter(IfNode.class).count());
    }

    private void test(String snippet) {
        StructuredGraph graph = parse(snippet);
        ParameterNode param = graph.getNodes(ParameterNode.class).iterator().next();
        ConstantNode constant = ConstantNode.forInt(0, graph);
        for (Node n : param.usages().filter(isNotA(FrameState.class)).snapshot()) {
            n.replaceFirstInput(param, constant);
        }
        Debug.dump(graph, "Graph");
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), new Assumptions(false)));
        for (FrameState fs : param.usages().filter(FrameState.class).snapshot()) {
            fs.replaceFirstInput(param, null);
        }
        StructuredGraph referenceGraph = parse(REFERENCE_SNIPPET);
        assertEquals(referenceGraph, graph);
    }
}
