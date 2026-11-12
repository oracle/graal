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
package jdk.graal.compiler.core.test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;

/// Checks exhaustion behavior of NodeMap key and value iterators.
public class NodeMapIteratorContractTest extends GraalCompilerTest {
    @Test
    public void emptyKeyIteratorThrowsNoSuchElementException() {
        StructuredGraph graph = newGraph();
        Iterator<Node> iterator = new NodeMap<>(graph).getKeys().iterator();
        Assert.assertFalse(iterator.hasNext());
        assertNextThrowsNoSuchElementException(iterator);
    }

    @Test
    public void keyIteratorNextDoesNotRequireHasNextPriming() {
        StructuredGraph graph = newGraph();
        NodeMap<String> map = new NodeMap<>(graph);
        Node node = graph.addWithoutUnique(ConstantNode.forInt(7));
        map.setAndGrow(node, "value");
        Assert.assertSame(node, map.getKeys().iterator().next());
    }

    @Test
    public void exhaustedKeyIteratorThrowsNoSuchElementException() {
        StructuredGraph graph = newGraph();
        NodeMap<String> map = new NodeMap<>(graph);
        Node node = graph.addWithoutUnique(ConstantNode.forInt(7));
        map.setAndGrow(node, "value");
        Iterator<Node> iterator = map.getKeys().iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertSame(node, iterator.next());
        Assert.assertFalse(iterator.hasNext());
        assertNextThrowsNoSuchElementException(iterator);
    }

    @Test
    public void emptyValueIteratorThrowsNoSuchElementException() {
        StructuredGraph graph = newGraph();
        Iterator<String> iterator = new NodeMap<String>(graph).getValues().iterator();
        Assert.assertFalse(iterator.hasNext());
        assertNextThrowsNoSuchElementException(iterator);
    }

    @Test
    public void valueIteratorNextDoesNotRequireHasNextPriming() {
        StructuredGraph graph = newGraph();
        NodeMap<String> map = new NodeMap<>(graph);
        Node node = graph.addWithoutUnique(ConstantNode.forInt(7));
        map.setAndGrow(node, "value");
        Assert.assertEquals("value", map.getValues().iterator().next());
    }

    @Test
    public void exhaustedValueIteratorThrowsNoSuchElementException() {
        StructuredGraph graph = newGraph();
        NodeMap<String> map = new NodeMap<>(graph);
        Node node = graph.addWithoutUnique(ConstantNode.forInt(7));
        map.setAndGrow(node, "value");
        Iterator<String> iterator = map.getValues().iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertEquals("value", iterator.next());
        Assert.assertFalse(iterator.hasNext());
        assertNextThrowsNoSuchElementException(iterator);
    }

    private StructuredGraph newGraph() {
        return new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
    }

    private static <T> void assertNextThrowsNoSuchElementException(Iterator<T> iterator) {
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }
}
