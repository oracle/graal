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

import at.ssw.dataflow.options.BooleanStringValidator;
import at.ssw.dataflow.options.DoubleStringValidator;
import at.ssw.dataflow.options.IntStringValidator;
import at.ssw.dataflow.options.Validator;
import at.ssw.positionmanager.Cluster;
import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Vertex;
import at.ssw.visualizer.graphhelper.DiGraph;
import at.ssw.visualizer.graphhelper.Edge;
import at.ssw.visualizer.graphhelper.Node;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;


/**
 * This class implements a force directed layout algorithm. It uses the well
 * known spring model that relys on springs and electrical forces. To implement
 * clustering additional virtual nodes are inserted to the original graph.
 * For each cluster one such node is inserted that is connected to all of the
 * nodes within the cluster. Moreover it is connected to each cluster that has
 * links common with the current cluster.
 *
 * @author Stefan Loidl
 */
public class CompoundForceLayouter implements ExternalGraphLayouter{

    //Parameters for the force model
    private double SPRINGLEN = 50;
    private double STIFFNESS = 15;
    private double REPULSION = 100;

    //forces between expanded nodes in the same cluster
    private double SPRINGLENEXP = 50;
    private double STIFFNESSEXP = 15;
    private double REPULSIONEXP = 300;

    //forces between nodes within different clusters
    private double OVERCLUSTERSPRINGLEN = 70;
    private double OVERCLUSTERSTIFFNESS = 15;
    private double OVERCLUSTERREPULSION = 200;

    //Forces between real and virtual nodes
    private double CLUSTERSPRINGLEN = 50;
    private double CLUSTERSTIFFNESS = 15;
    private double CLUSTERREPULSION = 100;

    //Forces between virtual nodes
    private double INTERCLUSTERSPRINGLEN = 300;
    private double INTERCLUSTERSTIFFNESS = 30;
    private double INTERCLUSTERREPULSION = 800;

    //During one cycle one node may only move this distance
    private double MAXIMUMMOVEMENT=500;

    //Space between the connected components
    private int PADDING=30;

    //Iterations the algorithms uses.
    private int ITERATIONS=100;

    //Seed for the random node positioning
    private long SEED=133;

    //Use logarithmic spring model. The alternative is hookes law.
    private boolean USELOGSPRINGS=false;

    //Reuse current node positions for further optimizations
    //the alternative is to use random positions.
    private boolean USECURRENTNODEPOSITIONS=false;

    //Current positions of the nodes
    private Hashtable<String,Point2D> posList;


    //Performs the layout task.
    public void doLayout(LayoutGraph graph) {
        Hashtable<String, Vertex> idtoverticles=new Hashtable<String, Vertex>();
        Hashtable<Vertex, String> verticlestoid=new Hashtable<Vertex,String>();

        DiGraph dg=new DiGraph();
        Iterator<Vertex> iter=graph.getVertices().iterator();

        //Add Nodes to Graphhelper
        int i=0;
        while(iter.hasNext()){
            String id=String.valueOf(i);
            dg.addNode(new Node(id));
            Vertex v=iter.next();

            idtoverticles.put(id,v);
            verticlestoid.put(v,id);
            i++;
        }

        //Add Edges to Graphhelper
        for(Link l: graph.getLinks()){
            String from=verticlestoid.get(l.getFrom().getVertex());
            String to=verticlestoid.get(l.getTo().getVertex());

            dg.addEdge(new Edge(dg.getNode(from),dg.getNode(to)));
        }

        //Add virtual cluster Nodes to Graphhelper
        iter=graph.getVertices().iterator();
        Hashtable<Cluster, Node> virtualNodes=new Hashtable<Cluster,Node>();
        Node defaultNode=new Node(String.valueOf(i++));
        dg.addNode(defaultNode);
        boolean defNodeUsed=false;

        while(iter.hasNext()){
            Vertex v=iter.next();
            Cluster c=v.getCluster();
            Node vn;

            if(c==null) {
                vn=defaultNode;
                defNodeUsed=true;
            }
            else{
                if(virtualNodes.containsKey(c)) vn=virtualNodes.get(c);
                else{
                    String id=String.valueOf(i++);
                    vn=new Node(id);
                    virtualNodes.put(c,vn);
                    dg.addNode(vn);
                }
            }

            dg.addEdge(new Edge(vn,dg.getNode(verticlestoid.get(v))));
        }

        //Create structure to identify virtual nodes later-on
        HashSet<String> vNodes=new HashSet<String>();
        for(Node n: virtualNodes.values()) vNodes.add(n.ID);

        //nodes with no cluster are added to defaultnode. If none exists
        //then delete the node.
        if(!defNodeUsed) dg.removeNode(defaultNode);
        else vNodes.add(defaultNode.ID);

        //Add cluster links to Graphhelper
        for(Cluster c: virtualNodes.keySet()){
            if(c.getSuccessors()!=null){
                for(Cluster c1: c.getSuccessors()){
                    dg.addEdge(new Edge(virtualNodes.get(c),virtualNodes.get(c1)));
                }
            }
        }

        //Perform layout
        layout(graph,dg,idtoverticles,verticlestoid, vNodes);
    }


