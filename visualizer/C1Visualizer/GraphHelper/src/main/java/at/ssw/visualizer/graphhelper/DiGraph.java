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

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 * Implements a Directed Graph. Some graph theoretic features like filtering
 * connected components and making a graph biconnected are supported.
 * Furthermore a planarity testing algorithm is implemented as described in:
 *
 * Kurt Mehlhorn, Petra Mutzel, Stefan Naeher: An Implementation of the Hopcroft
 * and Trajan Planarity Test and Embedding Algorithm. Technical Report, 1993.
 * MPI-I-93-151
 *
 * @author Stefan Loidl
 */
public class DiGraph {

    protected Hashtable<String, Node> nodes;
    protected LinkedList<Edge> edges;

    //This field is used within makeBiConnected Algorithm.
    private int dfsCount=0;

    //Used during planarity testing
    final static int LEFT=1;
    final static int RIGHT=2;

    //during the panarity-test step this variable
    //is assigned to be the biconnected component
    //which is used within the embedding step.
    private DiGraph BiConnected=null;

    //Used in embedding algorithm
    private int cur_nr=0;


    /** Creates a new instance of DiGraph */
    public DiGraph() {
        nodes=new Hashtable<String,Node>();
        edges=new LinkedList<Edge>();
    }

    /** Adds a new Node to the DiGraph*/
    public void addNode(Node n){
        nodes.put(n.ID,n);
    }

    /** Removes a Node from the DiGraph*/
    public void removeNode(Node n){
        for(Edge e : n.edges.toArray(new Edge[n.edges.size()]))
            removeEdge(e);

        nodes.remove(n.ID);
    }

    /** Returns a Node object with specified id*/
    public Node getNode(String id){
        return nodes.get(id);
    }

    /** Returns if a node with specified id is contained*/
    public boolean contains(String id){
        return nodes.containsKey(id);
    }

    /** Adds a new edge to the DiGraph*/
    public void addEdge(Edge e){
        e.source.succ.add(e.destination);
        e.destination.pred.add(e.source);
        edges.add(e);
        e.source.edges.add(e);
        e.destination.edges.add(e);
    }

    /** Removes an edge for the DiGraph*/
    public void removeEdge(Edge e) {
        if(e==null) return;
        edges.remove(e);
        e.source.succ.remove(e.destination);
        e.destination.pred.remove(e.source);
        e.source.edges.remove(e);
        e.destination.edges.remove(e);
    }

    /** Returns a Collection of all Nodes*/
    public Collection<Node> getNodes(){
        return nodes.values();
    }

    /** Returns a Collection of all Edges*/
    public Collection<Edge> getEdges(){
        return edges;
    }

    /** Returns the Edge object with source and destination given*/
    protected Edge getEdge(Node source, Node dest) {
        for(Edge e: edges){
            if(e.source==source && e.destination==dest) return e;
        }
        return null;
    }

    /** Resets the flags of all Nodes*/
    public void resetNodeFlags(){
        for(Node n:nodes.values()){
            n.visited=false;
        }
    }

    /**
     * Returns a clone of the current DiGraph
     */
    public DiGraph clone(){
        DiGraph c=new DiGraph();

        for(Node n: nodes.values()) c.addNode(new Node(n.ID));

        for(Edge e: edges)
            c.addEdge(new Edge(c.getNode(e.source.ID),c.getNode(e.destination.ID)));

        return c;
    }

    /**
     * Makes the Graph bidirected by adding Edges
     */
    public void makeBiDirected(){
       for(Node n: nodes.values()){
           for(Node s:n.succ){
               if(!n.pred.contains(s)) addEdge(new Edge(s,n));
           }
           for(Node p:n.pred){
               if(!n.succ.contains(p)) addEdge(new Edge(n,p));
           }
       }
    }

