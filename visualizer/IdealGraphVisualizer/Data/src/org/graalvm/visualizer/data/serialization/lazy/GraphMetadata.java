/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.BitSet;

/**
 * Metadata distilled during scanning the input data, for use before the graph
 * is fully loaded.
 */
final class GraphMetadata {
    final BitSet nodeIds = new BitSet();
    final BitSet changedNodeIds = new BitSet();

    private int highestNodeId = -1;
    private int edgeCount;
    private int nodeCount;
    private boolean duplicate;

    void addEdge(int from, int to) {
        edgeCount++;
    }

    void addNode(int id) {
        nodeIds.set(id);
        if (highestNodeId < id) {
            highestNodeId = id;
        }
        nodeCount++;
    }

    public BitSet getNodeIds() {
        return nodeIds;
    }

    void markDuplicate() {
        this.duplicate = true;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public int getEdgeCount() {
        return edgeCount;
    }

    public void nodeChanged(int id) {
        changedNodeIds.set(id);
    }

    public int getHighestNodeId() {
        return highestNodeId;
    }
}
