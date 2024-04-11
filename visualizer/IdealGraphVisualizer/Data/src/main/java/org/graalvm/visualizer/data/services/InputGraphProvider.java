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
package org.graalvm.visualizer.data.services;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.openide.util.Lookup;

import java.util.Set;

/**
 * Represents a component that displays or works with graph(s). Multiple instances
 * of InputGraphProvider may actually represent the same component/window, if it
 * is capable to display multiple different graphs.
 *
 * @author sdedic
 */
public interface InputGraphProvider {
    /**
     * Returns Lookup for this provider. The Lookup may be used to locate
     * further services.
     *
     * @return lookup instance
     */
    Lookup getLookup();

    /**
     * Returns the current graph instance
     *
     * @return viewed graph instance.
     */
    InputGraph getGraph();

    GraphContainer getContainer();

    /**
     * Selects the specified nodes
     *
     * @param nodes nodes to select
     */
    void setSelectedNodes(Set<InputNode> nodes);

    /**
     * @return an iterator walking forward through the {@link InputGraph}s following the
     * {@link #getGraph()}
     */
    Iterable<InputGraph> searchForward();

    /**
     * @return an iterator walking backward through the {@link InputGraph}s preceeding the
     * {@link #getGraph()}
     */
    Iterable<InputGraph> searchBackward();
}
