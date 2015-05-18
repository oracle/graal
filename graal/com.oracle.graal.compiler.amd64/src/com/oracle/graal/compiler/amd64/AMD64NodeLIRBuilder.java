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

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64Assembler.SSEOp;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.compiler.match.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;

public abstract class AMD64NodeLIRBuilder extends NodeLIRBuilder {

    public AMD64NodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool gen) {
        super(graph, gen);
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
                        Value[] results = ((AMD64LIRGenerator) gen).emitIntegerDivRem(operand(divRem.getX()), operand(divRem.getY()), state((DeoptimizingNode) valueNode));
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

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected Kind getMemoryKind(Access access) {
        return (Kind) gen.getLIRKind(access.asNode().stamp()).getPlatformKind();
    }

    protected OperandSize getMemorySize(Access access) {
        switch (getMemoryKind(access)) {
            case Boolean:
            case Byte:
                return OperandSize.BYTE;
            case Char:
            case Short:
                return OperandSize.WORD;
            case Int:
                return OperandSize.DWORD;
            case Long:
                return OperandSize.QWORD;
            case Float:
                return OperandSize.SS;
            case Double:
                return OperandSize.SD;
            default:
                throw GraalInternalError.shouldNotReachHere("unsupported memory access type " + getMemoryKind(access));
        }
    }

    protected AMD64AddressValue makeAddress(Access access) {
        return (AMD64AddressValue) access.accessLocation().generateAddress(this, gen, operand(access.object()));
    }

    protected ValueNode uncast(ValueNode value) {
        if (value instanceof UnsafeCastNode) {
            UnsafeCastNode cast = (UnsafeCastNode) value;
            return cast.getOriginalNode();
        }
        return value;
    }

    protected ComplexMatchResult emitCompareBranchMemory(IfNode ifNode, CompareNode compare, ValueNode value, Access access) {
        Condition cond = compare.condition();
        Kind kind = getMemoryKind(access);

        if (value.isConstant()) {
            JavaConstant constant = value.asJavaConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            if (kind.isNumericFloat()) {
                Debug.log("Skipping constant compares for float kinds");
                return null;
            }
            if (kind == Kind.Object) {
                if (!constant.isNull()) {
                    Debug.log("Skipping constant compares for Object kinds");
                    return null;
                }
            }
        } else {
            if (kind == Kind.Object) {
                // Can't compare against objects since they require encode/decode
                Debug.log("Skipping compares for Object kinds");
                return null;
            }
        }

        // emitCompareBranchMemory expects the memory on the right, so mirror the condition if
        // that's not true. It might be mirrored again the actual compare is emitted but that's
        // ok.
        Condition finalCondition = uncast(compare.getX()) == access ? cond.mirror() : cond;
        return new ComplexMatchResult() {
            public Value evaluate(NodeLIRBuilder builder) {
                LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
                LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
                boolean unorderedIsTrue = compare.unorderedIsTrue();
                double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
                Value other;
                if (value.isConstant()) {
                    other = value.asJavaConstant();
                } else {
                    other = operand(value);
                }

                getLIRGeneratorTool().emitCompareBranchMemory(kind, other, makeAddress(access), getState(access), finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability);
                return null;
            }
        };
    }

    private ComplexMatchResult emitIntegerTestBranchMemory(IfNode x, ValueNode value, Access access) {
        LabelRef trueLabel = getLIRBlock(x.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(x.falseSuccessor());
        double trueLabelProbability = x.probability(x.trueSuccessor());
        Kind kind = getMemoryKind(access);
        OperandSize size = kind == Kind.Long ? QWORD : DWORD;
        if (value.isConstant()) {
            if (kind != kind.getStackKind()) {
                return null;
            }
            JavaConstant constant = value.asJavaConstant();
            if (kind == Kind.Long && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            return builder -> {
                gen.append(new AMD64BinaryConsumer.MemoryConstOp(AMD64MIOp.TEST, size, makeAddress(access), (int) constant.asLong(), getState(access)));
                gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        } else {
            return builder -> {
                gen.append(new AMD64BinaryConsumer.MemoryRMOp(AMD64RMOp.TEST, size, gen.asAllocatable(operand(value)), makeAddress(access), getState(access)));
                gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        }
    }

    protected ComplexMatchResult emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, Access access) {
        return builder -> {
            AMD64AddressValue address = makeAddress(access);
            LIRFrameState state = getState(access);
            return getLIRGeneratorTool().emitConvertMemoryOp(kind, op, size, address, state);
        };
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        Kind kind = null;
        AMD64RMOp op;
        OperandSize size;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            kind = Kind.Long;
            size = QWORD;
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    op = MOVSXB;
                    break;
                case 16:
                    op = MOVSX;
                    break;
                case 32:
                    op = MOVSXD;
                    break;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            kind = Kind.Int;
            size = DWORD;
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    op = MOVSXB;
                    break;
                case 16:
                    op = MOVSX;
                    break;
                case 32:
                    return null;
                default:
                    throw GraalInternalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
        if (kind != null && op != null) {
            return emitConvertMemoryOp(kind, op, size, access);
        }
        return null;
    }

    private Value emitReinterpretMemory(LIRKind to, Access access) {
        AMD64AddressValue address = makeAddress(access);
        LIRFrameState state = getState(access);
        return getLIRGeneratorTool().emitLoad(to, address, state);
    }

    @MatchRule("(If (IntegerTest Read=access value))")
    @MatchRule("(If (IntegerTest FloatingRead=access value))")
    public ComplexMatchResult integerTestBranchMemory(IfNode root, Access access, ValueNode value) {
        return emitIntegerTestBranchMemory(root, value, access);
    }

    @MatchRule("(If (IntegerEquals=compare value Read=access))")
    @MatchRule("(If (IntegerLessThan=compare value Read=access))")
    @MatchRule("(If (IntegerBelow=compare value Read=access))")
    @MatchRule("(If (IntegerEquals=compare value FloatingRead=access))")
    @MatchRule("(If (IntegerLessThan=compare value FloatingRead=access))")
    @MatchRule("(If (IntegerBelow=compare value FloatingRead=access))")
    @MatchRule("(If (FloatEquals=compare value Read=access))")
    @MatchRule("(If (FloatEquals=compare value FloatingRead=access))")
    @MatchRule("(If (FloatLessThan=compare value Read=access))")
    @MatchRule("(If (FloatLessThan=compare value FloatingRead=access))")
    public ComplexMatchResult ifCompareMemory(IfNode root, CompareNode compare, ValueNode value, Access access) {
        return emitCompareBranchMemory(root, compare, value, access);
    }

    @MatchRule("(Or (LeftShift=lshift value Constant) (UnsignedRightShift=rshift value Constant))")
    public ComplexMatchResult rotateLeftConstant(LeftShiftNode lshift, UnsignedRightShiftNode rshift) {
        if ((lshift.getShiftAmountMask() & (lshift.getY().asJavaConstant().asInt() + rshift.getY().asJavaConstant().asInt())) == 0) {
            return builder -> getLIRGeneratorTool().emitRol(operand(lshift.getX()), operand(lshift.getY()));
        }
        return null;
    }

    @MatchRule("(Or (LeftShift value (Sub Constant=delta shiftAmount)) (UnsignedRightShift value shiftAmount))")
    public ComplexMatchResult rotateRightVariable(ValueNode value, ConstantNode delta, ValueNode shiftAmount) {
        if (delta.asJavaConstant().asLong() == 0 || delta.asJavaConstant().asLong() == 32) {
            return builder -> getLIRGeneratorTool().emitRor(operand(value), operand(shiftAmount));
        }
        return null;
    }

    @MatchRule("(Or (LeftShift value shiftAmount) (UnsignedRightShift value (Sub Constant=delta shiftAmount)))")
    public ComplexMatchResult rotateLeftVariable(ValueNode value, ValueNode shiftAmount, ConstantNode delta) {
        if (delta.asJavaConstant().asLong() == 0 || delta.asJavaConstant().asLong() == 32) {
            return builder -> getLIRGeneratorTool().emitRol(operand(value), operand(shiftAmount));
        }
        return null;
    }

    private ComplexMatchResult binaryRead(AMD64RMOp op, OperandSize size, ValueNode value, Access access) {
        return builder -> getLIRGeneratorTool().emitBinaryMemory(op, size, getLIRGeneratorTool().asAllocatable(operand(value)), makeAddress(access), getState(access));
    }

    @MatchRule("(Add value Read=access)")
    @MatchRule("(Add value FloatingRead=access)")
    public ComplexMatchResult addMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryRead(SSEOp.ADD, size, value, access);
        } else {
            return binaryRead(ADD.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Sub value Read=access)")
    @MatchRule("(Sub value FloatingRead=access)")
    public ComplexMatchResult subMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryRead(SSEOp.SUB, size, value, access);
        } else {
            return binaryRead(SUB.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Mul value Read=access)")
    @MatchRule("(Mul value FloatingRead=access)")
    public ComplexMatchResult mulMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return binaryRead(SSEOp.MUL, size, value, access);
        } else {
            return binaryRead(AMD64RMOp.IMUL, size, value, access);
        }
    }

    @MatchRule("(And value Read=access)")
    @MatchRule("(And value FloatingRead=access)")
    public ComplexMatchResult andMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(AND.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Or value Read=access)")
    @MatchRule("(Or value FloatingRead=access)")
    public ComplexMatchResult orMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(OR.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Xor value Read=access)")
    @MatchRule("(Xor value FloatingRead=access)")
    public ComplexMatchResult xorMemory(ValueNode value, Access access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(XOR.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Write object location Narrow=narrow)")
    public ComplexMatchResult writeNarrow(WriteNode root, NarrowNode narrow) {
        return builder -> {
            LIRKind writeKind = getLIRGeneratorTool().getLIRKind(root.value().stamp());
            Value address = root.location().generateAddress(builder, getLIRGeneratorTool(), operand(root.object()));
            Value v = operand(narrow.getValue());
            getLIRGeneratorTool().emitStore(writeKind, address, v, state(root));
            return null;
        };
    }

    @MatchRule("(SignExtend Read=access)")
    @MatchRule("(SignExtend FloatingRead=access)")
    public ComplexMatchResult signExtend(SignExtendNode root, Access access) {
        return emitSignExtendMemory(access, root.getInputBits(), root.getResultBits());
    }

    @MatchRule("(ZeroExtend Read=access)")
    @MatchRule("(ZeroExtend FloatingRead=access)")
    public ComplexMatchResult zeroExtend(ZeroExtendNode root, Access access) {
        Kind memoryKind = getMemoryKind(access);
        if (memoryKind.getBitCount() != root.getInputBits() && !memoryKind.isUnsigned()) {
            /*
             * The memory being read from is signed and smaller than the result size so this is a
             * sign extension to inputBits followed by a zero extension to resultBits which can't be
             * expressed in a memory operation.
             */
            return null;
        }
        return builder -> getLIRGeneratorTool().emitZeroExtendMemory(memoryKind == Kind.Short ? Kind.Char : memoryKind, root.getResultBits(), makeAddress(access), getState(access));
    }

    @MatchRule("(FloatConvert Read=access)")
    @MatchRule("(FloatConvert FloatingRead=access)")
    public ComplexMatchResult floatConvert(FloatConvertNode root, Access access) {
        switch (root.getFloatConvert()) {
            case D2F:
                return emitConvertMemoryOp(Kind.Float, SSEOp.CVTSD2SS, SD, access);
            case D2I:
                return emitConvertMemoryOp(Kind.Int, SSEOp.CVTTSD2SI, DWORD, access);
            case D2L:
                return emitConvertMemoryOp(Kind.Long, SSEOp.CVTTSD2SI, QWORD, access);
            case F2D:
                return emitConvertMemoryOp(Kind.Double, SSEOp.CVTSS2SD, SS, access);
            case F2I:
                return emitConvertMemoryOp(Kind.Int, SSEOp.CVTTSS2SI, DWORD, access);
            case F2L:
                return emitConvertMemoryOp(Kind.Long, SSEOp.CVTTSS2SI, QWORD, access);
            case I2D:
                return emitConvertMemoryOp(Kind.Double, SSEOp.CVTSI2SD, DWORD, access);
            case I2F:
                return emitConvertMemoryOp(Kind.Float, SSEOp.CVTSI2SS, DWORD, access);
            case L2D:
                return emitConvertMemoryOp(Kind.Double, SSEOp.CVTSI2SD, QWORD, access);
            case L2F:
                return emitConvertMemoryOp(Kind.Float, SSEOp.CVTSI2SS, QWORD, access);
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    @MatchRule("(Reinterpret Read=access)")
    @MatchRule("(Reinterpret FloatingRead=access)")
    public ComplexMatchResult reinterpret(ReinterpretNode root, Access access) {
        return builder -> {
            LIRKind kind = getLIRGeneratorTool().getLIRKind(root.stamp());
            return emitReinterpretMemory(kind, access);
        };

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
