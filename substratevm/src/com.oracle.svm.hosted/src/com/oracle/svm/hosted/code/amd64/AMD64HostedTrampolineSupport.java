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
package com.oracle.svm.hosted.code.amd64;

import java.util.function.Consumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.HostedDirectCallTrampolineSupport;
import com.oracle.svm.hosted.code.HostedPatcher;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;

@AutomaticallyRegisteredImageSingleton(HostedDirectCallTrampolineSupport.class)
@Platforms(Platform.AMD64.class)
public class AMD64HostedTrampolineSupport implements HostedDirectCallTrampolineSupport, FeatureSingleton, UnsavedSingleton {

    @Override
    public boolean mayNeedTrampolines() {
        return SubstrateOptions.UseDirectCallTrampolinesALot.getValue();
    }

    @Override
    public int getMaxCallDistance() {
        if (SubstrateOptions.UseDirectCallTrampolinesALot.getValue()) {
            return NumUtil.getNbitNumberInt(10);
        } else {
            throw VMError.shouldNotReachHere("AMD64 currently does not need direct call trampolines");
        }
    }

    @Override
    public int getTrampolineSize() {
        if (SubstrateOptions.UseDirectCallTrampolinesALot.getValue()) {
            return 9;
        } else {
            throw VMError.shouldNotReachHere("AMD64 currently does not need direct call trampolines");
        }
    }

    @Override
    public int getTrampolineAlignment() {
        if (SubstrateOptions.UseDirectCallTrampolinesALot.getValue()) {
            return 16;
        } else {
            throw VMError.shouldNotReachHere("AMD64 currently does not need direct call trampolines");
        }
    }

    @Override
    public byte[] createTrampoline(TargetDescription td, HostedMethod target, int trampolineStart) {
        if (!SubstrateOptions.UseDirectCallTrampolinesALot.getValue()) {
            throw VMError.shouldNotReachHere("AMD64 currently does not need direct call trampolines");
        }

        /*
         * Creates a trampoline via using a pc-relative conditional jump which will be
         * unconditionally taken.
         *
         * Note that this is an extremely contrived code pattern; however, since this is only used
         * for testing, it is sufficient to test our trampoline infrastructure.
         *
         * A correct pattern would involve using a caller-saved register to store the target address
         * and then jumping to that address, but this requires more infrastructure to be added.
         */
        AMD64MacroAssembler masm = new AMD64MacroAssembler(td);
        CompilationResult compilationResult = new CompilationResult("trampoline");
        Consumer<Assembler.CodeAnnotation> consumer = PatchConsumerFactory.HostedPatchConsumerFactory.factory().newConsumer(compilationResult);
        masm.setCodePatchingAnnotationConsumer(consumer);

        masm.cmpl(AMD64.r8, AMD64.r8);
        int jccOffset = masm.position();
        masm.jcc(AMD64Assembler.ConditionFlag.Equal);

        byte[] code = masm.close(true);

        VMError.guarantee(compilationResult.getCodeAnnotations().size() == 1);
        HostedPatcher patcher = (HostedPatcher) compilationResult.getCodeAnnotations().get(0);
        int relativeAddress = target.getCodeAddressOffset() - (trampolineStart + jccOffset);
        patcher.patch(trampolineStart, relativeAddress, code);

        return code;
    }
}
