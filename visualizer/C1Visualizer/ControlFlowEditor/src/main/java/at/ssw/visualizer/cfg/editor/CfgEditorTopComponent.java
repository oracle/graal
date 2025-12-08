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
package at.ssw.visualizer.cfg.editor;

import at.ssw.visualizer.cfg.action.ShowAllAction;
import at.ssw.visualizer.cfg.action.ColorAction;
import at.ssw.visualizer.cfg.action.ExportAction;
import at.ssw.visualizer.cfg.action.HideEdgesAction;
import at.ssw.visualizer.cfg.action.HierarchicalCompoundLayoutAction;
import at.ssw.visualizer.cfg.action.HierarchicalNodeLayoutAction;
import at.ssw.visualizer.cfg.action.ShowEdgesAction;
import at.ssw.visualizer.cfg.action.SwitchLoopClustersAction;
import at.ssw.visualizer.cfg.action.UseBezierRouterAction;
import at.ssw.visualizer.cfg.action.UseDirectLineRouterAction;
import at.ssw.visualizer.cfg.action.ZoominAction;
import at.ssw.visualizer.cfg.action.ZoomoutAction;
import at.ssw.visualizer.cfg.graph.CfgEventListener;
import at.ssw.visualizer.cfg.graph.CfgScene;
import at.ssw.visualizer.cfg.graph.EdgeWidget;
import at.ssw.visualizer.cfg.graph.NodeWidget;
import at.ssw.visualizer.cfg.model.CfgEdge;
import at.ssw.visualizer.cfg.model.CfgNode;
import at.ssw.visualizer.cfg.preferences.CfgPreferences;
import at.ssw.visualizer.cfg.preferences.FlagsSetting;
import javax.swing.ScrollPaneConstants;
import at.ssw.visualizer.core.selection.Selection;
import at.ssw.visualizer.core.selection.SelectionManager;
import at.ssw.visualizer.core.selection.SelectionProvider;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import java.awt.BorderLayout;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.Border;
import org.netbeans.api.visual.widget.Widget;
import org.openide.awt.Toolbar;
import org.openide.util.ImageUtilities;
import org.openide.windows.CloneableTopComponent;
import org.openide.windows.TopComponent;
import org.openide.util.actions.SystemAction;

public class CfgEditorTopComponent extends CloneableTopComponent implements PropertyChangeListener, SelectionProvider {

    private CfgScene scene;
    private JScrollPane jScrollPane;
    private ControlFlowGraph cfg;
    private JComponent myView;
    private Selection selection;

    public CfgEditorTopComponent(ControlFlowGraph cfg) {
        this.cfg = cfg;

        setIcon(ImageUtilities.loadImage("at/ssw/visualizer/cfg/icons/cfg.gif"));
        setName(cfg.getParent().getShortName());
        setToolTipText(cfg.getCompilation().getMethod() + " - " + cfg.getName());

        //panel setup
        this.jScrollPane = new JScrollPane();
        this.jScrollPane.setOpaque(true);
        this.jScrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.jScrollPane.setViewportBorder(BorderFactory.createEmptyBorder());
        this.scene = new CfgScene(this);
        this.myView = scene.createView();
        this.jScrollPane.setViewportView(myView);
        this.setLayout(new BorderLayout());
        this.add(createToolbar(), BorderLayout.NORTH);
        this.add(jScrollPane, BorderLayout.CENTER);
        jScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        jScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        jScrollPane.getVerticalScrollBar().setEnabled(true);
        jScrollPane.getHorizontalScrollBar().setEnabled(true);

        //setup enviroment,register listeners
        selection = new Selection();
        selection.put(cfg);
        selection.put(scene);
        selection.addChangeListener(scene);

        scene.validate();
        scene.applyLayout();
    }

    public Selection getSelection() {
        return selection;
    }

    public ControlFlowGraph getCfg() {
        return cfg;
    }

    public JScrollPane getJScrollPanel() {
        return jScrollPane;
    }

    public CfgScene getCfgScene() {
        return scene;
    }

    @Override
    public int getPersistenceType() {
        return TopComponent.PERSISTENCE_NEVER;
    }

    @Override
    protected void componentOpened() {
        super.componentOpened();
        CfgPreferences.getInstance().addPropertyChangeListener(this);
    }

    @Override
    protected void componentActivated() {
        super.componentActivated();
        SelectionManager.getDefault().setSelection(selection);
        this.getCfgScene().updateGlobalSelection();
        this.getCfgScene().fireSelectionChanged();
    }

    @Override
    protected void componentClosed() {
        super.componentClosed();
        SelectionManager.getDefault().removeSelection(selection);
        CfgPreferences.getInstance().removePropertyChangeListener(this);
    }

    @Override
    protected CloneableTopComponent createClonedObject() {
        CfgEditorTopComponent component = new CfgEditorTopComponent(cfg);
        component.setActivatedNodes(getActivatedNodes());
        return component;
    }

