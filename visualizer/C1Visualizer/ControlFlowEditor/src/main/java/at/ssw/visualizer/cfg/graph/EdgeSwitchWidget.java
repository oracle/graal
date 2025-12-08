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
import at.ssw.visualizer.cfg.model.CfgNode;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Collection;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.action.TwoStateHoverProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;


public class EdgeSwitchWidget extends Widget {
    private final static Color color_enabled = Color.gray;
    private final static Color color_hover = Color.lightGray; 
    private float width=1;
    private float height=1;
    private CfgScene scene;
    private NodeWidget nodeWidget;    
    private boolean output;
    private WidgetAction hoverAction;
    private static final String TT_HIDE_EDGES = "Hide Edges";
    private static final String TT_SHOW_EDGES = "Show Edges";   
    private static SelectProvider selectProvider = createSelectProvider();
     
    
    public EdgeSwitchWidget(final CfgScene scene, NodeWidget nodeWidget, boolean output) {        
        super(scene);
        this.scene = scene;   
        this.output = output;
        this.nodeWidget = nodeWidget;  
  
        this.getActions().addAction(ActionFactory.createSelectAction(selectProvider));
        TwoStateHoverProvider ts = new TsHover(this);   
        WidgetAction wa = ActionFactory.createHoverAction(ts);
        this.hoverAction = wa;
        this.getActions().addAction(wa);
        scene.getActions().addAction(wa);
        this.setToolTipText(TT_HIDE_EDGES);      
        this.setForeground(color_enabled);
        this.setState(ObjectState.createNormal());
    }
    
    
    @Override
    protected Rectangle calculateClientArea() {
       if (this.nodeWidget.getBounds() == null) return new Rectangle(0, 0, 1, 1);
       int hw = (int) (this.width / 2);
       int hh = (int) (this.height /2);
       
       return new Rectangle(-hw, -hh, 2*hw, 2*hh); 
   }    
     
   
    public void updatePosition() { 
        if (this.nodeWidget.getBounds() != null) {   
            this.width = nodeWidget.getBounds().width*9;
            this.width /=10;
            this.height = nodeWidget.getBounds().height/4;      
            int offset=(int)(2 * (height / 3));
                        
            Rectangle bounds = nodeWidget.getBounds();
            Point location = nodeWidget.getLocation();
          
            Point newLoc = new Point();           
            newLoc.x = location.x;
             
            if(output) {               
                newLoc.y = +location.y + bounds.height/2+offset;      
            }else {            
                newLoc.y = location.y - bounds.height/2-offset;
            }          
            this.setPreferredLocation(newLoc);           
        }           
    }
 
    private Collection<CfgEdge> getEdges(){
        Collection<CfgEdge> edges;
        CfgNode node = nodeWidget.getNodeModel();
        if (output) {
            edges = scene.findNodeEdges(node, true, false);
        } else {
            edges = scene.findNodeEdges(node, false, true);
        }
        return edges;
    }
 
    //change visibility for all Edges
    public void changeEdgeVisibility(boolean visible){
        Collection<CfgEdge> edges = this.getEdges();  
            
        for(CfgEdge e: edges) {
            EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
            if(visible != ew.isEdgeVisible()){              
                ew.setEdgeVisible(visible);     
                if(output){
                    scene.getInputSwitch(e.getTargetNode()).updateStatus();
                } else {
                    scene.getOutputSwitch(e.getSourceNode()).updateStatus();
                }              
            }
        }
        if(visible)    
            this.setToolTipText(TT_HIDE_EDGES); 
        else 
            this.setToolTipText(TT_SHOW_EDGES);
   
        this.setForeground(color_enabled);
        this.bringToBack();
        ObjectState os = this.getState();   
        this.setState(os.deriveSelected(!visible));
    }
    
    /**
     *  Update the status of the switch to the current state of the edges
     *  usually needed when the opposit switch changes the state
     */
    private void updateStatus(){
        Collection<CfgEdge> edges = this.getEdges(); 
        boolean hiddenFound=false;
        for(CfgEdge e: edges) {
            EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
            if(!ew.isVisible()) {
                hiddenFound=true;
                break;
            }
        }        
        ObjectState os = this.getState();
        if(os.isSelected() && !hiddenFound) {
            this.setState(os.deriveSelected(false));
            setToolTipText(TT_HIDE_EDGES);
        } else if (!os.isSelected() && hiddenFound) {
            this.setState(os.deriveSelected(true));
            setToolTipText(TT_SHOW_EDGES); 
        }
        this.revalidate();
    }
    

