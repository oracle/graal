/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.nodes;

import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;

/**
 * Interface for all nodes that are used to mark graph clusters. A graph cluster is a given part of
 * a {@link StructuredGraph}. It's a loose definition of a set of nodes interconnected via fixed
 * nodes. Clusters typically have entry nodes that mark the beginning of a cluster and exit nodes
 * that mark the end of a cluster. All {@link FixedNode} in between are considered part of a
 * cluster. All floating nodes which have inputs (transitively) that are inside the cluster are part
 * of the cluster. Clusters can be used to duplicate certain parts of a graph and insert them at
 * other positions.
 */
public interface ClusterNode extends ControlFlowAnchored {
    void delete();
}
