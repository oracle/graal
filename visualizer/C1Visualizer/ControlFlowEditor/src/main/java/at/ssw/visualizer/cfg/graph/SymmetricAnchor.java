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
package at.ssw.visualizer.cfg.graph;

import org.netbeans.api.visual.widget.Widget;
import org.netbeans.api.visual.anchor.Anchor;
import java.awt.*;


/**
 * This Anchor can be used with symmetric edges to create parallel Edges.
 * Two Directed Edges are symmetric if they are connecting the same Nodes in different directions.
 * e.g.  Nodes (1, 2) Edges(a , b)  with (1)-a->(2)  ,  (2)-b->(1)  
 * Start-/End positions are calculated with a fixed offset to prevent edges from overlapping. 
 * If the edges are drawn as straight lines they will appear as parallel edges.
 */

public final class SymmetricAnchor extends Anchor {
    
   private final static int OFFSET = 10;
   private boolean includeBorders;  
   private int offx;
   private int offy;
   
   public SymmetricAnchor (Widget widget, boolean includeBorders, boolean source) {
        super (widget);
        this.includeBorders = includeBorders;        
        if (source) {
            offx = OFFSET;
            offy = OFFSET;        
        }
        else {
            offx = -OFFSET;
            offy = -OFFSET;
        }
    }

    public Result compute (Entry entry) {        
        Point relatedLocation = //center of the widget 
                getRelatedSceneLocation ();
        Point oppositeLocation = //center of the widget
                getOppositeSceneLocation (entry);

        Widget widget = getRelatedWidget ();
        Rectangle bounds = widget.getBounds ();
        if (! includeBorders) {
            Insets insets = widget.getBorder ().getInsets ();
            bounds.x += insets.left;
            bounds.y += insets.top;
            bounds.width -= insets.left + insets.right;
            bounds.height -= insets.top + insets.bottom;
        }
      
        bounds = widget.convertLocalToScene (bounds);
       
        if (bounds.isEmpty ()  || relatedLocation.equals (oppositeLocation))
            return new Anchor.Result (relatedLocation, Anchor.DIRECTION_ANY);

        float dx  //distance  x-axis
                = oppositeLocation.x - relatedLocation.x;
        float dy  //distance y-axis  
                = oppositeLocation.y - relatedLocation.y;
       
        
        float ddx 
                = Math.abs (dx) / (float) bounds.width;
        float ddy = 
                Math.abs (dy) / (float) bounds.height;

        Anchor.Direction direction;
      

        if (ddx >= ddy) {
            if(dx >= 0.0f){        
                direction = Direction.RIGHT;
                relatedLocation.y -= offy;
            } else {
                direction = Direction.LEFT;
                relatedLocation.y += offy;
            }         
        } else {
            if(dy >= 0.0f){
                direction = Direction.BOTTOM;
                relatedLocation.x += offx;
            } else {
                direction = Direction.TOP;
                relatedLocation.x -= offx;            
            }           
        }
        

        float scale = 0.5f / Math.max (ddx, ddy);

        float ex = scale * dx;
        float ey = scale * dy;
        
        Point point = new Point (Math.round (relatedLocation.x + ex), Math.round (relatedLocation.y + ey));
            
         if(direction == Direction.RIGHT) {
             int top = bounds.y;//-bounds.height;// left y of the widget
             int bottom = bounds.y + bounds.height;// right y of the widget
             if(point.y < top) {//above the widget
                 int cor = top-point.y;
                 point.x -= cor;
                 point.y += cor;              
             } else if ( point.y > bottom) {
                 int cor = point.y-bottom;
                 point.x -= cor;
                 point.y -= cor;
             }
             
         } else if (direction == Direction.LEFT) {
             int top = bounds.y;//-bounds.height;// left y of the widget
             int bottom = bounds.y + bounds.height;// right y of the widget
                         
           
                        
             if(point.y < top) {//above the widget
                 int cor = top-point.y;
                 point.x += cor;
                 point.y += cor;
             } else if ( point.y > bottom) {              
                int cor = bottom-point.y;
                point.x -= cor;
                point.y += cor;
             }
            
                      
         } else if (direction == Direction.BOTTOM) {
             int left = bounds.x;//-bounds.height;// left y of the widget
             int right = bounds.x + bounds.width;// right y of the widget
             if(point.x < left) {//above the widget
                int cor = left-point.x;                
                point.x += cor;
                point.y -= cor;
             } else if ( point.x > right) {
                int cor = point.x- right;
                point.x -= cor;
                point.y -= cor;
             }
         
         } else if (direction == Direction.TOP) {
            int left = bounds.x;//-bounds.height;// left y of the widget
            int right = bounds.x + bounds.width;// right y of the widget
            if(point.x < left) {//above the widget
                int cor = left-point.x;
                point.x += cor;
                point.y += cor;
             } else if ( point.x > right) {
                 int cor = point.x - right;
                 point.x -= cor;
                 point.y += cor;
             }
         
         }          
        
        return new Anchor.Result (point, direction);
    }
}
