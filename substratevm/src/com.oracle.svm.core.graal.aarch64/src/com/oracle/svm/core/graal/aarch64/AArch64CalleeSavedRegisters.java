/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.aarch64;

import static jdk.vm.ci.aarch64.AArch64.CPU;
import static jdk.vm.ci.aarch64.AArch64.SIMD;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;

final class AArch64CalleeSavedRegisters extends CalleeSavedRegisters {

    @Fold
    public static AArch64CalleeSavedRegisters singleton() {
        return (AArch64CalleeSavedRegisters) CalleeSavedRegisters.singleton();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void createAndRegister() {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        SubstrateRegisterConfig registerConfig = new SubstrateAArch64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NORMAL, null, target, SubstrateOptions.PreserveFramePointer.getValue());

        Register frameRegister = registerConfig.getFrameRegister();
        List<Register> calleeSavedRegisters = new ArrayList<>(registerConfig.getAllocatableRegisters().asList());

        /*
         * Even though lr is an allocatable register, it is not possible for this register to be
         * callee saved, as AArch64 call instructions override this register.
         */
        calleeSavedRegisters.remove(AArch64.lr);

        /*
         * Reverse list so that CPU registers are spilled close to the beginning of the frame, i.e.,
         * with a closer-to-0 negative reference map index in the caller frame. That makes the
         * reference map encoding of the caller frame a bit smaller.
         */
        Collections.reverse(calleeSavedRegisters);

        int offset = 0;
        Map<Register, Integer> calleeSavedRegisterOffsets = new HashMap<>();
        for (Register register : calleeSavedRegisters) {
            int regByteSize = register.getRegisterCategory().equals(CPU) ? 8 : 16;
            /*
             * It is beneficial to have the offsets aligned to the respective register size so that
             * scaled addressing modes can be used.
             */
            offset += offset % regByteSize;
            calleeSavedRegisterOffsets.put(register, offset);
            offset += regByteSize;
        }

        int calleeSavedRegistersSizeInBytes = offset;

        int saveAreaOffsetInFrame = -(FrameAccess.returnAddressSize() +
                        FrameAccess.wordSize() + // slot is always reserved for frame pointer
                        calleeSavedRegistersSizeInBytes);

        ImageSingletons.add(CalleeSavedRegisters.class,
                        new AArch64CalleeSavedRegisters(frameRegister, calleeSavedRegisters, calleeSavedRegisterOffsets, calleeSavedRegistersSizeInBytes, saveAreaOffsetInFrame));

    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private AArch64CalleeSavedRegisters(Register frameRegister, List<Register> calleeSavedRegisters, Map<Register, Integer> offsetsInSaveArea, int saveAreaSize, int saveAreaOffsetInFrame) {
        super(frameRegister, calleeSavedRegisters, offsetsInSaveArea, saveAreaSize, saveAreaOffsetInFrame);
    }

    /**
     * Saving all registers.
     */
    public void emitSave(AArch64MacroAssembler masm, int frameSize) {
        try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
            Register scratchReg = scratch.getRegister();
            ScratchRegState scratchState = ScratchRegState.initialize(masm, scratchReg, frameRegister, frameSize + saveAreaOffsetInFrame);
            for (Register register : calleeSavedRegisters) {
                AArch64Address address = calleeSaveAddress(scratchState, register);
                Register.RegisterCategory category = register.getRegisterCategory();
                if (category.equals(CPU)) {
                    masm.str(64, register, address);
                } else {
                    assert category.equals(SIMD);
                    masm.fstr(128, register, address);
                }
            }
        }
    }

    /**
     * Restoring all registers.
     */
    public void emitRestore(AArch64MacroAssembler masm, int frameSize, Register excludedRegister) {
        try (AArch64MacroAssembler.ScratchRegister scratch = masm.getScratchRegister()) {
            Register scratchReg = scratch.getRegister();
            ScratchRegState scratchState = ScratchRegState.initialize(masm, scratchReg, frameRegister, frameSize + saveAreaOffsetInFrame);
            for (Register register : calleeSavedRegisters) {
                if (register.equals(excludedRegister)) {
                    continue;
                }

                AArch64Address address = calleeSaveAddress(scratchState, register);
                if (register.getRegisterCategory().equals(CPU)) {
                    masm.ldr(64, register, address);
                } else {
                    assert register.getRegisterCategory().equals(SIMD);
                    masm.fldr(128, register, address);
                }
            }
        }
    }

    /**
     * Maintains the state of the scratch register. This is necessary to ensure the address offset
     * immediate always can be encoded as a scaled immediate within a pairwise memory operation.
     *
     * Note that although {@link #emitSave(AArch64MacroAssembler, int)} and
     * {@link #emitRestore(AArch64MacroAssembler, int, Register)} emit single load/store operations,
     * within the macro assembler these will be merged into pairwise operations.
     */
    private static final class ScratchRegState {
        /* AArch64 pairwise operations use a 7-bit scaled operation. */
        final int maxUnscaledOffset = NumUtil.getNbitNumberInt(6);

        final Register scratch;
        final AArch64MacroAssembler masm;
        int curScratchOffset;

        private ScratchRegState(AArch64MacroAssembler masm, Register scratch, int initialOffset) {
            this.masm = masm;
            this.scratch = scratch;
            this.curScratchOffset = initialOffset;
        }

        static ScratchRegState initialize(AArch64MacroAssembler masm, Register scratch, Register frameRegister, int saveAreaBase) {
            /* Initially the scratch register is set to be at the bottom of the save area. */
            masm.add(64, scratch, frameRegister, saveAreaBase);
            return new ScratchRegState(masm, scratch, 0);
        }

        /**
         * Based on the current scratch value, calculates the immediate offset to used in the
         * address.
         */
        int getAddressOffset(int regSize, int offset) {
            int offsetFromScratch = offset - curScratchOffset;
            /*
             * Due to the way calleeSavedRegisters are iterated, the offsets requested should be
             * always be positive from the current scratch value.
             */
            assert offsetFromScratch >= 0;
            if (offsetFromScratch > maxUnscaledOffset * regSize / Byte.SIZE) {
                /*
                 * If the offset is too large to fit in the immediate, then update the scratch value
                 * to be this position.
                 */
                masm.add(64, scratch, scratch, offsetFromScratch);
                curScratchOffset = offset;
                return 0;
            } else {
                return offsetFromScratch;
            }
        }
    }

    private AArch64Address calleeSaveAddress(ScratchRegState scratchState, Register register) {
        int size = register.getRegisterCategory().equals(CPU) ? 64 : 128;
        int immOffset = scratchState.getAddressOffset(size, offsetsInSaveArea.get(register));
        return AArch64Address.createImmediateAddress(size, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, scratchState.scratch, immOffset);
    }
}
