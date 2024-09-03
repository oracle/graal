/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.ADD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.AND;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.OR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.SUB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.XOR;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSX;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXB;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp.MOVSXD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64Shift.ROL;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VADDSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VMULSS;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSD;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.VexRVMOp.VSUBSS;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.SS;

import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64RMOp;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.SSEOp;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.calc.FloatConvertCategory;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.core.gen.NodeLIRBuilder;
import jdk.graal.compiler.core.gen.NodeMatchRules;
import jdk.graal.compiler.core.match.ComplexMatchResult;
import jdk.graal.compiler.core.match.MatchRule;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.lir.CastValue;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.LabelRef;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64BinaryConsumer;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64ControlFlow.TestConstBranchOp;
import jdk.graal.compiler.lir.amd64.AMD64UnaryConsumer;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.LeftShiftNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.ReinterpretNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.calc.UnsignedRightShiftNode;
import jdk.graal.compiler.nodes.calc.ZeroExtendNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.java.ValueCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.AddressableMemoryAccess;
import jdk.graal.compiler.nodes.memory.LIRLowerableAccess;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

public class AMD64NodeMatchRules extends NodeMatchRules {
    /*
     * Note that many of the read + action simplifications here may convert an ordered read into a
     * plain read. However, since on AMD64 no fences/barriers are added for ordered reads and also
     * this pattern matching comes after all memory movement, it does not affect program
     * correctness.
     */

    public AMD64NodeMatchRules(LIRGeneratorTool gen) {
        super(gen);
    }

    protected LIRFrameState getState(MemoryAccess access) {
        if (access instanceof DeoptimizingNode) {
            return state((DeoptimizingNode) access);
        }
        return null;
    }

    protected AMD64Kind getMemoryKind(LIRLowerableAccess access) {
        return (AMD64Kind) getLirKind(access).getPlatformKind();
    }

    protected LIRKind getLirKind(LIRLowerableAccess access) {
        return gen.getLIRKind(access.getAccessStamp(NodeView.DEFAULT));
    }

