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

import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.layout.LayoutManager;
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.BLOCKVIEW_AS_CONTROLFLOW;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DECREASE_LAYER_WIDTH_DEVIATION;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DEFAULT_LAYOUT;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DRAW_LONG_EDGES;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.DUMMY_WIDTH;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.EDGE_BENDING;
import static org.graalvm.visualizer.settings.layout.LayoutSettings.STANDALONES;

public class HierarchicalClusterLayoutManager implements LayoutManager {

    private static final Logger LOG = Logger.getLogger(HierarchicalClusterLayoutManager.class.getName());

    private final AtomicBoolean cancelled;

    @Override
    public boolean cancel() {
        cancelled.set(true);
        manager.cancel();
        subManager.cancel();
        return true;
    }

    private final HierarchicalLayoutManager.Combine combine;
    private final LayoutSettingBean layoutSetting;

    private HierarchicalLayoutManager subManager;
    private HierarchicalLayoutManager manager;
    private static final boolean TRACE = false;

    public HierarchicalClusterLayoutManager(HierarchicalLayoutManager.Combine combine, LayoutSettingBean layoutSetting) {
        this(combine, new HierarchicalLayoutManager(combine, layoutSetting), new HierarchicalLayoutManager(combine, layoutSetting), layoutSetting);
    }

    private HierarchicalClusterLayoutManager(HierarchicalLayoutManager.Combine combine, HierarchicalLayoutManager manager, HierarchicalLayoutManager subManager, LayoutSettingBean layoutSetting) {
        this.combine = combine;
        this.subManager = subManager;
        this.manager = manager;
        this.layoutSetting = layoutSetting;
        cancelled = new AtomicBoolean(false);
    }

    @Override
    public void doLayout(LayoutGraph graph) {
        doLayout(graph, new HashSet<>(), new HashSet<>());
    }

    public void setSubManager(HierarchicalLayoutManager manager) {
        this.subManager = manager;
    }

    public void setManager(HierarchicalLayoutManager manager) {
        this.manager = manager;
    }

    public HierarchicalLayoutManager getSubManager() {
        return subManager;
    }

    public HierarchicalLayoutManager getManager() {
        return manager;
    }

    private Set<Cluster> discoverClusters(LayoutGraph graph) {
        Set<Cluster> clusters = graph.getClusters();
        Set<Cluster> others = new HashSet<>();
        for (Cluster c : clusters) {
            getOtherClusters(clusters, others, c);
        }
        clusters.addAll(others);
        clusters = clusters.stream().filter(c -> c.isVisible()).collect(Collectors.toSet());
        return clusters;
    }

    private void getOtherClusters(Set<Cluster> clusters, Set<Cluster> others, Cluster current) {
        for (Cluster c : current.getSuccessors()) {
            if (!clusters.contains(c) && others.add(c)) {
                getOtherClusters(clusters, others, c);
            }
        }
    }

