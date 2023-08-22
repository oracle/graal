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
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.FALSE;
import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMRelocationIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSectionIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSymbolIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;

/**
 * LLVM target-specific inline assembly snippets and information.
 */
public interface LLVMTargetSpecific {
    static LLVMTargetSpecific get() {
        return ImageSingletons.lookup(LLVMTargetSpecific.class);
    }

    /**
     * Snippet that gets the value of an arbitrary register.
     */
    String getRegisterInlineAsm(String register);

    /**
     * Snippet that sets the value of an arbitrary register.
     */
    String setRegisterInlineAsm(String register);

    /**
     * Snippet that jumps to a runtime-computed address.
     */
    String getJumpInlineAsm();

    /**
     * Snippet that loads a value in a register.
     */
    String getLoadInlineAsm(String inputRegister, int offset);

    /**
     * Snippet that adds two registers and save the result in one of them.
     */
    String getAddInlineAssembly(String outputRegisterName, String inputRegisterName);

    /**
     * Snippet representing a nop instruction.
     */
    String getNopInlineAssembly();

    /**
     * Name of the architecture to be passed to the LLVM compiler.
     */
    String getLLVMArchName();

    /**
     * Number of bytes separating two adjacent call frames. A call frame starts at the stack pointer
     * and its size is as given by the LLVM stack map.
     */
    int getCallFrameSeparation();

    /**
     * Offset of the frame pointer relative to the first address outside the current call frame.
     * This offset should be negative.
     */
    int getFramePointerOffset();

    /**
     * Register number of the stack pointer used by the LLVM stack maps.
     */
    int getStackPointerDwarfRegNum();

    /**
     * Register number of the frame pointer used by the LLVM stack maps.
     */
    int getFramePointerDwarfRegNum();

    /**
     * Offset between the stack pointer of the caller and the frame pointer of the callee.
     */
    long getCallerSPOffset();

    /**
     * Additional target-specific options to be passed to the LLVM compiler.
     */
    default List<String> getLLCAdditionalOptions() {
        return Collections.emptyList();
    }

    /**
     * Transformation to be applied to the name of a register given by Graal to obtain the
     * corresponding name in assembly.
     */
    default String getLLVMRegisterName(String register) {
        return register;
    }

    /**
     * A scratch register of the architecture.
     */
    String getScratchRegister();

    /**
     * Condition for adding section in sections info to avoid duplicates.
     */
    default boolean isSymbolValid(@SuppressWarnings("unused") String symbol) {
        return true;
    }

    /**
     * Extracts the instruction offset from the Stack Map section.
     */
    default int getInstructionOffset(ByteBuffer buffer, int offset, @SuppressWarnings("unused") LLVMSectionIteratorRef relocationsSectionIteratorRef,
                    @SuppressWarnings("unused") LLVMRelocationIteratorRef relocationIteratorRef) {
        return buffer.getInt(offset);
    }