    protected OperandSize getMemorySize(LIRLowerableAccess access) {
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
                throw GraalError.shouldNotReachHere("unsupported memory access type " + getMemoryKind(access)); // ExcludeFromJacocoGeneratedReport
        }
    }

    protected ComplexMatchResult emitCompareBranchMemory(IfNode ifNode, CompareNode compare, ValueNode value, LIRLowerableAccess access) {
        Condition cond = compare.condition().asCondition();
        AMD64Kind kind = getMemoryKind(access);
        boolean matchedAsConstant = false; // For assertion checking

        if (value.isConstant()) {
            JavaConstant constant = value.asJavaConstant();
            if (constant != null) {
                if (kind == AMD64Kind.QWORD && !constant.getJavaKind().isObject() && !NumUtil.isInt(constant.asLong())) {
                    // Only imm32 as long
                    return null;
                }
                // A QWORD that can be encoded as int can be embedded as a constant
                matchedAsConstant = kind == AMD64Kind.QWORD && !constant.getJavaKind().isObject() && NumUtil.isInt(constant.asLong());
            }
            if (kind == AMD64Kind.DWORD) {
                // Any DWORD value should be embeddable as a constant
                matchedAsConstant = true;
            }
            if (kind.isXMM()) {
                ifNode.getDebug().log("Skipping constant compares for float kinds");
                return null;
            }
        }
        boolean matchedAsConstantFinal = matchedAsConstant;

        /*
         * emitCompareBranchMemory expects the memory on the right, so mirror the condition if
         * that's not true. It might be mirrored again the actual compare is emitted but that's ok.
         */
        Condition finalCondition = GraphUtil.unproxify(compare.getX()) == access ? cond.mirror() : cond;
        return new ComplexMatchResult() {
            @Override
            public Value evaluate(NodeLIRBuilder builder) {
                LabelRef trueLabel = getLIRBlock(ifNode.trueSuccessor());
                LabelRef falseLabel = getLIRBlock(ifNode.falseSuccessor());
                boolean unorderedIsTrue = compare.unorderedIsTrue();
                double trueLabelProbability = ifNode.probability(ifNode.trueSuccessor());
                Value other = operand(value);
                /*
                 * Check that patterns which were matched as a constant actually end up seeing a
                 * constant in the LIR.
                 */
                assert !matchedAsConstantFinal || !LIRValueUtil.isVariable(other) : "expected constant value " + value;
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                getLIRGeneratorTool().emitCompareBranchMemory(kind, other, address, getState(access), finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability);
                return null;
            }
        };
    }

    private ComplexMatchResult emitIntegerTestBranchMemory(IfNode x, ValueNode value, LIRLowerableAccess access) {
        LabelRef trueLabel = getLIRBlock(x.trueSuccessor());
        LabelRef falseLabel = getLIRBlock(x.falseSuccessor());
        double trueLabelProbability = x.probability(x.trueSuccessor());
        AMD64Kind kind = getMemoryKind(access);
        OperandSize size = kind == AMD64Kind.QWORD ? QWORD : DWORD;
        if (kind.getVectorLength() > 1) {
            return null;
        }
        if (value.isJavaConstant()) {
            JavaConstant constant = value.asJavaConstant();
            if (kind == AMD64Kind.QWORD && !NumUtil.isInt(constant.asLong())) {
                // Only imm32 as long
                return null;
            }
            return builder -> {
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                gen.append(new TestConstBranchOp(size, address, (int) constant.asLong(), getState(access), Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        } else {
            return builder -> {
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                gen.append(new TestBranchOp(size, gen.asAllocatable(operand(value)), address, getState(access), Condition.EQ, trueLabel, falseLabel, trueLabelProbability));
                return null;
            };
        }
    }

    protected ComplexMatchResult emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, AddressableMemoryAccess access, ValueKind<?> addressKind) {
        return builder -> {
            AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
            LIRFrameState state = getState(access);
            if (addressKind != null) {
                address = address.withKind(addressKind);
            }
            return getArithmeticLIRGenerator().emitConvertMemoryOp(kind, op, size, address, state);
        };
    }

    protected ComplexMatchResult emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, AddressableMemoryAccess access) {
        return emitConvertMemoryOp(kind, op, size, access, null);
    }

    private ComplexMatchResult emitSignExtendMemory(AddressableMemoryAccess access, int fromBits, int toBits, ValueKind<?> addressKind) {
        assert fromBits <= toBits && toBits <= 64 : fromBits + " " + toBits;
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
                    throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
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
                    throw GraalError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)"); // ExcludeFromJacocoGeneratedReport
            }
        }
        if (kind != null && op != null) {
            return emitConvertMemoryOp(kind, op, size, access, addressKind);
        }
        return null;
    }

    private Value emitReinterpretMemory(LIRKind to, AddressableMemoryAccess access) {
        AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
        LIRFrameState state = getState(access);
        return getArithmeticLIRGenerator().emitLoad(to, address, state, MemoryOrderMode.PLAIN, MemoryExtendKind.DEFAULT);
    }

    private boolean supports(CPUFeature feature) {
        return ((AMD64) getLIRGeneratorTool().target().arch).getFeatures().contains(feature);
    }

    @MatchRule("(And (Not a) b)")
    public ComplexMatchResult logicalAndNot(ValueNode a, ValueNode b) {
        if (!supports(CPUFeature.BMI1)) {
            return null;
        }
        return builder -> getArithmeticLIRGenerator().emitLogicalAndNot(operand(a), operand(b));
    }

    @MatchRule("(And a (Negate a))")
    public ComplexMatchResult lowestSetIsolatedBit(ValueNode a) {
        if (!supports(CPUFeature.BMI1)) {
            return null;
        }
        return builder -> getArithmeticLIRGenerator().emitLowestSetIsolatedBit(operand(a));
    }

    @MatchRule("(Xor a (Add a b))")
    public ComplexMatchResult getMaskUpToLowestSetBit(ValueNode a, ValueNode b) {
        if (!supports(CPUFeature.BMI1)) {
            return null;
        }

        // Make sure that the pattern matches a subtraction by one.
        if (!b.isJavaConstant()) {
            return null;
        }

        JavaConstant bCst = b.asJavaConstant();
        long bValue;
        if (bCst.getJavaKind() == JavaKind.Int) {
            bValue = bCst.asInt();
        } else if (bCst.getJavaKind() == JavaKind.Long) {
            bValue = bCst.asLong();
        } else {
            return null;
        }

        if (bValue == -1) {
            return builder -> getArithmeticLIRGenerator().emitGetMaskUpToLowestSetBit(operand(a));
        } else {
            return null;
        }
    }

    @MatchRule("(And a (Add a b))")
    public ComplexMatchResult resetLowestSetBit(ValueNode a, ValueNode b) {
        if (!supports(CPUFeature.BMI1)) {
            return null;
        }
        // Make sure that the pattern matches a subtraction by one.
        if (!b.isJavaConstant()) {
            return null;
        }

        JavaConstant bCst = b.asJavaConstant();
        long bValue;
        if (bCst.getJavaKind() == JavaKind.Int) {
            bValue = bCst.asInt();
        } else if (bCst.getJavaKind() == JavaKind.Long) {
            bValue = bCst.asLong();
        } else {
            return null;
        }

        if (bValue == -1) {
            return builder -> getArithmeticLIRGenerator().emitResetLowestSetBit(operand(a));
        } else {
            return null;
        }
    }

    @MatchRule("(If (IntegerTest value Constant=a))")
    public ComplexMatchResult testBitAndBranch(IfNode root, ValueNode value, ConstantNode a) {
        long constant = a.asJavaConstant().asLong();
        if (Long.bitCount(constant) == 1) {
            return builder -> {
                LabelRef trueDestination = getLIRBlock(root.trueSuccessor());
                LabelRef falseDestination = getLIRBlock(root.falseSuccessor());
                gen.append(new AMD64ControlFlow.BitTestAndBranchOp(trueDestination, falseDestination, gen.asAllocatable(operand(value)),
                                root.getTrueSuccessorProbability(), Long.numberOfTrailingZeros(constant)));
                return null;
            };
        }
        return null;
    }

    @MatchRule("(If (IntegerTest Read=access value))")
    @MatchRule("(If (IntegerTest FloatingRead=access value))")
    public ComplexMatchResult integerTestBranchMemory(IfNode root, LIRLowerableAccess access, ValueNode value) {
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
    @MatchRule("(If (PointerEquals=compare value Read=access))")
    @MatchRule("(If (PointerEquals=compare value FloatingRead=access))")
    @MatchRule("(If (ObjectEquals=compare value Read=access))")
    @MatchRule("(If (ObjectEquals=compare value FloatingRead=access))")
    public ComplexMatchResult ifCompareMemory(IfNode root, CompareNode compare, ValueNode value, LIRLowerableAccess access) {
        return emitCompareBranchMemory(root, compare, value, access);
    }

    @MatchRule("(If (ObjectEquals=compare value ValueCompareAndSwap=cas))")
    @MatchRule("(If (PointerEquals=compare value ValueCompareAndSwap=cas))")
    @MatchRule("(If (FloatEquals=compare value ValueCompareAndSwap=cas))")
    @MatchRule("(If (IntegerEquals=compare value ValueCompareAndSwap=cas))")
    public ComplexMatchResult ifCompareValueCas(IfNode root, CompareNode compare, ValueNode value, ValueCompareAndSwapNode cas) {
        assert compare.condition() == CanonicalCondition.EQ : Assertions.errorMessage(compare, value, cas);
        if (value == cas.getExpectedValue() && cas.hasExactlyOneUsage()) {
            return builder -> {
                LIRKind kind = getLirKind(cas);
                LabelRef trueLabel = getLIRBlock(root.trueSuccessor());
                LabelRef falseLabel = getLIRBlock(root.falseSuccessor());
                double trueLabelProbability = root.probability(root.trueSuccessor());
                Value expectedValue = operand(cas.getExpectedValue());
                Value newValue = operand(cas.getNewValue());
                AMD64AddressValue address = (AMD64AddressValue) operand(cas.getAddress());
                getLIRGeneratorTool().emitCompareAndSwapBranch(false, kind, address, expectedValue, newValue, Condition.EQ, trueLabel, falseLabel, trueLabelProbability, cas.getBarrierType());
                return null;
            };
        }
        return null;
    }

    @MatchRule("(If (ObjectEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (PointerEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (FloatEquals=compare value LogicCompareAndSwap=cas))")
    @MatchRule("(If (IntegerEquals=compare value LogicCompareAndSwap=cas))")
    public ComplexMatchResult ifCompareLogicCas(IfNode root, CompareNode compare, ValueNode value, LogicCompareAndSwapNode cas) {
        JavaConstant constant = value.asJavaConstant();
        assert compare.condition() == CanonicalCondition.EQ : Assertions.errorMessage(root, compare, value, cas);
        if (constant != null && cas.hasExactlyOneUsage()) {
            long constantValue = constant.asLong();
            boolean successIsTrue;
            if (constantValue == 0) {
                successIsTrue = false;
            } else if (constantValue == 1) {
                successIsTrue = true;
            } else {
                return null;
            }
            return builder -> {
                LIRKind kind = getLirKind(cas);
                LabelRef trueLabel = getLIRBlock(root.trueSuccessor());
                LabelRef falseLabel = getLIRBlock(root.falseSuccessor());
                double trueLabelProbability = root.probability(root.trueSuccessor());
                Value expectedValue = operand(cas.getExpectedValue());
                Value newValue = operand(cas.getNewValue());
                AMD64AddressValue address = (AMD64AddressValue) operand(cas.getAddress());
                Condition condition = successIsTrue ? Condition.EQ : Condition.NE;
                getLIRGeneratorTool().emitCompareAndSwapBranch(true, kind, address, expectedValue, newValue, condition, trueLabel, falseLabel, trueLabelProbability, cas.getBarrierType());
                return null;
            };
        }
        return null;
    }

    @MatchRule("(If (ObjectEquals=compare value FloatingRead=access))")
    public ComplexMatchResult ifLogicCas(IfNode root, CompareNode compare, ValueNode value, LIRLowerableAccess access) {
        return emitCompareBranchMemory(root, compare, value, access);
    }

    @MatchRule("(Or (LeftShift=lshift value Constant) (UnsignedRightShift=rshift value Constant))")
    public ComplexMatchResult rotateLeftConstant(LeftShiftNode lshift, UnsignedRightShiftNode rshift) {
        JavaConstant lshiftConst = lshift.getY().asJavaConstant();
        JavaConstant rshiftConst = rshift.getY().asJavaConstant();
        if ((lshift.getShiftAmountMask() & (lshiftConst.asInt() + rshiftConst.asInt())) == 0) {
            return builder -> {
                Value a = operand(lshift.getX());
                OperandSize size = OperandSize.get(a.getPlatformKind());
                assert size == OperandSize.DWORD || size == OperandSize.QWORD : size;
                return getArithmeticLIRGenerator().emitShiftConst(ROL, size, a, lshiftConst);
            };
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

    private ComplexMatchResult binaryRead(AMD64RMOp op, OperandSize size, ValueNode value, LIRLowerableAccess access) {
        return builder -> getArithmeticLIRGenerator().emitBinaryMemory(op, size, getLIRGeneratorTool().asAllocatable(operand(value)), (AMD64AddressValue) operand(access.getAddress()),
                        getState(access));
    }

    private ComplexMatchResult binaryRead(AMD64Assembler.VexRVMOp op, OperandSize size, ValueNode value, LIRLowerableAccess access) {
        assert size == SS || size == SD : size;
        return builder -> getArithmeticLIRGenerator().emitBinaryMemory(op, size, getLIRGeneratorTool().asAllocatable(operand(value)), (AMD64AddressValue) operand(access.getAddress()),
                        getState(access));
    }

    @MatchRule("(Add value Read=access)")
    @MatchRule("(Add value FloatingRead=access)")
    public ComplexMatchResult addMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            if (getArithmeticLIRGenerator().supportAVX()) {
                return binaryRead(size == SS ? VADDSS : VADDSD, size, value, access);
            } else {
                return binaryRead(SSEOp.ADD, size, value, access);
            }
        } else {
            return binaryRead(ADD.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Sub value Read=access)")
    @MatchRule("(Sub value FloatingRead=access)")
    public ComplexMatchResult subMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            if (getArithmeticLIRGenerator().supportAVX()) {
                return binaryRead(size == SS ? VSUBSS : VSUBSD, size, value, access);
            } else {
                return binaryRead(SSEOp.SUB, size, value, access);
            }
        } else {
            return binaryRead(SUB.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Mul value Read=access)")
    @MatchRule("(Mul value FloatingRead=access)")
    public ComplexMatchResult mulMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            if (getArithmeticLIRGenerator().supportAVX()) {
                return binaryRead(size == SS ? VMULSS : VMULSD, size, value, access);
            } else {
                return binaryRead(SSEOp.MUL, size, value, access);
            }
        } else {
            return binaryRead(AMD64RMOp.IMUL, size, value, access);
        }
    }

    @MatchRule("(And value Read=access)")
    @MatchRule("(And value FloatingRead=access)")
    public ComplexMatchResult andMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(AND.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Or value Read=access)")
    @MatchRule("(Or value FloatingRead=access)")
    public ComplexMatchResult orMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(OR.getRMOpcode(size), size, value, access);
        }
    }

    @MatchRule("(Xor value Read=access)")
    @MatchRule("(Xor value FloatingRead=access)")
    public ComplexMatchResult xorMemory(ValueNode value, LIRLowerableAccess access) {
        OperandSize size = getMemorySize(access);
        if (size.isXmmType()) {
            return null;
        } else {
            return binaryRead(XOR.getRMOpcode(size), size, value, access);
        }
    }

    private ComplexMatchResult emitMemoryConsumer(WriteNode write, AMD64Assembler.AMD64BinaryArithmetic arithmeticOp, ReadNode read, ValueNode value) {
        if (getMemoryKind(write).isInteger() && !write.canDeoptimize() && !write.ordersMemoryAccesses() && !read.canDeoptimize()) {
            OperandSize size = getMemorySize(write);
            if (write.getAddress() == read.getAddress()) {
                if (value.isJavaConstant()) {
                    long valueCst = value.asJavaConstant().asLong();
                    if (NumUtil.isInt(valueCst)) {
                        AMD64Assembler.AMD64MOp mop = AMD64ArithmeticLIRGenerator.getMOp(arithmeticOp, size, (int) valueCst);
                        if (mop != null) {
                            return builder -> {
                                AMD64AddressValue addressValue = (AMD64AddressValue) operand(write.getAddress());
                                builder.append(new AMD64UnaryConsumer.MemoryOp(mop, size, addressValue));
                                return null;
                            };
                        } else {
                            return builder -> {
                                AMD64AddressValue addressValue = (AMD64AddressValue) operand(write.getAddress());
                                builder.append(new AMD64BinaryConsumer.MemoryConstOp(arithmeticOp.getMIOpcode(size, NumUtil.isByte(valueCst)), size, addressValue, (int) valueCst, state(write)));
                                return null;
                            };
                        }
                    }
                }
                return builder -> {
                    AMD64AddressValue addressValue = (AMD64AddressValue) operand(write.getAddress());
                    builder.append(new AMD64BinaryConsumer.MemoryMROp(arithmeticOp.getMROpcode(size), size, addressValue, builder.getLIRGeneratorTool().asAllocatable(operand(value)), state(write)));
                    return null;
                };
            }
        }
        return null;
    }

    @MatchRule("(Write=write object (Add Read=read value))")
    @MatchRule("(SideEffectFreeWrite=write object (Add Read=read value))")
    public ComplexMatchResult addToMemory(WriteNode write, ReadNode read, ValueNode value) {
        return emitMemoryConsumer(write, ADD, read, value);
    }

    @MatchRule("(Write=write object (Sub Read=read value))")
    public ComplexMatchResult subToMemory(WriteNode write, ReadNode read, ValueNode value) {
        return emitMemoryConsumer(write, SUB, read, value);
    }

    @MatchRule("(Write=write object (Or Read=read value))")
    public ComplexMatchResult orToMemory(WriteNode write, ReadNode read, ValueNode value) {
        return emitMemoryConsumer(write, OR, read, value);
    }

    @MatchRule("(Write=write object (Xor Read=read value))")
    public ComplexMatchResult xorToMemory(WriteNode write, ReadNode read, ValueNode value) {
        return emitMemoryConsumer(write, XOR, read, value);
    }

    @MatchRule("(Write object Narrow=narrow)")
    public ComplexMatchResult writeNarrow(WriteNode root, NarrowNode narrow) {
        return builder -> {
            LIRKind writeKind = getLIRGeneratorTool().getLIRKind(root.value().stamp(NodeView.DEFAULT));
            Value input = operand(narrow.getValue());
            LIRKind inputKind = LIRKind.combine(input).changeType(writeKind.getPlatformKind());
            Value narrowed = new CastValue(inputKind, getLIRGeneratorTool().asAllocatable(input));
            getArithmeticLIRGenerator().emitStore(writeKind, operand(root.getAddress()), narrowed, state(root), root.getMemoryOrder());
            return null;
        };
    }

    @MatchRule("(SignExtend Read=access)")
    @MatchRule("(SignExtend FloatingRead=access)")
    public ComplexMatchResult signExtend(SignExtendNode root, LIRLowerableAccess access) {
        return emitSignExtendMemory(access, root.getInputBits(), root.getResultBits(), null);
    }

    @MatchRule("(ZeroExtend Read=access)")
    @MatchRule("(ZeroExtend FloatingRead=access)")
    public ComplexMatchResult zeroExtend(ZeroExtendNode root, LIRLowerableAccess access) {
        AMD64Kind memoryKind = getMemoryKind(access);
        return builder -> getArithmeticLIRGenerator().emitZeroExtendMemory(memoryKind, root.getResultBits(), (AMD64AddressValue) operand(access.getAddress()), getState(access));
    }

    @MatchRule("(Narrow Read=access)")
    @MatchRule("(Narrow FloatingRead=access)")
    public ComplexMatchResult narrowRead(NarrowNode root, LIRLowerableAccess access) {
        return new ComplexMatchResult() {
            @Override
            public Value evaluate(NodeLIRBuilder builder) {
                AMD64AddressValue address = (AMD64AddressValue) operand(access.getAddress());
                LIRKind addressKind = LIRKind.combineDerived(getLIRGeneratorTool().getLIRKind(root.asNode().stamp(NodeView.DEFAULT)),
                                address.getBase(), address.getIndex());
                AMD64AddressValue newAddress = address.withKind(addressKind);
                LIRKind readKind = getLIRGeneratorTool().getLIRKind(root.stamp(NodeView.DEFAULT));
                return getArithmeticLIRGenerator().emitZeroExtendMemory((AMD64Kind) readKind.getPlatformKind(),
                                root.getResultBits(), newAddress, getState(access));
            }
        };
    }

    @MatchRule("(SignExtend (Narrow=narrow Read=access))")
    @MatchRule("(SignExtend (Narrow=narrow FloatingRead=access))")
    public ComplexMatchResult signExtendNarrowRead(SignExtendNode root, NarrowNode narrow, LIRLowerableAccess access) {
        LIRKind kind = getLIRGeneratorTool().getLIRKind(narrow.stamp(NodeView.DEFAULT));
        return emitSignExtendMemory(access, narrow.getResultBits(), root.getResultBits(), kind);
    }

    @MatchRule("(FloatConvert Read=access)")
    @MatchRule("(FloatConvert FloatingRead=access)")
    public ComplexMatchResult floatConvert(FloatConvertNode root, LIRLowerableAccess access) {
        if (root.getFloatConvert().getCategory().equals(FloatConvertCategory.FloatingPointToInteger) && (root.inputCanBeNaN() || root.canOverflow())) {
            /* We need to fix up the result of the conversion, the input should be in a register. */
            return null;
        }
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
                throw GraalError.shouldNotReachHereUnexpectedValue(root.getFloatConvert()); // ExcludeFromJacocoGeneratedReport
        }
    }

    @MatchRule("(Reinterpret Read=access)")
    @MatchRule("(Reinterpret FloatingRead=access)")
    public ComplexMatchResult reinterpret(ReinterpretNode root, LIRLowerableAccess access) {
        return builder -> {
            LIRKind kind = getLIRGeneratorTool().getLIRKind(root.stamp(NodeView.DEFAULT));
            return emitReinterpretMemory(kind, access);
        };

    }

    @MatchRule("(Write object Reinterpret=reinterpret)")
    public ComplexMatchResult writeReinterpret(WriteNode root, ReinterpretNode reinterpret) {
        return builder -> {
            LIRKind kind = getLIRGeneratorTool().getLIRKind(reinterpret.getValue().stamp(NodeView.DEFAULT));
            AllocatableValue value = getLIRGeneratorTool().asAllocatable(operand(reinterpret.getValue()));

            AMD64AddressValue address = (AMD64AddressValue) operand(root.getAddress());
            getArithmeticLIRGenerator().emitStore(kind, address, value, getState(root), root.getMemoryOrder());
            return null;
        };
    }

    @MatchRule("(Conditional (IntegerBelow x y) Constant=cm1 (Conditional (IntegerEquals x y) Constant=c0 Constant=c1))")
    public ComplexMatchResult normalizedIntegerCompare(ValueNode x, ValueNode y, ConstantNode cm1, ConstantNode c0, ConstantNode c1) {
        if (cm1.getStackKind() == JavaKind.Int && cm1.asJavaConstant().asInt() == -1 && c0.getStackKind() == JavaKind.Int && c0.asJavaConstant().asInt() == 0 && c1.getStackKind() == JavaKind.Int &&
                        c1.asJavaConstant().asInt() == 1) {
            GraalError.guarantee(PrimitiveStamp.getBits(x.stamp(NodeView.DEFAULT)) == PrimitiveStamp.getBits(y.stamp(NodeView.DEFAULT)), "need compatible inputs: %s, %s", x, y);
            return builder -> {
                LIRKind compareKind = gen.getLIRKind(x.stamp(NodeView.DEFAULT));
                return getArithmeticLIRGenerator().emitNormalizedUnsignedCompare(compareKind, operand(x), operand(y));
            };
        }
        return null;
    }

    @Override
    public AMD64LIRGenerator getLIRGeneratorTool() {
        return (AMD64LIRGenerator) gen;
    }

    protected AMD64ArithmeticLIRGenerator getArithmeticLIRGenerator() {
        return (AMD64ArithmeticLIRGenerator) getLIRGeneratorTool().getArithmetic();
    }
}
