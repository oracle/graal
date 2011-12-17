/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.graph;

import java.util.*;

import com.oracle.max.graal.graph.GraphEvent.NodeEvent;
import com.oracle.max.graal.graph.Node.IterableNodeType;
import com.oracle.max.graal.graph.Node.ValueNumberable;
import com.oracle.max.graal.graph.iterators.*;

/**
 * This class is a graph container, it contains the set of nodes that belong to this graph.
 */
public class Graph {

    protected final String name;

    private static final boolean TIME_TRAVEL = false;

    private final ArrayList<Node> nodes;

    // these two arrays contain one entry for each NodeClass, indexed by NodeClass.iterableId.
    // they contain the first and last pointer to a linked list of all nodes with this type.
    private final ArrayList<Node> nodeCacheFirst;
    private final ArrayList<Node> nodeCacheLast;
    private int deletedNodeCount;
    private int mark;
    private GraphEventLog eventLog;

    ArrayList<Node> usagesDropped = new ArrayList<Node>();
    private final HashMap<CacheEntry, Node> cachedNodes = new HashMap<CacheEntry, Node>();

    private static final class CacheEntry {

        private final Node node;

        public CacheEntry(Node node) {
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
                if (other.node.getNodeClass() == nodeClass) {
                    return nodeClass.valueNumberable() && nodeClass.valueEqual(node, other.node) && nodeClass.edgesEqual(node, other.node);
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

    /**
     * Creates an empty Graph with a given name.
     *
     * @param name the name of the graph, used for debugging purposes
     */
    public Graph(String name) {
        nodes = new ArrayList<Node>(32);
        nodeCacheFirst = new ArrayList<Node>(NodeClass.cacheSize());
        nodeCacheLast = new ArrayList<Node>(NodeClass.cacheSize());
        this.name = name;
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
     * @param name the name of the copy, used for debugging purposes (can be null)
     */
    public Graph copy(String name) {
        Graph copy = new Graph(name);
        copy.addDuplicates(getNodes(), null);
        return copy;
    }

    @Override
    public String toString() {
        return name == null ? super.toString() : "Graph " + name;
    }

    /**
     * Gets the number of live nodes in this graph. That is the number of nodes which have been added to the graph minus the number of deleted nodes.
     * @return the number of live nodes in this graph
     */
    public int getNodeCount() {
        return nodes.size() - getDeletedNodeCount();
    }

    /**
     * Gets the number of node which have been deleted from this graph.
     * @return the number of node which have been deleted from this graph
     */
    public int getDeletedNodeCount() {
        return deletedNodeCount;
    }

    /**
     * Adds a new node to the graph.
     * @param node the node to be added
     * @return the node which was added to the graph
     */
    public <T extends Node> T add(T node) {
        node.initialize(this);
        return node;
    }

    public int getUsagesDroppedNodesCount() {
        return usagesDropped.size();
    }

    public List<Node> getAndCleanUsagesDroppedNodes() {
        ArrayList<Node> result = usagesDropped;
        usagesDropped = new ArrayList<Node>();
        return result;
    }

    /**
     * Adds a new node to the graph, if a <i>similar</i> node already exists in the graph, the provided node will not be added to the graph but the <i>similar</i> node will be returned instead.
     * @param node
     * @return the node which was added to the graph or a <i>similar</i> which was already in the graph.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node & ValueNumberable> T unique(T node) {
        assert checkValueNumberable(node);
        if (!node.getNodeClass().hasOutgoingEdges()) {
            Node cachedNode = cachedNodes.get(new CacheEntry(node));
            if (cachedNode != null && cachedNode.isAlive()) {
                return (T) cachedNode;
            } else {
                Node result = add(node);
                cachedNodes.put(new CacheEntry(node), result);
                return (T) result;
            }
        } else {
            Node duplicate = findDuplicate(node);
            if (duplicate != null) {
                return (T) duplicate;
            }
            return add(node);
        }
    }

    public Node findDuplicate(Node node) {
        if (node.getNodeClass().valueNumberable()) {
            for (Node input : node.inputs()) {
                if (input != null) {
                    for (Node usage : input.usages()) {
                        if (usage != node && node.getNodeClass().valueEqual(node, usage) && node.getNodeClass().edgesEqual(node, usage)) {
                            return usage;
                        }
                    }
                    break;
                }
            }
        }
        return null;
    }

    private boolean checkValueNumberable(Node node) {
        if (!node.getNodeClass().valueNumberable()) {
            throw new VerificationError("node is not valueNumberable").addContext(node);
        }
        return true;
    }

    public void mark() {
        this.mark = nodeIdCount();
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
            if (index < nodes.size()) {
                do {
                    index++;
                } while (index < nodes.size() && nodes.get(index) == null);
            }
        }

        @Override
        public boolean hasNext() {
            checkForDeletedNode();
            return index < nodes.size();
        }

        private void checkForDeletedNode() {
            if (index < nodes.size()) {
                while (index < nodes.size() && nodes.get(index) == null) {
                    index++;
                }
            }
        }

        @Override
        public Node next() {
            try {
                return nodes.get(index);
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
     * Returns an {@link Iterable} providing all nodes added since the last {@link Graph#mark() mark}.
     * @return an {@link Iterable} providing the new nodes
     */
    public Iterable<Node> getNewNodes() {
        final int index = this.mark;
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new NodeIterator(index);
            }
        };
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes.
     * @return an {@link Iterable} providing all the live nodes.
     */
    public NodeIterable<Node> getNodes() {
        return new NodeIterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new NodeIterator();
            }
        };
    }

