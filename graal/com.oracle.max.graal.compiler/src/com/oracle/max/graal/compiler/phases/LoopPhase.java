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
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.sun.cri.ci.*;


public class LoopPhase extends Phase {

    @Override
    protected void run(Graph graph) {
        List<Loop> loops = LoopUtil.computeLoops(graph);

        if (GraalOptions.LoopPeeling || GraalOptions.LoopInversion) {
            for (Loop loop : loops) {
                boolean hasBadExit = false;
                for (Node exit : loop.exits()) {
                    if (!(exit instanceof StateSplit) || ((StateSplit) exit).stateAfter() == null) {
                        // TODO (gd) can not do loop peeling if an exit has no state. see LoopUtil.findNearestMergableExitPoint
                        hasBadExit = true;
                        break;
                    }
                }
                if (hasBadExit) {
                    continue;
                }
                boolean canInvert = false;
                if (GraalOptions.LoopInversion  && loop.loopBegin().next() instanceof If) {
                    If ifNode = (If) loop.loopBegin().next();
                    if (loop.exits().isMarked(ifNode.trueSuccessor()) || loop.exits().isMarked(ifNode.falseSuccessor())) {
                        canInvert = true;
                    }
                }
                if (canInvert) {
                    LoopUtil.inverseLoop(loop, (If) loop.loopBegin().next());
                } else if (GraalOptions.LoopPeeling) {
                    GraalCompilation compilation = GraalCompilation.compilation();
                    if (compilation.compiler.isObserved()) {
                        Map<String, Object> debug = new HashMap<String, Object>();
                        debug.put("loopExits", loop.exits());
                        debug.put("inOrBefore", loop.inOrBefore());
                        debug.put("inOrAfter", loop.inOrAfter());
                        debug.put("nodes", loop.nodes());
                        compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Loop #" + loop.loopBegin().id(), graph, true, false, debug));
                    }
                    LoopUtil.peelLoop(loop);
                }
            }
        }

        for (Loop loop : loops) {
            findInductionVariables(loop);
        }
    }

    private void findInductionVariables(Loop loop) {
        LoopBegin loopBegin = loop.loopBegin();
        NodeBitMap loopNodes = loop.nodes();
        List<Phi> phis = new ArrayList<Phi>(loopBegin.phis());
        int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        int initIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (Phi phi : phis) {
            Value init = phi.valueAt(initIndex);
            Value backEdge = phi.valueAt(backIndex);
            if (loopNodes.isNew(init) || loopNodes.isNew(backEdge)) {
                continue;
            }
            if (loopNodes.isMarked(backEdge)) {
                Binary binary;
                if (backEdge instanceof IntegerAdd || backEdge instanceof IntegerSub) {
                    binary = (Binary) backEdge;
                } else {
                    continue;
                }
                Value stride;
                if (binary.x() == phi) {
                    stride = binary.y();
                } else if (binary.y() == phi) {
                    stride = binary.x();
                } else {
                    continue;
                }
                if (loopNodes.isNotNewNotMarked(stride)) {
                    Graph graph = loopBegin.graph();
                    if (backEdge instanceof IntegerSub) {
                        stride = new Negate(stride, graph);
                    }
                    CiKind kind = phi.kind;
                    LoopCounter counter = loopBegin.loopCounter(kind);
                    BasicInductionVariable biv1 = null;
                    BasicInductionVariable biv2 = null;
                    if (phi.usages().size() > 1) {
                        biv1 = new BasicInductionVariable(kind, init, stride, counter, graph);
                        phi.replaceAndDelete(biv1);
                    } else {
                        phi.replaceFirstInput(binary, null);
                        phi.delete();
                    }
                    if (backEdge.usages().size() > 0) {
                        biv2 = new BasicInductionVariable(kind, IntegerArithmeticNode.add(init, stride), stride, counter, graph);
                        backEdge.replaceAndDelete(biv2);
                    } else {
                        backEdge.delete();
                    }
                    if (biv1 != null) {
                        findDerivedInductionVariable(biv1, kind, loopNodes);
                    }
                    if (biv2 != null) {
                        findDerivedInductionVariable(biv2, kind, loopNodes);
                    }
                }
            }
        }
    }
    private void findDerivedInductionVariable(BasicInductionVariable biv, CiKind kind, NodeBitMap loopNodes) {
        for (Node usage : biv.usages().snapshot()) {
            Value scale = scale(usage, biv, loopNodes);
            Value offset = null;
            Node node = null;
            if (scale == null) {
                if (usage instanceof IntegerAdd) {
                    IntegerAdd add = (IntegerAdd) usage;
                    if (add.x() == biv || (scale = scale(add.x(), biv, loopNodes)) != null) {
                        offset = add.y();
                    } else if (add.y() == biv || (scale = scale(add.y(), biv, loopNodes)) != null) {
                        offset = add.x();
                    }
                    if (offset != null) {
                        if (loopNodes.isNotNewNotMarked(offset)) {
                            node = add;
                        } else {
                            offset = null;
                        }
                    }
                }
            } else {
                node = usage;
            }
            if (scale != null || offset != null) {
                if (scale == null) {
                    scale = Constant.forInt(1, biv.graph());
                } else if (offset == null) {
                    offset = Constant.forInt(0, biv.graph());
                }
                DerivedInductionVariable div = new DerivedInductionVariable(kind, offset, scale, biv, biv.graph());
                node.replaceAndDelete(div);
            }
        }
    }

    private Value scale(Node n, BasicInductionVariable biv, NodeBitMap loopNodes) {
        if (n instanceof IntegerMul) {
            IntegerMul mul = (IntegerMul) n;
            Value scale = null;
            if (mul.x() == biv) {
                scale = mul.y();
            } else if (mul.y() == biv) {
                scale = mul.x();
            }
            if (scale != null && loopNodes.isNotNewNotMarked(scale)) {
                return scale;
            }
        }
        return null;
    }
}
