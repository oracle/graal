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
package org.graalvm.visualizer.coordinator.actions;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.difference.Difference;
import org.graalvm.visualizer.util.LookupHistory;
import org.openide.nodes.Node;
import org.openide.util.Lookup;

import java.util.Objects;

public class DiffGraphCookie implements Node.Cookie {

    private final InputGraph graph;

    public DiffGraphCookie(InputGraph graph) {
        this.graph = graph;
    }

    private InputGraph getCurrentGraph() {
        InputGraphProvider graphProvider = LookupHistory.getLast(InputGraphProvider.class);
        if (graphProvider != null) {
            return graphProvider.getGraph();
        }
        return null;
    }

    public boolean isPossible() {
        InputGraph cg = getCurrentGraph();
        if (cg == null || cg == graph || !(Objects.equals(cg.getGraphType(), graph.getGraphType()))) {
            return false;
        }
        Group p1 = graph.getGroup();
        Group p2 = cg.getGroup();
        if (p1 == null || p2 == null) {
            return false;
        }
        if (p1.getOwner() == null || p2.getOwner() == null) {
            return false;
        }
        return true;
    }

    public void openDiff() {
        if (!isPossible()) {
            return;
        }
        InputGraph other = getCurrentGraph();
        final GraphViewer viewer = Lookup.getDefault().lookup(GraphViewer.class);
        if (viewer != null) {
            InputGraph diffGraph = Difference.createDiffGraph(other, graph);
            viewer.view(diffGraph, true);
        }
    }
}
