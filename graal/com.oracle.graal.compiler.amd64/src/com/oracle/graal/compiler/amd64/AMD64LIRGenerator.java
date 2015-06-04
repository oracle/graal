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

import com.oracle.jvmci.amd64.*;
import com.oracle.jvmci.code.RegisterConfig;
import com.oracle.jvmci.code.Register;
import com.oracle.jvmci.code.ForeignCallLinkage;
import com.oracle.jvmci.code.CodeUtil;
import com.oracle.jvmci.code.Architecture;
import com.oracle.jvmci.code.StackSlotValue;
import com.oracle.jvmci.code.CallingConvention;
import com.oracle.jvmci.code.RegisterValue;
import com.oracle.jvmci.code.VirtualStackSlot;
import com.oracle.jvmci.meta.Kind;
import com.oracle.jvmci.meta.JavaConstant;
import com.oracle.jvmci.meta.PlatformKind;
import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.AllocatableValue;
import com.oracle.jvmci.meta.LIRKind;

import static com.oracle.jvmci.code.ValueUtil.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64BinaryArithmetic.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64MOp.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64RMOp.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.AMD64Shift.*;
import static com.oracle.graal.asm.amd64.AMD64Assembler.OperandSize.*;
import static com.oracle.graal.lir.amd64.AMD64Arithmetic.*;
import static com.oracle.graal.lir.amd64.AMD64MathIntrinsicOp.IntrinsicOpcode.*;

import java.util.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.AMD64Address.*;
import com.oracle.graal.asm.amd64.AMD64Assembler.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.spi.*;
import com.oracle.graal.compiler.common.util.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.JumpOp;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Arithmetic.FPDivRemOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.CondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatBranchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.FloatCondMoveOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.ReturnOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.TableSwitchOp;
import com.oracle.graal.lir.amd64.AMD64Move.AMD64StackMove;
import com.oracle.graal.lir.amd64.AMD64Move.CompareAndSwapOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaDataOp;
import com.oracle.graal.lir.amd64.AMD64Move.LeaOp;
import com.oracle.graal.lir.amd64.AMD64Move.MembarOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveFromRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.MoveToRegOp;
import com.oracle.graal.lir.amd64.AMD64Move.StackLeaOp;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.phases.util.*;
import com.oracle.jvmci.common.*;

/**
 * This class implements the AMD64 specific portion of the LIR generator.
 */
public abstract class AMD64LIRGenerator extends LIRGenerator implements AMD64ArithmeticLIRGenerator {

    private static final RegisterValue RCX_I = AMD64.rcx.asValue(LIRKind.value(Kind.Int));
    private AMD64SpillMoveFactory moveFactory;
    private Map<PlatformKind.Key, RegisterBackupPair> categorized;

    private static class RegisterBackupPair {
        public final Register register;
        public final StackSlotValue backupSlot;

        RegisterBackupPair(Register register, StackSlotValue backupSlot) {
            this.register = register;
            this.backupSlot = backupSlot;
        }
    }

    private class AMD64SpillMoveFactory implements LIRGeneratorTool.SpillMoveFactory {

        @Override
        public LIRInstruction createMove(AllocatableValue result, Value input) {
            return AMD64LIRGenerator.this.createMove(result, input);
        }

        @Override
        public LIRInstruction createStackMove(AllocatableValue result, Value input) {
            return AMD64LIRGenerator.this.createStackMove(result, input);
        }

    }

    public AMD64LIRGenerator(LIRKindTool lirKindTool, Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(lirKindTool, providers, cc, lirGenRes);
    }

    public SpillMoveFactory getSpillMoveFactory() {
        if (moveFactory == null) {
            moveFactory = new AMD64SpillMoveFactory();
        }
        return moveFactory;
    }

