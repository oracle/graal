/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.graph.iterators.*;

public abstract class NodeList<T extends Node> extends AbstractList<T> implements NodeIterable<T>, RandomAccess {

    protected static final Node[] EMPTY_NODE_ARRAY = new Node[0];

    protected Node[] nodes;
    private int size;
    protected final int initialSize;

    protected NodeList() {
        this.nodes = EMPTY_NODE_ARRAY;
        this.initialSize = 0;
    }

    protected NodeList(int initialSize) {
        this.size = initialSize;
        this.initialSize = initialSize;
        this.nodes = new Node[initialSize];
    }

    protected NodeList(T[] elements) {
        if (elements == null || elements.length == 0) {
            this.size = 0;
            this.nodes = EMPTY_NODE_ARRAY;
            this.initialSize = 0;
        } else {
            this.size = elements.length;
            this.initialSize = elements.length;
            this.nodes = new Node[elements.length];
            for (int i = 0; i < elements.length; i++) {
                this.nodes[i] = elements[i];
                assert this.nodes[i] == null || !this.nodes[i].isDeleted() : "Initializing nodelist with deleted element : " + nodes[i];
            }
        }
    }

    protected NodeList(List<? extends T> elements) {
        if (elements == null || elements.isEmpty()) {
            this.size = 0;
            this.nodes = EMPTY_NODE_ARRAY;
            this.initialSize = 0;
        } else {
            this.size = elements.size();
            this.initialSize = elements.size();
            this.nodes = new Node[elements.size()];
            for (int i = 0; i < elements.size(); i++) {
                this.nodes[i] = elements.get(i);
                assert this.nodes[i] == null || !this.nodes[i].isDeleted();
            }
        }
    }

    protected abstract void update(T oldNode, T newNode);

    @Override
    public final int size() {
        return size;
    }

    @Override
    public final boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean isNotEmpty() {
        return size > 0;
    }

    @Override
    public int count() {
        return size;
    }

    protected final void incModCount() {
        modCount++;
    }

    @Override
    public boolean add(T node) {
        incModCount();
        if (size == nodes.length) {
            nodes = Arrays.copyOf(nodes, nodes.length * 2 + 1);
        }
        nodes[size++] = node;
        update(null, node);
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T get(int index) {
        assert index < size() : index + " < " + size();
        return (T) nodes[index];
    }

    public T last() {
        return get(size() - 1);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T set(int index, T node) {
        incModCount();
        T oldValue = (T) nodes[index];
        assert index < size();
        update((T) nodes[index], node);
        nodes[index] = node;
        return oldValue;
    }

    void copy(NodeList<T> other) {
        incModCount();
        nodes = Arrays.copyOf(other.nodes, other.size);
        size = other.size;
    }

    public boolean equals(NodeList<T> other) {
        if (size != other.size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (nodes[i] != other.nodes[i]) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void clear() {
        incModCount();
        for (int i = 0; i < size; i++) {
            update((T) nodes[i], null);
        }
        nodes = EMPTY_NODE_ARRAY;
        size = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean remove(Object node) {
        int i = 0;
        incModCount();
        while (i < size && nodes[i] != node) {
            i++;
        }
        if (i < size) {
            T oldValue = (T) nodes[i];
            i++;
            while (i < size) {
                nodes[i - 1] = nodes[i];
                i++;
            }
            nodes[--size] = null;
            update(oldValue, null);
            return true;
        } else {
            return false;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public T remove(int index) {
        T oldValue = (T) nodes[index];
        int i = index + 1;
        incModCount();
        while (i < size) {
            nodes[i - 1] = nodes[i];
            i++;
        }
        nodes[--size] = null;
        update(oldValue, null);
        return oldValue;
    }

    boolean replaceFirst(Node node, Node other) {
        for (int i = 0; i < size; i++) {
            if (nodes[i] == node) {
                nodes[i] = other;
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {

            private final int expectedModCount = NodeList.this.modCount;
            private int index = 0;

            @Override
            public boolean hasNext() {
                assert expectedModCount == NodeList.this.modCount;
                return index < NodeList.this.size;
            }

            @SuppressWarnings("unchecked")
            @Override
            public T next() {
                assert expectedModCount == NodeList.this.modCount;
                return (T) NodeList.this.nodes[index++];
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public boolean contains(T other) {
        for (int i = 0; i < size; i++) {
            if (nodes[i] == other) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<T> snapshot() {
        return (List<T>) Arrays.asList(Arrays.copyOf(this.nodes, this.size));
    }

    @Override
    public void snapshotTo(Collection<T> to) {
        for (int i = 0; i < size; i++) {
            to.add(get(i));
        }
    }

    @SuppressWarnings("unchecked")
    public void setAll(NodeList<T> values) {
        incModCount();
        for (int i = 0; i < size(); i++) {
            update((T) nodes[i], null);
        }
        nodes = Arrays.copyOf(values.nodes, values.size());
        size = values.size();

        for (int i = 0; i < size(); i++) {
            update(null, (T) nodes[i]);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A> A[] toArray(A[] template) {
        return (A[]) Arrays.copyOf(nodes, size, template.getClass());
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOf(nodes, size);
    }

    protected void replace(T node, T other) {
        incModCount();
        for (int i = 0; i < size(); i++) {
            if (nodes[i] == node) {
                nodes[i] = other;
                update(node, other);
            }
        }
    }

    @Override
    public int indexOf(Object node) {
        for (int i = 0; i < size; i++) {
            if (nodes[i] == node) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        for (T e : c) {
            add(e);
        }
        return true;
    }

    public boolean addAll(T[] c) {
        for (T e : c) {
            add(e);
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < size; i++) {
            if (i != 0) {
                sb.append(", ");
            }
            sb.append(nodes[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    @Override
    public NodeIterable<T> until(final T u) {
        return new FilteredNodeIterable<>(this).until(u);
    }

    @Override
    public NodeIterable<T> until(final Class<? extends T> clazz) {
        return new FilteredNodeIterable<>(this).until(clazz);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <F extends T> NodeIterable<F> filter(Class<F> clazz) {
        return (NodeIterable<F>) new FilteredNodeIterable<>(this).and(NodePredicates.isA(clazz));
    }

    @Override
    public NodeIterable<T> filterInterface(Class<?> iface) {
        return new FilteredNodeIterable<>(this).and(NodePredicates.isAInterface(iface));
    }

    @Override
    public FilteredNodeIterable<T> filter(NodePredicate predicate) {
        return new FilteredNodeIterable<>(this).and(predicate);
    }

    @Override
    public FilteredNodeIterable<T> nonNull() {
        return new FilteredNodeIterable<>(this).and(NodePredicates.isNotNull());
    }

    @Override
    public NodeIterable<T> distinct() {
        return new FilteredNodeIterable<>(this).distinct();
    }

    @Override
    public T first() {
        if (size() > 0) {
            return get(0);
        }
        return null;
    }
}
