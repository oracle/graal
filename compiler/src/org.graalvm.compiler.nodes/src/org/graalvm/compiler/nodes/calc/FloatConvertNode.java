/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.calc;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_8;
import static org.graalvm.compiler.nodes.calc.BinaryArithmeticNode.getArithmeticOpTable;

import org.graalvm.compiler.core.common.calc.FloatConvert;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.FloatConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.UnaryOp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.Lowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;

/**
 * A {@code FloatConvert} converts between integers and floating point numbers according to Java
 * semantics.
 */
@NodeInfo(cycles = CYCLES_8)
public final class FloatConvertNode extends UnaryArithmeticNode<FloatConvertOp> implements ConvertNode, Lowerable, ArithmeticLIRLowerable {
    public static final NodeClass<FloatConvertNode> TYPE = NodeClass.create(FloatConvertNode.class);

    protected final FloatConvert op;

    public FloatConvertNode(FloatConvert op, ValueNode input) {
        super(TYPE, getArithmeticOpTable(input).getFloatConvert(op), input);
        this.op = op;
    }

    public static ValueNode create(FloatConvert op, ValueNode input, NodeView view) {
        ValueNode synonym = findSynonym(input, ArithmeticOpTable.forStamp(input.stamp(view)).getFloatConvert(op));
        if (synonym != null) {
            return synonym;
        }
        return new FloatConvertNode(op, input);
    }

    @Override
    protected UnaryOp<FloatConvertOp> getOp(ArithmeticOpTable table) {
        return table.getFloatConvert(op);
    }

    public FloatConvert getFloatConvert() {
        return op;
    }

    @Override
    public Constant convert(Constant c, ConstantReflectionProvider constantReflection) {
        return getArithmeticOp().foldConstant(c);
    }

    @Override
    public Constant reverse(Constant c, ConstantReflectionProvider constantReflection) {
        FloatConvertOp reverse = ArithmeticOpTable.forStamp(stamp(NodeView.DEFAULT)).getFloatConvert(op.reverse());
        return reverse.foldConstant(c);
    }

    @Override
    public boolean isLossless() {
        switch (getFloatConvert()) {
            case F2D:
            case I2D:
                return true;
            default:
                return false;
        }
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode ret = super.canonical(tool, forValue);
        if (ret != this) {
            return ret;
        }

        if (forValue instanceof FloatConvertNode) {
            FloatConvertNode other = (FloatConvertNode) forValue;
            if (other.isLossless() && other.op == this.op.reverse()) {
                return other.getValue();
            }
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        nodeValueMap.setResult(this, gen.emitFloatConvert(getFloatConvert(), nodeValueMap.operand(getValue())));
    }

    @Override
    public boolean mayNullCheckSkipConversion() {
        return false;
    }
}
