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

import at.ssw.visualizer.cfg.model.CfgNode;
import at.ssw.visualizer.cfg.preferences.CfgPreferences;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;


public class NodeWidget extends Widget  {    
    
    
    //basic block node dimension
    private final int halfheight=12;
    private final int halfwidth=17;
      
    private final int height=halfheight*2+1;  
    private final int width=halfwidth*2+1;     
    private final int arcHeight=(halfheight/4)*3;
    private final int arcWidth = arcHeight; 
    private final int FONT_MAXSIZE=18;
    
    private int borderWidth;
    private boolean selected=false; 
    private boolean nodeColorCustomized;
    private String text;
    private Rectangle2D fontRect;
    private Color nodeColor;
    
    protected static final Color HOVER_BACKGROUND = new Color(0xEEEEEE);
    protected static final Color HOVER_FOREGROUND = new Color(0xCDCDCD);

    public NodeWidget(CfgScene scene, CfgNode nodeModel ){        
        super(scene);   
        this.setToolTipText("<html>" + nodeModel.getDescription().replaceAll("\n", "<br>") + "</html>");
        this.selected=false;        
        this.text = nodeModel.getBasicBlock().getName();   
        this.borderWidth = nodeModel.getLoopDepth()+1;           
        this.setBorder(BorderFactory.createRoundedBorder(arcWidth+borderWidth, arcHeight+borderWidth, borderWidth, borderWidth, Color.BLACK, Color.BLACK));
        CfgPreferences prefs = CfgPreferences.getInstance();
        Color color = prefs.getFlagsSetting().getColor(nodeModel.getBasicBlock().getFlags());
        this.nodeColorCustomized = (color!=null);
        this.nodeColor = (nodeColorCustomized) ? color : prefs.getNodeColor();
        this.adjustFont(null);   
    }

    public void setBorderColor(Color color){ 
        this.setBorder(BorderFactory.createRoundedBorder(arcWidth+borderWidth, arcHeight+borderWidth, borderWidth, borderWidth, color, color));
    }
    
    public boolean isNodeColorCustomized() {    
        return nodeColorCustomized;        
    }
    
    //sets a customColor node color
    public void setNodeColor(Color color, boolean customColor) {
        this.nodeColorCustomized=customColor;
        this.nodeColor=color;
        this.revalidate();
    }
    
    public Color getNodeColor() {
        return this.nodeColor;
    }
    
    public CfgNode getNodeModel() {
        CfgScene scene = (CfgScene) this.getScene();
        return (CfgNode) scene.findObject(this);    
    }
      
   
    @Override
    public void notifyStateChanged(ObjectState oldState, ObjectState newState) {
        if(!oldState.equals(newState))
            this.revalidate();
        if(!oldState.isSelected() && newState.isSelected())
            this.bringToFront();
    }
       
    @Override
    protected Rectangle calculateClientArea() {       
        return new Rectangle(-(halfwidth+1), -(1+halfheight), width+1, height+1);//add border
    }

    public void adjustFont(Font font){
        if(font==null)
            font = CfgPreferences.getInstance().getTextFont();
        if(font.getSize()>FONT_MAXSIZE){
            font = new Font(font.getFamily(), font.getStyle(), FONT_MAXSIZE);
        }
        int size=font.getSize();
        int fontStyle = font.getStyle();
        String fontName = font.getFamily();
        FontRenderContext frc = new FontRenderContext(new AffineTransform(), false, false);
        Rectangle2D bounds = font.getStringBounds(text, frc);
        while(size > 1 && bounds.getWidth() > width) {
            font = new Font(fontName, fontStyle, --size);
            bounds = font.getStringBounds(text, frc);
        }       
        this.fontRect=bounds;
        this.setFont(font);
    }

    @Override
    protected void paintWidget() {     
        Graphics2D gr = getGraphics(); 
        gr.setColor(nodeColor); 
        Insets borderInsets  = this.getBorder().getInsets();    
        RoundRectangle2D.Float innerRect = new RoundRectangle2D.Float(-(halfwidth+1), -(halfheight+1), width+1, height+1,arcWidth-1, arcHeight-1);    
        gr.fill(innerRect);
        gr.setColor(getForeground()); 
        gr.setFont(getFont());
        float textX  = (float)( - fontRect.getCenterX());
        float textY  = (float)( - fontRect.getCenterY());                   
        gr.drawString(text, textX, textY); 
            
        RoundRectangle2D.Float outerRect = new RoundRectangle2D.Float(-(halfwidth+borderInsets.left + 1), -(halfheight+borderInsets.top + 1), 
            width+borderInsets.left + borderInsets.right + 1, height + borderInsets.top + borderInsets.bottom + 1, 
            arcWidth + borderWidth, arcHeight + borderWidth); 

        ObjectState os =this.getState();
        if(os.isSelected()){
            Composite composite = gr.getComposite();
            gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f));                     
            gr.setColor(CfgPreferences.getInstance().getSelectionColorForeground());
            gr.fill(outerRect);
            gr.setColor(CfgPreferences.getInstance().getSelectionColorBackground());            
            gr.setComposite(composite);       
        }  
        if(os.isHovered()){
            Composite composite = gr.getComposite();
            gr.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.5f));
            gr.setColor(HOVER_FOREGROUND);
            gr.fill(outerRect); 
            gr.setColor(HOVER_BACKGROUND);
            gr.setComposite(composite);        
        }       
    }  
    
    @Override
    public String toString() {
        return "NodeWidget[" + getNodeModel().getBasicBlock().getName() + "]";
    }
}
