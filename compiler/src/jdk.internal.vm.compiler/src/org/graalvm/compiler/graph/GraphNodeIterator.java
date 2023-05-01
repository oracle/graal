/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import java.util.Iterator;

/**
 * Iterates over the nodes in a given graph.
 */
class GraphNodeIterator implements Iterator<Node> {

    private final Graph graph;
    private int index;

    GraphNodeIterator(Graph graph) {
        this(graph, 0);
    }

    GraphNodeIterator(Graph graph, int index) {
        this.graph = graph;
        this.index = index - 1;
        forward();
    }

    private void forward() {
        if (index < graph.nodesSize) {
            do {
                index++;
            } while (index < graph.nodesSize && graph.nodes[index] == null);
        }
    }

    @Override
    public boolean hasNext() {
        checkForDeletedNode();
        return index < graph.nodesSize;
    }

    private void checkForDeletedNode() {
        while (index < graph.nodesSize && graph.nodes[index] == null) {
            index++;
        }
    }

    @Override
    public Node next() {
        try {
            return graph.nodes[index];
        } finally {
            forward();
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
