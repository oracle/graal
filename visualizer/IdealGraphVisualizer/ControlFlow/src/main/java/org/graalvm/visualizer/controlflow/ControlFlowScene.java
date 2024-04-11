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
package org.graalvm.visualizer.controlflow;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.*;

import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.netbeans.api.visual.action.*;
import org.netbeans.api.visual.anchor.AnchorFactory;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.graph.GraphScene;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.router.RouterFactory;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Widget;

import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputBlockEdge;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

public class ControlFlowScene extends GraphScene<InputBlock, InputBlockEdge> implements SelectProvider, MoveProvider, RectangularSelectDecorator, RectangularSelectProvider {
    private static final InputGraph EMPTY = new InputGraph(null, InputGraph.INVALID_INDEX, "", new Object[0]);
    
    private final HashSet<BlockWidget> selection;
    private InputGraphProvider provider;
    private final LayerWidget edgeLayer;
    private final LayerWidget mainLayer;
    private final LayerWidget selectLayer;
    private final WidgetAction hoverAction = this.createWidgetHoverAction();
    private final WidgetAction selectAction = new DoubleClickSelectAction(this);
    private final WidgetAction moveAction = ActionFactory.createMoveAction(null, this);

    private InputGraph oldGraph;

    public ControlFlowScene() {
        selection = new HashSet<>();

        this.getInputBindings().setZoomActionModifiers(0);
        this.setLayout(LayoutFactory.createAbsoluteLayout());

        mainLayer = new LayerWidget(this);
        this.addChild(mainLayer);

        edgeLayer = new LayerWidget(this);
        this.addChild(edgeLayer);

        selectLayer = new LayerWidget(this);
        this.addChild(selectLayer);

        this.getActions().addAction(hoverAction);
        this.getActions().addAction(selectAction);
        this.getActions().addAction(ActionFactory.createRectangularSelectAction(this, selectLayer, this));
        this.getActions().addAction(ActionFactory.createMouseCenteredZoomAction(1.1));
    }

    public void changeProvider(InputGraphProvider provider) {
        this.provider = provider;
        InputGraph graph = null;
        if (provider != null) {
            graph = provider.getGraph();
        }
        if (graph == null) {
            graph = EMPTY;
        }
        setGraph(graph);
        if (provider != null) {
            colorSelected();
        }
    }

    private void setGraph(InputGraph g) {
        if (g == oldGraph) {
            return;
        }
        oldGraph = g;

        ArrayList<InputBlock> blocks = new ArrayList<>(this.getNodes());
        blocks.forEach(this::removeNode);

        ArrayList<InputBlockEdge> edges = new ArrayList<>(this.getEdges());
        edges.forEach(this::removeEdge);

        g.getBlocks().forEach(this::addNode);

        for (InputBlockEdge e : g.getBlockEdges()) {
            addEdge(e);
            assert g.getBlocks().contains(e.getFrom());
            assert g.getBlocks().contains(e.getTo());
            this.setEdgeSource(e, e.getFrom());
            this.setEdgeTarget(e, e.getTo());
        }

        LayoutFactory.createSceneGraphLayout(this, new HierarchicalGraphLayout<>()).invokeLayout();
    }

    public void clearSelection() {
        for (BlockWidget w : selection) {
            w.setState(w.getState().deriveSelected(false));
        }
        selection.clear();
        selectionChanged();
    }

    public void selectionChanged() {
        if (provider != null) {
            Set<InputNode> inputNodes = new HashSet<>();
            for (BlockWidget w : selection) {
                inputNodes.addAll(w.getBlock().getNodes());
            }
            provider.setSelectedNodes(inputNodes);
        }
    }

    public void addToSelection(BlockWidget widget) {
        widget.setState(widget.getState().deriveSelected(true));
        selection.add(widget);
        selectionChanged();
    }

    public void removeFromSelection(BlockWidget widget) {
        widget.setState(widget.getState().deriveSelected(false));
        selection.remove(widget);
        selectionChanged();
    }

    @Override
    public boolean isAimingAllowed(Widget widget, Point point, boolean b) {
        return false;
    }

    @Override
    public boolean isSelectionAllowed(Widget widget, Point point, boolean b) {
        return true;
    }

    @Override
    public void select(Widget widget, Point point, boolean change) {
        if (widget == this) {
            clearSelection();
        } else {

            assert widget instanceof BlockWidget;
            BlockWidget bw = (BlockWidget) widget;
            if (change) {
                if (selection.contains(bw)) {
                    removeFromSelection(bw);
                } else {
                    addToSelection(bw);
                }
            } else {
                if (!selection.contains(bw)) {
                    clearSelection();
                    addToSelection(bw);
                }
            }
        }
    }

    @Override
    public void movementStarted(Widget widget) {
    }

    @Override
    public void movementFinished(Widget widget) {
    }

