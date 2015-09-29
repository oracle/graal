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

package com.oracle.graal.compiler.sparc;

import static com.oracle.graal.asm.sparc.SPARCAssembler.FMOVDCC;
import static com.oracle.graal.asm.sparc.SPARCAssembler.FMOVSCC;
import static com.oracle.graal.asm.sparc.SPARCAssembler.MOVicc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.CC.Fcc0;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Add;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Addcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.And;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Mulx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sdivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sllx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sra;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Srax;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Srl;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Sub;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Subcc;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Udivx;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Op3s.Xnor;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Faddd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fadds;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmpd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fcmps;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdivd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdivs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fdtos;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fitod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fitos;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fmuld;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fmuls;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fnegd;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fnegs;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fstod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.Fxtod;
import static com.oracle.graal.asm.sparc.SPARCAssembler.Opfs.UMulxhi;
import static com.oracle.graal.lir.LIRValueUtil.asJavaConstant;
import static com.oracle.graal.lir.LIRValueUtil.isJavaConstant;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.BSF;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.IBSR;
import static com.oracle.graal.lir.sparc.SPARCBitManipulationOp.IntrinsicOpcode.LBSR;
import static jdk.internal.jvmci.code.CodeUtil.mask;
import static jdk.internal.jvmci.code.ValueUtil.isStackSlotValue;
import static jdk.internal.jvmci.meta.JavaConstant.forLong;
import static jdk.internal.jvmci.sparc.SPARC.g0;
import static jdk.internal.jvmci.sparc.SPARCKind.DOUBLE;
import static jdk.internal.jvmci.sparc.SPARCKind.DWORD;
import static jdk.internal.jvmci.sparc.SPARCKind.SINGLE;
import static jdk.internal.jvmci.sparc.SPARCKind.WORD;
import jdk.internal.jvmci.code.CallingConvention;
import jdk.internal.jvmci.code.StackSlotValue;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.meta.AllocatableValue;
import jdk.internal.jvmci.meta.Constant;
import jdk.internal.jvmci.meta.JavaConstant;
import jdk.internal.jvmci.meta.JavaKind;
import jdk.internal.jvmci.meta.LIRKind;
import jdk.internal.jvmci.meta.PlatformKind;
import jdk.internal.jvmci.meta.Value;
import jdk.internal.jvmci.sparc.SPARC;
import jdk.internal.jvmci.sparc.SPARC.CPUFeature;
import jdk.internal.jvmci.sparc.SPARCKind;

import com.oracle.graal.asm.sparc.SPARCAssembler;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.CMOV;
import com.oracle.graal.asm.sparc.SPARCAssembler.ConditionFlag;
import com.oracle.graal.asm.sparc.SPARCAssembler.Op3s;
import com.oracle.graal.asm.sparc.SPARCAssembler.Opfs;
import com.oracle.graal.compiler.common.calc.Condition;
import com.oracle.graal.compiler.common.calc.FloatConvert;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRValueUtil;
import com.oracle.graal.lir.LabelRef;
import com.oracle.graal.lir.StandardOp.NoOp;
import com.oracle.graal.lir.SwitchStrategy;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGenerator;
import com.oracle.graal.lir.gen.SpillMoveFactoryBase;
import com.oracle.graal.lir.sparc.SPARCAddressValue;
import com.oracle.graal.lir.sparc.SPARCArithmetic;
import com.oracle.graal.lir.sparc.SPARCArithmetic.MulHighOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.MulHighOp.MulHigh;
import com.oracle.graal.lir.sparc.SPARCArithmetic.RemOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.RemOp.Rem;
import com.oracle.graal.lir.sparc.SPARCArithmetic.SPARCIMulccOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.SPARCLMulccOp;
import com.oracle.graal.lir.sparc.SPARCArithmetic.FloatConvertOp;
import com.oracle.graal.lir.sparc.SPARCArrayEqualsOp;
import com.oracle.graal.lir.sparc.SPARCBitManipulationOp;
import com.oracle.graal.lir.sparc.SPARCByteSwapOp;
import com.oracle.graal.lir.sparc.SPARCCall;
import com.oracle.graal.lir.sparc.SPARCControlFlow;
import com.oracle.graal.lir.sparc.SPARCControlFlow.BranchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.CondMoveOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.ReturnOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.StrategySwitchOp;
import com.oracle.graal.lir.sparc.SPARCControlFlow.TableSwitchOp;
import com.oracle.graal.lir.sparc.SPARCFloatCompareOp;
import com.oracle.graal.lir.sparc.SPARCImmediateAddressValue;
import com.oracle.graal.lir.sparc.SPARCJumpOp;
import com.oracle.graal.lir.sparc.SPARCLoadConstantTableBaseOp;
import com.oracle.graal.lir.sparc.SPARCMove;
import com.oracle.graal.lir.sparc.SPARCMove.LoadAddressOp;
import com.oracle.graal.lir.sparc.SPARCMove.LoadDataAddressOp;
import com.oracle.graal.lir.sparc.SPARCMove.LoadOp;
import com.oracle.graal.lir.sparc.SPARCMove.MembarOp;
import com.oracle.graal.lir.sparc.SPARCMove.Move;
import com.oracle.graal.lir.sparc.SPARCMove.MoveFpGp;
import com.oracle.graal.lir.sparc.SPARCMove.NullCheckOp;
import com.oracle.graal.lir.sparc.SPARCMove.StackLoadAddressOp;
import com.oracle.graal.lir.sparc.SPARCOP3Op;
import com.oracle.graal.lir.sparc.SPARCOPFOp;
import com.oracle.graal.phases.util.Providers;

