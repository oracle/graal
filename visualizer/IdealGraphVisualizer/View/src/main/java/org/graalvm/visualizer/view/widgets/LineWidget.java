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

import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.InputSlot;
import org.graalvm.visualizer.graph.OutputSlot;
import org.graalvm.visualizer.util.StringUtils;
import org.graalvm.visualizer.view.DiagramScene;
import org.netbeans.api.visual.action.ActionFactory;
import org.netbeans.api.visual.action.PopupMenuProvider;
import org.netbeans.api.visual.action.SelectProvider;
import org.netbeans.api.visual.animator.SceneAnimator;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LineWidget extends Widget implements PopupMenuProvider {
    public static final int BORDER = 5;
    public static final int ARROW_SIZE = 6;
    public static final int BOLD_ARROW_SIZE = 7;
    public static final int HOVER_ARROW_SIZE = 8;
    public static final int BOLD_STROKE_WIDTH = 2;
    public static final int HOVER_STROKE_WIDTH = 3;
    private static final double ZOOM_FACTOR = 0.1;

    private OutputSlot outputSlot;
    private DiagramScene scene;
    private List<Connection> connections;
    private Point from;
    private Point to;
    private Rectangle clientArea;
    private Color color = Color.BLACK;
    private LineWidget predecessor;
    private List<LineWidget> successors;
    private boolean highlighted;
    private boolean popupVisible;
    private boolean isBold;
    private boolean isDashed;

    public LineWidget(DiagramScene scene, OutputSlot s, List<Connection> connections, Point from, Point to, LineWidget predecessor, SceneAnimator animator, boolean isBold, boolean isDashed) {
        super(scene);
        this.scene = scene;
        this.outputSlot = s;
        this.connections = connections;
        this.predecessor = predecessor;
        this.successors = new ArrayList<>();
        if (predecessor != null) {
            predecessor.addSuccessor(this);
        }

        this.isBold = isBold;
        this.isDashed = isDashed;

        setPoints(from, to);

        if (connections.size() > 0) {
            color = connections.get(0).getColor();
        }
        this.setToolTipText("<HTML>" + generateToolTipText(this.connections) + "</HTML>");

        this.setCheckClipping(true);

        this.getActions().addAction(ActionFactory.createPopupMenuAction(this));
        if (animator == null) {
            this.setBackground(color);
        } else {
            this.setBackground(Color.WHITE);
            animator.animateBackgroundColor(this, color);
        }

        this.getActions().addAction(ActionFactory.createSelectAction(new SelectProvider() {

            @Override
            public boolean isAimingAllowed(Widget arg0, Point arg1, boolean arg2) {
                return true;
            }

            @Override
            public boolean isSelectionAllowed(Widget arg0, Point arg1, boolean arg2) {
                return true;
            }

            @Override
            public void select(Widget arg0, Point arg1, boolean arg2) {
                Set<Figure> set = new HashSet<>();
                set.add(outputSlot.getFigure());
                for (Connection c : LineWidget.this.connections) {
                    set.add(c.getInputSlot().getFigure());
                }
                LineWidget.this.scene.setSelectedObjects(set);
            }
        }));
    }

    /**
     * Sets control start/end points of the line widgets. Recalculates the
     * bounding rectangle
     *
     * @param from starting point
     * @param to   ending point
     */
    public void setPoints(Point from, Point to) {
        if (this.from != null && from.equals(this.from) && to.equals(this.to)) {
            return;
        }
        this.from = from;
        this.to = to;
        int minX = from.x;
        int minY = from.y;
        int maxX = to.x;
        int maxY = to.y;
        if (minX > maxX) {
            int tmp = minX;
            minX = maxX;
            maxX = tmp;
        }

        if (minY > maxY) {
            int tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        clientArea = new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        clientArea.grow(BORDER, BORDER);
        revalidate();
    }

    private String generateToolTipText(List<Connection> conn) {
        StringBuilder sb = new StringBuilder();
        for (Connection c : conn) {
            sb.append(StringUtils.escapeHTML(c.getToolTipText())).append("<br>");
        }
        return sb.toString();
    }

    public Point getFrom() {
        return from;
    }

    public Point getTo() {
        return to;
    }

    private void addSuccessor(LineWidget widget) {
        if (!successors.contains(widget)) {
            this.successors.add(widget);
        }
    }

    public void setPredecessor(LineWidget predecessor) {
        if (this.predecessor != predecessor) {
            if (this.predecessor != null) {
                this.predecessor.successors.remove(this);
            }
            this.predecessor = predecessor;
            if (predecessor != null) {
                predecessor.addSuccessor(this);
            }
        }
    }

    @Override
    protected Rectangle calculateClientArea() {
        return clientArea;
    }

    @Override
    protected void paintWidget() {
        if (scene.getZoomFactor() < ZOOM_FACTOR) {
            return;
        }

        Graphics2D g = getScene().getGraphics();
        g.setPaint(this.getBackground());
        float width = 1.0f;

        if (isBold) {
            width = BOLD_STROKE_WIDTH;
        }

        if (highlighted || popupVisible) {
            width = HOVER_STROKE_WIDTH;
        }

        Stroke oldStroke = g.getStroke();
        if (isDashed) {
            float[] dashPattern = {5, 5, 5, 5};
            g.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 10,
                    dashPattern, 0));
        } else {
            g.setStroke(new BasicStroke(width));
        }

        g.drawLine(from.x, from.y, to.x, to.y);

        boolean sameFrom = false;
        boolean sameTo = successors.isEmpty();
        for (LineWidget w : successors) {
            if (w.getFrom().equals(getTo())) {
                sameTo = true;
                break;
            }
        }

        if (predecessor == null || predecessor.getTo().equals(getFrom())) {
            sameFrom = true;
        }

        int size = ARROW_SIZE;
        if (isBold) {
            size = BOLD_ARROW_SIZE;
        }
        if (highlighted || popupVisible) {
            size = HOVER_ARROW_SIZE;
        }
        drawArrows(g, size, !sameFrom, !sameTo);
        g.setStroke(oldStroke);
    }

    //TODO: Make directed arrow
    private void drawArrows(Graphics2D g, int size, boolean drawFrom, boolean drawTo) {
        if (drawFrom || drawTo) {
            Polygon arrow = new Polygon(
                    new int[]{-size / 2, size / 2, 0},
                    new int[]{-size / 2, -size / 2, size / 2},
                    3);
            if (drawFrom) {
                assert predecessor != null;
                arrow.translate(from.x, from.y);
                g.fillPolygon(arrow);
            } else if (drawTo) {
                assert successors.size() > 0;
                arrow.translate(to.x, to.y);
                g.fillPolygon(arrow);
            }
        }
    }

    private void setHighlighted(boolean b) {
        this.highlighted = b;
        Set<Object> highlightedObjects = new HashSet<>(scene.getHighlightedObjects());
        Set<Object> highlightedObjectsChange = new HashSet<>();
        highlightedObjectsChange.add(outputSlot.getFigure());
        highlightedObjectsChange.add(outputSlot);
        for (Connection c : connections) {
            highlightedObjectsChange.add(c.getInputSlot().getFigure());
            highlightedObjectsChange.add(c.getInputSlot());
        }
        if (b) {
            highlightedObjects.addAll(highlightedObjectsChange);
        } else {
            highlightedObjects.removeAll(highlightedObjectsChange);
        }
        scene.setHighlightedObjects(highlightedObjects);
        this.revalidate(true);
    }

    private void setPopupVisible(boolean b) {
        this.popupVisible = b;
        this.revalidate(true);
    }

    @Override
    public boolean isHitAt(Point localPoint) {
        return Line2D.ptLineDistSq(from.x, from.y, to.x, to.y, localPoint.x, localPoint.y) <= BORDER * BORDER;
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        if (previousState.isHovered() != state.isHovered()) {
            setRecursiveHighlighted(state.isHovered());
        }
    }

    private void setRecursiveHighlighted(boolean b) {
        LineWidget cur = predecessor;
        while (cur != null) {
            cur.setHighlighted(b);
            cur = cur.predecessor;
        }

        highlightSuccessors(b);
        this.setHighlighted(b);
    }

    private void highlightSuccessors(boolean b) {
        for (LineWidget s : successors) {
            s.setHighlighted(b);
            s.highlightSuccessors(b);
        }
    }

    private void setRecursivePopupVisible(boolean b) {
        LineWidget cur = predecessor;
        while (cur != null) {
            cur.setPopupVisible(b);
            cur = cur.predecessor;
        }

        popupVisibleSuccessors(b);
        setPopupVisible(b);
    }

    private void popupVisibleSuccessors(boolean b) {
        for (LineWidget s : successors) {
            s.setPopupVisible(b);
            s.popupVisibleSuccessors(b);
        }
    }

    @Override
    public JPopupMenu getPopupMenu(Widget widget, Point localLocation) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(scene.createGotoAction(outputSlot.getFigure()));
        menu.addSeparator();

        for (Connection c : connections) {
            InputSlot s = c.getInputSlot();
            menu.add(scene.createGotoAction(s.getFigure()));
        }

        final LineWidget w = this;
        menu.addPopupMenuListener(new PopupMenuListener() {

            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                w.setRecursivePopupVisible(true);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                w.setRecursivePopupVisible(false);
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });

        return menu;
    }
}
