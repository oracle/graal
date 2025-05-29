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
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;

import java.awt.Dimension;
import java.awt.Point;

/**
 * @author Ondřej Douda <ondrej.douda@oracle.com>
 */
public class ClusterSlotNode implements Vertex {
    //should stay (0, 0)
    private static final Dimension ZERO_DIMENSION = new Dimension();

    private Point position;
    private final Port inputSlot = new ClusterSlotNodePort();
    private final Port outputSlot = new ClusterSlotNodePort();
    private final ClusterNode blockNode;
    private final String id;
    private final boolean isInputPort;
    private ClusterConnection conn;

    @SuppressWarnings("LeakingThisInConstructor")
    private ClusterSlotNode(boolean isInputSlot, final ClusterNode n, String id) {
        this.blockNode = n;
        this.id = id;
        this.isInputPort = isInputSlot;

        n.addSubNode(this);
    }

    public static ClusterSlotNode makeInputSlotNode(final ClusterNode n, String id) {
        assert n != null;
        return new ClusterSlotNode(true, n, id);
    }

    public static ClusterSlotNode makeOutputSlotNode(final ClusterNode n, String id) {
        assert n != null;
        return new ClusterSlotNode(false, n, id);
    }

    public boolean isInputSlot() {
        return isInputPort;
    }

    public void setConnection(ClusterConnection c) {
        conn = c;
    }

    public ClusterConnection getConnection() {
        return conn;
    }

    @Override
    public boolean isRoot() {
        return isInputPort;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public Dimension getSize() {
        return ZERO_DIMENSION;
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    public Port getInputSlot() {
        return inputSlot;
    }

    public Port getOutputSlot() {
        return outputSlot;
    }

    @Override
    public Cluster getCluster() {
        return blockNode.getCluster();
    }

    @Override
    public int compareTo(Vertex o) {
        return toString().compareTo(o.toString());
    }

    @Override
    public boolean isVisible() {
        return blockNode.isVisible();
    }

    private class ClusterSlotNodePort implements Port {
        public ClusterSlotNodePort() {
        }

        @Override
        public Vertex getVertex() {
            return (this == inputSlot) == isInputPort ? blockNode : ClusterSlotNode.this;
        }

        @Override
        public Point getRelativePosition() {
            return (this == inputSlot) == isInputPort ? new Point(getPosition().x - blockNode.getPosition().x, 0) : new Point();
        }

        @Override
        public String toString() {
            return (this == inputSlot ? "InPort of " : "OutPort of ") + id;
        }
    }
}
