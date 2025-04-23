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

import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.graph.OutputSlot;
import org.graalvm.visualizer.graph.Slot;
import org.graalvm.visualizer.util.DoubleClickHandler;
import org.graalvm.visualizer.view.DiagramScene;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.netbeans.api.visual.action.WidgetAction;
import org.netbeans.api.visual.model.ObjectState;
import org.netbeans.api.visual.widget.Widget;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class SlotWidget extends Widget implements DoubleClickHandler {

    private static final double TEXT_ZOOM_FACTOR = 0.9;
    private static final double ZOOM_FACTOR = 0.6;

    private final Slot slot;
    private final FigureWidget figureWidget;
    private final DiagramViewModel model;

    public SlotWidget(Slot slot, DiagramScene scene, Widget parent, FigureWidget fw) {
        super(scene);
        this.model = scene.getModel();
        this.slot = slot;
        figureWidget = fw;
        this.setToolTipText("<HTML>" + slot.getToolTipText() + "</HTML>");
        this.setCheckClipping(true);
        parent.addChild(this);
    }

    @Override
    protected void notifyStateChanged(ObjectState previousState, ObjectState state) {
        super.notifyStateChanged(previousState, state);
        repaint();
    }

    public Slot getSlot() {
        return slot;
    }

    public FigureWidget getFigureWidget() {
        return figureWidget;
    }

    @Override
    protected void paintWidget() {
        if (getScene().getZoomFactor() < ZOOM_FACTOR) {
            return;
        }
        Rectangle b = getBounds();
        if (b == null) {
            // not laid out
            return;
        }

        Graphics2D g = this.getGraphics();
        int w = b.width;
        int h = b.height;

        if (getSlot().getSource().getSourceNodes().size() > 0) {
            g.setColor(getSlot().getColor());

            int FONT_OFFSET = 2;

            int rectW = h;

            Font font = Diagram.getSlotFont();
            if (this.getState().isSelected()) {
                font = font.deriveFont(Font.BOLD);
            }
            String shortName = getSlot().getShortName();
            if (shortName != null && shortName.length() > 0) {
                g.setFont(font);
                Rectangle2D r1 = g.getFontMetrics().getStringBounds(shortName, g);
                rectW = (int) r1.getWidth() + FONT_OFFSET * 2;
            }
            g.fillRect(w / 2 - rectW / 2, 0, rectW - 1, h - 1);

            if (this.getState().isHighlighted()) {
                g.setColor(Color.BLUE);
            } else {
                g.setColor(Color.BLACK);
            }
            g.drawRect(w / 2 - rectW / 2, 0, rectW - 1, h - 1);

            if (shortName != null && shortName.length() > 0 && getScene().getZoomFactor() >= TEXT_ZOOM_FACTOR) {
                Rectangle2D r1 = g.getFontMetrics().getStringBounds(shortName, g);
                g.drawString(shortName, (int) (w - r1.getWidth()) / 2, g.getFontMetrics().getAscent() - 1);
            }

        } else {
            if (this.getSlot().getConnections().isEmpty()) {
                if (this.getState().isHighlighted()) {
                    g.setColor(Color.BLUE);
                } else {
                    g.setColor(Color.BLACK);
                }
                int r = 2;
                if (slot instanceof OutputSlot) {
                    g.fillOval(w / 2 - r, Figure.SLOT_WIDTH - Figure.SLOT_START - r, 2 * r, 2 * r);
                } else {
                    g.fillOval(w / 2 - r, Figure.SLOT_START - r, 2 * r, 2 * r);
                }
            } else {
                // Do not paint a slot with connections.
            }
        }
    }

    @Override
    protected Rectangle calculateClientArea() {
        return new Rectangle(0, 0, slot.getWidth(), Figure.SLOT_WIDTH);
    }

    protected abstract int calculateSlotWidth();

    protected int calculateWidth(int count) {
        return getFigureWidget().getFigure().getWidth() / count;
    }

    @Override
    public void handleDoubleClick(Widget w, WidgetAction.WidgetMouseEvent e) {
        Set<Integer> hiddenNodes;
        if (model.getHiddenNodes().isEmpty()) {
            hiddenNodes = new HashSet<>(model.getContainer().getChildNodeIds());
        } else {
            hiddenNodes = new HashSet<>(model.getHiddenNodes());
        }

        Set<Integer> clickedIds = slot.getSource().getSourceNodeIds();
        Diagram d = slot.getFigure().getDiagram();
        Set<Slot> slots = d.forSources(clickedIds, Slot.class);
        if (!slots.isEmpty()) {
            hiddenNodes.removeAll(slots.stream().map(s -> s.getFigure().getId()).collect(Collectors.toList()));
            model.showNot(hiddenNodes);
        }
    }
}