    /* Performs the layout of the spring algorithm via an iterative relaxation approach*/
    private void layout(LayoutGraph lg, DiGraph digraph, Hashtable<String, Vertex> idtoverticles, Hashtable<Vertex, String> verticlestoid, HashSet<String> vNodes) {
        int lastmax=0;
        int max=0;
        for(DiGraph dg:digraph.getConnectedComponents()){

            //initialize positions with random numbers
            posList=new Hashtable<String,Point2D>();
            Random rand=new Random(SEED);
            for(Node w: dg.getNodes()){
                if(USECURRENTNODEPOSITIONS){
                    Vertex v=idtoverticles.get(w.ID);
                    double x=0.0,y=0.0;
                    if(v!=null){
                        x=v.getPosition().x;
                        y=v.getPosition().y;
                    }
                    posList.put(w.ID,new doublePoint(x,y));
                }
                else posList.put(w.ID,new doublePoint(rand.nextDouble()*300d,rand.nextDouble()*300d));
            }

            //perform several relaxation iterations
            for(int i=0; i<ITERATIONS; i++){
                for(Node n: dg.getNodes()){
                    relaxation(n,dg, vNodes, idtoverticles);
                }
            }

            //Move the connected component next to the previously calculated one
            double smallestX=Double.MAX_VALUE;
            double smallestY=Double.MAX_VALUE;
            for(Node w: dg.getNodes()){
                Point2D p=posList.get(w.ID);
                if(smallestX>p.getX()) smallestX=p.getX();
                if(smallestY>p.getY()) smallestY=p.getY();
            }

            max=0;
            for(Node w: dg.getNodes()){
                Vertex v=idtoverticles.get(w.ID);
                if(v==null) continue;

                Point2D p=posList.get(w.ID);
                int x=((int)(p.getX()-smallestX))+lastmax+PADDING;
                int y=((int)(p.getY()-smallestY))+PADDING;

                if(x+v.getSize().width >max) max=x+v.getSize().width;
                v.setPosition(new Point(x, y));
            }
            lastmax=max;
        }
    }


