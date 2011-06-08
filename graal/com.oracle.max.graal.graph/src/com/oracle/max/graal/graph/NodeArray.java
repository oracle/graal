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
    final Node[] nodes;

    public NodeArray(Node node, int length) {
        this.node = node;
        this.nodes = new Node[length];
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

    @Override
    public Node set(int index, Node node) {
        assert node == Node.Null || node.graph == self().graph : "node is from different graph: (this=" + self() + ") and (node=" + node + ")";
        assert node == Node.Null || node.id() != Node.DeletedID : "inserted node must not be deleted";
        Node old = nodes[index];

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
                    for (int i = 0; i < old.predecessors.size(); ++i) {
                        Node cur = old.predecessors.get(i);
                        if (cur == self() && old.predecessorsIndex.get(i) == index) {
                            old.predecessors.remove(i);
                            old.predecessorsIndex.remove(i);
                        }
                    }
                }
                if (node != null) {
                    node.predecessors.add(self());
                    node.predecessorsIndex.add(index);
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

    @Override
    public Node get(int index) {
        return nodes[index];
    }

    @Override
    public Node[] toArray() {
        return Arrays.copyOf(nodes, nodes.length);
    }

    boolean replaceFirstOccurrence(Node toReplace, Node replacement) {
        for (int i = 0; i < nodes.length; i++) {
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
        for (int i = 0; i < nodes.length; i++) {
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
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == toReplace) {
                silentSet(i, replacement);
                result++;
            }
        }
        return result;
    }

    public void setAndClear(int index, Node clearedNode, int clearedIndex) {
        assert self().successors == this;
        Node value = clearedNode.successors.get(clearedIndex);
        assert value != Node.Null;
        clearedNode.successors.nodes[clearedIndex] = Node.Null;
        set(index, Node.Null);
        nodes[index] = value;

        for (int i = 0; i < value.predecessors.size(); ++i) {
            if (value.predecessors.get(i) == clearedNode && value.predecessorsIndex.get(i) == clearedIndex) {
                value.predecessors.set(i, self());
                value.predecessorsIndex.set(i, index);
                return;
            }
        }
        assert false;
    }

    @Override
    public int size() {
        return nodes.length;
    }

    public void clearAll() {
        for (int i = 0; i < nodes.length; i++) {
            set(i, Node.Null);
        }
    }
}
