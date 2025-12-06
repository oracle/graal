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

import at.ssw.visualizer.dataflow.attributes.IAdditionalWidgetAttribute;
import at.ssw.visualizer.dataflow.attributes.IExpandNodeAttribute;
import at.ssw.visualizer.dataflow.attributes.INodeAttribute;
import at.ssw.visualizer.dataflow.attributes.IInvisibilityAttribute;
import at.ssw.visualizer.dataflow.attributes.IPathHighlightAttribute;
import at.ssw.visualizer.dataflow.attributes.IPopupContributorAttribute;
import at.ssw.visualizer.dataflow.attributes.InvisibleAttribute;
import at.ssw.visualizer.dataflow.instructions.Instruction;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.MenuKeyEvent;
import javax.swing.event.MenuKeyListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.border.Border;
import org.netbeans.api.visual.border.BorderFactory;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.Scene;
import org.netbeans.api.visual.widget.SeparatorWidget;
import org.netbeans.api.visual.widget.Widget;

/**
 *
 * @author Stefan Loidl
 */
public class InstructionNodeWidget extends Widget{

    /*Font of the nodes*/
    protected final static String DEFAULTFONT = "Arial-12";
    /*This string is used if text in the node is abbriviated*/
    protected final static String ABBREV="...";
    //Length bounds of text within nodes
    protected final static int MAXSTRINGLENGTH=15;
    protected final static int MINSTRINGLENGTH=7;


    //Colors
    private static Color colorParameter=new Color(255,230,200);
    private static Color colorParameterSelect=new Color(240,180,0);
    private static Color colorConstant=new Color(255,200,200);
    private static Color colorConstantSelect=new Color(240,0,0);
    private static Color colorPhi=new Color(200,200,255);
    private static Color colorPhiSelect=new Color(0,0,240);
    private static Color colorOperation=new Color(200,255,200);
    private static Color colorOperationSelect=new Color(0,240,0);
    private static Color colorControlFlow=new Color(210,255,255);
    private static Color colorControlFlowSelect=new Color(0,240,200);
    private static Color colorDefault=new Color(255,255,255);
    private static Color colorDefaultSelect=new Color(245,245,245);

    private static Color colorLine=Color.BLACK;
    private static Color colorHLLine=Color.GRAY;


    //Because Borders can only be created by Factorys, but colors cannot be
    //changed afterwards- to reduce GC Load Boarders are created only once!
    private Border selectedBorder;
    private Border selectedHLBorder;
    private Border border;
    private Border HLBorder;
    private Border emptyBorder;

    public static final int BORDERARC=10;
    public static final int BORDERINSET=5;

    //popup of the node widget
    private JPopupMenu popup;

    //default menu item names
    private static final String EXPANDINFLUENCE="Expand Influenced Tree";
    private static final String EXPANDNODE="Expand Node";
    private static final String EXPANDPARENTINFLUENCE="Expand Parent Influence";
    private static final String EXPANDCYCLES="Expand Cycles";

    private static final String ALL="(All)";

    private static final String TOGGLEVISIBILITY="Toggle Visibility";
    private static final String HIDEALLBUT="Hide All But Current";
    private static final String SHOWINFLUENCE="Show Influence Tree";
    private static final String SHOWPARENTINFLUENCE="Show Parent Influence";
    private static final String SHOWCYCLES="Show Cycles";
    private static final String SHOWPREDECESSORS="Show Predecessors";
    private static final String SHOWSUCCESSORS="Show Successors";


    private static final String CYCLEITEMSEPERATOR="-";
    private static final char CYCLECMDSEPERATOR='-';


    //Sub Menu within the popup
    private static final String GOTOSUCCESSORS="Go to Successor";
    private static final String GOTOPREDECESSOR="Go to Predecessor";
    private static final String VISIBILITY="Visibility";



    private LabelWidget lwID;
    private LabelWidget lwInstruction;
    private LabelWidget lwBlock;

    //indicate if predecessors or sucessors exist.
    private Widget pred, suc;

    //Data model for the node.
    private Instruction instruction;

    //Attributelist
    private LinkedList<INodeAttribute> attributeList;

    //Widgets that are appended to the standard view
    private LinkedList<Widget> additionalWidgets;