    /*
     * Calculates the forces on node n and moves it a small distance to lessen
     * it. Forces for virtual nodes are different from others but do the same
     * job.
     */
    private void relaxation(Node n, DiGraph dg, HashSet<String> vNodes, Hashtable<String, Vertex> idtoverticles){

        double  X = posList.get(n.ID).getX();
        double  Y = posList.get(n.ID).getY();

        LinkedList<Node> adjacentVertices=new LinkedList<Node>();

        Vertex v=idtoverticles.get(n.ID);

        // Get all adjacent Nodes
        for(Node pre: n.pred){
            if(!n.ID.equals(pre.ID))
                adjacentVertices.add(pre);
        }
        for(Node succ: n.succ){
            if(!n.ID.equals(succ.ID))
                adjacentVertices.add(succ);
        }

        //Calcualte Spring len between all adjacent Nodes
        double  SpringX = 0, SpringY = 0;
        for(Node adjacent:adjacentVertices) {
            double stiffness, springlen;

            if(vNodes.contains(n.ID) || vNodes.contains(adjacent.ID)){
                if(vNodes.contains(n.ID) && vNodes.contains(adjacent.ID)){
                    stiffness=INTERCLUSTERSTIFFNESS;
                    springlen=INTERCLUSTERSPRINGLEN;
                }
                else{
                    stiffness=CLUSTERSTIFFNESS;
                    springlen=CLUSTERSPRINGLEN;
                }
            }
            else{
                Vertex v1=idtoverticles.get(adjacent.ID);
                if((v1!=null && v!=null)&& v1.getCluster()!=v.getCluster()){
                    stiffness=OVERCLUSTERSTIFFNESS;
                    springlen=OVERCLUSTERSPRINGLEN;
                }
                else{
                    if((v1!=null && v!=null)&&(v1.isExpanded()||v.isExpanded())){
                        stiffness=STIFFNESSEXP;
                        springlen=SPRINGLENEXP;
                    }else{
                        stiffness=STIFFNESS;
                        springlen=SPRINGLEN;
                    }
                }
            }

            double adjX = posList.get(adjacent.ID).getX();
            double adjY = posList.get(adjacent.ID).getY();

            double distance = Point2D.distance( adjX, adjY, X, Y );
            //Minimum distance between nodes!
            if(distance == 0) distance = 0.01d;

            if(USELOGSPRINGS){
                //Logarithmic Springs
                SpringX +=stiffness*Math.log(distance/springlen)*((X-adjX)/distance);
                SpringY +=stiffness*Math.log(distance/springlen)*((Y-adjY)/distance);
            }
            else{
                //Hookes Law with relativ springkonstant
                SpringX +=stiffness*((distance-springlen)/(2*springlen)) *((X-adjX)/distance);
                SpringY +=stiffness*((distance-springlen)/(2*springlen)) *((Y-adjY)/distance);
            }
        }

        //Calcualte Repulsion
        double  RepulsionX = 0, RepulsionY = 0;
        for(Node w: dg.getNodes()) {
            if(w == n) continue;

            double repulsion;
            if(vNodes.contains(n.ID) || vNodes.contains(w.ID)) {
                if(vNodes.contains(n.ID) && vNodes.contains(w.ID)){
                    repulsion=INTERCLUSTERREPULSION;
                }
                else{
                    repulsion=CLUSTERREPULSION;
                }
            }
            else {
                Vertex v1=idtoverticles.get(w.ID);
                if((v1!=null && v!=null)&& v1.getCluster()!=v.getCluster()){
                    repulsion=OVERCLUSTERREPULSION;
                }
                else {
                    if((v1!=null && v!=null)&&(v1.isExpanded()||v.isExpanded()))
                        repulsion=REPULSIONEXP;
                    else repulsion=REPULSION;
                }
            }

            double nX = posList.get(w.ID).getX();
            double nY = posList.get(w.ID).getY();

            double distance = Point2D.distance( nX, nY, X, Y );
            if(distance == 0) distance = 0.01d;

            //If Spring energy is positiv- this one is negativ
            RepulsionX -= (repulsion/distance)*((X-nX)/distance);
            RepulsionY -= (repulsion/distance)*((Y-nY)/distance);
        }

        // Move Node in direction of the force
        double dx =  -(SpringX + RepulsionX);
        double dy =  -(SpringY + RepulsionY);
        //Make shure the node moves not too far off the others
        if(dx>MAXIMUMMOVEMENT) dx=MAXIMUMMOVEMENT;
        if(dx*-1>MAXIMUMMOVEMENT) dx=-MAXIMUMMOVEMENT;
        if(dy>MAXIMUMMOVEMENT) dy=MAXIMUMMOVEMENT;
        if(dy*-1>MAXIMUMMOVEMENT) dy=-MAXIMUMMOVEMENT;

        Point2D p=posList.get(n.ID);
        p.setLocation(p.getX()+dx,p.getY()+dy);
    }

    /* Performs the routing via a simple direct line router */
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

    public void setUseCurrentNodePositions(boolean b) {
        USECURRENTNODEPOSITIONS=b;
    }

