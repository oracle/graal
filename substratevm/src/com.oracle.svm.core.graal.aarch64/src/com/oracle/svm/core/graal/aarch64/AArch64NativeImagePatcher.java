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
package com.oracle.svm.core.graal.aarch64;

import java.util.function.Consumer;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.SingleInstructionAnnotation;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.code.NativeImagePatcher;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.util.VMError;

@AutomaticFeature
@Platforms({Platform.AARCH64.class})
public class AArch64NativeImagePatcher implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PatchConsumerFactory.NativePatchConsumerFactory.class, new PatchConsumerFactory.NativePatchConsumerFactory() {
            @Override
            public Consumer<Assembler.CodeAnnotation> newConsumer(CompilationResult compilationResult) {
                return new Consumer<Assembler.CodeAnnotation>() {
                    @Override
                    public void accept(Assembler.CodeAnnotation annotation) {
                        if (annotation instanceof SingleInstructionAnnotation) {
                            compilationResult.addAnnotation(new SingleInstructionNativeImagePatcher(annotation.instructionPosition, (SingleInstructionAnnotation) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.MovSequenceAnnotation) {
                            compilationResult.addAnnotation(new MovSequenceNativeImagePatcher(annotation.instructionPosition, (AArch64MacroAssembler.MovSequenceAnnotation) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.AdrpLdrMacroInstruction) {
                            compilationResult.addAnnotation(new AdrpLdrMacroInstructionNativeImagePatcher((AArch64MacroAssembler.AdrpLdrMacroInstruction) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.AdrpAddMacroInstruction) {
                            compilationResult.addAnnotation(new AdrpAddMacroInstructionNativeImagePatcher((AArch64MacroAssembler.AdrpAddMacroInstruction) annotation));
                        }
                    }
                };
            }
        });
    }
}

class SingleInstructionNativeImagePatcher extends CompilationResult.CodeAnnotation implements NativeImagePatcher {
    private final SingleInstructionAnnotation annotation;

    SingleInstructionNativeImagePatcher(int instructionStartPosition, SingleInstructionAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    /**
     * The position from the beginning of the method where the patch is applied. This offset is used
     * in the reference map.
     */
    @Override
    public int getOffset() {
        return annotation.instructionPosition + annotation.offsetBits;
    }

    /**
     * The length of the value to patch in bytes, e.g., the size of an operand.
     */
    @Override
    public int getLength() {
        assert annotation.operandSizeBits % 8 == 0 : "operandSize is not a byte size";
        return annotation.operandSizeBits / 8;
    }

    @Override
    public void patchCode(int relative, byte[] code) {
        annotation.patch(annotation.instructionPosition, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }
}

class AdrpLdrMacroInstructionNativeImagePatcher extends CompilationResult.CodeAnnotation implements NativeImagePatcher {
    private final AArch64MacroAssembler.AdrpLdrMacroInstruction macroInstruction;

    AdrpLdrMacroInstructionNativeImagePatcher(AArch64MacroAssembler.AdrpLdrMacroInstruction macroInstruction) {
        super(macroInstruction.instructionPosition);
        this.macroInstruction = macroInstruction;
    }

    @Override
    public void patchCode(int relative, byte[] code) {
        macroInstruction.patch(macroInstruction.instructionPosition, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * The position from the beginning of the method where the patch is applied. This offset is used
     * in the reference map.
     */
    @Override
    public int getOffset() {
        throw VMError.unsupportedFeature("trying to get offset of adrp ldr macro instruction");
    }

    /**
     * The length of the value to patch in bytes, e.g., the size of an operand.
     */
    @Override
    public int getLength() {
        throw VMError.unsupportedFeature("trying to get length of adrp ldr macro instruction");
    }
}

class AdrpAddMacroInstructionNativeImagePatcher extends CompilationResult.CodeAnnotation implements NativeImagePatcher {
    private final AArch64MacroAssembler.AdrpAddMacroInstruction macroInstruction;

    AdrpAddMacroInstructionNativeImagePatcher(AArch64MacroAssembler.AdrpAddMacroInstruction macroInstruction) {
        super(macroInstruction.instructionPosition);
        this.macroInstruction = macroInstruction;
    }

    @Override
    public void patchCode(int relative, byte[] code) {
        macroInstruction.patch(macroInstruction.instructionPosition, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * The position from the beginning of the method where the patch is applied. This offset is used
     * in the reference map.
     */
    @Override
    public int getOffset() {
        throw VMError.unsupportedFeature("trying to get offset of adrp add instruction");
    }

    /**
     * The length of the value to patch in bytes, e.g., the size of an operand.
     */
    @Override
    public int getLength() {
        throw VMError.unsupportedFeature("trying to get length of adrp add instruction");
    }
}

class MovSequenceNativeImagePatcher extends CompilationResult.CodeAnnotation implements NativeImagePatcher {
    private final AArch64MacroAssembler.MovSequenceAnnotation annotation;

    MovSequenceNativeImagePatcher(int instructionStartPosition, AArch64MacroAssembler.MovSequenceAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Override
    public void patchCode(int relative, byte[] code) {
        annotation.patch(annotation.instructionPosition, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    /**
     * The position from the beginning of the method where the patch is applied. This offset is used
     * in the reference map.
     */
    @Override
    public int getOffset() {
        throw VMError.unsupportedFeature("trying to get offset of move sequence");
    }

    /**
     * The length of the value to patch in bytes, e.g., the size of an operand.
     */
    @Override
    public int getLength() {
        throw VMError.unsupportedFeature("trying to get length of move sequence");
    }
}