    /**
     * Breaks all bidirected Edges by deleting one of
     * them.
     */
    public void breakBiDirection(){
        LinkedList<Edge> del=new LinkedList<Edge>();
        for(Node n: nodes.values()){
            for(Node p:n.pred){
                if(n.succ.contains(p)){
                    Edge ed=getEdge(n,p);
                    if(!del.contains(ed))del.add(getEdge(p,n));
                }
            }
        }
        for(Edge e:del){
            removeEdge(e);
        }
    }

    /**
     * Returns a list of  DiGraphs containing the connected components of
     * the current DiGraph- Nodes are cloned!
     */
    public Collection<DiGraph> getConnectedComponents(){
        LinkedList<DiGraph> list=new LinkedList<DiGraph>();
        resetNodeFlags();
        for(Node n:nodes.values()){
            if(!n.visited){
                list.add(findConnctedComponent(n));
            }
        }

        return list;
    }

        /**
     * Extracts the connected component from Node n.
     */
    private DiGraph findConnctedComponent(Node n) {
        DiGraph dg=new DiGraph();
        findConnctedComponent_rek(n,dg);

        for(Edge e: getEdges()){
            if(dg.contains(e.source.ID)){
                dg.addEdge(new Edge(dg.getNode(e.source.ID),dg.getNode(e.destination.ID)));
            }
        }

        return dg;
    }

    private void findConnctedComponent_rek(Node n, DiGraph dg) {
        if(n.visited) return;
        n.visited=true;

        dg.addNode(new Node(n.ID));

        for(Node node:n.succ){
            findConnctedComponent_rek(node,dg);
        }
        for(Node node:n.pred){
            findConnctedComponent_rek(node,dg);
        }
    }

    /**
     * Returns a collecation of arrays of circualar Nodes
     */
    public Collection<Node[]> getCircularDependency(Node n){
        LinkedList<Node[]> ret=new LinkedList<Node[]>();
        LinkedList<Node> current=new LinkedList<Node>();

        resetNodeFlags();

        n.visited=true;
        current.add(n);
        for(Node s:n.succ){
            getCircularDependency_rec(n ,s, current, ret);
        }


        return ret;
    }

    /**
     * Recusive helper- searching cyclic structures.
     */
    private void getCircularDependency_rec(Node source ,Node node, LinkedList<Node> current, LinkedList<Node[]> result){
        if(node.visited) return;
        node.visited=true;

        current.addLast(node);

        for(Node n:node.succ){
            //Cycle found!
            if(n==source){
                result.add(current.toArray(new Node[current.size()]));
            }
            //Another unvisited node
            else if(!n.visited){
                getCircularDependency_rec(source,n,current,result);
            }
        }

        node.visited=false;
        current.removeLast();
    }


    /**
     * This method makes the current graph biconnected by adding
     * edges. It is assumed that the graph is connected & bidirected!
     * The data field of all nodes is destroyed!
     */
    public void makeBiConnected(){
        //distribute payload to all nodes
        for(Node n: nodes.values()) n.data=new BiConPayload();

        dfsCount=0;

        if(nodes.size()>0)
            dfsInMakeBiConnected(nodes.values().iterator().next());

    }

