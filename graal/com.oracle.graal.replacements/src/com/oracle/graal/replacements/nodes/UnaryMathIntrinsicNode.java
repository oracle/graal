/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.nodes;

import com.oracle.graal.compiler.common.spi.ForeignCallDescriptor;
import com.oracle.graal.compiler.common.type.FloatStamp;
import com.oracle.graal.compiler.common.type.PrimitiveStamp;
import com.oracle.graal.compiler.common.type.Stamp;
import com.oracle.graal.compiler.common.type.StampFactory;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.graph.NodeClass;
import com.oracle.graal.graph.spi.CanonicalizerTool;
import com.oracle.graal.lir.gen.ArithmeticLIRGeneratorTool;
import com.oracle.graal.nodeinfo.NodeCycles;
import com.oracle.graal.nodeinfo.NodeInfo;
import com.oracle.graal.nodeinfo.NodeSize;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.UnaryNode;
import com.oracle.graal.nodes.spi.ArithmeticLIRLowerable;
import com.oracle.graal.nodes.spi.Lowerable;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = NodeCycles.CYCLES_UNKOWN, size = NodeSize.SIZE_1)
public final class UnaryMathIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, Lowerable {

    public static final NodeClass<UnaryMathIntrinsicNode> TYPE = NodeClass.create(UnaryMathIntrinsicNode.class);
    protected final UnaryOperation operation;

    public enum UnaryOperation {
        LOG(new ForeignCallDescriptor("arithmeticLog", double.class, double.class)),
        LOG10(new ForeignCallDescriptor("arithmeticLog10", double.class, double.class)),
        SIN(new ForeignCallDescriptor("arithmeticSin", double.class, double.class)),
        COS(new ForeignCallDescriptor("arithmeticCos", double.class, double.class)),
        TAN(new ForeignCallDescriptor("arithmeticTan", double.class, double.class)),
        EXP(new ForeignCallDescriptor("arithmeticExp", double.class, double.class));

        public final ForeignCallDescriptor foreignCallDescriptor;

        UnaryOperation(ForeignCallDescriptor foreignCallDescriptor) {
            this.foreignCallDescriptor = foreignCallDescriptor;
        }
    }

    public UnaryOperation getOperation() {
        return operation;
    }

    public static ValueNode create(ValueNode value, UnaryOperation op) {
        ValueNode c = tryConstantFold(value, op);
        if (c != null) {
            return c;
        }
        return new UnaryMathIntrinsicNode(value, op);
    }

    protected static ValueNode tryConstantFold(ValueNode value, UnaryOperation op) {
        if (value.isConstant()) {
            double ret = doCompute(value.asJavaConstant().asDouble(), op);
            return ConstantNode.forDouble(ret);
        }
        return null;
    }

    protected UnaryMathIntrinsicNode(ValueNode value, UnaryOperation op) {
        super(TYPE, StampFactory.forKind(JavaKind.Double), value);
        assert value.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp()) == 64;
        this.operation = op;
    }

    @Override
    public Stamp foldStamp(Stamp newStamp) {
        if (newStamp instanceof FloatStamp) {
            FloatStamp floatStamp = (FloatStamp) newStamp;
            switch (getOperation()) {
                case COS:
                case SIN:
                    boolean nonNaN = floatStamp.lowerBound() != Double.NEGATIVE_INFINITY && floatStamp.upperBound() != Double.POSITIVE_INFINITY;
                    return StampFactory.forFloat(JavaKind.Double, -1.0, 1.0, nonNaN);
            }
        }
        return super.foldStamp(newStamp);
    }

    @Override
    public void lower(LoweringTool tool) {
        tool.getLowerer().lower(this, tool);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        Value input = nodeValueMap.operand(getValue());
        Value result;
        switch (getOperation()) {
            case LOG:
                result = gen.emitMathLog(input, false);
                break;
            case LOG10:
                result = gen.emitMathLog(input, true);
                break;
            case EXP:
                result = gen.emitMathExp(input);
                break;
            case SIN:
                result = gen.emitMathSin(input);
                break;
            case COS:
                result = gen.emitMathCos(input);
                break;
            case TAN:
                result = gen.emitMathTan(input);
                break;
            default:
                throw GraalError.shouldNotReachHere();
        }
        nodeValueMap.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(forValue, getOperation());
        if (c != null) {
            return c;
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter UnaryOperation op);

    private static double doCompute(double value, UnaryOperation op) {
        switch (op) {
            case LOG:
                return Math.log(value);
            case LOG10:
                return Math.log10(value);
            case EXP:
                return Math.exp(value);
            case SIN:
                return Math.sin(value);
            case COS:
                return Math.cos(value);
            case TAN:
                return Math.tan(value);
            default:
                throw new GraalError("unknown op %s", op);
        }
    }
}
