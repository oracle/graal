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
package com.oracle.svm.core.graal.amd64;

import java.util.function.Consumer;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandDataAnnotation;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.code.NativeImagePatcher;
import com.oracle.svm.core.graal.code.PatchConsumerFactory;

@AutomaticFeature
@Platforms({Platform.AMD64.class})
class AMD64NativeImagePatcherFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PatchConsumerFactory.NativePatchConsumerFactory.class, new PatchConsumerFactory.NativePatchConsumerFactory() {
            @Override
            public Consumer<Assembler.CodeAnnotation> newConsumer(CompilationResult compilationResult) {
                return new Consumer<Assembler.CodeAnnotation>() {
                    @Override
                    public void accept(Assembler.CodeAnnotation annotation) {
                        if (annotation instanceof OperandDataAnnotation) {
                            compilationResult.addAnnotation(new AMD64NativeImagePatcher(annotation.instructionPosition, (OperandDataAnnotation) annotation));
                        }
                    }
                };
            }
        });
    }
}

public class AMD64NativeImagePatcher extends CompilationResult.CodeAnnotation implements NativeImagePatcher {
    private final OperandDataAnnotation annotation;

    public AMD64NativeImagePatcher(int instructionStartPosition, OperandDataAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Override
    public void patchCode(int relative, byte[] code) {
        int curValue = relative - (annotation.nextInstructionPosition - annotation.instructionPosition);

        for (int i = 0; i < annotation.operandSize; i++) {
            assert code[annotation.operandPosition + i] == 0;
            code[annotation.operandPosition + i] = (byte) (curValue & 0xFF);
            curValue = curValue >>> 8;
        }
        assert curValue == 0;
    }

    @Override
    public int getOffset() {
        return annotation.operandPosition;
    }

    @Override
    public int getLength() {
        return annotation.operandSize;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }
}
