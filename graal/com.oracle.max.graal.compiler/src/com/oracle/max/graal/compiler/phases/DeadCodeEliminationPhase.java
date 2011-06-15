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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.graph.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;
    private Graph graph;

    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        this.flood = graph.createNodeFlood();

        // remove chained Merges
//        for (Merge merge : graph.getNodes(Merge.class)) {
//            if (merge.predecessors().size() == 1 && merge.usages().size() == 0) {
//                if (merge.successors().get(0) instanceof Merge) {
//                    Node pred = merge.predecessors().get(0);
//                    int predIndex = merge.predecessorsIndex().get(0);
//                    pred.successors().setAndClear(predIndex, merge, 0);
//                    merge.delete();
//                }
//            }
//        }
//        Node startSuccessor = graph.start().successors().get(0);
//        if (startSuccessor instanceof Merge) {
//            Merge startMerge = (Merge) startSuccessor;
//            if (startMerge.predecessors().size() == 1 && startMerge.usages().size() == 0) {
//                int predIndex = startMerge.predecessorsIndex().get(0);
//                graph.start().successors().setAndClear(predIndex, startMerge, 0);
//                startMerge.delete();
//            }
//        }

        flood.add(graph.start());

        iterateSuccessors();
        disconnectCFGNodes();

        iterateInputs();
        disconnectNonCFGNodes();

        deleteCFGNodes();
        deleteNonCFGNodes();

        new PhiSimplifier(graph);

        if (GraalOptions.TraceDeadCodeElimination) {
            System.out.printf("dead code elimination finished\n");
        }
    }

    private static boolean isCFG(Node n) {
        return n != null && ((n instanceof Instruction) || n == n.graph().start());
    }

    private void iterateSuccessors() {
        for (Node current : flood) {
            for (Node successor : current.successors()) {
                flood.add(successor);
            }
        }
    }

    private void disconnectCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node) && node instanceof EndNode) {
                EndNode end = (EndNode) node;
                Merge merge = end.merge();
                merge.removeEnd(end);
            }
        }

        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node) && isCFG(node)) {
                node.successors().clearAll();
                node.inputs().clearAll();
            }
        }
    }

    private void deleteCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node) && isCFG(node)) {
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

    private void deleteNonCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !flood.isMarked(node) && !isCFG(node)) {
                node.delete();
            }
        }
    }
}
