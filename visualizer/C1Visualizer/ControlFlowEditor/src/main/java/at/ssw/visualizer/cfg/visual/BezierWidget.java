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

import org.netbeans.api.visual.widget.ConnectionWidget;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.List;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.graph.GraphScene;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;


/**
 *  In comparison to the default ConnectionWidget this class is able to connect 
 *  Widgets with a curve instead of a straight line sequence. Between two control
 *  points a curve is painted as cubic bezier curve the control points are 
 *  calculated automaticaly they depend on the position of the prior- and the 
 *  following control points.
 *  In conjunction with a suitable router the connection will be a straight line 
 *  or a curve depending on the amount and the position of the controlpoints. 
 *  Controlpoints supplied by the router, are treated as curve intersection points.
 *  For Reflexive edges the router doesn`t  need to supply controlpoints, they 
 *  get painted by this class automatically. If the router supplys more as 2 
 *  control points for a recursive edge the edge gets painted with the default
 *  curve approximation algorithm.
 */
public class BezierWidget extends ConnectionWidget {
    private static final double BEZIER_SCALE = 0.3;     
    private static final double ENDPOINT_DEVIATION = 3;//curve endpoint approximation accuracy
   
    private GraphScene scene=null;
 
    public BezierWidget(Scene scene) {
        super(scene);          
    }
    
    public BezierWidget(GraphScene scene) {
        super(scene);   
        this.scene=scene;        
    }
 
    
    private boolean isReflexive(){
        return getSourceAnchor().getRelatedWidget() == getTargetAnchor().getRelatedWidget();
    }
    
    
    @Override
    protected Rectangle calculateClientArea() {              
        Rectangle bounds = null;
        if(this.getControlPoints().size()>0){           
            for(Point p : this.getControlPoints()){
              if(bounds==null)
                  bounds = new Rectangle(p);
              else
                  bounds.add(p);
            }
            bounds.grow(5,5);         
        }    
        if(isReflexive()){
            Widget related = this.getTargetAnchor().getRelatedWidget();
            bounds = related.convertLocalToScene(related.getBounds());
            bounds.grow(10, 10);
        }
        if(bounds==null)
            bounds = super.calculateClientArea();
           
        return bounds;
    }
    
 
    
    //returns prefered location for an edge -1 for left and 1 for right
    private int edgeBalance(Widget nodeWidget) {   
        if(scene == null)
            return 1;
        
        Point nodeLocation = nodeWidget.getLocation();
        int left = 0, right = 0;

        Object node = scene.findObject(nodeWidget);
    
        for(Object e : scene.findNodeEdges(node, true, true)) {//inputedges
            ConnectionWidget cw = (ConnectionWidget) scene.findWidget(e);
            
            if(cw != this) {                
                Widget targetNodeWidget = cw.getTargetAnchor().getRelatedWidget();

                Point location;
                if(targetNodeWidget == nodeWidget) {
                    Widget sourceNodeWidget = cw.getSourceAnchor().getRelatedWidget();
                    location = sourceNodeWidget.getLocation();
                } else {
                    location = targetNodeWidget.getLocation();
                }

                if(location.x < nodeLocation.x)
                    left++;
                else 
                    right++;
            }
        }    
        if(left < right)
            return -1;
        else
            return 1;
    } 
    
    
    
    
    /**
     * if the edge is reflexive its painted as a cyclic edge
     * if there are 2 controlpoints the connection is painted as a straight line from the source to the targetanchor
     * if there are more as 2 controlpoints the connection path between 2 control points is painted as bezier curve
     */
       
