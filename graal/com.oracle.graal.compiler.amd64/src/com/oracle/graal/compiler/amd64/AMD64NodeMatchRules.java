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

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSX;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXB;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.SS;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64Assembler.SSEOp;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.gen.NodeLIRBuilder;
import com.oracle.graal.compiler.gen.NodeMatchRules;
import com.oracle.graal.compiler.match.ComplexMatchResult;
import com.oracle.graal.compiler.match.MatchRule;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.amd64.AMD64AddressValue;
import com.oracle.graal.lir.amd64.AMD64BinaryConsumer;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.gen.LIRGeneratorTool;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.DeoptimizingNode;
import com.oracle.graal.nodes.IfNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.calc.CompareNode;
import com.oracle.graal.nodes.calc.FloatConvertNode;
import com.oracle.graal.nodes.calc.LeftShiftNode;
import com.oracle.graal.nodes.calc.NarrowNode;
import com.oracle.graal.nodes.calc.ReinterpretNode;
import com.oracle.graal.nodes.calc.SignExtendNode;
import com.oracle.graal.nodes.calc.UnsignedRightShiftNode;
import com.oracle.graal.nodes.calc.ZeroExtendNode;
import com.oracle.graal.nodes.extended.UnsafeCastNode;
import com.oracle.graal.nodes.memory.Access;
import com.oracle.graal.nodes.memory.WriteNode;

public class AMD64NodeMatchRules extends NodeMatchRules {

