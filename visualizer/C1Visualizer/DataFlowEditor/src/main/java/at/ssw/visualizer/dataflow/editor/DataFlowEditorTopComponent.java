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
package at.ssw.visualizer.dataflow.editor;

import at.ssw.graphanalyzer.positioning.HierarchicalLayoutManager;
import at.ssw.visualizer.model.cfg.BasicBlock;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.dataflow.attributes.InvisibleNeighbourAttribute;
import at.ssw.visualizer.dataflow.attributes.TBExpandAllAttribute;
import at.ssw.visualizer.dataflow.attributes.TBShowBlockAttribute;
import at.ssw.visualizer.dataflow.graph.InscribeNodeWidget;
import at.ssw.visualizer.dataflow.graph.InstructionNodeWidget;
import at.ssw.dataflow.layout.CompoundForceLayouter;
import at.ssw.dataflow.layout.CompoundHierarchicalNodesLayouter;
import at.ssw.dataflow.layout.ExternalGraphLayoutWrapper;
import at.ssw.dataflow.layout.ForceLayouter;
import at.ssw.dataflow.layout.HierarchicalNodesLayouter;
import at.ssw.visualizer.dataflow.graph.InstructionNodeGraphScene;
import at.ssw.visualizer.dataflow.instructions.Instruction;
import at.ssw.visualizer.dataflow.instructions.InstructionSet;
import at.ssw.visualizer.dataflow.instructions.InstructionSetGenerator;
import at.ssw.dataflow.layout.MagneticSpringForceLayouter;
import at.ssw.dataflow.options.OptionEditor;
import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.core.selection.SelectionProvider;
import at.ssw.visualizer.model.cfg.State;
import at.ssw.visualizer.model.cfg.StateEntry;
import at.ssw.visualizer.dataflow.graph.InstructionSceneListener;
import at.ssw.visualizer.model.cfg.IRInstruction;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import org.openide.awt.Toolbar;
import org.openide.util.ImageUtilities;
import org.openide.windows.CloneableTopComponent;
import org.openide.windows.TopComponent;

import org.apache.batik.dom.GenericDOMImplementation;
import org.apache.batik.svggen.SVGGeneratorContext;
import org.apache.batik.svggen.SVGGraphics2D;
import org.w3c.dom.DOMImplementation;
import java.io.*;
import java.awt.Graphics2D;

/**
 * Top component which displays the data-flow via the netbeans graph framework.
 *
 * @author Stefan Loidl
 */
final public class DataFlowEditorTopComponent extends CloneableTopComponent implements SelectionProvider {
    //Scene visualizing the data-flow
    private InstructionNodeGraphScene scene = null;

    //Datasources of the editor
    private ControlFlowGraph cfg;

    // Management of the application global selection
    private Selection selection;
    private boolean selectionUpdating;
    private BasicBlock[] curBlocks;

    //Buttons
    private JButton BUTHideConstant;
    private JToggleButton BUTShowBlocks;
    private JToggleButton BUTExpandAll;
    private JButton BUTHideParameter;
    private JToggleButton BUTUseHierLayout;
    private JToggleButton BUTUseCompoundLayout;
    private JToggleButton BUTUseForceLayout;
    private JToggleButton BUTUseDirectedForceLayout;
    private JToggleButton BUTUseCompoundForceLayout;
    private JToggleButton BUTUseHighlightClustering;
    private JToggleButton BUTForceAutoLayout;
    private JToggleButton BUTClusterLinkGrayed;
    private JToggleButton BUTLayoutInvisibleNodes;
    private JToggleButton BUTClusterBorderVisible;
    private JToggleButton BUTUseNodeAnimation;
    private JToggleButton BUTUseCurrentNodePosition;
    private JButton BUTDoLayout;
    private JButton BUTLayoutOptions;
    private JButton BUTShowConstant;
    private JButton BUTShowParameter;
    private JButton BUTShowAll;
    private JButton BUTHideAll;
    private JButton BUTShowPhi;
    private JButton BUTHidePhi;
    private JButton BUTShowOperation;
    private JButton BUTHideOperation;
    private JButton BUTExportGraph;
    private JToggleButton BUTUseAdvancedHierLayout;

