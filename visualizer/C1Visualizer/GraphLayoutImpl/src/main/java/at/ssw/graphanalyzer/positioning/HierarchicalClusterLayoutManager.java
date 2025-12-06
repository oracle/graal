/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.graphanalyzer.positioning;

import at.ssw.graphanalyzer.positioning.HierarchicalLayoutManager.Combine;
import at.ssw.positionmanager.Cluster;
import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.LayoutManager;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Port;
import at.ssw.positionmanager.Vertex;
import java.awt.Point;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;
import at.ssw.graphanalyzer.positioning.HierarchicalLayoutManager;

/**
 *
 * @author Thomas Wuerthinger
 */
public class HierarchicalClusterLayoutManager implements LayoutManager{

    private HierarchicalLayoutManager.Combine combine;

    /** Creates a new instance of HierarchicalClusterLayoutManager */
    public HierarchicalClusterLayoutManager(Combine combine) {
        this.combine = combine;
    }

    public void doLayout(LayoutGraph graph) {

        assert graph.verify();

        HierarchicalLayoutManager subManager = new HierarchicalLayoutManager(combine);
        HierarchicalLayoutManager manager = new HierarchicalLayoutManager(combine, 150);
        Hashtable<Cluster, List<Vertex>> lists = new Hashtable<Cluster, List<Vertex>>();
        Hashtable<Cluster, List<Link>> listsConnection = new Hashtable<Cluster, List<Link>>();
        Set<Vertex> vertices = graph.getVertices();
        Hashtable<Cluster, Hashtable<Port, ClusterInputSlotNode>> clusterInputSlotHash = new Hashtable<Cluster, Hashtable<Port, ClusterInputSlotNode>>();
        Hashtable<Cluster, Hashtable<Port, ClusterOutputSlotNode>> clusterOutputSlotHash = new Hashtable<Cluster, Hashtable<Port, ClusterOutputSlotNode>>();

        Hashtable<Cluster, ClusterNode> clusterNodes = new Hashtable<Cluster, ClusterNode>();
        Hashtable<Cluster, Set<ClusterInputSlotNode>> clusterInputSlotSet = new Hashtable<Cluster, Set<ClusterInputSlotNode>>();
        Hashtable<Cluster, Set<ClusterOutputSlotNode>> clusterOutputSlotSet = new Hashtable<Cluster, Set<ClusterOutputSlotNode>>();
        Set<Link> clusterEdges = new HashSet<Link>();
        Set<Link> interClusterEdges = new HashSet<Link>();
        Hashtable<Link, ClusterOutgoingConnection> linkClusterOutgoingConnection = new Hashtable<Link, ClusterOutgoingConnection>();
        Hashtable<Link, InterClusterConnection> linkInterClusterConnection = new Hashtable<Link, InterClusterConnection>();
        Hashtable<Link, ClusterIngoingConnection> linkClusterIngoingConnection = new Hashtable<Link, ClusterIngoingConnection>();

        Set<Cluster> cluster = graph.getClusters();
        int z = 0;
        for(Cluster c : cluster) {
            lists.put(c, new ArrayList<Vertex>());

            listsConnection.put(c, new ArrayList<Link>());
            clusterInputSlotHash.put(c, new Hashtable<Port, ClusterInputSlotNode>());
            clusterOutputSlotHash.put(c, new Hashtable<Port, ClusterOutputSlotNode>());
            clusterOutputSlotSet.put(c, new TreeSet<ClusterOutputSlotNode>());
            clusterInputSlotSet.put(c, new TreeSet<ClusterInputSlotNode>());
            clusterNodes.put(c, new ClusterNode(c, "" + z));
            z++;

        }

        // Add cluster edges
        for(Cluster c : cluster) {

            ClusterNode start = clusterNodes.get(c);

            for(Cluster succ : c.getSuccessors()) {
                ClusterNode end = clusterNodes.get(succ);
                if(end != null) {
                    ClusterEdge e = new ClusterEdge(start, end);
                    clusterEdges.add(e);
                    interClusterEdges.add(e);
                }
            }
        }

        for(Vertex v : graph.getVertices()) {

            Cluster c = v.getCluster();
            clusterNodes.get(c).addSubNode(v);

        }

        for(Link l : graph.getLinks()) {

            Port fromPort = l.getFrom();
            Port toPort = l.getTo();
            Vertex fromVertex = fromPort.getVertex();
            Vertex toVertex = toPort.getVertex();
            Cluster fromCluster = fromVertex.getCluster();
            Cluster toCluster = toVertex.getCluster();

            Port samePort = null;
            if(combine == Combine.SAME_INPUTS) {
                samePort = toPort;
            } else if(combine == Combine.SAME_OUTPUTS) {
                samePort = fromPort;
            }

            assert listsConnection.containsKey(fromCluster);
            assert listsConnection.containsKey(toCluster);

            if(fromCluster == toCluster) {
                listsConnection.get(fromCluster).add(l);
                clusterNodes.get(fromCluster).addSubEdge(l);
            } else {

                ClusterInputSlotNode inputSlotNode = null;
                ClusterOutputSlotNode outputSlotNode = null;

                if(samePort != null) {
                    outputSlotNode = clusterOutputSlotHash.get(fromCluster).get(samePort);
                    inputSlotNode = clusterInputSlotHash.get(toCluster).get(samePort);
                }

                boolean needInterClusterConnection = (outputSlotNode == null || inputSlotNode == null);

                if(outputSlotNode == null) {
                    outputSlotNode = new ClusterOutputSlotNode(clusterNodes.get(fromCluster), "Out " + fromCluster.toString() + " " + samePort.toString());
                    clusterOutputSlotSet.get(fromCluster).add(outputSlotNode);
                    ClusterOutgoingConnection conn = new ClusterOutgoingConnection(outputSlotNode, l);
                    outputSlotNode.setOutgoingConnection(conn);
                    clusterNodes.get(fromCluster).addSubEdge(conn);
                    if(samePort != null) {
                        clusterOutputSlotHash.get(fromCluster).put(samePort, outputSlotNode);
                    }

                    linkClusterOutgoingConnection.put(l, conn);
                } else {
                    linkClusterOutgoingConnection.put(l, outputSlotNode.getOutgoingConnection());
                }

                if(inputSlotNode == null) {
                    inputSlotNode = new ClusterInputSlotNode(clusterNodes.get(toCluster), "In " + toCluster.toString() + " " + samePort.toString());
                    clusterInputSlotSet.get(toCluster).add(inputSlotNode);
                }

                    ClusterIngoingConnection conn = new ClusterIngoingConnection(inputSlotNode, l);
                    inputSlotNode.setIngoingConnection(conn);
                    clusterNodes.get(toCluster).addSubEdge(conn);
                    if(samePort != null) {
                        clusterInputSlotHash.get(toCluster).put(samePort, inputSlotNode);
                    }

                    linkClusterIngoingConnection.put(l, conn);
                /*} else {
                    linkClusterIngoingConnection.put(l, inputSlotNode.getIngoingConnection());
                }*/

                InterClusterConnection interConn = new InterClusterConnection(outputSlotNode, inputSlotNode);
                linkInterClusterConnection.put(l, interConn);
                clusterEdges.add(interConn);
            }
        }

        for(Cluster c : cluster) {
            ClusterNode n = clusterNodes.get(c);
            subManager.doLayout(new LayoutGraph(n.getSubEdges(), n.getSubNodes()), clusterInputSlotSet.get(c), clusterOutputSlotSet.get(c));
            n.updateSize();
        }

        Set<Vertex> roots = new LayoutGraph(interClusterEdges).findRootVertices();
        for(Vertex v : roots) {
            assert v instanceof ClusterNode;
            ((ClusterNode)v).setRoot(true);
        }

        manager.doLayout(new LayoutGraph(clusterEdges), new HashSet<Vertex>(), new HashSet<Vertex>(), interClusterEdges);


        for(Link l : graph.getLinks()) {

            if(linkInterClusterConnection.containsKey(l)) {
                ClusterOutgoingConnection conn1 = linkClusterOutgoingConnection.get(l);
                InterClusterConnection conn2 = linkInterClusterConnection.get(l);
                ClusterIngoingConnection conn3 = linkClusterIngoingConnection.get(l);

                assert conn1 != null;
                assert conn2 != null;
                assert conn3 != null;

                List<Point> points = new ArrayList<Point>();

                points.addAll(conn1.getControlPoints());
                points.addAll(conn2.getControlPoints());
                points.addAll(conn3.getControlPoints());

                l.setControlPoints(points);
            }

        }
    }


    public void doRouting(LayoutGraph graph) {
    }
}
