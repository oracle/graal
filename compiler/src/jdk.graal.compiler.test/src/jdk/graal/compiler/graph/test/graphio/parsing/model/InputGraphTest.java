/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graph.test.graphio.parsing.model;

import static jdk.graal.compiler.graph.test.graphio.parsing.model.GraphioTestUtil.assertInputGraphEquals;
import static jdk.graal.compiler.graph.test.graphio.parsing.model.GraphioTestUtil.assertNot;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputEdge;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

public class InputGraphTest {

    /*
     * @formatter:off
     *   1
     *  / \
     * 2   3
     * \  |  5
     * \ | /
     * \|/
     *  4
     *  @formatter:on
     */
    private static InputGraph referenceGraph;

    private static InputGraph emptyGraph;

    private static final InputNode N1 = new InputNode(1);
    private static final InputNode N2 = new InputNode(2);
    private static final InputNode N3 = new InputNode(3);
    private static final InputNode N4 = new InputNode(4);
    private static final InputNode N5 = new InputNode(5);
    private static final InputEdge E12 = new InputEdge((char) 0, 1, 2);
    private static final InputEdge E13 = new InputEdge((char) 0, 1, 3);
    private static final InputEdge E24 = new InputEdge((char) 0, 2, 4);
    private static final InputEdge E34 = new InputEdge((char) 0, 3, 4);
    private static final InputEdge E54 = new InputEdge((char) 0, 5, 4);

    @BeforeClass
    public static void setUpClass() throws Exception {
        Group group = new Group(null);

        emptyGraph = InputGraph.createTestGraph("emptyGraph");
        group.addElement(emptyGraph);

        referenceGraph = InputGraph.createTestGraph("referenceGraph");
        referenceGraph.addNode(N1);
        referenceGraph.addNode(N2);
        referenceGraph.addNode(N3);
        referenceGraph.addNode(N4);
        referenceGraph.addNode(N5);

        referenceGraph.addEdge(E12);
        referenceGraph.addEdge(E13);
        referenceGraph.addEdge(E24);
        referenceGraph.addEdge(E34);
        referenceGraph.addEdge(E54);

        group.addElement(referenceGraph);
    }

    private static final String equalGraphs = "Graphs are equal!";

    /**
     * Test of equals method, of class InputGraph.
     */
    @Test
    public void testEquals() {
        Group parentA = new Group(null);
        InputGraph a = InputGraph.createTestGraph("graph");
        parentA.addElement(a);

        Group parentB = new Group(null);
        InputGraph b = InputGraph.createTestGraph("graph");
        parentB.addElement(b);

        assertInputGraphEquals(a, b);

        a = InputGraph.createTestGraph("graph");
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, GraphioTestUtil::assertInputGraphEquals, equalGraphs);

        b = InputGraph.createTestGraph("graph");
        b.addNode(new InputNode(1));
        parentB.addElement(b);
        assertInputGraphEquals(a, b);

        parentA.removeAll();
        parentB.removeAll();

        a = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        parentA.addElement(a);
        assertNot(a, b, GraphioTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        parentB.addElement(b);
        assertInputGraphEquals(a, b);

        a = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, GraphioTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        b.addNode(new InputNode(1));
        parentB.addElement(b);
        assertInputGraphEquals(a, b);

        parentA.removeAll();
        parentB.removeAll();

        a = InputGraph.createTestGraph("1:graph my 3");
        parentA.addElement(a);

        assertEquals(a.getName(), b.getName());
        assertNot(a, b, GraphioTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph my 3", new Object[0]);
        parentB.addElement(b);

        assertInputGraphEquals(a, b);

        a = InputGraph.createTestGraph("1:graph my 3");
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, GraphioTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph my 3", new Object[0]);
        b.addNode(new InputNode(1));
        parentB.addElement(b);
        assertInputGraphEquals(a, b);
    }

    /**
     * Test of findRootNodes method, of class InputGraph.
     */
    @Test
    public void testFindRootNodes() {
        assertTrue(emptyGraph.findRootNodes().isEmpty());

        List<InputNode> result = referenceGraph.findRootNodes();
        assertEquals(2, result.size());
        assertTrue(result.contains(N1));
        assertTrue(result.contains(N5));
    }

    /**
     * Test of findAllOutgoingEdges method, of class InputGraph.
     */
    @Test
    public void testFindAllOutgoingEdges() {
        assertTrue(emptyGraph.findAllOutgoingEdges().isEmpty());

        Map<InputNode, List<InputEdge>> result = referenceGraph.findAllOutgoingEdges();
        assertEquals(5, result.size());
        assertEquals(result.get(N1), Arrays.asList(E12, E13));
        assertEquals(result.get(N2), List.of(E24));
        assertEquals(result.get(N3), List.of(E34));
        assertEquals(result.get(N4), List.of());
        assertEquals(result.get(N5), List.of(E54));
    }

    /**
     * Test of findAllIngoingEdges method, of class InputGraph.
     */
    @Test
    public void testFindAllIngoingEdges() {
        assertTrue(emptyGraph.findAllIngoingEdges().isEmpty());

        Map<InputNode, List<InputEdge>> result = referenceGraph.findAllIngoingEdges();
        assertEquals(5, result.size());
        assertEquals(result.get(N1), List.of());
        assertEquals(result.get(N2), List.of(E12));
        assertEquals(result.get(N3), List.of(E13));
        assertEquals(result.get(N4), Arrays.asList(E24, E34, E54));
        assertEquals(result.get(N5), List.of());
    }

    /**
     * Test of findOutgoingEdges method, of class InputGraph.
     */
    @Test
    public void testFindOutgoingEdges() {
        assertTrue(emptyGraph.findOutgoingEdges(new InputNode(1)).isEmpty());

        assertEquals(referenceGraph.findOutgoingEdges(N1), Arrays.asList(E12, E13));
        assertEquals(referenceGraph.findOutgoingEdges(N2), List.of(E24));
        assertEquals(referenceGraph.findOutgoingEdges(N3), List.of(E34));
        assertEquals(referenceGraph.findOutgoingEdges(N4), List.of());
        assertEquals(referenceGraph.findOutgoingEdges(N5), List.of(E54));
    }

    /**
     * Test of getNext method, of class InputGraph.
     */
    @Test
    public void testGetNextPrev() {
        final Group group = new Group(null);
        final InputGraph a = InputGraph.createTestGraph("a");
        final InputGraph b = InputGraph.createTestGraph("b");
        final InputGraph c = InputGraph.createTestGraph("c");
        group.addElement(a);
        group.addElement(b);
        group.addElement(c);

        assertNull(a.getPrev());
        assertEquals(b, a.getNext());

        assertEquals(a, b.getPrev());
        assertEquals(c, b.getNext());

        assertEquals(b, c.getPrev());
        assertNull(c.getNext());
    }

    /**
     * Specific edge order is important for BGV serialization, if the original source of graph is
     * XML. Some metadata is missing and must be computed when saving BGV and order of edges is
     * used.
     */
    @Test
    public void testEdgeOrdering() {
        List<InputEdge> edgeOrder = List.of(new InputEdge[]{
                        E12, E13, E24, E34, E54
        });
        assertEquals(edgeOrder, new ArrayList<>(referenceGraph.getEdges()));
    }
}
