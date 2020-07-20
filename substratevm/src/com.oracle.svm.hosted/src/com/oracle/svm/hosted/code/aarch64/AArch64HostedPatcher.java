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
package com.oracle.svm.hosted.code.aarch64;

import java.util.function.Consumer;

import org.graalvm.compiler.asm.Assembler.CodeAnnotation;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.SingleInstructionAnnotation;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.MovSequenceAnnotation.MovAction;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.objectfile.ObjectFile.RelocationKind;
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
@Platforms({Platform.AARCH64.class})
public class AArch64HostedPatcher implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(PatchConsumerFactory.HostedPatchConsumerFactory.class, new PatchConsumerFactory.HostedPatchConsumerFactory() {
            @Override
            public Consumer<CodeAnnotation> newConsumer(CompilationResult compilationResult) {
                return new Consumer<CodeAnnotation>() {
                    @Override
                    public void accept(CodeAnnotation annotation) {
                        if (annotation instanceof SingleInstructionAnnotation) {
                            compilationResult.addAnnotation(new SingleInstructionHostedPatcher(annotation.instructionPosition, (SingleInstructionAnnotation) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.MovSequenceAnnotation) {
                            compilationResult.addAnnotation(new MovSequenceHostedPatcher(annotation.instructionPosition, (AArch64MacroAssembler.MovSequenceAnnotation) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.AdrpLdrMacroInstruction) {
                            compilationResult.addAnnotation(new AdrpLdrMacroInstructionHostedPatcher((AArch64MacroAssembler.AdrpLdrMacroInstruction) annotation));
                        } else if (annotation instanceof AArch64MacroAssembler.AdrpAddMacroInstruction) {
                            compilationResult.addAnnotation(new AdrpAddMacroInstructionHostedPatcher((AArch64MacroAssembler.AdrpAddMacroInstruction) annotation));
                        }
                    }
                };
            }
        });
    }
}

class SingleInstructionHostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final SingleInstructionAnnotation annotation;

    SingleInstructionHostedPatcher(int instructionStartPosition, SingleInstructionAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
        /*
         * Right now relocations need to have the ability to access a value via a PC-relative 32 bit
         * immediate, which is too big for a single instruction. Instead, either adrp/ldr, adrp/add,
         * or a sequence of moves should be used.
         */
        throw VMError.shouldNotReachHere("Currently relocations must use either adrp/ldp, adrp/add, or a sequence of moves");
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        annotation.patch(codePos, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }
}

class AdrpLdrMacroInstructionHostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final AArch64MacroAssembler.AdrpLdrMacroInstruction macroInstruction;

    AdrpLdrMacroInstructionHostedPatcher(AArch64MacroAssembler.AdrpLdrMacroInstruction macroInstruction) {
        super(macroInstruction.instructionPosition);
        this.macroInstruction = macroInstruction;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
        int siteOffset = compStart + macroInstruction.instructionPosition;

        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21, 4, Long.valueOf(0), ref);
        siteOffset += 4;
        RelocationKind secondRelocation;
        switch (macroInstruction.srcSize) {
            case 64:
                secondRelocation = RelocationKind.AARCH64_R_AARCH64_LDST64_ABS_LO12_NC;
                break;
            case 32:
                secondRelocation = RelocationKind.AARCH64_R_AARCH64_LDST32_ABS_LO12_NC;
                break;
            case 16:
                secondRelocation = RelocationKind.AARCH64_R_AARCH64_LDST16_ABS_LO12_NC;
                break;
            case 8:
                secondRelocation = RelocationKind.AARCH64_R_AARCH64_LDST8_ABS_LO12_NC;
                break;
            default:
                throw VMError.shouldNotReachHere("Unknown macro instruction src size of " + macroInstruction.srcSize);
        }
        relocs.addRelocation(siteOffset, secondRelocation, 4, Long.valueOf(0), ref);
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        macroInstruction.patch(codePos, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

}

class AdrpAddMacroInstructionHostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final AArch64MacroAssembler.AdrpAddMacroInstruction macroInstruction;

    AdrpAddMacroInstructionHostedPatcher(AArch64MacroAssembler.AdrpAddMacroInstruction macroInstruction) {
        super(macroInstruction.instructionPosition);
        this.macroInstruction = macroInstruction;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
        int siteOffset = compStart + macroInstruction.instructionPosition;

        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21, 4, Long.valueOf(0), ref);
        siteOffset += 4;
        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADD_ABS_LO12_NC, 4, Long.valueOf(0), ref);
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        macroInstruction.patch(codePos, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}

class MovSequenceHostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final AArch64MacroAssembler.MovSequenceAnnotation annotation;

    MovSequenceHostedPatcher(int instructionStartPosition, AArch64MacroAssembler.MovSequenceAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Override
    public void relocate(Reference ref, RelocatableBuffer relocs, int compStart) {
        /*
         * The relocation site is the start of some instruction, which is some offset into the
         * method, which is some offset into the text section (a.k.a. code cache). The offset we get
         * out of the RelocationSiteInfo accounts for the method offset, since we pass it the whole
         * method. We add the method start to get the section-relative offset.
         */
        int siteOffset = compStart + annotation.instructionPosition;
        if (ref instanceof DataSectionReference || ref instanceof CGlobalDataReference) {
            /*
             * calculating the last mov index. This is necessary ensure the proper overflow checks
             * occur.
             */
            int lastMovIndex = -1;
            MovAction[] includeSet = annotation.includeSet;
            for (int i = 0; i < includeSet.length; i++) {
                switch (includeSet[i]) {
                    case USED:
                        lastMovIndex = i;
                        break;
                    case SKIPPED:
                        break;
                    case NEGATED:
                        throw VMError.shouldNotReachHere("Negated mov action isn't handled by relocation currently.");
                }
            }

            RelocationKind[] relocations = {RelocationKind.AARCH64_R_MOVW_UABS_G0, RelocationKind.AARCH64_R_MOVW_UABS_G1, RelocationKind.AARCH64_R_MOVW_UABS_G2, RelocationKind.AARCH64_R_MOVW_UABS_G3};
            RelocationKind[] noCheckRelocations = {RelocationKind.AARCH64_R_MOVW_UABS_G0_NC, RelocationKind.AARCH64_R_MOVW_UABS_G1_NC, RelocationKind.AARCH64_R_MOVW_UABS_G2_NC};
            for (int i = 0; i < includeSet.length; i++) {
                if (includeSet[i] == MovAction.SKIPPED) {
                    continue;
                }
                if (i == lastMovIndex) {
                    relocs.addRelocation(siteOffset, relocations[i], 2, Long.valueOf(0), ref);
                } else {
                    relocs.addRelocation(siteOffset, noCheckRelocations[i], 2, Long.valueOf(0), ref);
                }
                siteOffset = siteOffset + 4;
            }
        } else if (ref instanceof ConstantReference) {
            for (MovAction include : annotation.includeSet) {
                if (include != MovAction.USED) {
                    throw VMError.shouldNotReachHere("This mov action isn't handled by relocation currently.");
                }
            }
            relocs.addDirectRelocationWithoutAddend(siteOffset, annotation.includeSet.length * 2, ref);
        } else {
            throw VMError.shouldNotReachHere("Unknown type of reference in code");
        }
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        annotation.patch(codePos, relative, code);
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this;
    }
}