    public void propertyChange(PropertyChangeEvent evt) {
        if (this.scene != null) {

            String propName = evt.getPropertyName();
            CfgPreferences prefs = CfgPreferences.getInstance();
            if (propName.equals(CfgPreferences.PROP_BACKGROUND_COLOR)) {
                scene.setBackground(prefs.getBackgroundColor());
                scene.revalidate();
            } else if (propName.equals(CfgPreferences.PROP_NODE_COLOR)) {
                for (NodeWidget nw : scene.getNodeWidgets()) {
                    //only change the node color if its not a custom color
                    if (!nw.isNodeColorCustomized()) {
                        nw.setNodeColor(prefs.getNodeColor(), false);
                    }
                }
            } else if (propName.equals(CfgPreferences.PROP_EDGE_COLOR)) {
                for (CfgEdge e : scene.getEdges()) {
                    if (!e.isBackEdge() && !e.isXhandler()) {
                        EdgeWidget w = (EdgeWidget) scene.findWidget(e);
                        w.setLineColor(prefs.getEdgeColor());
                    }
                }
            } else if (propName.equals(CfgPreferences.PROP_BACK_EDGE_COLOR)) {
                for (CfgEdge e : scene.getEdges()) {
                    if (e.isBackEdge()) {
                        EdgeWidget w = (EdgeWidget) scene.findWidget(e);
                        w.setLineColor(prefs.getBackedgeColor());
                    }
                }
            } else if (propName.equals(CfgPreferences.PROP_EXCEPTION_EDGE_COLOR)) {
                for (CfgEdge e : scene.getEdges()) {
                    if (e.isXhandler()) {
                        EdgeWidget w = (EdgeWidget) scene.findWidget(e);
                        w.setLineColor(prefs.getExceptionEdgeColor());
                    }
                }
            } else if (propName.equals(CfgPreferences.PROP_BORDER_COLOR)) {
                for (CfgNode n : scene.getNodes()) {
                    NodeWidget nw = (NodeWidget) scene.findWidget(n);
                    nw.setBorderColor(prefs.getBorderColor());
                }
            } else if (propName.equals(CfgPreferences.PROP_TEXT_FONT)) {
                for (CfgNode n : scene.getNodes()) {
                    NodeWidget nw = (NodeWidget) scene.findWidget(n);
                    nw.adjustFont(prefs.getTextFont());
                }
            } else if (propName.equals(CfgPreferences.PROP_TEXT_COLOR)) {
                for (CfgNode n : scene.getNodes()) {
                    NodeWidget nw = (NodeWidget) scene.findWidget(n);
                    nw.setForeground(prefs.getTextColor());
                }
            } else if (propName.equals(CfgPreferences.PROP_FLAGS)) {
                FlagsSetting fs = CfgPreferences.getInstance().getFlagsSetting();
                for (CfgNode n : scene.getNodes()) {
                    NodeWidget nw = (NodeWidget) scene.findWidget(n);
                    Color nodeColor = fs.getColor(n.getBasicBlock().getFlags());
                    if (nodeColor != null) {
                        nw.setNodeColor(nodeColor, true);
                    } else {
                        nw.setNodeColor(CfgPreferences.getInstance().getNodeColor(), false);
                    }
                }
            } else if (propName.equals(CfgPreferences.PROP_SELECTION_COLOR_BG) || propName.equals(CfgPreferences.PROP_SELECTION_COLOR_FG)) {
                for (CfgNode n : scene.getNodes()) {
                    Widget w = scene.findWidget(n);
                    w.revalidate();
                }
            }
            scene.validate();
        }

    }

    private Toolbar createToolbar() {
        Toolbar tb = new Toolbar("CfgToolbar");

        tb.setBorder((Border) UIManager.get("Nb.Editor.Toolbar.border"));

        //zoomin/zoomout buttons
        tb.add(SystemAction.get(ZoominAction.class).getToolbarPresenter());
        tb.add(SystemAction.get(ZoomoutAction.class).getToolbarPresenter());
        tb.addSeparator();

        //router buttons
        ButtonGroup routerButtons = new ButtonGroup();
        UseDirectLineRouterAction direct = SystemAction.get(UseDirectLineRouterAction.class);
        UseBezierRouterAction bezier = SystemAction.get(UseBezierRouterAction.class);
        JToggleButton button = (JToggleButton) direct.getToolbarPresenter();
        button.getModel().setGroup(routerButtons);
        button.setSelected(true);
        tb.add(button);
        button = (JToggleButton) bezier.getToolbarPresenter();
        button.getModel().setGroup(routerButtons);
        tb.add(button);
        tb.addSeparator();

        //layout buttons
        tb.add(SystemAction.get(HierarchicalNodeLayoutAction.class).getToolbarPresenter());
        tb.add(SystemAction.get(HierarchicalCompoundLayoutAction.class).getToolbarPresenter());

        tb.addSeparator();
        tb.add(SystemAction.get(ShowAllAction.class).getToolbarPresenter());
        tb.addSeparator();

        //cluster button
        tb.add(SystemAction.get(SwitchLoopClustersAction.class).getToolbarPresenter());
        tb.addSeparator();

        //show/hide edge button
        tb.add(SystemAction.get(ShowEdgesAction.class).getToolbarPresenter());
        tb.add(SystemAction.get(HideEdgesAction.class).getToolbarPresenter());
        tb.addSeparator();

        //color button       
        JComponent colorButton = SystemAction.get(ColorAction.class).getToolbarPresenter();
        getCfgScene().addCfgEventListener((CfgEventListener) colorButton);
        tb.add(colorButton);

        //export button           
        tb.add(SystemAction.get(ExportAction.class).getToolbarPresenter());
        tb.doLayout();

        return tb;
    }
}
