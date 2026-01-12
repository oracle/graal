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
package at.ssw.visualizer.graphhelper;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class is a helper that creates an embedding of a DiGraph
 * by traversing the nodes (and their edges) according to the clockwise
 * and counter clockwise ordering of the edges within the node.
 * Before creating an instance the DiGraph must be:
 *   - planarized
 *   - embedded
 * The embedding is represented by a finite list of faces.
 *
 * @author Stefan Loidl
 */
public class Embedding {

    private DiGraph graph;
    private LinkedList<Face> faces=new LinkedList<Face>();
    private Face infinityFace=null;


    /**
     * Creates an embedding from a planar DiGraph with
     * Edges sorted clockwise. Bidirected Edges are not allowed and
     * therefore broken within this class.
     */
    public Embedding(DiGraph dg) {
        graph=dg;
        graph.breakBiDirection();

        Collection<Edge> edges=dg.getEdges();
        for(Edge e:edges) e.data=new EdgePayload();
        for(Edge e:edges){
            EdgePayload ep=(EdgePayload)e.data;
            if(ep.left==null){
                Face p=new Face();
                createFace(e,e,p,true, true);
                for(Edge ed:p.edges){
                    EdgePayload payload=(EdgePayload)ed.data;
                    if(payload.left==null) payload.left=p;
                    else payload.right=p;
                }
                faces.add(p);
            }
            if(ep.right==null){
                Face p=new Face();
                createFace(e,e,p,true, false);
                if(ep.left.equals(p)){
                    p=new Face();
                    createFace(e,e,p,true, true);
                }
                for(Edge ed:p.edges){
                    EdgePayload payload=(EdgePayload)ed.data;
                    if(payload.left==null) payload.left=p;
                    else payload.right=p;
                }
                faces.add(p);
            }
        }

        //Determine the infinity face as the plane
        //with the most surrounding edges
        int max=0;
        for(Face p:faces){
            if(p.edges.size()>max){
                max=p.edges.size();
                infinityFace=p;
            }
        }
    }

    /**
     * Creates a new face by traversing the graph according to the
     * clockwise ordering of the edges.
     */
    private void createFace(Edge first, Edge e, Face p, boolean forward, boolean left){
        Edge next;
        int index;
        p.edges.add(e);
        //calculate next Edge
        Node n;
        if(forward) n=e.destination;
        else n=e.source;

        if(left){
            index=n.edges.indexOf(e);
            index++;
            if(index >= n.edges.size()) index=0;
            next=n.edges.get(index);
        }
        else{
            index=n.edges.indexOf(e);
            index--;
            if(index < 0) index=n.edges.size()-1;
            next=n.edges.get(index);
        }

        forward=(next.source==n);
        if(next==first) {
            Node no;
            if(forward) no=next.destination;
            else no=next.source;
            if(no.edges.size()==1) p.edges.add(next);
            return;
        }

        createFace(first,next,p,forward,left);
    }

     /**
     * Returns the graph.
     */
    public DiGraph getGraph(){
        return graph;
    }

    /**
     * Returns the faces of the embedding.
     */
    public LinkedList<Face> getFaces(){
        return faces;
    }

    /**
     * Returns the infinity face.
     */
    public Face getInfinityFace(){
        return infinityFace;
    }

    /**
     * Class representing a face
     */
    public class Face{

        //surrounding edges
        public LinkedList<Edge> edges=new LinkedList<Edge>();

        /**
         * Overriden equals
         */
        public boolean equals(Object o){
            if(!(o instanceof Face)) return false;
            Face p=(Face)o;

            if(edges.size()!=p.edges.size()) return false;

            for(Edge e:p.edges) if(!edges.contains(e)) return false;
            return true;
        }

        /**
         * Returns the nodes within the face.
         */
        public Collection<Node> getNodes(){
            LinkedList<Node> n=new LinkedList<Node>();
            for(Edge e:edges){
                n.add(e.source);
                n.add(e.destination);
            }
            return n;
        }

        /**
         * For debug reasons mainly
         */
        public String toString(){
            StringBuffer buf=new StringBuffer();
            for(Edge e: edges){
                buf.append("("+e.source.ID+","+e.destination.ID+"), ");
            }
            return buf.substring(0,buf.length()-2);
        }
    }



    /**
     * Adds left and right face to edge via payload data.
     */
    public class EdgePayload{
        public Face left=null;
        public Face right=null;
    }


    /**
     * For debug reasons mainly
     */
    public String toString(){
        StringBuffer buf=new StringBuffer();
        buf.append("\n"+graph.toString()+"\n");

        int i=0;
        for(Face p:faces){
            buf.append(i+": "+p.toString()+"\n");
            i++;
        }
        buf.append("\n");
        for(Node n:graph.getNodes()){
            buf.append(n.ID+" ");
            for(Edge e:n.edges){
                buf.append("("+e.source.ID+","+e.destination.ID+") ");
            }
            buf.append("\n");
        }
        return buf.toString();
    }

    /**
     * Adds an edge, splitting the face between the two nodes.
     */
    void addFaceEdge(Face face, Node from, Node to) {
        if(face==null || from==null || to==null) return;
        assert faces.contains(face);
        assert face.getNodes().contains(from);
        assert face.getNodes().contains(to);

        Edge lFrom=null, rFrom=null;
        //find the two edges containing from
        for(Edge e:face.edges){
            if(e.source==from || e.destination==from){
                if(lFrom==null) lFrom=e;
                else{
                    rFrom=e;
                    break;
                }
            }
        }

        //make shure lFrom has smaller index
        if(face.edges.indexOf(lFrom) > face.edges.indexOf(rFrom)){
            Edge t=rFrom;
            rFrom=lFrom;
            lFrom=t;
        }

        //find the right half of the face from "from" to "to"
        LinkedList<Edge> rightPart=new LinkedList<Edge>();
        int index=face.edges.indexOf(rFrom);
        while(true){
            Edge e=face.edges.get(index);
            rightPart.add(e);
            if(e.destination==to || e.source==to) break;
            index=(index+1)%face.edges.size();
        }

        //find the right half of the face from "from" to "to"
        int prevIndex=index;
        LinkedList<Edge> leftPart=new LinkedList<Edge>();
        index=face.edges.indexOf(lFrom);
        while(index!=prevIndex){
            Edge e=face.edges.get(index);
            leftPart.add(e);
            index--;
            if(index<0) index=face.edges.size()-1;
        }

        //Attention! Clockwise ordering is destroyed here
        Edge newedge=new Edge(from,to);
        graph.addEdge(newedge);

        leftPart.add(newedge);
        rightPart.add(newedge);

        //Split faces.
        face.edges=leftPart;
        Face newface=new Face();
        newface.edges=rightPart;
        faces.add(newface);
    }
}
