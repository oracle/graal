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

import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.compiler.util.LoopUtil.Loop;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.oracle.max.graal.nodes.loop.*;
import com.sun.cri.ci.*;


/**
 * Looks for linear induction variables in loops.
 * Saves the information in the graph by replacing these induction variables computations with subclasses of {@link InductionVariableNode} :
 * <ul>
 * <li> {@link LoopCounterNode} is the iteration counter (from 0 to Niter)</li>
 * <li> {@link BasicInductionVariableNode} is an induction variable of the form {@code stride * loopCount + init}. Computed using a phi and an add node</li>
 * <li> {@link DerivedInductionVariableNode} is an induction variable of the form {@code scale * base + offset} where base is an other of {@link InductionVariableNode}. Computed using multiply and add</li>
 * </ul>
 * This phase works in collaboration with {@link RemoveInductionVariablesPhase} which will convert the {@link InductionVariableNode}s back to phis and arithmetic nodes.
 */
public class FindInductionVariablesPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        List<Loop> loops = LoopUtil.computeLoops(graph);

        for (Loop loop : loops) {
            findInductionVariables(loop);
        }
    }

    private void findInductionVariables(Loop loop) {
        LoopBeginNode loopBegin = loop.loopBegin();
        NodeBitMap loopNodes = loop.nodes();
        for (PhiNode phi : loopBegin.phis().snapshot()) {
            ValueNode init = phi.valueAt(loopBegin.forwardEdge());
            ValueNode backEdge = phi.valueAt(loopBegin.loopEnd());
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
                        stride = graph.unique(new NegateNode(stride));
                    }
                    CiKind kind = phi.kind();
                    LoopCounterNode counter = loopBegin.loopCounter(kind);
                    BasicInductionVariableNode biv1 = null;
                    BasicInductionVariableNode biv2 = null;
                    if (phi.usages().size() > 1) {
                        biv1 = graph.add(new BasicInductionVariableNode(kind, init, stride, counter));
                        phi.replaceAndDelete(biv1);
                    } else {
                        phi.replaceFirstInput(binary, null);
                        phi.safeDelete();
                    }
                    if (backEdge.usages().size() > 0) {
                        biv2 = graph.add(new BasicInductionVariableNode(kind, IntegerArithmeticNode.add(init, stride), stride, counter));
                        backEdge.replaceAndDelete(biv2);
                    } else {
                        backEdge.safeDelete();
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
                    scale = ConstantNode.forIntegerKind(kind, 1, biv.graph());
                } else if (offset == null) {
                    offset = ConstantNode.forIntegerKind(kind, 0, biv.graph());
                }
                DerivedInductionVariableNode div = biv.graph().add(new DerivedInductionVariableNode(kind, offset, scale, biv));
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
