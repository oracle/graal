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
package com.oracle.svm.hosted.code.aarch64;

import java.util.function.Consumer;

import org.graalvm.compiler.asm.Assembler.CodeAnnotation;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.SingleInstructionAnnotation;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

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

//import static org.graalvm.compiler.asm.aarch64.AArch64Assembler.*;

@AutomaticFeature
@Platforms({Platform.AArch64.class})
class AArch64HostedPatcherFeature implements Feature {
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
                        } else if (annotation instanceof AArch64Assembler.MovSequenceAnnotation) {
                            compilationResult.addAnnotation(new MovSequenceHostedPatcher(annotation.instructionPosition, (AArch64Assembler.MovSequenceAnnotation) annotation));
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

public class SingleInstructionHostedPatcher extends CompilationResult.CodeAnnotation implements HostedPatcher {
    private final SingleInstructionAnnotation annotation;

    public SingleInstructionHostedPatcher(int instructionStartPosition, SingleInstructionAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        int curValue = relative;
        curValue = curValue >> annotation.shift;

        int bitsRemaining = annotation.operandSizeBits;
        int offsetRemaining = annotation.offsetBits;

        for (int i = 0; i < 4; ++i) {
            if (offsetRemaining >= 8) {
                offsetRemaining -= 8;
                continue;
            }

            // non-zero bits set
            int mask = 0;
            for (int j = 0; j < 8; ++j) {
                if (j >= offsetRemaining) {
                    mask |= (1 << j);
                    --bitsRemaining;
                }
                if (bitsRemaining == 0) {
                    break;
                }
            }

            byte patchTarget = code[annotation.instructionPosition + i];
            byte patch = (byte) ((((byte) (curValue & 0xFF)) & mask) << offsetRemaining);
            byte retainedPatchTarget = (byte) (patchTarget & (~mask << offsetRemaining));
            patchTarget = (byte) (retainedPatchTarget | patch);
            code[annotation.instructionPosition + i] = patchTarget;
            curValue = curValue >>> (8 - offsetRemaining);
            offsetRemaining = 0;
        }
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
        int siteOffset = compStart + annotation.instructionPosition;
        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21, 0, Long.valueOf(0), ref);
        siteOffset += 4;
        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADD_ABS_LO12_NC, 0, Long.valueOf(0), ref);
        // relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_GOT_LD_PREL19, 0,
        // Long.valueOf(0), ref);
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

        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21, 0, Long.valueOf(0), ref);
        siteOffset += 4;
        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_LDST64_ABS_LO12_NC, 0, Long.valueOf(0), ref);
    }

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

        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADR_PREL_PG_HI21, 0, Long.valueOf(0), ref);
        siteOffset += 4;
        relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_AARCH64_ADD_ABS_LO12_NC, 0, Long.valueOf(0), ref);
    }

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
    private final AArch64Assembler.MovSequenceAnnotation annotation;

    MovSequenceHostedPatcher(int instructionStartPosition, AArch64Assembler.MovSequenceAnnotation annotation) {
        super(instructionStartPosition);
        this.annotation = annotation;
    }

    @Uninterruptible(reason = ".")
    @Override
    public void patch(int codePos, int relative, byte[] code) {
        int curValue = relative - (4 * annotation.numInstrs); // n 32-bit instrs to patch n 16-bit
                                                              // movs

        int bitsRemaining = annotation.numInstrs * 8;

        for (int i = 0; i < 4 * annotation.numInstrs; i = i + 4) {
            if (bitsRemaining >= 8) {
                code[annotation.instructionPosition + i] = (byte) (curValue & 0xFF);
                bitsRemaining -= 8;
            } else {
                int mask = 0;
                for (int j = 0; j < bitsRemaining; ++j) {
                    mask |= (1 << j);
                }
                code[annotation.instructionPosition + i] = (byte) (((byte) (curValue & mask)) | (code[annotation.instructionPosition] & ~mask));
            }
            curValue = curValue >>> 8;
        }
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
        int siteOffset = compStart + annotation.instructionPosition;
        if (ref instanceof DataSectionReference || ref instanceof CGlobalDataReference) {
            if (annotation.numInstrs == 1) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G0, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            } else if (annotation.numInstrs > 1) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G0_NC, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            }
            if (annotation.numInstrs == 2) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G1, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            } else if (annotation.numInstrs > 2) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G1_NC, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            }
            if (annotation.numInstrs == 3) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G2, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            } else if (annotation.numInstrs > 3) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G2_NC, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            }
            if (annotation.numInstrs == 4) {
                relocs.addRelocation(siteOffset, RelocationKind.AARCH64_R_MOVW_UABS_G3, 2, Long.valueOf(0), ref);
                siteOffset = siteOffset + 4;
            }
        } else if (ref instanceof ConstantReference) {
            relocs.addDirectRelocationWithoutAddend(siteOffset, annotation.numInstrs * 2, ref);
        } else {
            throw VMError.shouldNotReachHere("Unknown type of reference in code");
        }
    }
}