    @Override
    public boolean canInlineConstant(JavaConstant c) {
        switch (c.getKind()) {
            case Long:
                return NumUtil.isInt(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return true;
        }
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
        switch (c.getKind()) {
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

    protected AMD64LIRInstruction createMove(AllocatableValue dst, Value src) {
        if (src instanceof AMD64AddressValue) {
            return new LeaOp(dst, (AMD64AddressValue) src);
        } else if (isRegister(src) || isStackSlotValue(dst)) {
            return new MoveFromRegOp(dst.getKind(), dst, src);
        } else {
            return new MoveToRegOp(dst.getKind(), dst, src);
        }
    }

    protected LIRInstruction createStackMove(AllocatableValue result, Value input) {
        RegisterBackupPair backup = getScratchRegister(input.getPlatformKind());
        Register scratchRegister = backup.register;
        StackSlotValue backupSlot = backup.backupSlot;
        return createStackMove(result, input, scratchRegister, backupSlot);
    }

    protected LIRInstruction createStackMove(AllocatableValue result, Value input, Register scratchRegister, StackSlotValue backupSlot) {
        return new AMD64StackMove(result, input, scratchRegister, backupSlot);
    }

    protected RegisterBackupPair getScratchRegister(PlatformKind kind) {
        PlatformKind.Key key = kind.getKey();
        if (categorized == null) {
            categorized = new HashMap<>();
        } else if (categorized.containsKey(key)) {
            return categorized.get(key);
        }

        FrameMapBuilder frameMapBuilder = getResult().getFrameMapBuilder();
        RegisterConfig registerConfig = frameMapBuilder.getRegisterConfig();

        Register[] availableRegister = registerConfig.filterAllocatableRegisters(kind, registerConfig.getAllocatableRegisters());
        assert availableRegister != null && availableRegister.length > 1;
        Register scratchRegister = availableRegister[0];

        Architecture arch = frameMapBuilder.getCodeCache().getTarget().arch;
        LIRKind largestKind = LIRKind.value(arch.getLargestStorableKind(scratchRegister.getRegisterCategory()));
        VirtualStackSlot backupSlot = frameMapBuilder.allocateSpillSlot(largestKind);

        RegisterBackupPair value = new RegisterBackupPair(scratchRegister, backupSlot);
        categorized.put(key, value);

        return value;
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(createMove(dst, src));
    }

    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LeaDataOp(dst, data));
    }

    @Override
    public AMD64AddressValue emitAddress(Value base, long displacement, Value index, int scale) {
        AllocatableValue baseRegister;
        long finalDisp = displacement;
        if (isConstant(base)) {
            if (asConstant(base).isNull()) {
                baseRegister = Value.ILLEGAL;
            } else if (asConstant(base).getKind() != Kind.Object && !getCodeCache().needsDataPatch(asConstant(base))) {
                finalDisp += asConstant(base).asLong();
                baseRegister = Value.ILLEGAL;
            } else {
                baseRegister = load(base);
            }
        } else {
            baseRegister = asAllocatable(base);
        }

        AllocatableValue indexRegister;
        Scale scaleEnum;
        if (!index.equals(Value.ILLEGAL) && scale != 0) {
            scaleEnum = Scale.fromInt(scale);
            if (isConstant(index)) {
                finalDisp += asConstant(index).asLong() * scale;
                indexRegister = Value.ILLEGAL;

            } else if (scaleEnum == null) {
                /* Scale value that architecture cannot handle, so scale manually. */
                Value longIndex = index.getKind() == Kind.Long ? index : emitSignExtend(index, 32, 64);
                if (CodeUtil.isPowerOf2(scale)) {
                    indexRegister = emitShl(longIndex, JavaConstant.forLong(CodeUtil.log2(scale)));
                } else {
                    indexRegister = emitMul(longIndex, JavaConstant.forLong(scale), false);
                }
                scaleEnum = Scale.Times1;

            } else {
                indexRegister = asAllocatable(index);
            }
        } else {
            indexRegister = Value.ILLEGAL;
            scaleEnum = Scale.Times1;
        }

        int displacementInt;
        if (NumUtil.isInt(finalDisp)) {
            displacementInt = (int) finalDisp;
        } else {
            displacementInt = 0;
            AllocatableValue displacementRegister = load(JavaConstant.forLong(finalDisp));
            if (baseRegister.equals(Value.ILLEGAL)) {
                baseRegister = displacementRegister;
            } else if (indexRegister.equals(Value.ILLEGAL)) {
                indexRegister = displacementRegister;
                scaleEnum = Scale.Times1;
            } else {
                baseRegister = emitAdd(baseRegister, displacementRegister, false);
            }
        }

        LIRKind resultKind = getAddressKind(base, displacement, index);
        return new AMD64AddressValue(resultKind, baseRegister, indexRegister, scaleEnum, displacementInt);
    }

    public AMD64AddressValue asAddressValue(Value address) {
        if (address instanceof AMD64AddressValue) {
            return (AMD64AddressValue) address;
        } else {
            return emitAddress(address, 0, Value.ILLEGAL, 0);
        }
    }

    @Override
    public Variable emitAddress(StackSlotValue address) {
        Variable result = newVariable(LIRKind.value(target().wordKind));
        append(new StackLeaOp(result, address));
        return result;
    }

    private static LIRKind toStackKind(LIRKind kind) {
        if (kind.getPlatformKind() instanceof Kind) {
            Kind stackKind = ((Kind) kind.getPlatformKind()).getStackKind();
            return kind.changeType(stackKind);
        } else {
            return kind;
        }
    }

    @Override
    public Variable emitLoad(LIRKind kind, Value address, LIRFrameState state) {
        AMD64AddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(toStackKind(kind));
        switch ((Kind) kind.getPlatformKind()) {
            case Boolean:
                append(new AMD64Unary.MemoryOp(MOVZXB, DWORD, result, loadAddress, state));
                break;
            case Byte:
                append(new AMD64Unary.MemoryOp(MOVSXB, DWORD, result, loadAddress, state));
                break;
            case Char:
                append(new AMD64Unary.MemoryOp(MOVZX, DWORD, result, loadAddress, state));
                break;
            case Short:
                append(new AMD64Unary.MemoryOp(MOVSX, DWORD, result, loadAddress, state));
                break;
            case Int:
                append(new AMD64Unary.MemoryOp(MOV, DWORD, result, loadAddress, state));
                break;
            case Long:
            case Object:
                append(new AMD64Unary.MemoryOp(MOV, QWORD, result, loadAddress, state));
                break;
            case Float:
                append(new AMD64Unary.MemoryOp(MOVSS, SS, result, loadAddress, state));
                break;
            case Double:
                append(new AMD64Unary.MemoryOp(MOVSD, SD, result, loadAddress, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    protected void emitStoreConst(Kind kind, AMD64AddressValue address, JavaConstant value, LIRFrameState state) {
        if (value.isNull()) {
            assert kind == Kind.Int || kind == Kind.Long || kind == Kind.Object;
            OperandSize size = kind == Kind.Int ? DWORD : QWORD;
            append(new AMD64BinaryConsumer.MemoryConstOp(AMD64MIOp.MOV, size, address, 0, state));
        } else {
            AMD64MIOp op = AMD64MIOp.MOV;
            OperandSize size;
            long imm;

            switch (kind) {
                case Boolean:
                case Byte:
                    op = AMD64MIOp.MOVB;
                    size = BYTE;
                    imm = value.asInt();
                    break;
                case Char:
                case Short:
                    size = WORD;
                    imm = value.asInt();
                    break;
                case Int:
                    size = DWORD;
                    imm = value.asInt();
                    break;
                case Long:
                    size = QWORD;
                    imm = value.asLong();
                    break;
                case Float:
                    size = DWORD;
                    imm = Float.floatToRawIntBits(value.asFloat());
                    break;
                case Double:
                    size = QWORD;
                    imm = Double.doubleToRawLongBits(value.asDouble());
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("unexpected kind " + kind);
            }

            if (NumUtil.isInt(imm)) {
                append(new AMD64BinaryConsumer.MemoryConstOp(op, size, address, (int) imm, state));
            } else {
                emitStore(kind, address, asAllocatable(value), state);
            }
        }
    }

    protected void emitStore(Kind kind, AMD64AddressValue address, AllocatableValue value, LIRFrameState state) {
        switch (kind) {
            case Boolean:
            case Byte:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVB, BYTE, address, value, state));
                break;
            case Char:
            case Short:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, WORD, address, value, state));
                break;
            case Int:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, DWORD, address, value, state));
                break;
            case Long:
            case Object:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOV, QWORD, address, value, state));
                break;
            case Float:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVSS, SS, address, value, state));
                break;
            case Double:
                append(new AMD64BinaryConsumer.MemoryMROp(AMD64MROp.MOVSD, SD, address, value, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public void emitStore(LIRKind lirKind, Value address, Value input, LIRFrameState state) {
        AMD64AddressValue storeAddress = asAddressValue(address);
        Kind kind = (Kind) lirKind.getPlatformKind();
        if (isConstant(input)) {
            emitStoreConst(kind, storeAddress, asConstant(input), state);
        } else {
            emitStore(kind, storeAddress, asAllocatable(input), state);
        }
    }

    @Override
    public Variable emitCompareAndSwap(Value address, Value expectedValue, Value newValue, Value trueValue, Value falseValue) {
        LIRKind kind = newValue.getLIRKind();
        assert kind.equals(expectedValue.getLIRKind());
        Kind memKind = (Kind) kind.getPlatformKind();

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
        Kind memKind = (Kind) kind.getPlatformKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndAddOp(memKind, result, addressValue, asAllocatable(delta)));
        return result;
    }

    @Override
    public Value emitAtomicReadAndWrite(Value address, Value newValue) {
        LIRKind kind = newValue.getLIRKind();
        Kind memKind = (Kind) kind.getPlatformKind();
        Variable result = newVariable(kind);
        AMD64AddressValue addressValue = asAddressValue(address);
        append(new AMD64Move.AtomicReadAndWriteOp(memKind, result, addressValue, asAllocatable(newValue)));
        return result;
    }

    @Override
    public void emitNullCheck(Value address, LIRFrameState state) {
        assert address.getKind() == Kind.Object || address.getKind() == Kind.Long : address + " - " + address.getKind() + " not a pointer!";
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
        if (cmpKind == Kind.Float || cmpKind == Kind.Double) {
            append(new FloatBranchOp(finalCondition, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability));
        } else {
            append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
        }
    }

    public void emitCompareBranchMemory(Kind cmpKind, Value left, AMD64AddressValue right, LIRFrameState state, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        boolean mirrored = emitCompareMemory(cmpKind, left, right, state);
        Condition finalCondition = mirrored ? cond.mirror() : cond;
        if (cmpKind == Kind.Float || cmpKind == Kind.Double) {
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
        if (cmpKind == Kind.Float || cmpKind == Kind.Double) {
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
        assert a.getKind().isNumericInteger();
        OperandSize size = a.getKind() == Kind.Long ? QWORD : DWORD;
        if (isConstant(b) && NumUtil.is32bit(asConstant(b).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(a), (int) asConstant(b).asLong()));
        } else if (isConstant(a) && NumUtil.is32bit(asConstant(a).asLong())) {
            append(new AMD64BinaryConsumer.ConstOp(AMD64MIOp.TEST, size, asAllocatable(b), (int) asConstant(a).asLong()));
        } else if (isAllocatableValue(b)) {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(b), asAllocatable(a)));
        } else {
            append(new AMD64BinaryConsumer.Op(AMD64RMOp.TEST, size, asAllocatable(a), asAllocatable(b)));
        }
    }

    protected void emitCompareOp(PlatformKind cmpKind, Variable left, Value right) {
        OperandSize size;
        switch ((Kind) cmpKind) {
            case Byte:
            case Boolean:
                size = BYTE;
                break;
            case Short:
            case Char:
                size = WORD;
                break;
            case Int:
                size = DWORD;
                break;
            case Long:
            case Object:
                size = QWORD;
                break;
            case Float:
                append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PS, left, asAllocatable(right)));
                return;
            case Double:
                append(new AMD64BinaryConsumer.Op(SSEOp.UCOMIS, PD, left, asAllocatable(right)));
                return;
            default:
                throw JVMCIError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isConstant(right)) {
            JavaConstant c = asConstant(right);
            if (c.isDefaultForKind()) {
                AMD64RMOp op = size == BYTE ? TESTB : TEST;
                append(new AMD64BinaryConsumer.Op(op, size, left, left));
                return;
            } else if (NumUtil.is32bit(c.asLong())) {
                append(new AMD64BinaryConsumer.ConstOp(CMP, size, left, (int) c.asLong()));
                return;
            }
        }

        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.Op(op, size, left, asAllocatable(right)));
    }

    /**
     * This method emits the compare against memory instruction, and may reorder the operands. It
     * returns true if it did so.
     *
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    private boolean emitCompareMemory(Kind cmpKind, Value a, AMD64AddressValue b, LIRFrameState state) {
        OperandSize size;
        switch (cmpKind) {
            case Byte:
            case Boolean:
                size = BYTE;
                break;
            case Short:
            case Char:
                size = WORD;
                break;
            case Int:
                size = DWORD;
                break;
            case Long:
            case Object:
                size = QWORD;
                break;
            case Float:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PS, asAllocatable(a), b, state));
                return false;
            case Double:
                append(new AMD64BinaryConsumer.MemoryRMOp(SSEOp.UCOMIS, PD, asAllocatable(a), b, state));
                return false;
            default:
                throw JVMCIError.shouldNotReachHere("unexpected kind: " + cmpKind);
        }

        if (isConstant(a)) {
            return emitCompareMemoryConOp(size, asConstant(a), b, state);
        } else {
            return emitCompareRegMemoryOp(size, a, b, state);
        }
    }

    protected boolean emitCompareMemoryConOp(OperandSize size, JavaConstant a, AMD64AddressValue b, LIRFrameState state) {
        if (NumUtil.is32bit(a.asLong())) {
            append(new AMD64BinaryConsumer.MemoryConstOp(CMP, size, b, (int) a.asLong(), state));
            return true;
        } else {
            return emitCompareRegMemoryOp(size, a, b, state);
        }
    }

    private boolean emitCompareRegMemoryOp(OperandSize size, Value a, AMD64AddressValue b, LIRFrameState state) {
        AMD64RMOp op = CMP.getRMOpcode(size);
        append(new AMD64BinaryConsumer.MemoryRMOp(op, size, asAllocatable(a), b, state));
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
        emitCompareOp(cmpKind, left, right);
        return mirrored;
    }

    @Override
    public Variable emitNegate(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind()) {
            case Int:
                append(new AMD64Unary.MOp(NEG, DWORD, result, input));
                break;
            case Long:
                append(new AMD64Unary.MOp(NEG, QWORD, result, input));
                break;
            case Float:
                append(new AMD64Binary.DataOp(SSEOp.XOR, PS, result, input, JavaConstant.forFloat(Float.intBitsToFloat(0x80000000)), 16));
                break;
            case Double:
                append(new AMD64Binary.DataOp(SSEOp.XOR, PD, result, input, JavaConstant.forDouble(Double.longBitsToDouble(0x8000000000000000L)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Variable emitNot(Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind()) {
            case Int:
                append(new AMD64Unary.MOp(NOT, DWORD, result, input));
                break;
            case Long:
                append(new AMD64Unary.MOp(NOT, QWORD, result, input));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private Variable emitBinary(AMD64BinaryArithmetic op, OperandSize size, boolean commutative, Value a, Value b, boolean setFlags) {
        if (isConstant(b)) {
            return emitBinaryConst(op, size, commutative, asAllocatable(a), asConstant(b), setFlags);
        } else if (commutative && isConstant(a)) {
            return emitBinaryConst(op, size, commutative, asAllocatable(b), asConstant(a), setFlags);
        } else {
            return emitBinaryVar(op.getRMOpcode(size), size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinary(AMD64RMOp op, OperandSize size, boolean commutative, Value a, Value b) {
        if (isConstant(b)) {
            return emitBinaryConst(op, size, asAllocatable(a), asConstant(b));
        } else if (commutative && isConstant(a)) {
            return emitBinaryConst(op, size, asAllocatable(b), asConstant(a));
        } else {
            return emitBinaryVar(op, size, commutative, asAllocatable(a), asAllocatable(b));
        }
    }

    private Variable emitBinaryConst(AMD64BinaryArithmetic op, OperandSize size, boolean commutative, AllocatableValue a, JavaConstant b, boolean setFlags) {
        if (NumUtil.isInt(b.asLong())) {
            Variable result = newVariable(LIRKind.derive(a, b));
            int constant = (int) b.asLong();

            if (!setFlags) {
                AMD64MOp mop = getMOp(op, constant);
                if (mop != null) {
                    append(new AMD64Unary.MOp(mop, size, result, a));
                    return result;
                }
            }

            append(new AMD64Binary.ConstOp(op, size, result, a, constant));
            return result;
        } else {
            return emitBinaryVar(op.getRMOpcode(size), size, commutative, a, asAllocatable(b));
        }
    }

    private static AMD64MOp getMOp(AMD64BinaryArithmetic op, int constant) {
        if (constant == 1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.INC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.DEC;
            }
        } else if (constant == -1) {
            if (op.equals(AMD64BinaryArithmetic.ADD)) {
                return AMD64MOp.DEC;
            }
            if (op.equals(AMD64BinaryArithmetic.SUB)) {
                return AMD64MOp.INC;
            }
        }
        return null;
    }

    private Variable emitBinaryConst(AMD64RMOp op, OperandSize size, AllocatableValue a, JavaConstant b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        append(new AMD64Binary.DataOp(op, size, result, a, b));
        return result;
    }

    private Variable emitBinaryVar(AMD64RMOp op, OperandSize size, boolean commutative, AllocatableValue a, AllocatableValue b) {
        Variable result = newVariable(LIRKind.derive(a, b));
        if (commutative) {
            append(new AMD64Binary.CommutativeOp(op, size, result, a, b));
        } else {
            append(new AMD64Binary.Op(op, size, result, a, b));
        }
        return result;
    }

    @Override
    public Variable emitAdd(Value a, Value b, boolean setFlags) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(ADD, DWORD, true, a, b, setFlags);
            case Long:
                return emitBinary(ADD, QWORD, true, a, b, setFlags);
            case Float:
                return emitBinary(SSEOp.ADD, SS, true, a, b);
            case Double:
                return emitBinary(SSEOp.ADD, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitSub(Value a, Value b, boolean setFlags) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(SUB, DWORD, false, a, b, setFlags);
            case Long:
                return emitBinary(SUB, QWORD, false, a, b, setFlags);
            case Float:
                return emitBinary(SSEOp.SUB, SS, false, a, b);
            case Double:
                return emitBinary(SSEOp.SUB, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Variable emitIMULConst(OperandSize size, AllocatableValue a, JavaConstant b) {
        if (NumUtil.isInt(b.asLong())) {
            int imm = (int) b.asLong();
            AMD64RMIOp op;
            if (NumUtil.isByte(imm)) {
                op = AMD64RMIOp.IMUL_SX;
            } else {
                op = AMD64RMIOp.IMUL;
            }

            Variable ret = newVariable(LIRKind.derive(a, b));
            append(new AMD64Binary.RMIOp(op, size, ret, a, imm));
            return ret;
        } else {
            return emitBinaryVar(AMD64RMOp.IMUL, size, true, a, asAllocatable(b));
        }
    }

    private Variable emitIMUL(OperandSize size, Value a, Value b) {
        if (isConstant(b)) {
            return emitIMULConst(size, asAllocatable(a), asConstant(b));
        } else if (isConstant(a)) {
            return emitIMULConst(size, asAllocatable(b), asConstant(a));
        } else {
            return emitBinaryVar(AMD64RMOp.IMUL, size, true, asAllocatable(a), asAllocatable(b));
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitIMUL(DWORD, a, b);
            case Long:
                return emitIMUL(QWORD, a, b);
            case Float:
                return emitBinary(SSEOp.MUL, SS, true, a, b);
            case Double:
                return emitBinary(SSEOp.MUL, SD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private RegisterValue moveToReg(Register reg, Value v) {
        RegisterValue ret = reg.asValue(v.getLIRKind());
        emitMove(ret, v);
        return ret;
    }

    private Value emitMulHigh(AMD64MOp opcode, OperandSize size, Value a, Value b) {
        AMD64MulDivOp mulHigh = append(new AMD64MulDivOp(opcode, size, LIRKind.derive(a, b), moveToReg(AMD64.rax, a), asAllocatable(b)));
        return emitMove(mulHigh.getHighResult());
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitMulHigh(AMD64MOp.IMUL, DWORD, a, b);
            case Long:
                return emitMulHigh(AMD64MOp.IMUL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitMulHigh(AMD64MOp.MUL, DWORD, a, b);
            case Long:
                return emitMulHigh(AMD64MOp.MUL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Value emitBinaryMemory(AMD64RMOp op, OperandSize size, AllocatableValue a, AMD64AddressValue location, LIRFrameState state) {
        Variable result = newVariable(LIRKind.derive(a));
        append(new AMD64Binary.MemoryOp(op, size, result, a, location, state));
        return result;
    }

    protected Value emitConvertMemoryOp(PlatformKind kind, AMD64RMOp op, OperandSize size, AMD64AddressValue address, LIRFrameState state) {
        Variable result = newVariable(LIRKind.value(kind));
        append(new AMD64Unary.MemoryOp(op, size, result, address, state));
        return result;
    }

    protected Value emitZeroExtendMemory(Kind memoryKind, int resultBits, AMD64AddressValue address, LIRFrameState state) {
        // Issue a zero extending load of the proper bit size and set the result to
        // the proper kind.
        Variable result = newVariable(LIRKind.value(resultBits == 32 ? Kind.Int : Kind.Long));
        switch (memoryKind) {
            case Boolean:
            case Byte:
                append(new AMD64Unary.MemoryOp(MOVZXB, DWORD, result, address, state));
                break;
            case Char:
            case Short:
                append(new AMD64Unary.MemoryOp(MOVZX, DWORD, result, address, state));
                break;
            case Int:
                append(new AMD64Unary.MemoryOp(MOV, DWORD, result, address, state));
                break;
            case Long:
                append(new AMD64Unary.MemoryOp(MOV, QWORD, result, address, state));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private AMD64MulDivOp emitIDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.derive(a, b);

        AMD64SignExtendOp sx = append(new AMD64SignExtendOp(size, kind, moveToReg(AMD64.rax, a)));
        return append(new AMD64MulDivOp(AMD64MOp.IDIV, size, kind, sx.getHighResult(), sx.getLowResult(), asAllocatable(b), state));
    }

    private AMD64MulDivOp emitDIV(OperandSize size, Value a, Value b, LIRFrameState state) {
        LIRKind kind = LIRKind.derive(a, b);

        RegisterValue rax = moveToReg(AMD64.rax, a);
        RegisterValue rdx = AMD64.rdx.asValue(kind);
        append(new AMD64ClearRegisterOp(size, rdx));
        return append(new AMD64MulDivOp(AMD64MOp.DIV, size, kind, rdx, rax, asAllocatable(b), state));
    }

    public Value[] emitIntegerDivRem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch (a.getKind().getStackKind()) {
            case Int:
                op = emitIDIV(DWORD, a, b, state);
                break;
            case Long:
                op = emitIDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return new Value[]{emitMove(op.getQuotient()), emitMove(op.getRemainder())};
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        switch (a.getKind().getStackKind()) {
            case Int:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return emitMove(op.getQuotient());
            case Long:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return emitMove(lop.getQuotient());
            case Float:
                return emitBinary(SSEOp.DIV, SS, false, a, b);
            case Double:
                return emitBinary(SSEOp.DIV, SD, false, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        switch (a.getKind().getStackKind()) {
            case Int:
                AMD64MulDivOp op = emitIDIV(DWORD, a, b, state);
                return emitMove(op.getRemainder());
            case Long:
                AMD64MulDivOp lop = emitIDIV(QWORD, a, b, state);
                return emitMove(lop.getRemainder());
            case Float: {
                Variable result = newVariable(LIRKind.derive(a, b));
                append(new FPDivRemOp(FREM, result, load(a), load(b)));
                return result;
            }
            case Double: {
                Variable result = newVariable(LIRKind.derive(a, b));
                append(new FPDivRemOp(DREM, result, load(a), load(b)));
                return result;
            }
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUDiv(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch (a.getKind().getStackKind()) {
            case Int:
                op = emitDIV(DWORD, a, b, state);
                break;
            case Long:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMove(op.getQuotient());
    }

    @Override
    public Variable emitURem(Value a, Value b, LIRFrameState state) {
        AMD64MulDivOp op;
        switch (a.getKind().getStackKind()) {
            case Int:
                op = emitDIV(DWORD, a, b, state);
                break;
            case Long:
                op = emitDIV(QWORD, a, b, state);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMove(op.getRemainder());
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(AND, DWORD, true, a, b, false);
            case Long:
                return emitBinary(AND, QWORD, true, a, b, false);
            case Float:
                return emitBinary(SSEOp.AND, PS, true, a, b);
            case Double:
                return emitBinary(SSEOp.AND, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(OR, DWORD, true, a, b, false);
            case Long:
                return emitBinary(OR, QWORD, true, a, b, false);
            case Float:
                return emitBinary(SSEOp.OR, PS, true, a, b);
            case Double:
                return emitBinary(SSEOp.OR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitBinary(XOR, DWORD, true, a, b, false);
            case Long:
                return emitBinary(XOR, QWORD, true, a, b, false);
            case Float:
                return emitBinary(SSEOp.XOR, PS, true, a, b);
            case Double:
                return emitBinary(SSEOp.XOR, PD, true, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Variable emitShift(AMD64Shift op, OperandSize size, Value a, Value b) {
        Variable result = newVariable(LIRKind.derive(a, b).changeType(a.getPlatformKind()));
        AllocatableValue input = asAllocatable(a);
        if (isConstant(b)) {
            JavaConstant c = asConstant(b);
            if (c.asLong() == 1) {
                append(new AMD64Unary.MOp(op.m1Op, size, result, input));
            } else {
                /*
                 * c is implicitly masked to 5 or 6 bits by the CPU, so casting it to (int) is
                 * always correct, even without the NumUtil.is32bit() test.
                 */
                append(new AMD64Binary.ConstOp(op.miOp, size, result, input, (int) c.asLong()));
            }
        } else {
            emitMove(RCX_I, b);
            append(new AMD64ShiftOp(op.mcOp, size, result, input, RCX_I));
        }
        return result;
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(SHL, DWORD, a, b);
            case Long:
                return emitShift(SHL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(SAR, DWORD, a, b);
            case Long:
                return emitShift(SAR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(SHR, DWORD, a, b);
            case Long:
                return emitShift(SHR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRol(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(ROL, DWORD, a, b);
            case Long:
                return emitShift(ROL, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public Variable emitRor(Value a, Value b) {
        switch (a.getKind().getStackKind()) {
            case Int:
                return emitShift(ROR, DWORD, a, b);
            case Long:
                return emitShift(ROR, QWORD, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64RMOp op, OperandSize size, Value input) {
        Variable result = newVariable(kind);
        append(new AMD64Unary.RMOp(op, size, result, asAllocatable(input)));
        return result;
    }

    private AllocatableValue emitConvertOp(LIRKind kind, AMD64MROp op, OperandSize size, Value input) {
        Variable result = newVariable(kind);
        append(new AMD64Unary.MROp(op, size, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitReinterpret(LIRKind to, Value inputVal) {
        LIRKind from = inputVal.getLIRKind();
        if (to.equals(from)) {
            return inputVal;
        }

        AllocatableValue input = asAllocatable(inputVal);
        /*
         * Conversions between integer to floating point types require moves between CPU and FPU
         * registers.
         */
        Kind fromKind = (Kind) from.getPlatformKind();
        switch ((Kind) to.getPlatformKind()) {
            case Int:
                switch (fromKind) {
                    case Float:
                        return emitConvertOp(to, AMD64MROp.MOVD, DWORD, input);
                }
                break;
            case Long:
                switch (fromKind) {
                    case Double:
                        return emitConvertOp(to, AMD64MROp.MOVQ, QWORD, input);
                }
                break;
            case Float:
                switch (fromKind) {
                    case Int:
                        return emitConvertOp(to, AMD64RMOp.MOVD, DWORD, input);
                }
                break;
            case Double:
                switch (fromKind) {
                    case Long:
                        return emitConvertOp(to, AMD64RMOp.MOVQ, QWORD, input);
                }
                break;
        }
        throw JVMCIError.shouldNotReachHere();
    }

    public Value emitFloatConvert(FloatConvert op, Value input) {
        switch (op) {
            case D2F:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Float), SSEOp.CVTSD2SS, SD, input);
            case D2I:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Int), SSEOp.CVTTSD2SI, DWORD, input);
            case D2L:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Long), SSEOp.CVTTSD2SI, QWORD, input);
            case F2D:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Double), SSEOp.CVTSS2SD, SS, input);
            case F2I:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Int), SSEOp.CVTTSS2SI, DWORD, input);
            case F2L:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Long), SSEOp.CVTTSS2SI, QWORD, input);
            case I2D:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Double), SSEOp.CVTSI2SD, DWORD, input);
            case I2F:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Float), SSEOp.CVTSI2SS, DWORD, input);
            case L2D:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Double), SSEOp.CVTSI2SD, QWORD, input);
            case L2F:
                return emitConvertOp(LIRKind.derive(input).changeType(Kind.Float), SSEOp.CVTSI2SS, QWORD, input);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getKind() == Kind.Long && bits <= 32) {
            // TODO make it possible to reinterpret Long as Int in LIR without move
            return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Int), AMD64RMOp.MOV, DWORD, inputVal);
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (toBits > 32) {
            // sign extend to 64 bits
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Long), MOVSXB, QWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Long), MOVSX, QWORD, inputVal);
                case 32:
                    return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Long), MOVSXD, QWORD, inputVal);
                default:
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        } else {
            // sign extend to 32 bits (smaller values are internally represented as 32 bit values)
            switch (fromBits) {
                case 8:
                    return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Int), MOVSXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(LIRKind.derive(inputVal).changeType(Kind.Int), MOVSX, DWORD, inputVal);
                case 32:
                    return inputVal;
                default:
                    throw JVMCIError.unimplemented("unsupported sign extension (" + fromBits + " bit -> " + toBits + " bit)");
            }
        }
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        } else if (fromBits > 32) {
            assert inputVal.getKind() == Kind.Long;
            Variable result = newVariable(LIRKind.derive(inputVal).changeType(Kind.Long));
            long mask = CodeUtil.mask(fromBits);
            append(new AMD64Binary.DataOp(AND.getRMOpcode(QWORD), QWORD, result, asAllocatable(inputVal), JavaConstant.forLong(mask)));
            return result;
        } else {
            assert inputVal.getKind().getStackKind() == Kind.Int;

            LIRKind resultKind = LIRKind.derive(inputVal);
            if (toBits > 32) {
                resultKind = resultKind.changeType(Kind.Long);
            } else {
                resultKind = resultKind.changeType(Kind.Int);
            }

            /*
             * Always emit DWORD operations, even if the resultKind is Long. On AMD64, all DWORD
             * operations implicitly set the upper half of the register to 0, which is what we want
             * anyway. Compared to the QWORD oparations, the encoding of the DWORD operations is
             * sometimes one byte shorter.
             */
            switch (fromBits) {
                case 8:
                    return emitConvertOp(resultKind, MOVZXB, DWORD, inputVal);
                case 16:
                    return emitConvertOp(resultKind, MOVZX, DWORD, inputVal);
                case 32:
                    return emitConvertOp(resultKind, MOV, DWORD, inputVal);
            }

            // odd bit count, fall back on manual masking
            Variable result = newVariable(resultKind);
            JavaConstant mask;
            if (toBits > 32) {
                mask = JavaConstant.forLong(CodeUtil.mask(fromBits));
            } else {
                mask = JavaConstant.forInt((int) CodeUtil.mask(fromBits));
            }
            append(new AMD64Binary.DataOp(AND.getRMOpcode(DWORD), DWORD, result, asAllocatable(inputVal), mask));
            return result;
        }
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
    public Variable emitBitCount(Value value) {
        Variable result = newVariable(LIRKind.derive(value).changeType(Kind.Int));
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64Unary.RMOp(POPCNT, DWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(POPCNT, QWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value value) {
        Variable result = newVariable(LIRKind.derive(value).changeType(Kind.Int));
        append(new AMD64Unary.RMOp(BSF, QWORD, result, asAllocatable(value)));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value value) {
        Variable result = newVariable(LIRKind.derive(value).changeType(Kind.Int));
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64Unary.RMOp(BSR, DWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(BSR, QWORD, result, asAllocatable(value)));
        }
        return result;
    }

    public Value emitCountLeadingZeros(Value value) {
        Variable result = newVariable(LIRKind.derive(value).changeType(Kind.Int));
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64Unary.RMOp(LZCNT, DWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(LZCNT, QWORD, result, asAllocatable(value)));
        }
        return result;
    }

    public Value emitCountTrailingZeros(Value value) {
        Variable result = newVariable(LIRKind.derive(value).changeType(Kind.Int));
        if (value.getKind().getStackKind() == Kind.Int) {
            append(new AMD64Unary.RMOp(TZCNT, DWORD, result, asAllocatable(value)));
        } else {
            append(new AMD64Unary.RMOp(TZCNT, QWORD, result, asAllocatable(value)));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind()) {
            case Float:
                append(new AMD64Binary.DataOp(SSEOp.AND, PS, result, asAllocatable(input), JavaConstant.forFloat(Float.intBitsToFloat(0x7FFFFFFF)), 16));
                break;
            case Double:
                append(new AMD64Binary.DataOp(SSEOp.AND, PD, result, asAllocatable(input), JavaConstant.forDouble(Double.longBitsToDouble(0x7FFFFFFFFFFFFFFFL)), 16));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        switch (input.getKind()) {
            case Float:
                append(new AMD64Unary.RMOp(SSEOp.SQRT, SS, result, asAllocatable(input)));
                break;
            case Double:
                append(new AMD64Unary.RMOp(SSEOp.SQRT, SD, result, asAllocatable(input)));
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    @Override
    public Value emitMathLog(Value input, boolean base10) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new AMD64MathIntrinsicOp(base10 ? LOG10 : LOG, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathCos(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new AMD64MathIntrinsicOp(COS, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathSin(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new AMD64MathIntrinsicOp(SIN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Value emitMathTan(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new AMD64MathIntrinsicOp(TAN, result, asAllocatable(input)));
        return result;
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.derive(input));
        append(new AMD64ByteSwapOp(result, input));
        return result;
    }

    @Override
    public Variable emitArrayEquals(Kind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(Kind.Int));
        append(new AMD64ArrayEqualsOp(this, kind, result, array1, array2, asAllocatable(length)));
        return result;
    }

    @Override
    public void emitReturn(Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(input.getLIRKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        // a temp is needed for loading object constants
        boolean needsTemp = key.getKind() == Kind.Object;
        append(new StrategySwitchOp(strategy, keyTargets, defaultTarget, key, needsTemp ? newVariable(key.getLIRKind()) : Value.ILLEGAL));
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        append(new TableSwitchOp(lowKey, defaultTarget, targets, key, newVariable(LIRKind.value(target().wordKind)), newVariable(key.getLIRKind())));
    }

}
