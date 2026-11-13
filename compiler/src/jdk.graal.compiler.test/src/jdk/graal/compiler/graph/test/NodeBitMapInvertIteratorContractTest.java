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

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;

/// Checks that an inverted NodeBitMap remains iterable over its graph's live node IDs.
public class NodeBitMapInvertIteratorContractTest extends GraphTest {
    @NodeInfo
    static final class TestNode extends Node {
        static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        protected TestNode() {
            super(TYPE);
        }
    }

    private Graph graph;
    private NodeBitMap map;
    private TestNode onlyNode;

    @Before
    public void createBitmap() {
        Graal.getRuntime();
        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        onlyNode = graph.add(new TestNode());
        map = graph.createNodeBitMap();
    }

    @Test
    public void invertedBitmapCountExcludesPaddingBits() {
        map.invert();
        Assert.assertEquals(1, map.count());
    }

    @Test
    public void invertedBitmapIteratesOnlyLiveGraphNodes() {
        map.invert();
        Assert.assertTrue(map.isMarked(onlyNode));
        List<Node> iterated = new ArrayList<>();
        for (Node node : map) {
            iterated.add(node);
        }
        Assert.assertEquals(List.of(onlyNode), iterated);
    }

    @Test
    public void invertedBitmapAtWordBoundaries() {
        for (int nodeCount : new int[]{0, 63, 64, 65, 128}) {
            Graph boundaryGraph = new Graph(getOptions(), getDebug(getOptions()));
            for (int i = 0; i < nodeCount; i++) {
                boundaryGraph.add(new TestNode());
            }
            NodeBitMap bitmap = boundaryGraph.createNodeBitMap();
            bitmap.invert();
            Assert.assertEquals(nodeCount, bitmap.count());
            Assert.assertEquals(boundaryGraph.getNodes().snapshot(), bitmap.snapshot());
            bitmap.invert();
            Assert.assertEquals(0, bitmap.count());
        }
    }

    @Test
    public void invertedBitmapDoesNotMarkSpareCapacityOrFutureNodes() {
        // Repeated growth reserves more words than the current node range needs.
        while (graph.nodeIdCount() < 65) {
            graph.add(new TestNode());
        }
        map.grow();
        while (graph.nodeIdCount() < 129) {
            graph.add(new TestNode());
        }
        map.grow();
        map.invert();
        Assert.assertEquals(129, map.count());
        Assert.assertEquals(graph.getNodes().snapshot(), map.snapshot());

        TestNode newNode = graph.add(new TestNode());
        map.grow();
        Assert.assertFalse(map.isMarked(newNode));
        Assert.assertEquals(129, map.count());
    }

}
