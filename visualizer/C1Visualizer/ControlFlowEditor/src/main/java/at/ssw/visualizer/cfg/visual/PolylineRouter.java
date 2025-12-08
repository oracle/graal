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
package at.ssw.visualizer.cfg.visual;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.router.Router;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.Widget;

/**
 * Router Class with Collision Detection
 * 
 *
 * The returned path is a straight line there is no node widget between the source and the target node,
 * if there are nodewidgets in between it tries to find a path around those abstacles to avoid collisions.
 * The algorithm for the search of this path is limited by a fixed number of iterations defined in 
 * NUMER_OF_ITERATIONS to return a result in reasonable time. If the calculation exceeds this limit the 
 * path returned contains at least 1 collision with a nodewidget.  
 */

public class PolylineRouter implements Router {
    private static final float FACTOR = 0.70f;
    
    private static final int HEXPAND = 3;
    private static final int VEXPAND = 3;
    private static final int NUMBER_OF_ITERATIONS = 8;   
    
    WidgetCollisionCollector collector;
    
     
    public PolylineRouter(WidgetCollisionCollector collector){
        this.collector=collector;
    }
   
  
    public List<Point> routeConnection(final ConnectionWidget widget) {       
        if(!widget.isVisible()) return Collections.<Point>emptyList();
   
        Anchor sourceAnchor = widget.getSourceAnchor();
        Anchor targetAnchor = widget.getTargetAnchor();
         
        if(sourceAnchor == null || targetAnchor == null) 
            return null;
        
        Point start = sourceAnchor.compute(widget.getSourceAnchorEntry()).getAnchorSceneLocation();
        Point end =   targetAnchor.compute(widget.getTargetAnchorEntry()).getAnchorSceneLocation();
        
        Widget sourceWidget = sourceAnchor.getRelatedWidget();
        Widget targetWidget = targetAnchor.getRelatedWidget();  
        
        
        if(sourceWidget == targetWidget){//reflexive edges doesnt need any path      
            return Collections.<Point>emptyList();
        }
           
        List<Widget> nodeWidgets = new ArrayList<Widget>();
        
        if(collector != null){
            collector.collectCollisions(nodeWidgets);    
            //source and target widget are not treatet as obstacle
            nodeWidgets.remove(sourceWidget);
            nodeWidgets.remove(targetWidget);
        }
                            
        List<Point> controlPoints = optimize(nodeWidgets, widget, start, end); 
              
        //size==2 => straight line
        //size==3 => ensures a collision between cp0 and cp2 therefore its not possible to simplify it
        if(controlPoints.size() > 3)
            controlPoints = simplify(nodeWidgets, controlPoints);
        
        return controlPoints;        
    }   
    
    
    private List<Point> simplify(Collection<Widget> nodeWidgets, List<Point> list) {        
        List<Point> result = new ArrayList<Point>();
        result.add( list.get(0) );//add startpoint
        for (int i = 1; i < list.size(); i++) {
            Point prev = list.get(i - 1);
            for (int j = i; j < list.size(); j++) {
                Point cur = list.get(j);
                if (!intersects(nodeWidgets, prev, cur)) {
                    i = j;
                }               
            }
            result.add(list.get(i));
        }      
        return result;
    }
    
    /**
     * Computates the Anchorposition like the Rectangular anchor for a
     * given widget as source/target and a controlpoint as opposit anchorposition
     */
     
    private Point computateAnchorPosition(Widget relatedWidget, Point controlPoint) {
        Rectangle bounds = relatedWidget.getBounds();
        Point relatedLocation = relatedWidget.getLocation();//center of the widget
       
        if (bounds.isEmpty ()  || relatedLocation.equals (controlPoint))
            return relatedLocation;

        float dx = controlPoint.x - relatedLocation.x;
        float dy = controlPoint.y - relatedLocation.y;

        float ddx = Math.abs (dx) / (float) bounds.width;
        float ddy = Math.abs (dy) / (float) bounds.height;

        float scale = 0.5f / Math.max (ddx, ddy);

        Point point = new Point (Math.round (relatedLocation.x + scale * dx), 
                Math.round (relatedLocation.y + scale * dy));
        return point;
    }
    
   
  
