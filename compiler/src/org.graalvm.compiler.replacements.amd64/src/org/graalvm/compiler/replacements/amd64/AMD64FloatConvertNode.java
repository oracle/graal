/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.amd64;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_1;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.FloatConvertNode;
import org.graalvm.compiler.nodes.calc.UnaryArithmeticNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaConstant;

/**
 * This node has the semantics of the AMD64 floating point conversions. It is used in the lowering
 * of the {@link FloatConvertNode} which, on AMD64 needs a {@link AMD64FloatConvertNode} plus some
 * fixup code that handles the corner cases that differ between AMD64 and Java.
 *
 * Since this node evaluates to a special value if the conversion is inexact, its stamp must be
 * modified to avoid optimizing away {@link AMD64ConvertSnippets}.
 */
@NodeInfo(cycles = CYCLES_8, size = SIZE_1)
public final class AMD64FloatConvertNode extends UnaryArithmeticNode<FloatConvertOp> implements ArithmeticLIRLowerable {
    public static final NodeClass<AMD64FloatConvertNode> TYPE = NodeClass.create(AMD64FloatConvertNode.class);

    protected final FloatConvert op;

    public AMD64FloatConvertNode(FloatConvert op, ValueNode value) {
        super(TYPE, getArithmeticOpTable(value).getFloatConvert(op), value);
        this.op = op;
        this.stamp = this.stamp.meet(createInexactCaseStamp());
    }

    @Override
    protected UnaryOp<FloatConvertOp> getOp(ArithmeticOpTable table) {
        return table.getFloatConvert(op);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isJavaConstant()) {
            // This doesn't match the semantics of CVTTSS2SI but since this is only used by
            // AMD64ConvertSnippets this will fold to still produce the right result.
            UnaryOp<FloatConvertOp> floatConvertOp = getOp(getArithmeticOpTable(value));
            return ConstantNode.forPrimitive(floatConvertOp.foldStamp(forValue.stamp(NodeView.DEFAULT)), floatConvertOp.foldConstant(forValue.asConstant()));
        }
        // nothing to do
        return this;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        // The semantics of the x64 CVTTSS2SI instruction allow returning 0x8000000 in the special
        // cases.
        Stamp foldedStamp = super.foldStamp(newStamp);
        return foldedStamp.meet(createInexactCaseStamp());
    }

    private Stamp createInexactCaseStamp() {
        IntegerStamp intStamp = (IntegerStamp) this.stamp;
        long inexactValue = intStamp.getBits() <= 32 ? 0x8000_0000L : 0x8000_0000_0000_0000L;
        return StampFactory.forConstant(JavaConstant.forPrimitiveInt(intStamp.getBits(), inexactValue));
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitFloatConvert(op, nodeValueMap.operand(getValue())));
    }
}
