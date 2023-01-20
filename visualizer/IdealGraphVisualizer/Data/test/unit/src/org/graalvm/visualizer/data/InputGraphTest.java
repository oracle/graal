/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data;

import java.util.ArrayList;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.InputGraph;
import static org.graalvm.visualizer.settings.TestUtils.*;
import static org.graalvm.visualizer.data.DataTestUtil.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.*;

public class InputGraphTest {

    /**
     *    1
     *   / \
     *  2   3
     *   \  |  5
     *    \ | /
     *     \|/
     *      4
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

    public InputGraphTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        Group group = new Group(null);

        emptyGraph = new InputGraph("emptyGraph");
        group.addElement(emptyGraph);

        referenceGraph = new InputGraph("referenceGraph");
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

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    private static final String equalGraphs = "Graphs are equal!";

    /**
     * Test of equals method, of class InputGraph.
     */
    @Test
    public void testEquals() {

        Group parentA = new Group(null);
        InputGraph a = new InputGraph("graph");
        parentA.addElement(a);

        Group parentB = new Group(null);
        InputGraph b = new InputGraph("graph");
        parentB.addElement(b);

        assertInputGraphEquals(a, b);

        a = new InputGraph("graph");
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, DataTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph("graph");
        b.addNode(new InputNode(1));
        parentB.addElement(b);
        assertInputGraphEquals(a, b);

        parentA.removeAll();
        parentB.removeAll();

        a = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        parentA.addElement(a);

        assertNot(a, b, DataTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        parentB.addElement(b);

        assertInputGraphEquals(a, b);

        a = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, DataTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph %s %h", new Object[]{"my", 3});
        b.addNode(new InputNode(1));
        parentB.addElement(b);
        assertInputGraphEquals(a, b);

        parentA.removeAll();
        parentB.removeAll();

        a = new InputGraph("1:graph my 3");
        parentA.addElement(a);

        assertEquals(a.getName(), b.getName());
        assertNot(a, b, DataTestUtil::assertInputGraphEquals, equalGraphs);

        b = new InputGraph(1, "graph my 3", new Object[0]);
        parentB.addElement(b);

        assertInputGraphEquals(a, b);

        a = new InputGraph("1:graph my 3");
        a.addNode(new InputNode(1));
        parentA.addElement(a);
        assertNot(a, b, DataTestUtil::assertInputGraphEquals, equalGraphs);

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
        assertTrue(result.size() == 2);
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
        assertTrue(result.size() == 5);
        assertEquals(result.get(N1), Arrays.asList(E12, E13));
        assertEquals(result.get(N2), Arrays.asList(E24));
        assertEquals(result.get(N3), Arrays.asList(E34));
        assertEquals(result.get(N4), Arrays.asList());
        assertEquals(result.get(N5), Arrays.asList(E54));
    }

    /**
     * Test of findAllIngoingEdges method, of class InputGraph.
     */
    @Test
    public void testFindAllIngoingEdges() {
        assertTrue(emptyGraph.findAllIngoingEdges().isEmpty());

        Map<InputNode, List<InputEdge>> result = referenceGraph.findAllIngoingEdges();
        assertTrue(result.size() == 5);
        assertEquals(result.get(N1), Arrays.asList());
        assertEquals(result.get(N2), Arrays.asList(E12));
        assertEquals(result.get(N3), Arrays.asList(E13));
        assertEquals(result.get(N4), Arrays.asList(E24, E34, E54));
        assertEquals(result.get(N5), Arrays.asList());
    }

    /**
     * Test of findOutgoingEdges method, of class InputGraph.
     */
    @Test
    public void testFindOutgoingEdges() {
        assertTrue(emptyGraph.findOutgoingEdges(new InputNode(1)).isEmpty());

        assertEquals(referenceGraph.findOutgoingEdges(N1), Arrays.asList(E12, E13));
        assertEquals(referenceGraph.findOutgoingEdges(N2), Arrays.asList(E24));
        assertEquals(referenceGraph.findOutgoingEdges(N3), Arrays.asList(E34));
        assertEquals(referenceGraph.findOutgoingEdges(N4), Arrays.asList());
        assertEquals(referenceGraph.findOutgoingEdges(N5), Arrays.asList(E54));
    }

    /**
     * Test of getNext method, of class InputGraph.
     */
    @Test
    public void testGetNextPrev() {
        final Group group = new Group(null);

        final InputGraph a = new InputGraph("a");

        final InputGraph b = new InputGraph("b");

        final InputGraph c = new InputGraph("c");
        group.addElement(a);
        group.addElement(b);
        group.addElement(c);

        assertEquals(null, a.getPrev());
        assertEquals(b, a.getNext());

        assertEquals(a, b.getPrev());
        assertEquals(c, b.getNext());

        assertEquals(b, c.getPrev());
        assertEquals(null, c.getNext());
    }

    /**
     * Specific edge order is important for BGV serialization, if the original
     * source of graph is XML. Some metadata is missing and must be computed when
     * saving BGV and order of edges is used.
     */
    @Test
    public void testEdgeOrdering() {
        List<InputEdge> edgeOrder = Arrays.asList(new InputEdge[]{
            E12, E13, E24, E34, E54
        });
        assertEquals(edgeOrder, new ArrayList<>(referenceGraph.getEdges()));

        // just for case, assert that without care, the edges WOULD be reodered.
        // add iteratively just like addEdge in the InputGraph
        Set s = new HashSet<>();
        for (InputEdge e : edgeOrder) {
            s.add(e);
        }
        assertTrue(!edgeOrder.equals(s));
    }
}