    public AMD64NodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    protected LIRFrameState getState(Access access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected AMD64Kind getMemoryKind(Access access) {
        return (AMD64Kind) gen.getLIRKind(access.asNode().stamp()).getPlatformKind();
    }

    protected OperandSize getMemorySize(Access access) {
        switch (getMemoryKind(access)) {
            case BYTE:
                return OperandSize.BYTE;
            case WORD:
                return OperandSize.WORD;
            case DWORD:
                return OperandSize.DWORD;
            case QWORD:
                return OperandSize.QWORD;
            case SINGLE:
                return OperandSize.SS;
            case DOUBLE:
                return OperandSize.SD;
            default:
                throw JVMCIError.shouldNotReachHere("unsupported memory access type " + getMemoryKind(access));
        }
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
        AMD64Kind kind = getMemoryKind(access);

        if (value.isConstant()) {
            JavaConstant constant = value.asJavaConstant();
            if (kind == AMD64Kind.QWORD && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            if (kind.isXMM()) {
                Debug.log("Skipping constant compares for float kinds");
                return null;
            }
            if (constant.getJavaKind() == JavaKind.Object && !constant.isNull()) {
                Debug.log("Skipping constant compares for Object kinds");
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
                    other = gen.emitJavaConstant(value.asJavaConstant());
                } else {
                    other = operand(value);
                }

                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                getLIRGeneratorTool().emitCompareBranchMemory(kind, other, address, getState(access), finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability);
                return null;
            }
        };
    }

    private ComplexMatchResult emitIntegerTestBranchMemory(IfNode x, ValueNode value, Access access) {
        LabelRef trueLabel = getLIRBlock(x.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(x.falseSuccessor());
        double trueLabelProbability = x.probability(x.trueSuccessor());
        AMD64Kind kind = getMemoryKind(access);
        OperandSize size = kind == AMD64Kind.QWORD ? QWORD : DWORD;
        if (value.isConstant()) {
            JavaConstant constant = value.asJavaConstant();
            if (kind == AMD64Kind.QWORD && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            return builder -> {
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                gen.append(new AMD64BinaryConsumer.MemoryConstOp(AMD64MIOp.TEST, size, address, (int) constant.asLong(), getState(access)));
                gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        } else {
            return builder -> {
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                gen.append(new AMD64BinaryConsumer.MemoryRMOp(AMD64RMOp.TEST, size, gen.asAllocatable(operand(value)), address, getState(access)));
                gen.append(new BranchOp(Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        }
    }

    protected ComplexMatchResult emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, Access access) {
        return builder -> {
            AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
            LIRFrameState state = getState(access);
            return getArithmeticLIRGenerator().emitConvertMemoryOp(kind, op, size, address, state);
        };
    }

    private ComplexMatchResult emitSignExtendMemory(Access access, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        AMD64Kind kind = null;
        AMD64RMOp op;
        OperandSize size;
        if (fromBits == toBits) {
            return null;
        } else if (toBits > 32) {
            kind = AMD64Kind.QWORD;
            size = OperandSize.QWORD;
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
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            kind = AMD64Kind.DWORD;
            size = OperandSize.DWORD;
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
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
        if (kind != null && op != null) {
            return emitConvertMemoryOp(kind, op, size, access);
        }
        return null;
    }

    private Value emitReinterpretMemory(LIRKind to, Access access) {
        AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
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
            return builder -> getArithmeticLIRGenerator().emitRol(operand(lshift.getX()), operand(lshift.getY()));
        }
        return null;
    }

    @MatchRule("(Or (LeftShift value (Sub Constant=delta shiftAmount)) (UnsignedRightShift value shiftAmount))")
    public ComplexMatchResult rotateRightVariable(ValueNode value, ConstantNode delta, ValueNode shiftAmount) {
        if (delta.asJavaConstant().asLong() == 0 || delta.asJavaConstant().asLong() == 32) {
            return builder -> getArithmeticLIRGenerator().emitRor(operand(value), operand(shiftAmount));
        }
        return null;
    }

    @MatchRule("(Or (LeftShift value shiftAmount) (UnsignedRightShift value (Sub Constant=delta shiftAmount)))")
    public ComplexMatchResult rotateLeftVariable(ValueNode value, ValueNode shiftAmount, ConstantNode delta) {
        if (delta.asJavaConstant().asLong() == 0 || delta.asJavaConstant().asLong() == 32) {
            return builder -> getArithmeticLIRGenerator().emitRol(operand(value), operand(shiftAmount));
        }
        return null;
    }

    private ComplexMatchResult binaryRead(AMD64RMOp op, OperandSize size, ValueNode value, Access access) {
        return builder -> getArithmeticLIRGenerator().emitBinaryMemory(op, size, getLIRGeneratorTool().asAllocatable(operand(value)), (AMD64AddressValue) operand(access.getAddress()),
                        getState(access));
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

    @MatchRule("(Write object Narrow=narrow)")
    public ComplexMatchResult writeNarrow(WriteNode root, NarrowNode narrow) {
        return builder -> {
            LIRKind writeKind = getLIRGeneratorTool().getLIRKind(root.value().stamp());
            getLIRGeneratorTool().emitStore(writeKind, operand(root.getAddress()), operand(narrow.getValue()), state(root));
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
        AMD64Kind memoryKind = getMemoryKind(access);
        return builder -> getArithmeticLIRGenerator().emitZeroExtendMemory(memoryKind, root.getResultBits(), (AMD64AddressValue) operand(access.getAddress()), getState(access));
    }

    @MatchRule("(FloatConvert Read=access)")
    @MatchRule("(FloatConvert FloatingRead=access)")
    public ComplexMatchResult floatConvert(FloatConvertNode root, Access access) {
        switch (root.getFloatConvert()) {
            case D2F:
                return emitConvertMemoryOp(AMD64Kind.SINGLE, SSEOp.CVTSD2SS, SD, access);
            case D2I:
                return emitConvertMemoryOp(AMD64Kind.DWORD, SSEOp.CVTTSD2SI, DWORD, access);
            case D2L:
                return emitConvertMemoryOp(AMD64Kind.QWORD, SSEOp.CVTTSD2SI, QWORD, access);
            case F2D:
                return emitConvertMemoryOp(AMD64Kind.DOUBLE, SSEOp.CVTSS2SD, SS, access);
            case F2I:
                return emitConvertMemoryOp(AMD64Kind.DWORD, SSEOp.CVTTSS2SI, DWORD, access);
            case F2L:
                return emitConvertMemoryOp(AMD64Kind.QWORD, SSEOp.CVTTSS2SI, QWORD, access);
            case I2D:
                return emitConvertMemoryOp(AMD64Kind.DOUBLE, SSEOp.CVTSI2SD, DWORD, access);
            case I2F:
                return emitConvertMemoryOp(AMD64Kind.SINGLE, SSEOp.CVTSI2SS, DWORD, access);
            case L2D:
                return emitConvertMemoryOp(AMD64Kind.DOUBLE, SSEOp.CVTSI2SD, QWORD, access);
            case L2F:
                return emitConvertMemoryOp(AMD64Kind.SINGLE, SSEOp.CVTSI2SS, QWORD, access);
            default:
                throw JVMCIError.shouldNotReachHere();
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
    public AMD64LIRGenerator getLIRGeneratorTool() {
        return (AMD64LIRGenerator) gen;
    }

    protected AMD64ArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (AMD64ArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
