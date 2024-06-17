/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.graphio.parsing.model;

import static jdk.graal.compiler.graphio.parsing.model.InputBlock.NO_BLOCK_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_DUPLICATE;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.graal.compiler.graphio.parsing.ModelBuilder;

public class InputGraph extends AbstractMutableDocumentItem<InputGraph> implements FolderElement, DumpedElement {
    public static final int INVALID_INDEX = -1;

    private final transient ChangedEvent<InputGraph> propertyChangedEvent;

    private final int dumpId;
    private final String format;
    private final Object[] args;
    private final transient Object id;
    private Folder parent;
    private Group parentGroup;
    private Set<Integer> nodeIds;
    private volatile boolean frozen;
    private String graphType;

    public int getDumpId() {
        return dumpId;
    }

    public String getFormat() {
        return format;
    }

    public Object[] getArgs() {
        return args;
    }

    void freeze() {
        assert graphType != null : "InputGraph type must be set before being frozen";
        freezeProperties();
        this.frozen = true;
    }

    boolean isFrozen() {
        return frozen;
    }

    public String getGraphType() {
        return graphType;
    }

    public final void setGraphType(String type) {
        assert !isFrozen();
        this.graphType = type;
    }

    @Override
    public ChangedEvent<InputGraph> getPropertyChangedEvent() {
        return propertyChangedEvent;
    }

    private final GraphData data = new GraphData();

    /**
     * The graph's own data. Data is separated from the InputGraph itself so they can be eventually
     * GCed independently.
     */
    public static final class GraphData {
        private final Map<Integer, InputNode> nodes = new LinkedHashMap<>();
        /*
         * See GR-3584: HashSet enables more efficient edge pruning
         */
        private final Collection<InputEdge> edges = new LinkedHashSet<>();
        private final Map<String, InputBlock> blocks = new LinkedHashMap<>();
        private final List<InputBlockEdge> blockEdges = new ArrayList<>();
        private final Map<Integer, InputBlock> nodeToBlock = new LinkedHashMap<>();
        private int highestNodeId = -1;

        public Collection<InputNode> getNodes() {
            return nodes.values();
        }

        public Collection<InputEdge> getEdges() {
            return edges;
        }

        public Map<String, InputBlock> getBlocks() {
            return blocks;
        }

        public List<InputBlockEdge> getBlockEdges() {
            return blockEdges;
        }

        public Map<Integer, InputBlock> getNodeToBlock() {
            return nodeToBlock;
        }

        public Map<Integer, InputNode> getNodeMap() {
            return nodes;
        }
    }

    public InputGraph(int dumpId, String format, Object[] args) {
        this(null, dumpId, format, args);
    }

    @SuppressWarnings("this-escape")
    public InputGraph(Object id, int dumpId, String format, Object[] args) {
        super(Properties.newProperties(PROPNAME_NAME, ModelBuilder.makeGraphName(dumpId, format, args)));
        if (id == null) {
            this.id = Group.uniqueIDGenerator.getAndIncrement();
        } else {
            this.id = id;
        }
        this.dumpId = dumpId;
        this.format = format;
        this.args = args;
        this.propertyChangedEvent = new ChangedEvent<>(this);
    }

    /**
     * Creates a new empty graph to be used only in tests.
     *
     * @param name a string containing the graph's name, optionally preceded by the graph's dump id
     *            (separated by a semicolon).
     */
    public static InputGraph createTestGraph(String name) {
        int dumpId = INVALID_INDEX;
        String format = name;
        if (name != null) {
            int index = name.indexOf(":");
            if (index != -1) {
                dumpId = Integer.parseInt(name.substring(0, index));
                format = name.substring(index + 1).trim();
            }
        }
        if (format == null) {
            format = "";
        }
        InputGraph ig = new InputGraph(null, dumpId, format, new Object[0]);
        ig.setGraphType(GraphClassifier.DEFAULT_TYPE);
        return ig;
    }

    @Override
    public Object getID() {
        return id;
    }

    protected GraphData data() {
        return data;
    }

    @Override
    public void setParent(Folder parent) {
        assert !isFrozen();
        this.parent = parent;
        if (parent instanceof Group) {
            assert this.parentGroup == null;
            this.parentGroup = (Group) parent;
            if (parentGroup.isPlaceholderGroup()) {
                return;
            }
        }
        if (parent != null) {
            freeze();
        }
    }

