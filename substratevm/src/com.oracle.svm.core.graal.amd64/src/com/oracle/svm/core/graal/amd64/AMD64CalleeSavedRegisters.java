/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.amd64;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.Register.RegisterCategory;

final class AMD64CalleeSavedRegisters extends CalleeSavedRegisters {

    @Fold
    public static AMD64CalleeSavedRegisters singleton() {
        return (AMD64CalleeSavedRegisters) CalleeSavedRegisters.singleton();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void createAndRegister() {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        SubstrateRegisterConfig registerConfig = new SubstrateAMD64RegisterConfig(SubstrateRegisterConfig.ConfigKind.NORMAL, null, target, SubstrateOptions.PreserveFramePointer.getValue());

        Register frameRegister = registerConfig.getFrameRegister();
        List<Register> calleeSavedRegisters = new ArrayList<>(registerConfig.getAllocatableRegisters().asList());

        /*
         * Reverse list so that CPU registers are spilled close to the beginning of the frame, i.e.,
         * with a closer-to-0 negative reference map index in the caller frame. That makes the
         * reference map encoding of the caller frame a bit smaller.
         */
        Collections.reverse(calleeSavedRegisters);

        int offset = 0;
        Map<Register, Integer> calleeSavedRegisterOffsets = new HashMap<>();
        for (Register register : calleeSavedRegisters) {
            calleeSavedRegisterOffsets.put(register, offset);
            offset += target.arch.getLargestStorableKind(register.getRegisterCategory()).getSizeInBytes();
        }
        int calleeSavedRegistersSizeInBytes = offset;

        int saveAreaOffsetInFrame = -(FrameAccess.returnAddressSize() +
                        (SubstrateOptions.PreserveFramePointer.getValue() ? FrameAccess.wordSize() : 0) +
                        calleeSavedRegistersSizeInBytes);

        ImageSingletons.add(CalleeSavedRegisters.class,
                        new AMD64CalleeSavedRegisters(frameRegister, calleeSavedRegisters, calleeSavedRegisterOffsets, calleeSavedRegistersSizeInBytes, saveAreaOffsetInFrame));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private AMD64CalleeSavedRegisters(Register frameRegister, List<Register> calleeSavedRegisters, Map<Register, Integer> offsetsInSaveArea, int saveAreaSize, int saveAreaOffsetInFrame) {
        super(frameRegister, calleeSavedRegisters, offsetsInSaveArea, saveAreaSize, saveAreaOffsetInFrame);
    }

    /**
     * The increasing different size and number of registers of SSE vs. AVX vs. AVX512 complicates
     * saving and restoring when AOT compilation and JIT compilation use different CPU features: A
     * JIT compiled caller could expect AVX512 registes to be saved, but the AOT compiled callee
     * only saves the SSE registers. Therefore, AOT and JIT compiled code need to have the same CPU
     * features right now. See {@link AMD64CPUFeatureAccess#enableFeatures}.
     */
    public void emitSave(AMD64MacroAssembler asm, int frameSize) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        AMD64 arch = (AMD64) target.arch;
        boolean hasAVX = arch.getFeatures().contains(AMD64.CPUFeature.AVX);
        boolean hasAVX512 = arch.getFeatures().contains(AMD64.CPUFeature.AVX512F);

        for (Register register : calleeSavedRegisters) {
            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(address, register);
            } else if (category.equals(AMD64.XMM)) {
                if (hasAVX512) {
                    asm.evmovdqu64(address, register);
                } else if (hasAVX) {
                    asm.vmovdqu(address, register);
                } else {
                    asm.movdqu(address, register);
                }
            } else if (category.equals(AMD64.MASK)) {
                /* Graal does not use the AVX512 mask registers yet. */
                throw VMError.unimplemented();
            } else {
                throw VMError.shouldNotReachHere();
            }
        }
    }

    public void emitRestore(AMD64MacroAssembler asm, int frameSize, Register excludedRegister) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        AMD64 arch = (AMD64) target.arch;
        boolean hasAVX = arch.getFeatures().contains(AMD64.CPUFeature.AVX);
        boolean hasAVX512 = arch.getFeatures().contains(AMD64.CPUFeature.AVX512F);

        for (Register register : calleeSavedRegisters) {
            if (register.equals(excludedRegister)) {
                continue;
            }

            AMD64Address address = calleeSaveAddress(asm, frameSize, register);
            RegisterCategory category = register.getRegisterCategory();
            if (category.equals(AMD64.CPU)) {
                asm.movq(register, address);
            } else if (category.equals(AMD64.XMM)) {
                if (hasAVX512) {
                    asm.evmovdqu64(register, address);
                } else if (hasAVX) {
                    asm.vmovdqu(register, address);
                } else {
                    asm.movdqu(register, address);
                }
            } else if (category.equals(AMD64.MASK)) {
                throw VMError.unimplemented();
            } else {
                throw VMError.shouldNotReachHere();
            }
        }
    }

    private AMD64Address calleeSaveAddress(AMD64MacroAssembler asm, int frameSize, Register register) {
        return asm.makeAddress(frameRegister, frameSize + getOffsetInFrame(register));
    }
}
