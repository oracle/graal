/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1024;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.BinaryNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SqrtNode;
import jdk.graal.compiler.nodes.spi.ArithmeticLIRLowerable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

@NodeInfo(nameTemplate = "MathIntrinsic#{p#operation/s}", cycles = CYCLES_1024, cyclesRationale = "stub based math intrinsics all have roughly the same high cycle count", size = SIZE_1)
public final class BinaryMathIntrinsicNode extends BinaryNode implements ArithmeticLIRLowerable, Lowerable {

    public static final NodeClass<BinaryMathIntrinsicNode> TYPE = NodeClass.create(BinaryMathIntrinsicNode.class);
    protected final BinaryOperation operation;

    public enum BinaryOperation {
        POW(new ForeignCallSignature("arithmeticPow", double.class, double.class, double.class));

        public final ForeignCallSignature foreignCallSignature;

        BinaryOperation(ForeignCallSignature foreignCallSignature) {
            this.foreignCallSignature = foreignCallSignature;
        }
    }

    public BinaryOperation getOperation() {
        return operation;
    }

    public static ValueNode create(ValueNode forX, ValueNode forY, BinaryOperation op) {
        ValueNode c = tryConstantFold(forX, forY, op);
        if (c != null) {
            return c;
        }
        return new BinaryMathIntrinsicNode(forX, forY, op);
    }

    protected static ValueNode tryConstantFold(ValueNode forX, ValueNode forY, BinaryOperation op) {
        if (forX.isConstant() && forY.isConstant()) {
            double ret = doCompute(forX.asJavaConstant().asDouble(), forY.asJavaConstant().asDouble(), op);
            return ConstantNode.forDouble(ret);
        }
        return null;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY) {
        return stamp(NodeView.DEFAULT);
    }

    protected BinaryMathIntrinsicNode(ValueNode forX, ValueNode forY, BinaryOperation op) {
        super(TYPE, StampFactory.forKind(JavaKind.Double), forX, forY);
        assert forX.stamp(NodeView.DEFAULT) instanceof FloatStamp : Assertions.errorMessageContext("forX", forX);
        assert PrimitiveStamp.getBits(forX.stamp(NodeView.DEFAULT)) == 64 : Assertions.errorMessageContext("forX", forX);
        assert forY.stamp(NodeView.DEFAULT) instanceof FloatStamp : Assertions.errorMessageContext("forY", forY);
        assert PrimitiveStamp.getBits(forY.stamp(NodeView.DEFAULT)) == 64 : Assertions.errorMessageContext("forY", forY);
        this.operation = op;
    }

    @Override
    public void generate(NodeLIRBuilderTool nodeValueMap, ArithmeticLIRGeneratorTool gen) {
        // We can only reach here in the math stubs
        Value xValue = nodeValueMap.operand(getX());
        Value yValue = nodeValueMap.operand(getY());
        Value result;
        switch (getOperation()) {
            case POW:
                result = gen.emitMathPow(xValue, yValue);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(getOperation()); // ExcludeFromJacocoGeneratedReport
        }
        nodeValueMap.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        NodeView view = NodeView.from(tool);
        ValueNode c = tryConstantFold(forX, forY, getOperation());
        if (c != null) {
            return c;
        }

        switch (getOperation()) {
            case POW:
                if (forY.isConstant()) {
                    double yValue = forY.asJavaConstant().asDouble();
                    // If the second argument is positive or negative zero, then the result is 1.0.
                    if (yValue == 0.0D) {
                        return ConstantNode.forDouble(1);
                    }

                    // If the second argument is 1.0, then the result is the same as the first
                    // argument.
                    if (yValue == 1.0D) {
                        return forX;
                    }

                    // If the second argument is NaN, then the result is NaN.
                    if (Double.isNaN(yValue)) {
                        return ConstantNode.forDouble(Double.NaN);
                    }

                    // x**-1 = 1/x
                    if (yValue == -1.0D) {
                        return new FloatDivNode(ConstantNode.forDouble(1), forX);
                    }

                    // x**2 = x*x
                    if (yValue == 2.0D) {
                        return new MulNode(forX, forX);
                    }

                    // x**0.5 = sqrt(x)
                    // Note that Math.pow(Double.MAX_VALUE, 0.5) returns different value than
                    // Math.sqrt(Double.MAX_VALUE) until Java 17.
                    if (yValue == 0.5D && forX.stamp(view) instanceof FloatStamp xStamp && xStamp.lowerBound() >= 0.0D) {
                        return SqrtNode.create(forX, view);
                    }
                }
                break;
            default:
                break;
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double x, double y, @ConstantNodeParameter BinaryOperation op);

    private static double doCompute(double x, double y, BinaryOperation op) {
        switch (op) {
            case POW:
                return Math.pow(x, y);
            default:
                throw new GraalError("unknown op %s", op);
        }
    }

}
