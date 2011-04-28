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
package com.oracle.graal.graph;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

public abstract class Node implements Cloneable {

    public static final Node Null = null;
    public static final int DeletedID = -1;

    private final Graph graph;
    private int id;
    private final NodeArray inputs;
    private final NodeArray successors;
    private final ArrayList<Node> usages;
    private final ArrayList<Node> predecessors;

    public Node(int inputCount, int successorCount, Graph graph) {
        assert graph != null;
        this.graph = graph;
        this.id = graph.register(this);
        this.inputs = new NodeArray(inputCount);
        this.successors = new NodeArray(successorCount);
        this.predecessors = new ArrayList<Node>();
        this.usages = new ArrayList<Node>();
    }

    public Collection<Node> predecessors() {
        return Collections.unmodifiableCollection(predecessors);
    }

    public Collection<Node> usages() {
        return Collections.unmodifiableCollection(usages);
    }

    public NodeArray inputs() {
        return inputs;
    }

    public NodeArray successors() {
        return successors;
    }

    public int id() {
        return id;
    }

    public Graph graph() {
        return graph;
    }

    public void replace(Node other) {
        assert !isDeleted() && !other.isDeleted();
        assert other == null || other.graph == graph;
        for (Node usage : usages) {
            usage.inputs.replaceFirstOccurrence(this, other);
        }
        for (Node predecessor : predecessors) {
            predecessor.successors.replaceFirstOccurrence(this, other);
        }
        if (other != null) {
            other.usages.addAll(usages);
            other.predecessors.addAll(predecessors);
        }
        usages.clear();
        predecessors.clear();
        delete();
    }

    public boolean isDeleted() {
        return id == DeletedID;
    }

    public void delete() {
        assert !isDeleted();
        assert usages.size() == 0 && predecessors.size() == 0;
        for (int i = 0; i < inputs.size(); ++i) {
            inputs.set(i, Null);
        }
        for (int i = 0; i < successors.size(); ++i) {
            successors.set(i, Null);
        }
        // make sure its not connected. pred usages
        graph.unregister(this);
        id = DeletedID;
        assert isDeleted();
    }

    public Node copy() {
        return copy(graph);
    }

    /**
     * 
     * @param into
     * @return
     */
    public abstract Node copy(Graph into);

    /**
     * 
     * @return
     */
    protected int inputCount() {
        return 0;
    }

    /**
     * 
     * @return
     */
    protected int successorCount() {
        return 0;
    }

    public class NodeArray extends AbstractList<Node> {

        private final Node[] nodes;

        public NodeArray(int length) {
            this.nodes = new Node[length];
        }

        public Iterator<Node> iterator() {
            return Arrays.asList(this.nodes).iterator();
        }

        private Node self() {
            return Node.this;
        }

        public Node set(int index, Node node) {
            assert node.graph == self().graph;
            Node old = nodes[index];

            if (old != node) {
                nodes[index] = node;
                if (Node.this.inputs == this) {
                    if (old != null) {
                        old.usages.remove(self());
                    }
                    if (node != null) {
                        node.usages.add(self());
                    }
                } else {
                    assert Node.this.successors == this;
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

        public Node get(int index) {
            return nodes[index];
        }

        public Node[] toArray() {
            return Arrays.copyOf(nodes, nodes.length);
        }

        private boolean replaceFirstOccurrence(Node toReplace, Node replacement) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == toReplace) {
                    nodes[i] = replacement;
                    return true;
                }
            }
            return false;
        }

        public int size() {
            return nodes.length;
        }
    }
}
