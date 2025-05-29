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

import org.graalvm.visualizer.hierarchicallayout.HierarchicalLayoutManager;
import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.netbeans.api.visual.graph.layout.GraphLayout;
import org.netbeans.api.visual.graph.layout.UniversalGraph;
import org.netbeans.api.visual.widget.Widget;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STANDALONES;

public class HierarchicalGraphLayout<N, E> extends GraphLayout<N, E> {

    public HierarchicalGraphLayout() {
    }

    private class LinkWrapper implements Link {

        private final VertexWrapper from;
        private final VertexWrapper to;

        public LinkWrapper(VertexWrapper from, VertexWrapper to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public Port getFrom() {
            return from.getSlot();
        }

        @Override
        public Port getTo() {
            return to.getSlot();
        }

        @Override
        public List<Point> getControlPoints() {
            return new ArrayList<>();
        }

        @Override
        public void setControlPoints(List<Point> list) {
            // Do nothing for now
        }

        @Override
        public boolean isVIP() {
            return false;
        }
    }

    private class VertexWrapper implements Vertex {

        private N node;
        private UniversalGraph<N, E> graph;
        private final Port slot;
        private Point position;

        public VertexWrapper(N node, UniversalGraph<N, E> graph) {
            this.node = node;
            this.graph = graph;
            this.slot = new Port() {

                @Override
                public Vertex getVertex() {
                    return VertexWrapper.this;
                }

                @Override
                public Point getRelativePosition() {
                    return new Point((int) (VertexWrapper.this.getSize().getWidth() / 2), (int) (VertexWrapper.this.getSize().getHeight() / 2));
                }
            };

            this.position = graph.getScene().findWidget(node).getPreferredLocation();
        }

        @Override
        public Cluster getCluster() {
            return null;
        }

        @Override
        public Dimension getSize() {
            Widget w = graph.getScene().findWidget(node);
            Rectangle bnds = w.getBounds();
            return bnds == null ? new Dimension(0, 0) : bnds.getSize();
        }

        @Override
        public Point getPosition() {
            return position;
        }

        @Override
        public void setPosition(Point p) {
            setResolvedNodeLocation(graph, node, p);
            position = p;
        }

        @Override
        public boolean isRoot() {
            return false;
        }

        @Override
        public int compareTo(Vertex o) {
            @SuppressWarnings("unchecked")
            VertexWrapper vw = (VertexWrapper) o;
            return node.toString().compareTo(vw.node.toString());
        }

        public Port getSlot() {
            return slot;
        }

        @Override
        public boolean isVisible() {
            return true;
        }
    }

    @Override
    protected void performGraphLayout(UniversalGraph<N, E> graph) {

        Set<LinkWrapper> links = new LinkedHashSet<>();
        Set<VertexWrapper> vertices = new LinkedHashSet<>();
        Map<N, VertexWrapper> vertexMap = new HashMap<>();

        for (N node : graph.getNodes()) {
            VertexWrapper v = new VertexWrapper(node, graph);
            vertexMap.put(node, v);
            vertices.add(v);
        }

        for (E edge : graph.getEdges()) {
            N source = graph.getEdgeSource(edge);
            N target = graph.getEdgeTarget(edge);
            LinkWrapper l = new LinkWrapper(vertexMap.get(source), vertexMap.get(target));
            links.add(l);
        }

        LayoutSettingBean layoutSetting = LayoutSettings.getBean();
        boolean decreaseDeviation = layoutSetting.get(Boolean.class, DECREASE_LAYER_WIDTH_DEVIATION);
        boolean standAlones = layoutSetting.get(Boolean.class, STANDALONES);
        layoutSetting.set(DECREASE_LAYER_WIDTH_DEVIATION, false);
        layoutSetting.set(STANDALONES, false);
        HierarchicalLayoutManager m = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.NONE, layoutSetting);
        m.doLayout(new LayoutGraph(links, vertices));
        layoutSetting.set(DECREASE_LAYER_WIDTH_DEVIATION, decreaseDeviation);
        layoutSetting.set(STANDALONES, standAlones);
    }

    @Override
    protected void performNodesLayout(UniversalGraph<N, E> graph, Collection<N> nodes) {
        throw new UnsupportedOperationException();
    }
}
