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

import at.ssw.positionmanager.Cluster;
import at.ssw.positionmanager.LayoutGraph;
import at.ssw.positionmanager.Link;
import at.ssw.positionmanager.Port;
import at.ssw.positionmanager.Vertex;
import at.ssw.positionmanager.export.GMLFileExport;
import at.ssw.positionmanager.impl.GraphCluster;
import at.ssw.positionmanager.impl.GraphLink;
import at.ssw.positionmanager.impl.GraphPort;
import at.ssw.positionmanager.impl.GraphVertex;
import at.ssw.visualizer.dataflow.attributes.ExpandNodeSwitchAttribute;
import at.ssw.visualizer.dataflow.attributes.ExpandStructureAttribute;
import at.ssw.visualizer.dataflow.attributes.ISwitchAttribute;
import at.ssw.visualizer.dataflow.attributes.InvisibleAttribute;
import at.ssw.visualizer.dataflow.attributes.SelfSwitchingExpandAttribute;
import at.ssw.visualizer.dataflow.instructions.Instruction;
import at.ssw.dataflow.layout.ExternalGraphLayouter;
import at.ssw.visualizer.graphhelper.DiGraph;
import at.ssw.visualizer.graphhelper.Edge;
import at.ssw.visualizer.graphhelper.Node;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JPopupMenu;
import javax.swing.JViewport;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.EditProvider;
import org.netbeans.api.visual.action.MoveProvider;
import org.netbeans.api.visual.action.MoveStrategy;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.RectangularSelectDecorator;
import org.netbeans.api.visual.action.RectangularSelectProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.anchor.Anchor;
import org.netbeans.api.visual.anchor.AnchorFactory;
import org.netbeans.api.visual.anchor.AnchorShape;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.graph.GraphScene;
import org.netbeans.api.visual.graph.layout.TreeGraphLayout;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.router.Router;
import org.netbeans.api.visual.router.RouterFactory;
import org.netbeans.api.visual.widget.ConnectionWidget;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.LayerWidget;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Stefan Loidl
 */
