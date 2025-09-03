/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.vector.lir.hotspot.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;

import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotLIRGenerator;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotMoveFactory;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.VirtualStackSlot;
import jdk.graal.compiler.lir.amd64.AMD64SaveRegistersOp;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.MoveFactory.BackupSlotProvider;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorArithmeticLIRGenerator;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorMoveFactory;
import jdk.graal.compiler.vector.nodes.simd.SimdConstant;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public class AMD64HotSpotVectorLIRGenerator extends AMD64HotSpotLIRGenerator {

    public AMD64HotSpotVectorLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes) {
        this(providers, config, lirGenRes, new BackupSlotProvider(lirGenRes.getFrameMapBuilder()));
    }

    private AMD64HotSpotVectorLIRGenerator(HotSpotProviders providers, GraalHotSpotVMConfig config, LIRGenerationResult lirGenRes, BackupSlotProvider backupSlotProvider) {
        super(new AMD64HotSpotSimdLIRKindTool(),
                        AMD64VectorArithmeticLIRGenerator.create(null, providers.getCodeCache().getTarget().arch),
                        getBarrierSet(config, providers),
                        new AMD64VectorMoveFactory(new AMD64HotSpotMoveFactory(backupSlotProvider), backupSlotProvider,
                                        AMD64SIMDInstructionEncoding.forFeatures(((AMD64) providers.getCodeCache().getTarget().arch).getFeatures())),
                        providers, config, lirGenRes);
    }

    @Override
    protected AMD64SaveRegistersOp emitSaveRegisters(Register[] savedRegisters, AllocatableValue[] savedRegisterLocations) {
        AMD64SaveRegistersOp save = new AMD64VectorMove.SaveRegistersOp(savedRegisters, savedRegisterLocations, ((AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen).getSimdEncoding());
        append(save);
        return save;
    }

    @Override
    protected Register[] getSaveableRegisters(boolean forSafepoint, AllocatableValue exclude) {
        Register[] allRegisters = super.getSaveableRegisters(forSafepoint, exclude);
        if (!forSafepoint) {
            return allRegisters;
        }
        // The full size vector registers can't be described through JVMCI so they must be treated
        // as killed. (GR-17373)
        Register excluded = isRegister(exclude) ? asRegister(exclude) : null;
        ArrayList<Register> registers = new ArrayList<>(allRegisters.length);
        for (Register r : allRegisters) {
            if (r.getRegisterCategory().equals(AMD64.CPU) && (excluded == null || !r.equals(excluded))) {
                registers.add(r);
            }
        }
        return registers.toArray(new Register[registers.size()]);
    }

    @Override
    protected VirtualStackSlot allocateSaveRegisterLocation(Register register) {
        PlatformKind kind = target().arch.getLargestStorableKind(register.getRegisterCategory());
        return getResult().getFrameMapBuilder().allocateSpillSlot(LIRKind.value(kind));
    }

    @Override
    protected void emitRestoreRegisters(AMD64SaveRegistersOp save) {
        append(new AMD64VectorMove.RestoreRegistersOp(save.getSlots().clone(), save, ((AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen).getSimdEncoding()));
    }

    @Override
    public Variable emitIntegerTestMove(Value left, Value right, Value trueValue, Value falseValue) {
        AMD64VectorArithmeticLIRGenerator vectorGen = (AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorIntegerTestMove(left, right, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitIntegerTestMove(left, right, trueValue, falseValue);
    }

    @Override
    public Variable emitOpMaskTestMove(Value left, boolean negateLeft, Value right, Value trueValue, Value falseValue) {
        AMD64VectorArithmeticLIRGenerator vectorGen = (AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorOpMaskTestMove(left, negateLeft, right, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitOpMaskTestMove(left, negateLeft, right, trueValue, falseValue);
    }

    @Override
    public Variable emitOpMaskOrTestMove(Value left, Value right, boolean allZeros, Value trueValue, Value falseValue) {
        AMD64VectorArithmeticLIRGenerator vectorGen = (AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorOpMaskOrTestMove(left, right, allZeros, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitOpMaskOrTestMove(left, right, allZeros, trueValue, falseValue);
    }

    @Override
    public Variable emitConditionalMove(PlatformKind cmpKind, Value left, Value right, Condition cond, boolean unorderedIsTrue, Value trueValue, Value falseValue) {
        AMD64VectorArithmeticLIRGenerator vectorGen = (AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen;
        Variable vectorResult = vectorGen.emitVectorConditionalMove(cmpKind, left, right, cond, unorderedIsTrue, trueValue, falseValue);
        if (vectorResult != null) {
            return vectorResult;
        }
        return super.emitConditionalMove(cmpKind, left, right, cond, unorderedIsTrue, trueValue, falseValue);
    }

    @Override
    public Value emitConstant(LIRKind kind, Constant constant) {
        int length = kind.getPlatformKind().getVectorLength();
        if (length == 1) {
            return super.emitConstant(kind, constant);
        } else if (constant instanceof SimdConstant simd) {
            /*
             * JVMCI doesn't have a 16-bit vector kind. Two-byte constants are assigned a 32-bit
             * kind instead.
             */
            boolean specialCaseTwoBytes = kind.getPlatformKind().equals(AMD64Kind.V32_BYTE) && simd.getSerializedSize() == 2;
            assert specialCaseTwoBytes || simd.getVectorLength() == length : constant + " " + length;
            return super.emitConstant(kind, simd);
        } else {
            return super.emitConstant(kind, SimdConstant.broadcast(constant, length));
        }
    }

    @Override
    public Variable emitReverseBytes(Value input) {
        AMD64Kind kind = (AMD64Kind) input.getPlatformKind();
        if (kind.getVectorLength() == 1) {
            return super.emitReverseBytes(input);
        }
        return ((AMD64VectorArithmeticLIRGenerator) arithmeticLIRGen).emitReverseBytes(input);
    }
}
