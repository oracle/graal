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

import java.awt.BasicStroke;
import java.awt.Color;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Stefan Loidl
 */
public class InstructionConnectionWidget extends ConnectionWidget{

    private static final BasicStroke EXPANDEDSTROKE=new BasicStroke(2.0f);
    private static final BasicStroke UNEXPANDEDSTROKE=new BasicStroke(1.0f);
    private static final BasicStroke SELECTEDSTROKE=new BasicStroke(3.0f);


    private static final Color EXPANDEDCOLOR=new Color(200,0,0);
    private static final Color UNEXPANDEDCOLOR=Color.BLACK;
    private static final Color INTERCLUSTERCOLOR=Color.GRAY;

    //If one if the endpoints is selected
    private static final Color SELECTEDCOLOR=Color.BLACK;
    private static final Color SELECTEDBACKWARDCOLOR=Color.BLUE;
    private static final Color INTERSELECTIONCOLOR=new Color(0,100,0);





    /** Creates a new instance of InstructionConnectionWidget */
    public InstructionConnectionWidget(Scene s) {
        super(s);
    }


    protected void paintWidget(){

        boolean expanded=false;
        String cluster=null;
        //is the link crossing cluster borders?
        boolean interClusterLink=false;
        ClusterWidget cw1=null,cw2=null;
        boolean sourceSelected=false;
        boolean targetSelected=false;


        Scene scene=this.getScene();
        if(scene instanceof InstructionNodeGraphScene)
            interClusterLink=((InstructionNodeGraphScene)scene).isInterClusterLinkGrayed();



        Widget w=getSourceAnchor().getRelatedWidget();
        if(w instanceof InstructionNodeWidget){
            if(!((InstructionNodeWidget)w).isWidgetVisible()) return;
            expanded=((InstructionNodeWidget)w).isPathHighlighted();
            cw1=((InstructionNodeWidget)w).getClusterWidget();
        }
        sourceSelected=w.getState().isSelected();


        w=getTargetAnchor().getRelatedWidget();
        if(w instanceof InstructionNodeWidget){
            if(!((InstructionNodeWidget)w).isWidgetVisible()) return;
            expanded = expanded && ((InstructionNodeWidget)w).isPathHighlighted();
            cw2=((InstructionNodeWidget)w).getClusterWidget();
            interClusterLink= interClusterLink && (cw1!=cw2);
        }
        targetSelected=w.getState().isSelected();

        if(expanded) {
            this.setStroke(EXPANDEDSTROKE);
            this.setForeground(EXPANDEDCOLOR);
        }
        else if(sourceSelected && targetSelected){
            this.setStroke(SELECTEDSTROKE);
            this.setForeground(INTERSELECTIONCOLOR);
        }
        else if(sourceSelected){
            this.setStroke(SELECTEDSTROKE);
            this.setForeground(SELECTEDCOLOR);
        }
        else if(targetSelected){
            this.setStroke(SELECTEDSTROKE);
            this.setForeground(SELECTEDBACKWARDCOLOR);
        }
        else {
            this.setStroke(UNEXPANDEDSTROKE);
            if(interClusterLink) this.setForeground(INTERCLUSTERCOLOR);
            else this.setForeground(UNEXPANDEDCOLOR);
        }

        super.paintWidget();
    }

}