    /**
     * This method searches for nodes connecting two subgraphs.
     * To satisfy BiConnection another Edge is added to bridge that
     * single node. This doesn't influence planarity of the graph.
     */
    private void dfsInMakeBiConnected(Node n) {
        Node u;
        BiConPayload pl=(BiConPayload)n.data;

        pl.dfsNum=dfsCount++;
        pl.lowPt=pl.dfsNum;
        pl.reached=true;

        if(n.succ.size()==0) return;

        //get first child
        u=n.succ.getFirst();

        //Within the loop changes may happen -> successors.
        //these do not have to be traversed.
        Node[] na=n.succ.toArray(new Node[n.succ.size()]);

        //traverse all children
        for(Node w: na){
            BiConPayload plw=(BiConPayload)w.data;

            //Edge (n->w) is a tree edge
            if(!plw.reached){
                plw.parent=n;
                dfsInMakeBiConnected(w);

                //Node was found -> a bridging edge has to
                //be added.
                if(plw.lowPt==pl.dfsNum){
                    //if w is the first child and n has a parent
                    //we simply add a edge between them (bidirected!)
                    //This may not be done between other than the first
                    //Node because in the case planerity would be lost! (no face!)
                    if(w==u && pl.parent!=null){
                        Edge e=new Edge(w,pl.parent);
                        addEdge(e);
                        addEdge(e.getReverseEdge());
                    }
                    //else we simply add a bidirected Edge between the first child and the
                    //current child
                    if(u!=w){
                        Edge e=new Edge(w,u);
                        addEdge(e);
                        addEdge(e.getReverseEdge());
                    }
                }

                //lowPt of our node n is minimum of lowPts
                if(pl.lowPt > plw.lowPt) pl.lowPt=plw.lowPt;
            }
            //Edge (n->w) is not a tree edge
            else{
                if(pl.lowPt > plw.dfsNum) pl.lowPt=plw.dfsNum;
            }
        } //for
    }



    /** Overrides the toString for debug reasons only.*/
    public String toString(){
        String ret="Nodes: ";
        for(Node n:nodes.values()){
            ret+=n.ID;
            if(n.data!=null) ret+="("+n.data+")";
            ret+=", ";
        }

        ret+="\nEdges: ";
        for(Edge e: edges)
            ret+="("+e.source.ID+"->"+e.destination.ID+") ";

        return ret;
    }

    /** Tests if the current graph is planar*/
    public boolean isPlanar(){
        boolean ret=true;
        for(DiGraph dg : getConnectedComponents()){
            ret= ret && dg.planar();
        }
        return ret;
    }

    /**
     * Tests for the planarity of the graph destroying its structure!
     * The graph is assumed to be connected.
     */
    protected boolean planar(){
        int num=nodes.size();
        if(num <= 3) return true;
        if(edges.size() > 6*num-12) return false;

        makeBiDirected();
        makeBiConnected();
        BiConnected=clone();

        //Planarity Test
        for(Node n: nodes.values()) n.data=new PlanarityNodePayload();
        for(Edge e: edges) e.data=new PlanarityEdgePayload();

        reorder();
        LinkedList<Integer> Att=new LinkedList<Integer>();

        if(nodes.size()==0) return true;
        Node first=nodes.values().iterator().next();

        assert first.succ.size()>0;
        Edge firstEdge=getEdge(first,first.succ.getFirst());

        ((PlanarityEdgePayload)firstEdge.data).alpha=LEFT;

        if(!stronglyPlanar(firstEdge,Att)) return false;

        return true;
    }


    private boolean stronglyPlanar(Edge e0,LinkedList<Integer> Att){
        //determine the cycle C(e0)
        Node x=e0.source;
        Node y=e0.destination;
        //get first adiacent edge to y
        Edge e= getEdge(y,y.succ.getFirst());

        Node wk=y;

        //while is a tree edge
        while(((PlanarityNodePayload)e.destination.data).dfsNum > ((PlanarityNodePayload)wk.data).dfsNum){
            wk=e.destination;
            e=getEdge(wk,wk.succ.getFirst());
        }

        Node w0=e.destination;

        //Process all edges leaving the spine of S(e0)
        Node w=wk;
        LinkedList<Block> S=new LinkedList<Block>();

        while(w!=x){
            int count=0;
            for(Node r:w.succ){
                e=getEdge(w,r);
                count++;
                if(count!=1){
                    //Test Recursivly
                    LinkedList<Integer> A=new LinkedList<Integer>();
                    //if: tree edge
                    if(((PlanarityNodePayload)w.data).dfsNum < ((PlanarityNodePayload)e.destination.data).dfsNum){
                        if(!stronglyPlanar(e,A)) return false;
                    }
                    //else: back edge
                    else A.add(new Integer(((PlanarityNodePayload)e.destination.data).dfsNum));

                    //update stack S
                    Block B=new Block(e,A);
                    while(true){
                        if(B.leftInterlace(S)) S.getFirst().flip();
                        if(B.leftInterlace(S)) return false;
                        if(B.rightInterlace(S)) B.combine(S.poll());
                        else break;
                    }
                    S.addFirst(B);
                }
            }

            //Prepare for next iteration
            while(!S.isEmpty() && S.getFirst().clean(((PlanarityNodePayload)((PlanarityNodePayload)w.data).parent.data).dfsNum))
                S.removeFirst();

            w=((PlanarityNodePayload)w.data).parent;
        }


        //test strong planerity and compute Att
        Att.clear();
        while(!S.isEmpty()){
            Block B=S.poll();
            if(!B.emptyLatt() && !B.emptyRatt() && B.headOfLatt().intValue() > ((PlanarityNodePayload)w0.data).dfsNum
                    && B.headOfRatt().intValue() > ((PlanarityNodePayload)w0.data).dfsNum){
                return false;
            }

            B.addToAtt(Att,((PlanarityNodePayload)w0.data).dfsNum);
        }

        //w0 is an attachment of S(e0) except if w0 = x
        if(w0 != x) Att.add(new Integer(((PlanarityNodePayload)w0.data).dfsNum));

        return true;
    }


