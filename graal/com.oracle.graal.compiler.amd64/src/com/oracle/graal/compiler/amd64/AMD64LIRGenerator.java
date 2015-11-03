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

import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.CMP;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.DWORD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PD;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.PS;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.QWORD;
import static com.oracle.graal.lir.LIRValueUtil.asConstantValue;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.code.ValueUtil.isAllocatableValue;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.VMConstant;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.asm.NumUtil;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MIOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp;
import com.oracle.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize;
import com.oracle.graal.asm.amd64.AMD64Assembler.SSEOp;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.compiler.common.util.Util;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRValueUtil;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.amd64.AMD64AddressValue;
import com.oracle.graal.lir.amd64.AMD64ArithmeticLIRGeneratorTool;
import com.oracle.graal.lir.amd64.AMD64ArrayEqualsOp;
import com.oracle.graal.lir.amd64.AMD64BinaryConsumer;
import com.oracle.graal.lir.amd64.AMD64ByteSwapOp;
import com.oracle.graal.lir.amd64.AMD64Call;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import com.oracle.graal.lir.amd64.AMD64Move;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaDataOp;
import com.oracle.graal.lir.amd64.AMD64Move.MembarOp;
import com.oracle.graal.lir.amd64.AMD64Move.StackLeaOp;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import com.oracle.graal.phases.util.Providers;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator {

    public AMD64LIRGenerator(LIRKindTool lirKindTool, AMD64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, cc, lirGenRes);
    }

    /**
     * Checks whether the supplied constant can be used without loading it into a register for store
     * operations, i.e., on the right hand side of a memory access.
     *
     * @param c The constant to check.
     * @return True if the constant can be used directly, false if the constant needs to be in a
     *         register.
     */
    protected final boolean canStoreConstant(JavaConstant c) {
        // there is no immediate move of 64-bit constants on Intel
        switch (c.getJavaKind()) {
            case Long:
                return Util.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Double:
                return false;
            case Object:
                return c.isNull();
            default:
                return true;
        }
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((AMD64Kind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case WORD:
                return JavaConstant.forShort((short) dead);
            case DWORD:
                return JavaConstant.forInt((int) dead);
            case QWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            default:
                // we don't support vector types, so just zap with double for all of them
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
        }
    }

    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LeaDataOp(dst, data));
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (NumUtil.isInt(displacement)) {
                    return new AMD64AddressValue(address.getLIRKind(), Value.ILLEGAL, (int) displacement);
                }
            }
            return new AMD64AddressValue(address.getLIRKind(), asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(AllocatableValue stackslot) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLeaOp(result, stackslot));
        return result;
    }

    /**
     * The AMD64 backend only uses DWORD and QWORD values in registers because of a performance
     * penalty when accessing WORD or BYTE registers. This function converts small integer kinds to
     * DWORD.
     */
    @Override
    public LIRKind toRegisterKind(LIRKind kind) {
        switch ((AMD64Kind) kind.getPlatformKind()) {
            case BYTE:
            case WORD:
                return kind.changeType(AMD64Kind.DWORD);
            default:
                return kind;
        }
    }

    @Override
    public Variable emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        LIRKind kind = newValue.getLIRKind();
        assert kind.equals(expectedValue.getLIRKind());
        AMD64Kind memKind = (AMD64Kind) kind.getPlatformKind();

        AMD64AddressValue addressValue = asAddressValue(address);
        RegisterValue raxRes = AMD64.rax.asValue(kind);
        emitMove(raxRes, expectedValue);
        append(new CompareAndSwapOp(memKind, raxRes, addressValue, raxRes, asAllocatable(newValue)));

        assert trueValue.getLIRKind().equals(falseValue.getLIRKind());
        Variable result = newVariable(trueValue.getLIRKind());
        append(new CondMoveOp(result, Condition.EQ, asAllocatable(trueValue), falseValue));
        return result;
    }

    @Override
    public Value emitAtomicReadAndAdd(Value address, Value delta) {
        LIRKind kind = delta.getLIRKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        LIRKind kind = newValue.getLIRKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndWriteOp((AMD64Kind) kind.getPlatformKind(), result, addressValue, asAllocatable(newValue)));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        append(new AMD64Move.NullCheckOp(asAddressValue(address), state));
    }

    @Override
    public void emitJump(LabelRef label) {
        assert label != null;
        append(new JumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel, double trueLabelProbability) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    public void emitCompareBranchMemory(AMD64Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        boolean mirrored = emitCompareMemory(cmpKind, left, right, state);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind.isXMM()) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        append(new BranchOp(ConditionFlag.Overflow, overflow, noOverflow, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(Condition.EQ, trueDestination, falseDestination, trueDestinationProbability));
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        boolean mirrored = emitCompare(cmpKind, left, right);
        Condition finalCondition = mirrored ? cond.mirror() : cond;

        Variable result = newVariable(trueValue.getLIRKind());
        if (cmpKind == AMD64Kind.SINGLE || cmpKind == AMD64Kind.DOUBLE) {
            append(new FloatCondMoveOp(result, finalCondition, unorderedIsTrue, load(trueValue), load(falseValue)));
        } else {
            append(new CondMoveOp(result, finalCondition, load(trueValue), loadNonConst(falseValue)));
        }
        return result;
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getLIRKind());
        append(new CondMoveOp(result, Condition.EQ, load(trueValue), loadNonConst(falseValue)));
        return result;
    }

    private void emitIntegerTest(Value a, Value b) {
        assert ((AMD64Kind) a.getPlatformKind()).isInteger();
        OperandSize size = a.getPlatformKind() == AMD64Kind.QWORD ? QWORD : DWORD;
        if (isJavaConstant(b) && NumUtil.is32bit(asJavaConstant(b).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(a), (int) asJavaConstant(b).asLong()));
        } else if (isJavaConstant(a) && NumUtil.is32bit(asJavaConstant(a).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(b), (int) asJavaConstant(a).asLong()));
        } else if (isAllocatableValue(b)) {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(b), asAllocatable(a)));
        } else {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(a), asAllocatable(b)));
        }
    }

    /**
     * This method emits the compare against memory instruction, and may reorder the operands. It
     * returns true if it did so.
     *
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompareMemory(AMD64Kind cmpKind, Value a, AMD64AddressValue b, LIRFrameState state) {
        OperandSize size;
        switch (cmpKind) {
            case BYTE:
                size = OperandSize.BYTE;
                break;
            case WORD:
                size = OperandSize.WORD;
                break;
            case DWORD:
                size = OperandSize.DWORD;
                break;
            case QWORD:
                size = OperandSize.QWORD;
                break;
            case SINGLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(a), b, state));
                return false;
            case DOUBLE:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(a), b, state));
                return false;
            default:
                throw JVMCIError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isJavaConstant(a)) {
            return emitCompareMemoryConOp(size, asConstantValue(a), b, state);
        } else {
            return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
        }
    }

    protected boolean emitCompareMemoryConOp(OperandSize size, ConstantValue a, AMD64AddressValue b, LIRFrameState state) {
        if (JavaConstant.isNull(a.getConstant())) {
            append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, 0, state));
            return true;
        } else if (a.getConstant() instanceof VMConstant && size == DWORD) {
            VMConstant vc = (VMConstant) a.getConstant();
            append(new AMD64BinaryConsumer.MemoryVMConstOp(CMP.getMIOpcode(size, false), b, vc, state));
            return true;
        } else {
            long value = a.getJavaConstant().asLong();
            if (NumUtil.is32bit(value)) {
                append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, (int) value, state));
                return true;
            } else {
                return emitCompareRegMemoryOp(size, asAllocatable(a), b, state);
            }
        }
    }

    private boolean emitCompareRegMemoryOp(OperandSize size, AllocatableValue a, AMD64AddressValue b, LIRFrameState state) {
        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.MemoryRMOp(op, size, a, b, state));
        return false;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompare(PlatformKind cmpKind, Value a, Value b) {
        Variable left;
        Value right;
        boolean mirrored;
        if (LIRValueUtil.isVariable(b)) {
            left = load(b);
            right = loadNonConst(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadNonConst(b);
            mirrored = false;
        }
        ((AMD64ArithmeticLIRGeneratorTool) arithmeticLIRGen).emitCompareOp((AMD64Kind) cmpKind, left, right);
        return mirrored;
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    public abstract void emitCCall(long address, CallingConvention nativeCallingConvention, Value[] args, int numberOfFloatingPointArguments);

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (maxOffset != (int) maxOffset) {
            append(new AMD64Call.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new AMD64Call.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new AMD64ByteSwapOp(result, input));
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(AMD64Kind.DWORD));
        append(new AMD64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
        return result;
    }

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getLIRKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    protected StrategySwitchOp createStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue temp) {
        return new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, temp);
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = !key.getLIRKind().isValue();
        append(createStrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getLIRKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(LIRKind.value(target().arch.getWordKind())), newVariable(key.getLIRKind())));
    }
}
