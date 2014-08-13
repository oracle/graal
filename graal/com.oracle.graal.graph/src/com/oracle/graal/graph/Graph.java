/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.graph.iterators.*;

/**
 * This class is a graph container, it contains the set of nodes that belong to this graph.
 */
public class Graph {

    public final String name;

    /**
     * The set of nodes in the graph, ordered by {@linkplain #register(Node) registration} time.
     */
    private Node[] nodes;

    /**
     * The number of valid entries in {@link #nodes}.
     */
    private int nodesSize;

    /**
     * Records the modification count for nodes. This is only used in assertions.
     */
    private int[] nodeModCounts;

    /**
     * Records the modification count for nodes' usage lists. This is only used in assertions.
     */
    private int[] nodeUsageModCounts;

    // these two arrays contain one entry for each NodeClass, indexed by NodeClass.iterableId.
    // they contain the first and last pointer to a linked list of all nodes with this type.
    private final ArrayList<Node> nodeCacheFirst;
    private final ArrayList<Node> nodeCacheLast;
    private int nodesDeletedSinceLastCompression;
    private int nodesDeletedBeforeLastCompression;

    /**
     * The number of times this graph has been compressed.
     */
    int compressions;

    NodeEventListener nodeEventListener;
    private final HashMap<CacheEntry, Node> cachedNodes = new HashMap<>();

    /*
     * Indicates that the graph should no longer be modified. Frozen graphs can be used my multiple
     * threads so it's only safe to read them.
     */
    private boolean isFrozen = false;

    private static final class CacheEntry {

        private final Node node;

        public CacheEntry(Node node) {
            assert node.getNodeClass().valueNumberable();
            assert node.getNodeClass().isLeafNode();
            this.node = node;
        }