    //Button tooltips
    private static final String ZOOMIN = "Zoom In";
    private static final String ZOOMOUT = "Zoom Out";
    private static final String HIDECONSTANT = "Hide Constants";
    private static final String SHOWBLOCKS = "Show Blocks";
    private static final String EXPANDALL = "Expand All";
    private static final String HIDEPARAMETER = "Hide Parameter";
    private static final String DOLAYOUT = "Do Layout";
    private static final String USEHIERLAYOUT = "Use Hierachical Layout";
    private static final String USECOMPLAYOUT = "Use Compound Layout";
    private static final String USEFORCELAYOUT = "Use Force Layout";
    private static final String USEDIRECTEDFORCELAYOUT = "Use Directed Force Layout";
    private static final String USECOMPOUNDFORCELAYOUT = "Use Compound Force Layout";
    private static final String USEHIGHLIGHTCLUSTERING = "Use Highlight Clustering";
    private static final String FORCEAUTOLAYOUT = "Force Auto Layout";
    private static final String CLUSTERLINKGRAYED = "Cluster Link Grayed";
    private static final String LAYOUTOPTIONS = "Layout Options...";
    private static final String LAYOUTINVISIBLENODES = "Layout Invisible Nodes";
    private static final String CLUSTERBORDERVISIBLE = "Cluster Border Visible";
    private static final String USENODEANIMATION = "Use Node Animation";
    private static final String USECURRENTNODEPOSITION = "The next layout cycle builds on the current node position";
    private static final String SHOWCONSTANT = "Show Constants";
    private static final String SHOWPARAMETER = "Show Parameter";
    private static final String SHOWALL = "Show All";
    private static final String HIDEALL = "Hide All";
    private static final String SHOWPHI = "Show Phi Functions";
    private static final String HIDEPHI = "Hide Phi Functions";
    private static final String SHOWOPERATION = "Show Operations";
    private static final String HIDEOPERATION = "Hide Operations";
    private static final String EXPORTGRAPH = "Export Graph To...";
    private static final String USEADVANCEDHIERLAYOUT = "Use Advanced Hierachical Layout";

    //Layouter
    private CompoundHierarchicalNodesLayouter compoundLayouter;
    private HierarchicalNodesLayouter hierarchicalLayouter;
    private ForceLayouter forceLayouter;
    private CompoundForceLayouter compoundForceLayouter;
    private MagneticSpringForceLayouter directedForceLayouter;
    private ExternalGraphLayoutWrapper advancedhierarchicalLayouter;

    /** path to the icon used by the component and its open action */
    private static final String ICON_PATH = "at/ssw/visualizer/dataflow/icons/";
    private static final String ICON_ZOOMIN = "zoomin.gif";
    private static final String ICON_ZOOMOUT = "zoomout.gif";
    private static final String ICON_EDITOR = "dfg.gif";
    private static final String ICON_HIRACHICALLAYOUT = "arrangehier.gif";
    private static final String ICON_HIRACHICALLAYOUTADVANCED = "arrangehieradvanced.gif";
    private static final String ICON_FORCELAYOUT = "arrangeforce.gif";
    private static final String ICON_DIRECTEDFORCELAYOUT = "directedforce.gif";
    private static final String ICON_HIRACHICALLAYOUTCL = "arrangehiercluster.gif";
    private static final String ICON_FORCELAYOUTCL = "arrangeforcecluster.gif";
    private static final String ICON_EXPANDALL = "expall.gif";
    private static final String ICON_EXPANDBLOCKS = "expblocks.gif";
    private static final String ICON_OPTIONS = "options.gif";
    private static final String ICON_LAYOUT = "layout.gif";
    private static final String ICON_AUTOLAYOUT = "autolayout.gif";
    private static final String ICON_LAYOUTINV = "layoutinvisible.gif";
    private static final String ICON_ANIMATE = "animate.gif";
    private static final String ICON_CLUSTERHIGH = "clusterhigh.gif";
    private static final String ICON_LINKGRAY = "linkgrayed.gif";
    private static final String ICON_CLUSTERB = "cluster.gif";
    private static final String ICON_HIDEPARAM = "hideparam.gif";
    private static final String ICON_HIDECONSTANT = "hideconst.gif";
    private static final String ICON_SHOWPARAM = "showparam.gif";
    private static final String ICON_SHOWCONSTANT = "showconst.gif";
    private static final String ICON_HIDEALL = "hideall.gif";
    private static final String ICON_SHOWALL = "showall.gif";
    private static final String ICON_HIDEPHI = "hidephi.gif";
    private static final String ICON_SHOWPHI = "showphi.gif";
    private static final String ICON_HIDEOPERATION = "hideoperation.gif";
    private static final String ICON_SHOWOPERATION = "showoperation.gif";
    private static final String ICON_CURRENTNODE = "currentnode.gif";
    private static final String ICON_EXPORTGRAPH = "disk.gif";