public class InstructionNodeGraphScene extends GraphScene<Instruction, String> implements   RectangularSelectDecorator,
                                                                                            RectangularSelectProvider,
                                                                                            MoveStrategy,
                                                                                            EditProvider, SelectProvider{

    //scene layers
    private Widget mainLayer;
    private Widget connectionLayer;
    private Widget selectLayer;
    private Widget clusterLayer;

    //Nodeclusters- not always used- layouter dependent
    private Collection<ClusterWidget> clusters=null;
    //nodes- saperated to achive efficiency
    private Hashtable<String,InstructionNodeWidget> nodewidgets;

    //Menu item strings
    private String RELEASEINFTREE="Collapse Influence Tree";
    private String RELEASEPARENTINF="Collapse Parent Influence";
    private String RELEASECYCLES="Collapse Cycles";
    private String RELEASESINGLECYCLE="Collapse Single Cycle";
    private String RELEASENODE="Collapse Node";

    private WidgetAction moveAction = ActionFactory.createMoveAction (this, new MultiWidgetMovementProvider());
    private WidgetAction selectAction= ActionFactory.createSelectAction(this);

    private ExternalGraphLayouter layouter=null;

    private LinkedList<InstructionSceneListener> listeners;

    //Defines if links between clusters are grayed
    private boolean interCluserLinkGray=true;

    //Used to determine planarity, cycles...
    private DiGraph calculationModel;

    //Is layouting done automaticly?
    private boolean autoLayout=true;

    //Are invisible Nodes layouted?
    private boolean layoutInvisibleNodes=false;

    //Is highlight clustering used?
    private boolean highlightClustering=false;

    //Are Clusterboarders visible
    private boolean clusterBordersVisible=true;

    //Are Location animations used
    private boolean locationAnimation=true;


    //Default Block/Cluster names
    private final static String HIGHLIGHTBLOCK="HIGHLIGHT";
    private final static String DEFAULTBLOCK="NULL";

    //Are current node positions used by layouters to build on?
    private boolean USECURRENTNODEPOSITIONS=false;

    private Router linkRouter= new at.ssw.visualizer.dataflow.graph.DirectLineRouter(this);


    /**
     * Constructor
     */
    public InstructionNodeGraphScene () {

        clusterLayer=new LayerWidget(this);
        addChild(clusterLayer);
        mainLayer = new LayerWidget (this);
        addChild (mainLayer);
        connectionLayer = new Widget (this);
        addChild (connectionLayer);
        selectLayer=new LayerWidget(this);
        addChild(selectLayer);

        getInputBindings().setZoomActionModifiers(0);
        getActions().addAction(ActionFactory.createMouseCenteredZoomAction(1.2));
        getActions().addAction(ActionFactory.createPanAction());
        getActions().addAction(ActionFactory.createRectangularSelectAction(this, (LayerWidget) selectLayer,this));

        nodewidgets=new Hashtable<String,InstructionNodeWidget>();
        listeners=new LinkedList<InstructionSceneListener>();
    }

    /**
     * Adds an array of instructions to the Scene. This is the only way nodes should
     * be added to this type of scene.
     */
    public void addInstructions(Instruction[] instructions){
        calculationModel=new DiGraph();

        //Add nodes to scene
        for(Instruction inst : instructions){
            addNode(inst);

            calculationModel.addNode(new Node(inst.getID()));
        }

        //Add edges to scene
        for(Instruction inst : instructions){
            for(Instruction x: inst.getSuccessors()){
                //in some cases it may happen that a phi expression
                //uses the same instruction more than once
                //edges would be duplicated. This if prevents this
                if(!getEdges().contains(inst.getID()+x.getID())){
                    addEdge(inst.getID()+x.getID());
                    setEdgeSource(inst.getID()+x.getID(),inst);
                    setEdgeTarget(inst.getID()+x.getID(),x);


                    calculationModel.addEdge(new Edge(calculationModel.getNode(inst.getID()),
                            calculationModel.getNode(x.getID())));
                }
            }
        }

        //Add cycle information to each node
        for(InstructionNodeWidget nw: getNodeWidgets()){
            Node n=calculationModel.getNode(nw.getID());
            if(n!=null){
                Collection<Node[]> cycles=calculationModel.getCircularDependency(n);
                LinkedList<InstructionNodeWidget[]> intc=new LinkedList<InstructionNodeWidget[]>();
                InstructionNodeWidget[] wid;

                for(Node[] nl:cycles){
                    wid=new InstructionNodeWidget[nl.length];
                    for(int i=0;i < nl.length;i++){
                        wid[i]=getNodeWidget(nl[i].ID);
                    }
                    intc.add(wid);
                }

                nw.setCycles(intc);
            }
        }
    }


    /**
     * Refreshes all node & cluster widget data
     */
    public void refreshAll(){
        Enumeration<InstructionNodeWidget> enu=nodewidgets.elements();

        while(enu.hasMoreElements()) {
            InstructionNodeWidget w=enu.nextElement();
            w.refresh();
        }
        for(Widget w: connectionLayer.getChildren()){
            w.revalidate(true);
        }

        refreshClusterWidgets();
        refreshLayoutVertex();
    }


    /**
     * Updates the data of the layoutgraph verticles.
     */
    protected void refreshLayoutVertex(){
        if(VertexToWidget!=null){
            for(Map.Entry<Vertex, InstructionNodeWidget> e:VertexToWidget.entrySet()){
                if(e.getKey() instanceof GraphVertex){
                    GraphVertex v=(GraphVertex)e.getKey();
                    v.setDirty(true);
                }
            }
        }

    }

    /**
     * Refreshes the size calculation of the cluster widgets.
     */
    protected void refreshClusterWidgets(){
        if(clusters!=null){
            for(ClusterWidget c:clusters){
                c.refresh();
            }
        }
    }

     /**
     * Centers the view at point p.
     */
    public void centerOn(Point p){
        Object o=getView().getParent();

        if(o instanceof JViewport){
            JViewport v=(JViewport)o;
            Point upperLeft=convertViewToScene(v.getViewPosition());
            Point lowerRight=v.getViewPosition();
            lowerRight.translate(v.getExtentSize().width,v.getExtentSize().height);
            lowerRight=convertViewToScene(lowerRight);

            int x=p.x-(lowerRight.x-upperLeft.x)/2;
            int y=p.y-(lowerRight.y-upperLeft.y)/2;

            Point n=convertSceneToView(new Point(x,y));

            int maxX=v.getViewSize().width-v.getExtentSize().width;
            int maxY=v.getViewSize().height-v.getExtentSize().height;

            if(n.x < 0) n.x=0;
            if(n.x > maxX) n.x=maxX;
            if(n.y < 0) n.y=0;
            if(n.y > maxY) n.y=maxY;


            v.setViewPosition(n);
        }
    }


    /**
     * Edit provider action- This method is called if a node is
     * double clicked.
     */
    public void edit(Widget widget) {
        if(widget instanceof InstructionNodeWidget){
            fireDoubleClicked((InstructionNodeWidget)widget);
        }
        if(widget instanceof ClusterWidget){
            try{
                int x=widget.getPreferredLocation().x+widget.getPreferredBounds().width/2;
                int y=widget.getPreferredLocation().y+widget.getPreferredBounds().height/2;
                centerOn(new Point(x,y));

                HashSet<Instruction> set=new HashSet<Instruction>();
                for(Widget w:((ClusterWidget)widget).getWidgets()){
                    if(w instanceof InstructionNodeWidget){
                        set.add(((InstructionNodeWidget)w).getInstruction());
                    }
                }

                setSelectedObjects(set);
                fireSelectionChanged();
                refreshAll();
                validate();
            }catch(Exception e){
            }
        }
    }

    /**
     * Exports the current layout graph into the GML file format.
     */
    public boolean exportToGMLFile(File f){
        Hashtable<Vertex,String> names=new Hashtable<Vertex,String>();
        Hashtable<Vertex,Color> colors=new Hashtable<Vertex,Color>();

        for(Vertex v:layoutGraph.getVertices()){
            InstructionNodeWidget w=VertexToWidget.get(v);
            if(w!=null){
                names.put(v,VertexToWidget.get(v).getID());
                colors.put(v,InstructionNodeWidget.getColorByType(w.getInstruction().getInstructionType(),false));
            }
        }
        GMLFileExport exp=new GMLFileExport(f,names,colors);
        return exp.export(layoutGraph);
    }


    //<editor-fold defaultstate="collapsed" desc=" Expansion & Visibility handling ">
    /**
     * This method expands the subtree with w as the root. Expansion is done
     * via the internal attribute system. If forward is true the successors
     * are used... if not the tree is traversed backward via Predecessors.
     */
    protected void handleExpandInfluenceTree(Instruction i, boolean forward){
        String desc="("+i.getID()+")";

        if(forward) desc=RELEASEINFTREE+desc;
        else desc=RELEASEPARENTINF+desc;


        ExpandNodeSwitchAttribute en= new ExpandNodeSwitchAttribute(true,desc,true, this);

        LinkedList<Instruction> list=new LinkedList<Instruction>();

        getNodeWidget(i.getID()).addNodeAttribute(en);

        list.add(i);

        if(forward){
            for(Instruction inst: i.getSuccessors()){
                rek_ExpandInfluenceTree(inst,en,list, forward);
            }
        } else{
            for(Instruction inst: i.getPredecessors()){
                rek_ExpandInfluenceTree(inst,en,list, forward);
            }
        }

        refreshAll();
        validate();
        autoLayout();
    }

    private void rek_ExpandInfluenceTree(Instruction i, ISwitchAttribute a,LinkedList<Instruction> list, boolean forward){
        if(list.contains(i)) return;
        list.add(i);

        ExpandStructureAttribute es=new ExpandStructureAttribute(a,this);

        getNodeWidget(i.getID()).addNodeAttribute(es);


        if(forward){
            for(Instruction inst: i.getSuccessors()){
                rek_ExpandInfluenceTree(inst,a,list, forward);
            }
        } else{
            for(Instruction inst: i.getPredecessors()){
                rek_ExpandInfluenceTree(inst,a,list, forward);
            }
        }
    }

    /**
     * This Method expands a single Node
     */
    protected void handleExpandNode(Instruction instruction) {
        Set<Instruction> selected=(Set<Instruction>)getSelectedObjects();

        //Prepare for multiselected usage
        if(selected == null || selected.size()==0 || !selected.contains(instruction)){
            selected=new HashSet<Instruction>();
            selected.add(instruction);
        }

        for(Instruction i: selected){
            InstructionNodeWidget n=getNodeWidget(i.getID());
            SelfSwitchingExpandAttribute en=new SelfSwitchingExpandAttribute(RELEASENODE+"("+i.getID()+")", this);
            n.addNodeAttribute(en);
        }

        refreshAll();
        validate();
        autoLayout();
    }


    /**
     * Add Expand attributes to the nodes making up cycles the the
     * Node with the given id
     */
    void handleExpandCycles(String id) {
        InstructionNodeWidget nw=nodewidgets.get(id);

        if(nw!=null){
            String desc=RELEASECYCLES+" ("+id+")";
            ExpandNodeSwitchAttribute en= new ExpandNodeSwitchAttribute(true,desc,true, this);
            nw.addNodeAttribute(en);


            Collection<InstructionNodeWidget[]> c=nw.getCycleWidgets();
            LinkedList<InstructionNodeWidget> handled=new LinkedList<InstructionNodeWidget>();
            handled.add(nw);

            for(InstructionNodeWidget[] a:c){
                for(InstructionNodeWidget w: a){
                    if(!handled.contains(w)){
                        handled.add(w);
                        ExpandStructureAttribute es=new ExpandStructureAttribute(en,this);
                        w.addNodeAttribute(es);
                    }
                }
            }
        }

        refreshAll();
        validate();
        autoLayout();
    }

     /**
     * This Method expands the Cycle with given index of the node.
     */
    public void handleExpandCycle(String node, int cycleIndex){
        InstructionNodeWidget nw=nodewidgets.get(node);

        if(nw!=null){
            String desc=RELEASESINGLECYCLE+" ("+node+"/"+cycleIndex+")";
            ExpandNodeSwitchAttribute en= new ExpandNodeSwitchAttribute(true,desc,true, this);
            nw.addNodeAttribute(en);

            LinkedList<InstructionNodeWidget> handled=new LinkedList<InstructionNodeWidget>();
            handled.add(nw);

            int i=0;
            for(InstructionNodeWidget[] a:nw.getCycleWidgets()){
                if(i==cycleIndex){
                    for(InstructionNodeWidget w: a){
                        if(!handled.contains(w)){
                            handled.add(w);
                            ExpandStructureAttribute es=new ExpandStructureAttribute(en,this);
                            w.addNodeAttribute(es);
                        }
                    }
                    break;
                }
                i++;
            }
        }
        refreshAll();
        validate();
        autoLayout();
    }

    /**
     * Hides all nodes but the current one (or all selected if so)
     */
    public void handleHideAllBut(Instruction instruction) {
        Set<Instruction> selected=(Set<Instruction>)getSelectedObjects();
        boolean deselect=false;

        //Prepare for multiselected usage
        if(selected.size()==0){
            selected=new HashSet<Instruction>();
            selected.add(instruction);
        } else if(!selected.contains(instruction)){
            selected=new HashSet<Instruction>();
            selected.add(instruction);
        }else{
            deselect=true;
        }

        for(InstructionNodeWidget w:getNodeWidgets()){
            if(!(selected!=null && selected.contains(w.getInstruction()))){
                if(w.isWidgetVisible()) w.addNodeAttribute(new InvisibleAttribute());
            }
        }

        if(deselect){
            //deselect all!
            setSelectedObjects(new HashSet<Instruction>());
            fireSelectionChanged();
        }

        refreshAll();
        validate();
        autoLayout();
    }

    /**
     * Handles the toggle visibility event. If includeSelection==ture selected
     * widgets are included if they contain inst.
     */
    public void handleToggleVisibility(Instruction inst, boolean includeSelection){
        Set<Instruction> selected=null;
        if(includeSelection){
            selected=(Set<Instruction>)getSelectedObjects();

            //Prepare for multiselected usage
            if(selected.size()==0){
                selected=new HashSet<Instruction>();
                selected.add(inst);
            } else if(!selected.contains(inst)){
                selected=new HashSet<Instruction>();
                selected.add(inst);
            }
        }else{
            selected=new HashSet<Instruction>();
            selected.add(inst);
        }

        //If nodes get visible- selection has to be canceled
        boolean unselect= false;
        for(Instruction i:selected){
            InstructionNodeWidget nw=getNodeWidget(i.getID());
            if(nw.isWidgetVisible()) {
                nw.addNodeAttribute(new InvisibleAttribute());
                unselect=true;
            }
            else {
                nw.makeVisible();
                createCurrentLayoutGraph();
            }
        }

        if(unselect){
            selected=new HashSet<Instruction>();
            setSelectedObjects(selected);
            fireSelectionChanged();
        }

        refreshAll();
        validate();
        autoLayout();
    }


    /**
     * Makes the influence tree (forward==true) or the parent influence tree (==false)
     * visible.
     */
    public void handleMakeTreeVisible(Instruction inst, boolean forward){
        Set<Instruction> selected=(Set<Instruction>)getSelectedObjects();
        LinkedList<Instruction> handled= new LinkedList<Instruction>();

        //Prepare for multiselected usage
        if(selected.size()==0){
            selected=new HashSet<Instruction>();
            selected.add(inst);
        }
        else if(!selected.contains(inst)){
            selected=new HashSet<Instruction>();
            selected.add(inst);
        }

        for(Instruction i: selected){
            InstructionNodeWidget nw=getNodeWidget(i.getID());
            nw.makeVisible();
            handled.add(i);
            if(forward) for(Instruction x: i.getSuccessors()) recHandleMakeTreeVisible(x,forward,handled);
            else for(Instruction x: i.getPredecessors()) recHandleMakeTreeVisible(x,forward,handled);
        }
        createCurrentLayoutGraph();
        refreshAll();
        validate();
        autoLayout();
    }

    private void recHandleMakeTreeVisible(Instruction inst, boolean forward, LinkedList<Instruction> handled){
        if(handled.contains(inst)) return;
        handled.add(inst);
        InstructionNodeWidget nw=getNodeWidget(inst.getID());
        nw.makeVisible();
        //set vertex dirty
        if(WidgetToVertex!=null) {
            Vertex v=WidgetToVertex.get(nw);
            if(v!=null && v instanceof GraphVertex) ((GraphVertex)v).setDirty(true);
        }
        if(forward) for(Instruction x: inst.getSuccessors()) recHandleMakeTreeVisible(x,forward, handled);
        else for(Instruction x: inst.getPredecessors()) recHandleMakeTreeVisible(x,forward, handled);
    }

    /**
     * Makes the Cycle Widgets visible- if cycleIndex==-1 then all cycles of the node
     * are expanded.
     */
    public void handleMakeCycleVisible(String node, int cycleIndex){
        InstructionNodeWidget nw=nodewidgets.get(node);

        if(nw!=null){
            nw.makeVisible();

            int i=0;
            for(InstructionNodeWidget[] a:nw.getCycleWidgets()){
                if(i==cycleIndex || cycleIndex==-1){
                    for(InstructionNodeWidget w: a) {
                        w.makeVisible();
                    }
                }
                i++;
            }
        }
        createCurrentLayoutGraph();
        refreshAll();
        validate();
        autoLayout();
    }

    /**
     * Hides all nodes of a specific type.
     */
    public void handleSetVisibilityNodeType(boolean visible, Instruction.InstructionType type){

        for(InstructionNodeWidget w: nodewidgets.values()){
            if(type==null || w.getInstruction().getInstructionType()==type){
                if(visible){
                    w.makeVisible();
                } else{
                    if(w.isWidgetVisible()) w.addNodeAttribute(new InvisibleAttribute());
                }
            }
        }

        createCurrentLayoutGraph();
        fireUpdateNodeData();
    }


    /**
     * Makes all nodes within the list visible
     */
    void handleShowNodes(Instruction[] instruction) {
        for(Instruction i: instruction){
            InstructionNodeWidget w=getNodeWidget(i.getID());
            if(w!=null){
                w.makeVisible();
            }
        }

        createCurrentLayoutGraph();
        refreshAll();
        validate();
        autoLayout();
    }
    //</editor-fold>


    //<editor-fold defaultstate="collapsed" desc=" Layouting ">

    /**
     * Performs a layout id autoLayout==true
     */
    public void autoLayout() {
        if(autoLayout){
            layout();
        }
    }

    private LayoutGraph layoutGraph=null;
    private Hashtable<Vertex, InstructionNodeWidget> VertexToWidget=null;
    private Hashtable<Widget, Vertex> WidgetToVertex=null;
    private Hashtable<Cluster,String> clusterToId=null;

    /**
     * Returns the current layoutgraph.
     */
    public LayoutGraph getLayoutGraph(){
        return layoutGraph;
    }

    /**
     * This method returns the binding list of Widgets an Verticles of the layoutgraph.
     */
    public Hashtable<Widget, Vertex> getWidgetToVertex(){
        return WidgetToVertex;
    }

    /**
     * This method fills the layoutGraph, clusterToId, WidgetToVertex and vertexToWidget Datastructures
     */
    private void createCurrentLayoutGraph(){
        Hashtable<String, Vertex> idToVertex=new Hashtable<String, Vertex>();
        TreeSet<Link> links=new TreeSet<Link>();
        TreeSet<Vertex> verticles=new TreeSet<Vertex>();

        clusterToId=new Hashtable<Cluster,String>();
        VertexToWidget=new Hashtable<Vertex, InstructionNodeWidget>();
        WidgetToVertex=new Hashtable<Widget, Vertex>();

        DiGraph model=calculationModel.clone();

        //Remove all invisible verticles from the model
        if(!layoutInvisibleNodes){
            for(InstructionNodeWidget nw:getNodeWidgets()){
                if(!nw.isWidgetVisible()) model.removeNode(model.getNode(nw.getID()));
            }
        }

        int i=0, j=0, ci=0;
        //collect the link set
        for(DiGraph dg:model.getConnectedComponents()){
            Hashtable<String, Cluster> idToCluster=new Hashtable<String, Cluster>();
            for(Edge e:dg.getEdges()){
                String nID1=e.source.ID;
                String nID2=e.destination.ID;
                Vertex v1,v2;
                InstructionNodeWidget nw1=getNodeWidget(nID1);
                InstructionNodeWidget nw2=getNodeWidget(nID2);

                if(idToVertex.containsKey(nID1)) v1=idToVertex.get(nID1);
                else{
                    Cluster c;
                    String block=nw1.getInstruction().getSourceBlock();
                    if(block==null) block=DEFAULTBLOCK;
                    if(highlightClustering&&nw1.isPathHighlighted()) block=HIGHLIGHTBLOCK;

                    if(idToCluster.containsKey(block)){
                        c=idToCluster.get(block);
                    } else{
                        c=new GraphCluster(ci++,null,null);
                        idToCluster.put(block,c);
                        clusterToId.put(c,block);
                    }
                    v1=new GraphVertex(i++,c,nw1.getLocation(),nw1);
                    idToVertex.put(nID1,v1);
                    VertexToWidget.put(v1,nw1);
                    WidgetToVertex.put(nw1,v1);
                }

                if(idToVertex.containsKey(nID2)) v2=idToVertex.get(nID2);
                else{
                    Cluster c;
                    String block=nw2.getInstruction().getSourceBlock();
                    if(block==null) block=DEFAULTBLOCK;
                    if(highlightClustering&&nw2.isPathHighlighted()) block=HIGHLIGHTBLOCK;

                    if(idToCluster.containsKey(block)){
                        c=idToCluster.get(block);
                    } else{
                        c=new GraphCluster(ci++,null,null);
                        idToCluster.put(block,c);
                        clusterToId.put(c,block);
                    }
                    v2=new GraphVertex(i++,c,nw2.getLocation(),nw2);
                    idToVertex.put(nID2,v2);
                    VertexToWidget.put(v2,nw2);
                    WidgetToVertex.put(nw2,v2);
                }

                Port p1=new GraphPort(v1);
                Port p2=new GraphPort(v2);


                //Add dataflow clusterlinks
                Cluster source=v1.getCluster();
                Cluster target=v2.getCluster();
                ((Set<Cluster>)source.getSuccessors()).add(target);
                ((Set<Cluster>)target.getPredecessors()).add(source);

                links.add(new GraphLink(j++,p1,p2));
            }
        }

        //collect additional Verticles
        for(Node n:model.getNodes()){
            if(n.edges.size()==0){
                InstructionNodeWidget nw=getNodeWidget(n.ID);
                Cluster c=new GraphCluster(ci++,null,null);
                Vertex v=new GraphVertex(i++,c,nw.getLocation(),nw);
                verticles.add(v);
                VertexToWidget.put(v,nw);
                WidgetToVertex.put(nw,v);
                idToVertex.put(n.ID,v);
                String block=nw.getInstruction().getSourceBlock();
                if(block==null) block=DEFAULTBLOCK;
                if(highlightClustering&&nw.isPathHighlighted()) block=HIGHLIGHTBLOCK;
                clusterToId.put(c,block);
            }
        }

        layoutGraph=new LayoutGraph(links,verticles);
        for(Link l: layoutGraph.getLinks()){
            ((GraphPort)l.getFrom()).setLayoutGraph(layoutGraph);
            ((GraphPort)l.getTo()).setLayoutGraph(layoutGraph);
        }

    }

    /** Performs the layout according to the External Layouter */
    public void layout(){
        //fill datastructures: layoutgraph, VertexToWidget and clusterToId
        createCurrentLayoutGraph();
        //Do layouting step!
        removeClusterWidgets();

        layouter.setUseCurrentNodePositions(USECURRENTNODEPOSITIONS);
        layouter.doLayout(layoutGraph);


        //Add cluster widgets
        if(layouter.isClusteringSupported()){
            Hashtable<Cluster,LinkedList<Widget>> clusters=new Hashtable<Cluster,LinkedList<Widget>>();
            for(Vertex v:layoutGraph.getVertices()){
                if(!clusters.containsKey(v.getCluster())){
                    clusters.put(v.getCluster(),new LinkedList<Widget>());
                }
                clusters.get(v.getCluster()).add(VertexToWidget.get(v));
            }

            LinkedList<ClusterWidget> widgets=new LinkedList<ClusterWidget>();
            for(Cluster c :clusters.keySet()){
                LinkedList<Widget> l=clusters.get(c);
                String id=clusterToId.get(c);
                Color col=null;
                if(HIGHLIGHTBLOCK.equals(id)) col=Color.RED;
                else col=Color.BLUE;
                widgets.add(new ClusterWidget(id,l,this,col));
            }

            setClustersWidgets(widgets);
        }




        //Set the real positions of the graph
        if(locationAnimation && layouter.isAnimationSupported()){
            SetLocationAnimator animator=new SetLocationAnimator(this,VertexToWidget);
            animator.animate();
        }
        else{
            for(Vertex v: layoutGraph.getVertices()){
                InstructionNodeWidget nw=VertexToWidget.get(v);
                //Instruction widget inset correction: Neccessary because layouter do not support insets!
                Point pos=v.getPosition();
                pos.translate(InstructionNodeWidget.BORDERINSET,InstructionNodeWidget.BORDERINSET);
                nw.setPreferredLocation(pos);
                if(v instanceof GraphVertex) ((GraphVertex)v).setDirty(true);
            }
            refreshClusterWidgets();
        }

        validate();

    }

     /**
     * Sets the cluster widgets, this method is meant to be
     * used within the external layouter.
     */
    public void setClustersWidgets(Collection<ClusterWidget> clusters){
        removeClusterWidgets();
        if(clusters!=null){
            this.clusters=clusters;

            for(ClusterWidget w:clusters) {
                clusterLayer.addChild(w);
                w.setVisibility(clusterBordersVisible);
                w.refresh();
                for(Widget iw:w.getWidgets()){
                    if(iw instanceof InstructionNodeWidget)
                        ((InstructionNodeWidget)iw).setClusterWidget(w);
                }

            }
        }
        fireUpdateNodeData();
    }


    /**
     * Removes all clusters. This method is called
     * before layouting is done.
     */
    public void removeClusterWidgets(){
        if(clusters!=null){
            for(ClusterWidget w:clusters){
                clusterLayer.removeChild(w);
            }
            for(InstructionNodeWidget w:nodewidgets.values()){
                w.setClusterWidget(null);
            }
        }
        clusters=null;
        fireUpdateNodeData();
    }
    //</editor-fold>


    //<editor-fold defaultstate="collapsed" desc=" Getter and Setter Methods ">
     /**
     * Implemented to achive a clear cut between data model and visualisation.
     * Otherwise the Instruction class would have to save position data.
     */
    public InstructionNodeWidget getNodeWidget(String i){
        return nodewidgets.get(i);
    }

    /**
     * Returns all Nodewidgets
     */
    public InstructionNodeWidget[] getNodeWidgets(){
        return nodewidgets.values().toArray(new InstructionNodeWidget[nodewidgets.size()]);
    }

    /** Sets the layouter */
    public void setExternalLayouter(ExternalGraphLayouter l){
        layouter=l;
    }

    /** Return the current layouter */
    public ExternalGraphLayouter getExternalLayouter(){
        return layouter;
    }

    /**
     * Defines if links between different clusters are grayed.
     */
    public void setInterClusterLinkGrayed(boolean b){
        interCluserLinkGray=b;
    }

    /**
     * Returns if links between different clusters are grayed
     */
    public boolean isInterClusterLinkGrayed(){
        return interCluserLinkGray;
    }

    /**
     * Defines if scene performs autolayout.
     */
    public void setAutoLayout(boolean b){
        autoLayout=b;
        autoLayout();

    }

    /**
     * Is Auto Layout active?.
     */
    public boolean isAutoLayout(){
        return autoLayout;
    }


    /**
     * Defines if invisible nodes are layouted.
     */
    public void setLayoutInvisibleNodes(boolean b){
        layoutInvisibleNodes=b;
    }

    /**
     * Returns if invisible Nodes are layouted or not.
     */
    public boolean isLayoutInvisibleNodes(){
        return layoutInvisibleNodes;
    }

    /**
     * Defines if node animation is used.
     */
    public void setNodeAnimation(boolean b){
        locationAnimation=b;
    }

    /**
     * Returns if invisible Nodes are layouted or not.
     */
    public boolean isNodeAnimation(){
        return locationAnimation;
    }

    /**
     * Returns a clone of the calculation model of the scene
     */
    public DiGraph getCaluculationModel(){
        return calculationModel.clone();
    }

    /**
     * Defines if highlightclustering is used.
     */
    public void setHighlightClustering(boolean b){
        highlightClustering=b;
    }

    /**
     * Returns if highlightclustering is active
     */
    public boolean isHighlightClustering(){
        return highlightClustering;
    }

    /**
     * Returns if cluster borders are visible
     */
    public boolean isClusterBordersVisible(){
        return clusterBordersVisible;
    }

    /**
     * Defines if the layout algorithms should build on the current node positions.
     * This does only applys to layout-algorithms optimizing from a predefined positioning
     * (like force layouts)
     */
    public void setUseCurrentNodePositions(boolean b){
        USECURRENTNODEPOSITIONS=b;
    }

    /**
     * Returns if the current node position is used to build on by the external layouter.
     * This does only applys to layout-algorithms optimizing from a predefined positioning
     * (like force layouts)
     */
    public boolean isUseCurrentNodePositions(){
        return USECURRENTNODEPOSITIONS;
    }


    /**
     * Sets is clusters borders are shown or not.
     */
    public void setClusterBordersVisible(boolean b){
        clusterBordersVisible=b;
        if(clusters!=null){
            for(ClusterWidget w: clusters){
                w.setVisibility(b);
            }
        }
        refreshAll();
        validate();
    }
    //</editor-fold>


    //<editor-fold defaultstate="collapsed" desc=" InstructionSceneListener Methods ">
    /**
     * Registers an scene listener
     */
    public void addInstructionSceneListener(InstructionSceneListener l){
        if(!listeners.contains(l)){
            listeners.add(l);
        }
    }

    /**
     * Removes a scene listener
     */
    public void removeInstructionSceneListener(InstructionSceneListener l){
        listeners.remove(l);
    }

    /**
     * Fires the double clicked event
     */
    protected void fireDoubleClicked(InstructionNodeWidget w){
        for(InstructionSceneListener l: listeners){
            l.doubleClicked(w);
        }
    }

    /**
     * Fires the update node data event
     */
    protected void fireUpdateNodeData(){
        for(InstructionSceneListener l: listeners){
            l.updateNodeData();
        }
    }

     /**
     * Fires the selection changed event
     */
    protected void fireSelectionChanged(){
        for(InstructionSceneListener l: listeners){
            HashSet<InstructionNodeWidget> w=new HashSet<InstructionNodeWidget>();
            for(Object o:getSelectedObjects()){
                if(o instanceof Instruction){
                    InstructionNodeWidget wi=getNodeWidget(((Instruction)o).getID());
                    if(wi != null) w.add(wi);
                }
            }
            l.selectionChanged(w);
        }
    }

    // </editor-fold>


    //<editor-fold defaultstate="collapsed" desc=" Mulit widget movement and selection strategy and provider ">
    /**
    * RectangularSelect Method returning a widget to be use as
    * selection boarder
    */
    public Widget createSelectionWidget() {
        Widget ret=new Widget(this);
        ret.setBorder(BorderFactory.createDashedBorder(Color.BLACK,10,5,true));
        return ret;
    }

    /**
     * RectangularSelect execution
     */
    public void performSelection(Rectangle rectangle) {
        HashSet<Instruction> set=new HashSet<Instruction>();

        //change to a rectangle with (x,y) at the top left
        if(rectangle.width<0){
            rectangle.x=rectangle.x+rectangle.width;
            rectangle.width*=-1;
        }
        if(rectangle.height<0){
            rectangle.y=rectangle.y+rectangle.height;
            rectangle.height*=-1;
        }

        for(InstructionNodeWidget n:nodewidgets.values()){
            if(n.isWidgetVisible() && rectangle.contains(n.getPreferredLocation()))
                set.add(n.getInstruction());
        }

        setSelectedObjects(set);
        refreshAll();
        validate();
        fireSelectionChanged();
    }

    /** MoveStrategy- free move, no modifications*/
    public Point locationSuggested(Widget widget, Point original, Point suggested) {
        return suggested;
    }

    /**
     * Selectes exactly one widget defined by the id and centers the view
     * on it if centeron==true.
     */
    public void setSingleSelectedWidget(String id, boolean centeron) {
        InstructionNodeWidget node=getNodeWidget(id);

        if(node!=null && node.isWidgetVisible()) {
            setSelectedObjects(Collections.singleton(node.getInstruction()));
            refreshAll();
            validate();
            fireSelectionChanged();
            if(centeron) centerOn(node.getPreferredLocation());
        }
    }

    /* Single click selection method- Copy from ObjectScene Selectprovider*/
    public boolean isAimingAllowed(Widget widget, Point point, boolean b) {
        return false;
    }

    /* Single click selection method- Copy from ObjectScene Selectprovider*/
    public boolean isSelectionAllowed(Widget widget, Point point, boolean b) {
        Object object = findObject (widget);
        return object != null  &&  (b  ||  ! getSelectedObjects ().contains (object));
    }

    /* Single click selection method- Copy from ObjectScene Selectprovider*/
    public void select(Widget widget, Point point, boolean b) {
        Object object = findObject (widget);
        if (object != null) {
            if (getSelectedObjects ().contains (object)) return;
            userSelectionSuggested (Collections.singleton (object), b);
        } else
            userSelectionSuggested (Collections.emptySet (), b);
        fireSelectionChanged();
    }



    /**
     * This class implements a MoveProvider to achieve the funktionality of
     * a multi widget movement.
     */
    private class MultiWidgetMovementProvider implements MoveProvider{

        private MoveProvider mp=ActionFactory.createDefaultMoveProvider();

        public void movementStarted(Widget widget) {
            mp.movementStarted(widget);
        }

        public void movementFinished(Widget widget) {
            mp.movementFinished(widget);
        }

        public Point getOriginalLocation(Widget widget) {
            return widget.getPreferredLocation();//mp.getOriginalLocation(widget);
        }

        public void setNewLocation(Widget widget, Point point) {
            //test for movement support
            Scene s=widget.getScene();
            if(s instanceof InstructionNodeGraphScene){
                boolean r=((InstructionNodeGraphScene)s).getExternalLayouter().isMovementSupported();
                if(!r) return;
            }
            //perform movement
            Point original=getOriginalLocation(widget);
            int dx=point.x-original.x;
            int dy=point.y-original.y;
            for(Object o: getSelectedObjects()){
                if(o instanceof Instruction){
                    InstructionNodeWidget w=getNodeWidget(((Instruction)o).getID());
                    if(w!=widget){
                        Point p=(Point)w.getPreferredLocation().clone();
                        p.translate(dx,dy);

                        //Update layoutgraph Positions
                        Vertex v=WidgetToVertex.get(w);
                        if(v!=null) v.setPosition(p);
                        ((GraphVertex)v).setDirty(true);

                        w.setPreferredLocation(p);
                        w.refresh();
                        ClusterWidget cw=w.getClusterWidget();
                        if(cw!=null) cw.refresh();
                    }
                }

            }
            mp.setNewLocation(widget,point);

            //Update layoutgraph Positions
            Vertex v=WidgetToVertex.get(widget);
            if(v!=null) v.setPosition(point);
            ((GraphVertex)v).setDirty(true);

            //Refresh of cluster for single node movement
            if(widget instanceof InstructionNodeWidget){
                ClusterWidget cw=((InstructionNodeWidget)widget).getClusterWidget();
                if(cw!=null) cw.refresh();
            }
        }
    }

     // </editor-fold>


    protected Widget attachNodeWidget (Instruction node) {

        InstructionNodeWidget widget = new InstructionNodeWidget (node,this);

        WidgetAction.Chain actions = widget.getActions ();
        actions.addAction (createObjectHoverAction ());

        actions.addAction (ActionFactory.createSelectAction(this));
        //actions.addAction(createSelectAction());
        actions.addAction (moveAction);

        actions.addAction(ActionFactory.createEditAction(this));

        nodewidgets.put(node.getID(),widget);
        mainLayer.addChild (widget);
        return widget;
    }


    protected void attachEdgeSourceAnchor (String edge, Instruction oldSourceNode, Instruction sourceNode) {
        ConnectionWidget edgeWidget = (ConnectionWidget) findWidget (edge);
        Widget sourceNodeWidget = findWidget (sourceNode);
        Anchor sourceAnchor = AnchorFactory.createRectangularAnchor (sourceNodeWidget);
        edgeWidget.setSourceAnchor (sourceAnchor);
    }

    protected void attachEdgeTargetAnchor (String edge, Instruction oldTargetNode, Instruction targetNode) {
        ConnectionWidget edgeWidget = (ConnectionWidget) findWidget (edge);
        Widget targetNodeWidget = findWidget (targetNode);
        Anchor targetAnchor = AnchorFactory.createRectangularAnchor (targetNodeWidget);
        edgeWidget.setTargetAnchor (targetAnchor);
    }

    protected Widget attachEdgeWidget(String edge) {
        ConnectionWidget widget = new InstructionConnectionWidget (this);
        widget.setTargetAnchorShape (AnchorShape.TRIANGLE_FILLED);
        connectionLayer.addChild (widget);
        widget.setRouter(linkRouter);
        return widget;
    }


}
