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
package at.ssw.dataflow.layout;

import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Vertex;
import at.ssw.visualizer.graphhelper.DiGraph;
import at.ssw.dataflow.options.IntStringValidator;
import at.ssw.dataflow.options.Validator;
import java.awt.Point;
import java.util.Hashtable;
import java.util.Iterator;
import org.eclipse.draw2d.geometry.Insets;
import org.eclipse.draw2d.graph.DirectedGraph;
import org.eclipse.draw2d.graph.DirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;

/**
 * Node Layout using Hirachical Layout from GEF draw2d library.
 * This class is a wrapper that transforms the input graph models.
 *
 * @author Stefan Loidl
 */
public class HierarchicalNodesLayouter implements ExternalGraphLayouter{

    //Inset of the graph in the overall painting area
    private static final int TOP_BORDER = 20;
    private static final int LEFT_BORDER = 20;

    //Padding of the nodes and the connected components
    private int PADDING = 30;

    /*
     * Performs the layout task via transformation of the LayoutGraph-model
     * to that of GEF. Afterwards the algorithm is simply applied.
     */
    public void doLayout(LayoutGraph graph) {
        Hashtable<String, Vertex> idtoverticles=new Hashtable<String, Vertex>();
        Hashtable<Vertex, String> verticlestoid=new Hashtable<Vertex,String>();

        Hashtable<String, Node> d2dNodes=new Hashtable<String, Node>();


        DiGraph dg=new DiGraph();
        Iterator<Vertex> iter=graph.getVertices().iterator();

        //Add Nodes to Graphhelper
        int i=0;
        while(iter.hasNext()){
            String id=String.valueOf(i);
            dg.addNode(new at.ssw.visualizer.graphhelper.Node(id));
            Vertex v=iter.next();

            idtoverticles.put(id,v);
            verticlestoid.put(v,id);
            i++;
        }

        //Add Edges to Graphhelper
        for(Link l: graph.getLinks()){
            String from=verticlestoid.get(l.getFrom().getVertex());
            String to=verticlestoid.get(l.getTo().getVertex());

            dg.addEdge(new at.ssw.visualizer.graphhelper.Edge(dg.getNode(from),dg.getNode(to)));
        }

        int max=0, lastmax=0;
        //Layouting for each connected component
        for(DiGraph g:dg.getConnectedComponents()){
            DirectedGraph d2dGraph=new DirectedGraph();
            //Add Nodes to Draw2D DirectedGraph
            for(at.ssw.visualizer.graphhelper.Node n:g.getNodes()){
                Vertex v=idtoverticles.get(n.ID);
                Node node=new Node();
                node.data=v;
                node.width = v.getSize().width;
                node.height = v.getSize().height;
                node.setPadding(new Insets(PADDING, PADDING, PADDING, PADDING));
                d2dGraph.nodes.add(node);
                d2dNodes.put(n.ID,node);
            }

            //Add Edges to Draw2D DirectedGraph
            for(at.ssw.visualizer.graphhelper.Edge e:g.getEdges()){
                Edge edge=new Edge(d2dNodes.get(e.source.ID),d2dNodes.get(e.destination.ID));
                d2dGraph.edges.add(edge);
            }

            //Perform Layouting step
            DirectedGraphLayout layout = new DirectedGraphLayout();
            layout.visit(d2dGraph);

            //Calculate actual positions
            lastmax+=max+PADDING;
            max=0;
            for (int j = 0; j < d2dGraph.nodes.size(); j++) {
                Node n = d2dGraph.nodes.getNode(j);
                assert n.data != null;
                Vertex v = (Vertex)n.data;

                Point p = new Point(lastmax+ n.x + LEFT_BORDER, n.y + TOP_BORDER);

                int width=v.getSize().width;
                if(n.x+width > max) max=n.x+width;

                v.setPosition(p);
            }
        }
    }

    /* Performs the routing using a simple direct-line router */
    public void doRouting(LayoutGraph graph) {
        RoutingHelper.doRouting(graph);
    }

    public boolean isClusteringSupported() {
        return false;
    }

    public boolean isAnimationSupported() {
        return true;
    }

    public boolean isMovementSupported() {
        return true;
    }

    public void setUseCurrentNodePositions(boolean b) {
    }

     //<editor-fold defaultstate="collapsed" desc=" Option Provider Methods ">
     private static String[] options={"Padding"};
     private static String[] descriptions={"Minimum space between nodes"};
     private static Class[] optionclass={String.class};
     private static Validator paddingValidator=new IntStringValidator(0,1000);

     public String[] getOptionKeys() {
         return options;
     }

     public boolean setOption(String key, Object value) {
         //key equals "padding"
         if(key!=null && key.equals(options[0])){
             if(paddingValidator.validate(value)){
                 PADDING=Integer.parseInt((String)value);
                 return true;
             }
         }
         return false;
     }

     public Validator getOptionValidator(String key) {
         //key equals "padding"
         if(key!=null && key.equals(options[0])){
             return paddingValidator;
         }
         return null;
     }

     public String getOptionDescription(String key) {
         if (key==null) return null;
         for(int i=0; (i < descriptions.length) && (i < options.length); i++){
             if(key.equals(options[i])){
                 return descriptions[i];
             }
         }
         return null;
     }

     public Object getOption(String key) {
         //key equals "padding"
         if(key!=null && key.equals(options[0])){
             return String.valueOf(PADDING);
         }
         return null;
     }

     public Class getOptionClass(String key) {
         if (key==null) return null;
         for(int i=0; (i < optionclass.length) && (i < options.length); i++){
             if(key.equals(options[i])){
                 return optionclass[i];
             }
         }
         return null;
     }
     //</editor-fold>
}

