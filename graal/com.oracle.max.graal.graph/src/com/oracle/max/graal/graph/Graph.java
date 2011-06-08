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
package com.oracle.max.graal.graph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class Graph {

    private final ArrayList<Node> nodes;
    private final StartNode start;
    int nextId;

    static int nextGraphId = 0;
    int id = nextGraphId++;

    @Override
    public String toString() {
        return "Graph " + id;
    }

    public Graph() {
        nodes = new ArrayList<Node>();
        start = new StartNode(this);
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    int register(Node node) {
        int id = nextId++;
        nodes.add(id, node);
        return id;
    }

    void unregister(Node node) {
        nodes.set(node.id(), Node.Null);
    }

    public StartNode start() {
        return start;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap(this);
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<T>(this);
    }

    public NodeWorklist createNodeWorklist() {
        return new NodeWorklist(this);
    }

    public Map<Node, Node> addDuplicate(Collection<Node> nodes, Map<Node, Node> replacements) {
        Map<Node, Node> newNodes = new HashMap<Node, Node>();
        // create node duplicates
        for (Node node : nodes) {
            if (node != null && !replacements.containsKey(node)) {
                assert node.graph != this;
                Node newNode = node.copy(this);
                assert newNode.getClass() == node.getClass();
                newNodes.put(node, newNode);
            }
        }
        // re-wire inputs
        for (Entry<Node, Node> entry : newNodes.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (int i = 0; i < oldNode.inputs().size(); i++) {
                Node input = oldNode.inputs().get(i);
                Node target = replacements.get(input);
                if (target == null) {
                    target = newNodes.get(input);
                }
                node.inputs().set(i, target);
            }
        }
        for (Entry<Node, Node> entry : replacements.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (int i = 0; i < oldNode.inputs().size(); i++) {
                Node input = oldNode.inputs().get(i);
                if (newNodes.containsKey(input)) {
                    node.inputs().set(i, newNodes.get(input));
                }
            }
        }
        // re-wire successors
        for (Entry<Node, Node> entry : newNodes.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (int i = 0; i < oldNode.predecessors().size(); i++) {
                Node pred = oldNode.predecessors().get(i);
                int predIndex = oldNode.predecessorsIndex().get(i);
                Node source = replacements.get(pred);
                if (source == null) {
                    source = newNodes.get(pred);
                }
                source.successors().set(predIndex,  node);
            }
        }
        for (Entry<Node, Node> entry : replacements.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (int i = 0; i < oldNode.predecessors().size(); i++) {
                Node pred = oldNode.predecessors().get(i);
                int predIndex = oldNode.predecessorsIndex().get(i);
                if (newNodes.containsKey(pred)) {
                    newNodes.get(pred).successors().set(predIndex, node);
                }
            }
        }
        return newNodes;
    }
}