        @Override
        public int hashCode() {
            return node.getNodeClass().valueNumber(node);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof CacheEntry) {
                CacheEntry other = (CacheEntry) obj;
                NodeClass nodeClass = node.getNodeClass();
                if (other.node.getClass() == node.getClass()) {
                    return nodeClass.valueEqual(node, other.node);
                }
            }
            return false;
        }
    }

    /**
     * Creates an empty Graph with no name.
     */
    public Graph() {
        this(null);
    }

    static final boolean MODIFICATION_COUNTS_ENABLED = assertionsEnabled();

    /**
     * Determines if assertions are enabled for the {@link Graph} class.
     */
    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    private static final int INITIAL_NODES_SIZE = 32;

    /**
     * Creates an empty Graph with a given name.
     *
     * @param name the name of the graph, used for debugging purposes
     */
    public Graph(String name) {
        nodes = new Node[INITIAL_NODES_SIZE];
        nodeCacheFirst = new ArrayList<>(NodeClass.cacheSize());
        nodeCacheLast = new ArrayList<>(NodeClass.cacheSize());
        this.name = name;
        if (MODIFICATION_COUNTS_ENABLED) {
            nodeModCounts = new int[INITIAL_NODES_SIZE];
            nodeUsageModCounts = new int[INITIAL_NODES_SIZE];
        }
    }

    int extractOriginalNodeId(Node node) {
        int id = node.id;
        if (id <= Node.DELETED_ID_START) {
            id = Node.DELETED_ID_START - id;
        }
        return id;
    }

    int modCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeModCounts.length) {
            return nodeModCounts[id];
        }
        return 0;
    }

    void incModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeModCounts.length) {
                nodeModCounts = Arrays.copyOf(nodeModCounts, id + 30);
            }
            nodeModCounts[id]++;
        } else {
            assert false;
        }
    }

    int usageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeUsageModCounts.length) {
            return nodeUsageModCounts[id];
        }
        return 0;
    }

    void incUsageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeUsageModCounts.length) {
                nodeUsageModCounts = Arrays.copyOf(nodeUsageModCounts, id + 30);
            }
            nodeUsageModCounts[id]++;
        } else {
            assert false;
        }
    }

    /**
     * Creates a copy of this graph.
     */
    public Graph copy() {
        return copy(name);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     */
    public Graph copy(String newName) {
        Graph copy = new Graph(newName);
        copy.addDuplicates(getNodes(), this, this.getNodeCount(), (Map<Node, Node>) null);
        return copy;
    }

    @Override
    public String toString() {
        return name == null ? super.toString() : "Graph " + name;
    }

    /**
     * Gets the number of live nodes in this graph. That is the number of nodes which have been
     * added to the graph minus the number of deleted nodes.
     *
     * @return the number of live nodes in this graph
     */
    public int getNodeCount() {
        return nodesSize - getNodesDeletedSinceLastCompression();
    }

    /**
     * Gets the number of times this graph has been {@linkplain #maybeCompress() compressed}. Node
     * identifiers are only stable between compressions. To ensure this constraint is observed, any
     * entity relying upon stable node identifiers should use {@link NodeIdAccessor}.
     */
    public int getCompressions() {
        return compressions;
    }

    /**
     * Gets the number of nodes which have been deleted from this graph since it was last
     * {@linkplain #maybeCompress() compressed}.
     */
    public int getNodesDeletedSinceLastCompression() {
        return nodesDeletedSinceLastCompression;
    }

    /**
     * Gets the total number of nodes which have been deleted from this graph.
     */
    public int getTotalNodesDeleted() {
        return nodesDeletedSinceLastCompression + nodesDeletedBeforeLastCompression;
    }

    /**
     * Adds a new node to the graph.
     *
     * @param node the node to be added
     * @return the node which was added to the graph
     */
    public <T extends Node> T add(T node) {
        if (node.getNodeClass().valueNumberable()) {
            throw new IllegalStateException("Using add for value numberable node. Consider using either unique or addWithoutUnique.");
        }
        return addHelper(node);
    }

    public <T extends Node> T addWithoutUnique(T node) {
        return addHelper(node);
    }

    public <T extends Node> T addOrUnique(T node) {
        if (node.getNodeClass().valueNumberable()) {
            return uniqueHelper(node, true);
        }
        return add(node);
    }

    public <T extends Node> T addOrUniqueWithInputs(T node) {
        NodeClassIterator iterator = node.inputs().iterator();
        while (iterator.hasNext()) {
            Position pos = iterator.nextPosition();
            Node input = pos.get(node);
            if (input != null && !input.isAlive()) {
                assert !input.isDeleted();
                pos.initialize(node, addOrUniqueWithInputs(input));
            }
        }
        if (node.getNodeClass().valueNumberable()) {
            return uniqueHelper(node, true);
        }
        return add(node);
    }

    private <T extends Node> T addHelper(T node) {
        node.initialize(this);
        return node;
    }

    /**
     * The type of events sent to a {@link NodeEventListener}.
     */
    public enum NodeEvent {
        /**
         * A node's input is changed.
         */
        INPUT_CHANGED,

        /**
         * A node's {@linkplain Node#usages() usages} count dropped to zero.
         */
        ZERO_USAGES,

        /**
         * A node was added to a graph.
         */
        NODE_ADDED;
    }

    /**
     * Client interested in one or more node related events.
     */
    public interface NodeEventListener {

        /**
         * Default handler for events.
         *
         * @param e an event
         * @param node the node related to {@code e}
         */
        default void event(NodeEvent e, Node node) {
        }

        /**
         * Notifies this listener of a change in a node's inputs.
         *
         * @param node a node who has had one of its inputs changed
         */
        default void inputChanged(Node node) {
            event(NodeEvent.INPUT_CHANGED, node);
        }

        /**
         * Notifies this listener of a node becoming unused.
         *
         * @param node a node whose {@link Node#usages()} just became empty
         */
        default void usagesDroppedToZero(Node node) {
            event(NodeEvent.ZERO_USAGES, node);
        }

        /**
         * Notifies this listener of an added node.
         *
         * @param node a node that was just added to the graph
         */
        default void nodeAdded(Node node) {
            event(NodeEvent.NODE_ADDED, node);
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with the enclosing graph until this object is
     * {@linkplain #close() closed}.
     */
    public final class NodeEventScope implements AutoCloseable {
        NodeEventScope(NodeEventListener listener) {
            if (nodeEventListener == null) {
                nodeEventListener = listener;
            } else {
                nodeEventListener = new ChainedNodeEventListener(listener, nodeEventListener);
            }
        }

        public void close() {
            assert nodeEventListener != null;
            if (nodeEventListener instanceof ChainedNodeEventListener) {
                nodeEventListener = ((ChainedNodeEventListener) nodeEventListener).next;
            } else {
                nodeEventListener = null;
            }
        }
    }

    private static class ChainedNodeEventListener implements NodeEventListener {

        NodeEventListener head;
        NodeEventListener next;

        ChainedNodeEventListener(NodeEventListener head, NodeEventListener next) {
            this.head = head;
            this.next = next;
        }

        public void nodeAdded(Node node) {
            head.nodeAdded(node);
            next.nodeAdded(node);
        }

        public void inputChanged(Node node) {
            head.inputChanged(node);
            next.inputChanged(node);
        }

        public void usagesDroppedToZero(Node node) {
            head.usagesDroppedToZero(node);
            next.usagesDroppedToZero(node);
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with this graph. This should be used in
     * conjunction with try-with-resources statement as follows:
     *
     * <pre>
     * try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
     *     // make changes to the graph
     * }
     * </pre>
     */
    public NodeEventScope trackNodeEvents(NodeEventListener listener) {
        return new NodeEventScope(listener);
    }

    /**
     * Looks for a node <i>similar</i> to {@code node} and returns it if found. Otherwise
     * {@code node} is added to this graph and returned.
     *
     * @return a node similar to {@code node} if one exists, otherwise {@code node}
     */
    public <T extends Node & ValueNumberable> T unique(T node) {
        return uniqueHelper(node, true);
    }

    @SuppressWarnings("unchecked")
    <T extends Node> T uniqueHelper(T node, boolean addIfMissing) {
        assert node.getNodeClass().valueNumberable();
        Node other = this.findDuplicate(node);
        if (other != null) {
            return (T) other;
        } else {
            Node result = addIfMissing ? addHelper(node) : node;
            if (node.getNodeClass().isLeafNode()) {
                putNodeIntoCache(result);
            }
            return (T) result;
        }
    }

    void putNodeIntoCache(Node node) {
        assert node.graph() == this || node.graph() == null;
        assert node.getNodeClass().valueNumberable();
        assert node.getNodeClass().isLeafNode() : node.getClass();
        cachedNodes.put(new CacheEntry(node), node);
    }

    Node findNodeInCache(Node node) {
        CacheEntry key = new CacheEntry(node);
        Node result = cachedNodes.get(key);
        if (result != null && result.isDeleted()) {
            cachedNodes.remove(key);
            return null;
        }
        return result;
    }

    public Node findDuplicate(Node node) {
        NodeClass nodeClass = node.getNodeClass();
        assert nodeClass.valueNumberable();
        if (nodeClass.isLeafNode()) {
            Node cachedNode = findNodeInCache(node);
            if (cachedNode != null) {
                return cachedNode;
            } else {
                return null;
            }
        } else {

            int minCount = Integer.MAX_VALUE;
            Node minCountNode = null;
            for (Node input : node.inputs()) {
                if (input != null && input.recordsUsages()) {
                    int estimate = input.getUsageCountUpperBound();
                    if (estimate == 0) {
                        return null;
                    } else if (estimate < minCount) {
                        minCount = estimate;
                        minCountNode = input;
                    }
                }
            }
            if (minCountNode != null) {
                for (Node usage : minCountNode.usages()) {
                    if (usage != node && nodeClass == usage.getNodeClass() && nodeClass.valueEqual(node, usage) && nodeClass.edgesEqual(node, usage)) {
                        return usage;
                    }
                }
                return null;
            }
            return null;
        }
    }

    public boolean isNew(Mark mark, Node node) {
        return node.id >= mark.getValue();
    }

    /**
     * A snapshot of the {@linkplain Graph#getNodeCount() live node count} in a graph.
     */
    public static class Mark extends NodeIdAccessor {
        private final int value;

        Mark(Graph graph) {
            super(graph);
            this.value = graph.nodeIdCount();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Mark) {
                Mark other = (Mark) obj;
                return other.getValue() == getValue() && other.getGraph() == getGraph();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value ^ (epoch + 11);
        }

        /**
         * Determines if this mark is positioned at the first live node in the graph.
         */
        public boolean isStart() {
            return value == 0;
        }

        /**
         * Gets the {@linkplain Graph#getNodeCount() live node count} of the associated graph when
         * this object was created.
         */
        int getValue() {
            return value;
        }

        /**
         * Determines if this mark still represents the {@linkplain Graph#getNodeCount() live node
         * count} of the graph.
         */
        public boolean isCurrent() {
            return value == graph.nodeIdCount();
        }
    }

    /**
     * Gets a mark that can be used with {@link #getNewNodes}.
     */
    public Mark getMark() {
        return new Mark(this);
    }

    private class NodeIterator implements Iterator<Node> {

        private int index;

        public NodeIterator() {
            this(0);
        }

        public NodeIterator(int index) {
            this.index = index - 1;
            forward();
        }

        private void forward() {
            if (index < nodesSize) {
                do {
                    index++;
                } while (index < nodesSize && nodes[index] == null);
            }
        }

        @Override
        public boolean hasNext() {
            checkForDeletedNode();
            return index < nodesSize;
        }

        private void checkForDeletedNode() {
            if (index < nodesSize) {
                while (index < nodesSize && nodes[index] == null) {
                    index++;
                }
            }
        }

        @Override
        public Node next() {
            try {
                return nodes[index];
            } finally {
                forward();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns an {@link Iterable} providing all nodes added since the last {@link Graph#getMark()
     * mark}.
     */
    public NodeIterable<Node> getNewNodes(Mark mark) {
        final int index = mark.getValue();
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new NodeIterator(index);
            }
        };
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes.
     *
     * @return an {@link Iterable} providing all the live nodes.
     */
    public NodeIterable<Node> getNodes() {
        return new NodeIterable<Node>() {

            @Override
            public Iterator<Node> iterator() {
                return new NodeIterator();
            }

            @Override
            public int count() {
                return getNodeCount();
            }
        };
    }

    @NodeInfo
    static class PlaceHolderNode extends Node {

    }

    private static final Node PLACE_HOLDER = new PlaceHolderNode();

    /**
     * When the percent of live nodes in {@link #nodes} fall below this number, a call to
     * {@link #maybeCompress()} will actually do compression.
     */
    public static final int COMPRESSION_THRESHOLD = Integer.getInteger("graal.graphCompressionThreshold", 70);

    private static final DebugMetric GraphCompressions = Debug.metric("GraphCompressions");

    /**
     * If the {@linkplain #COMPRESSION_THRESHOLD compression threshold} is met, the list of nodes is
     * compressed such that all non-null entries precede all null entries while preserving the
     * ordering between the nodes within the list.
     */
    public boolean maybeCompress() {
        if (Debug.isDumpEnabledForMethod() || Debug.isLogEnabledForMethod()) {
            return false;
        }
        int liveNodeCount = getNodeCount();
        int liveNodePercent = liveNodeCount * 100 / nodesSize;
        if (COMPRESSION_THRESHOLD == 0 || liveNodePercent >= COMPRESSION_THRESHOLD) {
            return false;
        }
        GraphCompressions.increment();
        int nextId = 0;
        for (int i = 0; nextId < liveNodeCount; i++) {
            Node n = nodes[i];
            if (n != null) {
                assert n.id == i;
                if (i != nextId) {
                    assert n.id > nextId;
                    n.id = nextId;
                    nodes[nextId] = n;
                    nodes[i] = null;
                }
                nextId++;
            }
        }
        if (MODIFICATION_COUNTS_ENABLED) {
            // This will cause any current iteration to fail with an assertion
            Arrays.fill(nodeModCounts, 0);
            Arrays.fill(nodeUsageModCounts, 0);
        }
        nodesSize = nextId;
        compressions++;
        nodesDeletedBeforeLastCompression += nodesDeletedSinceLastCompression;
        nodesDeletedSinceLastCompression = 0;
        return true;
    }

    private class TypedNodeIterator<T extends IterableNodeType> implements Iterator<T> {

        private final int[] ids;
        private final Node[] current;

        private int currentIdIndex;
        private boolean needsForward;

        public TypedNodeIterator(NodeClass clazz) {
            ids = clazz.iterableIds();
            currentIdIndex = 0;
            current = new Node[ids.length];
            Arrays.fill(current, PLACE_HOLDER);
            needsForward = true;
        }

        private Node findNext() {
            if (needsForward) {
                forward();
            } else {
                Node c = current();
                Node afterDeleted = skipDeleted(c);
                if (afterDeleted == null) {
                    needsForward = true;
                } else if (c != afterDeleted) {
                    setCurrent(afterDeleted);
                }
            }
            if (needsForward) {
                return null;
            }
            return current();
        }

        private Node skipDeleted(Node node) {
            Node n = node;
            while (n != null && n.isDeleted()) {
                n = n.typeCacheNext;
            }
            return n;
        }

        private void forward() {
            needsForward = false;
            int startIdx = currentIdIndex;
            while (true) {
                Node next;
                if (current() == PLACE_HOLDER) {
                    next = getStartNode(ids[currentIdIndex]);
                } else {
                    next = current().typeCacheNext;
                }
                next = skipDeleted(next);
                if (next == null) {
                    currentIdIndex++;
                    if (currentIdIndex >= ids.length) {
                        currentIdIndex = 0;
                    }
                    if (currentIdIndex == startIdx) {
                        needsForward = true;
                        return;
                    }
                } else {
                    setCurrent(next);
                    break;
                }
            }
        }

        private Node current() {
            return current[currentIdIndex];
        }

        private void setCurrent(Node n) {
            current[currentIdIndex] = n;
        }

        @Override
        public boolean hasNext() {
            return findNext() != null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            Node result = findNext();
            if (result == null) {
                throw new NoSuchElementException();
            }
            needsForward = true;
            return (T) result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes whose type is compatible with
     * {@code type}.
     *
     * @param type the type of node to return
     * @return an {@link Iterable} providing all the matching nodes
     */
    public <T extends Node & IterableNodeType> NodeIterable<T> getNodes(final Class<T> type) {
        final NodeClass nodeClass = NodeClass.get(type);
        return new NodeIterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return new TypedNodeIterator<>(nodeClass);
            }
        };
    }

    /**
     * Returns whether the graph contains at least one node of the given type.
     *
     * @param type the type of node that is checked for occurrence
     * @return whether there is at least one such node
     */
    public <T extends Node & IterableNodeType> boolean hasNode(final Class<T> type) {
        return getNodes(type).iterator().hasNext();
    }

    private Node getStartNode(int iterableId) {
        Node start = nodeCacheFirst.size() <= iterableId ? null : nodeCacheFirst.get(iterableId);
        return start;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap(this);
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<>(this);
    }

    public NodeFlood createNodeFlood() {
        return new NodeFlood(this);
    }

    public NodeWorkList createNodeWorkList() {
        return new NodeWorkList.SingletonNodeWorkList(this);
    }

    public NodeWorkList createIterativeNodeWorkList(boolean fill, int iterationLimitPerNode) {
        return new NodeWorkList.IterativeNodeWorkList(this, fill, iterationLimitPerNode);
    }

    void register(Node node) {
        assert !isFrozen();
        assert node.id() == Node.INITIAL_ID;
        if (nodes.length == nodesSize) {
            nodes = Arrays.copyOf(nodes, (nodesSize * 2) + 1);
        }
        int id = nodesSize;
        nodes[id] = node;
        nodesSize++;

        int nodeClassId = node.getNodeClass().iterableId();
        if (nodeClassId != NodeClass.NOT_ITERABLE) {
            while (nodeCacheFirst.size() <= nodeClassId) {
                nodeCacheFirst.add(null);
                nodeCacheLast.add(null);
            }
            Node prev = nodeCacheLast.get(nodeClassId);
            if (prev != null) {
                prev.typeCacheNext = node;
            } else {
                nodeCacheFirst.set(nodeClassId, node);
            }
            nodeCacheLast.set(nodeClassId, node);
        }

        node.id = id;
        if (nodeEventListener != null) {
            nodeEventListener.nodeAdded(node);
        }
    }

    void unregister(Node node) {
        assert !isFrozen();
        assert !node.isDeleted() : "cannot delete a node twice! node=" + node;
        nodes[node.id] = null;
        nodesDeletedSinceLastCompression++;

        // nodes aren't removed from the type cache here - they will be removed during iteration
    }

    public boolean verify() {
        for (Node node : getNodes()) {
            try {
                try {
                    assert node.verify();
                } catch (AssertionError t) {
                    throw new GraalInternalError(t);
                } catch (RuntimeException t) {
                    throw new GraalInternalError(t);
                }
            } catch (GraalInternalError e) {
                throw GraalGraphInternalError.transformAndAddContext(e, node).addContext(this);
            }
        }
        return true;
    }

    Node getNode(int i) {
        return nodes[i];
    }

    /**
     * Returns the number of node ids generated so far.
     *
     * @return the number of node ids generated so far
     */
    int nodeIdCount() {
        return nodesSize;
    }

    /**
     * Adds duplicates of the nodes in {@code nodes} to this graph. This will recreate any edges
     * between the duplicate nodes. The {@code replacement} map can be used to replace a node from
     * the source graph by a given node (which must already be in this graph). Edges between
     * duplicate and replacement nodes will also be recreated so care should be taken regarding the
     * matching of node types in the replacement map.
     *
     * @param newNodes the nodes to be duplicated
     * @param replacementsMap the replacement map (can be null if no replacement is to be performed)
     * @return a map which associates the original nodes from {@code nodes} to their duplicates
     */
    public Map<Node, Node> addDuplicates(Iterable<Node> newNodes, final Graph oldGraph, int estimatedNodeCount, Map<Node, Node> replacementsMap) {
        DuplicationReplacement replacements;
        if (replacementsMap == null) {
            replacements = null;
        } else {
            replacements = new MapReplacement(replacementsMap);
        }
        return addDuplicates(newNodes, oldGraph, estimatedNodeCount, replacements);
    }

    public interface DuplicationReplacement {

        Node replacement(Node original);
    }

    private static final class MapReplacement implements DuplicationReplacement {

        private final Map<Node, Node> map;

        public MapReplacement(Map<Node, Node> map) {
            this.map = map;
        }

        @Override
        public Node replacement(Node original) {
            Node replacement = map.get(original);
            return replacement != null ? replacement : original;
        }

    }

    @SuppressWarnings("all")
    public Map<Node, Node> addDuplicates(Iterable<Node> newNodes, final Graph oldGraph, int estimatedNodeCount, DuplicationReplacement replacements) {
        return NodeClass.addGraphDuplicate(this, oldGraph, estimatedNodeCount, newNodes, replacements);
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    public void freeze() {
        this.isFrozen = true;
    }
}
