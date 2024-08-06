/*
 * Copyright (c) 2009, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.amd64;

import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.IndirectCallTargetNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode.Op;
import jdk.graal.compiler.debug.Assertions;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public abstract class AMD64NodeLIRBuilder extends NodeLIRBuilder {

    public AMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen, AMD64NodeMatchRules nodeMatchRules) {
        super(graph, gen, nodeMatchRules);
    }

    @Override
    protected void emitIndirectCall(IndirectCallTargetNode callTarget, Value result, Value[] parameters, Value[] temps, LIRFrameState callState) {
        Value targetAddressSrc = operand(callTarget.computedAddress());
        AllocatableValue targetAddress = AMD64.rax.asValue(targetAddressSrc.getValueKind());
        gen.emitMove(targetAddress, targetAddressSrc);
        append(new AMD64Call.IndirectCallOp(callTarget.targetMethod(), result, parameters, temps, targetAddress, callState));
    }

    @Override
    protected boolean peephole(ValueNode valueNode) {
        if (valueNode instanceof IntegerDivRemNode) {
            AMD64ArithmeticLIRGenerator arithmeticGen = (AMD64ArithmeticLIRGenerator) gen.getArithmetic();
            IntegerDivRemNode divRem = (IntegerDivRemNode) valueNode;
            FixedNode node = divRem.next();
            while (true) { // TERMINATION ARGUMENT: iterating next nodes
                CompilationAlarm.checkProgress(valueNode.graph());
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
                if (fixedWithNextNode instanceof IntegerDivRemNode) {
                    IntegerDivRemNode otherDivRem = (IntegerDivRemNode) fixedWithNextNode;
                    if (divRem.getOp() != otherDivRem.getOp() && divRem.getType() == otherDivRem.getType()) {
                        if (otherDivRem.getX() == divRem.getX() && otherDivRem.getY() == divRem.getY() && !hasOperand(otherDivRem)) {
                            Value[] results;
                            switch (divRem.getType()) {
                                case SIGNED:
                                    results = arithmeticGen.emitSignedDivRem(operand(divRem.getX()), operand(divRem.getY()), state((DeoptimizingNode) valueNode));
                                    break;
                                case UNSIGNED:
                                    results = arithmeticGen.emitUnsignedDivRem(operand(divRem.getX()), operand(divRem.getY()), state((DeoptimizingNode) valueNode));
                                    break;
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(divRem.getType()); // ExcludeFromJacocoGeneratedReport
                            }
                            switch (divRem.getOp()) {
                                case DIV:
                                    assert otherDivRem.getOp() == Op.REM : Assertions.errorMessage(valueNode, otherDivRem);
                                    setResult(divRem, results[0]);
                                    setResult(otherDivRem, results[1]);
                                    break;
                                case REM:
                                    assert otherDivRem.getOp() == Op.DIV : Assertions.errorMessage(valueNode, otherDivRem);
                                    setResult(divRem, results[1]);
                                    setResult(otherDivRem, results[0]);
                                    break;
                                default:
                                    throw GraalError.shouldNotReachHereUnexpectedValue(divRem.getOp()); // ExcludeFromJacocoGeneratedReport
                            }
                            return true;
                        }
                    }
                }
                node = fixedWithNextNode.next();
            }
        }
        return false;
    }

    @Override
    public AMD64LIRGenerator getLIRGeneratorTool() {
        return (AMD64LIRGenerator) gen;
    }
}
