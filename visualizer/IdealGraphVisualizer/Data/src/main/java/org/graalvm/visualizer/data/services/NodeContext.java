/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides additional context information for the node. The node itself does not link
 * to its parent, so it can be eventually embedded in several graphs (this is used in diff graph, for example).
 * A {@link InputNode} can be wrapped/subclassed so that it exposes {@link NodeContext#Provider} and delivers
 * information about node's owner or other associated services.
 *
 * @author sdedic
 */
public final class NodeContext {
    private final InputNode node;
    private final InputGraph graph;
    private final GraphContainer parent;

    public NodeContext(InputNode node, InputGraph graph, GraphContainer parent) {
        this.node = node;
        this.graph = graph;
        this.parent = parent;
    }

    public InputNode getNode() {
        return node;
    }

    public InputGraph getGraph() {
        return graph;
    }

    public GraphContainer getParent() {
        return parent;
    }

    public interface Provider extends Lookup.Provider {
        public NodeContext nodeContext();
    }

    public static NodeContext fromNode(InputNode n) {
        if (n instanceof Provider) {
            return ((Provider) n).nodeContext();
        } else if (n instanceof Lookup.Provider) {
            Provider p = ((Lookup.Provider) n).getLookup().lookup(NodeContext.Provider.class);
            if (p != null) {
                return p.nodeContext();
            }
        }
        return null;
    }
}
