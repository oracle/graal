/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.common;

import java.util.Optional;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;

/**
 * This phase locates the following pattern in a loop:
 *
 * <pre>
 * PhiNode  ConstantNode
 *     \       /
 *      AddNode(32bit)
 *         |
 *    (ValueProxy)
 *         |
 *    ZeroExtendNode(32bit -> 64bit)
 *         |
 *        ...
 *         |
 *    OffsetAddressNode
 * </pre>
 *
 * and turns into:
 *
 * <pre>
 * PhiNode
 *    |
 * ZeroExtendNode(32bit -> 64bit)
 *     \
 *      \     ConstantNode
 *       \       /
 *        AddNode(64bit)
 *           |
 *          ...
 *           |
 *    OffsetAddressNode
 *
 * if
 * 1. PhiNode and AddNode are always positive;
 * 2. PhiNode is a {@link BasicInductionVariable}; and
 * 3. all nodes between the PhiNode and the OffsetAddressNode are {@link InductionVariable}.
 * </pre>
 *
 * This pattern is generated from array access with a constant offset-ted index, e.g.,
 * https://github.com/openjdk/jdk/blob/b65f7ec2f149d688a37b2c5b2ece312b52133dec/src/java.base/share/classes/sun/security/provider/SHA.java#L158
 * This further triggers constant folding of the offsets, i.e., the constant offset to the index,
 * and the constant offset of the array object header. It may eventually reduce the amount of
 * register spilling, especially on architectures that allow implicit zero extends.
 */
public class OptimizeOffsetAddressPhase extends PostRunCanonicalizationPhase<CoreProviders> {

    private static final int ADDRESS_BITS = 64;
    private static final int INT_BITS = 32;

    public OptimizeOffsetAddressPhase(CanonicalizerPhase canonicalizer) {
        super(canonicalizer);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FIXED_READS, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        if (graph.hasLoops()) {
            LoopsData loopsData = context.getLoopsDataProvider().getLoopsData(graph);
            loopsData.detectCountedLoops();

            for (LoopEx loop : loopsData.countedLoops()) {
                for (OffsetAddressNode offsetAddressNode : loop.whole().nodes().filter(OffsetAddressNode.class)) {
                    tryOptimize(offsetAddressNode, loop);
                }
            }
        }
    }

    private static void tryOptimize(OffsetAddressNode offsetAddressNode, LoopEx loop) {
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        InductionVariable currentIV = ivs.get(offsetAddressNode.getOffset());

        while (currentIV instanceof DerivedInductionVariable) {
            ValueNode currentNode = currentIV.valueNode();

            if (currentNode.isDeleted()) {
                break;
            }

            if (currentNode instanceof ZeroExtendNode zeroExtendNode) {
                tryOptimize(zeroExtendNode, loop);
                break;
            }

            if (currentNode instanceof NarrowNode) {
                break;
            }

            currentIV = ((DerivedInductionVariable) currentIV).getBase();
        }
    }

    /**
     * Match the node pattern described in the class javadoc and replace with the new
     * {@link ZeroExtendNode}.
     */
    private static void tryOptimize(ZeroExtendNode zeroExtendNode, LoopEx loop) {
        if (zeroExtendNode.getInputBits() == INT_BITS && zeroExtendNode.getResultBits() == ADDRESS_BITS &&
                        ((IntegerStamp) zeroExtendNode.getValue().stamp(NodeView.DEFAULT)).isPositive()) {
            ValueNode input = GraphUtil.unproxify(zeroExtendNode.getValue());

            if (input instanceof AddNode addNode &&
                            addNode.getX() instanceof PhiNode phi &&
                            addNode.getY() instanceof ConstantNode cst &&
                            cst.asJavaConstant() != null &&
                            loop.getInductionVariables().get(phi) instanceof BasicInductionVariable inductionVariable) {
                // We know that the sum of phi and cst is always positive or guarded positive, and
                // thus can only be
                // 0 | 0XXXXXXX XXXXXXXX XXXXXXXX XXXXXXXX or
                // 1 | 0XXXXXXX XXXXXXXX XXXXXXXX XXXXXXXX
                //
                // If we can prove that phi is always positive, then
                // a) in the former case, the significant bit of cst must be 0.
                // SignExtend(Add(phi, cst)) can be transformed to
                // Add(SignExtend(phi), SignExtend(cst)), because all the upper bits of
                // SignExtend(phi), SignExtend(cst), and SignExtend(Add) are 0;
                //
                // b) in the latter case, the significant bit of cst must be 1.
                // SignExtend(Add(phi, cst)) can still be transformed to
                // Add(SignExtend(phi), SignExtend(cst)), because the upper bits of SignExtend(cst)
                // are all 1, and thus Add(SignExtend(phi), SignExtend(cst)) is effectively
                // 00000000 00000000 00000000 00000001 0XXXXXXX XXXXXXXX XXXXXXXX XXXXXXXX +
                // 11111111 11111111 11111111 11111111 00000000 00000000 00000000 00000000
                // and is the same as SignExtend(Add(phi, cst)).
                //
                // Since phi and the sum are positive,
                // ZeroExtend(Add(phi, cst)) == Add(ZeroExtend(phi), SignExtend(cst)) always hold.
                CountedLoopInfo countedLoopInfo = loop.counted();
                IntegerStamp initStamp = (IntegerStamp) inductionVariable.initNode().stamp(NodeView.DEFAULT);
                if (initStamp.isPositive()) {
                    int cstAsInt = cst.asJavaConstant().asInt();

                    if (countedLoopInfo.counterNeverOverflows() &&
                                    inductionVariable.isConstantInit() &&
                                    inductionVariable.isConstantStride() &&
                                    inductionVariable.isConstantExtremum()) {
                        long init = inductionVariable.constantInit();
                        long stride = inductionVariable.constantStride();
                        long extremum = inductionVariable.constantExtremum();

                        if (init >= 0 && extremum >= 0) {
                            long shortestTrip = (extremum - init) / stride + 1;
                            if (countedLoopInfo.constantMaxTripCount().equals(shortestTrip)) {
                                // Since the initial value and the extremum value are both positive,
                                // we cannot overflow in between if the shortest trip count is the
                                // maximum trip count. Therefore, phi is always positive.
                                replace(zeroExtendNode, phi, cstAsInt);
                                return;
                            }
                        }
                    }
                    if (countedLoopInfo.getLimitCheckedIV() == inductionVariable &&
                                    inductionVariable.direction() == InductionVariable.Direction.Up &&
                                    (countedLoopInfo.getOverFlowGuard() != null || countedLoopInfo.counterNeverOverflows())) {
                        replace(zeroExtendNode, phi, cstAsInt);
                    }
                }
            }
        }
    }

    /**
     * Replace {@code zeroExtendNode} with 64-bits {@link AddNode} of a new {@link ZeroExtendNode}
     * of the {@code phi} and the {@code cst}.
     */
    private static void replace(ZeroExtendNode zeroExtendNode, PhiNode phi, long cst) {
        StructuredGraph graph = zeroExtendNode.graph();
        ZeroExtendNode newZeroExtendNode = graph.unique(new ZeroExtendNode(phi, INT_BITS, ADDRESS_BITS));
        AddNode newAddNode = graph.unique(new AddNode(newZeroExtendNode, ConstantNode.forLong(cst, graph)));
        zeroExtendNode.replaceAndDelete(newAddNode);
    }
}
