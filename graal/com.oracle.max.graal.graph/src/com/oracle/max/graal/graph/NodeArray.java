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

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;

public class NodeArray extends AbstractList<Node> {

    private final Node node;
    private Node[] nodes;
    private final int fixedLength;
    private int variableLength;

    public NodeArray(Node node, int length) {
        this.node = node;
        this.nodes = new Node[length];
        this.fixedLength = length;
    }

    @Override
    public Iterator<Node> iterator() {
        return Arrays.asList(this.nodes).iterator();
    }

    private Node self() {
        return this.node;
    }

    Node silentSet(int index, Node node) {
        Node result = nodes[index];
        nodes[index] = node;
        return result;
    }

    public AbstractList<Node> variablePart() {
        return new AbstractList<Node>() {

            @Override
            public Node get(int index) {
                checkIndex(index);
                return NodeArray.this.get(fixedLength + index);
            }

            @Override
            public int size() {
                return variableLength;
            }

            public Node set(int index, Node element) {
                checkIndex(index);
                return NodeArray.this.set(fixedLength + index, element);
            }

            public void add(int index, Node element) {
                variableLength++;
                checkIndex(index);
                NodeArray.this.ensureSize();
                for (int i = size() - 1; i > index; i--) {
                    NodeArray.this.nodes[fixedLength + i] = NodeArray.this.nodes[fixedLength + i - 1];
                }
                set(index, element);
            }

            private void checkIndex(int index) {
                if (index < 0 || index >= size()) {
                    throw new IndexOutOfBoundsException();
                }
            }

            @Override
            public Node remove(int index) {
                checkIndex(index);
                Node n = get(index);
                set(index, Node.Null);
                for (int i = index; i < size() - 1; i++) {
                    NodeArray.this.nodes[fixedLength + i] = NodeArray.this.nodes[fixedLength + i + 1];
                }
                NodeArray.this.nodes[fixedLength + size() - 1] = Node.Null;
                variableLength--;
                assert variableLength >= 0;
                return n;
            }
        };
    }

    private void ensureSize() {
        if (size() > nodes.length) {
            nodes = Arrays.copyOf(nodes, (nodes.length + 1) * 2);
        }
    }

    public void setOrExpand(int index, Node node) {
        if (index < 0) {
            throw new IndexOutOfBoundsException();
        }

        while (index >= size()) {
            variablePart().add(Node.Null);
        }

        set(index, node);
    }

    @Override
    public Node set(int index, Node node) {
        assert !self().isDeleted() : "trying to set input/successor of deleted node: " + self().shortName();
        assert node == Node.Null || node.graph == self().graph : "node is from different graph: (this=" + self() + ") and (node=" + node + ")";
        assert node == Node.Null || node.id() != Node.DeletedID : "inserted node must not be deleted";
        assert node != self() || node.getClass().toString().contains("Phi") : "No direct circles allowed in the graph! " + node;

        Node old = get(index);
        if (old != node) {
            silentSet(index, node);
            if (self().inputs == this) {
                if (old != null) {
                    old.usages.remove(self());
                }
                if (node != null) {
                    node.usages.add(self());
                }
            } else {
                assert self().successors == this;
                if (old != null) {
                    old.predecessors.remove(self());
                }
                if (node != null) {
                    node.predecessors.add(self());
                }
            }
        }

        return old;
    }

    public void setAll(NodeArray other) {
        assert size() == other.size();
        for (int i = 0; i < other.size(); i++) {
            set(i, other.get(i));
        }
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public Node get(int index) {
        checkIndex(index);
        assert !self().isDeleted();
        return nodes[index];
    }

    @Override
    public Node[] toArray() {
        return Arrays.copyOf(nodes, size());
    }

    boolean replaceFirstOccurrence(Node toReplace, Node replacement) {
        for (int i = 0; i < size(); i++) {
            if (nodes[i] == toReplace) {
                nodes[i] = replacement;
                return true;
            }
        }
        return false;
    }

    public int remove(Node n) {
        return replace(n, null);
    }

    public int replace(Node toReplace, Node replacement) {
        int result = 0;
        for (int i = 0; i < size(); i++) {
            if (nodes[i] == toReplace) {
                set(i, replacement);
                result++;
            }
        }
        return result;
    }

    int silentRemove(Node n) {
        return silentReplace(n, null);
    }

    int silentReplace(Node toReplace, Node replacement) {
        int result = 0;
        for (int i = 0; i < size(); i++) {
            if (nodes[i] == toReplace) {
                silentSet(i, replacement);
                result++;
            }
        }
        return result;
    }

    @Override
    public int size() {
        return fixedLength + variableLength;
    }

    public void clearAll() {
        for (int i = 0; i < size(); i++) {
            set(i, Node.Null);
        }
    }
}
