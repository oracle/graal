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

import java.io.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.schedule.*;
import com.oracle.graal.phases.tiers.*;

/**
 * In the following tests, the scalar type system of the compiler should be complete enough to see
 * the relation between the different conditions.
 */
public class TypeSystemTest extends GraalCompilerTest {

    @Test
    public void test1() {
        testHelper("test1Snippet", CheckCastNode.class);
    }

    public static int test1Snippet(Object a) {
        if (a instanceof Boolean) {
            return ((Boolean) a).booleanValue() ? 0 : 1;
        }
        return 1;
    }

    @Test
    public void test2() {
        testHelper("test2Snippet", CheckCastNode.class);
    }

    public static int test2Snippet(Object a) {
        if (a instanceof Integer) {
            return ((Number) a).intValue();
        }
        return 1;
    }

    @Test
    public void test3() {
        test("test3Snippet", "referenceSnippet3");
    }

    public static int referenceSnippet3(Object o) {
        if (o == null) {
            return 1;
        } else {
            return 2;
        }
    }

    @SuppressWarnings("unused")
    public static int test3Snippet(Object o) {
        if (o == null) {
            if (o != null) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

    @Test
    public void test4() {
        test("test4Snippet", "referenceSnippet3");
    }

    @SuppressWarnings("unused")
    public static int test4Snippet(Object o) {
        if (o == null) {
            Object o2 = Integer.class;
            if (o == o2) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

    @Test
    public void test5() {
        test("test5Snippet", "referenceSnippet5");
    }

    public static int referenceSnippet5(Object o, Object a) {
        if (o == null) {
            if (a == Integer.class || a == Double.class) {
                return 1;
            }
        } else {
            if (a == Double.class || a == Long.class) {
                return 11;
            }
        }
        if (a == Integer.class) {
            return 3;
        }
        return 5;
    }

    @SuppressWarnings("unused")
    public static int test5Snippet(Object o, Object a) {
        if (o == null) {
            if (a == Integer.class || a == Double.class) {
                if (a == null) {
                    return 10;
                }
                return 1;
            }
        } else {
            if (a == Double.class || a == Long.class) {
                if (a != null) {
                    return 11;
                }
                return 2;
            }
        }
        if (a == Integer.class) {
            return 3;
        }
        if (a == Double.class) {
            return 4;
        }
        return 5;
    }

    @Test
    public void test6() {
        testHelper("test6Snippet", CheckCastNode.class);
    }

    public static int test6Snippet(int i) throws IOException {
        Object o = null;

        if (i == 5) {
            o = new FileInputStream("asdf");
        }
        if (i < 10) {
            o = new ByteArrayInputStream(new byte[]{1, 2, 3});
        }
        if (i > 0) {
            o = new BufferedInputStream(null);
        }

        return ((InputStream) o).available();
    }

    private void test(String snippet, String referenceSnippet) {
        StructuredGraph graph = parse(snippet);
        Debug.dump(graph, "Graph");
        Assumptions assumptions = new Assumptions(false);
        new ConditionalEliminationPhase(getMetaAccess()).apply(graph);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        // a second canonicalizer is needed to process nested MaterializeNodes
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        StructuredGraph referenceGraph = parse(referenceSnippet);
        new CanonicalizerPhase(true).apply(referenceGraph, new PhaseContext(getProviders(), assumptions));
        assertEquals(referenceGraph, graph);
    }

    @Override
    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        if (getNodeCountExcludingUnusedConstants(expected) != getNodeCountExcludingUnusedConstants(graph)) {
            Debug.dump(expected, "expected (node count)");
            Debug.dump(graph, "graph (node count)");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount());
        }
    }

    public static void outputGraph(StructuredGraph graph, String message) {
        TTY.println("========================= " + message);
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);
        for (Block block : schedule.getCFG().getBlocks()) {
            TTY.print("Block " + block + " ");
            if (block == schedule.getCFG().getStartBlock()) {
                TTY.print("* ");
            }
            TTY.print("-> ");
            for (Block succ : block.getSuccessors()) {
                TTY.print(succ + " ");
            }
            TTY.println();
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                outputNode(node);
            }
        }
    }

    private static void outputNode(Node node) {
        TTY.print("  " + node + "    (usage count: " + (node.recordsUsages() ? node.usages().count() : "?") + ") (inputs:");
        for (Node input : node.inputs()) {
            TTY.print(" " + input.toString(Verbosity.Id));
        }
        TTY.println(")");
        if (node instanceof MergeNode) {
            for (PhiNode phi : ((MergeNode) node).phis()) {
                outputNode(phi);
            }
        }
    }

    private <T extends Node> void testHelper(String snippet, Class<T> clazz) {
        StructuredGraph graph = parse(snippet);
        Assumptions assumptions = new Assumptions(false);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        new ConditionalEliminationPhase(getMetaAccess()).apply(graph);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        Debug.dump(graph, "Graph " + snippet);
        Assert.assertFalse("shouldn't have nodes of type " + clazz, graph.getNodes().filter(clazz).iterator().hasNext());
    }
}
