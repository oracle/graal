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
package at.ssw.visualizer.cfg.graph.layout;

import at.ssw.visualizer.cfg.graph.CfgScene;
import at.ssw.visualizer.cfg.model.CfgEdge;
import at.ssw.visualizer.cfg.model.CfgNode;
import java.awt.Point;
import java.util.Collection;
import org.netbeans.api.visual.graph.layout.GraphLayout;
import org.netbeans.api.visual.graph.layout.UniversalGraph;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.EdgeList;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.NodeList;
import org.netbeans.api.visual.widget.Widget;


public class HierarchicalNodeLayout extends GraphLayout<CfgNode, CfgEdge> {
           
    private static final int TOP_BORDER = 20;
    private static final int LEFT_BORDER = 40;    
    
    private CfgScene scene;  
    
    public HierarchicalNodeLayout(CfgScene scene){
        this.scene = scene;      
    }
    
    @Override
    protected void performGraphLayout(UniversalGraph<CfgNode, CfgEdge> ug) {  
        DirectedGraph dg = new DirectedGraph();
        DirectedGraphLayout layout = new DirectedGraphLayout();
       
        NodeList nodeList = dg.nodes;
        EdgeList edgeList = dg.edges;
        
        for(CfgNode n : scene.getCfgEnv().getNodes() ) {
            Widget nodeWidget = scene.findWidget(n);
            Node node = new Node(n);           
            node.width=nodeWidget.getBounds().width;
            node.height = nodeWidget.getBounds().height;         
            nodeList.add(node);
        }
        
        for(CfgEdge e : scene.getCfgEnv().getEdges() ) {
            if(e.isBackEdge()) continue;
            Edge edge = new Edge(e, nodeList.getNode(e.getSourceNode().getNodeIndex()), 
                    nodeList.getNode(e.getTargetNode().getNodeIndex()));            
            edgeList.add(edge);
        }        
      
        layout.visit(dg);
        
        for(Object obj : dg.nodes){
            Node n = (Node) obj;
            CfgNode cfgNode  = (CfgNode) n.data;
            Point pos = new Point(n.x + LEFT_BORDER , n.y + TOP_BORDER);
            Point scenepos = scene.convertLocalToScene(pos);
            setResolvedNodeLocation(ug, cfgNode, scenepos);              
        }
        
    }

    @Override
    protected void performNodesLayout(UniversalGraph<CfgNode, CfgEdge> ug, Collection<CfgNode> collection) {        
    }

}
