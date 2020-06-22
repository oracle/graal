/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.asm.Assembler.CodeAnnotation;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandDataAnnotation;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.graal.code.CGlobalDataReference;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.HostedPatcher;
import com.oracle.svm.hosted.image.RelocatableBuffer;

import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataSectionReference;
import jdk.vm.ci.code.site.Reference;

@AutomaticFeature
@Platforms({Platform.AMD64.class})
class AMD64HostedPatcherFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PatchConsumerFactory.HostedPatchConsumerFactory.class, new PatchConsumerFactory.HostedPatchConsumerFactory() {
            @Override
            public Consumer<CodeAnnotation> newConsumer(CompilationResult compilationResult) {
                return new Consumer<CodeAnnotation>() {
                    @Override
                    public void accept(CodeAnnotation annotation) {
                        if (annotation instanceof OperandDataAnnotation) {
                            compilationResult.addAnnotation(new AMD64HostedPatcher(annotation.instructionPosition, (OperandDataAnnotation) annotation));
                        }
                    }
                };
            }
        });
    }
}

public class AMD64HostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final OperandDataAnnotation annotation;

    public AMD64HostedPatcher(int instructionStartPosition, OperandDataAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        int curValue = relative - (annotation.nextInstructionPosition - annotation.instructionPosition);

        for (int i = 0; i < annotation.operandSize; i++) {
            assert code[annotation.operandPosition + i] == 0;
            code[annotation.operandPosition + i] = (byte) (curValue & 0xFF);
            curValue = curValue >>> 8;
        }
        assert curValue == 0;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
        /*
         * The relocation site is some offset into the instruction, which is some offset into the
         * method, which is some offset into the text section (a.k.a. code cache). The offset we get
         * out of the RelocationSiteInfo accounts for the first two, since we pass it the whole
         * method. We add the method start to get the section-relative offset.
         */
        long siteOffset = compStart + annotation.operandPosition;
        if (ref instanceof DataSectionReference || ref instanceof CGlobalDataReference) {
            /*
             * Do we have an addend? Yes; it's constStart. BUT x86/x86-64 PC-relative references are
             * relative to the *next* instruction. So, if the next instruction starts n bytes from
             * the relocation site, we want to subtract n bytes from our addend.
             */
            long addend = (annotation.nextInstructionPosition - annotation.operandPosition);
            relocs.addPCRelativeRelocationWithAddend((int) siteOffset, annotation.operandSize, addend, ref);
        } else if (ref instanceof ConstantReference) {
            assert SubstrateOptions.SpawnIsolates.getValue() : "Inlined object references must be base-relative";
            relocs.addDirectRelocationWithoutAddend((int) siteOffset, annotation.operandSize, ref);
        } else {
            throw VMError.shouldNotReachHere("Unknown type of reference in code");
        }
    }
}
