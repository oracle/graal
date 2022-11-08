/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.stream.StreamSupport;

import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class NodeMapTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        final int i;

        protected TestNode(int i) {
            super(TYPE);
            this.i = i;
        }
    }

    private static String str(TestNode node) {
        return node.toString() + "#" + node.i;
    }

    private Graph graph;
    private TestNode[] nodes = new TestNode[100];
    private NodeMap<Integer> map;

    @Before
    public void before() {
        // Need to initialize HotSpotGraalRuntime before any Node class is initialized.
        Graal.getRuntime();

        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = graph.add(new TestNode(i));
        }
        map = new NodeMap<>(graph);
        for (int i = 0; i < nodes.length; i++) {
            TestNode key = nodes[i];
            if (i % 2 == 0) {
                map.put(key, i);
            } else if (i % 10 == 9) {
                map.put(key, null);
            } else {
            }
        }
    }

    @Test
    public void testEmpty() {
        NodeMap<Integer> emptyMap = new NodeMap<>(graph);
        for (TestNode node : nodes) {
            assertEquals(null, emptyMap.get(node));
        }
    }

    @Test
    public void testSimple() {
        assertEquals(graph, map.graph());
        for (int i = 0; i < nodes.length; i++) {
            TestNode key = nodes[i];

            if (i % 2 == 0) {
                assertTrue(str(key), map.containsKey(key));
                assertEquals(str(key), Integer.valueOf(i), map.get(key));
            } else if (i % 10 == 9) {
                assertTrue(str(key), map.containsKey(key));
                assertEquals(str(key), null, map.get(key));
            } else {
                assertFalse(str(key), map.containsKey(key));
                assertEquals(str(key), null, map.get(key));
            }
        }
    }

    @Test
    public void testSimpleChanged() {
        for (TestNode node : nodes) {
            map.set(node, 1);
        }
        for (TestNode node : nodes) {
            map.set(node, 32);
        }
        for (int i = 0; i < nodes.length; i += 2) {
            map.set(nodes[i], i);
        }

        for (int i = 0; i < nodes.length; i++) {
            if ((i & 1) == 0) {
                assertEquals((Integer) i, map.get(nodes[i]));
            } else {
                assertEquals(Integer.valueOf(32), map.get(nodes[i]));
            }
        }
    }

    @Test
    public void testGetNull() {
        try {
            map.get(null);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    @Test
    public void testNewGet() {
        TestNode newNode = graph.add(new TestNode(43));
        try {
            map.get(newNode);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // pass
        }
    }

    @Test
    public void testNewSet() {
        TestNode newNode = graph.add(new TestNode(53));
        try {
            map.set(newNode, 1);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // pass
        }
    }

    @Test
    public void testNewGetAndGrow() {
        TestNode newNode = graph.add(new TestNode(63));
        assertEquals(null, map.getAndGrow(newNode));
    }

    @Test
    public void testNewSetAndGrow() {
        TestNode newNode = graph.add(new TestNode(73));
        map.setAndGrow(newNode, 1);
        assertEquals((Integer) 1, map.get(newNode));
    }

    @Test
    public void testCopyConstructor() {
        NodeMap<Integer> copy = new NodeMap<>(map);
        assertEquals(map.toString(), copy.toString());
    }

    @Test
    public void testIsEmpty() {
        try {
            map.isEmpty();
            fail("expected exception");
        } catch (Exception e) {
            // pass
        }
    }

    @Test
    public void testSize() {
        try {
            map.size();
            fail("expected exception");
        } catch (Exception e) {
            // pass
        }
    }

    @Test
    public void testRemoveKey() {
        List<Node> keys = StreamSupport.stream(map.getKeys().spliterator(), false).toList();
        for (Node key : keys) {
            assertTrue(map.containsKey(key));
            TestNode node = (TestNode) key;
            Integer expect = node.i % 2 == 0 ? Integer.valueOf(node.i) : null;
            Integer removed = map.removeKey(key);
            assertEquals(str(node), expect, removed);
            assertFalse(str(node), map.containsKey(key));
        }
    }

    @Test
    public void testReplaceAll() {
        List<Node> keys = StreamSupport.stream(map.getKeys().spliterator(), false).toList();
        map.replaceAll((key, value) -> value == null ? 42 : -value);
        for (Node key : keys) {
            TestNode node = (TestNode) key;
            int i = node.i;
            if (i % 2 == 0) {
                assertEquals(str(node), Integer.valueOf(-i), map.get(key));
            } else if (i % 10 == 9) {
                assertEquals(str(node), Integer.valueOf(42), map.get(key));
            }
        }
    }

    @Test
    public void testContainsKey() {
        for (Node node : map.getKeys()) {
            Assert.assertTrue(String.valueOf(node), map.containsKey(node));
        }

        // Test key from another graph
        OptionValues options = getOptions();
        Graph otherGraph = new Graph(options, getDebug(options));

        TestNode key = otherGraph.add(new TestNode(76));
        try {
            map.containsKey(key);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // pass
        }

        try {
            map.get(key);
            fail("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // pass
        }
    }

    @Test
    public void testClear() {
        MapCursor<Node, Integer> entries = map.getEntries();
        assertTrue(String.valueOf(map), entries.advance());

        map.clear();
        entries = map.getEntries();
        assertFalse(String.valueOf(map), entries.advance());
    }

    @Test
    public void testIteration() {
        String s = map.toString();
        Assert.assertTrue(s, s.startsWith("{") && s.endsWith("}"));

        for (int i = 0; i < nodes.length; i++) {
            TestNode key = nodes[i];
            if (i % 2 == 0) {
                assertTrue(str(key), map.containsKey(key));
                assertEquals(str(key), Integer.valueOf(i), map.get(key));
            } else if (i % 10 == 9) {
                assertTrue(str(key), map.containsKey(key));
                assertEquals(str(key), null, map.get(key));
            } else {
                assertFalse(str(key), map.containsKey(key));
                assertEquals(str(key), null, map.get(key));
            }
        }

        MapCursor<Node, Integer> entries = map.getEntries();
        try {
            entries.getKey();
            Assert.fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // pass
        }
        try {
            entries.getValue();
            Assert.fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException e) {
            // pass
        }

        for (int i = 0; i < nodes.length; i++) {
            TestNode key = nodes[i];
            Integer value = Integer.valueOf(i);

            if (i % 2 == 0) {
                entries.advance();
                checkAndRemoveEntry(entries, key, value);
            } else if (i % 10 == 9) {
                entries.advance();
                value = null;
                checkAndRemoveEntry(entries, key, value);
            } else {
                assertFalse(map.containsKey(key));
            }
        }

        entries = map.getEntries();
        assertFalse(String.valueOf(map), entries.advance());
    }

    private void checkAndRemoveEntry(MapCursor<Node, Integer> cursor, TestNode key, Integer value) {
        assertEquals(key, cursor.getKey());
        assertEquals(value, cursor.getValue());

        cursor.remove();
        assertNull(String.valueOf(cursor.getValue()), cursor.getValue());
        assertEquals(key, cursor.getKey());
        assertFalse(str(key), map.containsKey(key));

        try {
            // Cannot remove twice without advancing
            cursor.remove();
            Assert.fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            // pass
        }

        cursor.setValue(value);
        assertTrue(str(key), map.containsKey(key));
        cursor.remove();
        assertFalse(str(key), map.containsKey(key));
    }

    @Test
    public void testToString() {
        String s = map.toString();
        Assert.assertTrue(s, s.startsWith("{") && s.endsWith("}"));
    }
}