    public InputBlockEdge addBlockEdge(InputBlock left, InputBlock right) {
        assert !isFrozen();
        InputBlockEdge edge = new InputBlockEdge(left, right);
        data().blockEdges.add(edge);
        left.addSuccessor(right);
        return edge;
    }

    public Map<Integer, InputNode> getNodeMap() {
        return Collections.unmodifiableMap(data().nodes);
    }

    public Set<Integer> getNodeIds() {
        if (nodeIds != null) {
            return nodeIds;
        }
        return nodeIds = Collections.unmodifiableSet(new HashSet<>(data().nodes.keySet()));
    }

    public List<InputNode> findRootNodes() {
        List<InputNode> result = new ArrayList<>();
        Set<Integer> nonRoot = new HashSet<>();
        GraphData d = data();
        for (InputEdge curEdges : d.edges) {
            nonRoot.add(curEdges.getTo());
        }

        for (InputNode node : d.getNodes()) {
            if (!nonRoot.contains(node.getId())) {
                result.add(node);
            }
        }

        return result;
    }

    public Map<InputNode, List<InputEdge>> findAllOutgoingEdges() {
        Map<InputNode, List<InputEdge>> result = new HashMap<>(getNodes().size());
        for (InputNode n : this.getNodes()) {
            result.put(n, new ArrayList<>());
        }
        GraphData d = data();
        for (InputEdge e : d.edges) {
            int from = e.getFrom();
            InputNode fromNode = this.getNode(from);
            List<InputEdge> fromList = result.get(fromNode);
            assert fromList != null;
            fromList.add(e);
        }

        for (InputNode n : d.getNodes()) {
            List<InputEdge> list = result.get(n);
            list.sort(InputEdge.OUTGOING_COMPARATOR);
        }

        return result;
    }

    public Map<InputNode, List<InputEdge>> findAllIngoingEdges() {
        Map<InputNode, List<InputEdge>> result = new HashMap<>(getNodes().size());
        GraphData d = data();
        for (InputNode n : d.getNodes()) {
            result.put(n, new ArrayList<>());
        }

        for (InputEdge e : d.edges) {
            int to = e.getTo();
            InputNode toNode = this.getNode(to);
            List<InputEdge> toList = result.get(toNode);
            assert toList != null;
            toList.add(e);
        }

        for (InputNode n : d.getNodes()) {
            List<InputEdge> list = result.get(n);
            list.sort(InputEdge.INGOING_COMPARATOR);
        }

        return result;
    }

    public List<InputEdge> findOutgoingEdges(InputNode n) {
        List<InputEdge> result = new ArrayList<>();

        for (InputEdge e : data().edges) {
            if (e.getFrom() == n.getId()) {
                result.add(e);
            }
        }

        result.sort(InputEdge.OUTGOING_COMPARATOR);

        return result;
    }

    public void clearBlocks() {
        assert !isFrozen();
        data().blocks.clear();
        data().nodeToBlock.clear();
    }

    public void setEdge(int fromIndex, int toIndex, int from, int to) {
        assert !isFrozen();
        assert fromIndex == ((char) fromIndex) : "Downcast must be safe";
        assert toIndex == ((char) toIndex) : "Downcast must be safe";

        InputEdge edge = new InputEdge((char) fromIndex, (char) toIndex, from, to);
        if (!this.getEdges().contains(edge)) {
            this.addEdge(edge);
        }
    }

    public void ensureNodesInBlocks() {
        InputBlock noBlock = null;
        GraphData d = data();
        for (InputNode n : d.getNodes()) {
            assert d.nodes.get(n.getId()) == n;
            if (!d.nodeToBlock.containsKey(n.getId())) {
                if (noBlock == null) {
                    noBlock = this.addBlock(NO_BLOCK_NAME);
                }
                noBlock.addNode(n.getId());
            }
            assert this.getBlock(n) != null;
        }
    }

    public void setBlock(InputNode node, InputBlock block) {
        assert !isFrozen();
        data().nodeToBlock.put(node.getId(), block);
    }

    public InputBlock getBlock(int nodeId) {
        return data().nodeToBlock.get(nodeId);
    }

