/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.nodes;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_8;

import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.nodes.util.GraphUtil;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

/**
 * Intrinsification for {@link Integer#compareUnsigned(int, int)} and
 * {@link Long#compareUnsigned(long, long)}.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_8)
public final class UnsignedCompareNode extends BinaryNode implements LIRLowerable {

    public static final NodeClass<UnsignedCompareNode> TYPE = NodeClass.create(UnsignedCompareNode.class);

    public UnsignedCompareNode(ValueNode x, ValueNode y) {
        super(TYPE, StampFactory.forInteger(32, -1, 1), x, y);
    }

    public static boolean reuseCompareInBackend(Architecture arch) {
        return arch instanceof AMD64 || arch instanceof AArch64;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        if (stampX instanceof IntegerStamp && stampY instanceof IntegerStamp) {
            IntegerStamp integerStampX = (IntegerStamp) stampX;
            IntegerStamp integerStampY = (IntegerStamp) stampY;

            if (integerStampX.getBits() == 32) {
                GraalError.guarantee(integerStampY.getBits() == 32, "incompatible stampY: %s", stampY);
                if (Integer.compareUnsigned((int) integerStampX.downMask(), (int) integerStampY.upMask()) > 0) {
                    return StampFactory.forConstant(JavaConstant.INT_1);
                } else if (Integer.compareUnsigned((int) integerStampX.upMask(), (int) integerStampY.downMask()) < 0) {
                    return StampFactory.forConstant(JavaConstant.INT_MINUS_1);
                }
            } else {
                GraalError.guarantee(integerStampX.getBits() == 64 && integerStampY.getBits() == 64, "incompatible stamps: %s %s", stampX, stampY);
                if (Long.compareUnsigned(integerStampX.downMask(), integerStampY.upMask()) > 0) {
                    return StampFactory.forConstant(JavaConstant.INT_1);
                } else if (Long.compareUnsigned(integerStampX.upMask(), integerStampY.downMask()) < 0) {
                    return StampFactory.forConstant(JavaConstant.INT_MINUS_1);
                }
            }
        }

        return stamp(NodeView.DEFAULT);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        if (GraphUtil.unproxify(forX) == GraphUtil.unproxify(forY)) {
            return ConstantNode.forInt(0);
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        Value result = gen.getLIRGeneratorTool().emitUnsignedCompare(gen.operand(x), gen.operand(y));
        gen.setResult(this, result);
    }
}
