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
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.loop.*;
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
                if (GraalOptions.LoopInversion  && loop.loopBegin().next() instanceof IfNode) {
                    IfNode ifNode = (IfNode) loop.loopBegin().next();
                    if (loop.exits().isMarked(ifNode.trueSuccessor()) || loop.exits().isMarked(ifNode.falseSuccessor())) {
                        canInvert = true;
                    }
                }
                if (canInvert) {
                    LoopUtil.inverseLoop(loop, (IfNode) loop.loopBegin().next());
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
        LoopBeginNode loopBegin = loop.loopBegin();
        NodeBitMap loopNodes = loop.nodes();
        List<PhiNode> phis = new ArrayList<PhiNode>(loopBegin.phis());
        int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        int initIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (PhiNode phi : phis) {
            ValueNode init = phi.valueAt(initIndex);
            ValueNode backEdge = phi.valueAt(backIndex);
            if (loopNodes.isNew(init) || loopNodes.isNew(backEdge)) {
                continue;
            }
            if (loopNodes.isMarked(backEdge)) {
                BinaryNode binary;
                if (backEdge instanceof IntegerAddNode || backEdge instanceof IntegerSubNode) {
                    binary = (BinaryNode) backEdge;
                } else {
                    continue;
                }
                ValueNode stride;
                if (binary.x() == phi) {
                    stride = binary.y();
                } else if (binary.y() == phi) {
                    stride = binary.x();
                } else {
                    continue;
                }
                if (loopNodes.isNotNewNotMarked(stride)) {
                    Graph graph = loopBegin.graph();
                    if (backEdge instanceof IntegerSubNode) {
                        stride = new NegateNode(stride, graph);
                    }
                    CiKind kind = phi.kind;
                    LoopCounterNode counter = loopBegin.loopCounter(kind);
                    BasicInductionVariableNode biv1 = null;
                    BasicInductionVariableNode biv2 = null;
                    if (phi.usages().size() > 1) {
                        biv1 = new BasicInductionVariableNode(kind, init, stride, counter, graph);
                        phi.replaceAndDelete(biv1);
                    } else {
                        phi.replaceFirstInput(binary, null);
                        phi.delete();
                    }
                    if (backEdge.usages().size() > 0) {
                        biv2 = new BasicInductionVariableNode(kind, IntegerArithmeticNode.add(init, stride), stride, counter, graph);
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
    private void findDerivedInductionVariable(BasicInductionVariableNode biv, CiKind kind, NodeBitMap loopNodes) {
        for (Node usage : biv.usages().snapshot()) {
            ValueNode scale = scale(usage, biv, loopNodes);
            ValueNode offset = null;
            Node node = null;
            if (scale == null) {
                if (usage instanceof IntegerAddNode) {
                    IntegerAddNode add = (IntegerAddNode) usage;
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
                    scale = ConstantNode.forInt(1, biv.graph());
                } else if (offset == null) {
                    offset = ConstantNode.forInt(0, biv.graph());
                }
                DerivedInductionVariableNode div = new DerivedInductionVariableNode(kind, offset, scale, biv, biv.graph());
                node.replaceAndDelete(div);
            }
        }
    }

    private ValueNode scale(Node n, BasicInductionVariableNode biv, NodeBitMap loopNodes) {
        if (n instanceof IntegerMulNode) {
            IntegerMulNode mul = (IntegerMulNode) n;
            ValueNode scale = null;
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
