/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.nodes.simd;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.VectorLIRLowerable;
import jdk.vm.ci.meta.JavaKind;

/**
 * Convert a SIMD vector to a 64-bit bitmask, where every bit is equal to the most significant bit
 * of every element in the vector. The bitmask is zero-extended if the vector has fewer than 64
 * elements.
 * </p>
 *
 * Outside users building this node can only build it as a 64-bit value. However, this node may
 * canonicalize itself to a narrower value if permitted by its context.
 */
@NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
public final class SimdToBitMaskNode extends UnaryNode implements VectorLIRLowerable {
    public static final NodeClass<SimdToBitMaskNode> TYPE = NodeClass.create(SimdToBitMaskNode.class);

    public SimdToBitMaskNode(ValueNode vector) {
        this(vector, JavaKind.Long);
    }

    private SimdToBitMaskNode(ValueNode vector, JavaKind resultKind) {
        super(TYPE, computeStamp(vector.stamp(NodeView.DEFAULT), resultKind), vector);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forConstant(stamp, ((SimdConstant) forValue.asConstant()).toBitMask(), tool.getMetaAccess());
        }
        if (forValue instanceof SimdPrimitiveCompareNode compare && compare.getCondition().equals(CanonicalCondition.LT) &&
                        compare.getY().stamp(NodeView.from(tool)) instanceof SimdStamp yStamp && yStamp.isIntegerStamp() && yStamp.isAllZeros()) {
            /*
             * We're extracting the most significant bits of the result of a `vector < zeroVector`
             * comparison. Those bits are 1 iff the corresponding elements are negative, i.e., the
             * sign bit is set. So we can skip the compare and just extract the sign bits directly.
             * Not valid for floating point vectors because of NaNs and negative zero.
             */
            return new SimdToBitMaskNode(compare.getX(), stamp.getStackKind());
        }
        if (stamp.getStackKind() == JavaKind.Long && tool.allUsagesAvailable() && hasExactlyOneUsage() &&
                        singleUsage() instanceof NarrowNode narrowUsage && narrowUsage.getResultBits() == JavaKind.Int.getBitCount()) {
            /*
             * We're only interested in up to 32 bits of this mask. We can skip the narrow and just
             * compute a narrower SimdToBitMask. Because we can't replace the usage directly while
             * canonicalizing this node, we insert a temporary ZeroExtend that will fold away
             * together with the narrow.
             */
            return ZeroExtendNode.create(new SimdToBitMaskNode(forValue, JavaKind.Int), JavaKind.Long.getBitCount(), NodeView.from(tool));
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, VectorLIRGeneratorTool gen) {
        LIRKind resultKind = builder.getLIRGeneratorTool().getLIRKind(stamp(NodeView.DEFAULT));
        builder.setResult(this, gen.emitVectorToBitMask(resultKind, builder.operand(getValue())));
    }

    private static Stamp computeStamp(Stamp stamp, JavaKind resultKind) {
        Stamp result = StampFactory.forKind(resultKind);
        return stamp.isEmpty() ? result.empty() : result;
    }
}
