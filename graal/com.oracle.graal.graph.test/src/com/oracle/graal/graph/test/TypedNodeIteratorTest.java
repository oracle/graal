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
package com.oracle.graal.graph.test;

import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;

public class TypedNodeIteratorTest {

    @NodeInfo
    static class TestNode extends Node implements IterableNodeType, TestNodeInterface {

        protected String name;

        public static TestNode create(String name) {
            return USE_GENERATED_NODES ? new TypedNodeIteratorTest_TestNodeGen(name) : new TestNode(name);
        }

        protected TestNode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Test
    public void singleNodeTest() {
        Graph graph = new Graph();
        graph.add(TestNode.create("a"));
        assertTrue(graph.hasNode(TestNode.class));
        assertEquals("a", toString(graph.getNodes(TestNode.class)));
    }

    @Test
    public void deletingNodeTest() {
        TestNode testNode = TestNode.create("a");
        Graph graph = new Graph();
        graph.add(testNode);
        testNode.safeDelete();
        assertEquals("", toString(graph.getNodes(TestNode.class)));
    }

    @Test
    public void deleteAndAddTest() {
        TestNode testNode = TestNode.create("b");
        Graph graph = new Graph();
        graph.add(TestNode.create("a"));
        graph.add(testNode);
        testNode.safeDelete();
        assertEquals("a", toString(graph.getNodes(TestNode.class)));
        graph.add(TestNode.create("c"));
        assertEquals("ac", toString(graph.getNodes(TestNode.class)));
    }

    @Test
    public void iteratorBehaviorTest() {
        Graph graph = new Graph();
        graph.add(TestNode.create("a"));
        Iterator<TestNode> iterator = graph.getNodes(TestNode.class).iterator();
        assertTrue(iterator.hasNext());
        assertEquals("a", iterator.next().getName());
        assertFalse(iterator.hasNext());
        graph.add(TestNode.create("b"));
        assertTrue(iterator.hasNext());
        assertEquals("b", iterator.next().getName());
        assertFalse(iterator.hasNext());
        TestNode c = TestNode.create("c");
        graph.add(c);
        assertTrue(iterator.hasNext());
        c.safeDelete();
        assertFalse(iterator.hasNext());
    }

    @Test
    public void complicatedIterationTest() {
        Graph graph = new Graph();
        graph.add(TestNode.create("a"));
        for (TestNode tn : graph.getNodes(TestNode.class)) {
            String name = tn.getName();
            for (int i = 0; i < name.length(); ++i) {
                char c = name.charAt(i);
                if (c == 'a') {
                    tn.safeDelete();
                    graph.add(TestNode.create("b"));
                    graph.add(TestNode.create("c"));
                } else if (c == 'b') {
                    tn.safeDelete();
                } else if (c == 'c') {
                    graph.add(TestNode.create("d"));
                    graph.add(TestNode.create("e"));
                    graph.add(TestNode.create("d"));
                    graph.add(TestNode.create("e"));
                    graph.add(TestNode.create("e"));
                    graph.add(TestNode.create("d"));
                    graph.add(TestNode.create("e"));
                    graph.add(TestNode.create("d"));
                } else if (c == 'd') {
                    for (TestNode tn2 : graph.getNodes(TestNode.class)) {
                        if (tn2.getName().equals("e")) {
                            tn2.safeDelete();
                        } else if (tn2.getName().equals("c")) {
                            tn2.safeDelete();
                        }
                    }
                } else if (c == 'e') {
                    fail("All e nodes must have been deleted by visiting the d node");
                }
            }
        }
        assertEquals("dddd", toString(graph.getNodes(TestNode.class)));
    }

    @Test
    public void addingNodeDuringIterationTest() {
        Graph graph = new Graph();
        graph.add(TestNode.create("a"));
        StringBuilder sb = new StringBuilder();
        int z = 0;
        for (TestNode tn : graph.getNodes(TestNode.class)) {
            if (z == 0) {
                graph.add(TestNode.create("b"));
            }
            sb.append(tn.getName());
            z++;
        }
        assertEquals(2, z);
        assertEquals("ab", sb.toString());
        z = 0;
        for (TestNode tn : graph.getNodes(TestNode.class)) {
            if (z == 0) {
                graph.add(TestNode.create("c"));
            }
            assertNotNull(tn);
            z++;
        }
        assertEquals(3, z);
    }

    public static String toString(Iterable<? extends TestNodeInterface> nodes) {
        StringBuilder sb = new StringBuilder();
        for (TestNodeInterface tn : nodes) {
            sb.append(tn.getName());
        }
        return sb.toString();
    }
}
