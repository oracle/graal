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

import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Port;
import at.ssw.positionmanager.Vertex;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Set;

/**
 * Implements a Port in the open layouter model of SSW.
 *
 * @author Stefan Loidl
 */
public class GraphPort implements Port{

    private Vertex vertex;
    private LayoutGraph graph=null;

    /** Creates a new instance of GraphPort */
    public GraphPort(Vertex v) {
        this.vertex=v;
    }

    public Vertex getVertex() {
        return vertex;
    }

    public void setLayoutGraph(LayoutGraph graph){
        this.graph=graph;
    }

    public Point getRelativePosition() {
        Dimension d=vertex.getSize();
        int y=0, x=0;

        if(graph==null) return new Point(d.width/2,d.height/2);

        Set<Port> ports=graph.getInputPorts(vertex);
        if(!ports.contains(this)){
            ports=graph.getOutputPorts(vertex);
            y=d.height;
        }

        int offset= d.width/(ports.size()+1);
        boolean found=false;
        for(Port p:ports){
            x+=offset;
            if(p==this) {
                found=true;
                break;
            }
        }
        if(found) return new Point(x,y);
        else return new Point(d.width/2,d.height/2);
    }
}