/**
 * This class implements the SPARC specific portion of the LIR generator.
 */
public abstract class SPARCLIRGenerator extends LIRGenerator {

    private StackSlotValue tmpStackSlot;
    private SPARCSpillMoveFactory moveFactory;
    private Variable constantTableBase;
    private SPARCLoadConstantTableBaseOp loadConstantTableBaseOp;

    private class SPARCSpillMoveFactory extends SpillMoveFactoryBase {

        @Override
        protected LIRInstruction createMoveIntern(AllocatableValue result, Value input) {
            return SPARCLIRGenerator.this.createMove(result, input);
        }

        @Override
        protected LIRInstruction createStackMoveIntern(AllocatableValue result, AllocatableValue input) {
            return SPARCLIRGenerator.this.createStackMove(result, input);
        }

        @Override
        protected LIRInstruction createLoadIntern(AllocatableValue result, Constant input) {
            return SPARCLIRGenerator.this.createMoveConstant(result, input);
        }
    }

    public SPARCLIRGenerator(LIRKindTool lirKindTool, Providers providers, CallingConvention cc, LIRGenerationResult lirGenRes) {
        super(lirKindTool, providers, cc, lirGenRes);
    }

    public SpillMoveFactory getSpillMoveFactory() {
        if (moveFactory == null) {
            moveFactory = new SPARCSpillMoveFactory();
        }
        return moveFactory;
    }

    @Override
    public boolean canInlineConstant(JavaConstant c) {
        switch (c.getJavaKind()) {
            case Boolean:
            case Byte:
            case Char:
            case Short:
            case Int:
                return SPARCAssembler.isSimm13(c.asInt()) && !getCodeCache().needsDataPatch(c);
            case Long:
                return SPARCAssembler.isSimm13(c.asLong()) && !getCodeCache().needsDataPatch(c);
            case Object:
                return c.isNull();
            default:
                return false;
        }
    }

    @Override
    protected JavaConstant zapValueForKind(PlatformKind kind) {
        long dead = 0xDEADDEADDEADDEADL;
        switch ((SPARCKind) kind) {
            case BYTE:
                return JavaConstant.forByte((byte) dead);
            case HWORD:
                return JavaConstant.forShort((short) dead);
            case WORD:
                return JavaConstant.forInt((int) dead);
            case DWORD:
                return JavaConstant.forLong(dead);
            case SINGLE:
            case V32_BYTE:
            case V32_HWORD:
                return JavaConstant.forFloat(Float.intBitsToFloat((int) dead));
            case DOUBLE:
            case V64_BYTE:
            case V64_HWORD:
            case V64_WORD:
                return JavaConstant.forDouble(Double.longBitsToDouble(dead));
            default:
                throw new IllegalArgumentException(kind.toString());
        }
    }

    protected LIRInstruction createMove(AllocatableValue dst, Value src) {
        boolean srcIsSlot = isStackSlotValue(src);
        boolean dstIsSlot = isStackSlotValue(dst);
        if (src instanceof ConstantValue) {
            return createMoveConstant(dst, ((ConstantValue) src).getConstant());
        } else if (src instanceof SPARCAddressValue) {
            return new LoadAddressOp(dst, (SPARCAddressValue) src);
        } else {
            assert src instanceof AllocatableValue;
            if (srcIsSlot && dstIsSlot) {
                throw JVMCIError.shouldNotReachHere(src.getClass() + " " + dst.getClass());
            } else {
                return new Move(dst, (AllocatableValue) src);
            }
        }
    }

