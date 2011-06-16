/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.phases;

import java.util.*;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.graph.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;
    private Graph graph;
    private ArrayList<LoopBegin> brokenLoops;

    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        this.flood = graph.createNodeFlood();
        this.brokenLoops = new ArrayList<LoopBegin>();

        // remove chained Merges
        for (Merge merge : graph.getNodes(Merge.class)) {
            if (merge.endCount() == 1 && merge.usages().size() == 0 && !(merge instanceof LoopEnd)) {
                merge.endAt(0).replace(merge.next());
                merge.delete();
            }
        }

        flood.add(graph.start());

        iterateSuccessors();
        disconnectCFGNodes();
        deleteBrokenLoops();
        iterateInputs();
        disconnectNonCFGNodes();
        deleteNodes();

        new PhiSimplifier(graph);

        if (GraalOptions.TraceDeadCodeElimination) {
            TTY.println("dead code elimination finished");
        }
    }

    private static boolean isCFG(Node n) {
        return n != null && ((n instanceof Instruction) || n == n.graph().start());
    }

    private void iterateSuccessors() {
        for (Node current : flood) {
            if (current instanceof EndNode) {
                EndNode end = (EndNode) current;
                flood.add(end.merge());
            } else {
                for (Node successor : current.successors()) {
                    flood.add(successor);
                }
            }
        }
    }

    private void disconnectCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node)) {
                if (isCFG(node)) {
                    node.successors().clearAll();
                    node.inputs().clearAll();
                } else if (node instanceof EndNode) {
                    EndNode end = (EndNode) node;
                    Merge merge = end.merge();
                    if (merge != null && flood.isMarked(merge)) {
                        // We are a dead end node leading to a live merge.
                        merge.removeEnd(end);
                    }
                }
            }
        }
    }

    private void deleteBrokenLoops() {
        for (LoopBegin loop : brokenLoops) {
            assert loop.predecessors().size() == 1;
            for (Node usage : new ArrayList<Node>(loop.usages())) {
                assert usage instanceof Phi;
                usage.replace(((Phi) usage).valueAt(0));
            }

            loop.replace(loop.next());
        }
    }

    private void deleteNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node)) {
                node.delete();
            }
        }
    }

    private void iterateInputs() {
        for (Node node : graph.getNodes()) {
            if (node instanceof Local) {
                flood.add(node);
            }
            if (node != Node.Null && flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    if (!isCFG(input)) {
                        flood.add(input);
                    }
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                if (!isCFG(input)) {
                    flood.add(input);
                }
            }
        }
    }

    private void disconnectNonCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node) && !isCFG(node)) {
                node.inputs().clearAll();
            }
        }
    }
}
