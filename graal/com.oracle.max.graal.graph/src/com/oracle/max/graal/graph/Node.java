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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Node {

    public static final Node Null = null;
    public static final int DeletedID = -1;

    final Graph graph;
    private int id;
    final NodeArray inputs;
    final NodeArray successors;
    final ArrayList<Node> usages;
    final ArrayList<Node> predecessors;

    public Node(int inputCount, int successorCount, Graph graph) {
        assert graph != null : "cannot create a node for a null graph";
        this.graph = graph;
        this.id = graph.register(this);
        this.inputs = new NodeArray(this, inputCount);
        this.successors = new NodeArray(this, successorCount);
        this.predecessors = new ArrayList<Node>(1);
        this.usages = new ArrayList<Node>(4);
    }

    public List<Node> predecessors() {
        return Collections.unmodifiableList(predecessors);
    }

    public List<Node> usages() {
        return Collections.unmodifiableList(usages);
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

    public <T extends Op> T lookup(Class<T> clazz) {
        return null;
    }

    public String shortName() {
        return getClass().getSimpleName();
    }

    public Node replace(Node other) {
        assert !isDeleted() && (other == null || !other.isDeleted());
        assert other == null || other.graph == graph;
        for (Node usage : usages) {
            usage.inputs.replaceFirstOccurrence(this, other);
        }
        int z = 0;
        for (Node predecessor : predecessors) {
            for (int i=0; i<predecessor.successors.size(); i++) {
                if (predecessor.successors.get(i) == this) {
                    predecessor.successors.silentSet(i, other);
                }
            }
            ++z;
        }
        if (other != null) {
            other.usages.addAll(usages);
            other.predecessors.addAll(predecessors);
        }
        usages.clear();
        predecessors.clear();
        delete();
        return other;
    }

    public boolean isDeleted() {
        return id == DeletedID;
    }

    public void forceDelete() {
        for (Node n : usages) {
            n.inputs.silentRemove(this);
        }
        for (Node n : predecessors) {
            n.successors.silentRemove(this);
        }
        usages.clear();
        predecessors.clear();
    }

    public void delete() {
        assert !isDeleted();
        assert checkDeletion() : "Could not delete " + this;
        for (int i = 0; i < inputs.size(); ++i) {
            inputs.set(i, Null);
        }
        for (int i = 0; i < successors.size(); ++i) {
            successors.set(i, Null);
        }
        assert predecessors().size() == 0 && usages().size() == 0;
        // make sure its not connected. pred usages
        graph.unregister(this);
        id = DeletedID;
        assert isDeleted();
    }

    private boolean checkDeletion() {
        if (usages.size() != 0 || predecessors.size() != 0) {
            System.out.println(this.shortName() + ", id: " + id + ", usages: " + usages.size() + ", predecessors: " + predecessors().size());
            System.out.println("usages:");
            for (Node n : usages()) {
                System.out.print(n.id() + " (" + n.shortName() + ") ");
            }
            System.out.println("\npreds:");
            for (Node n : predecessors()) {
                System.out.print(n.id() + " (" + n.shortName() + ") ");
            }
            System.out.println();
            return false;
        }
        return true;
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

    /**
     * Provides a {@link Map} of properties of this node for use in debugging (e.g., to view in the ideal graph
     * visualizer). Subclasses overriding this method should add to the map returned by their superclass.
     */
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put("inputCount", inputCount());
        map.put("usageCount", usages.size());
        map.put("successorCount", successorCount());
        map.put("predecessorCount", predecessors.size());
        return map;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "-" + this.id();
    }
}
