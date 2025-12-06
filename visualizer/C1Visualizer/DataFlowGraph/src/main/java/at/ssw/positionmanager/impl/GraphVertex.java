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
package at.ssw.positionmanager.impl;

import at.ssw.positionmanager.Cluster;
import at.ssw.positionmanager.Vertex;
import at.ssw.visualizer.dataflow.graph.InstructionNodeWidget;
import java.awt.Dimension;
import java.awt.Point;

/**
 * Implements a Vertex which is used as node representation in the open layouter definition of
 * SSW.
 *
 * @author Stefan Loidl
 */
public class GraphVertex implements Vertex {

    private Cluster cluster=null;
    private Point position=null;
    private boolean dirty=false;
    private boolean fixed=false;
    private boolean marked=false;

    private InstructionNodeWidget widget;
    protected int index;

    /** Creates a new instance of GraphVertex */
    public GraphVertex(int index, Cluster cluster, Point position, InstructionNodeWidget widget) {
        assert widget !=null;
        this.index=index;
        this.cluster=cluster;
        this.position=position;
        this.widget=widget;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Dimension getSize() {
        return widget.getBounds().getSize();
    }

    public Point getPosition() {
        Point p=(Point)position.clone();
        p.translate(-InstructionNodeWidget.BORDERINSET,-InstructionNodeWidget.BORDERINSET);
        return p;
    }

    public void setPosition(Point p) {
        position=p;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean d){
        dirty=d;
    }

    public boolean isRoot() {
        return false;
    }

    public int compareTo(Vertex o) {
        if(o instanceof GraphVertex){
            return index-((GraphVertex)o).index;
        }
        return this.hashCode()-o.hashCode();
    }

    public boolean isExpanded() {
        return widget.isExpanded();
    }

    public boolean isFixed() {
        return fixed;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setFixed(boolean b){
        fixed=b;
    }

    public void setMarked(boolean b){
        marked=b;
    }

}
