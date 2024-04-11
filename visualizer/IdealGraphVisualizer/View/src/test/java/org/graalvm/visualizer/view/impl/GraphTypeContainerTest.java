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

package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.view.ViewTestUtil;
import org.graalvm.visualizer.view.api.TimelineModel;
import org.netbeans.junit.NbTestCase;

import java.util.function.Predicate;

/**
 * @author odouda
 */
public class GraphTypeContainerTest extends NbTestCase {
    private final Group topGroup;

    private static Predicate<InputGraph> matcher
            = (graph) -> true;
    private static Predicate<InputGraph> matcher2
            = (graph) -> graph.getName().contains("init");

    public GraphTypeContainerTest(String name) throws Exception {
        super(name);
        topGroup = (Group) ((Group) ViewTestUtil.loadMegaData().getElements().get(0)).getElements().get(0);
    }

    public void testIdentity() {
        GraphViewerImplementation gvi = new GraphViewerImplementation();
        InputGraph g1 = topGroup.getGraphs().get(0);
        TimelineModel timeline = GraphViewerImplementation.createTimeline(g1);
        String gType = g1.getGraphType();

        GraphContainer gc1 = timeline.getPartition(gType);
        GraphContainer gc2 = timeline.getPrimaryPartition();
        assertSame(gc1, gc2);
        assertSame(gc1.getContentOwner(), gc2.getContentOwner());

        Group group2 = (Group) topGroup.getElements().get(0);
        TimelineModel timeline2 = GraphViewerImplementation.createTimeline(group2.getGraphs().get(0));

        GraphContainer gc3 = timeline2.getPartition(gType);
        assertNotSame(gc1, gc3);
        assertNotSame(gc1.getContentOwner(), gc3.getContentOwner());

        GraphContainer gc4 = timeline.getPartition("myType");
        assertNotSame(gc1, gc4);
    }

    public void testContainerData() {
        Group g = topGroup;
        InputGraph g1 = g.getGraphs().get(0);
        String gType = g1.getGraphType();
        GraphTypeContainer gc1 = new GraphTypeContainer(g, gType, matcher);

        assertSame(g, gc1.getContentOwner());
        assertEquals(g.getGraphs(), gc1.getGraphs());
        assertEquals(g.getGraphsCount(), gc1.getGraphsCount());
        assertEquals(g.getChildNodeIds(), gc1.getChildNodeIds());
        assertEquals(g.getChildNodes(), gc1.getChildNodes());
        assertEquals(g.getLastGraph(), gc1.getLastGraph());
        assertEquals(g.getName(), gc1.getName());
        assertEquals(gType, gc1.getType());
    }

    public void testMatching() {
        Group g = topGroup;
        InputGraph g1 = g.getGraphs().get(0);
        String gType = g1.getGraphType();
        GraphTypeContainer gc1 = new GraphTypeContainer(g, gType, matcher2);

        assertFalse(gc1.getGraphs().isEmpty());
        assertFalse(g.getGraphsCount() == gc1.getGraphsCount());
        assertTrue(gc1.getGraphs().stream().allMatch(graph -> graph.getName().contains("init")));
    }
}
