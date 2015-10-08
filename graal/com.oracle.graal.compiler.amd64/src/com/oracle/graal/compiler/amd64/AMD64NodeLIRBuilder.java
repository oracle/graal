/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.amd64;

import jdk.internal.jvmci.amd64.AMD64;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.JavaType;
import jdk.internal.jvmci.meta.Value;

import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.amd64.AMD64BreakpointOp;
import com.oracle.graal.lir.amd64.AMD64Call;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.BreakpointNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.FixedWithNextNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.IndirectCallTargetNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.FixedBinaryNode;
import com.oracle.graal.nodes.calc.IntegerDivNode;
import com.oracle.graal.nodes.calc.IntegerRemNode;

public abstract class AMD64NodeLIRBuilder extends NodeLIRBuilder {

    public AMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value targetAddressSrc = operand(callTarget.computedAddress());
        AllocatableValue targetAddress = AMD64.rax.asValue(targetAddressSrc.getLIRKind());
        gen.emitMove(targetAddress, targetAddressSrc);
        append(new AMD64Call.IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, targetAddress, callState));
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        if ((valueNode instanceof IntegerDivNode) || (valueNode instanceof IntegerRemNode)) {
            FixedBinaryNode divRem = (FixedBinaryNode) valueNode;
            FixedNode node = divRem.next();
            while (true) {
                if (node instanceof IfNode) {
                    IfNode ifNode = (IfNode) node;
                    double probability = ifNode.getTrueSuccessorProbability();
                    if (probability == 1.0) {
                        node = ifNode.trueSuccessor();
                    } else if (probability == 0.0) {
                        node = ifNode.falseSuccessor();
                    } else {
                        break;
                    }
                } else if (!(node instanceof FixedWithNextNode)) {
                    break;
                }

                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                if (((fixedWithNextNode instanceof IntegerDivNode) || (fixedWithNextNode instanceof IntegerRemNode)) && fixedWithNextNode.getClass() != divRem.getClass()) {
                    FixedBinaryNode otherDivRem = (FixedBinaryNode) fixedWithNextNode;
                    if (otherDivRem.getX() == divRem.getX() && otherDivRem.getY() == divRem.getY() && !hasOperand(otherDivRem)) {
                        Value[] results = ((AMD64ArithmeticLIRGenerator) gen.getArithmetic()).emitIntegerDivRem(operand(divRem.getX()), operand(divRem.getY()), state((DeoptimizingNode) valueNode));
                        if (divRem instanceof IntegerDivNode) {
                            setResult(divRem, results[0]);
                            setResult(otherDivRem, results[1]);
                        } else {
                            setResult(divRem, results[1]);
                            setResult(otherDivRem, results[0]);
                        }
                        return true;
                    }
                }
                node = fixedWithNextNode.next();
            }
        }
        return false;
    }

    @Override
    public void visitBreakpointNode(BreakpointNode node) {
        JavaType[] sig = new JavaType[node.arguments().size()];
        for (int i = 0; i < sig.length; i++) {
            sig[i] = node.arguments().get(i).stamp().javaType(gen.getMetaAccess());
        }

        Value[] parameters = visitInvokeArguments(gen.getResult().getFrameMapBuilder().getRegisterConfig().getCallingConvention(CallingConvention.Type.JavaCall, null, sig, gen.target(), false),
                        node.arguments());
        append(new AMD64BreakpointOp(parameters));
    }

    @Override
    public AMD64LIRGenerator getLIRGeneratorTool() {
        return (AMD64LIRGenerator) gen;
    }
}
