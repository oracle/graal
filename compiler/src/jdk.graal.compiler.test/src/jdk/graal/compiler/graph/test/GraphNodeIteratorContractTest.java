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
package jdk.graal.compiler.graph.test;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;

/// Checks the exhaustion behavior of Graph.getNodes() and Graph.getNewNodes() iterators.
public class GraphNodeIteratorContractTest extends GraphTest {
    @NodeInfo
    static final class TestNode extends Node {
        static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        protected TestNode() {
            super(TYPE);
        }
    }

    private Graph graph;
    private TestNode onlyNode;

    @Before
    public void createGraph() {
        Graal.getRuntime();
        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        onlyNode = graph.add(new TestNode());
    }

    @Test
    public void graphNodesIteratorThrowsNoSuchElementExceptionAfterExhaustion() {
        assertExhaustedIteratorThrows(graph.getNodes().iterator());
    }

    @Test
    public void newNodesIteratorThrowsNoSuchElementExceptionAfterExhaustion() {
        assertExhaustedIteratorThrows(graph.getNewNodes(null).iterator());
    }

    @Test
    public void iteratesTheLiveNodeBeforeExhaustion() {
        Iterator<Node> iterator = graph.getNodes().iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertSame(onlyNode, iterator.next());
        Assert.assertFalse(iterator.hasNext());
    }

    private static void assertExhaustedIteratorThrows(Iterator<Node> iterator) {
        while (iterator.hasNext()) {
            Assert.assertNotNull(iterator.next());
        }
        Assert.assertFalse(iterator.hasNext());
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }
}
