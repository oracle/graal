/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.amd64;

import org.graalvm.collections.EconomicMap;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.core.amd64.AMD64AddressNode;
import jdk.graal.compiler.core.amd64.AMD64CompressAddressLowering;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.nodes.CompressionNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.loop.BasicInductionVariable;
import jdk.graal.compiler.nodes.loop.CountedLoopInfo;
import jdk.graal.compiler.nodes.loop.DerivedInductionVariable;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.loop.LoopsData;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoopsDataProvider;
import jdk.vm.ci.code.Register;

public class AMD64HotSpotAddressLowering extends AMD64CompressAddressLowering {

    private static final int ADDRESS_BITS = 64;
    private static final int INT_BITS = 32;

    private final long heapBase;
    private final Register heapBaseRegister;

    public AMD64HotSpotAddressLowering(GraalHotSpotVMConfig config, Register heapBaseRegister) {
        this.heapBase = config.getOopEncoding().getBase();
        if (heapBase == 0) {
            this.heapBaseRegister = null;
        } else {
            this.heapBaseRegister = heapBaseRegister;
        }
    }

    @Override
    protected final boolean improveUncompression(AMD64AddressNode addr, CompressionNode compression, ValueNode other) {
        CompressEncoding encoding = compression.getEncoding();
        if (!AMD64Address.isScaleShiftSupported(encoding.getShift())) {
            return false;
        }

        if (heapBaseRegister != null && encoding.getBase() == heapBase) {
            if (other == null) {
                ValueNode base = compression.graph().unique(new HeapBaseNode(heapBaseRegister));
                addr.setBase(base);
            } else {
                return false;
            }
        } else if (encoding.getBase() != 0) {
            if (updateDisplacement(addr, encoding.getBase(), false)) {
                addr.setBase(other);
            } else {
                return false;
            }
        } else {
            addr.setBase(other);
        }

        Stride stride = Stride.fromLog2(encoding.getShift());
        addr.setScale(stride);
        addr.setIndex(compression.getValue());
        return true;
    }

    @Override
    public void preProcess(StructuredGraph graph, LoopsDataProvider loopsDataProvider) {
        if (graph.hasLoops()) {
            LoopsData loopsData = loopsDataProvider.getLoopsData(graph);
            loopsData.detectCountedLoops();
            for (LoopEx loop : loopsData.countedLoops()) {
                for (OffsetAddressNode offsetAdressNode : loop.whole().nodes().filter(OffsetAddressNode.class)) {
                    tryOptimize(offsetAdressNode, loop);
                }
            }
        }
    }

    @Override
    public void postProcess(AddressNode lowered) {
        // Allow implicit zero extend for always positive input. This
        // assumes that the upper bits of the operand is zero out by
        // the backend.
        AMD64AddressNode address = (AMD64AddressNode) lowered;
        address.setBase(tryImplicitZeroExtend(address.getBase()));
        address.setIndex(tryImplicitZeroExtend(address.getIndex()));
    }

    private static void tryOptimize(OffsetAddressNode offsetAddress, LoopEx loop) {
        EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
        InductionVariable currentIV = ivs.get(offsetAddress.getOffset());
        while (currentIV != null) {
            if (!(currentIV instanceof DerivedInductionVariable)) {
                break;
            }
            ValueNode currentValue = currentIV.valueNode();
            if (currentValue.isDeleted()) {
                break;
            }

            if (currentValue instanceof ZeroExtendNode) {
                ZeroExtendNode zeroExtendNode = (ZeroExtendNode) currentValue;
                if (applicableToImplicitZeroExtend(zeroExtendNode)) {
                    ValueNode input = zeroExtendNode.getValue();
                    if (input instanceof AddNode) {
                        AddNode add = (AddNode) input;
                        if (add.getX().isConstant()) {
                            optimizeAdd(zeroExtendNode, (ConstantNode) add.getX(), add.getY(), loop);
                        } else if (add.getY().isConstant()) {
                            optimizeAdd(zeroExtendNode, (ConstantNode) add.getY(), add.getX(), loop);
                        }
                    }
                }
            }

            currentIV = ((DerivedInductionVariable) currentIV).getBase();
        }
    }

    /**
     * Given that Add(a, cst) is always positive, performs the following: ZeroExtend(Add(a, cst)) ->
     * Add(SignExtend(a), SignExtend(cst)).
     */
    private static void optimizeAdd(ZeroExtendNode zeroExtendNode, ConstantNode constant, ValueNode other, LoopEx loop) {
        StructuredGraph graph = zeroExtendNode.graph();
        AddNode addNode = graph.unique(new AddNode(signExtend(other, loop), ConstantNode.forLong(constant.asJavaConstant().asInt(), graph)));
        zeroExtendNode.replaceAtUsages(addNode);
    }

    /**
     * Create a sign extend for {@code input}, or zero extend if {@code input} can be proven
     * positive.
     */
    private static ValueNode signExtend(ValueNode input, LoopEx loop) {
        StructuredGraph graph = input.graph();
        if (input instanceof PhiNode) {
            EconomicMap<Node, InductionVariable> ivs = loop.getInductionVariables();
            InductionVariable inductionVariable = ivs.get(input);
            if (inductionVariable != null && inductionVariable instanceof BasicInductionVariable) {
                CountedLoopInfo countedLoopInfo = loop.counted();
                IntegerStamp initStamp = (IntegerStamp) inductionVariable.initNode().stamp(NodeView.DEFAULT);
                if (initStamp.isPositive()) {
                    if (inductionVariable.isConstantExtremum() && countedLoopInfo.counterNeverOverflows()) {
                        long init = inductionVariable.constantInit();
                        long stride = inductionVariable.constantStride();
                        long extremum = inductionVariable.constantExtremum();

                        if (init >= 0 && extremum >= 0) {
                            long shortestTrip = (extremum - init) / stride + 1;
                            if (countedLoopInfo.constantMaxTripCount().equals(shortestTrip)) {
                                return graph.unique(new ZeroExtendNode(input, INT_BITS, ADDRESS_BITS, true));
                            }
                        }
                    }
                    if (countedLoopInfo.getLimitCheckedIV() == inductionVariable &&
                                    inductionVariable.direction() == InductionVariable.Direction.Up &&
                                    (countedLoopInfo.getOverFlowGuard() != null || countedLoopInfo.counterNeverOverflows())) {
                        return graph.unique(new ZeroExtendNode(input, INT_BITS, ADDRESS_BITS, true));
                    }
                }
            }
        }
        return input.graph().addOrUnique(SignExtendNode.create(input, ADDRESS_BITS, NodeView.DEFAULT));
    }

    private static boolean applicableToImplicitZeroExtend(ZeroExtendNode zeroExtendNode) {
        return zeroExtendNode.isInputAlwaysPositive() && zeroExtendNode.getInputBits() == INT_BITS && zeroExtendNode.getResultBits() == ADDRESS_BITS;
    }

    private static ValueNode tryImplicitZeroExtend(ValueNode input) {
        if (input instanceof ZeroExtendNode) {
            ZeroExtendNode zeroExtendNode = (ZeroExtendNode) input;
            if (applicableToImplicitZeroExtend(zeroExtendNode)) {
                return zeroExtendNode.getValue();
            }
        }
        return input;
    }

}
