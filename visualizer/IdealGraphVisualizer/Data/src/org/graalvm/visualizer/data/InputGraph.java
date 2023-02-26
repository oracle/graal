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
package org.graalvm.visualizer.data;

import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_DUPLICATE;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_NAME;
import static org.graalvm.visualizer.data.impl.Defaults.INVALID_INDEX;
import static org.graalvm.visualizer.data.impl.Defaults.NO_BLOCK_NAME;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.services.GraphClassifier;
import java.util.*;

public class InputGraph extends AbstractMutableDocumentItem<InputGraph> implements FolderElement, DumpedElement {

    public static final String DEFAULT_TYPE = "defaultType"; // NOI18N

    private final transient ChangedEvent<InputGraph> propertyChangedEvent = 
            new ThreadedChange<InputGraph>(this, new Group.ExecutorDelegate(this));
    
    private final int dumpId;
    private final String format;
    private final Object[] args;
    private transient final Object id;
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
        freezeProperties();
        this.frozen = true;
    }

    boolean isFrozen() {
        return frozen;
    }

    public String getGraphType() {
        return graphType;
    }

    public void setGraphType(String type) {
        assert !isFrozen();
        this.graphType = type;
    }

    @Override
    public ChangedEvent<InputGraph> getPropertyChangedEvent() {
        return propertyChangedEvent;
    }

    private final GraphData data = new GraphData();

    /**
     * The graph's own data. Data is separated from the InputGraph itself so
     * they can be eventually GCed independently.
     */
    public final static class GraphData {
        private final Map<Integer, InputNode> nodes = new LinkedHashMap<>();
        /**
         * See GR-3584: HashSet enables more efficient edge pruning
         */
        private final Collection<InputEdge> edges = new LinkedHashSet<>();
        private final Map<String, InputBlock> blocks = new LinkedHashMap<>();
        private final List<InputBlockEdge> blockEdges = new ArrayList<>();
        private final Map<Integer, InputBlock> nodeToBlock = new LinkedHashMap<>();
        private int highestNodeId = -1;

        public final Collection<InputNode> getNodes() {
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

    public InputGraph(InputGraph diffGraph1, InputGraph diffGraph2) {
        this.dumpId = INVALID_INDEX;

        String format;
        int argsSize = diffGraph1.args.length + diffGraph2.args.length;
        if (diffGraph1.dumpId < 0) {
            format = diffGraph1.format;
        } else {
            format = "%s: " + diffGraph1.format;
            ++argsSize;
        }
        if (diffGraph2.dumpId < 0) {
            format += ", " + diffGraph2.format;
        } else {
            format += ", %s: " + diffGraph2.format;
            ++argsSize;
        }
        this.format = format;

        Object[] args = new Object[argsSize];
        if (diffGraph1.dumpId < 0) {
            System.arraycopy(diffGraph1.args, 0, args, 0, diffGraph1.args.length);
            if (diffGraph2.dumpId < 0) {
                System.arraycopy(diffGraph2.args, 0, args, diffGraph1.args.length, diffGraph2.args.length);
            } else {
                args[diffGraph1.args.length] = diffGraph2.dumpId;
                System.arraycopy(diffGraph2.args, 0, args, diffGraph1.args.length + 1, diffGraph2.args.length);
            }
        } else {
            args[0] = diffGraph1.dumpId;
            System.arraycopy(diffGraph1.args, 0, args, 1, diffGraph1.args.length);
            if (diffGraph2.dumpId < 0) {
                System.arraycopy(diffGraph2.args, 0, args, diffGraph1.args.length + 1, diffGraph2.args.length);
            } else {
                args[diffGraph1.args.length + 1] = diffGraph2.dumpId;
                System.arraycopy(diffGraph2.args, 0, args, diffGraph1.args.length + 2, diffGraph2.args.length);
            }
        }
        this.args = args;

        setName(dumpId, format, args);
        this.id = diffGraph1.getID().toString() + "/" + diffGraph2.getID().toString();
    }
    
    InputGraph(int dumpId, String format, Object[] args) {
        this(null, dumpId, format, args);
    }

    public InputGraph(Object id, int dumpId, String format, Object[] args) {
        if (id == null) {
            this.id = Group.uniqueIDGenerator.getAndIncrement();
        } else {
            this.id = id;
        }
        this.dumpId = dumpId;
        this.format = format;
        this.args = args;
        setName(dumpId, format, args);
    }
    
    /**
     * Creates named graph. This form is deprecated and should not be used.
     * @param name
     * @deprecated use {@link #InputGraph(java.lang.Object, int, java.lang.String, java.lang.Object[]) 
     */
    @Deprecated
    public InputGraph(String name) {
        this(null, name);
    }

    /**
     * To be used only in tests.
     * @param id unique ID, possibly null
     * @param name graph name
     */
    public InputGraph(Object id, String name) {
        if (id == null) {
            this.id = Group.uniqueIDGenerator.getAndIncrement();
        } else {
            this.id = id;
        }
        args = new Object[0];
        if (name != null) {
            int index = name.indexOf(":");
            if (index == -1) {
                dumpId = INVALID_INDEX;
                format = name;
            } else {
                dumpId = Integer.parseInt(name.substring(0, index));
                format = name.substring(index + 1, name.length()).trim();
            }
        } else {
            dumpId = INVALID_INDEX;
            format = ""; // NOI18N
        }
        setName(dumpId, format, args);
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
            if (graphType == null) {
                graphType = GraphClassifier.INSTANCE.classifyGraphType(getProperties());
            }
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
            Collections.sort(list, InputEdge.OUTGOING_COMPARATOR);
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
            Collections.sort(list, InputEdge.INGOING_COMPARATOR);
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

        Collections.sort(result, InputEdge.OUTGOING_COMPARATOR);

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
     * Determines whether a node changed compared to state in its predecessor
     * graph. If the node is newly introduced, or discarded in this graph, it is
     * considered changed. Otherwise node is changed if its properties are not
     * equal.
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

    private void setName(int dumpId, String format, Object[] args) {
        assert !isFrozen();
        this.getProperties().setProperty(PROPNAME_NAME, makeGraphName(dumpId, format, args));
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
     * Highest node ID in the graph. Under assumption that node IDs are assigned
     * sequentially (though some IDs may be missing), the value may allow some
     * preallocations or optimizations. Returns -1 for empty graph with no
     * nodes.
     *
     * @return highest node ID.
     */
    public int getHighestNodeId() {
        return data().highestNodeId;
    }

    public InputNode getNode(int id) {
        return data().nodes.get(id);
    }

    public InputNode removeNode(int id) {
        assert !isFrozen();
        InputNode n = data().nodes.remove(id);
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

    public boolean containsNode(int id) {
        return getNode(id) != null;
    }

    public boolean isDuplicate() {
        return getProperties().get(PROPNAME_DUPLICATE) != null; // NOI18N
    }

    public static String makeGraphName(int dumpId, String format, Object[] args) {
        assert format != null && args != null;
        if (args.length == 0) {
            return (dumpId < 0) ? format : (dumpId + ": " + format);
        }
        Object[] tmpArgs = args.clone();
        for (int i = 0; i < args.length; ++i) {
            if (args[i] instanceof BinaryReader.Klass) {
                String className = args[i].toString();
                String s = className.substring(className.lastIndexOf(".") + 1); // strip the package name
                int innerClassPos = s.indexOf('$');
                if (innerClassPos > 0) {
                    /* Remove inner class name. */
                    s = s.substring(0, innerClassPos);
                }
                if (s.endsWith("Phase")) {
                    s = s.substring(0, s.length() - "Phase".length());
                }
                tmpArgs[i] = s;
            }
        }
        return (dumpId < 0) ? String.format(format, tmpArgs) : (dumpId + ": " + String.format(format, tmpArgs));
    }
}
