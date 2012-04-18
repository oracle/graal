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
package com.oracle.graal.compiler.tests;

import java.io.*;

import junit.framework.Assert;

import org.junit.*;

import com.oracle.graal.compiler.phases.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.compiler.types.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.printer.*;

/**
 * In the following tests, the scalar type system of the compiler should be complete enough to see the relation between the different conditions.
 */
public class TypeSystemTest extends GraphTest {

    @Test
    public void test1() {
        test("test1Snippet", CheckCastNode.class);
    }

    public static int test1Snippet(Object a) {
        if (a instanceof Boolean) {
            return ((Boolean) a).booleanValue() ? 0 : 1;
        }
        return 1;
    }

    @Test
    public void test2() {
        test("test2Snippet", CheckCastNode.class);
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

    public static final Object constantObject1 = "1";
    public static final Object constantObject2 = "2";
    public static final Object constantObject3 = "3";

    public static int test4Snippet(Object o) {
        if (o == null) {
            if (o == constantObject1) {
                return 3;
            } else {
                return 1;
            }
        } else {
            return 2;
        }
    }

//    @Test
    public void test5() {
        test("test5Snippet", "referenceSnippet5");
    }

    public static int referenceSnippet5(Object o, Object a) {
        if (o == null) {
            if (a == constantObject1 || a == constantObject2) {
                return 1;
            }
        } else {
            if (a == constantObject2 || a == constantObject3) {
                if (a != null) {
                    return 11;
                }
                return 2;
            }
        }
        if (a == constantObject1) {
            return 3;
        }
        return 5;
    }

    public static int test5Snippet(Object o, Object a) {
        if (o == null) {
            if (a == constantObject1 || a == constantObject2) {
                if (a == null) {
                    return 10;
                }
                return 1;
            }
        } else {
            if (a == constantObject2 || a == constantObject3) {
                if (a != null) {
                    return 11;
                }
                return 2;
            }
        }
        if (a == constantObject1) {
            return 3;
        }
        if (a == constantObject2) {
            return 4;
        }
        return 5;
    }

    @Test
    public void test6() {
        test("test6Snippet", CheckCastNode.class);
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
        System.out.println("==================== " + snippet);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new PropagateTypeCachePhase(null, runtime(), null).apply(graph);
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new GlobalValueNumberingPhase().apply(graph);
        StructuredGraph referenceGraph = parse(referenceSnippet);
        new CanonicalizerPhase(null, runtime(), null).apply(referenceGraph);
        new GlobalValueNumberingPhase().apply(referenceGraph);
        assertEquals(referenceGraph, graph);
    }

    @Override
    protected void assertEquals(StructuredGraph expected, StructuredGraph graph) {
        if (expected.getNodeCount() != graph.getNodeCount()) {
            Debug.dump(expected, "Node count not matching - expected");
            Debug.dump(graph, "Node count not matching - actual");
            System.out.println("================ expected");
            outputGraph(expected);
            System.out.println("================ actual");
            outputGraph(graph);
            new IdealGraphPrinterDumpHandler().dump(graph, "asdf");
            Assert.fail("Graphs do not have the same number of nodes: " + expected.getNodeCount() + " vs. " + graph.getNodeCount());
        }
    }

    public static void outputGraph(StructuredGraph graph) {
        SchedulePhase schedule = new SchedulePhase();
        schedule.apply(graph);
        for (Block block : schedule.getCFG().getBlocks()) {
            System.out.print("Block " + block + " ");
            if (block == schedule.getCFG().getStartBlock()) {
                System.out.print("* ");
            }
            System.out.print("-> ");
            for (Block succ : block.getSuccessors()) {
                System.out.print(succ + " ");
            }
            System.out.println();
            for (Node node : schedule.getBlockToNodesMap().get(block)) {
                System.out.println("  " + node + "    (" + node.usages().size() + ")");
            }
        }
    }


    private <T extends Node & Node.IterableNodeType> void test(String snippet, Class<T> clazz) {
        StructuredGraph graph = parse(snippet);
        Debug.dump(graph, "Graph");
        new CanonicalizerPhase(null, runtime(), null).apply(graph);
        new PropagateTypeCachePhase(null, runtime(), null).apply(graph);
        Debug.dump(graph, "Graph");
        if (graph.getNodes(clazz).iterator().hasNext()) {
            outputGraph(graph);
        }
        Assert.assertFalse("shouldn't have nodes of type " + clazz, graph.getNodes(clazz).iterator().hasNext());
    }
}
