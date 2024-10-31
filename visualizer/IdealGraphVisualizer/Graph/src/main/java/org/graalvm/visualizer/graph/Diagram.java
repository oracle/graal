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
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.NAME_START;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import org.graalvm.visualizer.data.Source;

import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.EqualityPropertyMatcher;

/**
 * Visual model of an {@link InputGraph}. Captures positions, sizes, routing
 * points for edges (connections), colors etc.
 * <p/>
 * Threading: individual calls are generally unsynchronized; the caller is
 * responsible to wrap the calls in either {@link #render} or {@link #change}
 * calls, which use read/write reentrant lock to ensure consistency of Diagram
 * data.
 *
 * @author sdedic
 */
public class Diagram {
    private static final Font FONT = new Font("Arial", Font.PLAIN, 12);
    private static final Font FONT_SLOT = new Font("Arial", Font.PLAIN, 10);
    private static final Font FONT_BOLD = FONT.deriveFont(Font.BOLD);

    private final InputGraph graph;
    private final String nodeText;//TODO: make nodeText dynamicaly changeable

    private final Map<Integer, Figure> figureMap;
    private final Map<InputBlock, Block> blocks;

    /**
     * The model lock
     */
    private final ReadWriteLock diagramLock = new ReentrantReadWriteLock();

    /**
     * Maps IDs to individual Figures and Slots.
     */
    private Map<Integer, Collection<Source.Provider>> sourceMap;

    public static Font getFont() {
        return FONT;
    }

    public static Font getSlotFont() {
        return FONT_SLOT;
    }

    public static Font getBoldFont() {
        return FONT_BOLD;
    }

    private Diagram(InputGraph graph, String text) {
        this.graph = graph;
        this.nodeText = text;
        figureMap = new LinkedHashMap<>();
        blocks = new LinkedHashMap<>(8);
        updateBlocks();
    }

    public Block getBlock(InputBlock b) {
        assert blocks.containsKey(b);
        return blocks.get(b);
    }

    public String getNodeText() {
        return nodeText;
    }

    public void render(Runnable r) {
        Lock l = diagramLock.readLock();
        l.lock();
        try {
            r.run();
        } finally {
            l.unlock();
        }
    }

    public <T> T render(Callable<T> r) throws Exception {
        Lock l = diagramLock.readLock();
        l.lock();
        try {
            return r.call();
        } finally {
            l.unlock();
        }
    }

    public void change(Runnable r) {
        Lock l = diagramLock.writeLock();
        l.lock();
        try {
            r.run();
        } finally {
            l.unlock();
        }
    }

    private void updateBlocks() {
        blocks.clear();
        for (InputBlock b : graph.getBlocks()) {
            Block curBlock = new Block(b, this);
            blocks.put(b, curBlock);
        }
    }

    public Collection<Block> getBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    public Collection<Figure> getFigures() {
        return Collections.unmodifiableCollection(figureMap.values());
    }

    /**
     * Returns a list of figures. The should be treated as read-only to allow
     * possible caching.
     *
     * @return list of figures.
     */
    public List<Figure> getFigureList() {
        return Collections.unmodifiableList(new ArrayList<>(figureMap.values()));
    }

    // used only in diagram from graph creation, for new diagram instances.
    private Map<Integer, Figure> createFigures() {
        for (InputNode n : graph.getNodes()) {
            Figure f = new Figure(this, n.getId());
            f.getSource().addSourceNode(n);
            f.getProperties().add(n.getProperties());
            f.setSubgraphs(n.getSubgraphs());

            assert !figureMap.containsKey(f.getId());
            figureMap.put(f.getId(), f);
        }
        return figureMap;
    }

    public Connection createConnection(InputSlot inputSlot, OutputSlot outputSlot, String label, String type) {
        assert inputSlot.getFigure().getDiagram() == this;
        assert outputSlot.getFigure().getDiagram() == this;
        return new Connection(inputSlot, outputSlot, label, type);
    }