    public void doLayout(LayoutGraph graph, Set<? extends Vertex> firstLayerHint, Set<? extends Vertex> lastLayerHint) {

        assert graph.verify();

        HashMap<Cluster, HashMap<Port, ClusterSlotNode>> clusterInputSlotHash = new HashMap<>();
        HashMap<Cluster, HashMap<Port, ClusterSlotNode>> clusterOutputSlotHash = new HashMap<>();

        HashMap<Cluster, ClusterNode> clusterNodes = new HashMap<>();
        Set<Link> clusterEdges = new HashSet<>();
        Set<Link> interClusterEdges = new HashSet<>();
        HashMap<Link, ClusterConnection> linkClusterOutgoingConnection = new HashMap<>();
        HashMap<Link, ClusterConnection> linkInterClusterConnection = new HashMap<>();
        HashMap<Link, ClusterConnection> linkClusterIngoingConnection = new HashMap<>();
        Set<ClusterNode> clusterNodeSet = new HashSet<>();

        Set<Cluster> clusters = discoverClusters(graph);
        int z = 0;
        for (Cluster c : clusters) {
            clusterInputSlotHash.put(c, new HashMap<>());
            clusterOutputSlotHash.put(c, new HashMap<>());
            ClusterNode cn = new ClusterNode(c, "" + z);
            clusterNodes.put(c, cn);
            clusterNodeSet.add(cn);
            z++;
        }

        // Add cluster edges
        for (Cluster c : clusters) {
            ClusterNode start = clusterNodes.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if (end != null && start != end) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    interClusterEdges.add(e);
                }
            }
        }

        for (Vertex v : graph.getVertices()) {
            if (v.isVisible()) {
                Cluster c = v.getCluster();
                assert c != null : "Cluster of vertex " + v + " is null!";
                clusterNodes.get(c).addSubNode(v);
            }
        }

        for (Link l : graph.getLinks()) {
            Port fromPort = l.getFrom();
            Port toPort = l.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();
            if (!fromVertex.isVisible() || !toVertex.isVisible()) {
                continue;
            }
            Cluster fromCluster = fromVertex.getCluster();
            Cluster toCluster = toVertex.getCluster();

            Port samePort = null;
            if (combine == HierarchicalLayoutManager.Combine.SAME_INPUTS) {
                samePort = toPort;
            } else if (combine == HierarchicalLayoutManager.Combine.SAME_OUTPUTS) {
                samePort = fromPort;
            }

            if (fromCluster == toCluster) {
                clusterNodes.get(fromCluster).addSubEdge(l);
            } else {
                ClusterSlotNode inputSlotNode = null;
                ClusterSlotNode outputSlotNode = null;

                if (samePort != null) {
                    outputSlotNode = clusterOutputSlotHash.get(fromCluster).get(samePort);
                    inputSlotNode = clusterInputSlotHash.get(toCluster).get(samePort);
                }

                if (outputSlotNode == null) {
                    outputSlotNode = ClusterSlotNode.makeOutputSlotNode(clusterNodes.get(fromCluster), "Out " + fromCluster + " " + samePort);
                    ClusterConnection conn = ClusterConnection.makeOutputConnection(outputSlotNode, l);
                    outputSlotNode.setConnection(conn);
                    clusterNodes.get(fromCluster).addSubEdge(conn);
                    if (samePort != null) {
                        clusterOutputSlotHash.get(fromCluster).put(samePort, outputSlotNode);
                    }

                    linkClusterOutgoingConnection.put(l, conn);
                } else {
                    linkClusterOutgoingConnection.put(l, outputSlotNode.getConnection());
                }

                if (inputSlotNode == null) {
                    inputSlotNode = ClusterSlotNode.makeInputSlotNode(clusterNodes.get(toCluster), "In " + toCluster + " " + samePort);
                }

                ClusterConnection conn = ClusterConnection.makeInputConnection(inputSlotNode, l);
                inputSlotNode.setConnection(conn);
                clusterNodes.get(toCluster).addSubEdge(conn);
                if (samePort != null) {
                    clusterInputSlotHash.get(toCluster).put(samePort, inputSlotNode);
                }

                linkClusterIngoingConnection.put(l, conn);

                ClusterConnection interConn = ClusterConnection.makeInnerConnection(inputSlotNode, outputSlotNode, l);
                linkInterClusterConnection.put(l, interConn);
                clusterEdges.add(interConn);
            }
        }
        if (cancelled.get()) {
            return;
        }

        Timing t = null;

        if (TRACE) {
            t = new Timing("Child timing");
            t.start();
        }

        for (Cluster c : clusters) {
            if (cancelled.get()) {
                return;
            }
            ClusterNode n = clusterNodes.get(c);
            LayoutGraph clusterGraph = new LayoutGraph(n.getSubEdges(), n.getSubNodes());
            subManager.doLayout(clusterGraph);
            n.updateSize(clusterGraph.getSize());
        }

        Set<Vertex> roots = new LayoutGraph(interClusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }

        LayoutGraph graph2;
        if (!layoutSetting.get(Boolean.class, DEFAULT_LAYOUT) && layoutSetting.get(Boolean.class, BLOCKVIEW_AS_CONTROLFLOW)) {
            boolean longEdges = layoutSetting.get(Boolean.class, DRAW_LONG_EDGES);
            boolean decreaseDeviation = layoutSetting.get(Boolean.class, DECREASE_LAYER_WIDTH_DEVIATION);
            boolean standAlones = layoutSetting.get(Boolean.class, STANDALONES);
            layoutSetting.set(DRAW_LONG_EDGES, true);
            layoutSetting.set(DECREASE_LAYER_WIDTH_DEVIATION, false);
            layoutSetting.set(STANDALONES, false);

            graph2 = new LayoutGraph(interClusterEdges, clusterNodeSet);
            manager.doLayout(graph2);

            for (Cluster c : clusters) {
                ClusterNode n = clusterNodes.get(c);
                c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
                c.setVisible(n.isVisible());
            }

            layoutSetting.set(DRAW_LONG_EDGES, longEdges);
            layoutSetting.set(DECREASE_LAYER_WIDTH_DEVIATION, decreaseDeviation);
            layoutSetting.set(STANDALONES, standAlones);

            graph2 = new LayoutGraph(clusterEdges, clusterNodeSet);
            manager.doRouting(graph2);
        } else {
            boolean standAlones = layoutSetting.get(Boolean.class, STANDALONES);
            layoutSetting.set(STANDALONES, false);
            graph2 = new LayoutGraph(clusterEdges, clusterNodeSet);
            manager.doLayout(graph2);
            layoutSetting.set(STANDALONES, standAlones);
        }
        graph.setSize(graph2.getSize());

        if (cancelled.get()) {
            return;
        }
        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
            c.setVisible(n.isVisible());
        }

        // TODO: handle case where blocks are not fully connected
        if (TRACE) {
            t.stop();
            t.print();
        }

        for (Link l : graph.getLinks()) {
            if (linkInterClusterConnection.containsKey(l)) {
                ClusterConnection conn1 = linkClusterOutgoingConnection.get(l);
                ClusterConnection conn2 = linkInterClusterConnection.get(l);
                ClusterConnection conn3 = linkClusterIngoingConnection.get(l);

                assert conn1 != null;
                assert conn2 != null;
                assert conn3 != null;

                List<Point> points = new ArrayList<>();

                points.addAll(conn1.getControlPoints());
                points.addAll(conn2.getControlPoints());
                points.addAll(conn3.getControlPoints());

                l.setControlPoints(points);
            }
        }
    }

    @Override
    public void doRouting(LayoutGraph graph) {

        assert graph.verify();

        HashMap<Cluster, HashMap<Port, ClusterSlotNode>> clusterInputSlotHash = new HashMap<>();
        HashMap<Cluster, HashMap<Port, ClusterSlotNode>> clusterOutputSlotHash = new HashMap<>();

        HashMap<Cluster, ClusterNode> clusterNodes = new HashMap<>();
        Set<Link> clusterEdges = new HashSet<>();
        Set<Link> interClusterEdges = new HashSet<>();
        HashMap<Link, ClusterConnection> linkClusterOutgoingConnection = new HashMap<>();
        HashMap<Link, ClusterConnection> linkInterClusterConnection = new HashMap<>();
        HashMap<Link, ClusterConnection> linkClusterIngoingConnection = new HashMap<>();
        Set<ClusterNode> clusterNodeSet = new HashSet<>();
        Set<Link> reverseEdges = new HashSet<>();

        Set<Cluster> clusters = discoverClusters(graph);
        int z = 0;
        for (Cluster c : clusters) {
            clusterInputSlotHash.put(c, new HashMap<>());
            clusterOutputSlotHash.put(c, new HashMap<>());
            ClusterNode cn = new ClusterNode(c, "" + z);
            clusterNodes.put(c, cn);
            clusterNodeSet.add(cn);
            z++;
        }

        // Add cluster edges
        for (Cluster c : clusters) {
            ClusterNode start = clusterNodes.get(c);
            for (Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if (end != null && start != end) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    interClusterEdges.add(e);
                }
            }
        }

        for (Vertex v : graph.getVertices()) {
            if (v.isVisible()) {
                Cluster c = v.getCluster();
                assert c != null : "Cluster of vertex " + v + " is null!";
                clusterNodes.get(c).addSubNode(v);
            }
        }

        for (Link l : graph.getLinks()) {
            Port fromPort = l.getFrom();
            Port toPort = l.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();
            if (!fromVertex.isVisible() || !toVertex.isVisible()) {
                continue;
            }
            Cluster fromCluster = fromVertex.getCluster();
            Cluster toCluster = toVertex.getCluster();

            Port samePort = null;
            if (combine == HierarchicalLayoutManager.Combine.SAME_INPUTS) {
                samePort = toPort;
            } else if (combine == HierarchicalLayoutManager.Combine.SAME_OUTPUTS) {
                samePort = fromPort;
            }

            if (fromCluster == toCluster) {
                clusterNodes.get(fromCluster).addSubEdge(l);
            } else {
                ClusterSlotNode inputSlotNode = null;
                ClusterSlotNode outputSlotNode = null;

                if (samePort != null) {
                    outputSlotNode = clusterOutputSlotHash.get(fromCluster).get(samePort);
                    inputSlotNode = clusterInputSlotHash.get(toCluster).get(samePort);
                }

                if (outputSlotNode == null) {
                    outputSlotNode = ClusterSlotNode.makeOutputSlotNode(clusterNodes.get(fromCluster), "Out " + fromCluster + " " + samePort);
                    ClusterConnection conn = ClusterConnection.makeOutputConnection(outputSlotNode, l);
                    outputSlotNode.setConnection(conn);
                    resolveSlotAndConnection(outputSlotNode, conn, l);
                    clusterNodes.get(fromCluster).addSubEdge(conn);
                    if (samePort != null) {
                        clusterOutputSlotHash.get(fromCluster).put(samePort, outputSlotNode);
                    }

                    linkClusterOutgoingConnection.put(l, conn);
                } else {
                    linkClusterOutgoingConnection.put(l, outputSlotNode.getConnection());
                }

                if (inputSlotNode == null) {
                    inputSlotNode = ClusterSlotNode.makeInputSlotNode(clusterNodes.get(toCluster), "In " + toCluster + " " + samePort);
                }

                ClusterConnection conn = ClusterConnection.makeInputConnection(inputSlotNode, l);
                inputSlotNode.setConnection(conn);
                resolveSlotAndConnection(inputSlotNode, conn, l);
                clusterNodes.get(toCluster).addSubEdge(conn);
                if (samePort != null) {
                    clusterInputSlotHash.get(toCluster).put(samePort, inputSlotNode);
                }

                linkClusterIngoingConnection.put(l, conn);

                ClusterConnection interConn = ClusterConnection.makeInnerConnection(inputSlotNode, outputSlotNode, l);
                resolveInnerSlotAndConnection(reverseEdges, outputSlotNode, inputSlotNode, interConn, l);
                linkInterClusterConnection.put(l, interConn);
                clusterEdges.add(interConn);
            }
        }

        Timing t = null;

        if (TRACE) {
            t = new Timing("Child timing");
            t.start();
        }

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            LayoutGraph clusterGraph = new LayoutGraph(n.getSubEdges(), n.getSubNodes());
            subManager.doRouting(clusterGraph);
            n.updateSize(clusterGraph.getSize());
            n.setPosition(c.getBounds().getLocation());
        }

        Set<Vertex> roots = new LayoutGraph(interClusterEdges).findRootVertices();
        for (Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode) v).setRoot(true);
        }

        resolveReversedEdges(reverseEdges, linkInterClusterConnection);

        LayoutGraph graph2 = new LayoutGraph(clusterEdges, clusterNodeSet);
        manager.doRouting(graph2);
        graph.setSize(graph2.getSize());

        for (Cluster c : clusters) {
            ClusterNode n = clusterNodes.get(c);
            c.setBounds(new Rectangle(n.getPosition(), n.getSize()));
            c.setVisible(n.isVisible());
        }

        // TODO: handle case where blocks are not fully connected
        if (TRACE) {
            t.stop();
            t.print();
        }

        for (Link l : graph.getLinks()) {
            if (linkInterClusterConnection.containsKey(l)) {
                ClusterConnection conn1 = linkClusterOutgoingConnection.get(l);
                ClusterConnection conn2 = linkInterClusterConnection.get(l);
                ClusterConnection conn3 = linkClusterIngoingConnection.get(l);

                assert conn1 != null;
                assert conn2 != null;
                assert conn3 != null;

                List<Point> points = new ArrayList<>();

                points.addAll(conn1.getControlPoints());
                points.addAll(conn2.getControlPoints());
                points.addAll(conn3.getControlPoints());

                l.setControlPoints(points);
            }
        }
    }

    private void resolveSlotAndConnection(ClusterSlotNode slotNode, ClusterConnection connection, Link link) {
        boolean input = slotNode.isInputSlot();
        Rectangle bounds = slotNode.getCluster().getBounds();
        int y = bounds.y + (input ? 0 : bounds.height);
        List<Point> linkPoints = link.getControlPoints();
        assert linkPoints.size() > 2;
        List<Point> points = new ArrayList<>();
        Point a = null;
        if (input) {
            for (int i = linkPoints.size() - 1; i > 0; --i) {
                a = linkPoints.get(i);
                if (a != null && a.y < y) {
                    break;
                }
                points.add(a);
            }
        } else {
            for (int i = 0; i < linkPoints.size(); ++i) {
                a = linkPoints.get(i);
                if (a != null && a.y > y) {
                    break;
                }
                points.add(a);
            }
        }
        assert a.x == points.get(points.size() - 1).x;
        points.add(a = new Point(a.x, y));
        assert points.size() > 2;
        if (input) {
            Collections.reverse(points);
        }
        connection.setControlPoints(points);
        slotNode.setPosition(new Point(a.x, a.y + (input ? 20 : -20)));
    }

    private void resolveInnerSlotAndConnection(Set<Link> reversedEdges, ClusterSlotNode outputSlotNode, ClusterSlotNode inputSlotNode, ClusterConnection connection, Link link) {
        List<Point> outPoints = outputSlotNode.getConnection().getControlPoints();
        List<Point> inPoints = inputSlotNode.getConnection().getControlPoints();
        assert outPoints.size() > 2;
        assert inPoints.size() > 2;
        List<Point> points = link.getControlPoints();
        assert points.contains(outPoints.get(outPoints.size() - 2));
        assert points.contains(inPoints.get(1));
        List<Point> innPoints = new ArrayList<>();
        innPoints.add(outPoints.get(outPoints.size() - 1));
        for (int i = points.indexOf(outPoints.get(outPoints.size() - 2)) + 1; i < points.indexOf(inPoints.get(1)); ++i) {
            innPoints.add(points.get(i));
        }
        innPoints.add(inPoints.get(0));
        connection.setControlPoints(innPoints);

        if (innPoints.get(0).y > innPoints.get(innPoints.size() - 1).y) {
            reversedEdges.add(link);
        }
    }

    private void resolveReversedEdges(Set<Link> links, HashMap<Link, ClusterConnection> inner) {
        HashMap<Vertex, List<Link>> inMap = new HashMap<>();
        HashMap<Vertex, List<Link>> outMap = new HashMap<>();
        HashMap<Port, Link> usedPorts = new HashMap<>();
        for (Link li : links) {
            Link l = inner.get(li);
            inMap.computeIfAbsent(l.getTo().getVertex(), (v) -> new ArrayList<>()).add(l);
            outMap.computeIfAbsent(l.getFrom().getVertex(), (v) -> new ArrayList<>()).add(l);
        }
        final int offset = HierarchicalLayoutManager.X_OFFSET + layoutSetting.get(Integer.class, DUMMY_WIDTH);
        for (Map.Entry<Vertex, List<Link>> entry : outMap.entrySet()) {
            final Point position = entry.getKey().getPosition();
            final Dimension size = entry.getKey().getSize();
            List<Link> outputs = entry.getValue();
            Collections.sort(outputs, (l1, l2) -> l1.getControlPoints().get(1).y - l2.getControlPoints().get(1).y);
            int outCount = 1;
            for (Link l : outputs) {
                Link lastLink = usedPorts.get(l.getFrom());
                if (lastLink == null) {
                    List<Point> points = l.getControlPoints();
                    Point portPoint = position.getLocation();
                    portPoint.translate(l.getFrom().getRelativePosition().x, size.height);

                    points.get(0).move(portPoint.x, portPoint.y);
                    points.get(1).move(portPoint.x, portPoint.y + offset * outCount);
                    Point last;
                    if (!layoutSetting.get(Boolean.class, EDGE_BENDING)) {
                        points.get(2).move(portPoint.x, portPoint.y + offset * outCount);
                        last = points.get(3);
                    } else {
                        last = points.get(2);
                    }
                    last.move(position.x + (last.x > position.x ? size.width + offset * outCount : -offset * outCount), portPoint.y + offset * outCount);
                    usedPorts.put(l.getFrom(), l);
                    ++outCount;
                } else {
                    List<Point> lastPoints = lastLink.getControlPoints();
                    List<Point> points = l.getControlPoints();

                    points.get(0).setLocation(lastPoints.get(0));
                    points.get(1).setLocation(lastPoints.get(1));
                    points.get(2).setLocation(lastPoints.get(2));
                    if (!layoutSetting.get(Boolean.class, EDGE_BENDING)) {
                        points.get(3).setLocation(lastPoints.get(3));
                    }
                }
            }
        }
        for (Map.Entry<Vertex, List<Link>> entry : inMap.entrySet()) {
            final Point position = entry.getKey().getPosition();
            final Dimension size = entry.getKey().getSize();
            List<Link> inputs = entry.getValue();
            int inCount = 1;
            Collections.sort(inputs, (l1, l2) -> {
                List<Point> p1 = l1.getControlPoints();
                List<Point> p2 = l2.getControlPoints();
                return p2.get(p2.size() - 2).y - p1.get(p1.size() - 2).y;
            });
            for (Link l : inputs) {
                Link lastLink = usedPorts.get(l.getTo());
                if (lastLink == null) {
                    List<Point> points = l.getControlPoints();
                    Point portPoint = position.getLocation();
                    portPoint.x += l.getTo().getRelativePosition().x;

                    points.get(points.size() - 1).move(portPoint.x, portPoint.y);
                    points.get(points.size() - 2).move(portPoint.x, portPoint.y - offset * inCount);
                    Point last;
                    if (!layoutSetting.get(Boolean.class, EDGE_BENDING)) {
                        points.get(points.size() - 3).move(portPoint.x, portPoint.y - offset * inCount);
                        last = points.get(points.size() - 4);
                    } else {
                        last = points.get(points.size() - 3);
                    }
                    last.move(position.x + size.width + offset * inCount, portPoint.y - offset * inCount);
                    usedPorts.put(l.getTo(), l);
                    ++inCount;
                } else {
                    List<Point> lastPoints = lastLink.getControlPoints();
                    List<Point> points = l.getControlPoints();

                    points.get(points.size() - 1).setLocation(lastPoints.get(lastPoints.size() - 1));
                    points.get(points.size() - 2).setLocation(lastPoints.get(lastPoints.size() - 2));
                    points.get(points.size() - 3).setLocation(lastPoints.get(lastPoints.size() - 3));
                    if (!layoutSetting.get(Boolean.class, EDGE_BENDING)) {
                        points.get(points.size() - 4).setLocation(lastPoints.get(lastPoints.size() - 4));
                    }
                }
            }
        }
    }
}