    /**
     * This is a substep in planarity test algorithm- All data fields of
     * nodes and edges are lost during this step!
     */
    private void reorder(){
        if(nodes.size()==0) return;

        LinkedList<Edge> deledges=new LinkedList<Edge>();
        dfsCount=0;

        dfsInReorder(nodes.values().iterator().next(),deledges);

        for(Edge e: deledges) removeEdge(e);

        for(Edge e: edges){
            PlanarityNodePayload source=(PlanarityNodePayload)e.source.data;
            PlanarityNodePayload dest=(PlanarityNodePayload)e.destination.data;

            PlanarityEdgePayload epl=(PlanarityEdgePayload)e.data;

            if(dest.dfsNum < source.dfsNum){
                epl.cost=2*dest.dfsNum;
            } else{
                if(dest.lowpt2 >= source.dfsNum) epl.cost=2*dest.lowpt1;
                else epl.cost=2*dest.lowpt1+1;
            }
        }

        sortEdges();
    }

    private void dfsInReorder(Node v, LinkedList<Edge> deledges) {
        PlanarityNodePayload vpl=(PlanarityNodePayload)v.data;

        vpl.dfsNum=dfsCount++;
        vpl.lowpt1=vpl.lowpt2=vpl.dfsNum;
        vpl.reached=true;

        //First pass
        for(Node w: v.succ){
            PlanarityNodePayload wpl=(PlanarityNodePayload)w.data;

            //The edge (v -> w) is a tree edge
            if(!wpl.reached){
                wpl.parent=v;
                dfsInReorder(w,deledges);
                vpl.lowpt1=Min(wpl.lowpt1,vpl.lowpt1);
            }
            else{
                vpl.lowpt1=Min(wpl.dfsNum,vpl.lowpt1);

                //the edge (v -> w) is a forward edge or the reversal of a tree edge.
                if((wpl.dfsNum >= vpl.dfsNum) || w==vpl.parent)
                    deledges.add(getEdge(v,w));
            }
        }


        //Second pass
        for(Node w: v.succ){
            PlanarityNodePayload wpl=(PlanarityNodePayload)w.data;

            //tree edge (assigned during first pass)
            if(wpl.parent==v){
                if(wpl.lowpt1!=vpl.lowpt1) vpl.lowpt2=Min(wpl.lowpt1,vpl.lowpt2);
                vpl.lowpt2=Min(vpl.lowpt2, wpl.lowpt2);
            }
            else{
                if(vpl.lowpt1 != wpl.dfsNum) vpl.lowpt2=Min(vpl.lowpt2, wpl.dfsNum);
            }
        }

    }

