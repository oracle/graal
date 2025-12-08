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
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.graph.GraphScene;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;


/**
 *  In comparison to the default ConnectionWidget this class is able to connect 
 *  widgets with a curve instead of a straight line sequence. 
 *  In conjunction with a suitable router the connection will be a straight line 
 *  or a curve depending on the amount and the position of the controlpoints. 
 *  Controlpoints supplied by the router, are treated as curve intersection points.
 *  For Reflexive edges the router doesn`t necessarily need to supply 
 *  any controlpoints, they get painted by this automatically, this can be 
 *  excepted if the router supplys more as 2 control points for a self edge,
 *  then the edge gets painted with the default curve interpolation algorithm.
 *  The method used for drawing curves uses a piecewise cubic interpolation 
 *  algorithm. Between two control points a curve is painted as cubic bezier 
 *  curve the, inner bezier points are calculated automatically with FMILL 
 *  tangents and a chord parametrization, this interpolant is also known as
 *  cutmull-rom spline. The resulting spline fullfills c^1 continuity.
 *  The the end points the interpolation algorithm uses the bessel end condition.
 */

public class SplineConnectionWidget extends ConnectionWidget {     
   private static final double ENDPOINT_DEVIATION = 3;//curve endpoint approximation accuracy
   private static final double HIT_DISTANCE_SQUARE = 4.0;//distance for intersection test
    private GraphScene scene=null;
    private Point2D [] bezierPoints = null;
 
    public SplineConnectionWidget(Scene scene) {
        super(scene);   
        if(scene instanceof GraphScene)
            this.scene=(GraphScene) scene;
    }
    
    //check for self - edge
    private boolean isReflexive(){
        return getSourceAnchor().getRelatedWidget() == getTargetAnchor().getRelatedWidget();
    }
    
    
    @Override
    protected Rectangle calculateClientArea() {              
        
        Rectangle bounds = null;

        if(this.getControlPoints().size()>2){
            bezierPoints = createBezierPoints(getControlPoints());
        }
        //minmax method - returns the smallest bounding rectangle  
        //Curves and surfaces for CAGD (3rd ed.), p.54  
        //exact calculation of the bounding min rect
        if(bezierPoints != null) {
            Rectangle2D bounds2D = null;
            for (int i = 0; i < bezierPoints.length; i++) {
                Point2D point = bezierPoints[i];
                if(bounds2D==null)
                    bounds2D = new Rectangle2D.Double(point.getX(),point.getY(),0,0);
                else
                    bounds2D.add(point);
            }
            bounds = bounds2D.getBounds();
            bounds.grow(2, 2);
            
        } else if (getControlPoints().size()>0){
            for(Point p : this.getControlPoints()){
              if(bounds==null)
                  bounds = new Rectangle(p);
              else
                  bounds.add(p);
            }
            bounds.grow(5,5);     
            
        } else if (isReflexive()) {
            Widget related = this.getTargetAnchor().getRelatedWidget();
            bounds = related.convertLocalToScene(related.getBounds());
            bounds.grow(10, 10);
        }
        
        if(bounds==null)
            bounds = super.calculateClientArea();

        return bounds;
    }
 
    
    /**
     * if the edge is reflexive its painted as a cyclic self edge, if there 
     * are two controlpoints the connection is painted as a straight line from 
     * the source to the targetanchor, if there are more as 2 controlpoints the 
     * connection path between two control points is painted as cutmull rom 
     * spline with bessel end tangents.
     */
    @Override
    protected void paintWidget () {       
        List<Point> contrPoints = this.getControlPoints();
        int listSize = contrPoints.size();
  
        Graphics2D gr = getGraphics ();
        
        if (listSize <= 2) {
            this.bezierPoints=null;//set bezier Points null for calulateClientArea()
            if(isReflexive()) { //special case for reflexive connection widgets    
                this.drawReflexive(gr);
            } else {
                super.paintWidget();
            }         
            return;
        }
      
        //bezier curve... listSize > 2
        GeneralPath curvePath = new GeneralPath();      
        double lastControlPointRotation = 0.0;
        
        
        Point2D [] bezPoints  = this.createBezierPoints(contrPoints);
        curvePath.moveTo(bezPoints[0].getX(), bezPoints[0].getY());//b00
        
        //last segment is added by subdivision thats why its -5
        for (int i = 1; i < bezPoints.length-5; i+=3) {          
            curvePath.curveTo(
                    bezPoints[i].getX(), bezPoints[i].getY(),//b1i
                    bezPoints[i+1].getX(), bezPoints[i+1].getY(),//b2i
                    bezPoints[i+2].getX(), bezPoints[i+2].getY());//b3i   
           
        }        
   
        GeneralPath lastseg = subdivide2D(
                bezPoints[bezPoints.length-4], 
                bezPoints[bezPoints.length-3],
                bezPoints[bezPoints.length-2],
                bezPoints[bezPoints.length-1]);
        

        if(lastseg != null)
            curvePath.append(lastseg, true);
                    
        Point2D cur = curvePath.getCurrentPoint();
        Point lastControlPoint = contrPoints.get(listSize-1);
        
        lastControlPointRotation = //anchor anchorAngle 
            Math.atan2 (cur.getY() - lastControlPoint.y, cur.getX() - lastControlPoint.x);
  
        gr.setStroke(getStroke());
        gr.setColor(getLineColor());
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
    }
    
