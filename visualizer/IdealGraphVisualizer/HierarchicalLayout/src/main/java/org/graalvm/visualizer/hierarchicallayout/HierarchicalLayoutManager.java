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
package org.graalvm.visualizer.hierarchicallayout;

import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.layout.LayoutManager;
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntUnaryOperator;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.BOTH_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CENTER_CROSSING_X;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CENTER_SIMPLE_NODES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSSING_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSSING_SWEEP_COUNT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_BY_CONN_DIFF;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_FACTOR;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_POSITION_DURING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_REDUCE_ROUTING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_RESET_X_FROM_MIDDLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.CROSS_RESET_X_FROM_NODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION_QUICK;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION_UP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DEFAULT_LAYOUT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DELAY_DANGLING_NODES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DRAW_LONG_EDGES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_CROSSING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_FIRST_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.EDGE_BENDING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.IRRELEVANT_LAYOUT_CODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.LAST_DOWN_SWEEP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.LAST_UP_CROSSING_SWEEP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MEAN_NOT_MEDIAN;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.MIN_EDGE_ANGLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_CROSSING_LAYER_REASSIGN;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_DUMMY_LONG_EDGES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.NO_VIP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.OPTIMAL_UP_VIP;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.PROPER_CROSSING_CLOSEST_NODE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.REVERSE_SORT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.SPAN_BY_ANGLE;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.SQUASH_POSITION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STANDALONES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.UNKNOWN_CROSSING_NUMBER;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.UNREVERSE_VIPS;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.X_ASSIGN_SWEEP_COUNT;

public class HierarchicalLayoutManager implements LayoutManager {

    private static final Logger LOG = Logger.getLogger(HierarchicalLayoutManager.class.getName());

    public static final boolean TRACE = false;
    public static final boolean CHECK = false;
    public static final int SWEEP_ITERATIONS = 1;
    public static final int CROSSING_ITERATIONS = 2;
    public static final int DUMMY_HEIGHT = 1;
    public static final int DUMMY_WIDTH = 1;
    public static final int X_OFFSET = 8;
    public static final int LAYER_OFFSET = 8;
    public static final int MAX_LAYER_LENGTH = -1;
    public static final int VIP_BONUS = 10;

    private final AtomicBoolean cancelled;

    @Override
    public boolean cancel() {
        cancelled.set(true);
        return true;
    }

    public enum Combine {
        NONE,
        SAME_INPUTS,
        SAME_OUTPUTS
    }

    private final LayoutSettingBean setting;
    // Options
    private final Combine combine;
    private final int dummyWidth;
    private final int offset;
    private int maxLayerLength;
    // Algorithm global datastructures
    private final Set<Link> reversedLinks;
    private final Set<LayoutEdge> longEdges;
    private final List<LayoutNode> nodes;
    private final List<LayoutNode> standAlones;
    private final HashMap<Vertex, LayoutNode> vertexToLayoutNode;
    private final HashMap<Link, List<Point>> reversedLinkStartPoints;
    private final HashMap<Link, List<Point>> reversedLinkEndPoints;
    private LayoutGraph graph;
    private LayoutLayer[] layers;
    private int layerCount;

    //preloaded setting to speedup execution
    private final boolean isDefaultLayout;
    private final boolean isDummyCrossing;
    private final boolean isCrossingByConnDiff;
    private final boolean isDelayDanglingNodes;
    private final boolean isDrawLongEdges;

    private class LayoutNode {

        public int x;
        public int y;
        public int width;
        public int height;
        public int layer = -1;
        public int xOffset;
        public int yOffset;
        public int bottomYOffset;
        public final Vertex vertex; // Only used for non-dummy nodes, otherwise null

        public final List<LayoutEdge> preds = new ArrayList<>();
        public final List<LayoutEdge> succs = new ArrayList<>();
        public int pos = -1; // Position within layer

        public float crossingNumber = 0;

        public void loadCrossingNumber(boolean up) {
            crossingNumber = 0;
            int count = loadCrossingNumber(up, this);
            if (count > 0) {
                crossingNumber /= count;
            } else {
                crossingNumber = 0;
            }
        }

        public int loadCrossingNumber(boolean up, LayoutNode source) {
            int count = 0;
            if (up) {
                count = succs.stream().map((e) -> e.loadCrossingNumber(up, source)).reduce(count, Integer::sum);
            } else {
                count = preds.stream().map((e) -> e.loadCrossingNumber(up, source)).reduce(count, Integer::sum);
            }
            return count;
        }

        @Override
        public String toString() {
            return "Node " + vertex;
        }

        public LayoutNode(Vertex v) {
            vertex = v;
            if (v == null) {
                height = DUMMY_HEIGHT;
                width = dummyWidth;
            } else {
                Dimension size = v.getSize();
                height = size.height;
                width = size.width;
            }
        }

        public LayoutNode() {
            this(null);
        }

        public Collection<LayoutEdge> getVisibleSuccs() {
            List<LayoutEdge> edges = new ArrayList<>();
            for (int i = layer + 1; i < layerCount; ++i) {
                if (layers[i].isVisible()) {
                    for (LayoutEdge e : succs) {
                        LayoutEdge last = e;
                        for (int l = layer + 1; l < i; ++l) {
                            for (LayoutEdge ed : last.to.succs) {
                                if (ed.link == e.link) {
                                    last = ed;
                                    break;
                                }
                            }
                        }
                        if (last == e) {
                            edges.add(e);
                        } else {
                            edges.add(new LayoutEdge(e.from, last.to, e.relativeFrom, last.relativeTo, e.link, e.vip));
                        }
                    }
                    break;
                }
            }
            return edges;
        }

        public int getLeftSide() {
            return xOffset + x;
        }

        public int getWholeWidth() {
            return xOffset + width;
        }

        public int getWholeHeight() {
            return height + yOffset + bottomYOffset;
        }

        public int getRightSide() {
            return x + getWholeWidth();
        }

        public int getCenterX() {
            return getLeftSide() + (width / 2);
        }

        public int getBottom() {
            return y + height;
        }

        public boolean isDummy() {
            return vertex == null;
        }
    }

    private class LayoutEdge {

        public LayoutNode from;
        public LayoutNode to;
        public int relativeFrom;
        public int relativeTo;
        public Link link;
        public boolean vip;

        public int loadCrossingNumber(boolean up, LayoutNode source) {
            if (isDefaultLayout || !isDummyCrossing || !(up ? to.isDummy() : from.isDummy())) {
                int factor = vip ? VIP_BONUS : 1;
                int nr;
                if (!isDefaultLayout && isCrossingByConnDiff) {
                    if (up) {
                        nr = (getEndPoint() - getStartPoint()) * factor;
                    } else {
                        nr = (getStartPoint() - getEndPoint()) * factor;
                    }
                } else {
                    if (up) {
                        nr = getEndPoint() * factor;
                    } else {
                        nr = getStartPoint() * factor;
                    }
                }
                source.crossingNumber += nr;
                return factor;
            } else {
                if (up) {
                    return to.loadCrossingNumber(up, source);
                } else {
                    return from.loadCrossingNumber(up, source);
                }
            }
        }

        public int getStartPoint() {
            return relativeFrom + from.getLeftSide();
        }

        public int getEndPoint() {
            return relativeTo + to.getLeftSide();
        }

        public LayoutEdge(LayoutNode from, LayoutNode to) {
            assert from != null && to != null : "Dangling LayoutEdge.";
            this.from = from;
            this.to = to;
        }

        public LayoutEdge(LayoutNode from, LayoutNode to, int relativeFrom, int relativeTo, Link link, boolean vip) {
            this(from, to);
            this.relativeFrom = relativeFrom;
            this.relativeTo = relativeTo;
            this.link = link;
            this.vip = vip;
        }
    }

    private abstract class AlgorithmPart {

        public void start() {
            if (CHECK) {
                preCheck();
            }

            long start = 0;
            if (TRACE) {
                System.out.println("##################################################");
                System.out.println("Starting part " + this.getClass().getName());
                start = System.currentTimeMillis();
            }
            run();
            if (TRACE) {
                System.out.println("Timing for " + this.getClass().getName() + " is " + (System.currentTimeMillis() - start));
                printStatistics();
            }

            if (CHECK) {
                postCheck();
            }
        }

        protected abstract void run();

        protected void printStatistics() {
        }

        protected void postCheck() {
        }

        protected void preCheck() {
        }
    }

    public HierarchicalLayoutManager() {
        this(Combine.NONE, LayoutSettings.getBean());
    }

    public HierarchicalLayoutManager(Combine b) {
        this(b, LayoutSettings.getBean());
    }

    public HierarchicalLayoutManager(LayoutSettingBean layoutSetting) {
        this(Combine.NONE, layoutSetting);
    }

    public HierarchicalLayoutManager(Combine b, LayoutSettingBean layoutSetting) {
        setting = layoutSetting;
        isDefaultLayout = setting.get(Boolean.class, DEFAULT_LAYOUT);
        isDummyCrossing = setting.get(Boolean.class, DUMMY_CROSSING);
        isCrossingByConnDiff = setting.get(Boolean.class, CROSS_BY_CONN_DIFF);
        isDelayDanglingNodes = setting.get(Boolean.class, DELAY_DANGLING_NODES);
        isDrawLongEdges = setting.get(Boolean.class, DRAW_LONG_EDGES);

        this.combine = b;
        //when default value 1 is used, the calculated RelativeTo and relativeFrom
        //of dummyNodes are 0 which negatively affects calculations
        if (isDefaultLayout) {
            this.dummyWidth = DUMMY_WIDTH;
        } else {
            this.dummyWidth = setting.get(Integer.class, LayoutSettings.DUMMY_WIDTH);
        }
        this.maxLayerLength = MAX_LAYER_LENGTH;
        offset = X_OFFSET + dummyWidth;

        vertexToLayoutNode = new HashMap<>();
        reversedLinks = new HashSet<>();
        reversedLinkStartPoints = new HashMap<>();
        reversedLinkEndPoints = new HashMap<>();
        nodes = new ArrayList<>();
        standAlones = new ArrayList<>();
        longEdges = new HashSet<>();
        cancelled = new AtomicBoolean(false);
    }

    public int getMaxLayerLength() {
        return maxLayerLength;
    }

    public void setMaxLayerLength(int v) {
        maxLayerLength = v;
    }

    private void cleanup() {
        vertexToLayoutNode.clear();
        reversedLinks.clear();
        reversedLinkStartPoints.clear();
        reversedLinkEndPoints.clear();
        nodes.clear();
        longEdges.clear();
    }

    @Override
    public void doLayout(LayoutGraph graph) {
        this.graph = graph;

        cleanup();

        // #############################################################
        // Step 1: Build up data structure
        new BuildDatastructure().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 2: Reverse edges, handle backedges
        new ReverseEdges().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 3: Assign layers
        new AssignLayers().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 4: Create dummy nodes
        new CreateDummyNodes().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 5: Crossing Reduction
        new CrossingReduction().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 6: Assign X coordinates
        new AssignXCoordinates().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 7: Assign Y coordinates
        new AssignYCoordinates().start();
        if (cancelled.get()) {
            return;
        }

        // #############################################################
        // STEP 8: Write back to interface
        new WriteResult().start();
    }

    private class WriteResult extends AlgorithmPart {

        private HashMap<Link, List<Point>> splitStartPoints;
        private HashMap<Link, List<Point>> splitEndPoints;
        private HashMap<Point, Point> pointsIdentity;
        private int pointCount;
        private final int addition = LAYER_OFFSET / 2;

        @Override
        protected void run() {
            splitStartPoints = new HashMap<>();
            splitEndPoints = new HashMap<>();
            pointsIdentity = new HashMap<>();
            HashMap<Vertex, Point> vertexPositions = new HashMap<>();
            HashMap<Link, List<Point>> linkPositions = new HashMap<>();
            for (Vertex v : graph.getVertices()) {
                LayoutNode n = vertexToLayoutNode.get(v);
                if (!(setting.get(Boolean.class, STANDALONES) && standAlones.contains(n))) {
                    assert !vertexPositions.containsKey(v);
                    vertexPositions.put(v, new Point(n.getLeftSide(), n.y));
                }
            }

            for (LayoutNode n : nodes) {
                if (layers[n.layer].isVisible()) {
                    for (LayoutEdge e : n.preds) {
                        if (e.link != null) {
                            ArrayList<Point> points = new ArrayList<>();

                            points.add(new Point(e.getEndPoint(), e.to.y));
                            points.add(new Point(e.getEndPoint(), layers[e.to.layer].y - LAYER_OFFSET));

                            LayoutNode cur = e.from;
                            LayoutNode other = e.to;
                            LayoutEdge curEdge = e;
                            while (cur.isDummy() && !cur.preds.isEmpty()) {
                                if (layers[cur.layer].isVisible()) {
                                    points.add(new Point(cur.getCenterX(), layers[cur.layer].getBottom() + LAYER_OFFSET));
                                    points.add(new Point(cur.getCenterX(), cur.y - LAYER_OFFSET));
                                }
                                assert cur.preds.size() == 1;
                                curEdge = cur.preds.get(0);
                                cur = curEdge.from;
                            }

                            points.add(new Point(curEdge.getStartPoint(), layers[cur.layer].getBottom() + LAYER_OFFSET));
                            points.add(new Point(curEdge.getStartPoint(), cur.getBottom()));

                            Collections.reverse(points);

                            if (cur.isDummy() && cur.preds.isEmpty()) {
                                if (reversedLinkEndPoints.containsKey(e.link)) {
                                    for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                        points.add(new Point(p1.x + other.getLeftSide(), p1.y + other.y));
                                    }
                                }

                                if (splitStartPoints.containsKey(e.link)) {
                                    points.add(0, null);
                                    points.addAll(0, splitStartPoints.get(e.link));

                                    if (reversedLinks.contains(e.link)) {
                                        Collections.reverse(points);
                                    }
                                    assert !linkPositions.containsKey(e.link);
                                    linkPositions.put(e.link, points);
                                } else {
                                    splitEndPoints.put(e.link, points);
                                }

                            } else {
                                if (reversedLinks.contains(e.link)) {
                                    Collections.reverse(points);
                                    if (reversedLinkStartPoints.containsKey(e.link)) {
                                        for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                            points.add(new Point(p1.x + cur.getLeftSide(), p1.y + cur.y));
                                        }
                                    }

                                    if (reversedLinkEndPoints.containsKey(e.link)) {
                                        for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                            points.add(0, new Point(p1.x + other.getLeftSide(), p1.y + other.y));
                                        }
                                    }
                                }

                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            }
                            pointCount += points.size();

                            // No longer needed!
                            e.link = null;
                        }
                    }