    /** Simple helper function returning the minimum*/
    private int Min(int a, int b) {
        if(a>b) return b;
        else return a;
    }

    /**
     * Used in reordering process of the planarity test. Edges are sorted
     * according to their cost value.
     */
    private void sortEdges() {
        Edge[] e=edges.toArray(new Edge[edges.size()]);

        Arrays.sort(e,new Comparator<Edge>(){
            public int compare(Edge e1, Edge e2) {
                if(e1==null || e2==null ||
                   e1.data==null || e2.data==null||
                   !(e1.data instanceof PlanarityEdgePayload) ||
                   !(e2.data instanceof PlanarityEdgePayload) ) return 0;

                return ((PlanarityEdgePayload)e1.data).cost- ((PlanarityEdgePayload)e2.data).cost;
            }
        });

        //Delete all edges
        for(Edge edge:e){
            removeEdge(edge);
        }

        //Insert all edges is correct order
        for(Edge edge:e){
            addEdge(edge);
        }
    }

    /**
     * Assuming that the Graph is connected the method
     * tests the planarity of the graph and constructs an
     * embedding if it is planar.
     */
    public Embedding createEmbedding(){

        DiGraph G=clone();
        //Planar- algorithm makes shure the correct
        //payload types are in the nodes.
        if(G.planar()){
            if(G.nodes.size()<4) return new Embedding(clone());

            DiGraph H=G.BiConnected;
            for(Edge e :H.edges) e.data=new EmbeddingEdgePayload();
            //Lists of Edges of H
            LinkedList<Edge> T=new LinkedList<Edge>();
            LinkedList<Edge> A=new LinkedList<Edge>();

            cur_nr=0;

            Node first=G.nodes.values().iterator().next();
            assert first.succ.size()>0;
            Edge firstEdge=G.getEdge(first,first.succ.getFirst());



            G.resetNodeFlags();

            embedding(firstEdge,G,H,LEFT,T,A);

            //conc R and A
            T.addAll(A);

            for(Edge e:T) ((EmbeddingEdgePayload)e.data).sortNum=cur_nr++;

            //PlanarityPayload is used because cost can be used for
            //sorting purposes.
            for(Edge e:edges) {
                PlanarityEdgePayload pl=new PlanarityEdgePayload();
                pl.cost=((EmbeddingEdgePayload)H.getEdge(H.getNode(e.source.ID),H.getNode(e.destination.ID)).data).sortNum;
                e.data=pl;
            }

            sortEdges();

            return new Embedding(clone());
        }
        return null;
    }

