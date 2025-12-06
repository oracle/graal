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
package at.ssw.visualizer.dataflow.graph;

import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Port;
import at.ssw.positionmanager.Vertex;
import at.ssw.positionmanager.impl.GraphVertex;
import java.awt.Point;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import org.netbeans.api.visual.router.Router;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Stefan Loidl
 */
public class DirectLineRouter implements Router{

    private InstructionNodeGraphScene scene;

    /** Creates a new instance of DirectLineRouter */
    public DirectLineRouter(InstructionNodeGraphScene scene) {
        this.scene=scene;
    }

    public List<Point> routeConnection(ConnectionWidget widget) {
        List<Point> cp=new LinkedList<Point>();

        LayoutGraph graph=scene.getLayoutGraph();
        Hashtable<Widget, Vertex> WidgetToVertex=scene.getWidgetToVertex();
        if(graph==null || WidgetToVertex==null) return cp;

        Vertex v1=WidgetToVertex.get(widget.getSourceAnchor().getRelatedWidget());
        Vertex v2=WidgetToVertex.get(widget.getTargetAnchor().getRelatedWidget());

        if(v1==null || v2==null) return cp;

        //do the routing
        scene.getExternalLayouter().doRouting(graph);

        //Reset dirty state
        for(Vertex v: graph.getVertices()){
            if(v instanceof GraphVertex) ((GraphVertex)v).setDirty(false);
        }

        for(Port p: graph.getOutputPorts(v1)){
            for(Link l: graph.getPortLinks(p)){
                if(l.getTo().getVertex()==v2){
                    cp=l.getControlPoints();
                }
            }
        }


        return cp;
    }

}