    //true if the node contains an IPathHighlightAttribute
    private boolean pathHighlighted=false;

    //Inforation of cycles the node is within.
    private Collection<InstructionNodeWidget[]> cycles=null;

    //Cluster the node is within
    private ClusterWidget cluster;

    //This value is set during setWidgetData method
    private boolean expanded=false;


    /** Creates a new instance of InstructionNodeWidget */
    public InstructionNodeWidget(Instruction i, Scene s) {
        super(s);
        instruction=i;

        additionalWidgets=new LinkedList<Widget>();
        attributeList=new LinkedList<INodeAttribute>();

        //initialize popup menu
        popup=new JPopupMenu();
        getActions().addAction(ActionFactory.createPopupMenuAction(new PopupMenuProvider() {
            public JPopupMenu getPopupMenu(Widget widget, Point point) {
                return getPopup();
            }
        }));

        this.setLayout(LayoutFactory.createVerticalFlowLayout());

        lwID= new LabelWidget(s,i.getID());
        lwID.setFont(Font.decode(DEFAULTFONT).deriveFont(Font.BOLD));
        this.addChild (lwID);
        lwID.resolveBounds(lwID.getLocation(),lwID.getPreferredBounds());

        lwInstruction=new LabelWidget(s,modifiyStringLength(i.getInstructionString(),MINSTRINGLENGTH,MAXSTRINGLENGTH,ABBREV));
        lwInstruction.setFont(Font.decode(DEFAULTFONT));
        lwInstruction.setAlignment(LabelWidget.Alignment.CENTER);
        this.addChild(lwInstruction);
        lwInstruction.resolveBounds(lwInstruction.getLocation(),lwInstruction.getPreferredBounds());

        lwBlock=new LabelWidget(s,i.getSourceBlock());
        lwBlock.setFont(Font.decode(DEFAULTFONT));
        this.addChild(lwBlock);
        lwBlock.resolveBounds(lwBlock.getLocation(),lwBlock.getPreferredBounds());

        pred=new HiddenNodesWidget(s,true);
        suc=new HiddenNodesWidget(s,false);

        this.setToolTipText(createToolTip());

        initWidgetBorders();
        setWidgetData();
    }


    // <editor-fold defaultstate="collapsed" desc="visual helper functions- initialization of borders, appearance handling">
    /**
     * Return the color that is used to paint a node of specific type.
     */
    protected static Color getColorByType(Instruction.InstructionType type, boolean selected){
       switch(type){
            case PARAMETER:
                if(selected) return colorParameterSelect;
                else return colorParameter;
            case CONSTANT:
                if(selected) return colorConstantSelect;
                else return colorConstant;
            case PHI:
                if(selected) return colorPhiSelect;
                else return colorPhi;
             case CONTROLFLOW:
                if(selected) return colorControlFlowSelect;
                else return colorControlFlow;
             case OPERATION:
                if(selected) return colorOperationSelect;
                else return colorOperation;
            default:
                if(selected) return colorDefaultSelect;
                else return colorDefault;
        }
    }

    /**
     * Returns the line color to be used in the widget.
     */
    protected static Color getLineColor(){
        return colorLine;
    }

    /**
     * This function sets the borders (highlighted and selected combinations);
     */
    private void initWidgetBorders(){
        Color c=getColorByType(instruction.getInstructionType(),false);
        Color cs=getColorByType(instruction.getInstructionType(),true);

        border= BorderFactory.createRoundedBorder(BORDERARC,BORDERARC,
                BORDERINSET,BORDERINSET,c,colorLine);
        selectedBorder= BorderFactory.createRoundedBorder(BORDERARC,BORDERARC,
                BORDERINSET,BORDERINSET,cs,colorLine);
        HLBorder= BorderFactory.createRoundedBorder(BORDERARC,BORDERARC,
                BORDERINSET,BORDERINSET,c,colorHLLine);
        selectedHLBorder= BorderFactory.createRoundedBorder(BORDERARC,BORDERARC,
                BORDERINSET,BORDERINSET,cs,colorHLLine);

        emptyBorder=BorderFactory.createEmptyBorder();
    }



