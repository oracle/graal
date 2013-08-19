/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.loop;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.loop.InductionVariable.Direction;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.extended.*;

public class LoopsData {

    private Map<Loop, LoopEx> lirLoopToEx = new IdentityHashMap<>();
    private Map<LoopBeginNode, LoopEx> loopBeginToEx = new IdentityHashMap<>();
    private ControlFlowGraph cfg;

    public LoopsData(final StructuredGraph graph) {
        cfg = Debug.scope("ControlFlowGraph", new Callable<ControlFlowGraph>() {

            @Override
            public ControlFlowGraph call() throws Exception {
                return ControlFlowGraph.compute(graph, true, true, true, true);
            }
        });
        for (Loop lirLoop : cfg.getLoops()) {
            LoopEx ex = new LoopEx(lirLoop, this);
            lirLoopToEx.put(lirLoop, ex);
            loopBeginToEx.put(ex.loopBegin(), ex);
        }
    }

    public LoopEx loop(Loop lirLoop) {
        return lirLoopToEx.get(lirLoop);
    }

    public LoopEx loop(LoopBeginNode loopBegin) {
        return loopBeginToEx.get(loopBegin);
    }

    public Collection<LoopEx> loops() {
        return lirLoopToEx.values();
    }

    public List<LoopEx> outterFirst() {
        ArrayList<LoopEx> loops = new ArrayList<>(loops());
        Collections.sort(loops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o1.lirLoop().depth - o2.lirLoop().depth;
            }
        });
        return loops;
    }

    public List<LoopEx> innerFirst() {
        ArrayList<LoopEx> loops = new ArrayList<>(loops());
        Collections.sort(loops, new Comparator<LoopEx>() {

            @Override
            public int compare(LoopEx o1, LoopEx o2) {
                return o2.lirLoop().depth - o1.lirLoop().depth;
            }
        });
        return loops;
    }

    public Collection<LoopEx> countedLoops() {
        List<LoopEx> counted = new LinkedList<>();
        for (LoopEx loop : loops()) {
            if (loop.isCounted()) {
                counted.add(loop);
            }
        }
        return counted;
    }

    public void detectedCountedLoops() {
        for (LoopEx loop : loops()) {
            InductionVariables ivs = new InductionVariables(loop);
            LoopBeginNode loopBegin = loop.loopBegin();
            FixedNode next = loopBegin.next();
            while (next instanceof FixedGuardNode || next instanceof ValueAnchorNode) {
                next = ((FixedWithNextNode) next).next();
            }
            if (next instanceof IfNode) {
                IfNode ifNode = (IfNode) next;
                boolean negated = false;
                if (!loopBegin.isLoopExit(ifNode.falseSuccessor())) {
                    if (!loopBegin.isLoopExit(ifNode.trueSuccessor())) {
                        continue;
                    }
                    negated = true;
                }
                LogicNode ifTest = ifNode.condition();
                if (!(ifTest instanceof IntegerLessThanNode)) {
                    if (ifTest instanceof IntegerBelowThanNode) {
                        Debug.log("Ignored potential Counted loop at %s with |<|", loopBegin);
                    }
                    continue;
                }
                IntegerLessThanNode lessThan = (IntegerLessThanNode) ifTest;
                Condition condition = null;
                InductionVariable iv = null;
                ValueNode limit = null;
                if (loop.isOutsideLoop(lessThan.x())) {
                    iv = ivs.get(lessThan.y());
                    if (iv != null) {
                        condition = lessThan.condition().mirror();
                        limit = lessThan.x();
                    }
                } else if (loop.isOutsideLoop(lessThan.y())) {
                    iv = ivs.get(lessThan.x());
                    if (iv != null) {
                        condition = lessThan.condition();
                        limit = lessThan.y();
                    }
                }
                if (condition == null) {
                    continue;
                }
                if (negated) {
                    condition = condition.negate();
                }
                boolean oneOff = false;
                switch (condition) {
                    case LE:
                        oneOff = true; // fall through
                    case LT:
                        if (iv.direction() != Direction.Up) {
                            continue;
                        }
                        break;
                    case GE:
                        oneOff = true; // fall through
                    case GT:
                        if (iv.direction() != Direction.Down) {
                            continue;
                        }
                        break;
                    default:
                        throw GraalInternalError.shouldNotReachHere();
                }
                loop.setCounted(new CountedLoopInfo(loop, iv, limit, oneOff, negated ? ifNode.falseSuccessor() : ifNode.trueSuccessor()));
            }
        }
    }

    public ControlFlowGraph controlFlowGraph() {
        return cfg;
    }

    public InductionVariable getInductionVariable(ValueNode value) {
        InductionVariable match = null;
        for (LoopEx loop : loops()) {
            InductionVariable iv = loop.getInductionVariables().get(value);
            if (iv != null) {
                if (match != null) {
                    return null;
                }
                match = iv;
            }
        }
        return match;
    }
}
