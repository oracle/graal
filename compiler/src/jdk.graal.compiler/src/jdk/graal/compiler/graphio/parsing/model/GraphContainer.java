/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing.model;

import java.util.List;
import java.util.Set;

/**
 * Represents a collection of {@linkplain InputGraph InputGraphs}. Each {@link Group} forms a
 * GraphContainer, but while Group physically owns its contained graphs, {@code GraphContainer} may
 * represent a virtual collection, such as graphs of a certain type.
 *
 * @author sdedic
 */
public interface GraphContainer {
    /**
     * Returns the original {@link Group} that owns the contained graphs.
     *
     * @return owner group.
     */
    Group getContentOwner();

    /**
     * Provides event informing about content changes.
     *
     * @return change event
     */
    ChangedEvent<? extends GraphContainer> getChangedEvent();

    /**
     * Returns type ID of the collection.
     *
     * @return type ID
     */
    String getType();

    /**
     * Decides if the container accepts the graph.
     */
    boolean accept(InputGraph g);

    /**
     * Name of the container. Usually derived from {@link #getContentOwner()}.
     *
     * @return name
     */
    String getName();

    /**
     * @return number of contained graphs
     */
    int getGraphsCount();

    /**
     * Returns IDs of all nodes in all contained graphs. May return a value without loading all the
     * graph data.
     *
     * @return set of node IDs.
     */
    Set<Integer> getChildNodeIds();

    /**
     * Returns nodes of all child graphs. Loads full graph data, if necessary; use with care.
     *
     * @return set of all nodes.
     */
    Set<InputNode> getChildNodes();

    /**
     * Returns list of all graphs. Note that not all graphs have to be loaded in memory.
     *
     * @return list of all graphs
     */
    List<InputGraph> getGraphs();

    /**
     * @return the last graph in the container
     */
    InputGraph getLastGraph();

    /**
     * Determines if a node has been changed. If the node was changed between contained graphs
     * `base' and `to' (inclusive), the mehod returns true.
     *
     * @param base first graph to inspect
     * @param to last graph to inspect
     * @param nodeId ID of the node to check
     * @return true, if the node has been changed at least once
     */
    boolean isNodeChanged(InputGraph base, InputGraph to, int nodeId);
}
