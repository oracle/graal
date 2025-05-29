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

import static jdk.graal.compiler.graphio.parsing.model.GraphClassifier.DEFAULT_CLASSIFIER;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.visualizer.util.RangeSliderModel;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.GraphClassifier;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class TimelineModelImplTest {
    private static final String TYPE_INTERMEDIATE_GRAPH = GraphClassifier.STRUCTURED_GRAPH;
    private static final String TYPE_CALL_GRAPH = GraphClassifier.CALL_GRAPH;

    private List<InputGraph> graphs = new ArrayList<>();
    private final GraphDocument document = new GraphDocument();

    @Before
    public void createGraphs() {
        InputGraph g;

        g = InputGraph.createTestGraph("s1");
        g.getProperties().setProperty("graph", "StructuredGraph:1");
        graphs.add(g);
        g = InputGraph.createTestGraph("s2");
        g.getProperties().setProperty("graph", "StructuredGraph:2");
        graphs.add(g);
        g = InputGraph.createTestGraph("s3");
        g.getProperties().setProperty("graph", "StructuredGraph:3");
        graphs.add(g);
        g = InputGraph.createTestGraph("s4");
        g.getProperties().setProperty("graph", "StructuredGraph:4");
        graphs.add(g);
        g = InputGraph.createTestGraph("s5");
        g.getProperties().setProperty("graph", "StructuredGraph:5");
        graphs.add(g);
        g = InputGraph.createTestGraph("s6");
        g.getProperties().setProperty("graph", "StructuredGraph:6");
        graphs.add(g);

        g = InputGraph.createTestGraph("c11");
        g.getProperties().setProperty("graph", "inline:1-1");
        graphs.add(indexOf("StructuredGraph:2"), g);

        g = InputGraph.createTestGraph("c21");
        g.getProperties().setProperty("graph", "inline:2-1");
        graphs.add(indexOf("StructuredGraph:4"), g);
        g = InputGraph.createTestGraph("c22");
        g.getProperties().setProperty("graph", "inline:2-2");
        graphs.add(indexOf("StructuredGraph:4"), g);
        g = InputGraph.createTestGraph("c31");
        g.getProperties().setProperty("graph", "inline:3-1");
        graphs.add(indexOf("StructuredGraph:6"), g);
        g = InputGraph.createTestGraph("c32");
        g.getProperties().setProperty("graph", "inline:3-2");
        graphs.add(indexOf("StructuredGraph:6"), g);
        g = InputGraph.createTestGraph("c33");
        g.getProperties().setProperty("graph", "inline:3-3");
        graphs.add(indexOf("StructuredGraph:6"), g);
        for (InputGraph graph : graphs) {
            graph.setGraphType(DEFAULT_CLASSIFIER.classifyGraphType(graph.getProperties()));
        }
    }

    private int indexOf(String gn) {
        for (int i = 0; i < graphs.size(); i++) {
            String s = graphs.get(i).getProperties().get("graph", String.class);
            if (s != null && s.startsWith(gn)) {
                return i;
            }
        }
        Assert.fail("Graph not found");
        return -1;
    }

    /**
     * Checks that a completely loaded group is partitioned by the Timeline
     */
    @Test
    public void testCompleteGroupPartitioned() {
        createDefaultGroup();

        TimelineModelImpl timeline = new TimelineModelImpl(g, DEFAULT_CLASSIFIER, TYPE_INTERMEDIATE_GRAPH);
        timeline._testCompleteRefresh();

        assertEquals(2, timeline.getPartitions().size());
        RangeSliderModel mdl = timeline.getPartitionRange(TYPE_INTERMEDIATE_GRAPH);
        assertEquals(6, mdl.getPositions().size());

        mdl = timeline.getPartitionRange(TYPE_CALL_GRAPH);
        assertEquals(6, mdl.getPositions().size());
    }

    Group g = new Group(document);

    private void createDefaultGroup() {
        g.addElements(graphs);
    }

    private String sliderSpacedContents(RangeSliderModel sl) {
        List<String> pos = sl.getPositions(true);
        StringBuilder sb = new StringBuilder();
        for (String s : pos) {
            if (s == null) {
                sb.append("-");
            } else {
                sb.append(s);
            }
        }
        return sb.toString();
    }

    @Test
    public void testGapsInSliders() {
        createDefaultGroup();

        TimelineModelImpl timeline = new TimelineModelImpl(g, DEFAULT_CLASSIFIER, TYPE_INTERMEDIATE_GRAPH);
        timeline._testCompleteRefresh();

        RangeSliderModel mdl = timeline.getPartitionRange(TYPE_INTERMEDIATE_GRAPH);
        assertEquals("s1-s2s3--s4s5---s6", sliderSpacedContents(mdl));

        mdl = timeline.getPartitionRange(TYPE_CALL_GRAPH);
        assertEquals("-c11--c21c22--c31c32c33", sliderSpacedContents(mdl));
    }

    @Test
    public void testJustSinglePartition() {
        graphs.removeAll(graphs.subList(4, 12));
        graphs.remove(1);   // remove c11
        createDefaultGroup();

        TimelineModelImpl timeline = new TimelineModelImpl(g, DEFAULT_CLASSIFIER, TYPE_INTERMEDIATE_GRAPH);
        timeline._testCompleteRefresh();

        assertEquals(1, timeline.getPartitionTypes().size());

        RangeSliderModel mdl = timeline.getPartitionRange(TYPE_INTERMEDIATE_GRAPH);
        assertEquals("s1s2s3", sliderSpacedContents(mdl));
    }

    /**
     * Checks that a new segment appears after initialization of the model
     */
    @Test
    public void testPartitionAppears() {
        List<InputGraph> items = graphs;
        items.remove(1);
        graphs = new ArrayList<>();
        graphs.addAll(items.subList(0, 3));

        createDefaultGroup();

        TimelineModelImpl timeline = new TimelineModelImpl(g, DEFAULT_CLASSIFIER, TYPE_INTERMEDIATE_GRAPH);
        timeline._testCompleteRefresh();

        assertEquals(1, timeline.getPartitionTypes().size());
        assertEquals(3, timeline.getPrimaryRange().getPositionCount(true));

        g.addElements(items.subList(3, 4));
        timeline._testCompleteRefresh();

        assertEquals(2, timeline.getPartitionTypes().size());
        assertEquals(3, timeline.getPrimaryRange().getPositionCount(false));

        // the trailing slot is ignored.
        assertEquals(3, timeline.getPrimaryRange().getPositionCount(true));
    }

    /**
     * Checks that multiple segments / partitions appear after initialization
     */
    @Test
    public void testMultipleSegmentsAppear() {
        List<InputGraph> items = graphs;
        items.remove(1);
        graphs = new ArrayList<>();
        graphs.addAll(items.subList(0, 3));

        createDefaultGroup();

        TimelineModelImpl timeline = new TimelineModelImpl(g, DEFAULT_CLASSIFIER, TYPE_INTERMEDIATE_GRAPH);
        timeline._testCompleteRefresh();

        assertEquals(1, timeline.getPartitionTypes().size());
        assertEquals(3, timeline.getPrimaryRange().getPositionCount(true));

        g.addElements(items.subList(3, 11));
        timeline._testCompleteRefresh();

        assertEquals(2, timeline.getPartitionTypes().size());
        assertEquals(6, timeline.getPrimaryRange().getPositionCount(false));
        assertEquals("s1s2s3--s4s5---s6", sliderSpacedContents(timeline.getPrimaryRange()));

        // the trailing slot is ignored.
        assertEquals(11, timeline.getPrimaryRange().getPositionCount(true));
    }

}
