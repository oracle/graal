/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code.aarch64;

import java.util.function.Consumer;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.HostedDirectCallTrampolineSupport;
import com.oracle.svm.hosted.code.HostedPatcher;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

@AutomaticallyRegisteredImageSingleton(HostedDirectCallTrampolineSupport.class)
@Platforms(Platform.AARCH64.class)
public class AArch64HostedTrampolineSupport implements HostedDirectCallTrampolineSupport {
    private static final int INSTRUCTION_SIZE = 4 * Byte.BYTES;

    @Override
    public boolean mayNeedTrampolines() {
        return true;
    }

    @Override
    public int getMaxCallDistance() {
        if (SubstrateOptions.UseDirectCallTrampolinesALot.getValue()) {
            return NumUtil.getNbitNumberInt(10);
        } else {
            return NumUtil.getNbitNumberInt(27);
        }
    }

    @Override
    public int getTrampolineSize() {
        int numInstructions = 3;
        return numInstructions * INSTRUCTION_SIZE;
    }

    @Override
    public int getTrampolineAlignment() {
        return INSTRUCTION_SIZE;
    }

    /**
     * AArch64 trampolines consists of the call sequence below.
     *
     * <pre>
     *     adrp reg, #target // load (target address & ~0xFFF)
     *     add reg, reg, #target // load lower 12-bits of target address
     *     jmp reg // jump to target
     * </pre>
     */
    @Override
    public byte[] createTrampoline(TargetDescription td, HostedMethod target, int trampolineStart) {
        AArch64MacroAssembler masm = new AArch64MacroAssembler(td);
        CompilationResult compilationResult = new CompilationResult("trampoline");
        Consumer<Assembler.CodeAnnotation> consumer = PatchConsumerFactory.HostedPatchConsumerFactory.factory().newConsumer(compilationResult);
        masm.setCodePatchingAnnotationConsumer(consumer);

        try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
            Register reg = sc.getRegister();
            masm.adrpAdd(reg);
            masm.jmp(reg);
        }

        byte[] code = masm.close(true);

        VMError.guarantee(compilationResult.getCodeAnnotations().size() == 1);
        HostedPatcher patcher = (HostedPatcher) compilationResult.getCodeAnnotations().get(0);
        int relativeAddress = target.getCodeAddressOffset() - trampolineStart;
        patcher.patch(trampolineStart, relativeAddress, code);

        return code;
    }
}
