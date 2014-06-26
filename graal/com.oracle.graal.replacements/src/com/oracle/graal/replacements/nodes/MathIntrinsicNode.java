/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

public class MathIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

    private final Operation operation;

    public enum Operation {
        ABS,
        SQRT,
        LOG,
        LOG10,
        SIN,
        COS,
        TAN
    }

    public Operation operation() {
        return operation;
    }

    public MathIntrinsicNode(ValueNode value, Operation op) {
        super(StampFactory.forKind(Kind.Double), value);
        assert value.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp()) == 64;
        this.operation = op;
    }

    @Override
    public void generate(NodeMappableLIRBuilder builder, ArithmeticLIRGenerator gen) {
        Value input = builder.operand(getValue());
        Value result;
        switch (operation()) {
            case ABS:
                result = gen.emitMathAbs(input);
                break;
            case SQRT:
                result = gen.emitMathSqrt(input);
                break;
            case LOG:
                result = gen.emitMathLog(input, false);
                break;
            case LOG10:
                result = gen.emitMathLog(input, true);
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
                throw GraalInternalError.shouldNotReachHere();
        }
        builder.setResult(this, result);
    }

    public Constant evalConst(Constant... inputs) {
        assert inputs.length == 1;
        return Constant.forDouble(doCompute(inputs[0].asDouble(), operation()));
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        if (forValue.isConstant()) {
            return ConstantNode.forPrimitive(evalConst(forValue.asConstant()));
        }
        return this;
    }

    @NodeIntrinsic
    public static double compute(double value, @ConstantNodeParameter Operation op) {
        return doCompute(value, op);
    }

    private static double doCompute(double value, Operation op) {
        switch (op) {
            case ABS:
                return Math.abs(value);
            case SQRT:
                return Math.sqrt(value);
            case LOG:
                return Math.log(value);
            case LOG10:
                return Math.log10(value);
            case SIN:
                return Math.sin(value);
            case COS:
                return Math.cos(value);
            case TAN:
                return Math.tan(value);
            default:
                throw new GraalInternalError("unknown op %s", op);
        }
    }
}
