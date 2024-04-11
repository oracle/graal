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

package org.graalvm.visualizer.graph;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_STATE;
import static org.graalvm.visualizer.data.DataTestUtil.*;
import static org.graalvm.visualizer.layout.LayoutTestUtil.assertPortEquals;
import static org.graalvm.visualizer.layout.LayoutTestUtil.assertVertexEquals;
import static org.graalvm.visualizer.settings.TestUtils.*;
import static org.junit.Assert.*;

import java.awt.*;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.graalvm.visualizer.data.DataTestUtil;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalLayoutManager;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.settings.TestUtils;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class GraphTestUtil {//uncomplete/incorrect

    public static void assertDiagramCorrectState(InputGraph sourceGraph, String name, Diagram diagram) {
        assertNotNull(sourceGraph);
        assertNotNull(diagram);
        assertEquals(name, diagram.getNodeText());
        assertInputGraphEquals(sourceGraph, diagram.getGraph());
        assertListEquals(new ArrayList<>(makeFigures(sourceGraph, name, diagram).values()),
                new ArrayList<>(diagram.getFigures()), GraphTestUtil::assertFigureEqualsDeep);
        assertCollections_NoOrder(sourceGraph.getBlocks().stream()
                        .map(x -> new Block(x, diagram))
                        .collect(Collectors.toList()),
                diagram.getBlocks(), GraphTestUtil::assertBlockEquals, Block::getInputBlock);
    }

    private static LinkedHashMap<Integer, Figure> makeFigures(InputGraph graph, String name, Diagram sortBy) {
        LinkedHashMap<Integer, Figure> map = new LinkedHashMap<>();
        Diagram d = Diagram.createDiagram(graph, name);
        for (Figure f : d.getFigures()) {
            map.put(f.getId(), f);
        }
        if ("Difference".equals(graph.getGroup().getName())) {
            colorizeFigures(map);
        }
        layoutFigures(d);
        return map;
    }

    private static void colorizeFigures(LinkedHashMap<Integer, Figure> map) {
        //"colorize('state', 'same', white);" + "colorize('state', 'changed', orange);" + "colorize('state', 'new', green);" + "colorize('state', 'deleted', red);");
        map.forEach((i, f) -> {
            String state = f.getProperties().get(PROPNAME_STATE, String.class);
            switch (state) {
                case "same":
                    f.setColor(Color.white);
                    break;
                case "changed":
                    f.setColor(Color.orange);
                    break;
                case "new":
                    f.setColor(Color.green);
                    break;
                case "deleted":
                    f.setColor(Color.red);
                    break;
            }
        });
    }

    private static void layoutFigures(Diagram outputDiagram) {
        HashSet<Figure> figures = new HashSet<>(outputDiagram.getFigures());
        HashSet<Connection> edges = new HashSet<>(outputDiagram.getConnections());
        LayoutSettingBean layoutSettings = LayoutSettings.getBean();
        InputGraph g = outputDiagram.getGraph();
        for (Figure f : figures) {
            f.setVisible(true);
            for (InputNode n : f.getSource().getSourceNodes()) {
                outputDiagram.getBlock(g.getBlock(n)).setVisible(true);
            }
        }
        HierarchicalLayoutManager manager = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSettings);
        manager.setMaxLayerLength(10);
        manager.doLayout(new LayoutGraph(edges, figures));
    }

    public static void assertDiagramEqualsShallow(Diagram a, Diagram b) {
        if (checkNotNulls(a, b)) {
            assertCollections_NoOrder(a.getBlocks(), b.getBlocks(), GraphTestUtil::assertBlockEquals, Block::getInputBlock);
            assertInputGraphEquals(a.getGraph(), b.getGraph());
            assertEquals(a.getNodeText(), b.getNodeText());
        }
    }

    public static <T extends Figure> void assertContentsEquals(Collection<T> a, Collection<T> b, BiConsumer<T, T> assertion) {
        Map<Integer, T> x = new HashMap<>();
        Map<Integer, T> y = new HashMap<>();
        for (T item : a) {
            x.put(item.getId(), item);
        }
        for (T item : b) {
            y.put(item.getId(), item);
        }
    }

    public static void assertDiagramEquals(Diagram a, Diagram b) {
        if (TestUtils.checkNotNulls(a, b)) {
            assertDiagramEqualsShallow(a, b);
            assertContentsEquals(a.getFigures(), b.getFigures(), GraphTestUtil::assertFigureEqualsShallow);
        }
    }

    public static void assertDiagramEqualsDeep(Diagram a, Diagram b) {
        if (checkNotNulls(a, b)) {
            assertDiagramEqualsShallow(a, b);
            assertContentsEquals(a.getFigures(), b.getFigures(), GraphTestUtil::assertFigureEquals);
            assertListEquals(a.getRootFigures(), b.getRootFigures(), GraphTestUtil::assertFigureEquals);
        }
    }

    public static void assertBlockEquals(Block a, Block b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.getBounds(), b.getBounds());
            assertInputBlockEquals(a.getInputBlock(), b.getInputBlock());
        }
    }

    public static void assertConnectionEqualsShallow(Connection a, Connection b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.getColor(), b.getColor());
            assertEquals(a.getControlPoints(), b.getControlPoints());
            assertEquals(a.getLabel(), b.getLabel());
            assertEquals(a.getStyle(), b.getStyle());
            assertEquals(a.getToolTipText(), b.getToolTipText());
            assertEquals(a.getType(), b.getType());
            assertEquals(a.isVIP(), b.isVIP());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertConnectionEquals(Connection a, Connection b) {
        if (checkNotNulls(a, b)) {
            assertConnectionEqualsShallow(a, b);
            assertPortEquals(a.getFrom(), b.getFrom());
            assertInputSlotEqualsSimple(a.getInputSlot(), b.getInputSlot());
            assertEquals(a.getOutputSlot(), b.getOutputSlot());
            assertEquals(a.getTo(), b.getTo());
        }
    }

    public static void assertConnectionEqualsDeep(Connection a, Connection b) {
        if (checkNotNulls(a, b)) {
            assertConnectionEqualsShallow(a, b);
            assertPortEquals(a.getFrom(), b.getFrom());
            assertInputSlotEquals(a.getInputSlot(), b.getInputSlot());
            assertEquals(a.getOutputSlot(), b.getOutputSlot());
            assertEquals(a.getTo(), b.getTo());
        }
    }

    public static void assertInputSlotEqualsSimple(InputSlot a, InputSlot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEqualsShallow(a, b);
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertInputSlotEquals(InputSlot a, InputSlot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEquals(a, b);
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertOutputSlotEqualsShallow(OutputSlot a, OutputSlot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEqualsShallow(a, b);
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertOutputSlotEquals(OutputSlot a, OutputSlot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEquals(a, b);
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertOutputSlotEqualsDeep(OutputSlot a, OutputSlot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEquals(a, b);
            assertEquals(a.getRelativePosition(), b.getRelativePosition());
            assertEquals(a.toString(), b.toString());
        }
    }

    public static void assertSlotEqualsShallow(Slot a, Slot b) {
        if (checkNotNulls(a, b)) {
            assertPortEquals(a, b);
            assertInputNodeEquals(a.getAssociatedNode(), b.getAssociatedNode());
            assertEquals(a.getColor(), b.getColor());
            assertEquals(a.getPosition(), b.getPosition());
            assertPropertiesEquals(a.getProperties(), b.getProperties());
            assertEquals(a.getShortName(), b.getShortName());
            assertEquals(a.getText(), b.getText());
            assertEquals(a.getToolTipText(), b.getToolTipText());
            assertEquals(a.getWantedIndex(), b.getWantedIndex());
            assertEquals(a.getWidth(), b.getWidth());
            assertEquals(a.shouldShowName(), b.shouldShowName());
            assertListEquals(a.getSource().getSourceNodes(), b.getSource().getSourceNodes(), DataTestUtil::assertInputNodeEquals);
        }
    }

    public static void assertSlotEquals(Slot a, Slot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEqualsShallow(a, b);
            assertCollections_NoOrder(a.getConnections(), b.getConnections(), GraphTestUtil::assertConnectionEqualsShallow, (c) -> c.getInputSlot().toString() + ">;<" + c.getOutputSlot().toString());
            assertFigureEqualsShallow(a.getFigure(), b.getFigure());
        }
    }

    public static void assertSlotEqualsDeep(Slot a, Slot b) {
        if (checkNotNulls(a, b)) {
            assertSlotEqualsShallow(a, b);
            assertCollections_NoOrder(a.getConnections(), b.getConnections(), GraphTestUtil::assertConnectionEquals, (c) -> c.getInputSlot().toString() + ">;<" + c.getOutputSlot().toString());
            assertFigureEquals(a.getFigure(), b.getFigure());
        }
    }

    public static void assertFigureEqualsShallow(Figure a, Figure b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.getId(), b.getId());
            assertVertexEquals(a, b);
            assertEquals(a.getBounds(), b.getBounds());
            assertEquals(a.getColor(), b.getColor());
            assertEquals(a.getHeight(), b.getHeight());
            assertArrayEquals(a.getLines(), b.getLines());
            assertPropertiesEquals(a.getProperties(), b.getProperties());
            assertEquals(a.getSubgraphs(), b.getSubgraphs());
            assertEquals(a.getWidth(), b.getWidth());
        }
    }

    public static void assertFigureEquals(Figure a, Figure b) {
        if (checkNotNulls(a, b)) {
            assertFigureEqualsShallow(a, b);
            assertDiagramEqualsShallow(a.getDiagram(), b.getDiagram());
            assertCollections_NoOrder(a.getInputSlots(), b.getInputSlots(), GraphTestUtil::assertInputSlotEqualsSimple, Slot::getPosition);
            assertCollections_NoOrder(a.getOutputSlots(), b.getOutputSlots(), GraphTestUtil::assertOutputSlotEqualsShallow, Slot::getPosition);
            assertCollections_NoOrder(a.getPredecessorSet(), b.getPredecessorSet(), GraphTestUtil::assertFigureEqualsShallow, Figure::getId);
            assertCollections_NoOrder(a.getPredecessors(), b.getPredecessors(), GraphTestUtil::assertFigureEqualsShallow, Figure::getId);
            assertCollections_NoOrder(a.getSlots(), b.getSlots(), GraphTestUtil::assertSlotEqualsShallow, Slot::toString);
            assertCollections_NoOrder(a.getSuccessorSet(), b.getSuccessorSet(), GraphTestUtil::assertFigureEqualsShallow, Figure::getId);
            assertCollections_NoOrder(a.getSuccessors(), b.getSuccessors(), GraphTestUtil::assertFigureEqualsShallow, Figure::getId);
        }
    }

    public static void assertFigureEqualsDeep(Figure a, Figure b) {
        if (checkNotNulls(a, b)) {
            assertFigureEqualsShallow(a, b);
            assertDiagramEquals(a.getDiagram(), b.getDiagram());
            assertCollections_NoOrder(a.getInputSlots(), b.getInputSlots(), GraphTestUtil::assertInputSlotEquals, Slot::getPosition);
            assertCollections_NoOrder(a.getOutputSlots(), b.getOutputSlots(), GraphTestUtil::assertOutputSlotEquals, Slot::getPosition);
            assertCollections_NoOrder(a.getPredecessorSet(), b.getPredecessorSet(), GraphTestUtil::assertFigureEquals, Figure::getId);
            assertCollections_NoOrder(a.getPredecessors(), b.getPredecessors(), GraphTestUtil::assertFigureEquals, Figure::getId);
            assertCollections_NoOrder(a.getSlots(), b.getSlots(), GraphTestUtil::assertSlotEquals, Slot::toString);
            assertCollections_NoOrder(a.getSuccessorSet(), b.getSuccessorSet(), GraphTestUtil::assertFigureEquals, Figure::getId);
            assertCollections_NoOrder(a.getSuccessors(), b.getSuccessors(), GraphTestUtil::assertFigureEquals, Figure::getId);
        }
    }
}