    /**
     * String representing the target for compilation.
     */
    default String getTargetTriple() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            return "-unknown-darwin";
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return "-unknown-linux-gnu";
        } else {
            throw shouldNotReachHere("Unexpected target for LLVM backend: " + ImageSingletons.lookup(Platform.class).toString());
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
class LLVMAMD64TargetSpecificFeature implements InternalFeature {
    private static final int AMD64_RSP_IDX = 7;
    private static final int AMD64_RBP_IDX = 6;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMTargetSpecific() {
            @Override
            public String getRegisterInlineAsm(String register) {
                return "movq %" + register + ", $0";
            }

            @Override
            public String setRegisterInlineAsm(String register) {
                return "movq $0, %" + register;
            }

            @Override
            public String getJumpInlineAsm() {
                return "jmpq *$0";
            }

            @Override
            public String getLoadInlineAsm(String inputRegister, int offset) {
                return "movq " + offset + "(%" + inputRegister + "), $0";
            }

            @Override
            public String getAddInlineAssembly(String outputRegister, String inputRegister) {
                return "addq %" + inputRegister + ", %" + outputRegister;
            }

            @Override
            public String getNopInlineAssembly() {
                return "nop";
            }

            @Override
            public String getLLVMArchName() {
                return "x86-64";
            }

            /*
             * The return address is pushed to the stack just before each call, but is not part of
             * the stack frame of the callee. It is therefore not accounted for in either call
             * frame.
             */
            @Override
            public int getCallFrameSeparation() {
                return FrameAccess.returnAddressSize();
            }

            /*
             * The frame pointer is stored as the first element on the stack, just below the return
             * address.
             */
            @Override
            public int getFramePointerOffset() {
                return -FrameAccess.wordSize();
            }

            @Override
            public long getCallerSPOffset() {
                return 2L * FrameAccess.wordSize();
            }

            @Override
            public int getStackPointerDwarfRegNum() {
                return AMD64_RSP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AMD64_RBP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                List<String> list = new ArrayList<>();
                list.add("-no-x86-call-frame-opt");
                if (Platform.includedIn(Platform.IOS.class)) {
                    list.add("-mtriple=x86_64-ios");
                }
                return list;
            }

            @Override
            public String getScratchRegister() {
                return "rax";
            }

            @Override
            public String getTargetTriple() {
                return "x86_64" + LLVMTargetSpecific.super.getTargetTriple();
            }
        });
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AARCH64.class)
class LLVMAArch64TargetSpecificFeature implements InternalFeature {
    private static final int AARCH64_FP_IDX = 29;
    private static final int AARCH64_SP_IDX = 31;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMTargetSpecific() {
            @Override
            public String getRegisterInlineAsm(String register) {
                return "MOV $0, " + getLLVMRegisterName(register);
            }

            @Override
            public String setRegisterInlineAsm(String register) {
                return "MOV " + getLLVMRegisterName(register) + ", $0";
            }

            @Override
            public String getJumpInlineAsm() {
                return "BR $0";
            }

            @Override
            public String getLoadInlineAsm(String inputRegister, int offset) {
                return "LDR $0, [" + getLLVMRegisterName(inputRegister) + ", #" + offset + "]";
            }

            @Override
            public String getAddInlineAssembly(String outputRegister, String inputRegister) {
                return "ADD " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(inputRegister);
            }

            @Override
            public String getNopInlineAssembly() {
                return "NOP";
            }

            @Override
            public String getLLVMArchName() {
                return "aarch64";
            }

            /*
             * The return address is not saved on the stack on ARM, so the stack frames have no
             * space inbetween them.
             */
            @Override
            public int getCallFrameSeparation() {
                return 0;
            }

            /*
             * The frame pointer is stored below the saved value for the link register.
             */
            @Override
            public int getFramePointerOffset() {
                return -2 * FrameAccess.wordSize();
            }

            @Override
            public long getCallerSPOffset() {
                return 2L * FrameAccess.wordSize();
            }

            @Override
            public int getStackPointerDwarfRegNum() {
                return AARCH64_SP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AARCH64_FP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                List<String> list = new ArrayList<>();
                list.add("--frame-pointer=all");
                list.add("--aarch64-frame-record-on-top");
                if (Platform.includedIn(Platform.IOS.class)) {
                    list.add("-mtriple=arm64-ios");
                }
                return list;
            }

            @Override
            public String getLLVMRegisterName(String register) {
                return register.replace("r", "x");
            }

            @Override
            public String getScratchRegister() {
                return "x16";
            }

            @Override
            public String getTargetTriple() {
                return "arm64" + LLVMTargetSpecific.super.getTargetTriple();
            }
        });
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.RISCV64.class)
class LLVMRISCV64TargetSpecificFeature implements InternalFeature {
    private static final int RISCV64_FP_IDX = 8;
    private static final int RISCV64_SP_IDX = 2;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMTargetSpecific() {
            @Override
            public String getRegisterInlineAsm(String register) {
                return "mv $0, " + getLLVMRegisterName(register);
            }

            @Override
            public String setRegisterInlineAsm(String register) {
                return "mv " + getLLVMRegisterName(register) + ", $0";
            }

            @Override
            public String getJumpInlineAsm() {
                return "jr $0";
            }

            @Override
            public String getLoadInlineAsm(String inputRegister, int offset) {
                return "ld $0, " + offset + "(" + getLLVMRegisterName(inputRegister) + ")";
            }

            @Override
            public String getAddInlineAssembly(String outputRegister, String inputRegister) {
                return "add " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(inputRegister);
            }

            @Override
            public String getNopInlineAssembly() {
                return "nop";
            }

            @Override
            public String getLLVMArchName() {
                return "riscv64";
            }

            /*
             * All data push on the stack is in the call frame
             */
            @Override
            public int getCallFrameSeparation() {
                return 0;
            }

            /*
             * The frame pointer is stored below the saved value for the return register.
             */
            @Override
            public int getFramePointerOffset() {
                return 0;
            }

            @Override
            public long getCallerSPOffset() {
                return 0;
            }

            @Override
            public int getStackPointerDwarfRegNum() {
                return RISCV64_SP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return RISCV64_FP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                List<String> list = new ArrayList<>();
                list.add("--frame-pointer=all");
                list.add("-mattr=+c,+d");
                list.add("-target-abi=lp64d");
                return list;
            }

            @Override
            public String getScratchRegister() {
                return "x5";
            }

            /*
             * When compiling for RISC-V, llc produces labels in the intermediate files, which we
             * must remove when we parse the code at the linking step.
             */
            @Override
            public boolean isSymbolValid(String section) {
                return !section.isEmpty() && !section.startsWith(".LBB") && !section.startsWith(".Lpcrel_hi");
            }

            /*
             * We have the use the relocations to parse the instruction offset in RISC-V, as the
             * offset is 0 otherwise, due to RISC-V specific relocations.
             */
            @Override
            public int getInstructionOffset(ByteBuffer buffer, int offset, LLVMSectionIteratorRef relocationsSectionIteratorRef, LLVMRelocationIteratorRef relocationIteratorRef) {
                while (LLVM.LLVMIsRelocationIteratorAtEnd(relocationsSectionIteratorRef, relocationIteratorRef) == FALSE && offset != LLVM.LLVMGetRelocationOffset(relocationIteratorRef)) {
                    LLVM.LLVMMoveToNextRelocation(relocationIteratorRef);
                }
                if (offset == LLVM.LLVMGetRelocationOffset(relocationIteratorRef)) {
                    LLVMSymbolIteratorRef firstSymbol = LLVM.LLVMGetRelocationSymbol(relocationIteratorRef);
                    LLVM.LLVMMoveToNextRelocation(relocationIteratorRef);
                    assert offset == LLVM.LLVMGetRelocationOffset(relocationIteratorRef);
                    LLVMSymbolIteratorRef secondSymbol = LLVM.LLVMGetRelocationSymbol(relocationIteratorRef);
                    return (int) (LLVM.LLVMGetSymbolAddress(firstSymbol) - LLVM.LLVMGetSymbolAddress(secondSymbol));
                } else {
                    throw shouldNotReachHere("Stack map has no relocation for offset " + offset);
                }
            }

            @Override
            public String getTargetTriple() {
                return "riscv64" + LLVMTargetSpecific.super.getTargetTriple();
            }
        });
    }
}