    @Override
    protected void paintWidget () {  

        List<Point> contrPoints = this.getControlPoints();
        int listSize = contrPoints.size();
                
        Graphics2D gr = getGraphics ();
        
        if (listSize <= 2) {
            if(isReflexive()) { //special case for reflexive connection widgets    
                Widget related = this.getTargetAnchor().getRelatedWidget();
                int position = this.edgeBalance(related);
                Rectangle bounds = related.convertLocalToScene(related.getBounds());
                gr.setColor (getLineColor()); 
                Point first = new Point();
                Point last = new Point();
                double centerX = bounds.getCenterX();
                first.x = (int) (centerX + bounds.width / 4);          
                first.y = bounds.y + bounds.height;
                last.x = first.x;
                last.y = bounds.y;

                gr.setStroke(this.getStroke());

                double cutDistance = this.getTargetAnchorShape().getCutDistance();
                double anchorAngle = Math.PI/-3.0;
                double cutX = Math.abs(Math.cos(anchorAngle)*cutDistance); 
                double cutY = Math.abs(Math.sin(anchorAngle)*cutDistance);
                int ydiff=first.y-last.y; 
                int endy = -ydiff;
                double height=bounds.getHeight();
                double cy = height/4.0;
                double cx=bounds.getWidth()/5.0;
                double dcx = cx*2;
                GeneralPath gp = new GeneralPath();
                gp.moveTo(0, 0);
                gp.quadTo(0, cy, cx, cy);
                gp.quadTo(dcx, cy, dcx, -height/2.0);
                gp.quadTo(dcx, endy - cy, cy, -(cy+ydiff));
                gp.quadTo(cutX*1.5, endy - cy, cutX, endy-cutY);   

                AffineTransform af = new AffineTransform();           
                AnchorShape anchorShape = this.getTargetAnchorShape();           

                if(position < 0) {
                    first.x = (int) (centerX - bounds.width / 4);        
                    af.translate(first.x, first.y);
                    af.scale(-1.0, 1.0);
                    last.x = first.x;
                } else {
                    af.translate(first.x, first.y);
                }
                Shape s = gp.createTransformedShape(af);  
                gr.draw(s);

                if (last != null) {
                    AffineTransform previousTransform = gr.getTransform ();
                    gr.translate (last.x, last.y);  

                    if(position < 0)
                        gr.rotate(Math.PI - anchorAngle);
                    else                  
                        gr.rotate (anchorAngle);

                    anchorShape.paint (gr, false);
                    gr.setTransform (previousTransform);
                }                                      
                   
            } else {
                super.paintWidget();
            }
            return;
        }
           
        //bezier curve... 
        GeneralPath curvePath = new GeneralPath();
        Point lastControlPoint = null;
        double lastControlPointRotation = 0.0;
       
        Point prev = null;                 
        for (int i = 0; i < listSize - 1; i++) {
            Point cur = contrPoints.get(i);
            Point next = contrPoints.get(i + 1);
            Point nextnext = null;
            if (i < listSize - 2) {
                nextnext = contrPoints.get(i + 2);
            }     
            
            double len = cur.distance(next);                
            double scale = len * BEZIER_SCALE;     
            Point bezierFrom = null;//first ControlPoint         
            Point bezierTo = null;//second ControlPoint
            
            if (prev == null) {
                //first point 
                curvePath.moveTo(cur.x, cur.y);//startpoint
                bezierFrom = cur;              
            } else {            
                bezierFrom = new Point(next.x - prev.x, next.y - prev.y);
                bezierFrom = scaleVector(bezierFrom, scale);
                bezierFrom.translate(cur.x, cur.y); 
            }
       
            if (nextnext == null) {//next== last point (curve to)               
                lastControlPoint=next;  
                bezierTo = next;//set 2nd intermediate point to endpoint              
                GeneralPath lastseg = this.subdivide(cur, bezierFrom, bezierTo, next);
                if(lastseg != null)
                    curvePath.append(lastseg, true);
                break;                
            } else {
                bezierTo = new Point(cur.x - nextnext.x, cur.y - nextnext.y);
                bezierTo = scaleVector(bezierTo, scale);
                bezierTo.translate(next.x, next.y); 
            }
          
            curvePath.curveTo(
                    bezierFrom.x, bezierFrom.y,//controlPoint1
                    bezierTo.x, bezierTo.y,//controlPoint2
                    next.x,next.y
            );        
            prev = cur;
        }
        Point2D cur = curvePath.getCurrentPoint();
        Point next = lastControlPoint;
        
        lastControlPointRotation = //anchor anchorAngle 
            Math.atan2 (cur.getY() - next.y, cur.getX() - next.x);
                         
        Color previousColor = gr.getColor();
        gr.setColor (getLineColor());    
        Stroke s = this.getStroke();
        gr.setStroke(s);
        gr.setColor(this.getLineColor());
        gr.draw(curvePath);
                      
        AffineTransform previousTransform = gr.getTransform ();    
        gr.translate (lastControlPoint.x, lastControlPoint.y);       
        gr.rotate (lastControlPointRotation);
        AnchorShape targetAnchorShape = this.getTargetAnchorShape();           
        targetAnchorShape.paint (gr, false);
        gr.setTransform (previousTransform);
       
        //paint ControlPoints if enabled
        if (isPaintControlPoints()) {
            int last = listSize - 1;
            for (int index = 0; index <= last; index ++) {
                Point point = contrPoints.get (index);
                previousTransform = gr.getTransform ();
                gr.translate (point.x, point.y);
                if (index == 0  ||  index == last)
                    getEndPointShape().paint (gr);
                else
                    getControlPointShape().paint (gr);
                gr.setTransform (previousTransform);
            }
           
        }
        gr.setColor(previousColor);
    }
    
    
    
    private GeneralPath subdivide (Point b0, Point b1, Point b2, Point b3) {            
        double cutDistance = getTargetAnchorShape().getCutDistance();
        double minDistance = cutDistance - ENDPOINT_DEVIATION;
        /**
         * if the cutDistance is valid the last segment of the curve
         * gets reduced by subdivision until the distance of the endpoint(epDistance) 
         * satisfys the condition (cutDistance > epDistance > (cutDistance - ENDPOINT-DEVIATION)
         */
        if(cutDistance > minDistance && minDistance > 0 ) {
            GeneralPath path = new GeneralPath(); 
            
            path.moveTo(b0.x, b0.y);
            
            CubicCurve2D.Double left = new CubicCurve2D.Double(
                    b0.x, b0.y, 
                    b1.x, b1.y,
                    b2.x, b2.y, 
                    b3.x, b3.y);
            
            CubicCurve2D right=new CubicCurve2D.Double();
            left.subdivide(left, right);   
            double distance = b3.distance(left.getP2());
            //if the distance is bigger as the cutDistance the left segment is added
            //and the right segment is divided again
            while(distance>cutDistance){                    
                path.append(left, true);
                right.subdivide(left, right);
                distance = b3.distance(left.getP2());
                //if the devision removed to much the left segment is divided
                while(distance < minDistance) {                            
                    //changes the distance to ~ (distance+distance/2)
                    left.subdivide(left, right);
                    distance = b3.distance(left.getP2());
                }
            }                  
            //append the last segment with (minDistance < distance < cutDistance)
            //actually we should check if the a division happend, but this is very unlikly
            path.append(left, true);         
            return path;
        }
        return null;
    }
    
  
   
    
       
    private static Point scaleVector(Point vector, double len) {
        double scale = Math.sqrt(vector.x * vector.x + vector.y * vector.y);
        if(scale==0.0) return vector;
        scale = len / scale;          
        return new Point(
                Math.round(vector.x * (float)scale), 
                Math.round(vector.y * (float)scale));
    }
     
}    