    private List<Point> optimize(Collection<Widget> nodeWidgets, ConnectionWidget connWidget, Point start, Point end) {
        
        List<Point> list = new ArrayList<Point>();
        list.add(start);
        list.add(end);
                           
        boolean progress = true;
        
        for (int j = 0; progress && j < NUMBER_OF_ITERATIONS ; j++) {
            progress = false;                  
            List<Point> newList = new ArrayList<Point>();              
            for (int i = 0; i < list.size() - 1 ; i++) {
                Point cur = list.get(i);
                Point next = list.get(i + 1);
                newList.add(cur);
                List<Point> intermediate = optimizeLine(nodeWidgets, cur, next);
                if (intermediate != null && intermediate.size() > 0) { 
                    progress = true;
                    newList.addAll(intermediate);//insert new controlpoints between cur and next
                } 
            }            
            newList.add(list.get(list.size()-1));//add endpoint of the polyline
            list = newList;   
            
        }
               
        if(list.size() > 2) {          
            Widget sourceNode = connWidget.getSourceAnchor().getRelatedWidget();
            Widget targetNode = connWidget.getTargetAnchor().getRelatedWidget();
            Rectangle sourceBounds = sourceNode.convertLocalToScene(sourceNode.getBounds());
            Rectangle targetBounds = targetNode.convertLocalToScene(targetNode.getBounds());
            sourceBounds.grow(HEXPAND, VEXPAND);
            
            /**
             * add only points which are not intersecting the source and the target 
             * widget bounds caused by invalid bad anchor positions. The first 
             * and the last point is ignored cause the anchor is recalculated 
             * anyway.
             */
            ArrayList<Point> tmp = new ArrayList<Point>();
            int listSize=list.size();
            tmp.add(list.get(0));
            int i=0;
            while(++i < listSize-1) {
                Point p = list.get(i);
                if(!sourceBounds.contains(p) || !targetBounds.contains(p))
                    tmp.add(p);               
            }
           
            tmp.add(list.get(i));
            if(tmp.size() < 3)
                return tmp;
            
            list=tmp;                    
            //calculate a proper anchor position using the second/penultimate controlpoint for start/end
            start = this.computateAnchorPosition(connWidget.getSourceAnchor().getRelatedWidget(), list.get(1));            
            end = this.computateAnchorPosition(connWidget.getTargetAnchor().getRelatedWidget(), list.get(list.size()-2));            
            list.set(0,start);           
            list.set(list.size()-1, end);         
        }          
        return list;
    }
 
    
    /**
     *  trys to optimize a line from p1 to p2 to avoid collisions with node widgets
     *  returns null if the line doesn`t intersect with any nodewidget
     *  or a list with immediate points between p1 and p2 to avoid collisions
     */
    private List<Point> optimizeLine(Collection<Widget> nodeWidgets, Point p1, Point p2) {        
        Line2D line = new Line2D.Double(p1, p2);
                     
        for(Widget w : nodeWidgets ) {            
            if( w.isVisible() ) {                              
                Rectangle r = w.convertLocalToScene(w.getBounds());             
                r.grow(HEXPAND, VEXPAND);
       
                if (!line.intersects(r)) continue;
                  
                Point location = w.getLocation();                
                int distx = (int) (r.width * FACTOR);
                int disty = (int) (r.height * FACTOR);
                
                int minIntersects = Integer.MAX_VALUE;
                int min = Integer.MAX_VALUE;
                List<Point> minSol = null;
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        if (i != 0 || j != 0) {                           
                            Point cur = new Point(location.x + i * distx, location.y + j * disty);
                            List<Point> list1 = new ArrayList<Point>();
                            list1.add(p1);
                            list1.add(cur);
                            list1.add(p2);                                                 
                            int crossProd = Math.abs(crossProduct(p1, cur, p2));
                            if (!intersects(w, list1)) {
                                Line2D line1 = new Line2D.Float(p1, cur);
                                Line2D line2 = new Line2D.Float(p2, cur);
                                int curIntersects = this.countNodeIntersections(nodeWidgets, line1, line2);
                                
                                if (curIntersects < minIntersects
                                        || (curIntersects == minIntersects && crossProd < min)) {
                                    minIntersects = curIntersects;
                                    min = crossProd;
                                    minSol = new ArrayList<Point>();
                                    minSol.add(cur);
                                }
                            }

                            if (i == 0 || j == 0) {
                                Point cur1, cur2;
                                if (i == 0) {
                                      cur1 = new Point(location.x + distx/2, location.y + j * disty);
                                      cur2 = new Point(location.x - distx/2, location.y + j * disty);
                                } else { // (j == 0) 
                                    cur1 = new Point(location.x + i * distx, location.y + disty/2);
                                    cur2 = new Point(location.x + i * distx, location.y - disty/2);
                                }

                                Point vec1 = new Point(p1.x - cur1.x, p1.y - cur1.y);
                                int offset1 = vec1.x * vec1.x + vec1.y * vec1.y;

                                Point vec2 = new Point(p1.x - cur2.x, p1.y - cur2.y);
                                int offset2 = vec2.x * vec2.x + vec2.y * vec2.y;

                                if (offset2 < offset1) {
                                    Point tmp = cur1;
                                    cur1 = cur2;
                                    cur2 = tmp;
                                }

                                List<Point> list2 = new ArrayList<Point>();
                                list2.add(p1);
                                list2.add(cur1);
                                list2.add(cur2);
                                list2.add(p2);

                                int cross1 = crossProduct(p1, cur1, cur2);
                                int cross2 = crossProduct(cur1, cur2, p2);

                                if (cross1 > 0) {
                                    cross1 = 1;
                                } else if (cross1 < 0) {
                                    cross1 = -1;
                                }
                                
                                if (cross2 > 0) {
                                    cross2 = 1;
                                } else if (cross2 < 0) {
                                    cross2 = -1;
                                }
                                if ((cross1 == cross2 || cross1 == 0 || cross2 == 0) && !intersects(w, list2)) {                                 
                                    Line2D line1 = new Line2D.Float(p1, cur1);
                                    Line2D line2 = new Line2D.Float(cur1, cur2);
                                    Line2D line3 = new Line2D.Float(p2, cur2);
                                    int curIntersects = this.countNodeIntersections(nodeWidgets, line1, line2, line3);
                                   
                                    // This is a bit better
                                    crossProd--;

                                    if (curIntersects < minIntersects
                                            || (curIntersects == minIntersects && crossProd < min)) {
                                        minIntersects = curIntersects;
                                        min = crossProd;
                                        minSol = new ArrayList<Point>();
                                        minSol.add(cur1);
                                        minSol.add(cur2);                                       
                                    }
                                }
                            }
                        }
                    }
                }           
                if (minSol != null) {      
                    return minSol;
                } 
            }
        }
        return null;
    }
    
    
   
    private int countNodeIntersections(Collection<Widget> nodeWidgets, Line2D... lines){
        int count=0;     
        for(Widget nw : nodeWidgets){
            if(nw.isVisible()) {
                Rectangle bounds = nw.convertLocalToScene(nw.getBounds());
                for( Line2D line : lines){
                    if(line.intersects(bounds))
                        count++;
                }
            }
        }
        return count;
    }
    
    
    private boolean intersects(Collection<Widget> nodeWidgets, Point start, Point end) {
        List<Point> pointlist = new ArrayList<Point>();
        pointlist.add(start);
        pointlist.add(end);

        for(Widget w : nodeWidgets){
            if(w.isVisible() && intersects(w, pointlist)) {
                return true;
            }
        }
        return false;
    }
        
    
    private boolean intersects(Widget w, List<Point> list) {
        Rectangle r = w.convertLocalToScene(w.getBounds());
        r.grow(HEXPAND, VEXPAND);
        return intersects(list, r);
    }
    
    
    private boolean intersects(List<Point> list, Rectangle rect){
        for(int i=1; i < list.size(); i++) {
            Point cur = list.get(i-1);
            Point next = list.get(i);   
            if(rect.intersectsLine(cur.x, cur.y, next.x, next.y))
                return true;
        }
        return false;
    }

    
    private int crossProduct(Point p1, Point p2, Point p3) {
        Point off1 = new Point(p1.x - p2.x, p1.y - p2.y);
        Point off2 = new Point(p3.x - p2.x, p3.y - p2.y);
        return (off1.x * off2.y - off1.y * off2.x);
    }
        
}