    //This view is used for the export of vector graphics
    private JScrollPane scenePane;


    /**
     * Constructor
     */
    public DataFlowEditorTopComponent(ControlFlowGraph cfg) {
        this.cfg = cfg;

        setIcon(ImageUtilities.loadImage(ICON_PATH + ICON_EDITOR, true));
        setName(cfg.getCompilation().getShortName());
        setToolTipText(cfg.getCompilation().getMethod() + " - " + cfg.getName());

        scene = new InstructionNodeGraphScene();
        scene.addInstructionSceneListener(instructionSceneListener);

        selection = new Selection();
        selection.put(cfg);
        selection.put(scene);
        selection.addChangeListener(selectionListener);

        scenePane = new JScrollPane(scene.createView());
        scenePane.setBorder(BorderFactory.createEmptyBorder());
        scenePane.setViewportBorder(BorderFactory.createEmptyBorder());
        JPanel scenePanel = new JPanel();
        scenePanel.setLayout(new BorderLayout());
        scenePanel.add(scenePane, BorderLayout.CENTER);

        //create the layouters
        hierarchicalLayouter = new HierarchicalNodesLayouter();
        compoundLayouter = new CompoundHierarchicalNodesLayouter();
        forceLayouter = new ForceLayouter();
        compoundForceLayouter = new CompoundForceLayouter();
        directedForceLayouter = new MagneticSpringForceLayouter();
        advancedhierarchicalLayouter = new ExternalGraphLayoutWrapper(new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.NONE), false, false, false);

        scenePanel.add(createToolbar(), BorderLayout.NORTH);

