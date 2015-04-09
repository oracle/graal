/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.compiler.common.UnsafeAccess.*;
import static com.oracle.graal.graph.Graph.*;
import static com.oracle.graal.graph.Node.*;

import java.util.*;
import java.util.function.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.NodeClass.EdgeInfo;

/**
 * Describes {@link Node} fields representing the set of inputs for the node or the set of the
 * node's successors.
 */
public abstract class Edges extends Fields {

    /**
     * Constants denoting whether a set of edges are inputs or successors.
     */
    public enum Type {
        Inputs,
        Successors;
    }

    private final int directCount;
    private final Type type;

    public Edges(Type type, int directCount, ArrayList<? extends FieldsScanner.FieldInfo> edges) {
        super(edges);
        this.type = type;
        this.directCount = directCount;
    }

    public static void translateInto(Edges edges, ArrayList<EdgeInfo> infos) {
        for (int index = 0; index < edges.getCount(); index++) {
            infos.add(new EdgeInfo(edges.offsets[index], edges.getName(index), edges.getType(index)));
        }
    }

    private static Node getNodeUnsafe(Node node, long offset) {
        return (Node) unsafe.getObject(node, offset);
    }

    @SuppressWarnings("unchecked")
    private static NodeList<Node> getNodeListUnsafe(Node node, long offset) {
        return (NodeList<Node>) unsafe.getObject(node, offset);
    }

    private static void putNodeUnsafe(Node node, long offset, Node value) {
        unsafe.putObject(node, offset, value);
    }

    private static void putNodeListUnsafe(Node node, long offset, NodeList<?> value) {
        unsafe.putObject(node, offset, value);
    }

    /**
     * Get the number of direct edges represented by this object. A direct edge goes directly to
     * another {@link Node}. An indirect edge goes via a {@link NodeList}.
     */
    public int getDirectCount() {
        return directCount;
    }

    /**
     * Gets the {@link Node} at the end point of a {@linkplain #getDirectCount() direct} edge.
     *
     * @param node one end point of the edge
     * @param index the index of a non-list the edge (must be less than {@link #getDirectCount()})
     * @return the Node at the other edge of the requested edge
     */
    public static Node getNode(Node node, long[] offsets, int index) {
        return getNodeUnsafe(node, offsets[index]);
    }

    /**
     * Gets the {@link NodeList} at the end point of a {@linkplain #getDirectCount() direct} edge.
     *
     * @param node one end point of the edge
     * @param index the index of a non-list the edge (must be equal to or greater than
     *            {@link #getDirectCount()})
     * @return the {@link NodeList} at the other edge of the requested edge
     */
    public static NodeList<Node> getNodeList(Node node, long[] offsets, int index) {
        return getNodeListUnsafe(node, offsets[index]);
    }

