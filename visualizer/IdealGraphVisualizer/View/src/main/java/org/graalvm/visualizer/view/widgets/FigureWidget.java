/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.view.widgets;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.util.DoubleClickAction;
import org.graalvm.visualizer.util.DoubleClickHandler;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.graalvm.visualizer.view.DiagramScene;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.layout.LayoutFactory;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.LabelWidget;
import org.netbeans.api.visual.widget.Widget;
import org.openide.nodes.AbstractNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;
import org.openide.util.Lookup;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public final class FigureWidget extends Widget implements Properties.Provider, PopupMenuProvider, DoubleClickHandler {
    public static final boolean VERTICAL_LAYOUT = true;
    private static final double LABEL_ZOOM_FACTOR = 0.3;

    private final Figure figure;
    private final Widget middleWidget;
    private final ArrayList<LabelWidget> labelWidgets;
    private final DiagramScene diagramScene;
    private boolean boundary;
    private final Node node;

    public void setBoundary(boolean b) {
        boundary = b;
    }

    public Rectangle getRectangle() {
        Rectangle r = getBounds();
        Point p = getLocation();
        if (r == null || p == null) {
            return null;
        }
        return new Rectangle(p.x, p.y, r.width, r.height);
    }

    public boolean isBoundary() {
        return boundary;
    }

    public Node getNode() {
        return node;
    }

    @Override
    public boolean isHitAt(Point localLocation) {
        return middleWidget.isHitAt(localLocation);
    }

    public FigureWidget(final Figure f, WidgetAction hoverAction, WidgetAction selectAction, DiagramScene scene, Widget parent) {
        super(scene);
        assert this.getScene() != null;
        assert this.getScene().getView() != null;

        this.figure = f;
        this.setCheckClipping(true);
        this.diagramScene = scene;
        this.boundary = f.isBoundary();
        parent.addChild(this);

        middleWidget = new Widget(scene);
        middleWidget.setLayout(LayoutFactory.createVerticalFlowLayout(LayoutFactory.SerialAlignment.CENTER, 0));
        middleWidget.setBackground(f.getColor());
        middleWidget.setOpaque(true);
        middleWidget.getActions().addAction(new DoubleClickAction(this));
        middleWidget.setCheckClipping(true);

        String[] strings = figure.getLines();
        labelWidgets = new ArrayList<>(strings.length);

        for (String displayString : strings) {
            LabelWidget lw = new LabelWidget(scene, displayString);
            labelWidgets.add(lw);
            middleWidget.addChild(lw);
            lw.setFont(Diagram.getFont());
            lw.setForeground(getTextColor());
            lw.setAlignment(LabelWidget.Alignment.CENTER);
            lw.setVerticalAlignment(LabelWidget.VerticalAlignment.CENTER);
            lw.setBorder(BorderFactory.createEmptyBorder());
        }

        middleWidget.setPreferredBounds(new Rectangle(0, 0, f.getWidth(), f.getHeight()));
        this.addChild(middleWidget);

        // Initialize node for property sheet
        node = new AbstractNode(Children.LEAF) {

            @Override
            protected Sheet createSheet() {
                Sheet s = super.createSheet();
                PropertiesSheet.initializeSheet(f.getProperties(), s);
                return s;
            }
        };
        node.setDisplayName(getName());
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);

        Font font = Diagram.getFont();
        if (state.isSelected()) {
            font = Diagram.getBoldFont();
        }

        Color borderColor = Color.BLACK;
        Color innerBorderColor = getFigure().getColor();
        Color[] ret = new Color[]{innerBorderColor};
        figure.getDiagram().render(() -> {
            if (state.isHighlighted()) {
                ret[0] = Color.BLUE;
            }
        });
        if (ret[0] != innerBorderColor) {
            innerBorderColor = borderColor = ret[0];
        }

        middleWidget.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(borderColor, 1), BorderFactory.createLineBorder(innerBorderColor, 1)));
        for (LabelWidget labelWidget : labelWidgets) {
            labelWidget.setFont(font);
        }
        repaint();
    }

    public String getName() {
        return getProperties().getString(PROPNAME_NAME, null);
    }

    @Override
    public Properties getProperties() {
        // XXX FIXME: unsynchronized access to mutable properties; copy is overkill.
        return figure.getProperties();
    }

    public Figure getFigure() {
        return figure;
    }

    // threading: only called from within constructor
    private Color getTextColor() {
        Color bg = figure.getColor();
        double brightness = bg.getRed() * 0.21 + bg.getGreen() * 0.72 + bg.getBlue() * 0.07;
        if (brightness < 150) {
            return Color.WHITE;
        } else {
            return Color.BLACK;
        }
    }

    @Override
    protected void paintChildren() {
        Composite oldComposite = null;
        if (boundary) {
            oldComposite = getScene().getGraphics().getComposite();
            float alpha = DiagramScene.ALPHA;
            this.getScene().getGraphics().setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }

        if (diagramScene.getZoomFactor() < LABEL_ZOOM_FACTOR) {
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(false);
            }
            super.paintChildren();
            for (LabelWidget labelWidget : labelWidgets) {
                labelWidget.setVisible(true);
            }
        } else {
            Color oldColor = null;
            if (boundary) {
                for (LabelWidget labelWidget : labelWidgets) {
                    oldColor = labelWidget.getForeground();
                    labelWidget.setForeground(Color.BLACK);
                }
            }
            super.paintChildren();
            if (boundary) {
                for (LabelWidget labelWidget : labelWidgets) {
                    labelWidget.setForeground(oldColor);
                }
            }
        }

        if (boundary) {
            getScene().getGraphics().setComposite(oldComposite);
        }
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point point) {
        JPopupMenu menu = diagramScene.createPopupMenu();
        menu.addSeparator();

        build(menu, getFigure(), this, false, diagramScene);
        menu.addSeparator();
        build(menu, getFigure(), this, true, diagramScene);

        if (getFigure().getSubgraphs() != null) {
            menu.addSeparator();
            JMenu subgraphs = new JMenu("Subgraphs");
            menu.add(subgraphs);

            final GraphViewer viewer = Lookup.getDefault().lookup(GraphViewer.class);
            for (final InputGraph subgraph : getFigure().getSubgraphs()) {
                Action a = new AbstractAction() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        viewer.view(subgraph, true);
                    }
                };

                a.setEnabled(true);
                a.putValue(Action.NAME, subgraph.getName());
                subgraphs.add(a);
            }
        }

        return menu;
    }

    public static void build(JPopupMenu menu, Figure figure, FigureWidget figureWidget, boolean successors, DiagramScene diagramScene) {
        Set<Figure> set = figure.getPredecessorSet();
        if (successors) {
            set = figure.getSuccessorSet();
        }

        boolean first = true;
        for (Figure f : set) {
            if (f == figure) {
                continue;
            }

            if (first) {
                first = false;
            } else {
                menu.addSeparator();
            }

            Action go = diagramScene.createGotoAction(f);
            menu.add(go);

            JMenu preds = new JMenu("Nodes Above");
            preds.addMenuListener(figureWidget.new NeighborMenuListener(preds, f, false));
            menu.add(preds);

            JMenu succs = new JMenu("Nodes Below");
            succs.addMenuListener(figureWidget.new NeighborMenuListener(succs, f, true));
            menu.add(succs);
        }

        if (figure.getPredecessorSet().isEmpty() && figure.getSuccessorSet().isEmpty()) {
            menu.add("(none)");
        }
    }

    /**
     * Builds the submenu for a figure's neighbors on demand.
     */
    public class NeighborMenuListener implements MenuListener {

        private final JMenu menu;
        private final Figure figure;
        private final boolean successors;

        public NeighborMenuListener(JMenu menu, Figure figure, boolean successors) {
            this.menu = menu;
            this.figure = figure;
            this.successors = successors;
        }

        @Override
        public void menuSelected(MenuEvent e) {
            if (menu.getItemCount() > 0) {
                // already built before
                return;
            }

            build(menu.getPopupMenu(), figure, FigureWidget.this, successors, diagramScene);
        }

        @Override
        public void menuDeselected(MenuEvent e) {
            // ignore
        }

        @Override
        public void menuCanceled(MenuEvent e) {
            // ignore
        }
    }

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        final Set<Integer> hiddenIds;
        Set<Integer> selectedIds = this.getFigure().getSource().getSourceNodeIds();
        if (diagramScene.isAllVisible()) {
            hiddenIds = new HashSet<>(diagramScene.getModel().getContainer().getChildNodeIds());
            hiddenIds.removeAll(selectedIds);
        } else if (isBoundary()) {
            hiddenIds = new HashSet<>(diagramScene.getModel().getHiddenNodes());
            hiddenIds.removeAll(selectedIds);
        } else {
            hiddenIds = new HashSet<>(diagramScene.getModel().getHiddenNodes());
            hiddenIds.addAll(selectedIds);
            Set<Integer> ids = new HashSet<>(diagramScene.getModel().getGraphToView().getNodeIds());
            ids.removeAll(hiddenIds);
            if (ids.isEmpty()) {
                this.diagramScene.getModel().showNot(ids);
                return;
            }
        }
        this.diagramScene.getModel().showNot(hiddenIds);
    }
}
