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

/// Checks that NodeBitMap.count() matches the nodes exposed by its NodeIterable iterator.
public class NodeBitMapCountContractTest extends GraphTest {
    @NodeInfo
    static final class TestNode extends Node {
        static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        protected TestNode() {
            super(TYPE);
        }
    }

    private Graph graph;
    private NodeBitMap map;
    private TestNode node;

    @Before
    public void createBitmap() {
        Graal.getRuntime();
        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        node = graph.add(new TestNode());
        map = graph.createNodeBitMap();
        map.mark(node);
    }

    @Test
    public void countMatchesIterationForLiveMarkedNode() {
        Assert.assertEquals(1, map.count());
        int iterated = 0;
        for (Node ignored : map) {
            iterated++;
        }
        Assert.assertEquals(iterated, map.count());
    }

    @Test
    public void countMatchesIterationAfterMarkedNodeIsDeleted() {
        node.safeDelete();
        int reportedCount = map.count();
        int iterated = 0;
        for (Node ignored : map) {
            iterated++;
        }
        Assert.assertEquals("NodeBitMap is a NodeIterable of live marked nodes", iterated, reportedCount);
    }

    @Test
    public void countIncludesLiveMarksAmongDeletedNodes() {
        TestNode deleted = graph.add(new TestNode());
        TestNode live = graph.add(new TestNode());
        map.markAndGrow(deleted);
        map.markAndGrow(live);
        node.safeDelete();
        deleted.safeDelete();
        Assert.assertEquals(1, map.count());
        Assert.assertEquals(List.of(live), map.snapshot());
    }

    @Test
    public void countMatchesIterationAcrossWords() {
        OptionValues options = getOptions();
        for (int nodeCount : new int[]{0, 1, 63, 64, 65, 129}) {
            Graph testGraph = new Graph(options, getDebug(options));
            TestNode[] nodes = new TestNode[nodeCount];
            for (int i = 0; i < nodeCount; i++) {
                nodes[i] = testGraph.add(new TestNode());
            }
            NodeBitMap bitmap = testGraph.createNodeBitMap();
            bitmap.markAll(testGraph.getNodes());
            Assert.assertEquals(nodeCount, bitmap.count());

            // Delete marks across word boundaries, including the sign bit of a word.
            for (int i = 0; i < nodeCount; i += 3) {
                nodes[i].safeDelete();
            }
            int expectedCount = nodeCount - (nodeCount + 2) / 3;
            Assert.assertEquals(expectedCount, bitmap.count());
            Assert.assertEquals(testGraph.getNodes().snapshot(), bitmap.snapshot());
        }
    }

    @Test
    public void countIncludesLiveMarksAfterUnmarkedNodeIsDeleted() {
        TestNode unmarked = graph.add(new TestNode());
        unmarked.safeDelete();
        Assert.assertEquals(1, map.count());
    }

    @Test
    public void countIncludesLiveMarksAfterLastDeletedNode() {
        for (int i = 1; i < 129; i++) {
            map.markAndGrow(graph.add(new TestNode()));
        }
        node.safeDelete();
        Assert.assertEquals(128, map.count());
        Assert.assertEquals(graph.getNodes().snapshot(), map.snapshot());
    }

}
