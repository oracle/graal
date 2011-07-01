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
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;


public class LoopPhase extends Phase {

    @Override
    protected void run(Graph graph) {
        List<Loop> loops = LoopUtil.computeLoops(graph);

//        for (Loop loop : loops) {
//            System.out.println("Peel loop : " + loop.loopBegin());
//            LoopUtil.peelLoop(loop);
//        }
//        loops = LoopUtil.computeLoops(graph); // TODO (gd) avoid recomputing loops

        for (Loop loop : loops) {
            doLoopCounters(loop);
        }
    }

    private void doLoopCounters(Loop loop) {
        LoopBegin loopBegin = loop.loopBegin();
        List<LoopCounter> counters = findLoopCounters(loopBegin, loop.nodes());
        mergeLoopCounters(counters, loopBegin);
    }

    private void mergeLoopCounters(List<LoopCounter> counters, LoopBegin loopBegin) {
        Graph graph = loopBegin.graph();
        FrameState stateAfter = loopBegin.stateAfter();
        LoopCounter[] acounters = counters.toArray(new LoopCounter[counters.size()]);
        for (int i = 0; i < acounters.length; i++) {
            LoopCounter c1 = acounters[i];
            if (c1 == null) {
                continue;
            }
            for (int j = i + 1; j < acounters.length; j++) {
                LoopCounter c2 = acounters[j];
                if (c2 != null && c1.stride().valueEqual(c2.stride())) {
                    boolean c1InCompare = Util.filter(c1.usages(), Compare.class).size() > 0;
                    boolean c2inCompare = Util.filter(c2.usages(), Compare.class).size() > 0;
                    if (c2inCompare && !c1InCompare) {
                        c1 = acounters[j];
                        c2 = acounters[i];
                        acounters[i] = c2;
                        acounters[j] = c1;
                    }
                    boolean c2InFramestate = stateAfter != null ? stateAfter.inputs().contains(c2) : false;
                    acounters[j] = null;
                    CiKind kind = c1.kind;
                    if (c2InFramestate) {
                        IntegerSub sub = new IntegerSub(kind, c2.init(), c1.init(), graph);
                        IntegerAdd addStride = new IntegerAdd(kind, sub, c1.stride(), graph);
                        IntegerAdd add = new IntegerAdd(kind, c1, addStride, graph);
                        Phi phi = new Phi(kind, loopBegin, graph); // (gd) assumes order on loopBegin preds - works in collab with graph builder
                        phi.addInput(c2.init());
                        phi.addInput(add);
                        c2.replaceAndDelete(phi);
                    } else {
                        IntegerSub sub = new IntegerSub(kind, c2.init(), c1.init(), graph);
                        IntegerAdd add = new IntegerAdd(kind, c1, sub, graph);
                        c2.replaceAndDelete(add);
                    }
                }
            }
        }
    }

    private List<LoopCounter> findLoopCounters(LoopBegin loopBegin, NodeBitMap loopNodes) {
        LoopEnd loopEnd = loopBegin.loopEnd();
        FrameState loopEndState = null;
        Node loopEndPred = loopEnd.singlePredecessor();
        if (loopEndPred instanceof Merge) {
            loopEndState = ((Merge) loopEndPred).stateAfter();
        }
        List<Node> usages = new ArrayList<Node>(loopBegin.usages());
        List<LoopCounter> counters = new LinkedList<LoopCounter>();
        for (Node usage : usages) {
            if (usage instanceof Phi) {
                Phi phi = (Phi) usage;
                if (phi.valueCount() == 2) {
                    Value backEdge = phi.valueAt(1);
                    Value init = phi.valueAt(0);
                    if (loopNodes.isNew(init) || loopNodes.isNew(backEdge)) {
                        continue;
                    }
                    if (loopNodes.isMarked(init)) {
                        // try to reverse init/backEdge order
                        Value tmp = backEdge;
                        backEdge = init;
                        init = tmp;
                    }
                    Value stride = null;
                    boolean useCounterAfterAdd = false;
                    if (!loopNodes.isMarked(init) && backEdge instanceof IntegerAdd && loopNodes.isMarked(backEdge)) {
                        IntegerAdd add = (IntegerAdd) backEdge;
                        int addUsageCount = 0;
                        for (Node u : add.usages()) {
                            if (u != loopEndState && u != phi) {
                                addUsageCount++;
                            }
                        }
                        if (add.x() == phi) {
                            stride = add.y();
                        } else if (add.y() == phi) {
                            stride = add.x();
                        }
                        if (addUsageCount > 0) {
                            useCounterAfterAdd = true;
                        }
                    }
                    if (stride != null && !loopNodes.isNew(stride) &&  !loopNodes.isMarked(stride)) {
                        Graph graph = loopBegin.graph();
                        LoopCounter counter = new LoopCounter(init.kind, init, stride, loopBegin, graph);
                        counters.add(counter);
                        phi.replaceAndDelete(counter);
                        if (loopEndState != null) {
                            loopEndState.inputs().replace(backEdge, counter);
                        }
                        if (useCounterAfterAdd) {
                            /*IntegerAdd otherInit = new IntegerAdd(init.kind, init, stride, graph); // would be nice to canonicalize
                            LoopCounter otherCounter = new LoopCounter(init.kind, otherInit, stride, loopBegin, graph);
                            backEdge.replace(otherCounter);*/
                        } else {
                            backEdge.delete();
                        }
                    }
                }
            }
        }
        return counters;
    }
}