    /**
     * This method handles the the Data that is shown within the widget. This
     * data depends on the Attibutes of the Widget.
     */
    private void setWidgetData(){
        boolean visible=true, showBlock=false, showInstr=false;
        additionalWidgets.clear();
        pathHighlighted=false;

        //determine node attibutes
        Iterator<INodeAttribute> iter=attributeList.iterator();
        while(iter.hasNext()){
            INodeAttribute a=iter.next();

            if(!a.validate()) {
                if(a.removeable()) {
                    iter.remove();
                }

                continue;
            }

            if(a instanceof IInvisibilityAttribute) visible=false;
            if(a instanceof IExpandNodeAttribute){
               if(((IExpandNodeAttribute)a).showBlock()) showBlock=true;
               if(((IExpandNodeAttribute)a).showInstruction()) showInstr=true;
            }

            if(a instanceof IPathHighlightAttribute) pathHighlighted=true;

            if(a instanceof IAdditionalWidgetAttribute){
                additionalWidgets.add(((IAdditionalWidgetAttribute)a).getWidget());
            }
        }

        removeChildren();

        if(visible){
            //Add invisible predecessor indicator
            InstructionNodeGraphScene s=(InstructionNodeGraphScene) getScene();
            for(Instruction i: instruction.getPredecessors()){
                InstructionNodeWidget w=s.getNodeWidget(i.getID());
                if(!(w==null) && !w.isWidgetVisible()){
                    addChild(pred);
                    break;
                }
            }

            expanded=false;
            addChild(lwID);
            if(showInstr) {
                addChild(lwInstruction);
                expanded=true;
            }
            if(showBlock) {
                addChild(lwBlock);
                expanded=true;
            }
            //Embedded widgets are only shown if expanded
            if(showInstr){
                Iterator<Widget> iterator =additionalWidgets.iterator();
                while(iterator.hasNext()){
                    addChild(iterator.next());
                }
            }

            //Add invisible sucessor indicator
            for(Instruction i: instruction.getSuccessors()){
                InstructionNodeWidget w=s.getNodeWidget(i.getID());
                if(!(w==null) && !w.isWidgetVisible()){
                    addChild(suc);
                    break;
                }
            }
        }

        setWidgetBorder();
        resolveBounds(getLocation(),calculatePreferredBounds());
    }

    /**
     * NOTE: This method is a only slightly changed copy an paste from Widget!!!
     * Instead of getBounds- getPreferredBounds is used.
     */
     private Rectangle calculatePreferredBounds () {
        Insets insets = border.getInsets ();
        Rectangle clientArea = calculateClientArea ();



        for (Widget child : getChildren()) {
            Point location = child.getLocation ();
            Rectangle bounds = child.getPreferredBounds ();
            bounds.translate (location.x, location.y);
            clientArea.add (bounds);
        }
        clientArea.x -= insets.left;
        clientArea.y -= insets.top;
        clientArea.width += insets.left + insets.right;
        clientArea.height += insets.top + insets.bottom;
        return clientArea;
    }


    /**
     * This Method handels the Widgets border and coloring depending on hovering
     * and selection state.
     */
    private void setWidgetBorder(){
        if(isWidgetVisible()){
            if(this.getState().isHovered()){

                if(this.getState().isSelected())
                    this.setBorder(selectedHLBorder);
                else
                    this.setBorder(HLBorder);

                lwID.setForeground(colorHLLine);
                lwInstruction.setForeground(colorHLLine);
                lwBlock.setForeground(colorHLLine);
            } else{

                if(this.getState().isSelected())
                    this.setBorder(selectedBorder);
                else
                    this.setBorder(border);

                lwID.setForeground(colorLine);
                lwInstruction.setForeground(colorLine);
                lwBlock.setForeground(colorLine);
            }
        }
        else this.setBorder(emptyBorder);
    }

    // </editor-fold>


     /**
     * This Method is used to unifiy the length of Stings. Therefore a String
     * is modified according to the following rules:
     * 1. If in==null -> Return a String with min blanks
     * 2. If length of in is grater max then in is cut and abbrev is concatinated
     * to indicate that the String was shortend
     * 3. If Length is smaller than min blanks are added
     */
    protected static String modifiyStringLength(String in, int min, int max, String abbrev){
        StringBuffer mod;
        if(in == null)
            mod=new StringBuffer("");
        else
            mod=new StringBuffer(in);

        if(mod.length()<min){
            for(int i=mod.length();i<min;i++) mod.append(' ');
        }
        else if(mod.length() > max){
            mod=new StringBuffer(mod.substring(0,max-abbrev.length())+abbrev);
        }

        return mod.toString();
    }