    public Map<InputNode, Set<Figure>> calcSourceToFigureRelation() {
        Map<InputNode, Set<Figure>> map = new HashMap<>();

        for (InputNode node : graph.getNodes()) {
            map.put(node, new HashSet<>());
        }

        for (Figure f : figureMap.values()) {
            for (InputNode node : f.getSource().getSourceNodes()) {
                map.get(node).add(f);
            }
        }

        return map;
    }

    /**
     * Creates a copy of the Diagram. Copies both the structure and the
     * properties of the figures.
     *
     * @return created Diagram instance
     */
    public Diagram copy() {
        Diagram d = new Diagram(graph, nodeText);

        render(() -> {
            // clone all figures from the diagram, not from InputGraph.
            for (Figure f : this.figureMap.values()) {
                Figure cf = f.makeCopy(d);
                d.figureMap.put(cf.getId(), cf);
            }
            replaceConnections(this, d);
            //Commented, as this part is essental only for extraction speedup of BlockView
            for (Map.Entry<InputBlock, Block> entry : blocks.entrySet()) {
                d.getBlock(entry.getKey()).setBounds(entry.getValue().getBounds());
            }
        });
        return d;
    }

    private static void replaceConnections(Diagram from, Diagram to) {
        for (Figure f : from.figureMap.values()) {
            Figure cfrom = to.figureMap.get(f.getId());
            List<? extends Slot> outputSlots = f.getOutputSlots();
            for (int i = 0; i < outputSlots.size(); i++) {
                Slot s = outputSlots.get(i);
                OutputSlot cs = cfrom.getOutputSlots().get(i);

                for (Connection c : s.getConnections()) {
                    InputSlot ts = c.getInputSlot();
                    int tsIndex = ts.getPosition();

                    Figure tf = ts.getFigure();
                    Figure cto = to.figureMap.get(tf.getId());
                    InputSlot cts = cto.getInputSlots().get(tsIndex);
                    // PENDING: while the order of Connection in an OutputSlot is the same, the order of Connection
                    // instances in a specific target InputSlot may vary from the original.
                    c.makeCopy(cts, cs);
                }
            }
        }
    }

    public void replaceFrom(Diagram source) {
        source.render(() -> change(() -> replaceFrom0(source)));
    }

    private void replaceFrom0(Diagram source) {
        assert graph == source.graph;
        List<Figure> unmapped = new ArrayList<>();
        Set<Integer> mapped = new HashSet<>();
        for (Map.Entry<Integer, Figure> entry : source.figureMap.entrySet()) {
            Figure fig = figureMap.get(entry.getKey());
            if (fig == null) {
                unmapped.add(entry.getValue());
            } else {
                fig.replaceFrom(entry.getValue());
                mapped.add(fig.getId());
            }
        }
        figureMap.keySet().retainAll(mapped);
        for (Figure um : unmapped) {
            Figure cf = um.makeCopy(this);
            figureMap.put(cf.getId(), cf);
        }
        replaceConnections(source, this);
    }

    public static Diagram createEmptyDiagram(InputGraph graph) {
        return new Diagram(graph, "");
    }

    public static Diagram createEmptyDiagram(InputGraph graph, String nodeText) {
        return new Diagram(graph, nodeText == null ? "" : nodeText);
    }

