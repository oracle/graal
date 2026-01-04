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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

/**
 * This class implements a force-directed layout algorithm. It uses the well
 * known spring model that relys on springs and electrical forces.
 *
 * @author Stefan Loidl
 */
public class ForceLayouter implements ExternalGraphLayouter{

    //Parameters for the foce model
    private double SPRINGLEN = 50;
    private double STIFFNESS = 15;
    private double REPULSION = 100;
    //Parameters if one of the nodes is expanded
    private double SPRINGLENEXP = 50;
    private double STIFFNESSEXP = 15;
    private double REPULSIONEXP = 300;

    //During one cycle one node may only move this distance
    private double MAXIMUMMOVEMENT=500;

    //Space between the connected components
    private int PADDING=30;

    //Iterations the algorithms uses.
    private int ITERATIONS=150;

    //Seed for the random node positioning
    private long SEED=133;

    //Use logarithmic spring model. The alternative is hookes law.
    private boolean USELOGSPRINGS=false;

    //Reuse current node positions for further optimizations
    //the alternative is to use random positions.
    private boolean USECURRENTNODEPOSITIONS=false;

    //Current positions of the nodes
    private Hashtable<String,Point2D> posList;

    /* Performs the layout cycles */
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

        //Perform the layout
        layout(graph,dg,idtoverticles,verticlestoid);
    }

    /* Peforms the layout per connected component */
    private void layout(LayoutGraph lg, DiGraph digraph, Hashtable<String, Vertex> idtoverticles, Hashtable<Vertex, String> verticlestoid) {
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
                    relaxation(n,dg,idtoverticles);
                }
            }

            //Move the current connected component next to the previous one
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
     * it.
     */
    private void relaxation(Node n, DiGraph dg, Hashtable<String, Vertex> idtoverticles){
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
     private static String[] options={"Componentpadding", "Springlength","Expanded Springlength", "Stiffness","Expanded Stiffness", "Iterations", "Repulsion","Expanded Repulsion", "Seed", "Log. Springs", "Maximum Displacement"};
     private static String[] descriptions={"Minimum space between connected components",
     "Length of the spring between nodes","Length of the spring between expanded nodes", "Stiffness of the spring between the nodes",
     "Stiffness of the spring between expanded nodes","Relaxation iterations the algorithm performs", "Standard repulsion of a unexpanded Node", "Standard repulsion of a expanded Node",
     "Seed for the random prepositioning", "Logarithmic spring simulation is used?", "Maximum displacement in x and y direction during relaxation."};
     private static Class[] optionclass={String.class,String.class,String.class, String.class,String.class,String.class,String.class,String.class};
     private static Validator[] validators={
         new IntStringValidator(0,1000),              //padding
         new DoubleStringValidator(0.0d,1000.0d),     //Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //expanded Springlen
         new DoubleStringValidator(0.0d,1000.0d),     //Stiffness
         new DoubleStringValidator(0.0d,1000.0d),     //expanded Stiffness
         new IntStringValidator(0,5000),              //Iterations
         new DoubleStringValidator(0.0d,10000.0d),    //Repulsion
         new DoubleStringValidator(0.0d,10000.0d),    //expanded Repulsion
         new IntStringValidator(0,Integer.MAX_VALUE), //Seed
         new BooleanStringValidator(),                //Log Spring
         new DoubleStringValidator(0.0d,5000.0d)      //max. displacement
     };


     public String[] getOptionKeys() {
         return options;
     }

     public boolean setOption(String key, Object value) {
         if(key==null) return false;
         //key equals "Componentpadding"
         if(key.equals(options[0])){
             if(validators[0].validate(value)){
                 PADDING=Integer.parseInt((String)value);
                 return true;
             }
         }
         else if(key.equals(options[1])){
             if(validators[1].validate(value)){
                 SPRINGLEN=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[2])){
             if(validators[2].validate(value)){
                 SPRINGLENEXP=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[3])){
             if(validators[3].validate(value)){
                 STIFFNESS=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[4])){
             if(validators[4].validate(value)){
                 STIFFNESSEXP=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[5])){
             if(validators[5].validate(value)){
                 ITERATIONS=Integer.parseInt((String)value);
                 return true;
             }
         }
         else if(key.equals(options[6])){
             if(validators[6].validate(value)){
                 REPULSION=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[7])){
             if(validators[7].validate(value)){
                 REPULSIONEXP=Double.parseDouble((String)value);
                 return true;
             }
         }
         else if(key.equals(options[8])){
             if(validators[8].validate(value)){
                 SEED=Integer.parseInt((String)value);
                 return true;
             }
         }
         else if(key.equals(options[9])){
             if(validators[9].validate(value)){
                 USELOGSPRINGS=Boolean.parseBoolean((String)value);
                 return true;
             }
         }
         else if(key.equals(options[10])){
             if(validators[10].validate(value)){
                 MAXIMUMMOVEMENT=Double.parseDouble((String)value);
                 return true;
             }
         }
         return false;
     }


     public Object getOption(String key) {
         if(key==null) return null;
         //key equals "Componentpadding"
         if(key.equals(options[0])){
             return String.valueOf(PADDING);
         }
         //key equals "Springlength"
         else if(key.equals(options[1])){
             return String.valueOf(SPRINGLEN);
         }
         //key equals "Expanded Springlength"
         else if(key.equals(options[2])){
             return String.valueOf(SPRINGLENEXP);
         }
         //key equals "Stiffness"
         else if(key.equals(options[3])){
             return String.valueOf(STIFFNESS);
         }
         //key equals "Expanded Stiffness"
         else if(key.equals(options[4])){
             return String.valueOf(STIFFNESSEXP);
         }
         //key equals "Iterations"
         else if(key.equals(options[5])){
             return String.valueOf(ITERATIONS);
         }
         //key equals "Repulsion"
         else if(key.equals(options[6])){
             return String.valueOf(REPULSION);
         }
         //key equals "Repulsion"
         else if(key.equals(options[7])){
             return String.valueOf(REPULSIONEXP);
         }
         //key equals "SEED"
         else if(key.equals(options[8])){
             return String.valueOf(SEED);
         }
         //key equals "Log Springs"
         else if(key.equals(options[9])){
             return String.valueOf(USELOGSPRINGS);
         }
         //key equals "Maximum Displacement"
         else if(key.equals(options[10])){
             return String.valueOf(MAXIMUMMOVEMENT);
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
