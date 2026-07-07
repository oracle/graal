/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.vector.phases;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.NegateNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.Loop;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.FloatingReadNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;

/**
 * Offset addresses [base, offset] that are used in loops should preferably have the following
 * properties:
 * <ul>
 * <li>base should be outside the loop</li>
 * <li>offset should be an induction variable</li>
 * </ul>
 *
 * Later phases such as the OptimisticAliasingAnalysisPhase and LoopVectorizationPhase rely on these
 * properties and can do a better job if we ensure these properties beforehand.
 */
public class OptimizeAddressesInLoopsPhase extends BasePhase<MidTierContext> {

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return ALWAYS_APPLICABLE;
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops();
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        if (graph.hasLoops()) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            for (Loop loop : loopsData.countedLoops()) {
                for (FloatingReadNode read : loop.whole().nodes().filter(FloatingReadNode.class)) {
                    OffsetAddressNode address = optimizeAddress((OffsetAddressNode) read.getAddress(), loop);
                    if (address != read.getAddress()) {
                        read.setAddress(address);
                    }
                }

                for (ReadNode read : loop.whole().nodes().filter(ReadNode.class)) {
                    OffsetAddressNode address = optimizeAddress((OffsetAddressNode) read.getAddress(), loop);
                    if (address != read.getAddress()) {
                        read.setAddress(address);
                    }
                }

                for (WriteNode write : loop.counted().getBody().getBlockNodes().filter(WriteNode.class)) {
                    OffsetAddressNode address = optimizeAddress((OffsetAddressNode) write.getAddress(), loop);
                    if (address != write.getAddress()) {
                        write.setAddress(address);
                    }
                }
            }
        }
    }

    private static OffsetAddressNode optimizeAddress(OffsetAddressNode offsetAddress, Loop loop) {
        // check if we can split base and offset in a way that base is outside the loop and offset
        // is an induction variable.
        ValueNode base = offsetAddress.getBase();
        ValueNode offset = offsetAddress.getOffset();
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        if (!loop.isOutsideLoop(base) || !isInductionVariable(offset, ivs)) {
            if (base instanceof AddNode || base instanceof SubNode) {
                BinaryArithmeticNode<?> arithmeticBase = (BinaryArithmeticNode<?>) base;
                if (loop.isOutsideLoop(arithmeticBase.getX())) {
                    if (canCreateDerivedInductionVariable(loop, ivs, offset, arithmeticBase.getY())) {
                        return computeOptimizedAddress(arithmeticBase, arithmeticBase.getX(), arithmeticBase.getY(), true, offset);
                    }
                } else if (loop.isOutsideLoop(arithmeticBase.getY())) {
                    if (canCreateDerivedInductionVariable(loop, ivs, offset, arithmeticBase.getX())) {
                        return computeOptimizedAddress(arithmeticBase, arithmeticBase.getY(), arithmeticBase.getX(), false, offset);
                    }
                }
            } else if (isInductionVariable(base, ivs) && loop.isOutsideLoop(offset) && base.stamp(NodeView.DEFAULT) instanceof IntegerStamp) {
                // Raw memory address with a variable base and non-variable offset, swap it around.
                return offsetAddress.graph().unique(new OffsetAddressNode(offset, base));
            }
        }

        return offsetAddress;
    }

    @SuppressWarnings("try")
    private static OffsetAddressNode computeOptimizedAddress(BinaryArithmeticNode<?> oldBase, ValueNode newBase, ValueNode newOffset, boolean yIsOffset, ValueNode oldOffset) {
        StructuredGraph graph = oldBase.graph();

        try (DebugCloseable position = oldBase.withNodeSourcePosition()) {
            ValueNode finalBase = newBase;
            ValueNode finalNewOffset = newOffset;
            if (oldBase instanceof SubNode) {
                if (yIsOffset) {
                    finalNewOffset = graph.addOrUniqueWithInputs(NegateNode.create(finalNewOffset, NodeView.DEFAULT));
                } else {
                    finalBase = graph.addOrUniqueWithInputs(NegateNode.create(finalBase, NodeView.DEFAULT));
                }
            }

            ValueNode combinedOffset = graph.addOrUniqueWithInputs(AddNode.create(finalNewOffset, oldOffset, NodeView.DEFAULT));
            graph.getOptimizationLog().report(OptimizeAddressesInLoopsPhase.class, "AddressOptimization", oldBase);
            return graph.unique(new OffsetAddressNode(finalBase, combinedOffset));
        }
    }

    private static boolean isInductionVariable(ValueNode value, EconomicMap<Node, InductionVariable> ivs) {
        return ivs.containsKey(value);
    }

    private static boolean canCreateDerivedInductionVariable(Loop loop, EconomicMap<Node, InductionVariable> ivs, ValueNode a, ValueNode b) {
        return isInductionVariable(a, ivs) && loop.isOutsideLoop(b) || isInductionVariable(b, ivs) && loop.isOutsideLoop(a);
    }
}
