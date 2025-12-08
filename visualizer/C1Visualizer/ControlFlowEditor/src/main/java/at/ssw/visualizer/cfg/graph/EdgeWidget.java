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

import at.ssw.visualizer.cfg.model.CfgEdge;
import at.ssw.visualizer.cfg.preferences.CfgPreferences;
import at.ssw.visualizer.cfg.visual.BezierWidget;
import at.ssw.visualizer.cfg.visual.SplineConnectionWidget;
import org.netbeans.api.visual.anchor.AnchorShapeFactory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.model.ObjectState;

//public class EdgeWidget extends BezierWidget {
public class EdgeWidget extends SplineConnectionWidget {
    
    private boolean visible=true;//to store the visible state when entering the preview 
    protected static final Stroke selectedStroke = new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    protected static final Stroke defaultStroke = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    protected static final Stroke previewStroke = new BasicStroke(
            0.5f,                      // Width
            BasicStroke.CAP_SQUARE,    // End cap
            BasicStroke.JOIN_MITER,    // Join style
            10.0f,                     // Miter limit
            new float[] {5.0f, 5.0f},  // Dash pattern
            0.0f);              
    
    
    
    public EdgeWidget(CfgScene scene, CfgEdge edge) {
        super(scene);       
        Color lineColor;
        CfgPreferences prefs = CfgPreferences.getInstance();
        
        if(edge.isBackEdge()) 
            lineColor = prefs.getBackedgeColor();       
        else if (edge.isXhandler())
            lineColor = prefs.getExceptionEdgeColor();
        else    
            lineColor = prefs.getEdgeColor();
        
        setLineColor(lineColor);
        AnchorShape as;
        if(edge.isReflexive())//small Arrow
            as = AnchorShapeFactory.createTriangleAnchorShape(6, true, false, 5);
        else
            as =AnchorShapeFactory.createTriangleAnchorShape(10, true, false, 9);
    
        setTargetAnchorShape(as);  
        setToolTipText(edge.toString());              
    }
 
    public CfgEdge getEdgeModel() {
        CfgScene scene = (CfgScene) this.getScene();
        return (CfgEdge) scene.findObject(this);
    }
    
    public void setEdgeVisible(boolean visible) {
        this.visible=visible;
        this.setVisible(visible);
        this.reroute();
        this.revalidate();
    }
    
    
    public boolean isEdgeVisible(){
        return visible;
    }
    
        
    @Override
    public void notifyStateChanged(ObjectState oldState, ObjectState newState) {
       setForeground (getLineColor());
      
       if(newState.isHighlighted() && !oldState.isHighlighted()){
            this.setStroke(previewStroke);
            this.setVisible(true);
       } else {
           if(newState.isSelected()){
                this.setStroke(selectedStroke);
           } else {
                this.setStroke(defaultStroke);
           }
           if(this.isEdgeVisible()){
                this.setVisible(true);
           } else {
                this.setVisible(false);
           }
       }       
    }
    
    
    @Override
    public String toString(){
        return "EdgeWidget[" + getEdgeModel().toString() + "]";
    }
}
