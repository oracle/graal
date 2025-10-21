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
import at.ssw.visualizer.graphhelper.Node;
import at.ssw.dataflow.options.BooleanStringValidator;
import at.ssw.dataflow.options.DoubleStringValidator;
import at.ssw.dataflow.options.IntStringValidator;
import at.ssw.dataflow.options.Validator;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * This class implements a force-directed layout building on the well known
 * spring-model. To introduce direction to the graph-layout the magnetic
 * spring model descibed in the following work was used:
 *
 * Kozo Sugiyama, Kazuo Misue: Graph Drawing by Magnetic-Spring Model;
 * Technical Report, 1994. ISIS-RR-94-14E.
 *
 * @author Stefan Loidl
 */
public class MagneticSpringForceLayouter implements ExternalGraphLayouter{

    //Parameters for the foce model
    private double SPRINGLEN = 50;
    private double STIFFNESS = 15;
    private double REPULSION = 100;

    //Parameters if one of the nodes is expanded
    private double SPRINGLENEXP = 50;
    private double STIFFNESSEXP = 15;
    private double REPULSIONEXP = 250;

    //Strength of the parallel vertical field
    private double FIELDSTRENGTH=0.05;

    //During one cycle one node may only move this distance
    private double MAXIMUMMOVEMENT=500;

    //Space between the connected components
    private int PADDING=30;

    //Iterations the algorithms uses.
    private int ITERATIONS=300;

    //Seed for the random node positioning
    private long SEED=133;

    //Use logarithmic spring model. The alternative is hookes law.
    private boolean USELOGSPRINGS=false;

    //Reuse current node positions for further optimizations
    //the alternative is to use random positions.
    private boolean USECURRENTNODEPOSITIONS=false;

    //length of the area the initial layout is done on
    private double INITIALLAYOUTSIDE=3000d;
    private double LAYERHEIGHT=100d;

    //Current positions of the nodes
    private Hashtable<String,Point2D> posList;


    /* Performs the layout algorithm */
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


    /*
     * Performs an initial layout step based on the breadth first search graph
     * traversial.
     */
    private void BFSInitialAssignment(LinkedList<Node> fringe, HashSet<Node> assigned, double layer){
        if(fringe==null || fringe.size()==0) return;
        assigned.addAll(fringe);

        LinkedList<Node> newFringe=new LinkedList<Node>();
        double x=0.0, deltaX=INITIALLAYOUTSIDE/(fringe.size()+1);

        for(Node n: fringe){
            x+=deltaX;
            posList.put(n.ID,new doublePoint(x,layer));
            for(Node s:n.succ) if(!assigned.contains(s)) newFringe.add(s);
        }
        BFSInitialAssignment(newFringe,assigned,layer+LAYERHEIGHT);
    }

