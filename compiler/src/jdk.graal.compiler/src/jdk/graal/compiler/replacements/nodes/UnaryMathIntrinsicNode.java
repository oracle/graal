/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.nodes;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_64;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.UnaryNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = CYCLES_64, size = SIZE_1)
public final class UnaryMathIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable, Lowerable {

    public static final NodeClass<UnaryMathIntrinsicNode> TYPE = NodeClass.create(UnaryMathIntrinsicNode.class);
    private final UnaryOperation operation;

    public enum UnaryOperation {
        LOG(new ForeignCallSignature("arithmeticLog", double.class, double.class)),
        LOG10(new ForeignCallSignature("arithmeticLog10", double.class, double.class)),
        SIN(new ForeignCallSignature("arithmeticSin", double.class, double.class)),
        SINH(new ForeignCallSignature("arithmeticSinh", double.class, double.class)),
        COS(new ForeignCallSignature("arithmeticCos", double.class, double.class)),
        TAN(new ForeignCallSignature("arithmeticTan", double.class, double.class)),
        TANH(new ForeignCallSignature("arithmeticTanh", double.class, double.class)),
        EXP(new ForeignCallSignature("arithmeticExp", double.class, double.class)),
        CBRT(new ForeignCallSignature("arithmeticCbrt", double.class, double.class));

        public final ForeignCallSignature foreignCallSignature;

        UnaryOperation(ForeignCallSignature foreignCallSignature) {
            this.foreignCallSignature = foreignCallSignature;
        }

        public static double compute(UnaryOperation op, double value) {
            return switch (op) {
                case LOG -> Math.log(value);
                case LOG10 -> Math.log10(value);
                case EXP -> Math.exp(value);
                case SIN -> Math.sin(value);
                case SINH -> Math.sinh(value);
                case COS -> Math.cos(value);
                case TAN -> Math.tan(value);
                case TANH -> Math.tanh(value);
                case CBRT -> Math.cbrt(value);
            };
        }

        public static Stamp computeStamp(UnaryOperation op, Stamp valueStamp) {
            if (valueStamp.isEmpty()) {
                return StampFactory.forKind(JavaKind.Double).empty();
            }
            if (valueStamp instanceof FloatStamp floatStamp) {
                switch (op) {
                    case TANH:
                    case COS:
                    case SIN: {
                        boolean nonNaN = floatStamp.lowerBound() != Double.NEGATIVE_INFINITY && floatStamp.upperBound() != Double.POSITIVE_INFINITY && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, -1.0, 1.0, nonNaN);
                    }
                    case SINH:
                    case TAN: {
                        boolean nonNaN = floatStamp.lowerBound() != Double.NEGATIVE_INFINITY && floatStamp.upperBound() != Double.POSITIVE_INFINITY && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, nonNaN);
                    }
                    case LOG:
                    case LOG10: {
                        double lowerBound = compute(op, floatStamp.lowerBound());
                        double upperBound = compute(op, floatStamp.upperBound());
                        if (floatStamp.contains(0.0)) {
                            // 0.0 and -0.0 infinity produces -Inf
                            lowerBound = Double.NEGATIVE_INFINITY;
                        }
                        boolean nonNaN = floatStamp.lowerBound() >= 0.0 && floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, nonNaN);
                    }
                    case CBRT:
                    case EXP: {
                        double lowerBound = compute(op, floatStamp.lowerBound());
                        double upperBound = compute(op, floatStamp.upperBound());
                        boolean nonNaN = floatStamp.isNonNaN();
                        return StampFactory.forFloat(JavaKind.Double, lowerBound, upperBound, nonNaN);
                    }
                }
            }
            return StampFactory.forKind(JavaKind.Double);
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

    private static ValueNode tryConstantFold(ValueNode value, UnaryOperation op) {
        if (value.isConstant()) {
            return ConstantNode.forDouble(UnaryOperation.compute(op, value.asJavaConstant().asDouble()));
        }
        return null;
    }

    protected UnaryMathIntrinsicNode(ValueNode value, UnaryOperation op) {
        super(TYPE, UnaryOperation.computeStamp(op, value.stamp(NodeView.DEFAULT)), value);
        assert value.stamp(NodeView.DEFAULT) instanceof FloatStamp : Assertions.errorMessageContext("value", value);
        assert PrimitiveStamp.getBits(value.stamp(NodeView.DEFAULT)) == 64 : value;
        this.operation = op;
    }

    @Override
    public Stamp foldStamp(Stamp valueStamp) {
        return UnaryOperation.computeStamp(this.operation, valueStamp);
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        // We can only reach here in the math stubs
        Value input = nodeValueMap.operand(getValue());
        Value result = switch (getOperation()) {
            case LOG -> gen.emitMathLog(input, false);
            case LOG10 -> gen.emitMathLog(input, true);
            case EXP -> gen.emitMathExp(input);
            case SIN -> gen.emitMathSin(input);
            case SINH -> gen.emitMathSinh(input);
            case COS -> gen.emitMathCos(input);
            case TAN -> gen.emitMathTan(input);
            case TANH -> gen.emitMathTanh(input);
            case CBRT -> gen.emitMathCbrt(input);
        };
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

}
