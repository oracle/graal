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
package org.graalvm.visualizer.graph;

import org.graalvm.visualizer.layout.Link;
import org.graalvm.visualizer.layout.Port;

import java.awt.Color;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Connection implements Link {

    @Override
    public boolean isVIP() {
        return style == ConnectionStyle.BOLD;
    }

    public enum ConnectionStyle {

        NORMAL,
        DASHED,
        BOLD
    }

    private final InputSlot inputSlot;
    private final OutputSlot outputSlot;
    private final int hash;
    private final String label;
    private final String type;

    private Color color;
    private ConnectionStyle style;
    private List<Point> controlPoints;

    protected Connection(InputSlot inputSlot, OutputSlot outputSlot, String label, String type) {
        this.inputSlot = inputSlot;
        this.outputSlot = outputSlot;
        this.label = label;
        this.type = type;
        this.inputSlot.connections.add(this);
        this.outputSlot.connections.add(this);
        controlPoints = new ArrayList<>();
        Figure sourceFigure = this.outputSlot.getFigure();
        Figure destFigure = this.inputSlot.getFigure();
        sourceFigure.addSuccessor(destFigure);
        destFigure.addPredecessor(sourceFigure);

        this.color = Color.BLACK;
        this.style = ConnectionStyle.NORMAL;

        hash = makeHash();
    }

    private int makeHash() {
        int h = inputSlot.getFigure().getId();
        h = h * 83 + outputSlot.getFigure().getId();
        h = h * 83 + inputSlot.getPosition();
        h = h * 83 + outputSlot.getPosition();
        return h;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Connection)) {
            return false;
        }
        if (other == this) {
            return true;
        }
        Connection c = (Connection) other;
        return ((inputSlot.getFigure().getId() == c.getInputSlot().getFigure().getId())
                && (outputSlot.getFigure().getId() == c.getOutputSlot().getFigure().getId())
                && (outputSlot.getPosition() == c.getOutputSlot().getPosition())
                && (inputSlot.getPosition() == c.getInputSlot().getPosition()));
    }

    public InputSlot getInputSlot() {
        return inputSlot;
    }

    public OutputSlot getOutputSlot() {
        return outputSlot;
    }

    public Color getColor() {
        return color;
    }

    public ConnectionStyle getStyle() {
        return style;
    }

    public void setColor(Color c) {
        color = c;
    }

    public void setStyle(ConnectionStyle s) {
        style = s;
    }

    public String getLabel() {
        return label;
    }

    public String getType() {
        return type;
    }

    public void remove() {
        inputSlot.getFigure().removePredecessor(outputSlot.getFigure());
        inputSlot.connections.remove(this);
        outputSlot.getFigure().removeSuccessor(inputSlot.getFigure());
        outputSlot.connections.remove(this);
    }

    public String getToolTipText() {
        StringBuilder builder = new StringBuilder();
        if (label != null) {
            builder.append(label).append(": ");
        }
        if (type != null) {
            builder.append(type).append(" ");
        }
        builder.append("from ");
        builder.append(getOutputSlot().getFigure().getSource().firstId());
        builder.append(" to ");
        builder.append(getInputSlot().getFigure().getSource().firstId());
        return builder.toString();
    }

    @Override
    public String toString() {
        return "Connection('" + label + "', " + getFrom().getVertex() + " to " + getTo().getVertex() + ")";
    }

    @Override
    public Port getFrom() {
        return outputSlot;
    }

    @Override
    public Port getTo() {
        return inputSlot;
    }

    @Override
    public List<Point> getControlPoints() {
        return controlPoints;
    }

    @Override
    public void setControlPoints(List<Point> list) {
        controlPoints = list;
    }

    public Connection makeCopy(InputSlot is, OutputSlot os) {
        Connection c = new Connection(is, os, this.label, this.type);
        c.color = getColor();
        c.style = getStyle();
        if (controlPoints == null) {
            c.controlPoints = Collections.emptyList();
        } else {
            List<Point> pts = new ArrayList<>(controlPoints.size());
            for (Point pt : controlPoints) {
                if (pt == null) {
                    pts.add(null);
                } else {
                    pts.add(pt.getLocation());
                }
            }
            c.controlPoints = pts;
        }
        return c;
    }
}