    /**
     * Returns the instruction which this nodewidget is a representation for.
     */
    public Instruction getInstruction(){
        return instruction;
    }

    /**
     * Overwritten from parent class- Painting the widget
     */
    protected void paintWidget(){
        setWidgetBorder();
        super.paintWidget();
    }


    /**
     * Is the node visible?
     */
    public boolean isWidgetVisible(){
        Iterator<INodeAttribute> iter=attributeList.iterator();
        while(iter.hasNext()){
            INodeAttribute a=iter.next();
            if(a instanceof IInvisibilityAttribute && a.validate()) return false;
        }
        return true;
    }

    /**
     * Removes all invisible attributes from the widget.
     */
    public void makeVisible(){
       for(INodeAttribute att: attributeList.toArray(new INodeAttribute[attributeList.size()])){
           if(att instanceof IInvisibilityAttribute && att.removeable()) attributeList.remove(att);
       }
    }

    /**
     * Returns if the instruction-string or basic-block is shown.
     */
    public boolean isExpanded(){
       return expanded;
    }

    /**
     * Adds a new attriute to the node.
     */
    public void addNodeAttribute(INodeAttribute a){
        if(a!=null) attributeList.add(a);
    }

    /**
     * Refreshes the visual appearence of the node and schedules it for
     * revalidation.
     */
    protected void refresh() {
        setWidgetData();
        revalidate();
    }