    /*
     * Performs the layout task. Note that three differnent approaches for
     * the initial positioning of nodes are implemented. One possibility is
     * to use the currnet node positions without change. The second is to use
     * a random initial layout. And the third one is to use the BFS positioning
     * that introduces a pre-layering step.
     */
    private void layout(LayoutGraph lg, DiGraph digraph, Hashtable<String, Vertex> idtoverticles, Hashtable<Vertex, String> verticlestoid) {
        int lastmax=0;
        int max=0;
        for(DiGraph dg:digraph.getConnectedComponents()){

            //initialize positions with random numbers
            posList=new Hashtable<String,Point2D>();
            Random rand=new Random(133);

            //reuse current position
            if(USECURRENTNODEPOSITIONS){
                for(Node w: dg.getNodes()){
                    Vertex v=idtoverticles.get(w.ID);
                    double x=0.0,y=0.0;
                    if(v!=null){
                        x=v.getPosition().x;
                        y=v.getPosition().y;
                    }
                    posList.put(w.ID,new doublePoint(x,y));
                }

            }
            //Breadth first search layer positioning
            else{
                HashSet<Node> assigned=new HashSet<Node>();
                LinkedList<Node> fringe=new LinkedList<Node>();

                for(Vertex v: new TreeSet<Vertex>(lg.findRootVertices())){
                    Node n=dg.getNode(verticlestoid.get(v));
                    if(n!=null) fringe.add(n);
                }
                BFSInitialAssignment(fringe,assigned,LAYERHEIGHT);
                for(Node n:dg.getNodes()){
                    if(!assigned.contains(n))
                        posList.put(n.ID,new doublePoint(rand.nextDouble()*INITIALLAYOUTSIDE,rand.nextDouble()*INITIALLAYOUTSIDE));
                }
            }

            //perform several relaxation iterations
            for(int i=0; i<ITERATIONS; i++){
                for(Node n: dg.getNodes()){
                    relaxation(n,dg,idtoverticles);
                }
            }

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
     * it. The magnetical field introduces a radial force on the edges.
     * This influence is simplified and modeled as vertical force pointing
     * downwards.
     */
    public void relaxation(Node n, DiGraph dg, Hashtable<String, Vertex> idtoverticles){
        double stiffness, repulsion, springlen;
        double  X = posList.get(n.ID).getX();
        double  Y = posList.get(n.ID).getY();
        boolean nExpanded=false;
        Vertex v=idtoverticles.get(n.ID);

        if(v!=null) nExpanded=v.isExpanded();

        LinkedList<Node> adjacentVertices=new LinkedList<Node>();

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
            //determine if one of the nodes is expanded
            boolean expanded=nExpanded;
            v=idtoverticles.get(adjacent.ID);
            if(v!=null) expanded|=v.isExpanded();

            if(expanded){
                stiffness=STIFFNESSEXP;
                springlen=SPRINGLENEXP;
            }
            else{
                stiffness=STIFFNESS;
                springlen=SPRINGLEN;
            }

            double adjX = posList.get(adjacent.ID).getX();
            double adjY = posList.get(adjacent.ID).getY();

            double distance = Point2D.distance( adjX, adjY, X, Y );
            //Minimum distance between nodes!
            if(distance == 0) distance = 0.01d;

            //Calculate the angle beween the directed spring and the vertical magnetic field
            //via the angle between the vector resulting from the positions (vX,vY) and
            //the vector (0,1). angle=arcCos( (0*vX+1*vY)/(len(vX,vY)*len(0,1)) )
            //This only applies if the current node is the endpoint of the spring
            if(n.pred.contains(adjacent) && !n.succ.contains(adjacent)){
                double vX=X-adjX;
                double vY=Y-adjY;
                double angle=Math.acos((vY)/Point2D.distance(0,0,vX,vY));
                SpringY-=Math.abs(angle)*distance*FIELDSTRENGTH;
            }


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

            //determine if one of the nodes is expanded
            boolean expanded=nExpanded;
            v=idtoverticles.get(w.ID);
            if(v!=null) expanded|=v.isExpanded();

            if(expanded) repulsion=REPULSIONEXP;
            else repulsion=REPULSION;

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
        USECURRENTNODEPOSITIONS=b;
    }

     //<editor-fold defaultstate="collapsed" desc=" Option Provider Methods ">
     private static String[] options={"Componentpadding", "Springlength","Expanded Springlength", "Stiffness","Expanded Stiffness","Field Strength", "Iterations", "Repulsion","Expanded Repulsion", "Log. Springs", "Maximum Displacement"};
     private static String[] descriptions={"Minimum space between connected components",
     "Length of the spring between nodes","Length of the spring between expanded nodes", "Stiffness of the spring between the nodes",
     "Stiffness of the spring between expanded nodes","Strength of the vertical parallel field", "Relaxation iterations the algorithm performs", "Standard repulsion of a unexpanded Node", "Standard repulsion of a expanded Node",
     "Logarithmic spring simulation is used?", "Maximum displacement in x and y direction during relaxation."};
     private static Class[] optionclass={String.class,String.class,String.class, String.class,String.class,String.class,String.class,String.class};
     private static Validator[] validators={
         new IntStringValidator(0,1000),              //padding
         new DoubleStringValidator(0.0d,1000.0d),     //Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //expanded Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //Stiffness
         new DoubleStringValidator(0.0d,1000.0d),     //expanded Stiffness
         new DoubleStringValidator(0.0d,10.0d),       //field strength
         new IntStringValidator(0,5000),              //Iterations
         new DoubleStringValidator(0.0d,10000.0d),    //Repulsion
         new DoubleStringValidator(0.0d,10000.0d),    //expanded Repulsion
         new BooleanStringValidator(),                //Log Spring
         new DoubleStringValidator(0.0d,5000.0d)      //max. displacement
     };


     public String[] getOptionKeys() {
         return options;
     }

     public int getIndexForKey(String key){
         for(int i=0;i<options.length;i++){
             String o=options[i];
             if(o.equals(key)) return i;
         }
         return -1;
     }

     public boolean setOption(String key, Object value) {
         if(key==null) return false;

         int index=getIndexForKey(key);

         if(index==-1 || !validators[index].validate((String)value)) return false;

         switch(index){
             case 0: PADDING=Integer.parseInt((String)value); return true;
             case 1: SPRINGLEN=Double.parseDouble((String)value); return true;
             case 2: SPRINGLENEXP=Double.parseDouble((String)value); return true;
             case 3: STIFFNESS=Double.parseDouble((String)value); return true;
             case 4: STIFFNESSEXP=Double.parseDouble((String)value); return true;
             case 5: FIELDSTRENGTH=Double.parseDouble((String)value); return true;
             case 6: ITERATIONS=Integer.parseInt((String)value); return true;
             case 7: REPULSION=Double.parseDouble((String)value); return true;
             case 8: REPULSIONEXP=Double.parseDouble((String)value); return true;
             case 9: USELOGSPRINGS=Boolean.parseBoolean((String)value); return true;
             case 10: MAXIMUMMOVEMENT=Double.parseDouble((String)value); return true;
         }
         return false;
     }


     public Object getOption(String key) {
         if(key==null) return null;

         int index=getIndexForKey(key);

         switch(index){
             case 0: return String.valueOf(PADDING);
             case 1: return String.valueOf(SPRINGLEN);
             case 2: return String.valueOf(SPRINGLENEXP);
             case 3: return String.valueOf(STIFFNESS);
             case 4: return String.valueOf(STIFFNESSEXP);
             case 5: return String.valueOf(FIELDSTRENGTH);
             case 6: return String.valueOf(ITERATIONS);
             case 7: return String.valueOf(REPULSION);
             case 8: return String.valueOf(REPULSIONEXP);
             case 9: return String.valueOf(USELOGSPRINGS);
             case 10: return String.valueOf(MAXIMUMMOVEMENT);
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
