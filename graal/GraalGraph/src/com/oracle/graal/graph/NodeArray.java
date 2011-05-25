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

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

public class NodeArray extends AbstractList<Node> {

    private final Node node;
    private final Node[] nodes;

    public NodeArray(Node node, int length) {
        this.node = node;
        this.nodes = new Node[length];
    }

    public Iterator<Node> iterator() {
        return Arrays.asList(this.nodes).iterator();
    }

    private Node self() {
        return this.node;
    }

    public Node set(int index, Node node) {
        assert node == Node.Null || node.graph == self().graph;
        assert node == Node.Null || node.id() != Node.DeletedID : "inserted node must not be deleted";
        Node old = nodes[index];

        if (old != node) {
            nodes[index] = node;
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

    /**
     * Sets the specified input/successor to the given node, and inserts the back edge (usage/predecessor) at the given index.
     */
    public Node set(int index, Node node, int backIndex) {
        assert node == Node.Null || node.graph == self().graph;
        Node old = nodes[index];

        if (old != node) {
            nodes[index] = node;
            if (self().inputs == this) {
                if (old != null) {
                    old.usages.remove(self());
                }
                if (node != null) {
                    node.usages.add(backIndex, self());
                }
            } else {
                assert self().successors == this;
                if (old != null) {
                    old.predecessors.remove(self());
                }
                if (node != null) {
                    node.predecessors.add(backIndex, self());
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

    public Node get(int index) {
        return nodes[index];
    }

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

    public void setAndClear(int index, Node clearedNode, int clearedIndex) {
        assert self().successors == this;
        Node value = clearedNode.successors.get(clearedIndex);
        assert value != Node.Null;
        clearedNode.successors.nodes[clearedIndex] = Node.Null;
        set(index, Node.Null);
        nodes[index] = value;

        int numberOfMatchingSuccs = 0;
        for (int i = 0; i < clearedIndex; ++i) {
            if (clearedNode.successors.get(i) == value) {
                numberOfMatchingSuccs++;
            }
        }

        for (int i = 0; i < value.predecessors.size(); ++i) {
            if (value.predecessors.get(i) == clearedNode) {
                if (numberOfMatchingSuccs == 0) {
                    value.predecessors.set(i, self());
                    break;
                } else {
                    numberOfMatchingSuccs--;
                }
            }
        }
    }

    public int replaceKeepOrder(Node targetNode, Node replacement) {
        int deletedSuccessorIndex = -1;
        if (this == self().successors) {
            int firstIndex = -1;
            for (int i = 0; i < replacement.successors.size(); ++i) {
                if (replacement.successors.get(i) == self()) {
                    replacement.successors().set(i, Node.Null);
                    firstIndex = i;
                    break;
                }
            }

            assert firstIndex != -1;

            for (int i = 0; i < this.size(); ++i) {
                if (nodes[i] == targetNode) {
                    replacement.successors().nodes[firstIndex] = targetNode;
                    for (int j = 0; j < targetNode.predecessors.size(); ++j) {
                        if (targetNode.predecessors.get(j) == self()) {
                            targetNode.predecessors.set(j, replacement);
                            break;
                        }
                    }
                    nodes[i] = Node.Null;
                    deletedSuccessorIndex = i;
                    break;
                }
            }
        } else {
            assert false : "not supported";
        }
        return deletedSuccessorIndex;
    }

    public int size() {
        return nodes.length;
    }

    public void clearAll() {
        for (int i = 0; i < nodes.length; i++) {
            set(i, Node.Null);
        }
    }
}
