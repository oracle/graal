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
package com.oracle.graal.replacements.amd64;

import jdk.internal.jvmci.common.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.spi.*;

@NodeInfo
public final class AMD64MathIntrinsicNode extends UnaryNode implements ArithmeticLIRLowerable {

    public static final NodeClass<AMD64MathIntrinsicNode> TYPE = NodeClass.create(AMD64MathIntrinsicNode.class);
    protected final Operation operation;

    public enum Operation {
        LOG,
        LOG10,
        SIN,
        COS,
        TAN
    }

    public Operation operation() {
        return operation;
    }

    public static ValueNode create(ValueNode value, Operation op) {
        ValueNode c = tryConstantFold(value, op);
        if (c != null) {
            return c;
        }
        return new AMD64MathIntrinsicNode(value, op);
    }

    protected static ValueNode tryConstantFold(ValueNode value, Operation op) {
        if (value.isConstant()) {
            double ret = doCompute(value.asJavaConstant().asDouble(), op);
            return ConstantNode.forDouble(ret);
        }
        return null;
    }

    protected AMD64MathIntrinsicNode(ValueNode value, Operation op) {
        super(TYPE, StampFactory.forKind(Kind.Double), value);
        assert value.stamp() instanceof FloatStamp && PrimitiveStamp.getBits(value.stamp()) == 64;
        this.operation = op;
    }

    @Override
    public void generate(NodeValueMap nodeValueMap, ArithmeticLIRGenerator lirGen) {
        AMD64ArithmeticLIRGenerator gen = (AMD64ArithmeticLIRGenerator) lirGen;
        Value input = nodeValueMap.operand(getValue());
        Value result;
        switch (operation()) {
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
                throw JVMCIError.shouldNotReachHere();
        }
        nodeValueMap.setResult(this, result);
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forValue) {
        ValueNode c = tryConstantFold(forValue, operation());
        if (c != null) {
            return c;
        }
        return this;
    }

    @NodeIntrinsic
    public static native double compute(double value, @ConstantNodeParameter Operation op);

    private static double doCompute(double value, Operation op) {
        switch (op) {
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
                throw new JVMCIError("unknown op %s", op);
        }
    }
}