     /**
     * This method fills the popup menu.
     */
    private void rebuildPopupMenu(){
        popup.removeAll();
        JMenuItem item;
        JMenu menu;


        //MENU Go to Predecessor
        menu=new JMenu(GOTOPREDECESSOR);
        if(instruction.getPredecessors().length==0){
            menu.setEnabled(false);
        } else{
            for(Instruction i: instruction.getPredecessors()){
                item=new JMenuItem(i.getID());
                item.setActionCommand(i.getID());
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                        s.setSingleSelectedWidget(e.getActionCommand(), true);
                        //Make the new node edited -> selected in the list
                        s.edit(s.getNodeWidget(e.getActionCommand()));
                    }
                });
                menu.add(item);
            }
        }
        popup.add(menu);

        //MENU Go to Successor
        menu=new JMenu(GOTOSUCCESSORS);
        if(instruction.getSuccessors().length==0){
            menu.setEnabled(false);
        } else{
            for(Instruction i: instruction.getSuccessors()){
                item=new JMenuItem(i.getID());
                item.setActionCommand(i.getID());
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                        s.setSingleSelectedWidget(e.getActionCommand(), true);
                        //Make the new node edited -> selected in the list
                        s.edit(s.getNodeWidget(e.getActionCommand()));
                    }
                });
                menu.add(item);
            }
        }
        popup.add(menu);
        popup.addSeparator();


         //MENUITEM: Expand Node
        item =new JMenuItem(EXPANDNODE);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Scene s=getScene();
                if(s instanceof InstructionNodeGraphScene){
                    ((InstructionNodeGraphScene)s).handleExpandNode(instruction);
                }
            }
        });
        popup.add(item);

        //MENUITEM: Expand Influence Tree
        item =new JMenuItem(EXPANDINFLUENCE);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Scene s=getScene();
                if(s instanceof InstructionNodeGraphScene){
                    ((InstructionNodeGraphScene)s).handleExpandInfluenceTree(instruction, true);
                }
            }
        });
        popup.add(item);
        item.setEnabled(instruction.getSuccessors().length>0);


        //MENUITEM: Expand Parent Influence
        item =new JMenuItem(EXPANDPARENTINFLUENCE);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Scene s=getScene();
                if(s instanceof InstructionNodeGraphScene){
                    ((InstructionNodeGraphScene)s).handleExpandInfluenceTree(instruction,false);
                }
            }
        });
        popup.add(item);
        item.setEnabled(instruction.getPredecessors().length>0);



        //MENU Expand Cycles
        menu=new JMenu(EXPANDCYCLES);
        if(cycles==null || cycles.size()==0){
            menu.setEnabled(false);
        } else{

            //Expand all Cycles
            item =new JMenuItem(ALL);
            item.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    Scene s=getScene();
                    if(s instanceof InstructionNodeGraphScene){
                        ((InstructionNodeGraphScene)s).handleExpandCycles(getID());
                    }
                }
            });
            menu.add(item);

            int i=0;
            for(InstructionNodeWidget[] nw: cycles){
                StringBuffer st=new StringBuffer();
                for(InstructionNodeWidget n:nw){
                    st.append(n.getID());
                    st.append(CYCLEITEMSEPERATOR);
                }
                st.setLength(st.length()-CYCLEITEMSEPERATOR.length());

                item=new JMenuItem(st.toString());
                item.setActionCommand(getID()+CYCLECMDSEPERATOR+String.valueOf(i));
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                        String com=e.getActionCommand();
                        int index=com.indexOf(CYCLECMDSEPERATOR);
                        if(index!=-1){
                            try{
                                s.handleExpandCycle(com.substring(0,index),
                                    Integer.parseInt(com.substring(index+1)));
                            }catch(Exception ex){}
                        }
                    }
                });
                menu.add(item);
                i++;
            }
        }
        popup.add(menu);
        popup.addSeparator();

        //ITEM Toggle Visibility
        item =new JMenuItem(TOGGLEVISIBILITY);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                s.handleToggleVisibility(instruction, true);

            }
        });
        popup.add(item);

        //ITEM Hide All BuT
        item =new JMenuItem(HIDEALLBUT);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                s.handleHideAllBut(instruction);
            }
        });
        popup.add(item);

        //ITEM: Make Influence Tree Visible
        item =new JMenuItem(SHOWINFLUENCE);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                s.handleMakeTreeVisible(instruction,true);
            }
        });
        popup.add(item);
        item.setEnabled(instruction.getSuccessors().length>0);

        //ITEM: Make Parent Influence Visible
        item =new JMenuItem(SHOWPARENTINFLUENCE);
        item.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                s.handleMakeTreeVisible(instruction,false);
            }
        });
        popup.add(item);
        item.setEnabled(instruction.getPredecessors().length>0);


        //MENU Make Cycles Visible
        menu=new JMenu(SHOWCYCLES);
        if(cycles==null || cycles.size()==0){
            menu.setEnabled(false);
        } else{

            //Expand all Cycles
            item =new JMenuItem(ALL);
            item.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    Scene s=getScene();
                    if(s instanceof InstructionNodeGraphScene){
                        ((InstructionNodeGraphScene)s).handleMakeCycleVisible(getID(),-1);
                    }
                }
            });
            menu.add(item);

            int i=0;
            for(InstructionNodeWidget[] nw: cycles){
                StringBuffer st=new StringBuffer();
                for(InstructionNodeWidget n:nw){
                    st.append(n.getID());
                    st.append(CYCLEITEMSEPERATOR);
                }
                st.setLength(st.length()-CYCLEITEMSEPERATOR.length());

                item=new JMenuItem(st.toString());
                item.setActionCommand(getID()+CYCLECMDSEPERATOR+String.valueOf(i));
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
                        String com=e.getActionCommand();
                        int index=com.indexOf(CYCLECMDSEPERATOR);
                        if(index!=-1){
                            try{
                                s.handleMakeCycleVisible(com.substring(0,index),
                                    Integer.parseInt(com.substring(index+1)));
                            }catch(Exception ex){}
                        }
                    }
                });
                menu.add(item);
                i++;
            }
        }
        popup.add(menu);

                //MENU Show Predecessors
        menu=new JMenu(SHOWPREDECESSORS);
        LinkedList<InstructionNodeWidget> list=new LinkedList<InstructionNodeWidget>();
        InstructionNodeGraphScene s=(InstructionNodeGraphScene)getScene();
        for(Instruction i:instruction.getPredecessors()){
            InstructionNodeWidget w=s.getNodeWidget(i.getID());
            if(w!=null && !w.isWidgetVisible()) list.add(w);
        }

        if(list.size()>0){
            //Show all
            item =new JMenuItem(ALL);
            item.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    InstructionNodeGraphScene scene=(InstructionNodeGraphScene)getScene();
                    scene.handleShowNodes(getInstruction().getPredecessors());
                }
            });
            menu.add(item);

            for(InstructionNodeWidget w: list){
                item =new JMenuItem(w.getID());
                item.setActionCommand(w.getID());
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene scene=(InstructionNodeGraphScene)getScene();
                        InstructionNodeWidget nw=scene.getNodeWidget(e.getActionCommand());
                        if(nw!=null){
                            Instruction[] inst=new Instruction[1];
                            inst[0]=nw.getInstruction();
                            scene.handleShowNodes(inst);
                        }
                    }
                });
                menu.add(item);
            }
        }
        else menu.setEnabled(false);
        popup.add(menu);


        //MENU Show Successors
        menu=new JMenu(SHOWSUCCESSORS);
        list=new LinkedList<InstructionNodeWidget>();

        for(Instruction i:instruction.getSuccessors()){
            InstructionNodeWidget w=s.getNodeWidget(i.getID());
            if(w!=null && !w.isWidgetVisible()) list.add(w);
        }

        if(list.size()>0){
            //Show all
            item =new JMenuItem(ALL);
            item.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    InstructionNodeGraphScene scene=(InstructionNodeGraphScene)getScene();
                    scene.handleShowNodes(getInstruction().getSuccessors());
                }
            });
            menu.add(item);

            for(InstructionNodeWidget w: list){
                item =new JMenuItem(w.getID());
                item.setActionCommand(w.getID());
                item.addActionListener(new ActionListener(){
                    public void actionPerformed(ActionEvent e) {
                        InstructionNodeGraphScene scene=(InstructionNodeGraphScene)getScene();
                        InstructionNodeWidget nw=scene.getNodeWidget(e.getActionCommand());
                        if(nw!=null){
                            Instruction[] inst=new Instruction[1];
                            inst[0]=nw.getInstruction();
                            scene.handleShowNodes(inst);
                        }
                    }
                });
                menu.add(item);
            }
        }
        else menu.setEnabled(false);
        popup.add(menu);

        boolean first=true;
        //MENUITEMS added by contibutor attibutes
        Iterator<INodeAttribute> iter=attributeList.iterator();
        while(iter.hasNext()){
            INodeAttribute a=iter.next();
            if(a instanceof IPopupContributorAttribute && a.validate()) {
                item=((IPopupContributorAttribute)a).getMenuItem();
                //seperator before the first element
                if(first){
                    popup.addSeparator();
                    first=false;
                }
                popup.add(item);
            }
        }
    }

    /**
     * Returns if the node is expanded.
     */
    public boolean isPathHighlighted(){
        return pathHighlighted;
    }

    /** Returns the unique id of the node*/
    public String getID() {
        return instruction.getID();
    }

    /** Sets the cycles the node is within*/
    public void setCycles(Collection<InstructionNodeWidget[]> cycles) {
        this.cycles=cycles;
        setToolTipText(createToolTip());
    }

    /** Returns a collection of all cycles the node is within */
    public Collection<InstructionNodeWidget[]> getCycleWidgets(){
        return cycles;
    }

    /** Sets the cluster the node is within */
    public void setClusterWidget(ClusterWidget w){
        cluster=w;
    }

    /** Returns the cluster */
    public ClusterWidget getClusterWidget(){
        return cluster;
    }

    /** Creates a html tooltip for the instruction */
    protected String createToolTip(){
        StringBuffer ret=new StringBuffer();
        ret.append("<html><b>Name: </b>"+instruction.getID()+"<p>");
        ret.append("<b>Instruction: </b>"+instruction.getInstructionString()+"<p>");
        if(cycles!=null && cycles.size() >0){
            ret.append("<b>Cycles:</b>");
            for(InstructionNodeWidget[] a:cycles){
                ret.append("<p> * ");
                for(InstructionNodeWidget w:a){
                    ret.append(w.getID());
                    ret.append(" ");
                }
            }
        }
        ret.append("</html>");
        return ret.toString();
    }

    /**
     * Returns the popup menu of the node
     */
    public JPopupMenu getPopup(){
        rebuildPopupMenu();
        return popup;
    }

}
