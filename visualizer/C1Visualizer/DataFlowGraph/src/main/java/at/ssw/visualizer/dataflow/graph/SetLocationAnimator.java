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

import at.ssw.positionmanager.Vertex;
import at.ssw.positionmanager.impl.GraphVertex;
import java.awt.Point;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import org.netbeans.api.visual.animator.Animator;

/**
 * This Animator works fairly like the SetPreferredLocation Animator built within
 * the visual library. The special difference is, that it handles more than one element
 * per iteration.
 * The reason why it is implemented are the cluster nodes which are calculated from the
 * node positions have to be updated after each step.
 *
 * @author Stefan Loidl
 */
public class SetLocationAnimator extends Animator{

    private LinkedList<InstructionNodeWidget> widgets;
    private LinkedList<Point> startpoint;
    private LinkedList<Point> targetpoint;
    private InstructionNodeGraphScene scene;


    /**
     * VertexToWidget contains the Vertex with the target position, and the widget with the
     * start position.
     * It's essential that scene parameter is assigned- otherwise a nullpointer exception will occur!
     */
    public SetLocationAnimator(InstructionNodeGraphScene scene, Hashtable<Vertex, InstructionNodeWidget> VertexToWidget) {
        super(scene.getSceneAnimator());

        widgets= new LinkedList<InstructionNodeWidget>();
        startpoint= new LinkedList<Point>();
        targetpoint=new LinkedList<Point>();

        if(VertexToWidget==null) return;
        this.scene=scene;

        Point p1;
        Point p2;


        for(Map.Entry<Vertex, InstructionNodeWidget> e: VertexToWidget.entrySet()){
            p1=e.getKey().getPosition();

            if(p1==null) p1=new Point();
            //instruction widget inset correction: needed because used layouter do not support insets!
            else p1.translate(InstructionNodeWidget.BORDERINSET,InstructionNodeWidget.BORDERINSET);

            p2=e.getValue().getPreferredLocation();
            if(p2==null) p2=new Point();

            //Set vertex position to start position to avoid some line flickering
            e.getKey().setPosition(p2);

            if(p1.x!=p2.x || p1.y!= p2.y){
                startpoint.add(p2);
                targetpoint.add(p1);
                widgets.add(e.getValue());
            }
            //No Animation in this case- but dirty flags have to be set nevertheless!
            else{
                Vertex v=e.getKey();
                if(v instanceof GraphVertex) ((GraphVertex)v).setDirty(true);
            }
        }

    }

    /**
     * Starts the animation.
     */
    public void animate(){
        start();
    }

    protected void tick(double d) {
        Iterator<InstructionNodeWidget> w=widgets.iterator();
        Iterator<Point> start=startpoint.iterator();
        Iterator<Point> target=targetpoint.iterator();
        while(w.hasNext()){
            InstructionNodeWidget widget=w.next();
            Point p1=start.next();
            Point p2=target.next();
            Point p3;

            if(d>=1.0) p3=p2;
            else p3=new Point ((int) (p1.x + d * (p2.x - p1.x)),
                    (int) (p1.y + d * (p2.y - p1.y)));

            widget.setPreferredLocation(p3);
            //Update layoutGraph positions
            Vertex v=scene.getWidgetToVertex().get(widget);
            if(v!=null) v.setPosition(p3);
            if(v instanceof GraphVertex) ((GraphVertex)v).setDirty(true);
        }

        scene.refreshClusterWidgets();
    }




}
