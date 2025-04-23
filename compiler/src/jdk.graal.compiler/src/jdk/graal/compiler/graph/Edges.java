/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph;

import static jdk.graal.compiler.graph.Graph.isNodeModificationCountsEnabled;
import static jdk.graal.compiler.graph.Node.NOT_ITERABLE;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.FieldsScanner;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.internal.misc.Unsafe;

/**
 * Describes {@link Node} fields representing the inputs for the node or the node's successors. The
 * primary ordering is that {@linkplain #getDirectCount() direct} edges come before indirect edges.
 * The secondary ordering is determined by {@link FieldsScanner#scan}.
 */
public abstract class Edges extends Fields {

    private static final long MAX_EDGES = 8;
    private static final long MAX_LIST_EDGES = 6;
    static final long OFFSET_MASK = 0xFC;
    static final long LIST_MASK = 0x01;
    static final long NEXT_EDGE = 0x08;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static long computeIterationMask(Type type, int directCount, long[] offsets) {
        long mask = 0;
        if (offsets.length > MAX_EDGES) {
            throw new GraalError("Exceeded maximum of %d edges (%s)", MAX_EDGES, type);
        }
        if (offsets.length - directCount > MAX_LIST_EDGES) {
            throw new GraalError("Exceeded maximum of %d list edges (%s)", MAX_LIST_EDGES, type);
        }

        for (int i = offsets.length - 1; i >= 0; i--) {
            long offset = offsets[i];
            assert ((offset & OFFSET_MASK) == offset) : Assertions.errorMessageContext("field offset too large or has low bits set", offset);
            mask <<= NEXT_EDGE;
            mask |= offset;
            if (i >= directCount) {
                mask |= 0x3;
            }
        }
        return mask;
    }

    /**
     * Constants denoting whether a set of edges are inputs or successors.
     */
    public enum Type {
        Inputs,
        Successors;
    }

    private final int directCount;
    private final Type type;
    private final long iterationMask;

    public Edges(Type type, int directCount, List<? extends FieldsScanner.FieldInfo> edges) {
        super(edges);
        this.type = type;
        this.directCount = directCount;
        this.iterationMask = computeIterationMask(type, directCount, offsets);
    }

    @Override
    public Map.Entry<long[], Long> recomputeOffsetsAndIterationMask(Function<Field, Long> getFieldOffset) {
        Map.Entry<long[], Long> e = super.recomputeOffsetsAndIterationMask(getFieldOffset);
        long[] newOffsets = e.getKey();
        return Map.entry(newOffsets, computeIterationMask(type, directCount, newOffsets));
    }

    public long getIterationMask() {
        return iterationMask;
    }

    public static Node getNodeUnsafe(Node node, long offset) {
        return (Node) UNSAFE.getReference(node, offset);
    }

    @SuppressWarnings("unchecked")
    public static NodeList<Node> getNodeListUnsafe(Node node, long offset) {
        return (NodeList<Node>) UNSAFE.getReference(node, offset);
    }

    public void putNodeUnsafeChecked(Node node, long offset, Node value, int index) {
        verifyUpdateValid(node, index, value);
        putNodeUnsafe(node, offset, value);
    }

    public static void putNodeUnsafe(Node node, long offset, Node value) {
        UNSAFE.putReference(node, offset, value);
    }

    public static void putNodeListUnsafe(Node node, long offset, NodeList<?> value) {
        UNSAFE.putReference(node, offset, value);
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
                initializeList(node, index, newList);
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
        assert fromNode != toNode : fromNode;
        assert fromNode.getNodeClass().getClazz() == toNode.getNodeClass().getClazz() : "fromNode " + fromNode + " toNode " + toNode;
        int index = 0;
        final long[] curOffsets = this.offsets;
        final Type curType = this.type;
        int curDirectCount = getDirectCount();
        while (index < curDirectCount) {
            initializeNode(toNode, index, getNode(fromNode, curOffsets, index));
            index++;
        }
        int curCount = getCount();
        while (index < curCount) {
            NodeList<Node> list = getNodeList(toNode, curOffsets, index);
            NodeList<Node> fromList = getNodeList(fromNode, curOffsets, index);
            // Some Nodes use a null NodeList if they know there are no values so don't introduce
            // an empty NodeList when cloning.
            if (fromList != null) {
                if (list == null || list == fromList) {
                    list = curType == Edges.Type.Inputs ? new NodeInputList<>(toNode, fromList) : new NodeSuccessorList<>(toNode, fromList);
                    initializeList(toNode, index, list);
                } else {
                    list.copy(fromList);
                }
            }
            index++;
        }
    }

    void minimizeSize(Node node) {
        for (int i = getDirectCount(); i < getCount(); i++) {
            NodeList<Node> list = getNodeList(node, offsets, i);
            if (list != null) {
                list.minimizeSize();
            }
        }
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
        putNodeUnsafeChecked(node, offsets[index], value, index);
    }

    public void initializeList(Node node, int index, NodeList<Node> value) {
        verifyUpdateValid(node, index, value);
        putNodeListUnsafe(node, offsets[index], value);
    }

    private void verifyUpdateValid(Node node, int index, Object newValue) {
        if (newValue != null && !getType(index).isAssignableFrom(newValue.getClass())) {
            throw new IllegalArgumentException("Can not assign " + newValue + " to " + getType(index) + " in " + node);
        }
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
        assert index < directCount : index;
        Node old = getNodeUnsafe(node, offsets[index]);
        initializeNode(node, index, value);
        update(node, old, value);
    }

    public abstract void update(Node node, Node oldValue, Node newValue);

    /**
     * An iterator that will iterate over edges.
     *
     * An iterator of this type will not return null values, unless edges are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    private static class EdgesIterator implements Iterator<Position> {
        protected final Node node;
        protected final Edges edges;
        protected int index;
        protected int subIndex;
        protected boolean needsForward;
        protected final int directCount;
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
        }

        void forward() {
            needsForward = false;
            if (index < directCount) {
                index++;
                if (index < directCount) {
                    return;
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
                NodeList<?> list = Edges.getNodeList(node, offsets, index);
                if (list != null) {
                    if (subIndex < list.size()) {
                        return;
                    }
                }
                subIndex = 0;
                index++;
            } while (index < edges.getCount());
        }

        @Override
        public boolean hasNext() {
            if (needsForward) {
                forward();
            }
            return index < edges.getCount();
        }

        @Override
        public Position next() {
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

    private static final class EdgesWithModCountIterator extends EdgesIterator {
        private final int modCount;

        private EdgesWithModCountIterator(Node node, Edges edges) {
            super(node, edges);
            assert isNodeModificationCountsEnabled();
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
        public Position next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }
    }

    public Iterable<Position> getPositionsIterable(final Node node) {
        return () -> {
            if (isNodeModificationCountsEnabled()) {
                return new EdgesWithModCountIterator(node, Edges.this);
            } else {
                return new EdgesIterator(node, Edges.this);
            }
        };
    }

    public Type type() {
        return type;
    }
}
