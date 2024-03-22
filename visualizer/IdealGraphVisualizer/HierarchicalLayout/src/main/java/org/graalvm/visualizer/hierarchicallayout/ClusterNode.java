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
import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;

import java.awt.Dimension;
import java.awt.Point;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ClusterNode implements Vertex {
    public static final int BORDER = 20;

    private Cluster cluster;
    private Port inputSlot;
    private Port outputSlot;
    private final Set<Vertex> subNodes;
    private Dimension size;
    private Point position;
    private final Set<Link> subEdges;
    private boolean root;
    private final String name;
    boolean visible;

    public ClusterNode(Cluster cluster, String name) {
        this.subNodes = new HashSet<>();
        this.subEdges = new HashSet<>();
        this.cluster = cluster;
        position = new Point(-BORDER, -BORDER);
        this.name = name;
        visible = cluster.isVisible();
    }

    public void addSubNode(Vertex v) {
        subNodes.add(v);
        visible = visible || v.isVisible();
    }

    public void addSubEdge(Link l) {
        subEdges.add(l);
    }

    public Set<Link> getSubEdges() {
        return Collections.unmodifiableSet(subEdges);
    }

    public void updateSize(Dimension graphSize) {
        if (visible && graphSize.width > 0 && graphSize.height > 0) {
            size = new Dimension(graphSize.width + (2 * BORDER), graphSize.height + (2 * BORDER));
        } else {
            assert (subNodes.isEmpty() || subNodes.stream().allMatch((n) -> !n.isVisible())) && !visible;
            size = new Dimension(3 * BORDER, 3 * BORDER);
        }

        final ClusterNode widget = this;
        inputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                return new Point(size.width / 2, 0);
            }

            @Override
            public Vertex getVertex() {
                return widget;
            }
        };

        outputSlot = new Port() {

            @Override
            public Point getRelativePosition() {
                return new Point(size.width / 2, 0);
            }

            @Override
            public Vertex getVertex() {
                return widget;
            }
        };
    }

    public Port getInputSlot() {
        return inputSlot;

    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    @Override
    public Dimension getSize() {
        return size;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    @Override
    public void setPosition(Point pos) {
        int dx = pos.x - position.x;
        int dy = pos.y - position.y;
        this.position = pos;
        for (Vertex n : subNodes) {
            n.getPosition().translate(dx, dy);
        }
        Set<Point> edgesPoints = new HashSet<>();
        for (Link e : subEdges) {
            edgesPoints.addAll(e.getControlPoints());
        }
        for (Point p : edgesPoints) {
            if (p != null) {
                p.translate(dx, dy);
            }
        }
    }

    @Override
    public Cluster getCluster() {
        return cluster;
    }

    public void setCluster(Cluster c) {
        cluster = c;
    }

    public void setRoot(boolean b) {
        root = b;
    }

    @Override
    public boolean isRoot() {
        return root;
    }

    @Override
    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return name;
    }

    public Set<? extends Vertex> getSubNodes() {
        return subNodes;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }
}
