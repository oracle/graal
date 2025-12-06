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
 * 
 * This class intend to solve the same problem as the class PolyLineRouter but
 * uses slightly different methods.
 * + uses another algorithm for solving the collision.
 * + the obstacle bounds are calulated in advance to avoid a recalculation.
 * - there is no heuristic, the shortest path around the widget is choosen asap.
 * 
 * 
 *  
 */

public class PolylineRouterV2 implements Router {    
    private static final int HEXPAND = 3;
    private static final int VEXPAND = 3;
    private static final int NUMBER_OF_ITERATIONS = 8;   
    
    WidgetCollisionCollector collector;
    
     
    public PolylineRouterV2(WidgetCollisionCollector collector){
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
        
        
        Point srcCenter = this.getSceneLocation(sourceWidget);
        Point tarCenter = this.getSceneLocation(targetWidget);
        
        List<Widget> widgetObstacles = new ArrayList<Widget>();
        
        if(collector != null){
            collector.collectCollisions(widgetObstacles);                    
        }
        
        List<Rectangle> obstacles = new ArrayList<Rectangle>(widgetObstacles.size());
        this.collectObstacles(obstacles, widgetObstacles, widget);
       
                    
        List<Point> controlPoints = optimize(obstacles, srcCenter, tarCenter);     
//        size==2 => straight line
//        size==3 => ensures a collision between cp0 and cp2 therefore its not possible to simplify it
        if(controlPoints.size() > 3){
            Point rstart = this.computateAnchorPosition(sourceWidget, controlPoints.get(1));            
            Point rend  = this.computateAnchorPosition(targetWidget, controlPoints.get(controlPoints.size()-2)); 
            controlPoints.set(0, rstart);           
            controlPoints.set(controlPoints.size()-1, rend);       
            controlPoints = simplify(obstacles, controlPoints);
        } else if (controlPoints.size()>=2){
                //use old points
            controlPoints.set(0, start);           
            controlPoints.set(controlPoints.size()-1, end);           
        
        }
        return controlPoints;        
    }  
    
    
    private int collectObstacles(List<Rectangle> colrects, List<Widget> colwidgets , ConnectionWidget cw){        
        int count=0;                        
        Anchor sourceAnchor = cw.getSourceAnchor();
        Anchor targetAnchor = cw.getTargetAnchor();
        Widget sourceWidget = sourceAnchor.getRelatedWidget();
        Widget targetWidget = targetAnchor.getRelatedWidget(); 
        Point start = sourceAnchor.compute(cw.getSourceAnchorEntry()).getAnchorSceneLocation();
        Point end =   targetAnchor.compute(cw.getTargetAnchorEntry()).getAnchorSceneLocation();
                        
        for(Widget w : colwidgets){
            
            if(w==sourceWidget || w == targetWidget) continue;
           
            Rectangle r = w.convertLocalToScene(w.getBounds());
            r.grow(HEXPAND, VEXPAND);
            if(r.intersectsLine(start.x,start.y,end.x,end.y))
                count++;
            colrects.add(r);
        }
        return count;
    }
    
    
    private Point center (Rectangle bounds) {
        return new Point (bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    }

    /**
     * Returns the scene location of a related widget.
     * bounds might be null if the widget was not added to the scene
     * @return the scene location; null if no related widget is assigned
     */
    public Point getSceneLocation (Widget relatedWidget) {
        if (relatedWidget != null) {
            Rectangle bounds = relatedWidget.getBounds ();
            if(bounds != null)
                return center(relatedWidget.convertLocalToScene(bounds));           
        }
        return null;
    }
       
    /**
     * Computates the Anchorposition like the Rectangular anchor for a
     * given widget as source/target and a controlpoint as opposit anchorposition
     */
     
    private Point computateAnchorPosition(Widget relatedWidget, Point controlPoint) {
        Rectangle bounds = relatedWidget.getBounds();
        
        //todo: fix, center of widget must be cacluated trough the bounds 
        //since there are some wheird widgets where the location is not the center of the widget
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
    
    private List<Point> simplify(List<Rectangle> obstacles, List<Point> list) {        
        List<Point> result = new ArrayList<Point>(list.size());
        result.add( list.get(0) );//add startpoint
        for (int i = 1; i < list.size(); i++) {
            Point prev = list.get(i - 1);
            for (int j = i; j < list.size(); j++) {
                Point cur = list.get(j);
                if (!intersects(obstacles, prev, cur)) {
                    i = j;
                }               
            }
            result.add(list.get(i));
        }      
        return result;
    }
  
    private List<Point> optimize(List<Rectangle> nodeWidgets, Point start, Point end) {
        
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
        
        return list;
    }
 
  
    /**
     *  trys to optimize a line from p1 to p2 to avoid collisions with rectangles
     *  returns null if the line doesn`t intersect with any nodewidget or
     *  if the obstacles are overlapping
     *  ----------------------------------------------------------------------
     *  if the collision is solved it returns a list with immediate points 
     *  between p1 and p2. The points are taken from hull points of and grown 
     *  rectangle.
     */
    private List<Point> optimizeLine(List<Rectangle> obstacles, Point p1, Point p2) {        
        Line2D line = new Line2D.Double(p1, p2);
        boolean inbounds=false;
        Rectangle ibr=null;
        ArrayList<Point> sol = new ArrayList<Point>();
        boolean leftIntersection;
        boolean rightIntersection; 
        boolean bottomIntersection; 
        boolean topIntersection; 
        Point interLeft=new Point();
        Point interRight=new Point();
        Point interBot=new Point();
        Point interTop=new Point();
        
    
        
        for(Rectangle r : obstacles ) {                      
            if (!line.intersects(r)) continue;
            
            int w=r.width+2;
            int h=r.height+2;
            Point topLeft = r.getLocation();
            topLeft.x-=1;
            topLeft.y-=1;
            Point topRight = new Point(topLeft.x+w, topLeft.y);
            Point bottomLeft = new Point(topLeft.x, topLeft.y+h);
            Point bottomRight = new Point(topRight.x, bottomLeft.y);  
            leftIntersection = findIntersectionPoint(p1, p2, topLeft, bottomLeft, interLeft);
            rightIntersection = findIntersectionPoint(p1, p2, topRight, bottomRight, interRight);
            bottomIntersection = findIntersectionPoint(p1, p2, bottomLeft, bottomRight, interBot);
            topIntersection = findIntersectionPoint(p1, p2, topLeft, topRight, interTop);
            
            //Intersection points are not used yet. This could be actually a 
            //good approach to avoid additional collisions because it would be
            //still the same vector.

            if(leftIntersection) {                      
                if(topIntersection) {//left and top   
                    sol.add(topLeft);                       
                }                        
                else if(bottomIntersection){//left and bottom
                    sol.add(bottomLeft);                                   
                }
                else if(rightIntersection){//left and right
                    double disttl = topLeft.distance(p1);
                    double distbl = bottomLeft.distance(p1);
                    if(disttl > distbl){
                        //pass at the bottom
                        double distbr = bottomRight.distance(p1);
                        if(distbl < distbr){
                            //from the left to the right
                            sol.add(bottomLeft);
                            sol.add(bottomRight);
                        } else {
                            //from the right to the left
                            sol.add(bottomRight);
                            sol.add(bottomLeft);
                        }
                    } else {
                        //pass at the top
                        double disttr = topRight.distance(p1);
                        if(disttl < disttr){
                            //from the left to the right
                            sol.add(topLeft);
                            sol.add(topRight);
                        } else {
                            //from the right to the left
                            sol.add(topRight);
                            sol.add(topLeft);
                        }
                    }                        
                 } else {//only left => inside bounds 
                    inbounds=true;                                  
                 } 
            } else if (rightIntersection) {                
                if(topIntersection) {//right and top
                    sol.add(topRight);
                }
                else if(bottomIntersection){//right and bottom
                    sol.add(bottomRight);
                } else { //only right => inside the bounds
                    inbounds=true;                             
                }                  
            } else if (topIntersection && bottomIntersection) {//top and bottom
                double disttop = interTop.distance(p1);
                double distbot = interBot.distance(p1);
                if(disttop < distbot ){
                    //from the top to the bottom
                    double distleft = interTop.distance(topLeft);
                    double distright = interTop.distance(topRight);
                    if(distleft < distright){
                        //pass left
                        sol.add(topLeft);
                        sol.add(bottomLeft);
                    } else {
                        //pass right
                        sol.add(topRight);
                        sol.add(bottomRight);
                    }   
                } else {
                    //from the bottom to the top
                    double distleft = interBot.distance(bottomLeft);
                    double distright = interBot.distance(bottomRight);
                    if(distleft < distright){
                        //pass left
                        sol.add(bottomLeft);
                        sol.add(topLeft);
                    } else {
                        //pass right
                        sol.add(bottomRight);
                        sol.add(topRight);
                    }   
                } 
            } else {//only bottom or only top
                inbounds=true;                 
            } /* ENDIF */
            
            //breakpoint <-- collision detected
            
            if(sol.size()>0) {//solution found
                assert(!inbounds);
                return sol;
            } else { //no solution found=> inbounds
                assert(inbounds);
                assert(sol.size()==0);
                //handle collision or just skip it and search for the next collisionj               
                ibr=r;
                //jump out of the loop to able to interate over the obstacles
                break;
            } 
        }/* end foreach obstacle */
        
        if(inbounds || ibr != null){
            assert(inbounds);
            assert(ibr!=null);        
        }       
        return null;//no collison found
    }/* end optimizeLine */
    
 
  
    //check intersection between line p0->p1 for a given set of obstacles
    private static boolean intersects(List<Rectangle> obstacles, Point p0, Point p1) {
        for(Rectangle r : obstacles){
            if(r.intersectsLine(p0.x, p0.y, p1.x, p1.y))
                return true;         
        }
        return false;
    }
    
   
    private boolean findIntersectionPoint(
            Point p0, Point p1, Point p2, Point p3, Point pI) {
        float q = (p0.y - p2.y)*(p3.x - p2.x) - (p0.x - p2.x)*(p3.y - p2.y);
        float d = (p1.x - p0.x)*(p3.y - p2.y) - (p1.y - p0.y)*(p3.x - p2.x);
        
        //parallel ?
        if(d==0) return false;
          
        float r = q / d;
        q = (p0.y - p2.y)*(p1.x - p0.x) - (p0.x - p2.x)*(p1.y - p0.y);
        
        float s = q / d;
        if(r<0 || r>1 || s<0 || s>1) return false;
        
        pI.x = p0.x + (int) (0.5f + r * (p1.x - p0.x));
        pI.y = p0.y + (int) (0.5f + r * (p1.y - p0.y));
        return true;
    }    
}
