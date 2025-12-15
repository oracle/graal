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
import at.ssw.visualizer.cfg.model.LoopInfo;
import java.awt.Point;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.draw2d.geometry.Insets;
import org.netbeans.api.visual.graph.layout.GraphLayout;
import org.netbeans.api.visual.graph.layout.UniversalGraph;
import org.eclipse.draw2d.graph.CompoundDirectedGraph;
import org.eclipse.draw2d.graph.CompoundDirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.EdgeList;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.NodeList;
import org.eclipse.draw2d.graph.Subgraph;
import org.netbeans.api.visual.widget.Widget;

public class HierarchicalCompoundLayout extends GraphLayout<CfgNode, CfgEdge> {
           
    private static final int TOP_BORDER = 20;
    private static final int LEFT_BORDER = 20;
    private int PADDING = 20;
    private static final int INSET = 20;
    private CfgScene scene;      
       
    public HierarchicalCompoundLayout(CfgScene scene){
        this.scene = scene;         
    }
    
    @Override
    protected void performGraphLayout(UniversalGraph<CfgNode, CfgEdge> ug) {          
        CompoundDirectedGraph dg = new CompoundDirectedGraph();
        CompoundDirectedGraphLayout layout = new CompoundDirectedGraphLayout();
        NodeList nodeList = dg.nodes;
        EdgeList edgeList = dg.edges;
                
        Map<Integer, Subgraph> idx2graph = new HashMap<Integer, Subgraph>();
        Subgraph base = new Subgraph(0);
        idx2graph.put(0, base);
        base.insets=getInsets();
        for(LoopInfo info : scene.getCfgEnv().getLoopMap().values()){           
            Subgraph subg = new Subgraph(info.getLoopIndex());   
            subg.insets=getInsets();
            idx2graph.put(info.getLoopIndex(), subg);
        }
            
        for(CfgNode n : scene.getCfgEnv().getNodes() ) {
            Widget nodeWidget = scene.findWidget(n);
            Node node = new Node(n);
            node.width=nodeWidget.getBounds().width;
            node.height = nodeWidget.getBounds().height;
            node.setPadding(new Insets(PADDING, PADDING, PADDING, PADDING));          
            Subgraph subg = idx2graph.get(n.getLoopIndex());
            assert(subg != null);
            node.setParent(subg);
            subg.addMember(node);
            nodeList.add(node);
          
        }             
        nodeList.addAll(idx2graph.values());         
        for(LoopInfo info : scene.getCfgEnv().getLoopMap().values()){
            Subgraph subg = idx2graph.get(info.getLoopIndex());
            if(info.getParent() != null){
                Subgraph parentsubg = idx2graph.get(info.getParent().getLoopIndex());
                Edge edge = new Edge(parentsubg, subg);
                parentsubg.addMember(subg);
                subg.setParent(parentsubg);              
                edgeList.add(edge);     
            }               
        }   
        for(CfgEdge e : scene.getCfgEnv().getEdges() ) {                      
            if(e.isBackEdge()) continue;
            Edge edge = new Edge(e, nodeList.getNode(e.getSourceNode().getNodeIndex()), nodeList.getNode(e.getTargetNode().getNodeIndex()));
            edgeList.add(edge);
        } 
        layout.visit(dg);
                
        for(Object obj : dg.nodes){           
            Node n = (Node) obj;
            if(n.data instanceof CfgNode){
                CfgNode cfgNode = (CfgNode) n.data;              
                Point pos = new Point(n.x + LEFT_BORDER, n.y + TOP_BORDER);
                Point scenepos = scene.convertLocalToScene(pos);
                this.setResolvedNodeLocation(ug, cfgNode, scenepos); 
            }
        }      
    }
    
    @Override
    protected void performNodesLayout(UniversalGraph<CfgNode, CfgEdge> ug, Collection<CfgNode> collection) {        
    }
    
    private Insets getInsets(){             
        return new Insets(INSET, INSET, INSET, INSET);        
    }

}
