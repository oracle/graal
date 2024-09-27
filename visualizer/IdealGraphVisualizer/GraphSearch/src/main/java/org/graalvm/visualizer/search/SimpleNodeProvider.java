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
package org.graalvm.visualizer.search;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.openide.util.NbBundle;

import java.util.Iterator;

/**
 * @author sdedic
 */
public final class SimpleNodeProvider implements NodesProvider {

    @Override
    public String getId() {
        return "igv.core.GraphNodes"; // NOI18N
    }

    @NbBundle.Messages({
            "NodeProvider.igv.core.GraphNodes=Graph Nodes"
    })
    @Override
    public String getDisplayName() {
        return Bundle.NodeProvider_igv_core_GraphNodes();
    }

    @Override
    public NodesList nodes(InputGraph graph) {
        return new NodesIterator(graph);
    }

    private static class NodesIterator implements NodesList {
        private final InputGraph graph;
        private final Iterator<InputNode> nodes;
        private int visited;

        public NodesIterator(InputGraph graph) {
            this.graph = graph;
            this.nodes = graph.getNodes().iterator();
        }

        @Override
        public int visitedCount() {
            return visited;
        }

        @Override
        public int nodesCount() {
            return graph.getNodeCount();
        }

        @Override
        public boolean hasNext() {
            return nodes.hasNext();
        }

        @Override
        public InputNode next() {
            visited++;
            return nodes.next();
        }
    }
}