        setLayout(new BorderLayout());
        add(scenePanel, BorderLayout.CENTER);
    }

    public Selection getSelection() {
        return selection;
    }

    private ChangeListener selectionListener = new ChangeListener() {
        public void stateChanged(ChangeEvent event) {
            if (selectionUpdating) {
                return;
            }
            selectionUpdating = true;

            BasicBlock[] newBlocks = selection.get(BasicBlock[].class);
            if (newBlocks != null && newBlocks.length > 0 && !Arrays.equals(curBlocks, newBlocks)) {
                HashSet<Instruction> instructions = new HashSet<Instruction>();
                for (BasicBlock b : newBlocks) {
                    for (IRInstruction i : b.getHirInstructions()) {
                        InstructionNodeWidget nw = scene.getNodeWidget(i.getValue(IRInstruction.HIR_NAME));
                        //Some instructions are within the states of more than one block
                        if (nw != null) {
                            instructions.add(nw.getInstruction());
                        }
                    }
                    for (State s : b.getStates()) {
                        for (StateEntry se : s.getEntries()) {
                            InstructionNodeWidget nw = scene.getNodeWidget(se.getName());
                            //Some instructions are within the states of more than one block
                            if (nw != null && b.getName() != null && b.getName().equals(nw.getInstruction().getSourceBlock())) {
                                instructions.add(nw.getInstruction());
                            }
                        }
                    }
                }
                scene.setSelectedObjects(instructions);
                scene.refreshAll();
                scene.validate();
            }
            curBlocks = newBlocks;
            selectionUpdating = false;
        }
    };


    private InstructionSceneListener instructionSceneListener = new InstructionSceneListener() {
        public void selectionChanged(Set<InstructionNodeWidget> w) {
            if (selectionUpdating) {
                return;
            }
            selectionUpdating = true;

            List<BasicBlock> newBlocks = new ArrayList<BasicBlock>();
            for (InstructionNodeWidget widget : w) {
                String name = widget.getInstruction().getSourceBlock();
                //Sometimes block values are not filled
                if (name == null) {
                    continue;
                }
                BasicBlock b = cfg.getBasicBlockByName(name);
                if (b != null) {
                    newBlocks.add(b);
                }
            }

            curBlocks = newBlocks.toArray(new BasicBlock[newBlocks.size()]);
            selection.put(curBlocks);
            selectionUpdating = false;
        }

        //Not used here
        public void doubleClicked(InstructionNodeWidget w) {
        }
        public void updateNodeData() {
        }
    };


    /**
     * This method activates the data source of the viewer.
     * BasicBlocks are used as basic granularity for this
     * reason. The Scene is also built within this method.
     */
    private void activateDataSource() {
        scene.validate();

        InstructionSet iset = InstructionSetGenerator.generateFromBlocks(cfg.getBasicBlocks());
        Instruction[] instructions = iset.getInstructions();
        //No control flow instructions are shown in the graph
        instructions = InstructionSet.filterInstructionType(instructions, Instruction.InstructionType.CONTROLFLOW);

        scene.addInstructions(instructions);

        for (Instruction inst : instructions) {
            InstructionNodeWidget inw = scene.getNodeWidget(inst.getID());

            //Hide Constants
            if (inst.getInstructionType() == Instruction.InstructionType.CONSTANT) {
                //inw.addNodeAttribute(new TBInvisibilityAttribute(BUTHideConstant));
                //Show constants in successor nodes
                for (Instruction i : inst.getSuccessors()) {
                    InstructionNodeWidget widget = scene.getNodeWidget(i.getID());
                    if (widget != null) {
                        InscribeNodeWidget newW = new InscribeNodeWidget(inst, scene);
                        widget.addNodeAttribute(new InvisibleNeighbourAttribute(newW, inw, false));
                    }
                }
            }

            //Hide Parameters
            if (inst.getInstructionType() == Instruction.InstructionType.PARAMETER) {
                //inw.addNodeAttribute(new TBInvisibilityAttribute(BUTHideParameter));
                //Show constants in successor nodes
                for (Instruction i : inst.getSuccessors()) {
                    InstructionNodeWidget widget = scene.getNodeWidget(i.getID());
                    if (widget != null) {
                        InscribeNodeWidget newW = new InscribeNodeWidget(inst, scene);
                        widget.addNodeAttribute(new InvisibleNeighbourAttribute(newW, inw, false));
                    }
                }
            }

            //Show Blocks
            inw.addNodeAttribute(new TBShowBlockAttribute(BUTShowBlocks));

            //Show Instructionstring
            inw.addNodeAttribute(new TBExpandAllAttribute(BUTExpandAll));
        }

        //Assign standard layouter
        scene.setExternalLayouter(hierarchicalLayouter);
        scene.refreshAll();
        scene.layout();
        scene.validate();
    }

    @Override
    protected void componentOpened() {
        super.componentOpened();
        activateDataSource();
    }

    /* overwritten from parent- called if editor is activated*/
    @Override
    protected void componentActivated() {
        super.componentActivated();
        SelectionManager.getDefault().setSelection(selection);
    }

    /* overwritten from parent- called if editor is closed*/
    @Override
    protected void componentClosed() {
        super.componentClosed();
        SelectionManager.getDefault().removeSelection(selection);
    }


    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    protected CloneableTopComponent createClonedObject() {
        return new DataFlowEditorTopComponent(cfg);
    }

    // <editor-fold defaultstate="collapsed" desc=" Toolbar creation & helper functions ">
    /** Creates the toolbar */
    private Toolbar createToolbar() {
        Toolbar toolBar = new Toolbar();
        toolBar.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border"));

        JButton zoomIn = new JButton();
        zoomIn.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_ZOOMIN, true)));
        zoomIn.setToolTipText(ZOOMIN);
        zoomIn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                scene.setZoomFactor(scene.getZoomFactor() * 1.2);
                scene.validate();
            }
        });

        JButton zoomOut = new JButton();
        zoomOut.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_ZOOMOUT, true)));
        zoomOut.setToolTipText(ZOOMOUT);
        zoomOut.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                scene.setZoomFactor(scene.getZoomFactor() / 1.2);
                scene.validate();
            }
        });

        BUTShowAll = new JButton();
        BUTShowAll.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_SHOWALL, true)));
        BUTShowAll.setToolTipText(SHOWALL);
        BUTShowAll.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(true, null);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTHideAll = new JButton();
        BUTHideAll.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIDEALL, true)));
        BUTHideAll.setToolTipText(HIDEALL);
        BUTHideAll.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(false, null);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTHidePhi = new JButton();
        BUTHidePhi.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIDEPHI, true)));
        BUTHidePhi.setToolTipText(HIDEPHI);
        BUTHidePhi.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(false, Instruction.InstructionType.PHI);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTShowPhi = new JButton();
        BUTShowPhi.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_SHOWPHI, true)));
        BUTShowPhi.setToolTipText(SHOWPHI);
        BUTShowPhi.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(true, Instruction.InstructionType.PHI);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTShowOperation = new JButton();
        BUTShowOperation.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_SHOWOPERATION, true)));
        BUTShowOperation.setToolTipText(SHOWOPERATION);
        BUTShowOperation.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(true, Instruction.InstructionType.OPERATION);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTHideOperation = new JButton();
        BUTHideOperation.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIDEOPERATION, true)));
        BUTHideOperation.setToolTipText(HIDEOPERATION);
        BUTHideOperation.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(false, Instruction.InstructionType.OPERATION);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTShowConstant = new JButton();
        BUTShowConstant.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_SHOWCONSTANT, true)));
        BUTShowConstant.setToolTipText(SHOWCONSTANT);
        BUTShowConstant.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(true, Instruction.InstructionType.CONSTANT);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTShowParameter = new JButton();
        BUTShowParameter.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_SHOWPARAM, true)));
        BUTShowParameter.setToolTipText(SHOWPARAMETER);
        BUTShowParameter.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(true, Instruction.InstructionType.PARAMETER);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTHideConstant = new JButton();
        BUTHideConstant.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIDECONSTANT, true)));
        BUTHideConstant.setToolTipText(HIDECONSTANT);
        BUTHideConstant.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(false, Instruction.InstructionType.CONSTANT);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTShowBlocks = new JToggleButton();
        BUTShowBlocks.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_EXPANDBLOCKS, true)));
        BUTShowBlocks.setToolTipText(SHOWBLOCKS);
        BUTShowBlocks.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTExpandAll = new JToggleButton();
        BUTExpandAll.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_EXPANDALL, true)));
        BUTExpandAll.setToolTipText(EXPANDALL);
        BUTExpandAll.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTHideParameter = new JButton();
        BUTHideParameter.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIDEPARAM, true)));
        BUTHideParameter.setToolTipText(HIDEPARAMETER);
        BUTHideParameter.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.handleSetVisibilityNodeType(false, Instruction.InstructionType.PARAMETER);
                scene.refreshAll();
                scene.validate();
                scene.autoLayout();
            }
        });

        BUTDoLayout = new JButton();
        BUTDoLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_LAYOUT, true)));
        BUTDoLayout.setToolTipText(DOLAYOUT);
        BUTDoLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                scene.layout();
                scene.refreshAll();
                scene.validate();
            }
        });

        BUTLayoutOptions = new JButton();
        BUTLayoutOptions.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_OPTIONS, true)));
        BUTLayoutOptions.setToolTipText(LAYOUTOPTIONS);
        BUTLayoutOptions.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                OptionEditor ed = new OptionEditor(scene.getExternalLayouter());
                ed.setVisible(true);
                scene.refreshAll();
                scene.validate();
                scene.layout();
            }
        });

        BUTUseHierLayout = new JToggleButton();
        BUTUseHierLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIRACHICALLAYOUT, true)));
        BUTUseHierLayout.setToolTipText(USEHIERLAYOUT);
        BUTUseHierLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });
        //Default selected button
        BUTUseHierLayout.setSelected(true);

        BUTUseAdvancedHierLayout = new JToggleButton();
        BUTUseAdvancedHierLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIRACHICALLAYOUTADVANCED, true)));
        BUTUseAdvancedHierLayout.setToolTipText(USEADVANCEDHIERLAYOUT);
        BUTUseAdvancedHierLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });


        //BUTTON: Use Compound Layout
        BUTUseCompoundLayout = new JToggleButton();
        BUTUseCompoundLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_HIRACHICALLAYOUTCL, true)));
        BUTUseCompoundLayout.setToolTipText(USECOMPLAYOUT);
        BUTUseCompoundLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });

        //BUTTON: Use Force Layout
        BUTUseForceLayout = new JToggleButton();
        BUTUseForceLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_FORCELAYOUT, true)));
        BUTUseForceLayout.setToolTipText(USEFORCELAYOUT);
        BUTUseForceLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });

        //BUTTON: Use Directed Force Layout
        BUTUseDirectedForceLayout = new JToggleButton();
        BUTUseDirectedForceLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_DIRECTEDFORCELAYOUT, true)));
        BUTUseDirectedForceLayout.setToolTipText(USEDIRECTEDFORCELAYOUT);
        BUTUseDirectedForceLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });

        //BUTTON: Use Compound Force Layout
        BUTUseCompoundForceLayout = new JToggleButton();
        BUTUseCompoundForceLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_FORCELAYOUTCL, true)));
        BUTUseCompoundForceLayout.setToolTipText(USECOMPOUNDFORCELAYOUT);
        BUTUseCompoundForceLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                changeLayouter((JToggleButton) e.getSource());
            }
        });

        //BUTTON: Highlight Clustering
        BUTUseHighlightClustering = new JToggleButton();
        BUTUseHighlightClustering.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_CLUSTERHIGH, true)));
        BUTUseHighlightClustering.setToolTipText(USEHIGHLIGHTCLUSTERING);
        BUTUseHighlightClustering.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTUseHighlightClustering.isSelected();
                scene.setHighlightClustering(b);
                scene.layout();
            }
        });
        BUTUseHighlightClustering.setEnabled(false);

        //BUTTON: Auto Layout
        BUTForceAutoLayout = new JToggleButton();
        BUTForceAutoLayout.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_AUTOLAYOUT, true)));
        BUTForceAutoLayout.setToolTipText(FORCEAUTOLAYOUT);
        BUTForceAutoLayout.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTForceAutoLayout.isSelected();
                scene.setAutoLayout(b);
            }
        });
        BUTForceAutoLayout.setSelected(scene.isAutoLayout());


        //BUTTON: Cluster Link Grayed
        BUTClusterLinkGrayed = new JToggleButton();
        BUTClusterLinkGrayed.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_LINKGRAY, true)));
        BUTClusterLinkGrayed.setToolTipText(CLUSTERLINKGRAYED);
        BUTClusterLinkGrayed.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTClusterLinkGrayed.isSelected();
                scene.setInterClusterLinkGrayed(b);
                scene.validate();
            }
        });
        BUTClusterLinkGrayed.setSelected(scene.isInterClusterLinkGrayed());
        BUTClusterLinkGrayed.setEnabled(false);

        //BUTTON: Cluster Border Visible
        BUTClusterBorderVisible = new JToggleButton();
        BUTClusterBorderVisible.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_CLUSTERB, true)));
        BUTClusterBorderVisible.setToolTipText(CLUSTERBORDERVISIBLE);
        BUTClusterBorderVisible.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTClusterBorderVisible.isSelected();
                scene.setClusterBordersVisible(b);
                scene.validate();
            }
        });
        BUTClusterBorderVisible.setSelected(scene.isClusterBordersVisible());
        BUTClusterBorderVisible.setEnabled(false);

        //BUTTON: Layout Invisible Nodes
        BUTLayoutInvisibleNodes = new JToggleButton();
        BUTLayoutInvisibleNodes.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_LAYOUTINV, true)));
        BUTLayoutInvisibleNodes.setToolTipText(LAYOUTINVISIBLENODES);
        BUTLayoutInvisibleNodes.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTLayoutInvisibleNodes.isSelected();
                scene.setLayoutInvisibleNodes(b);
                scene.layout();
                scene.validate();
            }
        });
        BUTLayoutInvisibleNodes.setSelected(scene.isLayoutInvisibleNodes());

        //BUTTON: Use Node Animation
        BUTUseNodeAnimation = new JToggleButton();
        BUTUseNodeAnimation.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_ANIMATE, true)));
        BUTUseNodeAnimation.setToolTipText(USENODEANIMATION);
        BUTUseNodeAnimation.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTUseNodeAnimation.isSelected();
                scene.setNodeAnimation(b);
            }
        });
        BUTUseNodeAnimation.setSelected(scene.isNodeAnimation());

        //BUTTON: Use current node position
        BUTUseCurrentNodePosition = new JToggleButton();
        BUTUseCurrentNodePosition.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_CURRENTNODE, true)));
        BUTUseCurrentNodePosition.setToolTipText(USECURRENTNODEPOSITION);
        BUTUseCurrentNodePosition.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                boolean b = BUTUseCurrentNodePosition.isSelected();
                scene.setUseCurrentNodePositions(b);
            }
        });
        BUTUseCurrentNodePosition.setSelected(scene.isUseCurrentNodePositions());

        // Button: Export graph
        BUTExportGraph = new JButton();
        BUTExportGraph.setIcon(new ImageIcon(ImageUtilities.loadImage(ICON_PATH + ICON_EXPORTGRAPH, true)));
        BUTExportGraph.setToolTipText(EXPORTGRAPH);
        BUTExportGraph.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                JFileChooser fc = new JFileChooser();
                fc.setAcceptAllFileFilterUsed(false);
                fc.setDialogTitle(EXPORTGRAPH);
                fc.addChoosableFileFilter(new SimpleFileFilter("Scaleable Vector Format (*.svg)", ".svg"));
                fc.addChoosableFileFilter(new SimpleFileFilter("Graph Modelling Language (*.gml)", ".gml"));
                fc.setMultiSelectionEnabled(false);
                fc.showSaveDialog(scenePane);
                //file selected?
                if (fc.getSelectedFile() != null) {
                    String fileName = fc.getSelectedFile().getAbsolutePath().toLowerCase();
                    SimpleFileFilter filter = (SimpleFileFilter) fc.getFileFilter();
                    if (!fileName.endsWith(filter.getFilter())) {
                        fileName += filter.getFilter();
                    }

                    if (fileName.endsWith("svg")) {
                        File f = new File(fileName);
                        DOMImplementation dom = GenericDOMImplementation.getDOMImplementation();
                        org.w3c.dom.Document document = dom.createDocument("http://www.w3.org/2000/svg", "svg", null);
                        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document);
                        ctx.setEmbeddedFontsOn(true);
                        Graphics2D svgGenerator = new SVGGraphics2D(ctx, true);
                        scenePane.paint(svgGenerator);
                        FileOutputStream os = null;
                        try {
                            os = new FileOutputStream(f);
                            Writer out = new OutputStreamWriter(os, "UTF-8");
                            assert svgGenerator instanceof SVGGraphics2D;
                            SVGGraphics2D svgGraphics = (SVGGraphics2D)svgGenerator;
                            svgGraphics.stream(out, true);
                        } catch (IOException exc) {
                            // NotifyDescriptor message = new NotifyDescriptor.Message(
                            //                                                         Bundle.EXPORT_BATIK_ErrorExportingSVG(e.getLocalizedMessage()), NotifyDescriptor.ERROR_MESSAGE);
                            // DialogDisplayer.getDefault().notifyLater(message);
                        } finally {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (IOException exc) {
                                }
                            }
                        }
                    }

                    if (fileName.endsWith("gml")) {
                        try {
                            File f = new File(fileName);
                            scene.exportToGMLFile(f);
                        } catch (Exception ex) {
                        }
                    }
                }
            }
        });

        toolBar.add(zoomIn);
        toolBar.add(zoomOut);
        toolBar.addSeparator();

        //  JLabel lab=new JLabel("Hide:");
        // Font font=lab.getFont().deriveFont(Font.BOLD);
        // lab.setFont(font);
        //  toolBar.add(lab);
        toolBar.add(BUTHideConstant);
        toolBar.add(BUTHideParameter);
        toolBar.add(BUTHidePhi);
        toolBar.add(BUTHideOperation);
        toolBar.add(BUTHideAll);

        toolBar.addSeparator();

        toolBar.add(BUTShowConstant);
        toolBar.add(BUTShowParameter);
        toolBar.add(BUTShowPhi);
        toolBar.add(BUTShowOperation);
        toolBar.add(BUTShowAll);

        toolBar.addSeparator();

        // lab=new JLabel("Expand:");
        // lab.setFont(font);
        // toolBar.add(lab);
        toolBar.add(BUTShowBlocks);
        toolBar.add(BUTExpandAll);
        toolBar.addSeparator();

        // lab=new JLabel("Layout:");
        // lab.setFont(font);
        //toolBar.add(lab);
        toolBar.add(BUTUseHierLayout);
        toolBar.add(BUTUseCompoundLayout);
        toolBar.add(BUTUseForceLayout);
        toolBar.add(BUTUseDirectedForceLayout);
        toolBar.add(BUTUseCompoundForceLayout);
        // [cwi] temporarily removed until new implementation of algorithm is integrated
        // toolBar.add(BUTUseAdvancedHierLayout);
        toolBar.addSeparator();

        // lab=new JLabel("Misc:");
        // lab.setFont(font);
        //toolBar.add(lab);
        toolBar.add(BUTDoLayout);
        toolBar.add(BUTForceAutoLayout);
        toolBar.add(BUTUseCurrentNodePosition);
        toolBar.add(BUTLayoutOptions);
        toolBar.add(BUTLayoutInvisibleNodes);
        toolBar.add(BUTUseNodeAnimation);
        toolBar.addSeparator();

        //   lab=new JLabel("Clustering:");
        //  lab.setFont(font);
        //toolBar.add(lab);
        toolBar.add(BUTUseHighlightClustering);
        toolBar.add(BUTClusterLinkGrayed);
        toolBar.add(BUTClusterBorderVisible);
        toolBar.addSeparator();

        toolBar.add(BUTExportGraph);

        return toolBar;
    }

    /** Used if one of the layouter buttons was activated */
    private void changeLayouter(JToggleButton sender) {
        BUTUseHierLayout.setSelected(false);
        BUTUseCompoundLayout.setSelected(false);
        BUTUseForceLayout.setSelected(false);
        BUTUseCompoundForceLayout.setSelected(false);
        BUTUseDirectedForceLayout.setSelected(false);
        BUTUseAdvancedHierLayout.setSelected(false);
        //doesn't trigger a action event
        sender.setSelected(true);

        if (sender == BUTUseHierLayout) {
            scene.setExternalLayouter(hierarchicalLayouter);
        }

        if (sender == BUTUseCompoundLayout) {
            scene.setExternalLayouter(compoundLayouter);
        }
        if (sender == BUTUseForceLayout) {
            scene.setExternalLayouter(forceLayouter);
        }
        if (sender == BUTUseCompoundForceLayout) {
            scene.setExternalLayouter(compoundForceLayouter);
        }
        if (sender == BUTUseAdvancedHierLayout) {
            scene.setExternalLayouter(advancedhierarchicalLayouter);
        }
        if (sender == BUTUseDirectedForceLayout) {
            scene.setExternalLayouter(directedForceLayouter);
        }
        boolean clustering = scene.getExternalLayouter().isClusteringSupported();
        BUTUseHighlightClustering.setEnabled(clustering);
        BUTClusterLinkGrayed.setEnabled(clustering);
        BUTClusterBorderVisible.setEnabled(clustering);

        scene.refreshAll();
        scene.layout();
        scene.validate();
    }

/**
     * Simple file filter implementation that filters all files that are no
     * diretories and do not end with a specified filter string.
     * This string can be used to get all files with specified type.
     */
    class SimpleFileFilter extends FileFilter {

        private String desc = null;
        private String filter = null;

        public SimpleFileFilter(String desc, String filter) {
            this.filter = filter.toLowerCase();
            this.desc = desc;
        }

        public boolean accept(File f) {
            if (f == null) {
                return false;
            }
            if (f.getName().toLowerCase().endsWith(filter)) {
                return true;
            }
            if (f.isDirectory()) {
                return true;
            }
            return false;
        }

        public String getDescription() {
            return desc;
        }

        public String getFilter() {
            return filter;
        }
    }
    // </editor-fold>
}