    private void drawReflexive(Graphics2D gr){
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
    
    
     private Point2D[] createBezierPoints(List<Point> list){
        if(list.size()<3) return null ;
        
        
        int lastIdx = list.size()-1;
        

        //chord length parametrization
        double[] uis = new double[list.size()];
        uis[0]=0;
        uis[1] = list.get(1).distance(list.get(0));
        for (int i = 1; i < uis.length; i++) {
            Point cur = list.get(i);
            Point prev = list.get(i-1);     
            uis[i]=uis[i-1]+ cur.distance(prev);          
        }
          
            
        for (int i = 1; i < uis.length; i++) {
            uis[i] /= uis[lastIdx];
            
        }
        double[] delta = new double[uis.length-1];     
        for (int i = 0; i < delta.length; i++) {
            double ui = uis[i];
            double uin = uis[i+1];
            delta[i] = uin-ui;
        }
       
        
        //FMILL tangent directions (chord length) 
        Point2D[] tangents  = new Point2D[list.size()];
 
        for (int i = 1; i < list.size()-1; i++) {
            Point xBefore = list.get(i-1);
            Point xAfter = list.get(i+1);
            Point2D.Double tangent = new Point2D.Double (xAfter.x - xBefore.x, xAfter.y - xBefore.y);          
            tangents[i] = tangent;
        }
        
        
        Point2D [] bezPoints = new Point2D[(list.size()-1)*2+list.size()];
        //Catmull-Rom
        for (int i = 1; i < list.size()-1; i++) {                
            Point b3i = list.get(i);
            Point2D b3ib = b3iBefore(b3i, delta[i-1], delta[i], tangents[i]);
            Point2D b3ia = b3iAfter(b3i, delta[i-1], delta[i], tangents[i]);
            bezPoints[3*i] = b3i;
            bezPoints[3*i-1] = b3ib;
            bezPoints[3*i+1] = b3ia;
        }             
        bezPoints[0] = list.get(0);
        bezPoints[bezPoints.length-1] = list.get(list.size()-1);

        Point p0 = list.get(0);
        Point p1 = list.get(1);
        Point p2 = list.get(2);
        Point pL_2 = list.get(lastIdx-2);
        Point pL_1 = list.get(lastIdx-1);
        Point pL = list.get(lastIdx);
       
        Point2D m1 = besselTangent(delta[0], delta[1], p0, p1, p2);
        Point2D m0 = besselEndTangent(p0, p1, delta[0], m1);
        
        Point2D mLb = besselTangent(delta[delta.length-2], delta[delta.length-1],
                pL_2,pL_1, pL);
        Point2D mL = besselEndTangent(pL_1, pL, delta[delta.length-1], mLb);
        
        Point2D scaleM0 = scale(normalize(m0), p0.distance(p1));//increase distx/distxl to make curve rounder at the end
        Point2D scaleML = scale(normalize(mL), pL.distance(pL_1));
         //Catmull-Rom for bessel points 
        Point2D b30a = b3iAfter(p0, delta[0], delta[0],scaleM0);
        Point2D b33b = b3iBefore(pL, delta[delta.length-1], delta[delta.length-1],scaleML);
         
        bezPoints[1] = b30a;
        bezPoints[bezPoints.length-2] = b33b;

        return bezPoints;
    }
    
     

    private static Point2D besselTangent(double delta_ib, double delta_i, Point2D p0, Point2D p1 , Point2D p2){
        double alpha_i = delta_ib/(delta_ib+delta_i);
        
        double x = (1-alpha_i)/delta_ib * (p1.getX() - p0.getX())
                + alpha_i/delta_i * (p2.getX()-p1.getX());
        double y = (1-alpha_i)/delta_ib * (p1.getY() - p0.getY())
                + alpha_i/delta_i * (p2.getY()-p1.getY());
       
        return new Point2D.Double(x,y);
    }
    
    private static Point2D besselEndTangent(Point2D p0, Point2D p1, double delta_u, Point2D m){
        double x = 2*((p1.getX()-p0.getX())/delta_u) - m.getX();
        double y = 2*((p1.getY()-p0.getY())/delta_u) - m.getY();
        return new Point2D.Double(x,y);
    }
    
    private static Point2D b3iBefore(Point2D b3i, double delta_ib, double delta_i, Point2D li){    
        double x = b3i.getX() - (delta_ib/(3*(delta_ib+delta_i)))*li.getX();
        double y = b3i.getY() - (delta_ib/(3*(delta_ib+delta_i)))*li.getY();   
        return new Point.Double(x,y);      
    }
    
    private static Point2D b3iAfter(Point2D b3i, double delta_ib,double delta_i,Point2D li){
        double x = b3i.getX() + (delta_i/(3*(delta_ib+delta_i)))*li.getX();
        double y = b3i.getY() + (delta_i/(3*(delta_ib+delta_i)))*li.getY();
        return new Point.Double(x,y);      
    }
    
    
    
    
    //returns length of vector v
    private static double norm(Point2D v){
        return Math.sqrt(v.getX()*v.getX()+v.getY()*v.getY());
    }
     
    //returns unity vector of vector v
    private static Point2D normalize(Point2D v){
        double norm = norm(v);
        if(norm==0) return new Point2D.Double(v.getX(), v.getY());
        return new Point2D.Double(v.getX()/norm , v.getY()/norm);
    }
    
    //scale vector to size of length
    private static Point2D scale(Point2D v, double length){
        Point2D tmp = normalize(v);
        return new Point2D.Double(tmp.getX()*length, tmp.getY()*length);
    }
    

    
    private GeneralPath subdivide2D (Point2D b0, Point2D b1, Point2D b2, Point2D b3) {
        //set 2nd intermediate point to endpoint
        //we could actually use another "better" point if we like to have a smoother curve
      
        double cutDistance = getTargetAnchorShape().getCutDistance();
        double minDistance = cutDistance - ENDPOINT_DEVIATION;
        /**
         * if the cutDistance is valid the last segment of the curve
         * gets reduced by subdivision until the distance of the endpoint(epDistance) 
         * satisfys the condition (cutDistance > epDistance > (cutDistance - ENDPOINT-DEVIATION)
         */
        if(cutDistance > minDistance && minDistance > 0 ) {
            GeneralPath path = new GeneralPath(); 
            
            path.moveTo(b0.getX(), b0.getY());
            
            CubicCurve2D.Double curve = new CubicCurve2D.Double(
                    b0.getX(), b0.getY(), 
                    b1.getX(), b1.getY(),
                    b2.getX(), b2.getY(), 
                    b3.getX(), b3.getY());
            
            
           
            CubicCurve2D right=new CubicCurve2D.Double();
            CubicCurve2D left=new CubicCurve2D.Double();
            curve.subdivide(left, right);   
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
            path.append(left, true);         
            return path;
        }
        return null;
    }
    
 
    /**
     * Returns whether a specified local point pL is a part of the connection 
     * widget. 
     * First it make a rough bounds check
     * for Line Segments => use Super call (ConnectionWidget.isHitAt(pL)). 
     * for self-edges => its sufficent to return getBounds.contains(pL).
     * for Splines => Interate over all Partitial segments of the curve and make 
     * a minmax check with the bezier points. If pL is inside the minmax 
     * rectangle of one segment the curve is constructed and subdivided until 
     * the distance d between center point pC (of the bounding rectangle) 
     * and pL is below HIT_DISTANCE_SQUARE, in this case it returns true.
     * If no no minmax check was successful or the subdivision lead to an 
     * rectangle witch doesn`t contain pL return false. 
     * @param localLocation the local location
     * @return true, if the location is a part of the connection widget
     */
    @Override
    public boolean isHitAt(Point localLocation) {     
        if(!isVisible()  || !getBounds ().contains (localLocation))
            return false;
          
        List<Point> controlPoints = getControlPoints ();
        if(controlPoints.size() <=2){
            if(isReflexive()) return true;
            return super.isHitAt(localLocation);
        }    
        
        if(bezierPoints != null) {         
            for (int i = 0; i < bezierPoints.length-1; i+=3) {          
                 Point2D b0 =   bezierPoints[i];
                 Point2D b1 =   bezierPoints[i+1];
                 Point2D b2 =   bezierPoints[i+2];
                 Point2D b3 =   bezierPoints[i+3];
                 
                 CubicCurve2D left = new CubicCurve2D.Double(
                    b0.getX(), b0.getY(), 
                    b1.getX(), b1.getY(),
                    b2.getX(), b2.getY(), 
                    b3.getX(), b3.getY());
                 
                 
                 Rectangle2D bounds = left.getBounds2D();
                 while(bounds.contains(localLocation)) {                                                     
                    //calculate the center and use HIT_DISTANCE_SQUARE for a range check  
                    Point2D test = new Point2D.Double(bounds.getCenterX(),bounds.getCenterY());
                    if(test.distance(localLocation) < HIT_DISTANCE_SQUARE){                        
                        return true;
                    }

                   
                    CubicCurve2D  right = new CubicCurve2D.Double();                    
                    left.subdivide(left, right);
                    Rectangle2D lb2d = left.getBounds2D();
                    Rectangle2D rb2d = right.getBounds2D();                    
                    if( lb2d.contains(localLocation)){      
                        bounds = lb2d;
                    } else if (rb2d.contains(localLocation)) {                        
                        left = right;
                        bounds = rb2d;
                    } else {                       
                        return false;
                    }
                    
                 }//end while               
            }//end for              
        }       
        return false;      
    }   
  
 
}



    