    protected LIRInstruction createMoveConstant(AllocatableValue dst, Constant src) {
        if (src instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) src;
            if (canInlineConstant(javaConstant)) {
                return new SPARCMove.LoadInlineConstant(javaConstant, dst);
            } else {
                return new SPARCMove.LoadConstantFromTable(javaConstant, getConstantTableBase(), dst);
            }
        } else {
            throw JVMCIError.shouldNotReachHere(src.getClass().toString());
        }
    }

    /**
     * The SPARC backend only uses WORD and DWORD values in registers because except to the ld/st
     * instructions no instruction deals either with 32 or 64 bits. This function converts small
     * integer kinds to WORD.
     */
    @Override
    public LIRKind toRegisterKind(LIRKind kind) {
        switch ((SPARCKind) kind.getPlatformKind()) {
            case BYTE:
            case HWORD:
                return kind.changeType(SPARCKind.WORD);
            default:
                return kind;
        }
    }

    protected LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        return new SPARCMove.Move(result, input);
    }

    @Override
    public void emitMove(AllocatableValue dst, Value src) {
        append(createMove(dst, src));
    }

    @Override
    public void emitMoveConstant(AllocatableValue dst, Constant src) {
        append(createMoveConstant(dst, src));
    }

    @Override
    public void emitData(AllocatableValue dst, byte[] data) {
        append(new LoadDataAddressOp(dst, data));
    }

    public SPARCAddressValue asAddressValue(Value address) {
        if (address instanceof SPARCAddressValue) {
            return (SPARCAddressValue) address;
        } else {
            LIRKind kind = address.getLIRKind();
            if (address instanceof JavaConstant) {
                long displacement = ((JavaConstant) address).asLong();
                if (SPARCAssembler.isSimm13(displacement)) {
                    return new SPARCImmediateAddressValue(kind, SPARC.g0.asValue(kind), (int) displacement);
                }
            }
            return new SPARCImmediateAddressValue(kind, asAllocatable(address), 0);
        }
    }

    @Override
    public Variable emitAddress(StackSlotValue address) {
        Variable result = newVariable(LIRKind.value(target().arch.getWordKind()));
        append(new StackLoadAddressOp(result, address));
        return result;
    }

    @Override
    public void emitReturn(JavaKind javaKind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(javaKind, input.getLIRKind());
            emitMove(operand, input);
        }
        append(new ReturnOp(operand));
    }

    @Override
    public void emitJump(LabelRef label) {
        append(new SPARCJumpOp(label));
    }

    @Override
    public void emitCompareBranch(PlatformKind cmpKind, Value x, Value y, Condition cond, boolean unorderedIsTrue, LabelRef trueDestination, LabelRef falseDestination,
                    double trueDestinationProbability) {
        Value left;
        Value right;
        Condition actualCondition;
        if (isJavaConstant(x)) {
            left = load(y);
            right = loadNonConst(x);
            actualCondition = cond.mirror();
        } else {
            left = load(x);
            right = loadNonConst(y);
            actualCondition = cond;
        }
        SPARCKind actualCmpKind = (SPARCKind) cmpKind;
        if (actualCmpKind.isInteger()) {
            actualCmpKind = toSPARCCmpKind(actualCmpKind);
            append(new SPARCControlFlow.CompareBranchOp(canonicalizeForCompare(left, cmpKind, actualCmpKind), canonicalizeForCompare(right, cmpKind, actualCmpKind), actualCondition, trueDestination,
                            falseDestination, actualCmpKind, unorderedIsTrue, trueDestinationProbability));
        } else if (actualCmpKind.isFloat()) {
            emitFloatCompare(actualCmpKind, x, y, Fcc0);
            ConditionFlag cf = SPARCControlFlow.fromCondition(false, cond, unorderedIsTrue);
            append(new SPARCControlFlow.BranchOp(cf, trueDestination, falseDestination, actualCmpKind, trueDestinationProbability));
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    private static SPARCKind toSPARCCmpKind(SPARCKind actualCmpKind) {
        if (actualCmpKind.isInteger() && actualCmpKind.getSizeInBytes() <= 4) {
            return SPARCKind.WORD;
        } else {
            return actualCmpKind;
        }
    }

    private Value canonicalizeForCompare(Value v, PlatformKind from, PlatformKind to) {
        if (LIRValueUtil.isJavaConstant(v)) {
            JavaConstant c = asJavaConstant(v);
            return new ConstantValue(v.getLIRKind().changeType(to), c);
        } else {
            int fromBytes = from.getSizeInBytes() * 8;
            int toBytes = to.getSizeInBytes() * 8;
            assert from.getSizeInBytes() <= v.getPlatformKind().getSizeInBytes();
            if (from == to) {
                return v;
            } else {
                return emitSignExtend(v, fromBytes, toBytes);
            }
        }
    }

    @Override
    public void emitOverflowCheckBranch(LabelRef overflow, LabelRef noOverflow, LIRKind cmpLIRKind, double overflowProbability) {
        SPARCKind cmpKind = (SPARCKind) cmpLIRKind.getPlatformKind();
        append(new BranchOp(ConditionFlag.OverflowSet, overflow, noOverflow, cmpKind, overflowProbability));
    }

    @Override
    public void emitIntegerTestBranch(Value left, Value right, LabelRef trueDestination, LabelRef falseDestination, double trueDestinationProbability) {
        emitIntegerTest(left, right);
        append(new BranchOp(ConditionFlag.Equal, trueDestination, falseDestination, (SPARCKind) left.getPlatformKind(), trueDestinationProbability));
    }

    private void emitIntegerTest(Value a, Value b) {
        assert ((SPARCKind) a.getPlatformKind()).isInteger();
        if (LIRValueUtil.isVariable(b)) {
            append(SPARCOP3Op.newBinaryVoid(Op3s.Andcc, load(b), loadNonConst(a)));
        } else {
            append(SPARCOP3Op.newBinaryVoid(Op3s.Andcc, load(a), loadNonConst(b)));
        }
    }

    private Value loadSimm11(Value value) {
        if (isJavaConstant(value)) {
            JavaConstant c = asJavaConstant(value);
            if (c.isNull() || SPARCAssembler.isSimm11(c)) {
                return value;
            }
        }
        return load(value);
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        // Emit compare
        SPARCKind cmpSPARCKind = (SPARCKind) cmpKind;
        boolean mirrored = emitCompare(cmpSPARCKind, left, right);

        // Emit move
        Value actualTrueValue = trueValue;
        Value actualFalseValue = falseValue;
        SPARCKind valueKind = (SPARCKind) trueValue.getPlatformKind();
        CMOV cmove;
        if (valueKind.isFloat()) {
            actualTrueValue = load(trueValue); // Floats cannot be immediate at all
            actualFalseValue = load(falseValue);
            cmove = valueKind.equals(SINGLE) ? FMOVSCC : FMOVDCC;
        } else if (valueKind.isInteger()) {
            actualTrueValue = loadSimm11(trueValue);
            actualFalseValue = loadSimm11(falseValue);
            cmove = MOVicc;
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
        Variable result = newVariable(trueValue.getLIRKind());
        ConditionFlag finalCondition = SPARCControlFlow.fromCondition(cmpSPARCKind.isInteger(), mirrored ? cond.mirror() : cond, unorderedIsTrue);
        CC cc = CC.forKind(toSPARCCmpKind(cmpSPARCKind));
        append(new CondMoveOp(cmove, cc, finalCondition, actualTrueValue, actualFalseValue, result));
        return result;
    }

    /**
     * This method emits the compare instruction, and may reorder the operands. It returns true if
     * it did so.
     *
     * @param cmpKind Kind how a and b have to be compared
     * @param a the left operand of the comparison
     * @param b the right operand of the comparison
     * @return true if the left and right operands were switched, false otherwise
     */
    protected boolean emitCompare(SPARCKind cmpKind, Value a, Value b) {
        boolean mirrored;
        if (cmpKind.isInteger()) { // Integer case
            mirrored = emitIntegerCompare(cmpKind, a, b);
        } else if (cmpKind.isFloat()) { // Float case
            mirrored = false; // No mirroring done on floats
            emitFloatCompare(cmpKind, a, b, Fcc0);
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
        return mirrored;
    }

    private boolean emitIntegerCompare(SPARCKind cmpKind, Value a, Value b) {
        boolean mirrored;
        assert cmpKind.isInteger();
        Value left;
        Value right;
        if (LIRValueUtil.isVariable(b)) {
            left = load(b);
            right = loadNonConst(a);
            mirrored = true;
        } else {
            left = load(a);
            right = loadNonConst(b);
            mirrored = false;
        }
        int compareBytes = cmpKind.getSizeInBytes();
        // SPARC compares 32 or 64 bits
        if (compareBytes < left.getPlatformKind().getSizeInBytes()) {
            left = emitSignExtend(left, compareBytes * 8, DWORD.getSizeInBytes() * 8);
        }
        if (compareBytes < right.getPlatformKind().getSizeInBytes()) {
            right = emitSignExtend(right, compareBytes * 8, DWORD.getSizeInBytes() * 8);
        }
        append(SPARCOP3Op.newBinaryVoid(Subcc, left, right));
        return mirrored;
    }

    private void emitFloatCompare(SPARCKind cmpJavaKind, Value a, Value b, CC cc) {
        Opfs floatCompareOpcode;
        assert cmpJavaKind.isFloat();
        switch (cmpJavaKind) {
            case DOUBLE:
                floatCompareOpcode = Fcmpd;
                break;
            case SINGLE:
                floatCompareOpcode = Fcmps;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        append(new SPARCFloatCompareOp(floatCompareOpcode, cc, load(a), load(b)));
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        emitIntegerTest(left, right);
        Variable result = newVariable(trueValue.getLIRKind());
        ConditionFlag flag = SPARCControlFlow.fromCondition(true, Condition.EQ, false);
        CC cc = CC.forKind(left.getPlatformKind());
        append(new CondMoveOp(MOVicc, cc, flag, loadSimm11(trueValue), loadSimm11(falseValue), result));
        return result;
    }

    @Override
    protected void emitForeignCallOp(ForeignCallLinkage linkage, Value result, Value[] arguments, Value[] temps, LIRFrameState info) {
        long maxOffset = linkage.getMaxCallTargetOffset();
        if (SPARCAssembler.isWordDisp30(maxOffset)) {
            append(new SPARCCall.DirectNearForeignCallOp(linkage, result, arguments, temps, info));
        } else {
            append(new SPARCCall.DirectFarForeignCallOp(linkage, result, arguments, temps, info));
        }
    }

    @Override
    public void emitStrategySwitch(SwitchStrategy strategy, Variable key, LabelRef[] keyTargets, LabelRef defaultTarget) {
        AllocatableValue scratchValue = newVariable(key.getLIRKind());
        AllocatableValue base = AllocatableValue.ILLEGAL;
        for (Constant c : strategy.getKeyConstants()) {
            if (!(c instanceof JavaConstant) || !canInlineConstant((JavaConstant) c)) {
                base = getConstantTableBase();
                break;
            }
        }
        append(createStrategySwitchOp(base, strategy, keyTargets, defaultTarget, key, scratchValue));
    }

    protected StrategySwitchOp createStrategySwitchOp(AllocatableValue base, SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Variable key, AllocatableValue scratchValue) {
        return new StrategySwitchOp(base, strategy, keyTargets, defaultTarget, key, scratchValue);
    }

    @Override
    protected void emitTableSwitch(int lowKey, LabelRef defaultTarget, LabelRef[] targets, Value key) {
        // Making a copy of the switch value is necessary because jump table destroys the input
        // value
        Variable tmp = newVariable(key.getLIRKind());
        emitMove(tmp, key);
        append(new TableSwitchOp(lowKey, defaultTarget, targets, tmp, newVariable(LIRKind.value(target().arch.getWordKind()))));
    }

    @Override
    public Variable emitBitCount(Value operand) {
        Variable result = newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        Value usedOperand = operand;
        if (operand.getPlatformKind() == SPARCKind.WORD) { // Zero extend
            usedOperand = newVariable(operand.getLIRKind());
            append(new SPARCOP3Op(Op3s.Srl, operand, SPARC.g0.asValue(), usedOperand));
        }
        append(new SPARCOP3Op(Op3s.Popc, SPARC.g0.asValue(), usedOperand, result));
        return result;
    }

    @Override
    public Variable emitBitScanForward(Value operand) {
        Variable result = newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        append(new SPARCBitManipulationOp(BSF, result, asAllocatable(operand), this));
        return result;
    }

    @Override
    public Variable emitBitScanReverse(Value operand) {
        Variable result = newVariable(LIRKind.combine(operand).changeType(SPARCKind.WORD));
        if (operand.getPlatformKind() == SPARCKind.DWORD) {
            append(new SPARCBitManipulationOp(LBSR, result, asAllocatable(operand), this));
        } else {
            append(new SPARCBitManipulationOp(IBSR, result, asAllocatable(operand), this));
        }
        return result;
    }

    @Override
    public Value emitMathAbs(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        SPARCKind kind = (SPARCKind) input.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fabss;
                break;
            case DOUBLE:
                opf = Opfs.Fabsd;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Input kind: " + kind);
        }
        append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    @Override
    public Value emitMathSqrt(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        SPARCKind kind = (SPARCKind) input.getPlatformKind();
        Opfs opf;
        switch (kind) {
            case SINGLE:
                opf = Opfs.Fsqrts;
                break;
            case DOUBLE:
                opf = Opfs.Fsqrtd;
                break;
            default:
                throw JVMCIError.shouldNotReachHere("Input kind: " + kind);
        }
        append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    @Override
    public Variable emitByteSwap(Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new SPARCByteSwapOp(this, result, input));
        return result;
    }

    @Override
    public Variable emitArrayEquals(JavaKind kind, Value array1, Value array2, Value length) {
        Variable result = newVariable(LIRKind.value(SPARCKind.WORD));
        append(new SPARCArrayEqualsOp(this, kind, result, load(array1), load(array2), asAllocatable(length)));
        return result;
    }

    @Override
    public Value emitNegate(Value input) {
        PlatformKind inputKind = input.getPlatformKind();
        if (isNumericInteger(inputKind)) {
            return emitUnary(Sub, input);
        } else {
            return emitUnary(inputKind.equals(DOUBLE) ? Fnegd : Fnegs, input);
        }
    }

    @Override
    public Value emitNot(Value input) {
        return emitUnary(Xnor, input);
    }

    private Variable emitUnary(Opfs opf, Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(new SPARCOPFOp(opf, g0.asValue(), input, result));
        return result;
    }

    private Variable emitUnary(Op3s op3, Value input) {
        Variable result = newVariable(LIRKind.combine(input));
        append(SPARCOP3Op.newUnary(op3, input, result));
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, Opfs opf, Value a, Value b) {
        return emitBinary(resultKind, opf, a, b, null);
    }

    private Variable emitBinary(LIRKind resultKind, Opfs opf, Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(resultKind);
        if (opf.isCommutative() && isJavaConstant(a) && canInlineConstant(asJavaConstant(a))) {
            append(new SPARCOPFOp(opf, b, a, result, state));
        } else {
            append(new SPARCOPFOp(opf, a, b, result, state));
        }
        return result;
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, int b) {
        return emitBinary(resultKind, op3, a, new ConstantValue(LIRKind.value(WORD), JavaConstant.forInt(b)));
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, Value b) {
        return emitBinary(resultKind, op3, a, b, null);
    }

    private Variable emitBinary(LIRKind resultKind, Op3s op3, Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(resultKind);
        if (op3.isCommutative() && isJavaConstant(a) && canInlineConstant(asJavaConstant(a))) {
            append(new SPARCOP3Op(op3, load(b), a, result, state));
        } else {
            append(new SPARCOP3Op(op3, load(a), b, result, state));
        }
        return result;
    }

    @Override
    protected boolean isNumericInteger(PlatformKind kind) {
        return ((SPARCKind) kind).isInteger();
    }

    @Override
    public Variable emitAdd(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Addcc : Add, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Faddd : Fadds, a, b);
        }
    }

    @Override
    public Variable emitSub(LIRKind resultKind, Value a, Value b, boolean setFlags) {
        if (isNumericInteger(a.getPlatformKind())) {
            return emitBinary(resultKind, setFlags ? Subcc : Sub, a, b);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Opfs.Fsubd : Opfs.Fsubs, a, b);
        }
    }

    @Override
    public Variable emitMul(Value a, Value b, boolean setFlags) {
        LIRKind resultKind = LIRKind.combine(a, b);
        PlatformKind aKind = a.getPlatformKind();
        if (isNumericInteger(aKind)) {
            if (setFlags) {
                Variable result = newVariable(LIRKind.combine(a, b));
                if (aKind == DWORD) {
                    append(new SPARCLMulccOp(result, load(a), load(b), this));
                } else if (aKind == WORD) {
                    append(new SPARCIMulccOp(result, load(a), load(b)));
                } else {
                    throw JVMCIError.shouldNotReachHere();
                }
                return result;
            } else {
                return emitBinary(resultKind, setFlags ? Op3s.Mulscc : Op3s.Mulx, a, b);
            }
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Fmuld : Fmuls, a, b);
        }
    }

    @Override
    public Value emitMulHigh(Value a, Value b) {
        MulHigh opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = MulHigh.IMUL;
                break;
            case DWORD:
                opcode = MulHigh.LMUL;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitMulHigh(opcode, a, b);
    }

    @Override
    public Value emitUMulHigh(Value a, Value b) {
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                Value aExtended = emitBinary(LIRKind.combine(a), Srl, a, 0);
                Value bExtended = emitBinary(LIRKind.combine(b), Srl, b, 0);
                Value result = emitBinary(LIRKind.combine(a, b), Mulx, aExtended, bExtended);
                return emitBinary(LIRKind.combine(a, b), Srax, result, WORD.getSizeInBits());
            case DWORD:
                return emitBinary(LIRKind.combine(a, b), UMulxhi, a, b);
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private Value emitMulHigh(MulHigh opcode, Value a, Value b) {
        Variable result = newVariable(LIRKind.combine(a, b));
        MulHighOp mulHigh = new MulHighOp(opcode, load(a), load(b), result, newVariable(LIRKind.combine(a, b)));
        append(mulHigh);
        return result;
    }

    @Override
    public Value emitDiv(Value a, Value b, LIRFrameState state) {
        LIRKind resultKind = LIRKind.combine(a, b);
        PlatformKind aKind = a.getPlatformKind();
        PlatformKind bKind = b.getPlatformKind();
        if (isJavaConstant(b) && asJavaConstant(b).isDefaultForKind()) { // Div by zero
            Value zero = SPARC.g0.asValue(LIRKind.value(SPARCKind.WORD));
            return emitBinary(resultKind, Op3s.Sdivx, zero, zero, state);
        } else if (isNumericInteger(aKind)) {
            Value fixedA = emitSignExtend(a, aKind.getSizeInBytes() * 8, 64);
            Value fixedB = emitSignExtend(b, bKind.getSizeInBytes() * 8, 64);
            return emitBinary(resultKind, Op3s.Sdivx, fixedA, fixedB, state);
        } else {
            boolean isDouble = a.getPlatformKind().equals(DOUBLE);
            return emitBinary(resultKind, isDouble ? Opfs.Fdivd : Opfs.Fdivs, a, b, state);
        }
    }

    @Override
    public Value emitRem(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(LIRKind.combine(a, b));
        Value aLoaded;
        Value bLoaded;
        Variable q1; // Intermediate values
        Variable q2;
        Variable q3;
        Variable q4;
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        switch (aKind) {
            case WORD:
                q1 = emitBinary(result.getLIRKind(), Sra, a, g0.asValue(LIRKind.value(WORD)));
                q2 = emitBinary(q1.getLIRKind(), Sdivx, q1, b, state);
                q3 = emitBinary(q2.getLIRKind(), Op3s.Mulx, q2, b);
                result = emitSub(q1, q3, false);
                break;
            case DWORD:
                aLoaded = load(a); // Reuse the loaded value
                q1 = emitBinary(result.getLIRKind(), Sdivx, aLoaded, b, state);
                q2 = emitBinary(result.getLIRKind(), Mulx, q1, b);
                result = emitSub(aLoaded, q2, false);
                break;
            case SINGLE:
                aLoaded = load(a);
                bLoaded = load(b);
                q1 = emitBinary(result.getLIRKind(), Fdivs, aLoaded, bLoaded, state);
                q2 = newVariable(LIRKind.value(aKind));
                append(new FloatConvertOp(FloatConvertOp.FloatConvert.F2I, q1, q2));
                q3 = emitUnary(Fitos, q2);
                q4 = emitBinary(LIRKind.value(aKind), Fmuls, q3, bLoaded);
                result = emitSub(aLoaded, q4, false);
                break;
            case DOUBLE:
                aLoaded = load(a);
                bLoaded = load(b);
                q1 = emitBinary(result.getLIRKind(), Fdivd, aLoaded, bLoaded, state);
                q2 = newVariable(LIRKind.value(aKind));
                append(new FloatConvertOp(FloatConvertOp.FloatConvert.D2L, q1, q2));
                q3 = emitUnary(Fxtod, q2);
                q4 = emitBinary(result.getLIRKind(), Fmuld, q3, bLoaded);
                result = emitSub(aLoaded, q4, false);
                break;
            default:
                throw JVMCIError.shouldNotReachHere("missing: " + a.getPlatformKind());
        }
        return result;
    }

    @Override
    public Value emitURem(Value a, Value b, LIRFrameState state) {
        Variable result = newVariable(LIRKind.combine(a, b));
        Variable scratch1 = newVariable(LIRKind.combine(a, b));
        Variable scratch2 = newVariable(LIRKind.combine(a, b));
        Rem opcode;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                opcode = Rem.IUREM;
                break;
            case DWORD:
                opcode = Rem.LUREM;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        append(new RemOp(opcode, result, load(a), load(b), scratch1, scratch2, state));
        return result;

    }

    @Override
    public Value emitUDiv(Value a, Value b, LIRFrameState state) {
        Value actualA = a;
        Value actualB = b;
        switch (((SPARCKind) a.getPlatformKind())) {
            case WORD:
                actualA = emitZeroExtend(actualA, 32, 64);
                actualB = emitZeroExtend(actualB, 32, 64);
                break;
            case DWORD:
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(LIRKind.combine(actualA, actualB), Udivx, actualA, actualB, state);
    }

    @Override
    public Variable emitAnd(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.And, a, b);
    }

    @Override
    public Variable emitOr(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Or, a, b);
    }

    @Override
    public Variable emitXor(Value a, Value b) {
        LIRKind resultKind = LIRKind.combine(a, b);
        return emitBinary(resultKind, Op3s.Xor, a, b);
    }

    @Override
    public Variable emitShl(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sll;
                break;
            case DWORD:
                op = Op3s.Sllx;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Sra;
                break;
            case DWORD:
                op = Op3s.Srax;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    @Override
    public Variable emitUShr(Value a, Value b) {
        SPARCKind aKind = (SPARCKind) a.getPlatformKind();
        LIRKind resultKind = LIRKind.combine(a, b).changeType(aKind);
        Op3s op;
        switch (aKind) {
            case WORD:
                op = Op3s.Srl;
                break;
            case DWORD:
                op = Op3s.Srlx;
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return emitBinary(resultKind, op, a, b);
    }

    private AllocatableValue emitConvertMove(LIRKind kind, AllocatableValue input) {
        Variable result = newVariable(kind);
        emitMove(result, input);
        return result;
    }

    @Override
    public Value emitFloatConvert(FloatConvert op, Value inputVal) {
        AllocatableValue input = asAllocatable(inputVal);
        Value result;
        switch (op) {
            case D2F:
                result = newVariable(LIRKind.combine(inputVal).changeType(SINGLE));
                append(new SPARCOPFOp(Fdtos, inputVal, result));
                break;
            case F2D:
                result = newVariable(LIRKind.combine(inputVal).changeType(DOUBLE));
                append(new SPARCOPFOp(Fstod, inputVal, result));
                break;
            case I2F: {
                AllocatableValue intEncodedFloatReg = newVariable(LIRKind.combine(input).changeType(SINGLE));
                result = newVariable(intEncodedFloatReg.getLIRKind());
                moveBetweenFpGp(intEncodedFloatReg, input);
                append(new SPARCOPFOp(Fitos, intEncodedFloatReg, result));
                break;
            }
            case I2D: {
                // Unfortunately we must do int -> float -> double because fitod has float
                // and double encoding in one instruction
                AllocatableValue convertedFloatReg = newVariable(LIRKind.combine(input).changeType(SINGLE));
                result = newVariable(LIRKind.combine(input).changeType(DOUBLE));
                moveBetweenFpGp(convertedFloatReg, input);
                append(new SPARCOPFOp(Fitod, convertedFloatReg, result));
                break;
            }
            case L2D: {
                AllocatableValue longEncodedDoubleReg = newVariable(LIRKind.combine(input).changeType(DOUBLE));
                moveBetweenFpGp(longEncodedDoubleReg, input);
                AllocatableValue convertedDoubleReg = newVariable(longEncodedDoubleReg.getLIRKind());
                append(new SPARCOPFOp(Fxtod, longEncodedDoubleReg, convertedDoubleReg));
                result = convertedDoubleReg;
                break;
            }
            case D2I: {
                AllocatableValue convertedFloatReg = newVariable(LIRKind.combine(input).changeType(SINGLE));
                append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2I, input, convertedFloatReg));
                AllocatableValue convertedIntReg = newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case F2L: {
                AllocatableValue convertedDoubleReg = newVariable(LIRKind.combine(input).changeType(DOUBLE));
                append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2L, input, convertedDoubleReg));
                AllocatableValue convertedLongReg = newVariable(LIRKind.combine(convertedDoubleReg).changeType(DWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case F2I: {
                AllocatableValue convertedFloatReg = newVariable(LIRKind.combine(input).changeType(SINGLE));
                append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.F2I, input, convertedFloatReg));
                AllocatableValue convertedIntReg = newVariable(LIRKind.combine(convertedFloatReg).changeType(WORD));
                moveBetweenFpGp(convertedIntReg, convertedFloatReg);
                result = convertedIntReg;
                break;
            }
            case D2L: {
                AllocatableValue convertedDoubleReg = newVariable(LIRKind.combine(input).changeType(DOUBLE));
                append(new SPARCArithmetic.FloatConvertOp(FloatConvertOp.FloatConvert.D2L, input, convertedDoubleReg));
                AllocatableValue convertedLongReg = newVariable(LIRKind.combine(convertedDoubleReg).changeType(DWORD));
                moveBetweenFpGp(convertedLongReg, convertedDoubleReg);
                result = convertedLongReg;
                break;
            }
            case L2F: {
                AllocatableValue convertedDoubleReg = newVariable(LIRKind.combine(input).changeType(DOUBLE));
                result = newVariable(LIRKind.combine(input).changeType(SINGLE));
                moveBetweenFpGp(convertedDoubleReg, input);
                append(new SPARCOPFOp(Opfs.Fxtos, convertedDoubleReg, result));
                break;
            }
            default:
                throw JVMCIError.shouldNotReachHere();
        }
        return result;
    }

    private void moveBetweenFpGp(AllocatableValue dst, AllocatableValue src) {
        AllocatableValue tempSlot;
        if (getArchitecture().getFeatures().contains(CPUFeature.VIS3)) {
            tempSlot = AllocatableValue.ILLEGAL;
        } else {
            tempSlot = getTempSlot(LIRKind.value(DWORD));
        }
        append(new MoveFpGp(dst, src, tempSlot));
    }

    protected StackSlotValue getTempSlot(LIRKind kind) {
        if (tmpStackSlot == null) {
            tmpStackSlot = getResult().getFrameMapBuilder().allocateSpillSlot(kind);
        }
        return tmpStackSlot;
    }

    protected SPARC getArchitecture() {
        return (SPARC) target().arch;
    }

    @Override
    public Value emitNarrow(Value inputVal, int bits) {
        if (inputVal.getPlatformKind() == DWORD && bits <= 32) {
            LIRKind resultKind = LIRKind.combine(inputVal).changeType(WORD);
            Variable result = newVariable(resultKind);
            emitMove(result, inputVal);
            return result;
        } else {
            return inputVal;
        }
    }

    @Override
    public Value emitSignExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= DWORD.getSizeInBits();
        LIRKind shiftKind = LIRKind.value(WORD);
        LIRKind resultKind = LIRKind.combine(inputVal).changeType(toBits > 32 ? DWORD : WORD);
        Value result;
        int shiftCount = DWORD.getSizeInBits() - fromBits;
        if (fromBits == toBits) {
            result = inputVal;
        } else if (isJavaConstant(inputVal)) {
            JavaConstant javaConstant = asJavaConstant(inputVal);
            long constant;
            if (javaConstant.isNull()) {
                constant = 0;
            } else {
                constant = javaConstant.asLong();
            }
            return new ConstantValue(resultKind, JavaConstant.forLong((constant << shiftCount) >> shiftCount));
        } else if (fromBits == WORD.getSizeInBits() && toBits == DWORD.getSizeInBits()) {
            result = newVariable(resultKind);
            append(new SPARCOP3Op(Sra, inputVal, SPARC.g0.asValue(LIRKind.value(WORD)), result));
        } else {
            Variable tmp = newVariable(resultKind.changeType(DWORD));
            result = newVariable(resultKind);
            append(new SPARCOP3Op(Sllx, inputVal, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), tmp));
            append(new SPARCOP3Op(Srax, tmp, new ConstantValue(shiftKind, JavaConstant.forInt(shiftCount)), result));
        }
        return result;
    }

    @Override
    public Value emitZeroExtend(Value inputVal, int fromBits, int toBits) {
        assert fromBits <= toBits && toBits <= 64;
        if (fromBits == toBits) {
            return inputVal;
        }
        Variable result = newVariable(LIRKind.combine(inputVal).changeType(toBits > WORD.getSizeInBits() ? DWORD : WORD));
        if (fromBits == 32) {
            append(new SPARCOP3Op(Srl, inputVal, g0.asValue(), result));
        } else {
            Value mask = emitConstant(LIRKind.value(DWORD), forLong(mask(fromBits)));
            append(new SPARCOP3Op(And, inputVal, mask, result));
        }
        return result;
    }

    @Override
    public AllocatableValue emitReinterpret(LIRKind to, Value inputVal) {
        SPARCKind fromKind = (SPARCKind) inputVal.getPlatformKind();
        SPARCKind toKind = (SPARCKind) to.getPlatformKind();
        AllocatableValue input = asAllocatable(inputVal);
        Variable result = newVariable(to);
        // These cases require a move between CPU and FPU registers:
        if (fromKind.isFloat() != toKind.isFloat()) {
            moveBetweenFpGp(result, input);
            return result;
        } else {
            // Otherwise, just emit an ordinary move instruction.
            // Instructions that move or generate 32-bit register values also set the upper 32
            // bits of the register to zero.
            // Consequently, there is no need for a special zero-extension move.
            return emitConvertMove(to, input);
        }
    }

    @Override
    public void emitMembar(int barriers) {
        int necessaryBarriers = target().arch.requiredBarriers(barriers);
        if (target().isMP && necessaryBarriers != 0) {
            append(new MembarOp(necessaryBarriers));
        }
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value speculation, LIRFrameState state) {
        append(new ReturnOp(Value.ILLEGAL));
    }

    public Value emitSignExtendLoad(LIRKind kind, Value address, LIRFrameState state) {
        SPARCAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        append(new LoadOp(kind.getPlatformKind(), result, loadAddress, state, true));
        return result;
    }

    public void emitNullCheck(Value address, LIRFrameState state) {
        PlatformKind kind = address.getPlatformKind();
        assert kind == DWORD : address + " - " + kind + " not an object!";
        append(new NullCheckOp(asAddressValue(address), state));
    }

    public void emitLoadConstantTableBase() {
        constantTableBase = newVariable(LIRKind.value(DWORD));
        int nextPosition = getResult().getLIR().getLIRforBlock(getCurrentBlock()).size();
        NoOp placeHolder = append(new NoOp(getCurrentBlock(), nextPosition));
        loadConstantTableBaseOp = new SPARCLoadConstantTableBaseOp(constantTableBase, placeHolder);
    }

    boolean useConstantTableBase = false;

    protected Variable getConstantTableBase() {
        useConstantTableBase = true;
        return constantTableBase;
    }

    @Override
    public void beforeRegisterAllocation() {
        LIR lir = getResult().getLIR();
        loadConstantTableBaseOp.setAlive(lir, useConstantTableBase);
    }
}
