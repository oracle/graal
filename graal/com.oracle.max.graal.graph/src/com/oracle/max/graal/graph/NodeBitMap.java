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

import com.sun.cri.ci.CiBitMap;


public final class NodeBitMap {

    private final CiBitMap bitMap;
    private final Graph graph;

    NodeBitMap(Graph graph) {
        this.graph = graph;
        bitMap = new CiBitMap(graph.nextId);
    }

    public Graph graph() {
        return graph;
    }

    public boolean setIntersect(NodeBitMap other) {
        return bitMap.setIntersect(other.bitMap);
    }

    public void setUnion(NodeBitMap other) {
        bitMap.setUnion(other.bitMap);
    }

    public boolean isMarked(Node node) {
        check(node);
        return bitMap.get(node.id());
    }

    public boolean isNew(Node node) {
        return node.id() >= bitMap.size();
    }

    public void mark(Node node) {
        check(node);
        bitMap.set(node.id());
    }

    public void clear(Node node) {
        check(node);
        bitMap.clear(node.id());
    }

    private void check(Node node) {
        assert node.graph == graph : "this node is not part of the graph";
        assert !isNew(node) : "this node (" + node.id() + ") was added to the graph after creating the node bitmap (" + bitMap.length() + ")";
    }

    @Override
    public String toString() {
        return bitMap.toBinaryString(-1);
    }
}
