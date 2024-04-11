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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.graalvm.visualizer.data.Source;
import org.graalvm.visualizer.layout.Port;
import org.graalvm.visualizer.layout.Vertex;
import org.graalvm.visualizer.util.StringUtils;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public abstract class Slot implements Port, Source.Provider, Properties.Provider {

    private int wantedIndex;
    private final Source source;
    protected List<Connection> connections;
    private InputNode associatedNode;
    private final Figure figure;
    private Color color;
    private String text;
    private String shortName;
    private int position;

    protected Slot(Figure figure, int wantedIndex) {
        this.figure = figure;
        connections = new ArrayList<>(2);
        source = new Source();
        this.wantedIndex = wantedIndex;
        text = "";
        shortName = "";
        assert figure != null;
    }

    void setPosition(int pos) {
        this.position = pos;
    }

    public int getPosition() {
        return position;
    }

    public <T extends Slot> T copySlot(Figure f) {
        Slot s = copyInto(f);
        return (T) s.replaceFrom(this);
    }

    protected abstract Slot copyInto(Figure f);

    protected Slot replaceFrom(Slot copyFrom) {
        this.wantedIndex = copyFrom.getWantedIndex();
        this.color = copyFrom.getColor();
        this.text = copyFrom.getText();
        this.associatedNode = copyFrom.getAssociatedNode();
        this.source.addSourceNodes(copyFrom.getSource());
        this.shortName = copyFrom.shortName;
        return this;
    }

    @Override
    public Properties getProperties() {
        Properties p = Properties.newProperties();
        if (source.getSourceNodes().size() > 0) {
            for (InputNode n : source.getSourceNodes()) {
                p.add(n.getProperties());
            }
        } else {
            p.setProperty(PROPNAME_NAME, "Slot");
            p.setProperty(PROPNAME_FIGURE, figure.getProperties().get(PROPNAME_NAME, String.class));
            p.setProperty(PROPNAME_CONNECTION_COUNT, Integer.toString(connections.size()));
        }
        return p;
    }

    public static final Comparator<Slot> slotIndexComparator = new Comparator<Slot>() {

        @Override
        public int compare(Slot o1, Slot o2) {
            return o1.wantedIndex - o2.wantedIndex;
        }
    };
    public static final Comparator<Slot> slotFigureComparator = new Comparator<Slot>() {

        @Override
        public int compare(Slot o1, Slot o2) {
            return o1.figure.getId() - o2.figure.getId();
        }
    };

    public InputNode getAssociatedNode() {
        return associatedNode;
    }

    public void setAssociatedNode(InputNode node) {
        associatedNode = node;
    }

    public int getWidth() {
        if (shortName == null || shortName.length() <= 1) {
            return Figure.SLOT_WIDTH;
        } else {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(Diagram.getSlotFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            return Math.max(Figure.SLOT_WIDTH, metrics.stringWidth(shortName) + 6);
        }
    }

    public int getWantedIndex() {
        return wantedIndex;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public String getText() {
        return text;
    }

    public void setShortName(String s) {
        assert s != null;
        // assert s.length() <= 2;
        this.shortName = s;

    }

    public String getShortName() {
        return shortName;
    }

    public String getToolTipText() {
        StringBuilder sb = new StringBuilder();
        sb.append(text);

        for (InputNode n : getSource().getSourceNodes()) {
            sb.append(StringUtils.escapeHTML("Node (ID=" + n.getId() + "): " + n.getProperties().get(PROPNAME_NAME, String.class)));
            sb.append("<br>");
        }

        return sb.toString();
    }

    public boolean shouldShowName() {
        return getShortName() != null && getShortName().length() > 0;
    }

    public void setText(String s) {
        if (s == null) {
            s = "";
        }
        this.text = s;
    }

    public Figure getFigure() {
        assert figure != null;
        return figure;
    }

    public Color getColor() {
        return this.color;
    }

    public void setColor(Color c) {
        color = c;
    }

    public List<Connection> getConnections() {
        return Collections.unmodifiableList(connections);
    }

    public void removeAllConnections() {
        List<Connection> connectionsCopy = new ArrayList<>(this.connections);
        for (Connection c : connectionsCopy) {
            c.remove();
        }
    }

    @Override
    public Vertex getVertex() {
        return figure;
    }

}
