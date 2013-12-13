/*
 * Copyright (c) 2013, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.spi;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import static com.oracle.graal.nodes.StructuredGraph.GuardsStage.*;

/**
 * Interface for nodes which have {@link FrameState} nodes as input.
 * <p>
 * Some node can implement more than one interface which requires a {@link FrameState} input (e.g.
 * {@link DeoptimizingNode} and {@link StateSplit}). Since this interface can only report one
 * FrameState, such nodes must ensure they only maintain a link to at most one FrameState at all
 * times. Usually this is not a problem because FrameStates are associated only with StateSplit
 * nodes before the {@link #AFTER_FSA} stage and only with DeoptimizingNodes after.
 * 
 */
public interface NodeWithState {
    /**
     * Gets the {@link FrameState} associated with this node.
     * 
     * @return the {@link FrameState} associated with this node
     */
    FrameState getState();

    Node asNode();
}
