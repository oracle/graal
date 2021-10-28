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
import org.graalvm.word.Pointer;

import com.oracle.svm.core.CalleeSavedRegisters;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.RegisterDumper;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.amd64.AMD64CPUFeatureAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.log.Log;
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

    @Override
    public void dumpRegisters(Log log, Pointer callerSP, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        log.string("Callee saved registers (sp=").zhex(callerSP).string(")").indent(true);
        /*
         * The loop to print all registers is manually unrolled so that the register order is
         * defined, and also so that the lookup of the "offset in frame" can be constant folded at
         * image build time using a @Fold method.
         */
        dumpReg(log, "RAX ", callerSP, offsetInFrameOrNull(AMD64.rax), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBX ", callerSP, offsetInFrameOrNull(AMD64.rbx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RCX ", callerSP, offsetInFrameOrNull(AMD64.rcx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDX ", callerSP, offsetInFrameOrNull(AMD64.rdx), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RBP ", callerSP, offsetInFrameOrNull(AMD64.rbp), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSI ", callerSP, offsetInFrameOrNull(AMD64.rsi), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RDI ", callerSP, offsetInFrameOrNull(AMD64.rdi), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "RSP ", callerSP, offsetInFrameOrNull(AMD64.rsp), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R8  ", callerSP, offsetInFrameOrNull(AMD64.r8), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R9  ", callerSP, offsetInFrameOrNull(AMD64.r9), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R10 ", callerSP, offsetInFrameOrNull(AMD64.r10), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R11 ", callerSP, offsetInFrameOrNull(AMD64.r11), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R12 ", callerSP, offsetInFrameOrNull(AMD64.r12), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R13 ", callerSP, offsetInFrameOrNull(AMD64.r13), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R14 ", callerSP, offsetInFrameOrNull(AMD64.r14), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        dumpReg(log, "R15 ", callerSP, offsetInFrameOrNull(AMD64.r15), printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        log.indent(false);
    }

    private static void dumpReg(Log log, String label, Pointer callerSP, int offsetInFrameOrNull, boolean printLocationInfo, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (offsetInFrameOrNull != 0) {
            long value = callerSP.readLong(offsetInFrameOrNull);
            RegisterDumper.dumpReg(log, label, value, printLocationInfo, allowJavaHeapAccess, allowUnsafeOperations);
        }
    }

    @Fold
    static int offsetInFrameOrNull(Register register) {
        AMD64CalleeSavedRegisters that = AMD64CalleeSavedRegisters.singleton();
        if (that.calleeSavedRegisters.contains(register)) {
            return that.getOffsetInFrame(register);
        } else {
            return 0;
        }
    }
}
