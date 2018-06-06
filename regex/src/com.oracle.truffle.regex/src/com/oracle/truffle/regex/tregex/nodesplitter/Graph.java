/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodesplitter;

import com.oracle.truffle.regex.tregex.automaton.StateIndex;

import java.util.ArrayList;

/**
 * An abstract graph wrapper used by {@link DFANodeSplit}.
 */
class Graph implements StateIndex<GraphNode> {

    private GraphNode start;
    private final ArrayList<GraphNode> nodes;

    Graph(int initialCapacity) {
        this.nodes = new ArrayList<>(initialCapacity);
    }

    public GraphNode getStart() {
        return start;
    }

    public void setStart(GraphNode start) {
        this.start = start;
    }

    public ArrayList<GraphNode> getNodes() {
        return nodes;
    }

    public GraphNode getNode(int id) {
        return nodes.get(id);
    }

    public void addGraphNode(GraphNode graphNode) {
        assert graphNode.getId() == nodes.size();
        nodes.add(graphNode);
        assert graphNode == nodes.get(graphNode.getId());
    }

    public int size() {
        return nodes.size();
    }

    @Override
    public int getNumberOfStates() {
        return size();
    }

    @Override
    public GraphNode getState(int id) {
        return getNode(id);
    }
}
