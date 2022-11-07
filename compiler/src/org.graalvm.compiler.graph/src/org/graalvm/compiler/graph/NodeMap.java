/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.debug.GraalError;

public class NodeMap<T> extends NodeIdAccessor implements EconomicMap<Node, T> {

    private static final int MIN_REALLOC_SIZE = 16;

    protected Object[] values;

    public NodeMap(Graph graph) {
        super(graph);
        this.values = new Object[graph.nodeIdCount()];
    }

    public NodeMap(NodeMap<T> copyFrom) {
        super(copyFrom.graph);
        this.values = Arrays.copyOf(copyFrom.values, copyFrom.values.length);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(Node node) {
        check(node);
        return (T) values[getNodeId(node)];
    }

    @SuppressWarnings("unchecked")
    public T getAndGrow(Node node) {
        checkAndGrow(node);
        return (T) values[getNodeId(node)];
    }

    private void checkAndGrow(Node node) {
        if (isNew(node)) {
            this.values = Arrays.copyOf(values, Math.max(MIN_REALLOC_SIZE, graph.nodeIdCount() * 3 / 2));
        }
        check(node);
    }

    @Override
    public boolean isEmpty() {
        throw new UnsupportedOperationException("isEmpty() is not supported for performance reasons");
    }

    @Override
    public boolean containsKey(Node node) {
        if (node.graph() == graph()) {
            return get(node) != null;
        }
        return false;
    }

    public boolean containsValue(Object value) {
        for (Object o : values) {
            if (o == value) {
                return true;
            }
        }
        return false;
    }

    public Graph graph() {
        return graph;
    }

    public void set(Node node, T value) {
        check(node);
        if (!node.isAlive()) {
            throw new GraalGraphError("this node is not alive: " + node);
        }
        values[getNodeId(node)] = value;
    }

    public void setAndGrow(Node node, T value) {
        checkAndGrow(node);
        set(node, value);
    }

    /**
     * @param i
     * @return Return the key for the entry at index {@code i}
     */
    protected Node getKey(int i) {
        return graph.getNode(i);
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException("size() is not supported for performance reasons");
    }

    public int capacity() {
        return values.length;
    }

    public boolean isNew(Node node) {
        return getNodeId(node) >= capacity();
    }

    private boolean check(Node node) {
        GraalError.guarantee(node.graph() == graph, "%s is not part of the graph", node);
        GraalError.guarantee(!isNew(node), "This node was added to the graph after creating the node map : %s", node);
        GraalError.guarantee(node.isAlive(), "this node is not alive: ", node);
        return true;
    }

    @Override
    public void clear() {
        Arrays.fill(values, null);
    }

    /**
     * Return the next value entry of this node map based on {@code currentIndex}. A valid entry in
     * a node map is one where both the {@code key}, i.e., the node in the {@link Graph} as well as
     * the {@code value} are not {@code null}.
     */
    private int getNextValidMapEntry(int currentIndex) {
        int nextValidIndex = currentIndex;
        while (nextValidIndex < NodeMap.this.values.length && (NodeMap.this.values[nextValidIndex] == null || NodeMap.this.getKey(nextValidIndex) == null)) {
            nextValidIndex++;
        }
        return nextValidIndex;
    }

    private abstract class NodeMapIterator<K> implements Iterator<K> {
        protected int index = 0;

        @Override
        public boolean hasNext() {
            forward();
            return index < NodeMap.this.values.length && index < NodeMap.this.graph.nodeIdCount();
        }

        void forward() {
            index = getNextValidMapEntry(index);
        }

        @Override
        public K next() {
            final K val = getValAtIndex();
            index++;
            forward();
            return val;
        }

        abstract K getValAtIndex();
    }

    @Override
    public Iterable<Node> getKeys() {
        return new Iterable<>() {
            @Override
            public Iterator<Node> iterator() {
                return new NodeMapIterator<>() {
                    @Override
                    Node getValAtIndex() {
                        return NodeMap.this.getKey(index);
                    }
                };
            }
        };
    }

    @Override
    public Iterable<T> getValues() {
        return new Iterable<>() {

            @Override
            public Iterator<T> iterator() {
                return new NodeMapIterator<>() {

                    @SuppressWarnings("unchecked")
                    @Override
                    T getValAtIndex() {
                        return (T) NodeMap.this.values[index];
                    }
                };
            }
        };
    }

    @Override
    public MapCursor<Node, T> getEntries() {
        return new MapCursor<>() {

            int current = -1;

            @Override
            public boolean advance() {
                current++;
                current = getNextValidMapEntry(current);
                return current < NodeMap.this.values.length;
            }

            @Override
            public Node getKey() {
                return NodeMap.this.getKey(current);
            }

            @SuppressWarnings("unchecked")
            @Override
            public T getValue() {
                return (T) NodeMap.this.values[current];
            }

            @Override
            public void remove() {
                GraalError.guarantee(NodeMap.this.values[current] != null, "Must only return non null values, index=%d", current);
                NodeMap.this.values[current] = null;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T setValue(T newValue) {
                T oldValue = (T) NodeMap.this.values[current];
                NodeMap.this.values[current] = newValue;
                return oldValue;
            }
        };
    }

    @Override
    public String toString() {
        MapCursor<Node, T> i = getEntries();
        if (!i.advance()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        while (true) {
            Node key = i.getKey();
            T value = i.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value);
            if (!i.advance()) {
                return sb.append('}').toString();
            }
            sb.append(',').append(' ');
        }
    }

    @Override
    public T put(Node key, T value) {
        T result = get(key);
        set(key, value);
        return result;
    }

    @Override
    public T removeKey(Node key) {
        return put(key, null);
    }

    @Override
    public void replaceAll(BiFunction<? super Node, ? super T, ? extends T> function) {
        for (Node n : getKeys()) {
            put(n, function.apply(n, get(n)));
        }
    }
}
