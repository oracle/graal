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



public final class NodeMap<T> {

    private final Object[] values;
    private final Graph graph;

    NodeMap(Graph graph) {
        this.graph = graph;
        values = new Object[graph.nextId];
    }

    @SuppressWarnings("unchecked")
    public T get(Node node) {
        check(node);
        return (T) values[node.id()];
    }

    public void set(Node node, T value) {
        check(node);
        values[node.id()] = value;
    }

    public int size() {
        return values.length;
    }

    private void check(Node node) {
        assert node.graph == graph : "this node is not part of the graph";
        assert node.id() < values.length : "this node was added to the graph after creating the node map";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            sb.append("[").append(i).append(" -> ").append(values[i]).append("]").append('\n');
        }
        return sb.toString();
    }
}
