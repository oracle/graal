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

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;


public class LoopPhase extends Phase {

    @Override
    protected void run(Graph graph) {
        List<Node> nodes = new ArrayList<Node>(graph.getNodes());
        for (Node n : nodes) {
            if (n instanceof LoopBegin) {
                doLoop((LoopBegin) n);
            }
        }
    }

    private void doLoop(LoopBegin loopBegin) {
        LoopEnd loopEnd = loopBegin.loopEnd();
        NodeBitMap loopNodes = computeLoopNodes(loopBegin);
        List<Node> usages = new ArrayList<Node>(loopBegin.usages());
        for (Node usage : usages) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                if (phi.valueCount() == 2) { // TODO (gd) remove predecessor order assumptions
                    Value backEdge = phi.valueAt(1); //assumes backEdge with pred index 1
                    Value init = phi.valueAt(0);
                    Value stride = null;
                    boolean createAfterAddCounter = false;
                    if (!loopNodes.isMarked(init) && backEdge instanceof IntegerAdd && loopNodes.isMarked(backEdge)) {
                        IntegerAdd add = (IntegerAdd) backEdge;
                        int addUsageCount = 0;
                        System.out.println("BackEdge usages :");
                        for (Node u : add.usages()) {
                            if (u != loopEnd.stateBefore()) {
                                System.out.println(" - " + u);
                                addUsageCount++;
                            }
                        }
                        if (add.x() == phi) {
                            stride = add.y();
                        } else if (add.y() == phi) {
                            stride = add.x();
                        }
                        if (addUsageCount > 1) {
                            createAfterAddCounter = true;
                        }
                    }
                    if (stride != null && !loopNodes.isMarked(stride)) {
                        Graph graph = loopBegin.graph();
                        LoopCounter counter = new LoopCounter(init.kind, init, stride, loopBegin, graph);
                        phi.replace(counter);
                        loopEnd.stateBefore().inputs().replace(backEdge, counter);
                        if (createAfterAddCounter) {
                            IntegerAdd otherInit = new IntegerAdd(init.kind, init, stride, graph); // would be nice to canonicalize
                            LoopCounter otherCounter = new LoopCounter(init.kind, otherInit, stride, loopBegin, graph);
                            backEdge.replace(otherCounter);
                        } else {
                            backEdge.delete();
                        }
                    }
                }
            }
        }
    }

    private NodeBitMap computeLoopNodes(LoopBegin loopBegin) {
        LoopEnd loopEnd = loopBegin.loopEnd();
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        NodeFlood workData = loopBegin.graph().createNodeFlood();
        workCFG.add(loopEnd);
        for (Node n : workCFG) {
            workData.add(n);
            loopNodes.mark(n);
            if (n == loopBegin) {
                continue;
            }
            for (Node pred : n.predecessors()) {
                workCFG.add(pred);
            }
        }
        for (Node n : workData) {
            loopNodes.mark(n);
            for (Node usage : n.usages()) {
                if (usage instanceof FrameState) {
                    continue;
                }
                workData.add(usage);
            }
        }
        return loopNodes;
    }
}
