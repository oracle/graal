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

import at.ssw.positionmanager.Cluster;
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
import org.eclipse.draw2d.graph.CompoundDirectedGraph;
import org.eclipse.draw2d.graph.CompoundDirectedGraphLayout;
import org.eclipse.draw2d.graph.Edge;
import org.eclipse.draw2d.graph.Node;
import org.eclipse.draw2d.graph.Subgraph;


/**
 * Positioning using Compound-Hirachical Layout from GEF draw2d library.
 * This class is a wrapper that transforms the input graph models.
 *
 * @author Stefan Loidl
 */
public class CompoundHierarchicalNodesLayouter implements ExternalGraphLayouter{

    //Borders of the graph within the drawing
    private static final int TOP_BORDER = 20;
    private static final int LEFT_BORDER = 20;

    //Padding of the nodes
    private int PADDING = 50;
    //Inset within the clusters
    private int SUBGRAPHINSET=10;

    //This Identifier is used as name for the default cluster
    private static final String DEFAULTCLUSTER="DEFAULT";
    //Top cluster holding all other clusters
    private static final String TOPCLUSTER="TOP";


    /* Performs the layout task */
    public void doLayout(LayoutGraph graph) {
        Hashtable<String, Vertex> idtoverticles=new Hashtable<String, Vertex>();
        Hashtable<Vertex, String> verticlestoid=new Hashtable<Vertex,String>();

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

        layout(graph,dg,idtoverticles,verticlestoid);
    }


    private void layout(LayoutGraph lg, DiGraph digraph, Hashtable<String, Vertex> idtoverticles, Hashtable<Vertex, String> verticlestoid) {
        int lastmax=0;
        int max=0;
        for(DiGraph dg:digraph.getConnectedComponents()){
            //build draw2d compound Graph
            CompoundDirectedGraph cdg=new CompoundDirectedGraph();

            Subgraph top=new Subgraph(TOPCLUSTER);
            Subgraph defaultG=new Subgraph(DEFAULTCLUSTER,top);
            defaultG.innerPadding=new Insets(SUBGRAPHINSET,SUBGRAPHINSET,SUBGRAPHINSET,SUBGRAPHINSET);
            cdg.nodes.add(defaultG);
            cdg.nodes.add(top);

            Hashtable<Cluster,Subgraph> subgraphs=new Hashtable<Cluster,Subgraph>();
            Hashtable<String,Node> nodelist=new Hashtable<String,Node>();

            //Add Nodes and clusters to the cdg
            for(at.ssw.visualizer.graphhelper.Node n: dg.getNodes()){
                Vertex v=idtoverticles.get(n.ID);
                Cluster c=v.getCluster();
                Subgraph sg;
                //find or create subgraph for cluster c
                if(c==null) sg=defaultG;
                else{
                    if(subgraphs.containsKey(c)) sg=subgraphs.get(c);
                    else{
                        sg=new Subgraph(c.toString(),top);
                        sg.innerPadding=new Insets(SUBGRAPHINSET,SUBGRAPHINSET,SUBGRAPHINSET,SUBGRAPHINSET);
                        subgraphs.put(c,sg);
                        cdg.nodes.add(sg);
                    }
                }

                Node node=new Node(v,sg);

                node.width = v.getSize().width;
                node.height = v.getSize().height;
                node.setPadding(new Insets(PADDING, PADDING, PADDING, PADDING));

                cdg.nodes.add(node);
                nodelist.put(n.ID,node);
            }

            //Add all edges to the cdg
            for(at.ssw.visualizer.graphhelper.Edge e: dg.getEdges()){
                Node n1=nodelist.get(e.source.ID);
                Node n2=nodelist.get(e.destination.ID);

                //Subgraphs are chained by node relation too
                if(n1.getParent()!=n2.getParent()) cdg.edges.add(new Edge(n1.getParent(),n2.getParent()));

                cdg.edges.add(new Edge(n1,n2));
            }


            //do Layouting step
            CompoundDirectedGraphLayout layout = new CompoundDirectedGraphLayout();
            layout.visit(cdg);

            //Assign positions
            lastmax+=max;
            max=0;
            for (int i = 0; i < cdg.nodes.size(); i++) {
                Node n = cdg.nodes.getNode(i);
                assert n.data != null;

                //Continue with subgraphs...
                if(!(n.data instanceof Vertex)) continue;
                Vertex v=(Vertex)n.data;

                Point p = new Point(lastmax+ n.x + LEFT_BORDER, n.y + TOP_BORDER);

                int width=v.getSize().width;
                if(n.x+width > max) max=n.x+width;

                v.setPosition(p);
            }
        }
    }

    /* Performs the routing task via a simple direct-line router */
    public void doRouting(LayoutGraph graph) {
        RoutingHelper.doRouting(graph);
    }

    public boolean isClusteringSupported() {
        return true;
    }

    public boolean isAnimationSupported() {
        return true;
    }

    public boolean isMovementSupported() {
        return true;
    }

    public void setUseCurrentNodePositions(boolean b) {}


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