    @Override
    public Point getOriginalLocation(Widget widget) {
        return widget.getPreferredLocation();
    }

    @Override
    public void setNewLocation(Widget widget, Point location) {
        if (widget instanceof BlockWidget && selection.contains((BlockWidget) widget)) {
            // move entire selection
            Point originalLocation = getOriginalLocation(widget);
            int xOffset = location.x - originalLocation.x;
            int yOffset = location.y - originalLocation.y;
            for (BlockWidget w : selection) {
                Point p = new Point(w.getPreferredLocation());
                p.translate(xOffset, yOffset);
                w.setPreferredLocation(p);
            }
        } else {
            widget.setPreferredLocation(location);
        }
    }

    @Override
    public Widget createSelectionWidget() {
        Widget widget = new Widget(this);
        widget.setOpaque(false);
        widget.setBorder(BorderFactory.createLineBorder(Color.black, 2));
        widget.setForeground(Color.red);
        return widget;
    }

    @Override
    public void performSelection(Rectangle rectangle) {
        if (rectangle.width < 0) {
            rectangle.x += rectangle.width;
            rectangle.width *= -1;
        }

        if (rectangle.height < 0) {
            rectangle.y += rectangle.height;
            rectangle.height *= -1;
        }

        boolean changed = false;
        for (InputBlock b : this.getNodes()) {
            BlockWidget w = (BlockWidget) findWidget(b);
            if (w == null) {
                continue;
            }
            Rectangle r = w.getBounds();
            boolean remove = false;
            if (r != null) {
                r.setLocation(w.getLocation());
                if (r.intersects(rectangle)) {
                    if (!selection.contains(w)) {
                        changed = true;
                        selection.add(w);
                        w.setState(w.getState().deriveSelected(true));
                    }
                    remove = true;
                }
            }
            if (remove) {
                if (selection.contains(w)) {
                    changed = true;
                    selection.remove(w);
                    w.setState(w.getState().deriveSelected(false));
                }
            }
        }

        if (changed) {
            selectionChanged();
        }

    }

    @Override
    protected Widget attachNodeWidget(InputBlock node) {
        BlockWidget w = new BlockWidget(this, node);
        mainLayer.addChild(w);
        w.getActions().addAction(hoverAction);
        w.getActions().addAction(selectAction);
        w.getActions().addAction(moveAction);
        return w;
    }

    @Override
    protected Widget attachEdgeWidget(InputBlockEdge edge) {
        BlockConnectionWidget w = new BlockConnectionWidget(this, edge);
        switch (edge.getState()) {
            case NEW:
                w.setBold(true);
                break;
            case DELETED:
                w.setDashed(true);
                break;
        }
        w.setRouter(RouterFactory.createDirectRouter());
        w.setTargetAnchorShape(AnchorShape.TRIANGLE_FILLED);
        edgeLayer.addChild(w);
        return w;
    }

    @Override
    protected void attachEdgeSourceAnchor(InputBlockEdge edge, InputBlock oldSourceNode, InputBlock sourceNode) {
        Widget w = this.findWidget(edge);
        assert w instanceof ConnectionWidget;
        ConnectionWidget cw = (ConnectionWidget) w;
        cw.setSourceAnchor(AnchorFactory.createRectangularAnchor(findWidget(sourceNode)));

    }

    @Override
    protected void attachEdgeTargetAnchor(InputBlockEdge edge, InputBlock oldTargetNode, InputBlock targetNode) {
        Widget w = this.findWidget(edge);
        assert w instanceof ConnectionWidget;
        ConnectionWidget cw = (ConnectionWidget) w;
        cw.setTargetAnchor(AnchorFactory.createRectangularAnchor(findWidget(targetNode)));
    }

    private void colorSelected() {
        Collection<? extends InputNode> nodes = provider.getLookup().lookupAll(InputNode.class);
        //Find selected blocks
        Set<String> selectedBlocks = nodes.stream()
                .map(InputNode::getId)
                .distinct()//get rid of same multiple ids
                .map(x -> oldGraph.getBlock(x))
                .filter(b -> b != null)
                //Comparing of blocks is expensive, name comparison is enough
                .map(InputBlock::getName)
                .collect(Collectors.toSet());
        //Find block corresponding widgets
        Set<Widget> selectedWidgets = mainLayer.getChildren().stream()
                .filter(x -> selectedBlocks.contains(((BlockWidget) x).getBlock().getName()))
                .collect(Collectors.toSet());
        //Reset color to default
        for (Widget widget : mainLayer.getChildren()) {
            widget.setBackground(BlockWidget.DEFAULT_COLOR);
        }
        //Highlight selected blocks
        for (Widget widget : selectedWidgets) {
            widget.setBackground(BlockWidget.SELECTED_COLOR);
        }
        validate();
    }
}