     //<editor-fold defaultstate="collapsed" desc=" Option Provider Methods ">
     private static String[] options={"Componentpadding", "Springlength","Over Cl. Sp.Length", "Virtual Springlength", "Stiffness","Over Cl. Stiffness", "Virtual Stiffness", "Iterations", "Repulsion","Over Cl. Repulsion","Virtual Repulsion", "Seed", "Log. Springs"};
     private static String[] descriptions={"Minimum space between connected components",
     "Length of the spring between nodes in the same cluster","Length of the spring between nodes in different clusters","Length of the spring between virtual nodes",
     "Stiffness of the spring between the nodes in the same cluster","Stiffness of the spring between the nodes in different clusters", "Stiffness of the spring between the virtual nodes",
     "Relaxation iterations the algorithm performs",
     "Repulsion between nodes in the same cluster","Repulsion between nodes in different clusters", "Repulsion between vitual nodes",
     "Seed for the random prepositioning", "Logarithmic spring simulation is used?"};
     private static Class[] optionclass={String.class,String.class, String.class,String.class,String.class,String.class,String.class};
     private static Validator[] validators={
         new IntStringValidator(0,1000),              //padding
         new DoubleStringValidator(0.0d,1000.0d),     //Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //Over cluster Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //Virtual Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //Stiffness
         new DoubleStringValidator(0.0d,1000.0d),     //over cluster Stiffness
         new DoubleStringValidator(0.0d,1000.0d),     //virtual Stiffness
         new IntStringValidator(0,5000),              //Iterations
         new DoubleStringValidator(0.0d,10000.0d),    //Repulsion
         new DoubleStringValidator(0.0d,10000.0d),    //Over cluster Repulsion
         new DoubleStringValidator(0.0d,10000.0d),    //Virtual Repulsion
         new IntStringValidator(0,Integer.MAX_VALUE), //Seed
         new BooleanStringValidator()                 //Log Spring
     };


     public String[] getOptionKeys() {
         return options;
     }

     public boolean setOption(String key, Object value) {
         if(key==null) return false;

         int i=getIndexForKey(key);
         if(i==-1) return false;

         if(validators[i].validate(value)){
             switch(i){
                 case 0: PADDING=Integer.parseInt((String)value); break;
                 case 1: SPRINGLEN=Double.parseDouble((String)value);break;
                 case 2: OVERCLUSTERSPRINGLEN=Double.parseDouble((String)value);break;
                 case 3: INTERCLUSTERSPRINGLEN=Double.parseDouble((String)value);break;
                 case 4: STIFFNESS=Double.parseDouble((String)value);break;
                 case 5: OVERCLUSTERSTIFFNESS=Double.parseDouble((String)value);break;
                 case 6: INTERCLUSTERSTIFFNESS=Double.parseDouble((String)value);break;
                 case 7: ITERATIONS=Integer.parseInt((String)value);break;
                 case 8: REPULSION=Double.parseDouble((String)value);break;
                 case 9: OVERCLUSTERREPULSION=Double.parseDouble((String)value);break;
                 case 10: INTERCLUSTERREPULSION=Double.parseDouble((String)value);break;
                 case 11: SEED=Integer.parseInt((String)value);break;
                 case 12: USELOGSPRINGS=Boolean.parseBoolean((String)value);break;
                 default: return false;
             }
             return true;
         }
         return false;
     }


     public Object getOption(String key) {
         if(key==null) return null;

         int i=getIndexForKey(key);

         switch(i){
             case 0: return String.valueOf(PADDING);
             case 1: return String.valueOf(SPRINGLEN);
             case 2: return String.valueOf(OVERCLUSTERSPRINGLEN);
             case 3: return String.valueOf(INTERCLUSTERSPRINGLEN);
             case 4:  return String.valueOf(STIFFNESS);
             case 5:  return String.valueOf(OVERCLUSTERSTIFFNESS);
             case 6:  return String.valueOf(INTERCLUSTERSTIFFNESS);
             case 7: return String.valueOf(ITERATIONS);
             case 8: return String.valueOf(REPULSION);
             case 9: return String.valueOf(OVERCLUSTERREPULSION);
             case 10: return String.valueOf(INTERCLUSTERREPULSION);
             case 11: return String.valueOf(SEED);
             case 12: return String.valueOf(USELOGSPRINGS);
             default: return null;
         }
     }

     public int getIndexForKey(String key){
         for(int i=0;i<options.length;i++){
             String o=options[i];
             if(o.equals(key)) return i;
         }
         return -1;
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


     public Validator getOptionValidator(String key) {
         if (key==null) return null;
         for(int i=0; (i < validators.length) && (i < options.length); i++){
             if(key.equals(options[i])){
                 return validators[i];
             }
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
