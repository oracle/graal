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
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;


public class DeadCodeEliminationPhase extends Phase {

    private NodeFlood flood;
    private Graph graph;

    @Override
    protected void run(Graph graph) {
        this.graph = graph;
        this.flood = graph.createNodeFlood();

        flood.add(graph.start());
        iterateSuccessors();
        disconnectCFGNodes();
        iterateInputs();
        disconnectNodes();
        deleteNodes();

        // remove chained Merges
        for (Merge merge : graph.getNodes(Merge.class)) {
            if (merge.endCount() == 1 && !(merge instanceof LoopBegin)) {
                replacePhis(merge);
                EndNode endNode = merge.endAt(0);
                FixedNode next = merge.next();
                merge.delete();
                endNode.replaceAndDelete(next);
            }
        }

        new PhiSimplifier(graph);
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
            if (!flood.isMarked(node)) {
                if (node instanceof EndNode) {
                    EndNode end = (EndNode) node;
                    Merge merge = end.merge();
                    if (merge != null && flood.isMarked(merge)) {
                        // We are a dead end node leading to a live merge.
                        merge.removeEnd(end);
                    }
                } else if (node instanceof LoopEnd) {
                    LoopBegin loop = ((LoopEnd) node).loopBegin();
                    if (flood.isMarked(loop)) {
                        if (GraalOptions.TraceDeadCodeElimination) {
                            TTY.println("Building loop begin node back: " + loop);
                        }
                        ((LoopEnd) node).setLoopBegin(null);
                        EndNode endNode = loop.endAt(0);
                        assert endNode.predecessors().size() == 1 : endNode.predecessors().size();
                        replacePhis(loop);
                        endNode.replaceAndDelete(loop.next());
                        loop.delete();
                    }
                } else if (node instanceof Merge) {
                    for (Node n : node.usages()) {
                        if (n instanceof Phi) {
                            Phi phi = (Phi) n;
                            if (phi.usages().size() == 1 && phi.usages().get(0) instanceof VirtualObject) {
                                // (tw) This VirtualObject instance is implicitely dead, because the CFG to it (i.e. the store that produced it) is dead! => fix this in escape analysis
                                VirtualObject virtualObject = (VirtualObject) phi.usages().get(0);
                                virtualObject.replaceAndDelete(virtualObject.object());
                            }
                        }
                    }
                }


                if (IdentifyBlocksPhase.isFixed(node)) {
                    for (Node n : new ArrayList<Node>(node.usages())) {
                        if (n instanceof VirtualObject) {
                            // (tw) This VirtualObject instance is implicitely dead, because the CFG to it (i.e. the
                            // store that produced it) is dead! => fix this in Escape analysis
                            VirtualObject virtualObject = (VirtualObject) n;
                            virtualObject.replaceAndDelete(virtualObject.object());
                        }
                    }
                }
            }
        }
    }

    private void replacePhis(Merge merge) {
        for (Node usage : new ArrayList<Node>(merge.usages())) {
            assert usage instanceof Phi;
            usage.replaceAndDelete(((Phi) usage).valueAt(0));
        }
    }

    private void deleteNodes() {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                for (int i = 0; i < node.inputs().size(); i++) {
                    node.inputs().set(i, Node.Null);
                }
                for (int i = 0; i < node.successors().size(); i++) {
                    node.successors().set(i, Node.Null);
                }
            }
        }
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                if (node.predecessors().size() > 0) {
                    for (Node pred : node.predecessors()) {
                        TTY.println("!PRED! " + pred + " (" + flood.isMarked(pred) + ")");
                        for (int i=0; i<pred.successors().size(); i++) {
                            TTY.println("pred=>succ: " + pred.successors().get(i));
                        }
                        for (int i=0; i<pred.usages().size(); i++) {
                            TTY.println("pred=>usage: " + pred.usages().get(i));
                        }
                    }
                }
                node.delete();
            }
        }
    }

    private void iterateInputs() {
        for (Node node : graph.getNodes()) {
            if (node instanceof Local) {
                flood.add(node);
            }
            if (flood.isMarked(node)) {
                for (Node input : node.inputs()) {
                    flood.add(input);
                }
            }
        }
        for (Node current : flood) {
            for (Node input : current.inputs()) {
                flood.add(input);
            }
        }
    }

    private void disconnectNodes() {
        for (Node node : graph.getNodes()) {
            if (!flood.isMarked(node)) {
                for (int i = 0; i < node.inputs().size(); i++) {
                    Node input = node.inputs().get(i);
                    if (input != Node.Null && flood.isMarked(input)) {
                        node.inputs().set(i, Node.Null);
                    }
                }
            }
        }
    }
}
