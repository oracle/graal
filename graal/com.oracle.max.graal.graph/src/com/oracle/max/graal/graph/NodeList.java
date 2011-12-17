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
package com.oracle.max.graal.graph;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public abstract class NodeList<T extends Node> implements Iterable<T>, List<T> {

    protected static final Node[] EMPTY_NODE_ARRAY = new Node[0];

    protected Node[] nodes;
    private int size;
    private int modCount;
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
        if (elements == null) {
            this.size = 0;
            this.nodes = EMPTY_NODE_ARRAY;
            this.initialSize = 0;
        } else {
            this.size = elements.length;
            this.initialSize = elements.length;
            this.nodes = new Node[elements.length];
            for (int i = 0; i < elements.length; i++) {
                this.nodes[i] = elements[i];
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

    public boolean contains(T other) {
        for (int i = 0; i < size; i++) {
            if (nodes[i] == other) {
                return true;
            }
        }
        return false;
    }

    public Iterable<T> snapshot() {
        return new Iterable<T>() {

            @Override
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    private Node[] nodesCopy = Arrays.copyOf(NodeList.this.nodes, NodeList.this.size);
                    private int index = 0;

                    @Override
                    public boolean hasNext() {
                        return index < nodesCopy.length;
                    }

                    @SuppressWarnings("unchecked")
                    @Override
                    public T next() {
                        return (T) nodesCopy[index++];
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
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
    public boolean containsAll(Collection< ? > c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean addAll(Collection< ? extends T> c) {
        for (T e : c) {
            add(e);
        }
        return true;
    }

    @Override
    public boolean addAll(int index, Collection< ? extends T> c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean removeAll(Collection< ? > c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public boolean retainAll(Collection< ? > c) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public void add(int index, T element) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public int lastIndexOf(Object o) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ListIterator<T> listIterator() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ListIterator<T> listIterator(int index) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<T> subList(int fromIndex, int toIndex) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String toString() {
        return Arrays.toString(nodes);
    }
}