                    for (LayoutEdge e : n.succs) {
                        if (e.link != null) {
                            ArrayList<Point> points = new ArrayList<>();
                            points.add(new Point(e.getStartPoint(), e.from.getBottom()));
                            points.add(new Point(e.getStartPoint(), layers[e.from.layer].getBottom() + LAYER_OFFSET));

                            LayoutNode cur = e.to;
                            LayoutNode other = e.from;
                            LayoutEdge curEdge = e;
                            while (cur.isDummy() && !cur.succs.isEmpty()) {
                                if (layers[cur.layer].isVisible()) {
                                    points.add(new Point(cur.getCenterX(), layers[cur.layer].y - LAYER_OFFSET));
                                    points.add(new Point(cur.getCenterX(), layers[cur.layer].getBottom() + LAYER_OFFSET));
                                }
                                if (cur.succs.isEmpty()) {
                                    break;
                                }
                                assert cur.succs.size() == 1;
                                curEdge = cur.succs.get(0);
                                cur = curEdge.to;
                            }

                            points.add(new Point(curEdge.getEndPoint(), layers[cur.layer].y - LAYER_OFFSET));
                            points.add(new Point(curEdge.getEndPoint(), cur.y));

                            if (cur.succs.isEmpty() && cur.isDummy()) {
                                if (reversedLinkStartPoints.containsKey(e.link)) {
                                    for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                        points.add(0, new Point(p1.x + other.getLeftSide(), p1.y + other.y));
                                    }
                                }

                                if (splitEndPoints.containsKey(e.link)) {
                                    points.add(null);
                                    points.addAll(splitEndPoints.get(e.link));

                                    // checkPoints(points);
                                    if (reversedLinks.contains(e.link)) {
                                        Collections.reverse(points);
                                    }
                                    assert !linkPositions.containsKey(e.link);
                                    linkPositions.put(e.link, points);
                                } else {
                                    splitStartPoints.put(e.link, points);
                                }
                            } else {
                                if (reversedLinks.contains(e.link)) {
                                    if (reversedLinkStartPoints.containsKey(e.link)) {
                                        for (Point p1 : reversedLinkStartPoints.get(e.link)) {
                                            points.add(0, new Point(p1.x + other.getLeftSide(), p1.y + other.y));
                                        }
                                    }
                                    if (reversedLinkEndPoints.containsKey(e.link)) {
                                        for (Point p1 : reversedLinkEndPoints.get(e.link)) {
                                            points.add(new Point(p1.x + cur.getLeftSide(), p1.y + cur.y));
                                        }
                                    }
                                    Collections.reverse(points);
                                }
                                // checkPoints(points);
                                assert !linkPositions.containsKey(e.link);
                                linkPositions.put(e.link, points);
                            }

                            pointCount += points.size();
                            e.link = null;
                        }
                    }
                }
            }

            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            for (Map.Entry<Vertex, Point> entry : vertexPositions.entrySet()) {
                Vertex v = entry.getKey();
                if (v.isVisible()) {
                    Point p = entry.getValue();
                    Dimension s = v.getSize();
                    minX = Math.min(minX, p.x);
                    minY = Math.min(minY, p.y);
                    maxX = Math.max(maxX, p.x + s.width);
                    maxY = Math.max(maxY, p.y + s.height);
                }
            }

            for (Map.Entry<Link, List<Point>> entry : linkPositions.entrySet()) {
                Link l = entry.getKey();
                if (l.getFrom().getVertex().isVisible() && l.getTo().getVertex().isVisible()) {
                    for (Point p : entry.getValue()) {
                        if (p != null) {
                            minX = Math.min(minX, p.x);
                            minY = Math.min(minY, p.y);
                            maxX = Math.max(maxX, p.x);
                            maxY = Math.max(maxY, p.y);
                        }
                    }
                }
            }
            Dimension dim;
            if (setting.get(Boolean.class, STANDALONES)) {
                dim = setStandAlones(new Rectangle(minX, minY, maxX - minX, maxY - minY), vertexPositions);
                minY -= dim.height - maxY + minY;
            } else {
                dim = new Dimension(maxX - minX, maxY - minY);
            }
            for (Map.Entry<Vertex, Point> entry : vertexPositions.entrySet()) {
                Point p = entry.getValue();
                p.x -= minX;
                p.y -= minY;
                entry.getKey().setPosition(p);
            }

            for (LayoutEdge e : longEdges) {
                e.from.succs.add(e);
                e.to.preds.add(e);
                linkPositions.put(e.link, makeLongEnding(e));
            }

            if (!setting.get(Boolean.class, EDGE_BENDING)) {
                for (List<Point> points : linkPositions.values()) {
                    points.remove(points.size() - 2);
                    points.remove(1);
                }
            }

            Map<Port, List<Map.Entry<Link, List<Point>>>> coLinks = new HashMap<>();
            for (Map.Entry<Link, List<Point>> entry : linkPositions.entrySet()) {
                Link l = entry.getKey();
                // separate link entries by their Output Port, don't gather invisible edges.
                if (l.getFrom().getVertex().isVisible() && l.getTo().getVertex().isVisible()) {
                    coLinks.computeIfAbsent(l.getFrom(), p -> new ArrayList<>()).add(entry);
                }
                List<Point> points = entry.getValue();
                for (Point p : points) {
                    if (p != null) {
                        p.x -= minX;
                        p.y -= minY;
                    }
                }
            }

            for (List<Map.Entry<Link, List<Point>>> links : coLinks.values()) {
                //reduce links points identified by their Output Port
                reduceLinksPoints(links);
                //write reduced lists of points to links
                links.forEach(e -> e.getKey().setControlPoints(e.getValue()));
            }
            assert allLinksSet() : failedLinks();
            graph.setSize(dim);
        }

        private Dimension setStandAlones(Rectangle oldSize, Map<Vertex, Point> vertexPositions) {
            if (standAlones.isEmpty()) {
                return oldSize.getSize();
            }
            int layerOffset = LAYER_OFFSET * 3;
            int rightSide = oldSize.x + oldSize.width;
            int curY = oldSize.y - layerOffset - (layers.length == 0 ? 0 : layers[0].height);
            int curX = oldSize.x;
            int maxHeight = 0;
            for (LayoutNode n : standAlones) {
                int width = n.getWholeWidth();
                if (curX + width > rightSide) {
                    curX = oldSize.x;
                    curY -= (maxHeight + layerOffset);
                    maxHeight = 0;
                }
                Vertex v = n.vertex;
                assert !vertexPositions.containsKey(v);
                vertexPositions.put(v, new Point(curX, curY));
                curX += width + X_OFFSET;
                maxHeight = Math.max(maxHeight, n.getWholeHeight());
            }
            return new Dimension(oldSize.width, oldSize.height + oldSize.y - curY);
        }

        private StringBuilder failedLinks() {
            final StringBuilder sb = new StringBuilder();
            // filter self-edges and invisible edges
            graph.getLinks().stream().filter(l -> l.getControlPoints().size() < 2 && l.getFrom().getVertex() != l.getTo().getVertex() && l.getTo().getVertex().isVisible() && l.getFrom().getVertex().isVisible()).forEach(l -> sb.append(l).append("\n"));
            return sb;
        }

        private boolean allLinksSet() {
            Link tst = graph.getLinks().stream().findAny().orElse(null);//orElse no links
            if (tst == null || tst.getControlPoints() != tst.getControlPoints()) {
                //LinkWrapper doesn't use computed edges (always returns new List)
                return true;
            }
            // filter self-edges and invisible edges
            return graph.getLinks().stream().filter(l -> l.getFrom().getVertex() != l.getTo().getVertex() && l.getTo().getVertex().isVisible() && l.getFrom().getVertex().isVisible()).map(l -> l.getControlPoints().size()).allMatch(s -> s > 1);
        }

        private class ReductionEntry {

            final Point lastPoint;
            final int nextPointIndex;
            final Point nextPoint;
            final List<Point> reducedPoints;
            final List<Map.Entry<Link, List<Point>>> entries;

            public ReductionEntry(Point lastPoint, int nextPointIndex, Point nextPoint, List<Map.Entry<Link, List<Point>>> entries, List<Point> reducedPoints) {
                this.lastPoint = lastPoint;
                this.nextPointIndex = nextPointIndex;
                this.nextPoint = nextPoint;
                this.entries = entries;
                this.reducedPoints = reducedPoints;
            }
        }

        /**
         * This algorithm reduces number of points on correlating edges to their
         * identities and removes any unnecessary points along the edges.
         * <p>
         * Algorithm expects Links to begin at the same point. Reduced points
         * are written back to its corresponding Map.Entry. Middle points in
         * line are omitted. Equal points are replaced by single identity.
         *
         * @param links List of Map.Entries of Points on Link.
         */
        private void reduceLinksPoints(List<Map.Entry<Link, List<Point>>> links) {
            Point firstPoint = links.get(0).getValue().get(0);
            ArrayList<Point> points = new ArrayList<>();
            //first Point is always the same
            points.add(firstPoint);
            //Avoiding SOE by not using recursion
            Queue<ReductionEntry> tasks = new ArrayDeque<>();
            //prepare entries for reduction
            tasks.addAll(makeReductionEntries(links, points, firstPoint, 0));
            while (!tasks.isEmpty()) {
                tasks.addAll(reducePoints(tasks.remove()));
            }
            //overwrite Points at same place by "identity"
            //needed for ClusterNodes setPosition() as it moves points by "identity!
            for (Map.Entry<Link, List<Point>> pnts : links) {
                List<Point> newPoints = new ArrayList<>(pnts.getValue().size());
                for (Point p : pnts.getValue()) {
                    newPoints.add(pointsIdentity.computeIfAbsent(p, x -> x));
                }
                pnts.setValue(newPoints);
            }
        }

        /**
         * Creates List of ReductionEntries, for the algorithm to process. This
         * method creates separate ReductionEntries for edges that has same
         * direction. Also this method writes reduced points back to Map.Entry.
         *
         * @param entries        List of mapping of Link to its points.
         * @param reducedPoints  Current already reduced points.
         * @param lastPoint      Last Point that was saved as reduced Point.
         * @param lastPointIndex Last Point index in unreduced Points list.
         * @return List of ReductionEntries.
         */
        private List<ReductionEntry> makeReductionEntries(List<Map.Entry<Link, List<Point>>> entries, List<Point> reducedPoints, Point lastPoint, int lastPointIndex) {
            assert assertCorrectPointAtIndex(entries, reducedPoints, lastPoint, lastPointIndex);
            int nextIndex = lastPointIndex + 1;
            Map<Point, List<Map.Entry<Link, List<Point>>>> nexts = new HashMap<>();
            //separate edges by their direction and write back finished edges.
            for (Map.Entry<Link, List<Point>> entry : entries) {
                List<Point> controlPoints = entry.getValue();
                if (controlPoints.size() > nextIndex) {
                    //collect links by their next points (separate branches)
                    nexts.computeIfAbsent(controlPoints.get(nextIndex), p -> new ArrayList<>()).add(entry);
                } else {
                    assert reducedPoints.size() > 1;
                    //write complete reduced points to its Map.Entry
                    entry.setValue(reducedPoints);
                }
            }
            //collect edges to ReductionEntries (copy reduced Points list if there is branching)
            boolean branching = nexts.size() > 1;
            return nexts.entrySet().stream().map(e
                            -> new ReductionEntry(lastPoint, nextIndex, e.getKey(), e.getValue(),
                            branching ? new ArrayList<>(reducedPoints) : reducedPoints))
                    .collect(Collectors.toList());
        }

        /**
         * Returns if points are in line.
         *
         * @return {@code true} if points are in line.
         */
        private boolean pointsInLine(Point p1, Point p2, Point p3) {
            return p1 != null && p2 != null && p3 != null
                    // (p1.x-p2.x)/(p1.y-p2.y)==(p2.x-p3.x)/(p2.y-p3.y); 
                    // Rearanged to avoid division by zero.
                    && (p1.x - p2.x) * (p2.y - p3.y) == (p1.y - p2.y) * (p2.x - p3.x);
        }

        /**
         * Method processes ReductionEntry to minimize Points on correlating
         * edges. Algorithm expects that current points are the same for all
         * edges. (lastP-currentP-nextP)
         * <p>
         * It then scans for next point and determines if edges branches, turns
         * or stays at line. If edges stays at line, it skips current Point and
         * moves to next. If edges turns, it saves current Point as last Point
         * and scans again. If edges branches it just saves current point and
         * let makeReductionEntries() separate the branches.
         *
         * @param task
         * @return List of ReductionEntries for further reduction.
         */
        private List<ReductionEntry> reducePoints(ReductionEntry task) {
            //lastPoint is already saved in reduced edges
            Point lastPoint = task.lastPoint;
            //currentPoint can be omited as middle point if in line with lastPoint and nextPoint
            Point currentPoint = task.nextPoint;
            int currentIndex = task.nextPointIndex;
            //null must stay where it is, save and move on
            if (currentPoint == null || lastPoint == null) {
                task.reducedPoints.add(currentPoint);
                return makeReductionEntries(task.entries, task.reducedPoints, currentPoint, currentIndex);
            }
            List<Point> points = task.entries.get(0).getValue();
            //if we are processing multiple edges at once - check for branching
            boolean multiple = task.entries.size() > 1;
            //process edges till we are done or we found branching (only single edges ends)
            while (points.size() > currentIndex + 1) {
                final int nextIndex = currentIndex + 1;
                final Point nextPoint = points.get(nextIndex);
                //branching check (next points of all edges are equal)
                if (multiple && !task.entries.stream().map(Map.Entry::getValue).allMatch(pts -> Objects.equals(nextPoint, pts.get(nextIndex)))) {
                    break;
                }
                //if edges turns save current point as last point (turning point)
                if (!pointsInLine(lastPoint, currentPoint, nextPoint)) {
                    task.reducedPoints.add(currentPoint);
                    lastPoint = currentPoint;
                }
                //move points by a step
                currentPoint = nextPoint;
                currentIndex = nextIndex;
            }
            task.reducedPoints.add(currentPoint);
            //prepare branched or finished edges for future processing or writeback
            return makeReductionEntries(task.entries, task.reducedPoints, currentPoint, currentIndex);
        }

        private boolean assertCorrectPointAtIndex(List<Map.Entry<Link, List<Point>>> entries, List<Point> reducedPoints, Point lastPoint, int lastPointIndex) {
            //assert that last point in reduced points is indeed the lastPoint (it was already added to list)
            if (!Objects.equals(reducedPoints.get(reducedPoints.size() - 1), lastPoint)) {
                return false;
            }
            //assert that all entries has the lastPoint at lastPointIndex position (no stray edges)
            for (Map.Entry<Link, List<Point>> entry : entries) {
                if (!Objects.equals(entry.getValue().get(lastPointIndex), lastPoint)) {
                    return false;
                }
            }
            return true;
        }

        private List<Point> makeLongEnding(LayoutEdge e) {
            List<Point> points = new ArrayList<>();
            if (!reversedLinks.contains(e.link)) {
                points.add(new Point(e.getStartPoint(), e.from.getBottom()));
                makeLongEndingStart(points);
                points.add(null);
                makeLongEndingEnd(points, new Point(e.getEndPoint(), e.to.y));
            } else {
                int diffX = e.from.getLeftSide();
                int diffY = e.from.y;
                for (Point p : reversedLinkStartPoints.get(e.link)) {
                    points.add(new Point(p.x + diffX, p.y + diffY));
                }
                Collections.reverse(points);
                points.add(new Point(points.get(points.size() - 1).x, diffY + e.from.height));
                makeLongEndingStart(points);
                points.add(null);
                List<Point> ends = reversedLinkEndPoints.get(e.link);
                diffX = e.to.getLeftSide();
                diffY = e.to.y;
                makeLongEndingEnd(points, new Point(ends.get(0).x + diffX, diffY));
                for (Point p : ends) {
                    points.add(new Point(p.x + diffX, p.y + diffY));
                }
                Collections.reverse(points);
            }
            return points;
        }

        private void makeLongEndingStart(List<Point> points) {
            Point last = points.get(points.size() - 1);
            points.add(last = new Point(last.x, last.y + addition));
            points.add(last = new Point(last.x + addition, last.y + addition));
            points.add(new Point(last.x, last.y + (addition * 2)));
        }

        private void makeLongEndingEnd(List<Point> points, Point last) {
            points.add(last = new Point(last.x - addition, last.y - (addition * 3)));
            points.add(last = new Point(last.x, last.y + addition));
            points.add(last = new Point(last.x + addition, last.y + addition));
            points.add(new Point(last.x, last.y + addition));
        }

        @Override
        protected void printStatistics() {
            System.out.println("Number of nodes: " + nodes.size());
            int edgeCount = 0;
            for (LayoutNode n : nodes) {
                edgeCount += n.succs.size();
            }
            System.out.println("Number of edges: " + edgeCount);
            System.out.println("Number of points: " + pointCount);
        }
    }

    private class AssignXCoordinates extends AlgorithmPart {

        private int[][] space;
        private LayoutNode[][] downProcessingOrder;
        private LayoutNode[][] upProcessingOrder;
        private LayoutNode[][] bothProcessingOrder;

        private Map<LayoutNode, NodeRow> dangling = new HashMap<>();

        private void initialPositions() {
            for (LayoutNode n : nodes) {
                n.x = space[n.layer][n.pos];
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void createArrays() {
            space = new int[layers.length][];
            if (isDefaultLayout || !setting.get(Boolean.class, BOTH_SORT)) {
                downProcessingOrder = new LayoutNode[layers.length][];
                upProcessingOrder = new LayoutNode[layers.length][];
            } else {
                bothProcessingOrder = new LayoutNode[layers.length][];
            }
            Comparator<LayoutNode> upComparer, downComparer, bothComparer;
            upComparer = NODE_PROCESSING_DUMMY_UP_COMPARATOR;
            downComparer = NODE_PROCESSING_DUMMY_DOWN_COMPARATOR;
            bothComparer = NODE_PROCESSING_DUMMY_BOTH_COMPARATOR;
            if (isDefaultLayout) {
                //leave default sort
            } else if (setting.get(Boolean.class, REVERSE_SORT)) {
                if (setting.get(Boolean.class, DUMMY_FIRST_SORT)) {
                    upComparer = NODE_PROCESSING_DUMMY_UP_REVERSE_COMPARATOR;
                    downComparer = NODE_PROCESSING_DUMMY_DOWN_REVERSE_COMPARATOR;
                    bothComparer = NODE_PROCESSING_DUMMY_BOTH_REVERSE_COMPARATOR;
                } else {
                    upComparer = NODE_PROCESSING_UP_REVERSE_COMPARATOR;
                    downComparer = NODE_PROCESSING_DOWN_REVERSE_COMPARATOR;
                    bothComparer = NODE_PROCESSING_BOTH_REVERSE_COMPARATOR;
                }
            } else if (!setting.get(Boolean.class, DUMMY_FIRST_SORT)) {
                upComparer = NODE_PROCESSING_UP_COMPARATOR;
                downComparer = NODE_PROCESSING_DOWN_COMPARATOR;
                bothComparer = NODE_PROCESSING_BOTH_COMPARATOR;
            }

            for (int i = 0; i < layers.length; i++) {
                LayoutLayer layer = layers[i];
                int[] layerSpace = space[i] = new int[layer.size()];
                boolean visible = layer.isVisible();
                int curX = 0;
                for (int y = 0; y < layer.size(); ++y) {
                    layerSpace[y] = curX;
                    LayoutNode node = layer.get(y);
                    if (!visible) {
                        node.width = 0;
                        node.xOffset = 0;
                    } else {
                        curX += node.getWholeWidth() + (node.isDummy() ? X_OFFSET : offset);
                    }
                }

                if (isDefaultLayout || !setting.get(Boolean.class, BOTH_SORT)) {
                    downProcessingOrder[i] = layer.toArray(new LayoutNode[layer.size()]);
                    upProcessingOrder[i] = layer.toArray(new LayoutNode[layer.size()]);
                    Arrays.sort(downProcessingOrder[i], downComparer);
                    Arrays.sort(upProcessingOrder[i], upComparer);
                } else {
                    bothProcessingOrder[i] = layer.toArray(new LayoutNode[layer.size()]);
                    Arrays.sort(bothProcessingOrder[i], bothComparer);
                }
            }
        }

        @Override
        protected void run() {
            createArrays();
            initialPositions();

            for (int i = 0; i < (isDefaultLayout ? SWEEP_ITERATIONS : setting.get(Integer.class, X_ASSIGN_SWEEP_COUNT)); i++) {
                sweepDown();
                sweepUp();
            }
            sweepDown();
            if (isDefaultLayout || !setting.get(Boolean.class, LAST_DOWN_SWEEP)) {
                sweepUp();
            }
        }

        private int getVisibleLayer(int start, boolean up) {
            if (up) {
                for (int i = start - 1; i >= 0; --i) {
                    if (layers[i].isVisible()) {
                        return i;
                    }
                }
            } else {
                for (int i = start + 1; i < layers.length; ++i) {
                    if (layers[i].isVisible()) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public List<Integer> getOptimalPositions(LayoutEdge edge, int layer, List<Integer> vals, int correction, boolean up) {
            if (up) {
                if (edge.from.layer <= layer) {
                    if (!dangling.containsKey(edge.from)) {
                        vals.add(edge.getStartPoint() - correction);
                    }
                } else if (edge.from.isDummy()) {
                    edge.from.preds.forEach(x -> getOptimalPositions(x, layer, vals, correction, up));
                }
            } else {
                if (edge.to.layer >= layer) {
                    if (!dangling.containsKey(edge.to)) {
                        vals.add(edge.getEndPoint() - correction);
                    }
                } else if (edge.to.isDummy()) {
                    edge.to.succs.forEach(x -> getOptimalPositions(x, layer, vals, correction, up));
                }
            }
            return vals;
        }

        private int calculateOptimalDown(LayoutNode n, LayoutNode last) {
            List<Integer> values = new ArrayList<>();
            int layer = getVisibleLayer(n.layer, true);

            if (n.preds.stream().anyMatch(x -> x.vip)) {
                for (LayoutEdge e : n.preds) {
                    if (e.vip) {
                        values = getOptimalPositions(e, layer, values, e.relativeTo, true);
                    }
                }
            } else {
                for (LayoutEdge e : n.preds) {
                    values = getOptimalPositions(e, layer, values, e.relativeTo, true);
                }
            }
            return median(values, n, last, true);
        }

        private int calcLayerCenter(LayoutLayer layer) {
            LayoutNode last = layer.get(layer.size() - 1);
            return (layer.get(0).x + last.getRightSide()) / 2;
        }

        private int calcClosestPosition(LayoutNode n, LayoutNode last, boolean up) {
            assert n != null;
            return last == null ? calcLastLayerCenter(n, up) : last.x;
        }

        public int getExpectedRelativePosition(LayoutLayer layer, LayoutNode n) {
            assert layer.get(n.pos) == n;
            int x = 0;
            for (int i = 0; i < n.pos; ++i) {
                x += layer.get(i).getWholeWidth() + offset;
            }
            return x;
        }

        private int calcLastLayerCenter(LayoutNode n, boolean up) {
            if (up) {
                for (int i = n.layer - 1; i >= 0; --i) {
                    if (layers[i].isVisible() && (dangling.isEmpty() || layers[i].stream().allMatch(node -> !dangling.containsKey(node)))) {
                        return (calcLayerCenter(layers[i]) - layers[n.layer].getMinimalWidth() / 2) + getExpectedRelativePosition(layers[n.layer], n);
                    }
                }
            } else {
                for (int i = n.layer + 1; i < layers.length; ++i) {
                    if (layers[i].isVisible() && (dangling.isEmpty() || layers[i].stream().allMatch(node -> !dangling.containsKey(node)))) {
                        return (calcLayerCenter(layers[i]) - layers[n.layer].getMinimalWidth() / 2) + getExpectedRelativePosition(layers[n.layer], n);
                    }
                }
            }
            return n.x;
        }

        private int calculateOptimalUp(LayoutNode n, LayoutNode last) {
            List<Integer> values = new ArrayList<>();
            int layer = getVisibleLayer(n.layer, false);

            if (isDefaultLayout || !setting.get(Boolean.class, OPTIMAL_UP_VIP)) {
                for (LayoutEdge e : n.succs) {
                    if (e.vip) {
                        values = getOptimalPositions(e, layer, new ArrayList<>(), e.relativeFrom, false);
                        break;
                    }
                    values = getOptimalPositions(e, layer, values, e.relativeFrom, false);
                }
            } else {
                if (n.succs.stream().anyMatch(x -> x.vip)) {
                    for (LayoutEdge e : n.succs) {
                        if (e.vip) {
                            values = getOptimalPositions(e, layer, values, e.relativeFrom, false);
                        }
                    }
                } else {
                    for (LayoutEdge e : n.succs) {
                        values = getOptimalPositions(e, layer, values, e.relativeFrom, false);
                    }
                }
            }
            return median(values, n, last, false);
        }

        private int median(List<Integer> values, LayoutNode n, LayoutNode last, boolean up) {
            if (values.isEmpty()) {
                if (isDefaultLayout || !setting.get(Boolean.class, SQUASH_POSITION)) {
                    return n.x;
                } else {
                    return calcClosestPosition(n, last, up);
                }
            }
            if (setting.get(Boolean.class, CENTER_SIMPLE_NODES) && !n.isDummy() && values.size() == 1 && (up ? n.preds.size() == 1 : n.succs.size() == 1) && !(n.vertex instanceof ClusterSlotNode)) {
                LayoutNode node;
                if (up) {
                    node = n.preds.get(0).from;
                } else {
                    node = n.succs.get(0).to;
                }
                if (!(node.vertex instanceof ClusterSlotNode) && (up ? node.succs.size() == 1 : node.preds.size() == 1)) {
                    return node.x + ((node.getWholeWidth() - n.getWholeWidth()) / 2);
                }
            }
            if (!isDefaultLayout && setting.get(Boolean.class, MEAN_NOT_MEDIAN)) {
                return values.stream().reduce(0, Integer::sum) / values.size();
            }
            values.sort(Integer::compare);
            if (values.size() % 2 == 0) {
                return (values.get(values.size() / 2 - 1) + values.get(values.size() / 2)) / 2;
            } else {
                return values.get(values.size() / 2);
            }
        }

        private void sweepUp() {
            LayoutNode[][] chosenOrder;
            if (isDefaultLayout || !setting.get(Boolean.class, BOTH_SORT)) {
                chosenOrder = upProcessingOrder;
            } else {
                chosenOrder = bothProcessingOrder;
            }
            for (int i = layers.length - 2; i >= 0; i--) {
                NodeRow r = new NodeRow(space[i]);
                LayoutNode last = null;
                for (LayoutNode n : chosenOrder[i]) {
                    if (!isDefaultLayout && isDelayDanglingNodes && ((n.succs.isEmpty() && !n.preds.isEmpty()) || (!n.succs.isEmpty() && n.succs.stream().allMatch(e -> dangling.containsKey(e.to))))) {
                        dangling.put(n, r);
                    } else {
                        int optimal = calculateOptimalUp(n, last);
                        r.insert(n, optimal);
                        last = n;
                    }
                }
            }
            resolveDanglingNodes(true);
        }

        private void sweepDown() {
            LayoutNode[][] chosenOrder;
            if (isDefaultLayout || !setting.get(Boolean.class, BOTH_SORT)) {
                chosenOrder = downProcessingOrder;
            } else {
                chosenOrder = bothProcessingOrder;
            }
            for (int i = 1; i < layers.length; i++) {
                NodeRow r = new NodeRow(space[i]);
                LayoutNode last = null;
                for (LayoutNode n : chosenOrder[i]) {
                    if (!isDefaultLayout && isDelayDanglingNodes && ((n.preds.isEmpty() && !n.succs.isEmpty()) || (!n.preds.isEmpty() && n.preds.stream().allMatch(e -> dangling.containsKey(e.from))))) {
                        dangling.put(n, r);
                    } else {
                        int optimal = calculateOptimalDown(n, last);
                        r.insert(n, optimal);
                        last = n;
                    }
                }
            }
            resolveDanglingNodes(false);
        }

        private void resolveDanglingNodes(boolean up) {
            boolean dir = up;
            boolean force = false;
            while (!dangling.isEmpty()) {
                int newResolved = 0;
                List<LayoutNode> nodes = new ArrayList<>(dangling.keySet());
                if (up) {
                    nodes.sort(DANGLING_UP_NODE_COMPARATOR);
                    for (LayoutNode n : nodes) {
                        NodeRow r = dangling.get(n);
                        if (r != null && (!(n.preds.isEmpty() || n.preds.stream().allMatch(e -> dangling.containsKey(e.from))) || (force && n.preds.isEmpty()))) {
                            int optimal = calculateOptimalDown(n, null);
                            r.insert(n, optimal);
                            dangling.remove(n);
                            newResolved++;
                        }
                    }
                } else {
                    nodes.sort(DANGLING_DOWN_NODE_COMPARATOR);
                    for (LayoutNode n : nodes) {
                        NodeRow r = dangling.get(n);
                        if (r != null && (!(n.succs.isEmpty() || n.succs.stream().allMatch(e -> dangling.containsKey(e.to))) || (force && n.succs.isEmpty()))) {
                            int optimal = calculateOptimalUp(n, null);
                            r.insert(n, optimal);
                            dangling.remove(n);
                            newResolved++;
                        }
                    }
                }
                if (newResolved == 0 && !dangling.isEmpty()) {
                    up = !up;
                    force = up == dir;
                }
            }
            assert dangling.isEmpty() : "dangling size: " + dangling.size();
        }
    }

    /*
     *   Partialy implemented class, only needed methods
     */
    private class LayoutLayer extends ArrayList<LayoutNode> {

        private boolean visible = false;
        private int minimalWidth = -offset;
        private int height = 0;
        private int y = 0;

        public boolean isVisible() {
            return visible;
        }

        @Override
        public boolean addAll(Collection<? extends LayoutNode> c) {
            c.forEach((n) -> add0(n));
            return super.addAll(c);
        }

        private void add0(LayoutNode n) {
            visible = visible || (!n.isDummy());
            height = Math.max(height, n.getWholeHeight());
            minimalWidth += n.getWholeWidth() + offset;
        }

        @Override
        public boolean add(LayoutNode n) {
            add0(n);
            return super.add(n);
        }

        private boolean remove0(LayoutNode n) {
            minimalWidth -= n.getWholeWidth() + offset;
            return true;
        }

        @Override
        public boolean remove(Object o) {
            return (o instanceof LayoutNode) && super.remove(o) && remove0((LayoutNode) o);
        }

        public int getBottom() {
            return y + height;
        }

        public int getMinimalWidth() {
            return minimalWidth;
        }

        public int getActualWidth() {
            LayoutNode n = get(size() - 1);
            return (n.x + n.width) - get(0).x;
        }

        public void refresh() {
            visible = false;
            minimalWidth = -offset;
            height = 0;
            y = Integer.MAX_VALUE;
            for (LayoutNode n : this) {
                y = Math.min(y, n.y - n.yOffset);
                visible = visible || (!n.isDummy());
                height = Math.max(height, n.getWholeHeight());
                minimalWidth += n.getWholeWidth() + offset;
            }
        }
    }

    private class NodeRow {

        private final TreeSet<LayoutNode> treeSet;
        private final int[] space;

        public NodeRow(int[] space) {
            treeSet = new TreeSet<>(NODE_POSITION_COMPARATOR);
            this.space = space;
        }

        public int offset(LayoutNode n1, LayoutNode n2) {
            int v1 = space[n1.pos] + n1.getWholeWidth();
            int v2 = space[n2.pos];
            return v2 - v1;
        }

        public void insert(LayoutNode n, int pos) {
            SortedSet<LayoutNode> headSet = treeSet.headSet(n);
            LayoutNode leftNeighbor;
            int minX = Integer.MIN_VALUE;
            if (!headSet.isEmpty()) {
                leftNeighbor = headSet.last();
                minX = leftNeighbor.getRightSide() + offset(leftNeighbor, n);
            }

            if (pos < minX) {
                n.x = minX;
            } else {
                LayoutNode rightNeighbor;
                SortedSet<LayoutNode> tailSet = treeSet.tailSet(n);
                int maxX = Integer.MAX_VALUE;
                if (!tailSet.isEmpty()) {
                    rightNeighbor = tailSet.first();
                    maxX = rightNeighbor.x - offset(n, rightNeighbor) - n.getWholeWidth();
                }

                if (pos > maxX) {
                    n.x = maxX;
                } else {
                    n.x = pos;
                }
                assert minX <= maxX : minX + " vs " + maxX;
            }
            treeSet.add(n);
        }
    }

    private class CrossingReduction extends AlgorithmPart {

        List<LayoutNode>[] upCrossing;
        List<LayoutNode>[] downCrossing;
        int[] centerDiffs;
        private final boolean routingCrossing;
        private LayoutLayer[] toMove;

        public CrossingReduction() {
            this(false);
        }

        public CrossingReduction(boolean routingCrossing) {
            this.routingCrossing = routingCrossing;
        }

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer < layerCount;
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void createLayers() {
            if (!isDefaultLayout && setting.get(Boolean.class, CROSSING_SORT) && !isCrossingByConnDiff) {
                upCrossing = new ArrayList[layerCount];
                downCrossing = new ArrayList[layerCount];
            }
            if (!routingCrossing) {
                layers = new LayoutLayer[layerCount];
                for (int i = 0; i < layerCount; i++) {
                    layers[i] = new LayoutLayer();
                }
                if (!isDefaultLayout && setting.get(Boolean.class, NO_CROSSING_LAYER_REASSIGN)) {
                    for (LayoutNode n : nodes) {
                        layers[n.layer].add(n);
                    }
                } else {
                    // Generate initial ordering
                    HashSet<LayoutNode> visited = new HashSet<>();
                    for (LayoutNode n : nodes) {
                        if (n.layer == 0) {
                            layers[0].add(n);
                            visited.add(n);
                        } else if (n.preds.isEmpty()) {
                            layers[n.layer].add(n);
                            visited.add(n);
                        }
                    }

                    for (int i = 0; i < layers.length - 1; i++) {
                        for (LayoutNode n : layers[i]) {
                            for (LayoutEdge e : n.succs) {
                                if (!visited.contains(e.to)) {
                                    visited.add(e.to);
                                    assert e.to.layer == i + 1;
                                    layers[i + 1].add(e.to);
                                }
                            }
                        }
                    }
                }
            }
            toMove = !routingCrossing ? layers : Arrays.stream(layers).map(l -> l.stream().filter(n -> n.isDummy()).collect(LayoutLayer::new, LayoutLayer::add, LayoutLayer::addAll)).toArray(LayoutLayer[]::new);
            if (!isDefaultLayout && setting.get(Boolean.class, CROSSING_SORT) && !isCrossingByConnDiff) {
                for (int i = 0; i < layerCount; ++i) {
                    upCrossing[i] = new ArrayList<>(layers[i]);
                    downCrossing[i] = new ArrayList<>(layers[i]);
                    upCrossing[i].sort(UP_CROSSING_COMPARATOR);
                    downCrossing[i].sort(DOWN_CROSSING_COMPARATOR);
                }
            }
        }

        @Override
        protected void run() {
            createLayers();
            if (setting.get(Boolean.class, IRRELEVANT_LAYOUT_CODE)) {
                updatePositions();
            }
            //will be reassigned
            initX();
            // Optimize
            int sweepCount = isDefaultLayout ? CROSSING_ITERATIONS : setting.get(Integer.class, CROSSING_SWEEP_COUNT);
            for (int i = 0; i < sweepCount; i++) {
                downSweep();
                upSweep();
            }
            if (isDefaultLayout || !setting.get(Boolean.class, LAST_UP_CROSSING_SWEEP)) {
                downSweep();
            }
            if (setting.get(Boolean.class, IRRELEVANT_LAYOUT_CODE)) {
                initX();
            }
            updatePositions();
        }

        private void initX() {
            if (!isDefaultLayout && setting.get(Boolean.class, CENTER_CROSSING_X)) {
                createCenterDiffs();
            }
            for (int i = 0; i < layers.length; i++) {
                updateXOfLayer(i);
            }
        }

        private void createCenterDiffs() {
            if (centerDiffs != null) {
                return;
            }
            int max = 0;
            centerDiffs = new int[layerCount];
            for (int i = 0; i < layers.length; ++i) {
                int length = 0;
                for (LayoutNode n : layers[i]) {
                    length += n.getWholeWidth() + offset;
                }
                centerDiffs[i] = length;
                max = Math.max(length, max);
            }
            for (int i = 0; i < centerDiffs.length; ++i) {
                centerDiffs[i] = (max - centerDiffs[i]) / 2;
            }
        }

        private void updateXOfLayer(int index) {
            if (!isDefaultLayout && setting.get(Boolean.class, CROSS_RESET_X_FROM_NODE)) {
                if (setting.get(Boolean.class, CROSS_RESET_X_FROM_MIDDLE)) {
                    updateFromMiddle(index);
                } else {
                    updateFromLeft(index);
                }
            } else {
                int x = (isDefaultLayout || !setting.get(Boolean.class, CENTER_CROSSING_X)) ? 0 : centerDiffs[index];
                for (LayoutNode n : layers[index]) {
                    n.x = x;
                    x += n.getWholeWidth() + offset;
                }
            }
        }

        private void updateFromMiddle(int index) {
            LayoutLayer layer = layers[index];
            int middleIndex = layer.size() / 2;
            LayoutNode n = layer.get(middleIndex);
            int add = (isDefaultLayout || !setting.get(Boolean.class, CENTER_CROSSING_X)) ? 0 : (centerDiffs[index] / (layer.size() + 1));
            int x = n.x;
            for (int i = middleIndex - 1; i >= 0; --i) {
                n = layer.get(i);
                x = n.x = Math.min(n.x, x - n.getWholeWidth() - offset - add);
            }
            n = layer.get(middleIndex);
            x = n.getRightSide() + offset;
            for (int i = middleIndex + 1; i < layer.size(); ++i) {
                n = layer.get(i);
                n.x = Math.max(n.x, x + add);
                x = n.getRightSide() + offset;
            }
        }

        private void updateFromLeft(int index) {
            LayoutLayer layer = layers[index];
            LayoutNode n = layer.get(0);
            int add = (isDefaultLayout || !setting.get(Boolean.class, CENTER_CROSSING_X)) ? 0 : (centerDiffs[index] / (layer.size() + 1));
            int x = n.getRightSide() + offset;
            for (int i = 0; i < layer.size(); ++i) {
                n = layer.get(i);
                n.x = Math.max(n.x, x + add);
                x = n.getRightSide() + offset;
            }
        }

        private void updatePositions() {
            for (int i = 0; i < layerCount; ++i) {
                updateLayerPositions(i);
            }
        }

        private void updateLayerPositions(int index) {
            int z = 0;
            for (LayoutNode n : layers[index]) {
                n.pos = z;
                z++;
            }
        }

        private void changeXOfLayer(int index) {
            float factor = !isDefaultLayout && setting.get(Boolean.class, CROSS_RESET_X_FROM_MIDDLE) ? setting.get(Float.class, CROSS_FACTOR) : 1;
            for (LayoutNode n : toMove[index]) {
                n.x += n.crossingNumber * factor;
            }
        }

        private void downSweep() {
            if (!isDefaultLayout && setting.get(Boolean.class, CROSS_POSITION_DURING)) {
                updatePositions();
                new AssignXCoordinates().start();
            }
            // Downsweep
            for (int i = 0; i < layerCount; i++) {
                for (LayoutNode n : toMove[i]) {
                    n.loadCrossingNumber(false);
                }
                if (!isDefaultLayout && isCrossingByConnDiff) {
                    changeXOfLayer(i);
                    layers[i].sort((n1, n2) -> Integer.compare(n1.getCenterX(), n2.getCenterX()));
                    updateXOfLayer(i);
                } else {
                    updateCrossingNumbers(i, true);
                    layers[i].sort(CROSSING_NODE_COMPARATOR);
                    updateXOfLayer(i);
                }
                if (setting.get(Boolean.class, IRRELEVANT_LAYOUT_CODE)) {
                    updateLayerPositions(i);
                }
            }
        }

        private void upSweep() {
            if (!isDefaultLayout && setting.get(Boolean.class, CROSS_POSITION_DURING)) {
                updatePositions();
                new AssignXCoordinates().start();
            }
            // Upsweep
            for (int i = layerCount - 1; i >= 0; i--) {
                for (LayoutNode n : toMove[i]) {
                    n.loadCrossingNumber(true);
                }

                if (!isDefaultLayout && isCrossingByConnDiff) {
                    changeXOfLayer(i);
                    layers[i].sort((n1, n2) -> Integer.compare(n1.getCenterX(), n2.getCenterX()));
                    updateXOfLayer(i);
                } else {
                    updateCrossingNumbers(i, false);
                    layers[i].sort(CROSSING_NODE_COMPARATOR);
                    updateXOfLayer(i);
                }
                if (setting.get(Boolean.class, IRRELEVANT_LAYOUT_CODE)) {
                    updateLayerPositions(i);
                }
            }
        }

        private void updateCrossingNumbers(int index, boolean down) {
            List<LayoutNode> layer;
            if (!isDefaultLayout && setting.get(Boolean.class, CROSSING_SORT)) {
                layer = down ? downCrossing[index] : upCrossing[index];
            } else {
                layer = layers[index];
            }
            boolean properCrossing = !isDefaultLayout && setting.get(Boolean.class, PROPER_CROSSING_CLOSEST_NODE);
            int diff = (!isDefaultLayout && setting.get(Boolean.class, UNKNOWN_CROSSING_NUMBER)) ? 1 : 0;
            LayoutNode prev;
            LayoutNode next;
            for (int i = 0; i < layer.size(); i++) {
                LayoutNode n = layer.get(i);
                if (down ? n.preds.isEmpty() : n.succs.isEmpty()) {
                    prev = null;
                    next = null;
                    if (properCrossing) {
                        prev = getPrevPositioned(layers[index], i);
                        next = getNextPositioned(layers[index], i);
                    } else {
                        if (i > 0) {
                            prev = layer.get(i - 1);
                        }
                        if (i < layer.size() - 1) {
                            next = layer.get(i + 1);
                        }
                    }

                    if (prev != null && next != null) {
                        n.crossingNumber = (prev.crossingNumber + next.crossingNumber) / 2;
                    } else if (prev != null) {
                        n.crossingNumber = prev.crossingNumber + diff;
                    } else if (next != null) {
                        n.crossingNumber = next.crossingNumber - diff;
                    }
                }
            }
        }

        private LayoutNode getPrevPositioned(List<LayoutNode> layer, int i) {
            LayoutNode node;
            while (--i >= 0) {
                node = layer.get(i);
                if (node.crossingNumber != 0) {
                    return node;
                }
            }
            return null;
        }

        private LayoutNode getNextPositioned(List<LayoutNode> layer, int i) {
            LayoutNode node;
            while (++i < layer.size()) {
                node = layer.get(i);
                if (node.crossingNumber != 0) {
                    return node;
                }
            }
            return null;
        }

        @Override
        public void postCheck() {
            HashSet<LayoutNode> visited = new HashSet<>();
            for (int i = 0; i < layers.length; i++) {
                for (LayoutNode n : layers[i]) {
                    assert !visited.contains(n);
                    assert n.layer == i;
                    visited.add(n);
                }
            }
            for (int i = 0; i < layers.length; i++) {
                assert !layers[i].isEmpty();
                for (LayoutNode n : layers[i]) {
                    assert n.layer == i;
                }
            }
        }
    }

    private class AssignYCoordinates extends AlgorithmPart {

        @Override
        protected void run() {
            final IntUnaryOperator layerDiff;
            if (isDefaultLayout || !setting.get(Boolean.class, SPAN_BY_ANGLE)) {
                layerDiff = (in) -> (int) (Math.sqrt(in) * 2);
            } else {
                final double coef = Math.tan(Math.toRadians(setting.get(Integer.class, MIN_EDGE_ANGLE)));
                layerDiff = (in) -> (int) (in * coef);
            }
            int curY = 0;
            for (LayoutLayer layer : layers) {
                if (layer.isVisible()) {
                    int maxHeight = layer.height;
                    layer.y = curY;
                    int maxXOffset = 0;
                    for (LayoutNode n : layer) {
                        n.y = curY;
                        if (!n.isDummy()) {
                            n.yOffset = (maxHeight - n.getWholeHeight()) / 2 + n.yOffset;
                            n.y += n.yOffset;
                            n.bottomYOffset = maxHeight - n.yOffset - n.height;
                        }
                        for (LayoutEdge e : n.getVisibleSuccs()) {
                            maxXOffset = Math.max(Math.abs(e.getStartPoint() - e.getEndPoint()), maxXOffset);
                        }
                    }
                    curY += maxHeight + Math.max(layerDiff.applyAsInt(maxXOffset), LAYER_OFFSET * 3);
                } else {
                    for (LayoutNode n : layer) {
                        n.height = 0;
                        n.y = curY;
                    }
                    layer.height = 0;
                }
            }
        }
    }

    private class CreateDummyNodes extends AlgorithmPart {

        private int oldNodeCount;

        @Override
        protected void preCheck() {
            for (LayoutNode n : nodes) {
                for (LayoutEdge e : n.succs) {
                    assert e.from != null;
                    assert e.from == n;
                    assert e.from.layer < e.to.layer;
                }

                for (LayoutEdge e : n.preds) {
                    assert e.to != null;
                    assert e.to == n;
                }
            }
        }

        @Override
        protected void run() {
            oldNodeCount = nodes.size();

            if (combine == Combine.SAME_OUTPUTS) {
                final boolean noDummyLongEdges = setting.get(Boolean.class, NO_DUMMY_LONG_EDGES);
                HashMap<Integer, List<LayoutEdge>> portHash = new HashMap<>();
                ArrayList<LayoutNode> currentNodes = new ArrayList<>(nodes);
                for (LayoutNode n : currentNodes) {
                    portHash.clear();

                    ArrayList<LayoutEdge> succs = new ArrayList<>(n.succs);
                    HashMap<Integer, LayoutNode> topNodeHash = new HashMap<>();
                    HashMap<Integer, HashMap<Integer, LayoutNode>> bottomNodeHash = new HashMap<>();
                    for (LayoutEdge e : succs) {
                        assert e.from.layer < e.to.layer;
                        if (e.from.layer != e.to.layer - 1) {
                            if (maxLayerLength != -1 && e.to.layer - e.from.layer > maxLayerLength && !setting.get(Boolean.class, DRAW_LONG_EDGES) /* && e.to.preds.size() > 1 && e.from.succs.size() > 1 */) {
                                assert maxLayerLength > 2;
                                e.to.preds.remove(e);
                                e.from.succs.remove(e);
                                if (isDefaultLayout || !noDummyLongEdges) {
                                    LayoutEdge topEdge;

                                    LayoutNode topNode = topNodeHash.get(e.relativeFrom);
                                    if (topNode == null) {
                                        topNode = new LayoutNode();
                                        topNode.layer = e.from.layer + 1;
                                        nodes.add(topNode);
                                        topNodeHash.put(e.relativeFrom, topNode);
                                        bottomNodeHash.put(e.relativeFrom, new HashMap<>());
                                    }
                                    topEdge = new LayoutEdge(e.from, topNode, e.relativeFrom, topNode.width / 2, e.link, e.vip);
                                    e.from.succs.add(topEdge);
                                    topNode.preds.add(topEdge);

                                    HashMap<Integer, LayoutNode> hash = bottomNodeHash.get(e.relativeFrom);

                                    LayoutNode bottomNode;
                                    if (hash.containsKey(e.to.layer)) {
                                        bottomNode = hash.get(e.to.layer);
                                    } else {
                                        bottomNode = new LayoutNode();
                                        bottomNode.layer = e.to.layer - 1;
                                        nodes.add(bottomNode);
                                        hash.put(e.to.layer, bottomNode);
                                    }

                                    LayoutEdge bottomEdge = new LayoutEdge(bottomNode, e.to, bottomNode.width / 2, e.relativeTo, e.link, e.vip);
                                    e.to.preds.add(bottomEdge);
                                    bottomNode.succs.add(bottomEdge);
                                } else {
                                    longEdges.add(e);
                                }
                            } else {
                                Integer i = e.relativeFrom;
                                if (!portHash.containsKey(i)) {
                                    portHash.put(i, new ArrayList<>());
                                }
                                portHash.get(i).add(e);
                            }
                        }
                    }

                    for (LayoutEdge e : succs) {
                        Integer i = e.relativeFrom;
                        if (portHash.containsKey(i)) {

                            List<LayoutEdge> list = portHash.get(i);
                            Collections.sort(list, LAYER_COMPARATOR);

                            if (list.size() == 1) {
                                processSingleEdge(list.get(0));
                            } else {

                                int maxLayer = list.get(list.size() - 1).to.layer;

                                int cnt = maxLayer - n.layer - 1;
                                LayoutEdge[] edges = new LayoutEdge[cnt];
                                LayoutNode[] nodes = new LayoutNode[cnt];

                                nodes[0] = new LayoutNode();
                                nodes[0].layer = n.layer + 1;
                                edges[0] = new LayoutEdge(n, nodes[0], i, nodes[0].width / 2, null, e.vip);
                                nodes[0].preds.add(edges[0]);
                                n.succs.add(edges[0]);
                                for (int j = 1; j < cnt; j++) {
                                    nodes[j] = new LayoutNode();
                                    nodes[j].layer = n.layer + j + 1;
                                    edges[j] = new LayoutEdge(nodes[j - 1], nodes[j], nodes[j - 1].width / 2, nodes[j].width / 2, null, e.vip);
                                    nodes[j - 1].succs.add(edges[j]);
                                    nodes[j].preds.add(edges[j]);
                                }
                                for (LayoutEdge curEdge : list) {
                                    assert curEdge.to.layer - n.layer - 2 >= 0;
                                    assert curEdge.to.layer - n.layer - 2 < cnt;
                                    LayoutNode anchor = nodes[curEdge.to.layer - n.layer - 2];
                                    for (int l = 0; l < curEdge.to.layer - n.layer - 1; ++l) {
                                        anchor = nodes[l];
                                    }
                                    anchor.succs.add(curEdge);
                                    curEdge.from = anchor;
                                    curEdge.relativeFrom = anchor.width / 2;
                                    n.succs.remove(curEdge);
                                }
                                if (!isDefaultLayout && setting.get(Boolean.class, NO_CROSSING_LAYER_REASSIGN)) {
                                    HierarchicalLayoutManager.this.nodes.addAll(Arrays.asList(nodes));
                                }
                            }
                            portHash.remove(i);
                        }
                    }
                }
            } else if (combine == Combine.SAME_INPUTS) {
                throw new UnsupportedOperationException("Currently not supported");
            } else {
                new ArrayList<>(nodes).forEach(n -> n.succs.forEach(this::processSingleEdge));
            }
        }

        private void processSingleEdge(LayoutEdge e) {
            LayoutNode n = e.from;
            if (e.to.layer > n.layer + 1) {
                LayoutEdge last = e;
                for (int i = n.layer + 1; i < last.to.layer; i++) {
                    last = addBetween(last, i);
                }
            }
        }

        private LayoutEdge addBetween(LayoutEdge e, int layer) {
            LayoutNode n = new LayoutNode();
            n.layer = layer;
            n.preds.add(e);
            nodes.add(n);
            LayoutEdge result = new LayoutEdge(n, e.to, n.width / 2, e.relativeTo, null, e.vip);
            n.succs.add(result);
            e.relativeTo = n.width / 2;
            e.to.preds.remove(e);
            e.to.preds.add(result);
            e.to = n;
            return result;
        }

        @Override
        public void printStatistics() {
            System.out.println("Dummy nodes created: " + (nodes.size() - oldNodeCount));
        }

        @Override
        public void postCheck() {
            ArrayList<LayoutNode> currentNodes = new ArrayList<>(nodes);
            for (LayoutNode n : currentNodes) {
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer == e.to.layer - 1;
                }
            }
        }
    }

    private class AssignLayers extends AlgorithmPart {

        Set<LayoutNode> checked = new HashSet<>();

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer == -1;
            }
        }

        @Override
        protected void run() {
            assignLayerDownwards();
            assignLayerUpwards();
            reassignInOutBlockNodes();
            if (!isDefaultLayout && setting.get(Boolean.class, DECREASE_LAYER_WIDTH_DEVIATION)) {
                reassignLayers();
            }
        }

        //ClusterSlotNodes needs to be at the edge of blocks.
        private void reassignInOutBlockNodes() {
            for (Vertex v : graph.getVertices()) {
                if (v instanceof ClusterSlotNode) {
                    LayoutNode n = vertexToLayoutNode.get(v);
                    if (((ClusterSlotNode) v).isInputSlot()) {
                        n.layer = 0;
                    } else {
                        n.layer = layerCount - 1;
                    }
                }
            }
        }

        private void assignLayerDownwards() {
            ArrayList<LayoutNode> hull = new ArrayList<>();
            for (LayoutNode n : nodes) {
                if (n.preds.isEmpty()) {
                    hull.add(n);
                    n.layer = 0;
                }
            }

            int z = 1;
            while (!hull.isEmpty()) {
                ArrayList<LayoutNode> newSet = new ArrayList<>();
                for (LayoutNode n : hull) {
                    for (LayoutEdge se : n.succs) {
                        LayoutNode s = se.to;
                        if (s.layer != -1) {
                            // This node was assigned before.
                        } else {
                            boolean unassignedPred = false;
                            for (LayoutEdge pe : s.preds) {
                                LayoutNode p = pe.from;
                                if (p.layer == -1 || p.layer >= z) {
                                    // This now has an unscheduled successor or a successor that was
                                    // scheduled only in this round.
                                    unassignedPred = true;
                                    break;
                                }
                            }

                            if (unassignedPred) {
                                // This successor node can not yet be assigned.
                            } else {
                                s.layer = z;
                                newSet.add(s);
                            }
                        }
                    }
                }

                hull = newSet;
                z++;
            }

            layerCount = z - 1;
            for (LayoutNode n : nodes) {
                n.layer = (layerCount - 1 - n.layer);
            }
        }

        private void assignLayerUpwards() {
            ArrayList<LayoutNode> hull = new ArrayList<>();
            for (LayoutNode n : nodes) {
                if (n.succs.isEmpty()) {
                    hull.add(n);
                } else {
                    n.layer = -1;
                }
            }

            int z = 1;
            while (!hull.isEmpty()) {
                ArrayList<LayoutNode> newSet = new ArrayList<>();
                for (LayoutNode n : hull) {
                    if (n.layer < z) {
                        for (LayoutEdge se : n.preds) {
                            LayoutNode s = se.from;
                            if (s.layer != -1) {
                                // This node was assigned before.
                            } else {
                                boolean unassignedSucc = false;
                                for (LayoutEdge pe : s.succs) {
                                    LayoutNode p = pe.to;
                                    if (p.layer == -1 || p.layer >= z) {
                                        // This now has an unscheduled successor or a successor that
                                        // was scheduled only in this round.
                                        unassignedSucc = true;
                                        break;
                                    }
                                }

                                if (unassignedSucc) {
                                    // This predecessor node can not yet be assigned.
                                } else {
                                    s.layer = z;
                                    newSet.add(s);
                                }
                            }
                        }
                    } else {
                        newSet.add(n);
                    }
                }

                hull = newSet;
                z++;
            }

            layerCount = z - 1;

            for (LayoutNode n : nodes) {
                n.layer = (layerCount - 1 - n.layer);
            }
        }

        private final Comparator<LayoutLayer> LAYER_WIDTH_COMPARATOR = (l1, l2) -> {
            int result = Integer.compare(l2.getMinimalWidth(), l1.getMinimalWidth());
            return result == 0 ? Integer.compare(l2.get(0).layer, l1.get(0).layer) : result;
        };
        private final Comparator<LayoutNode> NODE_WIDTH_COMPARATOR = (n1, n2) -> Integer.compare(n2.getWholeWidth(), n1.getWholeWidth());
        private final SortedSet<LayoutLayer> lay = new TreeSet<>(LAYER_WIDTH_COMPARATOR);

        private void reassignLayers() {
            layers = new LayoutLayer[layerCount];
            if (layerCount == 0) {
                return;
            }
            for (int i = 0; i < layerCount; i++) {
                layers[i] = new LayoutLayer();
            }
            for (LayoutNode n : nodes) {
                layers[n.layer].add(n);
            }
            lay.addAll(Arrays.asList(HierarchicalLayoutManager.this.layers));
            double avg = lay.stream().mapToInt(l -> l.getMinimalWidth()).sum() / layerCount;
            boolean up, down;
            LayoutNode node = null;
            final boolean isQuick = setting.get(Boolean.class, DECREASE_LAYER_WIDTH_DEVIATION_QUICK);
            while (!lay.isEmpty()) {
                if (cancelled.get()) {
                    return;
                }
                up = false;
                down = false;
                LayoutLayer layer = lay.first();
                if (layer.getMinimalWidth() < avg) {
                    break;
                }
                layer.sort(NODE_WIDTH_COMPARATOR);
                if (!setting.get(Boolean.class, DECREASE_LAYER_WIDTH_DEVIATION_UP)) {
                    for (LayoutNode n : layer) {
                        if ((down = canMoveDown(n)) || (up = canMoveUp(n))) {
                            break;
                        }
                        if (isQuick) {
                            break;
                        }
                    }
                    if (down) {
                        moveDown();
                        if (!isQuick) {
                            continue;
                        }
                    }
                    if (up) {
                        moveUp();
                        if (!isQuick) {
                            continue;
                        }
                    }
                } else {
                    for (LayoutNode n : layer) {
                        if ((up = canMoveUp(n)) || (down = canMoveDown(n))) {
                            break;
                        }
                        if (isQuick) {
                            break;
                        }
                    }
                    if (up) {
                        moveUp();
                        if (!isQuick) {
                            continue;
                        }
                    }
                    if (down) {
                        moveDown();
                        if (!isQuick) {
                            continue;
                        }
                    }
                }
                lay.remove(layer);
            }
        }

        private void moveUp() {
            Set<LayoutLayer> reassign = new HashSet<>();
            for (LayoutNode node : checked) {
                LayoutLayer rl = layers[node.layer];
                reassign.add(rl);
                lay.remove(rl);
                rl.remove(node);
                LayoutLayer al = layers[node.layer - 1];
                reassign.add(al);
                lay.remove(al);
                al.add(node);
                --node.layer;
            }
            for (LayoutLayer l : reassign) {
                lay.add(l);
            }
        }

        private void moveDown() {
            Set<LayoutLayer> reassign = new HashSet<>();
            for (LayoutNode node : checked) {
                LayoutLayer rl = layers[node.layer];
                reassign.add(rl);
                lay.remove(rl);
                rl.remove(node);
                LayoutLayer al = layers[node.layer + 1];
                reassign.add(al);
                lay.remove(al);
                al.add(node);
                ++node.layer;
            }
            for (LayoutLayer l : reassign) {
                lay.add(l);
            }
        }

        private boolean canMoveUp(LayoutNode node) {
            checked.clear();
            if (cantMoveUp(node)) {
                return false;
            }
            List<Integer> sizes = new ArrayList<>();
            getSizesUp(sizes);
            int layNr = sizes.size() + 1;
            int initLay = node.layer;
            int[] before = new int[layNr];
            for (int i = 0; i < layNr; ++i) {
                before[i] = layers[initLay - i].getMinimalWidth();
            }
            return improves(sizes, before);
        }

        private void getSizesUp(List<Integer> sizes) {
            assert !checked.isEmpty();
            List<LayoutNode> nodes = new ArrayList<>(checked);
            nodes.sort((n1, n2) -> Integer.compare(n2.layer, n1.layer));
            int first = nodes.get(0).layer;
            for (LayoutNode node : nodes) {
                int index = first - node.layer;
                if (sizes.size() == index) {
                    sizes.add(node.getWholeWidth() + offset);
                } else {
                    sizes.set(index, sizes.get(index) + node.getWholeWidth() + offset);
                }
            }
        }

        private boolean cantMoveUp(LayoutNode node) {
            Queue<LayoutNode> que = new ArrayDeque<>();
            que.add(node);
            while (!que.isEmpty()) {
                node = que.poll();
                if (checked.contains(node)) {
                    continue;
                }
                int layer = node.layer;
                if (layer == 0) {
                    return true;
                }
                if (!isDrawLongEdges && maxLayerLength >= 0) {
                    for (LayoutEdge e : node.succs) {
                        if (e.to.layer == layer + maxLayerLength) {
                            return true;
                        }
                    }
                }
                for (LayoutEdge e : node.preds) {
                    if (e.from.layer == layer - 1) {
                        que.add(e.from);
                    }
                }
                checked.add(node);
            }
            return false;
        }

        private boolean improves(List<Integer> others, int[] before) {
            int layNr = before.length;
            double avg = Arrays.stream(before).sum() / (double) layNr;
            double dev1 = 0;
            double tmp;
            for (int i : before) {
                tmp = i / avg;
                dev1 += tmp * tmp;
            }
            dev1 = Math.sqrt(dev1 / layNr);
            int[] after = new int[layNr];
            after[0] = before[0];
            double dev2 = 0;
            int dif;
            for (int i = 0; i < layNr - 1; ++i) {
                dif = others.get(i);
                after[i] -= dif;
                tmp = after[i] / avg;
                dev2 += tmp * tmp;
                after[i + 1] = before[i + 1] + dif;
            }
            tmp = after[layNr - 1] / avg;
            dev2 += tmp * tmp;
            dev2 = Math.sqrt(dev2 / layNr);
            return dev1 > dev2;
        }

        private boolean canMoveDown(LayoutNode node) {
            checked.clear();
            if (cantMoveDown(node)) {
                return false;
            }
            List<Integer> sizes = new ArrayList<>();
            getSizesDown(sizes);
            int layNr = sizes.size() + 1;
            int initLay = node.layer;
            int[] before = new int[layNr];
            for (int i = 0; i < layNr; ++i) {
                before[i] = layers[initLay + i].getMinimalWidth();
            }
            return improves(sizes, before);
        }

        private void getSizesDown(List<Integer> sizes) {
            assert !checked.isEmpty();
            List<LayoutNode> nodes = new ArrayList<>(checked);
            nodes.sort((n1, n2) -> Integer.compare(n1.layer, n2.layer));
            int first = nodes.get(0).layer;
            for (LayoutNode node : nodes) {
                int index = node.layer - first;
                if (sizes.size() == index) {
                    sizes.add(node.getWholeWidth() + offset);
                } else {
                    sizes.set(index, sizes.get(index) + node.getWholeWidth() + offset);
                }
            }
        }

        private boolean cantMoveDown(LayoutNode node) {
            Queue<LayoutNode> que = new ArrayDeque<>();
            que.add(node);
            while (!que.isEmpty()) {
                node = que.poll();
                if (checked.contains(node)) {
                    continue;
                }
                int layer = node.layer;
                if (layer == layerCount - 1) {
                    return true;
                }
                if (!isDrawLongEdges && maxLayerLength >= 0) {
                    for (LayoutEdge e : node.preds) {
                        if (e.from.layer == layer - maxLayerLength) {
                            return true;
                        }
                    }
                }
                for (LayoutEdge e : node.succs) {
                    if (e.to.layer == layer + 1) {
                        que.add(e.to);
                    }
                }
                checked.add(node);
            }
            return false;
        }

        @Override
        public void postCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer >= 0;
                assert n.layer < layerCount;
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer < e.to.layer;
                }
            }
        }
    }

    private class ReverseEdges extends AlgorithmPart {

        private HashSet<LayoutNode> visited;
        private HashSet<LayoutNode> active;

        @Override
        protected void run() {
            // Remove self-edges
            for (LayoutNode node : nodes) {
                ArrayList<LayoutEdge> succs = new ArrayList<>(node.succs);
                for (LayoutEdge e : succs) {
                    assert e.from == node;
                    if (e.to == node) {
                        node.succs.remove(e);
                        node.preds.remove(e);
                    }
                }
            }
            if (setting.get(Boolean.class, STANDALONES)) {
                for (Iterator<LayoutNode> it = nodes.iterator(); it.hasNext(); ) {
                    LayoutNode n = it.next();
                    if (n.succs.isEmpty() && n.preds.isEmpty()) {
                        it.remove();
                        standAlones.add(n);
                    }
                }
            }

            // Reverse inputs of roots
            for (LayoutNode node : nodes) {
                if (node.vertex.isRoot()) {
                    boolean ok = true;
                    for (LayoutEdge e : node.preds) {
                        if (e.from.vertex.isRoot()) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) {
                        reverseAllInputs(node);
                    }
                }
            }

            // Start DFS and reverse back edges
            visited = new HashSet<>();
            active = new HashSet<>();
            DFS();
            resolveInsOuts();
        }

        private void DFS(LayoutNode startNode, boolean vip) {
            if (visited.contains(startNode)) {
                return;
            }

            final boolean sortSuccs = vip && !isDefaultLayout && setting.get(Boolean.class, UNREVERSE_VIPS);

            Stack<LayoutNode> stack = new Stack<>();
            stack.push(startNode);

            while (!stack.empty()) {
                LayoutNode node = stack.pop();

                if (visited.contains(node)) {
                    // Node no longer active
                    active.remove(node);
                    continue;
                }

                // Repush immediately to know when no longer active
                stack.push(node);
                visited.add(node);
                active.add(node);

                ArrayList<LayoutEdge> succs = new ArrayList<>(node.succs);
                if (sortSuccs) {
                    //VIPs are first to follow
                    Collections.sort(succs, (e1, e2) -> Integer.compare(e1.vip ? e2.to.preds.size() : 0, e2.vip ? e1.to.preds.size() : 0));
                }
                for (LayoutEdge e : succs) {
                    if (active.contains(e.to)) {
                        assert visited.contains(e.to);
                        // Encountered back edge
                        reverseEdge(e);
                    } else if (!visited.contains(e.to)) {
                        stack.push(e.to);
                    }
                }
            }
        }

        private void DFS() {
            if (isDefaultLayout || !setting.get(Boolean.class, UNREVERSE_VIPS)) {
                nodes.forEach(n -> DFS(n, false));
            } else {
                //first search from vip starting nodes
                nodes.stream().filter((n) -> n.preds.stream().allMatch((e) -> !e.vip) && n.succs.stream().anyMatch((e) -> e.vip))
                        .forEach(n -> DFS(n, true));
                if (visited.size() < nodes.size()) {
                    //second look from leftover nodes
                    nodes.stream().filter((n) -> !visited.contains(n))
                            .sorted((n1, n2) -> Integer.compare(n1.preds.size(), n2.preds.size()))
                            .forEach(n -> DFS(n, false));
                }
            }
        }

        private void reverseAllInputs(LayoutNode node) {
            for (LayoutEdge e : node.preds) {
                assert !reversedLinks.contains(e.link);
                reversedLinks.add(e.link);
                node.succs.add(e);
                e.from.preds.add(e);
                e.from.succs.remove(e);
                int oldRelativeFrom = e.relativeFrom;
                int oldRelativeTo = e.relativeTo;
                e.to = e.from;
                e.from = node;
                e.relativeFrom = oldRelativeTo;
                e.relativeTo = oldRelativeFrom;
            }
            node.preds.clear();
        }

        @Override
        public void postCheck() {

            for (LayoutNode n : nodes) {

                HashSet<LayoutNode> curVisited = new HashSet<>();
                Queue<LayoutNode> queue = new LinkedList<>();
                for (LayoutEdge e : n.succs) {
                    LayoutNode s = e.to;
                    queue.add(s);
                    curVisited.add(s);
                }

                while (!queue.isEmpty()) {
                    LayoutNode curNode = queue.remove();

                    for (LayoutEdge e : curNode.succs) {
                        assert e.to != n;
                        if (!curVisited.contains(e.to)) {
                            queue.add(e.to);
                            curVisited.add(e.to);
                        }
                    }
                }
            }
        }
    }

    private void resolveInsOuts() {
        for (LayoutNode node : nodes) {
            SortedSet<Integer> reversedDown = new TreeSet<>();

            for (LayoutEdge e : node.succs) {
                if (reversedLinks.contains(e.link)) {
                    reversedDown.add(e.relativeFrom);
                }
            }

            SortedSet<Integer> reversedUp;
            if (reversedDown.isEmpty()) {
                reversedUp = new TreeSet<>(Collections.reverseOrder());
            } else {
                reversedUp = new TreeSet<>();
            }

            for (LayoutEdge e : node.preds) {
                if (reversedLinks.contains(e.link)) {
                    reversedUp.add(e.relativeTo);
                }
            }

            int cur = -reversedDown.size() * offset;
            int curWidth = node.width + reversedDown.size() * offset;
            for (int pos : reversedDown) {
                ArrayList<LayoutEdge> reversedSuccs = new ArrayList<>();
                for (LayoutEdge e : node.succs) {
                    if (e.relativeFrom == pos && reversedLinks.contains(e.link)) {
                        reversedSuccs.add(e);
                        e.relativeFrom = curWidth;
                    }
                }

                ArrayList<Point> startPoints = new ArrayList<>();
                startPoints.add(new Point(curWidth, cur));
                startPoints.add(new Point(pos, cur));
                startPoints.add(new Point(pos, cur));
                startPoints.add(new Point(pos, 0));
                for (LayoutEdge e : reversedSuccs) {
                    reversedLinkStartPoints.put(e.link, startPoints);
                }

                cur += offset;
                node.yOffset += offset;
                curWidth -= offset;
            }
            node.width += reversedDown.size() * offset;

            if (reversedDown.isEmpty()) {
                cur = offset;
            } else {
                cur = -offset;
            }
            for (int pos : reversedUp) {
                ArrayList<LayoutEdge> reversedPreds = new ArrayList<>();
                for (LayoutEdge e : node.preds) {
                    if (e.relativeTo == pos && reversedLinks.contains(e.link)) {
                        if (reversedDown.isEmpty()) {
                            e.relativeTo = node.width + offset;
                        } else {
                            e.relativeTo = cur;
                        }

                        reversedPreds.add(e);
                    }
                }
                node.bottomYOffset += offset;
                ArrayList<Point> endPoints = new ArrayList<>();

                node.width += offset;
                if (reversedDown.isEmpty()) {
                    endPoints.add(new Point(node.width, node.height + node.bottomYOffset));
                } else {
                    endPoints.add(new Point(cur, node.height + node.bottomYOffset));
                    cur -= offset;
                }

                endPoints.add(new Point(pos, node.height + node.bottomYOffset));
                endPoints.add(new Point(pos, node.height + node.bottomYOffset));
                endPoints.add(new Point(pos, node.height));
                for (LayoutEdge e : reversedPreds) {
                    reversedLinkEndPoints.put(e.link, endPoints);
                }
            }

            if (!reversedDown.isEmpty()) {
                node.xOffset = reversedUp.size() * offset;
            }
        }
    }

    private void reverseEdge(LayoutEdge e) {
        assert !reversedLinks.contains(e.link);
        reversedLinks.add(e.link);

        LayoutNode oldFrom = e.from;
        LayoutNode oldTo = e.to;
        int oldRelativeFrom = e.relativeFrom;
        int oldRelativeTo = e.relativeTo;

        e.from = oldTo;
        e.to = oldFrom;
        e.relativeFrom = oldRelativeTo;
        e.relativeTo = oldRelativeFrom;

        oldFrom.succs.remove(e);
        oldFrom.preds.add(e);
        oldTo.preds.remove(e);
        oldTo.succs.add(e);

    }

    private class BuildDatastructure extends AlgorithmPart {

        @Override
        protected void run() {
            // Set up nodes
            for (Vertex v : graph.getVertices()) {
                assert v.isVisible() : "Invisible nodes aren't permited in HierarchicalLayout.";
                LayoutNode node = new LayoutNode(v);
                nodes.add(node);
                vertexToLayoutNode.put(v, node);
            }

            // Set up edges
            List<? extends Link> links = new ArrayList<>(graph.getLinks());
            final boolean VIP = !setting.get(Boolean.class, NO_VIP);
            if (VIP) {
                Collections.sort(links, LINK_COMPARATOR);
            } else {
                Collections.sort(links, LINK_NOVIP_COMPARATOR);
            }

            for (Link l : links) {
                LayoutEdge edge = new LayoutEdge(
                        vertexToLayoutNode.get(l.getFrom().getVertex()),
                        vertexToLayoutNode.get(l.getTo().getVertex()),
                        l.getFrom().getRelativePosition().x,
                        l.getTo().getRelativePosition().x,
                        l,
                        VIP && l.isVIP());
                edge.from.succs.add(edge);
                edge.to.preds.add(edge);
                // assert edge.from != edge.to; // No self-loops allowed
            }
        }

        @Override
        public void postCheck() {
            assert vertexToLayoutNode.keySet().size() == nodes.size();
            assert nodes.size() == graph.getVertices().size();

            for (Vertex v : graph.getVertices()) {

                LayoutNode node = vertexToLayoutNode.get(v);
                assert node != null;

                for (LayoutEdge e : node.succs) {
                    assert e.from == node;
                }

                for (LayoutEdge e : node.preds) {
                    assert e.to == node;
                }

            }
        }
    }

    private static interface PartialComparator<T> {

        Integer partiallyCompare(T o1, T o2);
    }

    private static class NestedComparator<T> implements Comparator<T> {

        private final PartialComparator<T>[] partials;
        private final Comparator<T> nested;

        public NestedComparator(Comparator<T> comparator, PartialComparator<T>... partials) {
            this.partials = partials;
            nested = comparator;
        }

        @Override
        public int compare(T o1, T o2) {
            Integer part;
            for (PartialComparator<T> partial : partials) {
                part = partial.partiallyCompare(o1, o2);
                if (part != null) {
                    return part;
                }
            }
            return nested.compare(o1, o2);
        }
    }

    private static final Comparator<LayoutNode> NODE_POSITION_COMPARATOR = (n1, n2) -> n1.pos - n2.pos;

    private static final PartialComparator<LayoutNode> NODE_BOTHVIP = (n1, n2) -> {
        long n1VIP = n1.preds.stream().filter(x -> x.vip).count() + n1.succs.stream().filter(x -> x.vip).count();
        long n2VIP = n2.preds.stream().filter(x -> x.vip).count() + n2.succs.stream().filter(x -> x.vip).count();
        if (n1VIP != n2VIP) {
            return (int) (n2VIP - n1VIP);
        }
        return null;
    };
    private static final PartialComparator<LayoutNode> NODE_DOWNVIP = (n1, n2) -> {
        long n1VIP = n1.preds.stream().filter(x -> x.vip).count();
        long n2VIP = n2.preds.stream().filter(x -> x.vip).count();
        if (n1VIP != n2VIP) {
            return (int) (n2VIP - n1VIP);
        }
        return null;
    };
    private static final PartialComparator<LayoutNode> NODE_UPVIP = (n1, n2) -> {
        long n1VIP = n1.succs.stream().filter(x -> x.vip).count();
        long n2VIP = n2.succs.stream().filter(x -> x.vip).count();
        if (n1VIP != n2VIP) {
            return (int) (n2VIP - n1VIP);
        }
        return null;
    };
    private static final PartialComparator<LayoutNode> NODE_DUMMY = (n1, n2) -> {
        if (n1.isDummy()) {
            return n2.isDummy() ? 0 : 1;
        }
        return n2.isDummy() ? -1 : null;
    };
    private static final PartialComparator<LayoutNode> NODE_RDUMMY = (n1, n2) -> {
        if (n1.isDummy()) {
            return n2.isDummy() ? 0 : -1;
        }
        return n2.isDummy() ? 1 : null;
    };

    private static final Comparator<LayoutNode> NODE_BOTH = (n1, n2) -> (n1.preds.size() + n1.succs.size()) - (n2.preds.size() + n2.succs.size());
    private static final Comparator<LayoutNode> NODE_BOTH_REVERSE = (n1, n2) -> (n2.preds.size() + n2.succs.size()) - (n1.preds.size() + n1.succs.size());
    private static final Comparator<LayoutNode> NODE_DOWN = (n1, n2) -> n1.preds.size() - n2.preds.size();
    private static final Comparator<LayoutNode> NODE_DOWN_REVERSE = (n1, n2) -> n2.preds.size() - n1.preds.size();
    private static final Comparator<LayoutNode> NODE_UP = (n1, n2) -> n1.succs.size() - n2.succs.size();
    private static final Comparator<LayoutNode> NODE_UP_REVERSE = (n1, n2) -> n2.succs.size() - n1.succs.size();

    private static final Comparator<LayoutNode> NODE_PROCESSING_BOTH_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_BOTH, NODE_BOTHVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_BOTH_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_BOTH_REVERSE, NODE_BOTHVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DOWN_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_DOWN, NODE_DOWNVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DOWN_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_DOWN_REVERSE, NODE_DOWNVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_UP_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_UP, NODE_UPVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_UP_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_UP_REVERSE, NODE_UPVIP, NODE_DUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_BOTH_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_BOTH, NODE_BOTHVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_BOTH_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_BOTH_REVERSE, NODE_BOTHVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_DOWN_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_DOWN, NODE_DOWNVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_DOWN_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_DOWN_REVERSE, NODE_DOWNVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_UP_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_UP, NODE_UPVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> NODE_PROCESSING_DUMMY_UP_REVERSE_COMPARATOR = new NestedComparator<LayoutNode>(
            NODE_UP_REVERSE, NODE_UPVIP, NODE_RDUMMY);

    private static final Comparator<LayoutNode> CROSSING_NODE_COMPARATOR = (n1, n2) -> Float.compare(n1.crossingNumber, n2.crossingNumber);
    private static final Comparator<LayoutNode> DOWN_CROSSING_COMPARATOR = (n1, n2) -> n1.succs.size() - n2.succs.size();
    private static final Comparator<LayoutNode> UP_CROSSING_COMPARATOR = (n1, n2) -> n1.preds.size() - n2.preds.size();

    private static final Comparator<LayoutNode> DANGLING_UP_NODE_COMPARATOR = (n1, n2) -> {
        int ret = Integer.compare(n1.layer, n2.layer);
        if (ret != 0) {
            return ret;
        }
        ret = NODE_PROCESSING_UP_COMPARATOR.compare(n1, n2);
        if (ret != 0) {
            return ret;
        }
        return NODE_POSITION_COMPARATOR.compare(n1, n2);
    };

    private static final Comparator<LayoutNode> DANGLING_DOWN_NODE_COMPARATOR = (n1, n2) -> {
        int ret = Integer.compare(n2.layer, n1.layer);
        if (ret != 0) {
            return ret;
        }
        ret = NODE_PROCESSING_DOWN_COMPARATOR.compare(n1, n2);
        if (ret != 0) {
            return ret;
        }
        return NODE_POSITION_COMPARATOR.compare(n1, n2);
    };

    private static final Comparator<LayoutEdge> LAYER_COMPARATOR = (e1, e2) -> e1.to.layer - e2.to.layer;

    private static final Comparator<Link> LINK_NOVIP_COMPARATOR = (l1, l2) -> {
        int result = l1.getFrom().getVertex().compareTo(l2.getFrom().getVertex());
        if (result != 0) {
            return result;
        }
        result = l1.getTo().getVertex().compareTo(l2.getTo().getVertex());
        if (result != 0) {
            return result;
        }
        result = l1.getFrom().getRelativePosition().x - l2.getFrom().getRelativePosition().x;
        if (result != 0) {
            return result;
        }
        result = l1.getTo().getRelativePosition().x - l2.getTo().getRelativePosition().x;
        return result;
    };
    private static final Comparator<Link> LINK_COMPARATOR = (l1, l2) -> {
        if (l1.isVIP() && !l2.isVIP()) {
            return -1;
        }

        if (!l1.isVIP() && l2.isVIP()) {
            return 1;
        }
        return LINK_NOVIP_COMPARATOR.compare(l1, l2);
    };

    @Override
    public void doRouting(LayoutGraph graph) {
        this.graph = graph;

        cleanup();

        new BuildDatastructure().start();
        if (cancelled.get()) {
            return;
        }

        new ResolvePrevious().start();
        if (cancelled.get()) {
            return;
        }

        if (setting.get(Boolean.class, CROSS_REDUCE_ROUTING)) {
            new CrossingReduction(true).start();
        }
        if (cancelled.get()) {
            return;
        }

        new AssignXCoordinates().start();
        if (cancelled.get()) {
            return;
        }

        new AssignYCoordinates().start();
        if (cancelled.get()) {
            return;
        }

        new WriteResult().start();

    }

    private class ResolvePrevious extends AlgorithmPart {//Target for optimalization and refactoring

        @Override
        public void preCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer == -1;
                assert n.vertex.getPosition() != null;
            }
        }

        @Override
        protected void run() {
            reassignPositions();
            recreateLayers();
            reassignLayers();
            reassignInOutBlockNodes();
            reverseEdges();
            reassignLayers();
            recreateDummyNodes();
            reassignLayers();
            refreshLayers();
        }

        private void refreshLayers() {
            for (LayoutLayer l : layers) {
                l.refresh();
            }
        }

        private void reverseEdges() {
            // Remove self-edges and reverse edges
            for (LayoutNode node : nodes) {
                ArrayList<LayoutEdge> succs = new ArrayList<>(node.succs);
                for (LayoutEdge e : succs) {
                    assert e.from == node;
                    if (e.to == node) {
                        node.succs.remove(e);
                        node.preds.remove(e);
                    } else if (node.layer > e.to.layer) {
                        reverseEdge(e);
                    }
                }
            }
            resolveInsOuts();
        }

        private void recreateDummyNodes() {
            List<LayoutEdge> edges = new ArrayList<>();
            List<Point> points;
            for (int i = 0; i < layers.length - 2; i++) {
                for (LayoutNode n : layers[i]) {
                    if (n.isDummy()) {
                        continue;
                    }
                    for (LayoutEdge e : n.succs) {
                        points = e.link.getControlPoints();
                        if (e.to.layer == e.from.layer) {
                            edges.add(e);
                        } else if (points.isEmpty()) {
                            if (e.to.layer - e.from.layer > 1) {
                                edges.add(e);
                            }
                        } else {
                            if (points.get(0).y > points.get(points.size() - 1).y) {
                                Collections.reverse(points);
                            }
                            if (points.get(points.size() - 1).y > layers[i + 1].y) {
                                edges.add(e);
                            }
                        }
                    }
                    if (!edges.isEmpty()) {
                        resolveDummyNodes(n, edges);
                        edges.clear();
                    }
                }
            }
        }

        private int getInvisible(int from, int to) {
            int count = 0;
            for (int i = from + 1; i < to; ++i) {
                if (!layers[i].visible) {
                    count++;
                }
            }
            return count;
        }

        private void resolveDummyNodes(LayoutNode n, List<LayoutEdge> succs) {
            Map<Integer, List<LayoutEdge>> portHash = new HashMap<>();
            HashMap<Integer, LayoutNode> topNodeHash = new HashMap<>();
            HashMap<Integer, HashMap<Integer, LayoutNode>> bottomNodeHash = new HashMap<>();
            for (LayoutEdge e : succs) {
                int invisibleLayers = getInvisible(n.layer, e.to.layer);
                assert e.from.layer <= e.to.layer : e.from + "=" + e.from.layer + "; " + e.to + "=" + e.to.layer;
                if (e.from.layer != e.to.layer - 1 - invisibleLayers) {
                    if ((maxLayerLength != -1 && e.to.layer - invisibleLayers - e.from.layer > maxLayerLength && !setting.get(Boolean.class, DRAW_LONG_EDGES)) || e.from.layer == e.to.layer) {
                        assert maxLayerLength > 2;
                        e.to.preds.remove(e);
                        e.from.succs.remove(e);
                        if ((isDefaultLayout || !setting.get(Boolean.class, NO_DUMMY_LONG_EDGES)) && e.from.layer != e.to.layer) {
                            LayoutEdge topEdge;

                            LayoutNode topNode = topNodeHash.get(e.relativeFrom);
                            if (topNode == null) {
                                topNode = new LayoutNode();
                                topNode.layer = e.from.layer + 1;
                                nodes.add(topNode);
                                topNodeHash.put(e.relativeFrom, topNode);
                                bottomNodeHash.put(e.relativeFrom, new HashMap<>());
                            }
                            topEdge = new LayoutEdge(e.from, topNode, e.relativeFrom, topNode.width / 2, e.link, e.vip);
                            e.from.succs.add(topEdge);
                            topNode.preds.add(topEdge);

                            HashMap<Integer, LayoutNode> hash = bottomNodeHash.get(e.relativeFrom);

                            LayoutNode bottomNode;
                            if (hash.containsKey(e.to.layer)) {
                                bottomNode = hash.get(e.to.layer);
                            } else {
                                bottomNode = new LayoutNode();
                                bottomNode.layer = e.to.layer - 1;
                                nodes.add(bottomNode);
                                hash.put(e.to.layer, bottomNode);
                            }

                            LayoutEdge bottomEdge = new LayoutEdge(bottomNode, e.to, bottomNode.width / 2, e.relativeTo, e.link, e.vip);
                            e.to.preds.add(bottomEdge);
                            bottomNode.succs.add(bottomEdge);
                        } else {
                            longEdges.add(e);
                        }
                    } else {
                        Integer i = e.relativeFrom;
                        if (!portHash.containsKey(i)) {
                            portHash.put(i, new ArrayList<>());
                        }
                        portHash.get(i).add(e);
                    }
                }
            }

            for (LayoutEdge e : succs) {
                Integer i = e.relativeFrom;
                if (portHash.containsKey(i)) {
                    List<LayoutEdge> list = portHash.get(i);

                    if (list.size() == 1) {
                        resolveDummyNodes(list.get(0));
                    } else {
                        Collections.sort(list, LAYER_COMPARATOR);
                        int maxLayer = list.get(list.size() - 1).to.layer;
                        int cnt = maxLayer - n.layer - 1;
                        LayoutEdge[] edges = new LayoutEdge[cnt];
                        LayoutNode[] nodes = new LayoutNode[cnt];

                        nodes[0] = new LayoutNode();
                        nodes[0].layer = n.layer + 1;
                        layers[nodes[0].layer].add(nodes[0]);
                        nodes[0].y = layers[nodes[0].layer].y;
                        edges[0] = new LayoutEdge(n, nodes[0], i, nodes[0].width / 2, null, e.vip);
                        nodes[0].preds.add(edges[0]);
                        n.succs.add(edges[0]);
                        for (int j = 1; j < cnt; j++) {
                            nodes[j] = new LayoutNode();
                            nodes[j].layer = n.layer + j + 1;
                            edges[j] = new LayoutEdge(nodes[j - 1], nodes[j], nodes[j - 1].width / 2, nodes[j].width / 2, null, e.vip);
                            nodes[j - 1].succs.add(edges[j]);
                            nodes[j].preds.add(edges[j]);
                            layers[nodes[j].layer].add(nodes[j]);
                            nodes[j].y = layers[nodes[j].layer].y;
                        }
                        resolveDummyNodes(list, nodes);
                        for (LayoutEdge curEdge : list) {
                            assert curEdge.to.layer - n.layer - 2 >= 0;
                            assert curEdge.to.layer - n.layer - 2 < cnt;
                            LayoutNode anchor = nodes[curEdge.to.layer - n.layer - 2];
                            for (int l = 0; l < curEdge.to.layer - n.layer - 1; ++l) {
                                anchor = nodes[l];
                            }
                            anchor.succs.add(curEdge);
                            curEdge.from = anchor;
                            curEdge.relativeFrom = anchor.width / 2;
                            n.succs.remove(curEdge);
                        }
                        HierarchicalLayoutManager.this.nodes.addAll(Arrays.asList(nodes));
                    }
                    portHash.remove(i);
                }
            }
        }

        //ClusterSlotNodes needs to be at the edge of blocks.
        private void reassignInOutBlockNodes() {
            for (Vertex v : graph.getVertices()) {
                if (v instanceof ClusterSlotNode) {
                    LayoutNode n = vertexToLayoutNode.get(v);
                    layers[n.layer].remove(n);
                    if (((ClusterSlotNode) v).isInputSlot()) {
                        n.layer = 0;
                    } else {
                        n.layer = layerCount - 1;
                    }
                    layers[n.layer].add(n);
                }
            }
            List<LayoutLayer> lay = new ArrayList<>();
            for (LayoutLayer l : layers) {
                if (!l.isEmpty()) {
                    lay.add(l);
                }
            }
            layers = lay.toArray(new LayoutLayer[lay.size()]);
            layerCount = layers.length;
        }

        private void resolveDummyNodes(LayoutEdge edge) {
            double startX = edge.getStartPoint();
            double endX = edge.getEndPoint();
            double startY = edge.from.y;
            double endY = edge.to.y;
            int startLayer = edge.from.layer + 1;
            int endLayer = edge.to.layer - 1;
            double coef = (endX - startX) / (endY - startY);
            for (int i = 0; i <= endLayer - startLayer; ++i) {
                int layer = startLayer + i;
                LayoutLayer addedLayer = layers[layer];
                LayoutNode dummy = new LayoutNode();
                dummy.layer = layer + 1;
                LayoutEdge e = new LayoutEdge(edge.from, dummy, edge.relativeFrom, dummy.width / 2, null, edge.vip);
                edge.from.succs.remove(edge);
                edge.from.succs.add(e);
                edge.from = dummy;
                edge.relativeFrom = dummy.width / 2;
                dummy.succs.add(edge);
                dummy.preds.add(e);
                dummy.x = (int) (startX + (addedLayer.y - startY) * coef);
                dummy.y = addedLayer.y;
                addedLayer.add(dummy);
            }
        }

        private void resolveDummyNodes(List<LayoutEdge> edges, LayoutNode[] nodes) {
            LayoutEdge edge = edges.get(edges.size() - 1);
            int layer = edge.from.layer + 1;
            List<Integer> positions = resolveEdgePoints(edges);
            LayoutLayer nextLayer = layers[layer++];
            for (int i = 0; i < positions.size(); ++i) {
                LayoutNode dummy = nodes[i];
                dummy.x = positions.get(i);
                dummy.y = nextLayer.y;
                nextLayer = layers[layer + i];
            }
        }

        private List<Integer> resolveEdgePoints(List<LayoutEdge> edges) {
            List<Integer> positions = new ArrayList<>();
            LayoutEdge edge = getLastFullEdge(edges);
            if (edge != null) {
                LayoutEdge longestEdge = edges.get(edges.size() - 1);
                List<Point> points = edge.link.getControlPoints();
                int layer = edge.from.layer + 1;
                Point pointA = points.get(0);
                int lastLayer = edge.to.layer;
                LayoutLayer nextLayer = layers[layer];
                for (int i = 1; i < points.size(); ++i) {
                    Point pointB = points.get(i);
                    if (pointA.x == pointB.x) {
                        int avgY = (pointA.y + pointB.y) / 2;
                        if (avgY > nextLayer.y && avgY < nextLayer.y + nextLayer.height) {
                            if (layer == lastLayer) {
                                if (longestEdge.to.layer == lastLayer) {
                                    return positions;
                                }
                                break;
                            }
                            positions.add(pointA.x);
                            nextLayer = layers[++layer];
                        }
                    }
                    pointA = pointB;
                }
            }
            return resolveUnconnectedEdges(positions, edges);
        }

        private List<Integer> resolveUnconnectedEdges(List<Integer> positions, List<LayoutEdge> edges) {
            edges = edges.stream().filter(e -> e.link.getControlPoints().contains(null) || e.link.getControlPoints().isEmpty()).collect(Collectors.toList());
            if (edges.isEmpty()) {
                return positions;
            }
            LayoutEdge firstEdge = edges.get(0);
            int fromLayer = firstEdge.from.layer;
            int toLayer = edges.get(edges.size() - 1).to.layer;
            List<List<LayoutEdge>> packedEdges = new ArrayList<>();
            for (int i = fromLayer; i < toLayer - 1; ++i) {
                packedEdges.add(null);
            }
            for (LayoutEdge e : edges) {
                int index = e.to.layer - fromLayer - 2;
                List<LayoutEdge> el = packedEdges.get(index);
                if (el == null) {
                    packedEdges.set(index, el = new ArrayList<>());
                }
                el.add(e);
            }

            int lastLayer = fromLayer + positions.size();
            int misses = -positions.size();
            int lX = positions.isEmpty() ? firstEdge.getStartPoint() : positions.get(positions.size() - 1);
            int fX = 0;
            for (List<LayoutEdge> el : packedEdges) {
                if (el == null) {
                    ++misses;
                } else {
                    for (LayoutEdge ed : el) {
                        fX += ed.getEndPoint();
                    }
                    fX /= el.size();
                    if (misses > 0) {
                        resolveBetweenLongEdges(positions, lX, fX, lastLayer, misses);
                        lastLayer += misses;
                        misses = 0;
                    } else {
                        ++lastLayer;
                    }
                    positions.add(fX);
                    lX = fX;
                    fX = 0;
                }
            }

            return positions;
        }

        private void resolveBetweenLongEdges(List<Integer> positions, double startX, double endX, int lastLayer, int count) {
            double startY = layers[lastLayer].y;
            double endY = layers[lastLayer + count].y;
            double coef = (endX - startX) / (endY - startY);
            for (int i = lastLayer + 1; i <= lastLayer + count; ++i) {
                positions.add((int) (startX + (layers[i].y - startY) * coef));
            }
        }

        private LayoutEdge getLastFullEdge(List<LayoutEdge> edges) {
            for (int i = edges.size() - 1; i >= 0; i--) {
                LayoutEdge edge = edges.get(i);
                if (!edge.link.getControlPoints().contains(null) && !edge.link.getControlPoints().isEmpty()) {
                    return edge;
                }
            }
            return null;
        }

        private void reassignPositions() {
            for (LayoutNode n : nodes) {
                Point position = n.vertex.getPosition();
                n.x = position.x;
                n.y = position.y;
            }
        }

        private void recreateLayers() {
            List<LayoutLayer> layers = new ArrayList<>();
            for (LayoutNode n : nodes) {
                int nodeHeight = n.getWholeHeight();
                if (n.vertex instanceof ClusterNode) {
                    //we need to calculate with old height of clusternode, because they could grow a lot
                    nodeHeight = ((ClusterNode) n.vertex).getCluster().getBounds().height;
                }
                boolean assigned = false;
                int avgPos = n.y + (nodeHeight / 2);
                for (LayoutLayer l : layers) {
                    int lBot = l.getBottom();
                    if (avgPos > l.y && avgPos < lBot) {
                        l.add(n);
                        l.y = Math.min(l.y, n.y - n.yOffset);
                        l.height = Math.max(l.height, l.height + n.y + nodeHeight - l.getBottom());
                        assigned = true;
                        break;
                    }
                }
                if (!assigned) {
                    LayoutLayer l = new LayoutLayer();
                    layers.add(l);
                    l.add(n);
                    l.y = n.y - n.yOffset;
                    l.height = nodeHeight;
                }
            }
            for (LayoutLayer l : layers) {
                l.refresh();
            }
            layers.sort((l1, l2) -> Integer.compare(l1.y, l2.y));
            HierarchicalLayoutManager.this.layers = layers.toArray(new LayoutLayer[layers.size()]);
            layerCount = layers.size();
        }

        private void reassignLayers() {
            for (int i = 0; i < layers.length; i++) {
                LayoutLayer layer = layers[i];
                for (int p = 0; p < layer.size(); p++) {
                    LayoutNode n = layer.get(p);
                    n.pos = p;
                    n.layer = i;
                }
            }
        }

        @Override
        public void postCheck() {
            for (LayoutNode n : nodes) {
                assert n.layer >= 0;
                assert n.layer < layerCount;
                for (LayoutEdge e : n.succs) {
                    assert e.from.layer < e.to.layer;
                }
            }
        }
    }
}
