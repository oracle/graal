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

import com.oracle.graal.compiler.common.FieldIntrospection.FieldInfo;
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

    public Edges(Type type, int directCount, ArrayList<? extends FieldInfo> edges) {
        super(edges);
        this.type = type;
        this.directCount = directCount;
    }

    public static void translateInto(Edges edges, ArrayList<EdgeInfo> infos) {
        for (int index = 0; index < edges.getCount(); index++) {
            infos.add(new EdgeInfo(edges.offsets[index], edges.getName(index), edges.getType(index)));
        }
    }

    private static Node getNode(Node node, long offset) {
        return (Node) unsafe.getObject(node, offset);
    }

    @SuppressWarnings("unchecked")
    private static NodeList<Node> getNodeList(Node node, long offset) {
        return (NodeList<Node>) unsafe.getObject(node, offset);
    }

    private static void putNode(Node node, long offset, Node value) {
        unsafe.putObject(node, offset, value);
    }

    private static void putNodeList(Node node, long offset, NodeList<?> value) {
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
    public Node getNode(Node node, int index) {
        assert index >= 0 && index < directCount;
        return getNode(node, offsets[index]);
    }

    /**
     * Gets the {@link NodeList} at the end point of a {@linkplain #getDirectCount() direct} edge.
     *
     * @param node one end point of the edge
     * @param index the index of a non-list the edge (must be equal to or greater than
     *            {@link #getDirectCount()})
     * @return the {@link NodeList} at the other edge of the requested edge
     */
    public NodeList<Node> getNodeList(Node node, int index) {
        assert index >= directCount && index < getCount();
        return getNodeList(node, offsets[index]);
    }

    /**
     * Clear edges in a given node. This is accomplished by setting {@linkplain #getDirectCount()
     * direct} edges to null and replacing the lists containing indirect edges with new lists. The
     * latter is important so that this method can be used to clear the edges of cloned nodes.
     *
     * @param node the node whose edges are to be cleared
     */
    public void clear(Node node) {
        int index = 0;
        while (index < getDirectCount()) {
            initializeNode(node, index++, null);
        }
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(node, index);
            int size = list.initialSize;
            NodeList<Node> newList = type == Edges.Type.Inputs ? new NodeInputList<>(node, size) : new NodeSuccessorList<>(node, size);

            // replacing with a new list object is the expected behavior!
            initializeList(node, index, newList);
            index++;
        }
    }

    /**
     * Clear edges in a given node. This is accomplished by setting {@linkplain #getDirectCount()
     * direct} edges to null and replacing the lists containing indirect edges with new lists. The
     * latter is important so that this method can be used to clear the edges of cloned nodes.
     *
     * @param toNode the node whose list edges are to be initialized
     */
    public void initializeLists(Node toNode, Node fromNode) {
        int index = getDirectCount();
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(fromNode, index);
            int size = list.initialSize;
            NodeList<Node> newList = type == Edges.Type.Inputs ? new NodeInputList<>(toNode, size) : new NodeSuccessorList<>(toNode, size);

            // replacing with a new list object is the expected behavior!
            initializeList(toNode, index, newList);
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
        assert fromNode.getNodeClass().getClazz() == toNode.getNodeClass().getClazz();
        int index = 0;
        while (index < getDirectCount()) {
            initializeNode(toNode, index, getNode(fromNode, index));
            index++;
        }
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(toNode, index);
            if (list == null) {
                NodeList<Node> fromList = getNodeList(fromNode, index);
                list = type == Edges.Type.Inputs ? new NodeInputList<>(toNode, fromList) : new NodeSuccessorList<>(toNode, fromList);
                initializeList(toNode, index, list);
            } else {
                list.copy(getNodeList(fromNode, index));
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
        while (index < getDirectCount()) {
            Node edge = getNode(node, index);
            if (edge == key) {
                assert replacement == null || getType(index).isAssignableFrom(replacement.getClass()) : "Can not assign " + replacement.getClass() + " to " + getType(index) + " in " + node;
                initializeNode(node, index, replacement);
                return true;
            }
            index++;
        }
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(node, index);
            assert list != null : this;
            if (list.replaceFirst(key, replacement)) {
                return true;
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
    public void initializeNode(Node node, int index, Node value) {
        putNode(node, offsets[index], value);
    }

    public void initializeList(Node node, int index, NodeList<Node> value) {
        assert index >= directCount;
        putNodeList(node, offsets[index], value);
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
        Node old = getNode(node, offsets[index]);
        putNode(node, offsets[index], value);
        update(node, old, value);
    }

    protected abstract void update(Node node, Node oldValue, Node newValue);

    public boolean contains(Node node, Node value) {
        for (int i = 0; i < directCount; i++) {
            if (getNode(node, i) == value) {
                return true;
            }
        }
        for (int i = directCount; i < getCount(); i++) {
            if (getNodeList(node, i).contains(value)) {
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
        while (index < directCount) {
            if (getNode(other, index) != getNode(node, index)) {
                return false;
            }
            index++;
        }
        while (index < getCount()) {
            NodeList<Node> list = getNodeList(other, index);
            if (!list.equals(getNodeList(node, index))) {
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

        /**
         * Creates an iterator that will iterate over some given edges in a given node.
         */
        EdgesIterator(Node node, Edges edges) {
            this.node = node;
            this.edges = edges;
            index = NOT_ITERABLE;
            subIndex = 0;
            needsForward = true;
        }

        void forward() {
            needsForward = false;
            if (index < edges.getDirectCount()) {
                index++;
                while (index < edges.getDirectCount()) {
                    nextElement = edges.getNode(node, index);
                    if (nextElement != null) {
                        return;
                    }
                    index++;
                }
            } else {
                subIndex++;
            }
            while (index < edges.getCount()) {
                if (subIndex == 0) {
                    list = edges.getNodeList(node, index);
                }
                while (subIndex < list.size()) {
                    nextElement = list.get(subIndex);
                    if (nextElement != null) {
                        return;
                    }
                    subIndex++;
                }
                subIndex = 0;
                index++;
            }
        }

        private Node nextElement() {
            if (needsForward) {
                forward();
            }
            needsForward = true;
            if (index < edges.getCount()) {
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
            if (index < edges.getDirectCount()) {
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
            if (index < edges.getDirectCount()) {
                index++;
                if (index < edges.getDirectCount()) {
                    nextElement = edges.getNode(node, index);
                    return;
                }
            } else {
                subIndex++;
            }
            while (index < edges.getCount()) {
                if (subIndex == 0) {
                    list = edges.getNodeList(node, index);
                }
                if (subIndex < list.size()) {
                    nextElement = list.get(subIndex);
                    return;
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
}