    public InputBlock getBlock(InputNode node) {
        assert data().nodes.containsKey(node.getId());
        assert data().nodes.get(node.getId()).equals(node);
        return getBlock(node.getId());
    }

    public InputGraph getNext() {
        return parentGroup.getNext(this);
    }

    public InputGraph getPrev() {
        return parentGroup.getPrev(this);
    }

    /**
     * Determines whether a node changed compared to state in its predecessor graph. If the node is
     * newly introduced, or discarded in this graph, it is considered changed. Otherwise node is
     * changed if its properties are not equal.
     * <p/>
     * Note: if the graph is a duplicate, then none of its nodes can be changed.
     *
     * @param nodeId node to check
     * @return true, if the node in this graph has changed
     */
    public boolean isNodeChanged(int nodeId) {
        InputGraph prev = getPrev();
        if (prev == null || !containsNode(nodeId)) {
            return true;
        }
        if (!prev.containsNode(nodeId)) {
            return true;
        }
        if (isDuplicate()) {
            return false;
        }
        InputNode our = getNode(nodeId);
        InputNode their = prev.getNode(nodeId);
        return our.getProperties().equals(their.getProperties());
    }

    @Override
    public String getName() {
        return getProperties().get(PROPNAME_NAME, String.class);
    }

    public int getNodeCount() {
        return data().nodes.size();
    }

    public int getEdgeCount() {
        return data().edges.size();
    }

    public Collection<InputNode> getNodes() {
        return Collections.unmodifiableCollection(data().nodes.values());
    }

    public Set<Integer> getNodesAsSet() {
        return Collections.unmodifiableSet(data().nodes.keySet());
    }

    public Collection<InputBlock> getBlocks() {
        return Collections.unmodifiableCollection(data().blocks.values());
    }

    public void addNode(InputNode node) {
        assert !isFrozen();
        if (data().nodes.containsKey(node.getId())) {
            throw new IllegalStateException("InputGraph already contains InputNode with Id=" + node.getId());
        }
        data().nodes.put(node.getId(), node);
        if (data().highestNodeId < node.getId()) {
            data().highestNodeId = node.getId();
        }
        nodeIds = null;
    }

    /**
     * Highest node ID in the graph. Under assumption that node IDs are assigned sequentially
     * (though some IDs may be missing), the value may allow some preallocations or optimizations.
     * Returns -1 for empty graph with no nodes.
     *
     * @return highest node ID.
     */
    public int getHighestNodeId() {
        return data().highestNodeId;
    }

    public InputNode getNode(int nodeId) {
        return data().nodes.get(nodeId);
    }

    public InputNode removeNode(int nodeId) {
        assert !isFrozen();
        InputNode n = data().nodes.remove(nodeId);
        if (n != null) {
            nodeIds = null;
        }
        return n;
    }

    public Collection<InputEdge> getEdges() {
        return Collections.unmodifiableCollection(data().edges);
    }

    public void removeEdge(InputEdge c) {
        assert !isFrozen();
        boolean removed = data().edges.remove(c);
        assert removed;
    }

    public void addEdge(InputEdge c) {
        assert !isFrozen();
        data().edges.add(c);
    }

    public Group getGroup() {
        return parentGroup;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Graph ").append(getName()).append(" ").append(getProperties().toString()).append("\n");
        for (InputNode n : data().nodes.values()) {
            sb.append(n.toString());
            sb.append("\n");
        }

        for (InputEdge c : data().edges) {
            sb.append(c.toString());
            sb.append("\n");
        }

        for (InputBlock b : getBlocks()) {
            sb.append(b.toString());
            sb.append("\n");
        }

        return sb.toString();
    }

    public InputBlock addBlock(String name) {
        assert !isFrozen();
        final InputBlock b = new InputBlock(this, name);
        data().blocks.put(b.getName(), b);
        return b;
    }

    public InputBlock getBlock(String s) {
        return data().blocks.get(s);
    }

    public Collection<InputBlockEdge> getBlockEdges() {
        return Collections.unmodifiableList(data().blockEdges);
    }

    @Override
    public Folder getParent() {
        return parent;
    }

    protected void complete() {
    }

    public boolean containsNode(int nodeId) {
        return getNode(nodeId) != null;
    }

    public boolean isDuplicate() {
        return getProperties().get(PROPNAME_DUPLICATE) != null; // NOI18N
    }

}