    private static class TypedNodeIterator<T extends IterableNodeType> implements Iterator<T> {
        private Node current;
        private final Node start;

        public TypedNodeIterator(Node start) {
            if (start != null && start.isDeleted()) {
                this.current = start;
            } else {
                this.current = null;
            }
            this.start = start;
        }

        @Override
        public boolean hasNext() {
            if (current != null) {
                Node next = current.typeCacheNext;
                if (next != null) {
                    while (next.isDeleted()) {
                        next = next.typeCacheNext;
                        if (next == null) {
                            return false;
                        }
                        current.typeCacheNext = next;
                    }
                    return true;
                }
                return false;
            } else {
                return start != null;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            if (current == null) {
                Node result = start;
                current = result;
                return (T) result;
            } else {
                Node result = current.typeCacheNext;
                current = result;
                return (T) result;
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes whose type is compatible with {@code type}.
     * @param type the type of node to return
     * @return an {@link Iterable} providing all the matching nodes.
     */
    public <T extends Node & IterableNodeType> NodeIterable<T> getNodes(final Class<T> type) {
        final Node start = getStartNode(type);
        return new NodeIterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return new TypedNodeIterator<T>(start);
            }
        };
    }

    /**
     * Returns whether the graph contains at least one node of the given type.
     * @param type the type of node that is checked for occurrence
     * @return whether there is at least one such node
     */
    public <T extends Node & IterableNodeType> boolean hasNode(final Class<T> type) {
        return getNodes(type).iterator().hasNext();
    }

    private <T> Node getStartNode(final Class<T> type) {
        int nodeClassId = NodeClass.get(type).iterableId();
        assert nodeClassId != -1 : type + " is not iterable within graphs (missing \"implements IterableNodeType\"?)";
        Node start = nodeCacheFirst.size() <= nodeClassId ? null : nodeCacheFirst.get(nodeClassId);
        return start;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap(this);
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<T>(this);
    }

    public NodeFlood createNodeFlood() {
        return new NodeFlood(this);
    }

    public NodeWorkList createNodeWorkList() {
        return new NodeWorkList(this);
    }

    public NodeWorkList createNodeWorkList(boolean fill, int iterationLimitPerNode) {
        return new NodeWorkList(this, fill, iterationLimitPerNode);
    }

    void register(Node node) {
        assert node.id() == Node.INITIAL_ID;
        int id = nodes.size();
        nodes.add(id, node);

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
        logNodeAdded(node);
    }

    void logNodeAdded(Node node) {
        if (TIME_TRAVEL) {
            log(new GraphEvent.NodeEvent(node, GraphEvent.NodeEvent.Type.ADDED));
        }
    }

    void logNodeDeleted(Node node) {
        if (TIME_TRAVEL) {
            log(new GraphEvent.NodeEvent(node, GraphEvent.NodeEvent.Type.DELETED));
        }
    }

    private void log(NodeEvent nodeEvent) {
        if (eventLog == null) {
            eventLog = new GraphEventLog();
        }
        eventLog.add(nodeEvent);
    }

    public GraphEventLog getEventLog() {
        return eventLog;
    }

    void unregister(Node node) {
        assert !node.isDeleted() : "cannot delete a node twice! node=" + node;
        logNodeDeleted(node);
        nodes.set(node.id(), null);
        deletedNodeCount++;

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
                throw e.addContext(node).addContext(this);
            }
        }
        return true;
    }

    Node getNode(int i) {
        return nodes.get(i);
    }

    /**
     * Returns the number of node ids generated so far.
     * @return the number of node ids generated so far
     */
    int nodeIdCount() {
        return nodes.size();
    }

    /**
     * Adds duplicates of the nodes in {@code nodes} to this graph.
     * This will recreate any edges between the duplicate nodes. The {@code replacement} map can be used to
     * replace a node from the source graph by a given node (which must already be in this graph).
     * Edges between duplicate and replacement nodes will also be recreated so care should be taken
     * regarding the matching of node types in the replacement map.
     *
     * @param nodes the nodes to be duplicated
     * @param replacements the replacement map (can be null if no replacement is to be performed)
     * @return a map which associates the original nodes from {@code nodes} to their duplicates
     */
    public Map<Node, Node> addDuplicates(Iterable<Node> nodes, Map<Node, Node> replacements) {
        return NodeClass.addGraphDuplicate(this, nodes, replacements);
    }
}