    public static Diagram createDiagram(InputGraph graph, String nodeText) {
        if (graph == null) {
            return null;
        }

        Diagram d = new Diagram(graph, nodeText == null ? "" : nodeText);
        Map<Integer, Figure> figureHash = d.createFigures();
        for (InputEdge e : graph.getEdges()) {

            int from = e.getFrom();
            int to = e.getTo();
            Figure fromFigure = figureHash.get(from);
            Figure toFigure = figureHash.get(to);

            if (fromFigure == null || toFigure == null) {
                continue;
            }
            assert fromFigure != null && toFigure != null;

            int fromIndex = e.getFromIndex();
            for (int i = fromFigure.getOutputSlots().size(); i <= fromIndex; ++i) {
                fromFigure.createOutputSlot();
            }
            // it depends on the edge list order what connection comes first: the 2nd output might be ordered before the 1st one, especially in diff graphs.
            assert fromFigure.getOutputSlots().size() >= fromIndex + 1 : "os: " + fromFigure.getOutputSlots().size() + "; fi: " + fromIndex;
            OutputSlot outputSlot = fromFigure.getOutputSlots().get(fromIndex);

            int toIndex = e.getToIndex();
            for (int i = toFigure.getInputSlots().size(); i <= toIndex; ++i) {
                toFigure.createInputSlot();
            }
            assert toFigure.getInputSlots().size() >= toIndex + 1;
            InputSlot inputSlot = toFigure.getInputSlots().get(toIndex);

            Connection c = d.createConnection(inputSlot, outputSlot, e.getDisplayLabel(), e.getType());

            if (e.getState() == InputEdge.State.NEW) {
                c.setStyle(Connection.ConnectionStyle.BOLD);
            } else if (e.getState() == InputEdge.State.DELETED) {
                c.setStyle(Connection.ConnectionStyle.DASHED);
            }
        }
        return d;
    }

    public void removeAllFigures(Collection<Figure> figuresToRemove) {
        if (figuresToRemove instanceof Set) {
            removeAllFigures(((Set) figuresToRemove));
        }
        Set s = new HashSet<>(figuresToRemove);
        removeAllFigures(s);
    }

    public void removeAllFigures(Set<Figure> figuresToRemove) {
        if (!figuresToRemove.isEmpty()) {
            invalidateSlotMap();
        }
        for (Figure f : figuresToRemove) {
            freeFigure(f);
        }

        for (Figure f : figuresToRemove) {
            figureMap.remove(f.getId());
        }
    }

    private Set<Integer> collectFigureIds(Figure succ) {
        Set<Integer> representedIds = new HashSet<>();
        succ.getSource().collectIds(representedIds);
        succ.getInputSlots().forEach(is -> is.getSource().collectIds(representedIds));
        succ.getOutputSlots().forEach(is -> is.getSource().collectIds(representedIds));
        return representedIds;
    }

    private void freeFigure(Figure succ) {
        Set<Integer> representedIds = sourceMap == null ? null : collectFigureIds(succ);
        List<InputSlot> inputSlots = new ArrayList<>(succ.getInputSlots());
        for (InputSlot s : inputSlots) {
            succ.removeInputSlot(s);
        }

        List<OutputSlot> outputSlots = new ArrayList<>(succ.getOutputSlots());
        for (OutputSlot s : outputSlots) {
            succ.removeOutputSlot(s);
        }

        assert succ.getInputSlots().isEmpty();
        assert succ.getOutputSlots().isEmpty();
        assert succ.getPredecessors().isEmpty();
        assert succ.getSuccessors().isEmpty();

        if (representedIds != null) {
            invalidateSlotMap();
        }
    }

    public void removeFigure(Figure succ) {
        assert this.figureMap.containsValue(succ);
        freeFigure(succ);
        this.figureMap.remove(succ.getId());
        invalidateSlotMap();
    }

    public String getName() {
        return graph.getName();
    }

    public InputGraph getGraph() {
        return graph;
    }

    public Set<Connection> getConnections() {

        Set<Connection> connections = new HashSet<>();
        for (Figure f : figureMap.values()) {
            for (InputSlot s : f.getInputSlots()) {
                connections.addAll(s.getConnections());
            }
        }

        return connections;
    }

    public Figure getRootFigure() {
        Properties.PropertySelector<Figure> selector = new Properties.PropertySelector<>(getFigures());
        Figure root = selector.selectSingle(new EqualityPropertyMatcher(PROPNAME_NAME, NAME_ROOT));
        if (root == null) {
            root = selector.selectSingle(new EqualityPropertyMatcher(PROPNAME_NAME, NAME_START));
        }
        if (root == null) {
            List<Figure> rootFigures = getRootFigures();
            if (rootFigures.size() > 0) {
                root = rootFigures.get(0);
            } else if (!figureMap.isEmpty()) {
                root = figureMap.values().iterator().next();
            }
        }

        return root;
    }