    private void embedding(Edge e0, DiGraph G,DiGraph H, int t, LinkedList<Edge> T, LinkedList<Edge> A) {
        //Determine Cycle C(e0)
        Node x=e0.source;
        Node y=e0.destination;

        ((PlanarityNodePayload)y.data).treeEdgeInto=e0;
        Edge e1;
        //first adjacent edge
        e1=G.getEdge(y,y.succ.getFirst());
        Node wk=y;

        //e is a tree edge?
        while(((PlanarityNodePayload)e1.destination.data).dfsNum > ((PlanarityNodePayload)wk.data).dfsNum){
            wk=e1.destination;

            ((PlanarityNodePayload)wk.data).treeEdgeInto=e1;
            e1=G.getEdge(wk,wk.succ.getFirst());
        }

        Node w0=e1.destination;
        Edge back_edge_into_w0=e1;

        //Process the subsegments
        Node w=wk;
        LinkedList<Edge> Al=new LinkedList<Edge>();
        LinkedList<Edge> Ar=new LinkedList<Edge>();
        LinkedList<Edge> Tprime=new LinkedList<Edge>();
        LinkedList<Edge> Aprime=new LinkedList<Edge>();

        T.clear();
        T.add(H.getEdge(H.getNode(e1.source.ID),H.getNode(e1.destination.ID)));
        while(w!=x){
            int count=0;
            for(Node n:w.succ){

                Edge e=G.getEdge(w,n);
                count++;
                if(count!=1){
                    //Embed recursivly
                    //tree edge
                    if(((PlanarityNodePayload)w.data).dfsNum < ((PlanarityNodePayload)e.destination.data).dfsNum){
                        int tprime=(t==((PlanarityEdgePayload)e.data).alpha) ? LEFT : RIGHT;
                        embedding(e,G, H,tprime,Tprime,Aprime);
                    }
                    else{
                        Tprime.add(H.getEdge(H.getNode(e.source.ID),H.getNode(e.destination.ID)));
                        Aprime.add(H.getEdge(H.getNode(e.destination.ID),H.getNode(e.source.ID)));
                    }
                    //update lists T, Al and Ar
                    if(t==((PlanarityEdgePayload)e.data).alpha){
                        Tprime.addAll(T);
                        T.clear();
                        T.addAll(Tprime);   //T= Tprime conc T
                        Tprime.clear();
                        Al.addAll(Aprime);  //Al= Al conc Aprime
                        Aprime.clear();
                    }
                    else{
                        T.addAll(Tprime);   //T= T conc Tprime
                        Tprime.clear();
                        Aprime.addAll(Ar);
                        Ar.clear();
                        Ar.addAll(Aprime);  //Ar= Aprime conc Ar
                        Aprime.clear();
                    }
                }
            } //for
            //Compute w's adjacency list and prepare for next iteration
            Edge ne=((PlanarityNodePayload)w.data).treeEdgeInto;
            T.add(H.getEdge(H.getNode(ne.destination.ID),H.getNode(ne.source.ID)));

            for(Edge e: T) ((EmbeddingEdgePayload)e.data).sortNum=cur_nr++;
            T.clear();
            while(!Al.isEmpty() && Al.getLast().source ==H.getNode(((PlanarityNodePayload)w.data).parent.ID)){
                T.addFirst(Al.removeLast());
            }
            ne=((PlanarityNodePayload)w.data).treeEdgeInto;
            T.add(H.getEdge(H.getNode(ne.source.ID),H.getNode(ne.destination.ID)));
            while(!Ar.isEmpty() && Ar.getFirst().source == H.getNode(((PlanarityNodePayload)w.data).parent.ID)){
                T.add(Ar.removeFirst());
            }

            w=((PlanarityNodePayload)w.data).parent;
        }//while

        //Prepare the output
        A.clear();
        A.addAll(Ar);
        Ar.clear();
        A.add(H.getEdge(H.getNode(back_edge_into_w0.destination.ID),H.getNode(back_edge_into_w0.source.ID)));
        A.addAll(Al);
        Al.clear();
    }

    /**
     * Reverses the edge e
     */
    public boolean reverseEdge(Edge e){
        if(edges.contains(e)){
            Node source=e.source;
            Node dest=e.destination;
            source.succ.remove(dest);
            dest.pred.remove(source);
            source.pred.add(dest);
            dest.succ.add(source);
            e.reverseEdge();
            return true;
        }
        return false;
    }


   /**
    * This class encapsulates the payload a node
    * has to carry turing the planarization step.
    */
    public class PlanarityNodePayload{
        public int dfsNum=0;
        public Node parent=null;
        public boolean reached=false;
        public int lowpt1=0;
        public int lowpt2=0;
        //Embedding vars
        public Edge treeEdgeInto=null;
    }

    /**
    * This class encapsulates the payload a edge
    * has to carry turing the planarization step.
    */
    public class PlanarityEdgePayload{
        public int alpha=0;
        public int cost=0;
    }

    /**
    * This class encapsulates the payload a edge
    * has to carry turing the planarisation step.
    */
    public class EmbeddingEdgePayload{
        public int sortNum=0;
    }


    /**
     * This class encapsulates the payloade a node
     * has to carry within the algotithm for making it
     * biconnectional
     */
    public class BiConPayload{
        public int dfsNum=0;
        public int lowPt=0;
        public boolean reached=false;
        public Node parent=null;
    }

}
