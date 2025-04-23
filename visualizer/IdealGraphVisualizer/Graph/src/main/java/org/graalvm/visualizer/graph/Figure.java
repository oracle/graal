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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.NAME_ROOT;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import org.graalvm.visualizer.data.Source;
import org.graalvm.visualizer.layout.Cluster;
import org.graalvm.visualizer.layout.Vertex;

import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public class Figure extends Properties.Entity implements Source.Provider, Vertex, DiagramItem {
    public static final int INSET = 8;
    public static int SLOT_WIDTH = 10;
    public static final int OVERLAPPING = 6;
    public static final int SLOT_START = 4;
    public static final int SLOT_OFFSET = 8;

    protected List<InputSlot> inputSlots;
    private OutputSlot singleOutput;
    private List<OutputSlot> outputSlots;
    private final Source source;
    private final Diagram diagram;
    private Point position;
    private final List<Figure> predecessors;
    private final List<Figure> successors;
    private List<InputGraph> subgraphs;
    private Color color;
    private final int id;
    private final String idString;
    private String[] lines;
    private int heightCache = -1;
    private int widthCache = -1;
    private final int hash;
    private boolean boundary;

    /**
     * Visible flag
     */
    private boolean visible;

    public void updateDimensions(Figure f) {
        setPosition(f.getPosition());
        if (f.heightCache >= 0) {
            this.heightCache = f.heightCache;
        }
        if (f.widthCache >= 0) {
            this.widthCache = f.widthCache;
        }
    }

    public int getHeight() {
        if (heightCache == -1) {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(Diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            String nodeText = diagram.getNodeText();
            heightCache = nodeText.split("\n").length * metrics.getHeight() + INSET;
        }
        return heightCache;
    }

    public static <T> List<T> getAllBefore(List<T> inputList, T tIn) {
        List<T> result = new ArrayList<>();
        for (T t : inputList) {
            if (t.equals(tIn)) {
                break;
            }
            result.add(t);
        }
        return result;
    }

    public static int getSlotsWidth(Collection<? extends Slot> slots) {
        int result = Figure.SLOT_OFFSET;
        for (Slot s : slots) {
            result += s.getWidth() + Figure.SLOT_OFFSET;
        }
        return result;
    }

    public static int getSlotsWidth(Slot s) {
        if (s == null) {
            return Figure.SLOT_OFFSET;
        } else {
            return Figure.SLOT_OFFSET + s.getWidth() + Figure.SLOT_OFFSET;
        }
    }

    public int getWidth() {
        if (widthCache == -1) {
            int max = 0;
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            Graphics g = image.getGraphics();
            g.setFont(Diagram.getFont().deriveFont(Font.BOLD));
            FontMetrics metrics = g.getFontMetrics();
            for (String s : getLines()) {
                int cur = metrics.stringWidth(s);
                if (cur > max) {
                    max = cur;
                }
            }
            widthCache = max + INSET;
            widthCache = Math.max(widthCache, Figure.getSlotsWidth(inputSlots));
            widthCache = Math.max(widthCache, outputSlots == null ? getSlotsWidth(singleOutput) : getSlotsWidth(outputSlots));
        }
        return widthCache;
    }

    protected Figure(Diagram diagram, int id) {
        this.diagram = diagram;
        this.source = new FigureSource(this);
        inputSlots = new ArrayList<>(5);
        predecessors = new ArrayList<>(6);
        successors = new ArrayList<>(6);
        this.id = id;
        idString = Integer.toString(id);

        this.position = new Point(0, 0);
        this.color = Color.WHITE;

        this.hash = diagram.hashCode() * 83 + id;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Figure)) {
            return false;
        } else if (obj == this) {
            return true;
        }
        Figure f = (Figure) obj;
        return f.getDiagram() == diagram && f.getId() == id;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public int getId() {
        return id;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public Color getColor() {
        return color;
    }

    public List<Figure> getPredecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public Set<Figure> getPredecessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getPredecessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<Figure> getSuccessorSet() {
        Set<Figure> result = new HashSet<>();
        for (Figure f : getSuccessors()) {
            result.add(f);
        }
        return Collections.unmodifiableSet(result);
    }

    public List<Figure> getSuccessors() {
        return Collections.unmodifiableList(successors);
    }

    protected void addPredecessor(Figure f) {
        this.predecessors.add(f);
    }

    protected void addSuccessor(Figure f) {
        this.successors.add(f);
    }

    protected void removePredecessor(Figure f) {
        assert predecessors.contains(f);
        predecessors.remove(f);
    }

    protected void removeSuccessor(Figure f) {
        assert successors.contains(f);
        successors.remove(f);
    }

    public List<InputGraph> getSubgraphs() {
        return subgraphs;
    }

    public void setSubgraphs(List<InputGraph> subgraphs) {
        this.subgraphs = subgraphs;
    }

    @Override
    public void setPosition(Point p) {
        this.position = p;
    }

    @Override
    public Point getPosition() {
        return position;
    }

    public Diagram getDiagram() {
        return diagram;
    }

    @Override
    public Source getSource() {
        return source;
    }

    public InputSlot createInputSlot() {
        InputSlot slot = new InputSlot(this, -1);
        inputSlots.add(slot);
        slot.setPosition(inputSlots.size() - 1);
        return slot;
    }

    public InputSlot createInputSlot(int index) {
        InputSlot slot = new InputSlot(this, index);
        inputSlots.add(slot);
        Collections.sort(inputSlots, Slot.slotIndexComparator);
        assignPositions(inputSlots);
        return slot;
    }

    @SuppressWarnings("element-type-mismatch")
    public void removeSlot(Slot s) {

        assert inputSlots.contains(s) || (singleOutput == s || (outputSlots != null && outputSlots.contains(s)));

        List<Connection> connections = new ArrayList<>(s.getConnections());
        for (Connection c : connections) {
            c.remove();
        }

        if (inputSlots.contains(s)) {
            inputSlots.remove(s);
            assignPositions(inputSlots);
        } else if (!doRemoveOutputSlot(s)) {
            return; // ?
        }
        sourcesChanged(s.getSource());
    }

    private boolean doRemoveOutputSlot(Slot s) {
        if (outputSlots != null) {
            @SuppressWarnings("element-type-mismatch")
            boolean ret = outputSlots.remove(s);
            if (outputSlots.isEmpty()) {
                outputSlots = null;
                singleOutput = null;
            } else if (outputSlots.size() == 1) {
                singleOutput = outputSlots.get(0);
                singleOutput.setPosition(0);
                outputSlots = null;
            } else {
                assignPositions(outputSlots);
            }
            return ret;
        } else if (singleOutput == s) {
            singleOutput = null;
            return true;
        }
        return false;
    }

    public OutputSlot createOutputSlot() {
        OutputSlot slot = new OutputSlot(this, -1);
        if (addOutputSlot(slot)) {
            assignPositions(outputSlots);
        }
        return slot;
    }

    private boolean addOutputSlot(OutputSlot slot) {
        boolean res;
        if (outputSlots != null) {
            outputSlots.add(slot);
            res = true;
        } else if (singleOutput == null) {
            singleOutput = slot;
            slot.setPosition(0);
            res = false;
        } else {
            outputSlots = new ArrayList<>(2);
            outputSlots.add(singleOutput);
            outputSlots.add(slot);
            singleOutput = null;
            res = true;
        }
        if (res) {
            sourcesChanged(slot.getSource());
        }
        return res;
    }

    private void assignPositions(List<? extends Slot> slots) {
        for (int index = slots.size() - 1; index >= 0; index--) {
            slots.get(index).setPosition(index);
        }
    }

    public OutputSlot createOutputSlot(int index) {
        OutputSlot slot = new OutputSlot(this, index);
        if (addOutputSlot(slot)) {
            Collections.sort(outputSlots, Slot.slotIndexComparator);
            assignPositions(outputSlots);
        }
        return slot;
    }

    public List<InputSlot> getInputSlots() {
        return Collections.unmodifiableList(inputSlots);
    }

    public Set<Slot> getSlots() {
        Set<Slot> result = new HashSet<>();
        result.addAll(getInputSlots());
        result.addAll(getOutputSlots());
        return result;
    }

    public List<OutputSlot> getOutputSlots() {
        if (outputSlots != null) {
            return Collections.unmodifiableList(outputSlots);
        } else if (singleOutput != null) {
            return Collections.singletonList(singleOutput);
        } else {
            return Collections.emptyList();
        }
    }

    void removeInputSlot(InputSlot s) {
        s.removeAllConnections();
        inputSlots.remove(s);
    }

    void removeOutputSlot(OutputSlot s) {
        s.removeAllConnections();
        doRemoveOutputSlot(s);
    }

    public String[] getLines() {
        if (lines == null) {
            updateLines();
        }
        return lines;
    }

    public void updateLines() {
        String[] strings = diagram.getNodeText().split("\n");
        String[] result = new String[strings.length];

        for (int i = 0; i < strings.length; i++) {
            result[i] = resolveString(strings[i], getProperties());
        }

        lines = result;
    }

    public static final String resolveString(String string, Properties properties) {
        StringBuilder sb = new StringBuilder();
        boolean inBrackets = false;
        StringBuilder curIdent = new StringBuilder();

        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (inBrackets) {
                if (c == ']') {
                    Object value = properties.get(curIdent.toString());
                    if (value == null) {
                        value = "";
                    }
                    sb.append(value);
                    inBrackets = false;
                } else {
                    curIdent.append(c);
                }
            } else {
                if (c == '[') {
                    inBrackets = true;
                    curIdent = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }
        }

        return sb.toString();
    }

    private int outputSlotCount() {
        return outputSlots == null ? (singleOutput != null ? 1 : 0) : outputSlots.size();
    }

    @Override
    public Dimension getSize() {
        return new Dimension(getWidth(), getHeight());
    }

    @Override
    public String toString() {
        return idString;
    }

    @Override
    public Cluster getCluster() {
        if (getSource().getSourceNodes().isEmpty()) {
            assert false : "Should never reach here, every figure must have at least one source node!";
            return null;
        } else {
            final InputBlock inputBlock = diagram.getGraph().getBlock(getSource().first());
            assert inputBlock != null;
            Cluster result = diagram.getBlock(inputBlock);
            assert result != null;
            return result;
        }
    }

    @Override
    public boolean isRoot() {
        List<InputNode> sourceNodes = source.getSourceNodes();
        //Get property value just once
        return sourceNodes.size() > 0 && NAME_ROOT.equals(sourceNodes.get(0).getProperties().get(PROPNAME_NAME, String.class));
    }

    @Override
    public int compareTo(Vertex f) {
        return toString().compareTo(f.toString());
    }

    @Override
    public Rectangle getBounds() {
        return new Rectangle(this.getPosition(), getSize());
    }

    void sourcesChanged(Source s) {
        diagram.invalidateSlotMap();
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public void setBounds(Rectangle bounds) {
        // no effect
    }

    public boolean isBoundary() {
        return boundary;
    }

    public void setBoundary(boolean boundary) {
        this.boundary = boundary;
    }

    public Figure makeCopy(Diagram d) {
        Figure cf = new Figure(d, id);
        replaceFromTo(this, cf);
        return cf;
    }

    private static void replaceFromTo(Figure from, Figure to) {
        assert from != to;
        assert from != null && to != null;
        assert from.id == to.id;
        to.source.replaceFrom(from.source);
        to.getProperties().clear();
        to.getProperties().add(from.getProperties());
        to.subgraphs = from.subgraphs;

        to.color = from.color;
        to.visible = from.visible;
        to.position = from.position.getLocation();

        to.boundary = from.boundary;
        to.heightCache = from.heightCache;
        to.widthCache = from.widthCache;
        to.lines = from.lines;

        to.inputSlots.clear();
        to.outputSlots = null;
        to.singleOutput = null;
        if (from.outputSlots != null) {
            for (Slot s : from.outputSlots) {
                s.copySlot(to);
            }
        } else if (from.singleOutput != null) {
            from.singleOutput.copySlot(to);
        }
        for (Slot s : from.inputSlots) {
            s.copySlot(to);
        }
    }

    public void replaceFrom(Figure source) {
        replaceFromTo(source, this);
    }
}
