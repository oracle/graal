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
package at.ssw.dataflow.layout;

import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Port;
import at.ssw.positionmanager.Vertex;
import java.awt.Point;
import java.util.LinkedList;

/**
 * This class is a standard implementation of a direct routing algorithm, making it
 * possible to seperate two lines between the same two nodes.
 *
 * @author Stefan Loidl
 */
public class RoutingHelper {

    //This value is needed because of the definition of inset values- outranging the
    //position values of the original widget- if no inset is used- value is 0.
    private static final int BORDERINSETCORRECT=0;
    //Space between two twins lines
    private static final int SEPERATETWINLINES=5;

    /**
     * Does the routing for the graph.
     */
    public static void doRouting(LayoutGraph graph){
        if(graph==null) return;
        for(Link l: graph.getLinks()){
            boolean twin=false;
            Vertex from=l.getFrom().getVertex();
            Vertex to=l.getTo().getVertex();

            if(!(from.isDirty() || to.isDirty())) continue;

            //Search for twin link (in diffent direction!)
            for(Port p:graph.getInputPorts(from)){
                for(Link l2: graph.getPortLinks(p)){
                    if(l2.getFrom().getVertex()==l.getTo().getVertex()) twin=true;
                }
            }

            routeLink(l,twin);
        }
    }

    /**
     * Does the routing for the link l. The boolean defines if a second link exists connecting
     * the two verticles.
     */
    private static void routeLink(Link l, boolean hasTwin){
        Vertex v1=l.getFrom().getVertex();
        Vertex v2=l.getTo().getVertex();
        Point p1=(Point)v1.getPosition().clone();
        Point p2=(Point)v2.getPosition().clone();

        //Translate to the center
        p1.translate(-BORDERINSETCORRECT+ v1.getSize().width/2,-BORDERINSETCORRECT+ v1.getSize().height/2);
        p2.translate(-BORDERINSETCORRECT+ v2.getSize().width/2, -BORDERINSETCORRECT+ v2.getSize().height/2);


        //Handle the six cases possible:
        //1+2. x- Values are identical
        if(p1.x==p2.x){
            int shift=0;
            if(p1.y>p2.y){
                if(hasTwin) shift=SEPERATETWINLINES;
                p1.translate(shift,-v1.getSize().height/2);
                p2.translate(shift,v2.getSize().height/2);
            }
            else{
                if(hasTwin) shift=-SEPERATETWINLINES;
                p1.translate(shift,v1.getSize().height/2);
                p2.translate(shift,-v2.getSize().height/2);
            }
        }
        else{
            //gradient of the line
            double k=((double)(p1.y-p2.y))/(p1.x-p2.x);
            double gk= Math.atan(k);
            double twdx=0, twdy=0;

            //If x value of p1 <= p2 then the two are
            //simply swapped.
            boolean swap=false;
            Point p3;
            Vertex v3;

            if(hasTwin){
                twdx=Math.abs(Math.sin(gk)*SEPERATETWINLINES);
                twdy=Math.abs(Math.cos(gk)*SEPERATETWINLINES);
            }

            if(p1.x <= p2.x){
                swap=true;
                p3=p1; p1=p2; p2=p3;
                v3=v1; v1=v2; v2=v3;
                twdx*=-1; twdy*=-1;
            }

            if(p1.y>p2.y){
                //lower quarter with respect to p1
                p1.translate((int)twdx,-(int)twdy);
                p2.translate((int)twdx,-(int)twdy);

                double x=((double)v2.getSize().height/2.0)/Math.tan(gk);
                if(Math.abs(x)<=v2.getSize().width/2){
                    p2.translate((int)x,v2.getSize().height/2);
                } else{
                    x=(Math.tan(gk)*v2.getSize().width)/2.0;
                    p2.translate(v2.getSize().width/2,(int)x);
                }

                x=(Math.tan(Math.PI/2-gk)*v1.getSize().height)/2.0;
                if(Math.abs(x)<=v1.getSize().width/2){
                    p1.translate(-(int)x,-v1.getSize().height/2);
                } else{
                    x=((double)(v1.getSize().width)/2.0)/Math.tan(Math.PI/2-gk);
                    p1.translate(-v1.getSize().width/2,-(int)x);
                }

            }else{
                //upper quarter with respect to p1
                p1.translate((int)-twdx,(int)-twdy);
                p2.translate((int)-twdx,(int)-twdy);

                double x=((double)v1.getSize().height/2.0)/Math.tan(gk);
                if(Math.abs(x)<=v1.getSize().width/2){
                    p1.translate((int)x,v1.getSize().height/2);
                } else{
                    x=(Math.tan(gk)*v1.getSize().width)/2;
                    p1.translate(-v1.getSize().width/2,-(int)x);
                }

                x=(Math.tan(Math.PI/2-gk)*v2.getSize().height)/2.0;
                if(Math.abs(x)<=v2.getSize().width/2){
                    p2.translate(-(int)x,-v2.getSize().height/2);
                } else{
                    x=((double)(v2.getSize().width)/2.0)/Math.tan(Math.PI/2-gk);
                    p2.translate(v2.getSize().width/2,(int)x);
                }
            }
            if(swap){
                p3=p1; p1=p2; p2=p3;
                v3=v1; v1=v2; v2=v3;
            }


        }

        LinkedList<Point> cp=new LinkedList<Point>();
        cp.add(p1);
        cp.add(p2);
        l.setControlPoints(cp);
    }

}