    public void printStatistics() {
        System.out.println("=============================================================");
        System.out.println("Diagram statistics");

        Collection<Figure> tmpFigures = getFigures();
        Set<Connection> connections = getConnections();

        System.out.println("Number of figures: " + tmpFigures.size());
        System.out.println("Number of connections: " + connections.size());

        List<Figure> figuresSorted = new ArrayList<>(tmpFigures);
        Collections.sort(figuresSorted, (Figure a, Figure b)
                -> b.getPredecessors().size() + b.getSuccessors().size() - a.getPredecessors().size() - a.getSuccessors().size());

        final int COUNT = 10;
        int z = 0;
        for (Figure f : figuresSorted) {

            z++;
            int sum = f.getPredecessors().size() + f.getSuccessors().size();
            System.out.println("#" + z + ": " + f + ", predCount=" + f.getPredecessors().size() + " succCount=" + f.getSuccessors().size());
            if (sum < COUNT) {
                break;
            }

        }

        System.out.println("=============================================================");
    }

    public List<Figure> getRootFigures() {
        ArrayList<Figure> rootFigures = new ArrayList<>();
        for (Figure f : figureMap.values()) {
            if (f.getPredecessors().isEmpty()) {
                rootFigures.add(f);
            }
        }
        return rootFigures;
    }

    /**
     * Returns object that corresponds to the ID. Currently the provided objects
     * can be either a {@link Figure} or a {@link Slot}.
     *
     * @param id
     * @return
     */
    public Collection<Source.Provider> forSource(int id) {
        return Collections.unmodifiableCollection(ensureSlotMap().getOrDefault(id, Collections.emptyList()));
    }

    public Optional<Figure> getFigure(int id) {
        for (Source.Provider p : forSource(id)) {
            if (p instanceof Figure) {
                return Optional.of((Figure) p);
            }
        }
        return Optional.empty();
    }

    public Figure getFigureById(int id) {
        return figureMap.get(id);
    }

    public <T extends Source.Provider> Set<T> forSources(Collection ids, Class<T> clazz) {
        Set<T> r = new HashSet<>(ids.size());
        Map<Integer, Collection<Source.Provider>> slots = ensureSlotMap();
        int i;
        for (Object o : ids) {
            if (clazz.isInstance(o)) {
                r.add((T) o);
                continue;
            } else if (o instanceof InputNode) {
                i = ((InputNode) o).getId();
            } else if (o instanceof Integer) {
                i = (Integer) o;
            } else {
                continue;
            }
            for (Source.Provider s : slots.getOrDefault(i, Collections.emptyList())) {
                if (clazz.isInstance(s)) {
                    r.add(clazz.cast(s));
                }
            }
        }
        return r;
    }

    private Map<Integer, Collection<Source.Provider>> ensureSlotMap() {
        if (sourceMap != null) {
            return sourceMap;
        }
        Map<Integer, Collection<Source.Provider>> m = new HashMap<>();
        getFigures().stream().flatMap(f -> Stream.concat(
                Stream.of(f),
                f.getSlots().stream())).forEach(s -> {
            for (InputNode in : s.getSource().getSourceNodes()) {
                m.computeIfAbsent(in.getId(), (id) -> new ArrayList<>(2)).add(s);
            }
        });
        return sourceMap = m;
    }

    void invalidateSlotMap() {
        this.sourceMap = null;
    }

    private Dimension size;

    public Dimension getSize() {
        if (size == null) {
            return null;
        }
        return size.getSize();
    }

    public void setSize(Dimension size) {
        assert this.size == null;
        this.size = size.getSize();
    }
}