    /**
     * Clear edges in a given node. This is accomplished by setting {@linkplain #getDirectCount()
     * direct} edges to null and replacing the lists containing indirect edges with new lists. The
     * latter is important so that this method can be used to clear the edges of cloned nodes.
     *
     * @param node the node whose edges are to be cleared
     */
    public void clear(Node node) {
        final long[] curOffsets = this.offsets;
        final Type curType = this.type;
        int index = 0;
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            initializeNode(node, curOffsets, index++, null);
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(node, curOffsets, index);
            if (list != null) {
                int size = list.initialSize;
                NodeList<Node> newList = curType == Edges.Type.Inputs ? new NodeInputList<>(node, size) : new NodeSuccessorList<>(node, size);

                // replacing with a new list object is the expected behavior!
                initializeList(node, curOffsets, index, newList);
            }
            index++;
        }
    }

    /**
     * Initializes the list edges in a given node based on the size of the list edges in a prototype
     * node.
     *
     * @param node the node whose list edges are to be initialized
     * @param prototype the node whose list edge sizes are used when creating new edge lists
     */
    public void initializeLists(Node node, Node prototype) {
        int index = getDirectCount();
        final long[] curOffsets = this.offsets;
        final Edges.Type curType = this.type;
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(prototype, curOffsets, index);
            if (list != null) {
                int size = list.initialSize;
                NodeList<Node> newList = curType == Edges.Type.Inputs ? new NodeInputList<>(node, size) : new NodeSuccessorList<>(node, size);
                initializeList(node, curOffsets, index, newList);
            }
            index++;
        }
    }

    /**
     * Copies edges from {@code fromNode} to {@code toNode}. The nodes are expected to be of the
     * exact same type.
     *
     * @param fromNode the node from which the edges should be copied.
     * @param toNode the node to which the edges should be copied.
     */
    public void copy(Node fromNode, Node toNode) {
        assert fromNode != toNode;
        assert fromNode.getNodeClass().getClazz() == toNode.getNodeClass().getClazz();
        int index = 0;
        final long[] curOffsets = this.offsets;
        final Type curType = this.type;
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            initializeNode(toNode, curOffsets, index, getNode(fromNode, curOffsets, index));
            index++;
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(toNode, curOffsets, index);
            NodeList<Node> fromList = getNodeList(fromNode, curOffsets, index);
            if (list == null || list == fromList) {
                list = curType == Edges.Type.Inputs ? new NodeInputList<>(toNode, fromList) : new NodeSuccessorList<>(toNode, fromList);
                initializeList(toNode, curOffsets, index, list);
            } else {
                list.copy(fromList);
            }
            index++;
        }
    }

    /**
     * Searches for the first edge in a given node matching {@code key} and if found, replaces it
     * with {@code replacement}.
     *
     * @param node the node whose edges are to be searched
     * @param key the edge to search for
     * @param replacement the replacement for {@code key}
     * @return true if a replacement was made
     */
    public boolean replaceFirst(Node node, Node key, Node replacement) {
        int index = 0;
        final long[] curOffsets = this.getOffsets();
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            Node edge = getNode(node, curOffsets, index);
            if (edge == key) {
                assert replacement == null || getType(index).isAssignableFrom(replacement.getClass()) : "Can not assign " + replacement.getClass() + " to " + getType(index) + " in " + node;
                initializeNode(node, curOffsets, index, replacement);
                return true;
            }
            index++;
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(node, curOffsets, index);
            if (list != null) {
                if (list.replaceFirst(key, replacement)) {
                    return true;
                }
            }
            index++;
        }
        return false;
    }

    @Override
    public void set(Object node, int index, Object value) {
        throw new IllegalArgumentException("Cannot call set on " + this);
    }

    /**
     * Sets the value of a given edge without notifying the new and old nodes on the other end of
     * the edge of the change.
     *
     * @param node the node whose edge is to be updated
     * @param index the index of the edge (between 0 and {@link #getCount()})
     * @param value the node to be written to the edge
     */
    public static void initializeNode(Node node, long[] offsets, int index, Node value) {
        putNodeUnsafe(node, offsets[index], value);
    }

    public static void initializeList(Node node, long[] offsets, int index, NodeList<Node> value) {
        putNodeListUnsafe(node, offsets[index], value);
    }

    /**
     * Sets the value of a given edge and notifies the new and old nodes on the other end of the
     * edge of the change.
     *
     * @param node the node whose edge is to be updated
     * @param index the index of the edge (between 0 and {@link #getCount()})
     * @param value the node to be written to the edge
     */
    public void setNode(Node node, int index, Node value) {
        assert index < directCount;
        Node old = getNodeUnsafe(node, offsets[index]);
        putNodeUnsafe(node, offsets[index], value);
        update(node, old, value);
    }

    public abstract void update(Node node, Node oldValue, Node newValue);

    public boolean contains(Node node, Node value) {
        final long[] curOffsets = this.offsets;
        for (int i = 0; i < directCount; i++) {
            if (getNode(node, curOffsets, i) == value) {
                return true;
            }
        }
        for (int i = directCount; i < getCount(); i++) {
            NodeList<?> curList = getNodeList(node, curOffsets, i);
            if (curList != null && curList.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if the edges of two given nodes are the same.
     */
    public boolean areEqualIn(Node node, Node other) {
        assert node.getNodeClass().getClazz() == other.getNodeClass().getClazz();
        int index = 0;
        final long[] curOffsets = this.offsets;
        while (index < directCount) {
            if (getNode(other, curOffsets, index) != getNode(node, curOffsets, index)) {
                return false;
            }
            index++;
        }
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(other, curOffsets, index);
            if (!Objects.equals(list, getNodeList(node, curOffsets, index))) {
                return false;
            }
            index++;
        }
        return true;
    }

    /**
     * An iterator that will iterate over edges.
     *
     * An iterator of this type will not return null values, unless edges are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    private static class EdgesIterator implements NodePosIterator {
        protected final Node node;
        protected final Edges edges;
        protected int index;
        protected int subIndex;
        NodeList<Node> list;
        protected boolean needsForward;
        protected Node nextElement;
        protected final int directCount;
        protected final int count;
        protected final long[] offsets;

        /**
         * Creates an iterator that will iterate over some given edges in a given node.
         */
        EdgesIterator(Node node, Edges edges) {
            this.node = node;
            this.edges = edges;
            index = NOT_ITERABLE;
            subIndex = 0;
            needsForward = true;
            this.directCount = edges.getDirectCount();
            this.offsets = edges.getOffsets();
            this.count = edges.getCount();
        }

        void forward() {
            needsForward = false;
            if (index < directCount) {
                index++;
                while (index < directCount) {
                    nextElement = Edges.getNode(node, offsets, index);
                    if (nextElement != null) {
                        return;
                    }
                    index++;
                }
            } else {
                subIndex++;
            }
            if (index < edges.getCount()) {
                forwardNodeList();
            }
        }

        private void forwardNodeList() {
            do {
                if (subIndex == 0) {
                    list = Edges.getNodeList(node, offsets, index);
                }
                if (list != null) {
                    while (subIndex < list.size()) {
                        nextElement = list.get(subIndex);
                        if (nextElement != null) {
                            return;
                        }
                        subIndex++;
                    }
                }
                subIndex = 0;
                index++;
            } while (index < edges.getCount());
        }

        private Node nextElement() {
            if (needsForward) {
                forward();
            }
            needsForward = true;
            if (index < count) {
                return nextElement;
            }
            throw new NoSuchElementException();
        }

        @Override
        public boolean hasNext() {
            if (needsForward) {
                forward();
            }
            return index < edges.getCount();
        }

        @Override
        public Node next() {
            return nextElement();
        }

        public Position nextPosition() {
            if (needsForward) {
                forward();
            }
            needsForward = true;
            if (index < directCount) {
                return new Position(edges, index, NOT_ITERABLE);
            } else {
                return new Position(edges, index, subIndex);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class AllEdgesIterator extends EdgesIterator {

        AllEdgesIterator(Node node, Edges edges) {
            super(node, edges);
        }

        @Override
        void forward() {
            needsForward = false;
            if (index < directCount) {
                index++;
                if (index < edges.getDirectCount()) {
                    nextElement = Edges.getNode(node, edges.getOffsets(), index);
                    return;
                }
            } else {
                subIndex++;
            }
            while (index < edges.getCount()) {
                if (subIndex == 0) {
                    list = Edges.getNodeList(node, edges.getOffsets(), index);
                }
                if (list != null) {
                    if (subIndex < list.size()) {
                        nextElement = list.get(subIndex);
                        return;
                    }
                }
                subIndex = 0;
                index++;
            }
        }
    }

    private static final class EdgesWithModCountIterator extends EdgesIterator {
        private final int modCount;

        private EdgesWithModCountIterator(Node node, Edges edges) {
            super(node, edges);
            assert MODIFICATION_COUNTS_ENABLED;
            this.modCount = node.modCount();
        }

        @Override
        public boolean hasNext() {
            try {
                return super.hasNext();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Node next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Position nextPosition() {
            try {
                return super.nextPosition();
            } finally {
                assert modCount == node.modCount();
            }
        }
    }

    static int cnt1;
    static int cnt2;

    public NodeClassIterable getIterable(final Node node) {
        return new NodeClassIterable() {

            @Override
            public EdgesIterator iterator() {
                if (MODIFICATION_COUNTS_ENABLED) {
                    return new EdgesWithModCountIterator(node, Edges.this);
                } else {
                    return new EdgesIterator(node, Edges.this);
                }
            }

            public EdgesIterator withNullIterator() {
                return new AllEdgesIterator(node, Edges.this);
            }

            @Override
            public boolean contains(Node other) {
                return Edges.this.contains(node, other);
            }
        };
    }

    public Type type() {
        return type;
    }

    public void accept(Node node, BiConsumer<Node, Node> consumer) {
        int index = 0;
        int curDirectCount = this.directCount;
        final long[] curOffsets = this.offsets;
        while (index < curDirectCount) {
            Node curNode = getNode(node, curOffsets, index);
            if (curNode != null) {
                consumer.accept(node, curNode);
            }
            index++;
        }
        int count = curOffsets.length;
        while (index < count) {
            NodeList<Node> list = getNodeList(node, curOffsets, index);
            acceptHelper(node, consumer, list);
            index++;
        }
    }

    private static void acceptHelper(Node node, BiConsumer<Node, Node> consumer, NodeList<Node> list) {
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    consumer.accept(node, curNode);
                }
            }
        }
    }

    public long[] getOffsets() {
        return this.offsets;
    }

    public void pushAll(Node node, NodeStack stack) {
        int index = 0;
        int curDirectCount = this.directCount;
        final long[] curOffsets = this.offsets;
        while (index < curDirectCount) {
            Node curNode = getNode(node, curOffsets, index);
            if (curNode != null) {
                stack.push(curNode);
            }
            index++;
        }
        int count = curOffsets.length;
        while (index < count) {
            NodeList<Node> list = getNodeList(node, curOffsets, index);
            pushAllHelper(stack, list);
            index++;
        }
    }

    private static void pushAllHelper(NodeStack stack, NodeList<Node> list) {
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    stack.push(curNode);
                }
            }
        }
    }
}