    public void startPreview() {        
        ObjectState os = this.getState();
        
        for(CfgEdge e : getEdges()) {
            EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
            if(!os.isSelected() || !ew.isVisible()){             
                ObjectState edgeState = ew.getState();              
                ew.setState(edgeState.deriveHighlighted(true));
            }
        }               
    }
    
    public void endPreview(){       
       for(CfgEdge e : getEdges()) {
            EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
            ObjectState os = ew.getState();          
            ew.setState(os.deriveHighlighted(false));
        }      
    }
  
    /**
     * shows or hides the edges of the switch
     */
    public void switchEdges() { 
        endPreview();
        ObjectState os = this.getState();
        Collection<CfgEdge> edges = this.getEdges(); 
        ArrayList<CfgEdge> updates = new ArrayList<CfgEdge>();
        boolean visible=os.isSelected();
        this.setState(os.deriveSelected(!visible));     
        for(CfgEdge e: edges) {
            EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
                if(ew.isEdgeVisible() != visible){
                    updates.add(e);
                    ew.setEdgeVisible(visible);
                    if(output){
                        scene.getInputSwitch(e.getTargetNode()).updateStatus();
                    } else {
                        scene.getOutputSwitch(e.getSourceNode()).updateStatus();
                    }      
                }
        }
        if(visible)
             this.setToolTipText(TT_HIDE_EDGES);    
        else
             this.setToolTipText(TT_SHOW_EDGES);    
               
        scene.fireSelectionChanged();//updates Edge visibility for context action        
        revalidate();   
    }
  
  

 
    private class TsHover implements TwoStateHoverProvider {       
        EdgeSwitchWidget tw;    
       
        TsHover(EdgeSwitchWidget tw) {
            this.tw = tw;
        }
       
        public void unsetHovering(Widget w) {
            w.setForeground(color_enabled);  
            ObjectState state = w.getState();          
            w.setState(state.deriveWidgetHovered(false));           
            w.bringToBack();
            endPreview();
        }

        public void setHovering(Widget w) {        
            ObjectState state = w.getState();          
            w.setState(state.deriveWidgetHovered(true));          
            w.setForeground(color_hover);
            w.bringToFront();
            nodeWidget.bringToFront();                
            startPreview();
        }
    }
    
    @Override
    public void paintWidget() {   
        ObjectState os = this.getState();       
        if(!os.isHovered() && !os.isSelected()) return; //all previewEdges visible and not hovering, 
                                                        //no need to paint the switch         
        float hw = width/2;    
        Polygon pol = new Polygon();
        pol.addPoint(0,(int) -height/2); 
        pol.addPoint((int)hw,(int) height/2);
        pol.addPoint((int)-hw,(int) height/2);  
        Graphics2D gr = getGraphics();
        gr.setColor(this.getForeground());
        BasicStroke bs = new BasicStroke(2.0f, BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND);
        gr.setStroke(bs);
        AffineTransform previousTransform;
        previousTransform = gr.getTransform ();
        if(output) {
            if(os.isSelected() ){//hidden
                 gr.scale(1.0, -1.0);
            }
        } else { //input switch
            if(os.isHovered() && !os.isSelected()){
                 gr.scale(1.0, -1.0);
            }     
        }            
        gr.fillPolygon(pol);
        gr.setTransform(previousTransform);
        
    }
    
   
    
    //the constructor adds the hover WidgetAction to the scene
    //the action is removed from the scene when the object gets destroyed
    @Override
    protected void finalize() throws Throwable { 
        this.getScene().getActions().removeAction(hoverAction);
        this.getActions().removeAction(hoverAction);
    }
    
    @Override
    public String toString(){
        return "EDGESWITCH("+this.nodeWidget.getNodeModel().toString()+")";
    }
    
    private static SelectProvider createSelectProvider() {
        return new SelectProvider(){
            public boolean isAimingAllowed(Widget arg0, Point arg1, boolean arg2) {
                return false;
            }
            
            public boolean isSelectionAllowed(Widget arg0, Point arg1, boolean arg2) {
                return true;
            }
            
            public void select(Widget w, Point arg1, boolean arg2) {              
                if(w instanceof EdgeSwitchWidget){
                    EdgeSwitchWidget tw = (EdgeSwitchWidget) w;                    
                    tw.switchEdges();
                }                                    
            }
        };
    }
}
