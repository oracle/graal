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
package com.sun.c1x.graph;

import com.oracle.graal.graph.*;
import com.sun.c1x.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.ir.*;


public class DeadCodeElimination extends Phase {

    private NodeBitMap alive;
    private NodeWorklist worklist;
    private Graph graph;

    public int deletedNodeCount;

    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        this.alive = graph.createNodeBitMap();
        this.worklist = graph.createNodeWorklist();

        worklist.add(graph.start());

        iterateSuccessors();
        disconnectCFGNodes();

        iterateInputs();
        disconnectNonCFGNodes();

        deleteCFGNodes();
        deleteNonCFGNodes();

        new PhiSimplifier(graph);

        if (C1XOptions.TraceDeadCodeElimination) {
            System.out.printf("dead code elimination: deleted %d nodes\n", deletedNodeCount);
        }
    }

    private static boolean isCFG(Node n) {
        return n != null && ((n instanceof Instruction) || n == n.graph().start());
    }

    private void iterateSuccessors() {
        for (Node current : worklist) {
            for (Node successor : current.successors()) {
                worklist.add(successor);
            }
        }
    }

    private void disconnectCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !worklist.isMarked(node) && isCFG(node)) {
                // iterate backwards so that the predecessor indexes in removePhiPredecessor are correct
                for (int i = node.successors().size() - 1; i >= 0; i--) {
                    Node successor = node.successors().get(i);
                    if (successor != Node.Null && worklist.isMarked(successor)) {
                        if (successor instanceof Merge) {
                            ((Merge) successor).removePhiPredecessor(node);
                        }
                    }
                }
                node.successors().clearAll();
                node.inputs().clearAll();
            }
        }
    }

    private void deleteCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !worklist.isMarked(node) && isCFG(node)) {
                node.delete();
                deletedNodeCount++;
            }
        }
    }

    private void iterateInputs() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && worklist.isMarked(node)) {
                for (Node input : node.inputs()) {
                    worklist.add(input);
                }
            }
        }
        for (Node current : worklist) {
            for (Node input : current.inputs()) {
                worklist.add(input);
            }
        }
    }

    private void disconnectNonCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !worklist.isMarked(node) && !isCFG(node)) {
                node.inputs().clearAll();
            }
        }
    }

    private void deleteNonCFGNodes() {
        for (Node node : graph.getNodes()) {
            if (node != Node.Null && !worklist.isMarked(node) && !isCFG(node)) {
                node.delete();
                deletedNodeCount++;
            }
        }
    }
}
