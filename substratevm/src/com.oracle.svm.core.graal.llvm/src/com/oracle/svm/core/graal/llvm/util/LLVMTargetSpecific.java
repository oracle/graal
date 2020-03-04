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

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;

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
     * Snippet that jumps to a runtime-computed address.
     */
    String getJumpInlineAsm();

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
}

@AutomaticFeature
@Platforms(Platform.AMD64.class)
class LLVMAMD64TargetSpecificFeature implements Feature {
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
            public String getJumpInlineAsm() {
                return "jmpq *$0";
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
            public int getStackPointerDwarfRegNum() {
                return AMD64_RSP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AMD64_RBP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                return Collections.singletonList("-no-x86-call-frame-opt");
            }
        });
    }
}

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class LLVMAArch64TargetSpecificFeature implements Feature {
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
            public String getJumpInlineAsm() {
                return "BR $0";
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
            public int getStackPointerDwarfRegNum() {
                return AARCH64_SP_IDX;
            }

            @Override
            public int getFramePointerDwarfRegNum() {
                return AARCH64_FP_IDX;
            }

            @Override
            public List<String> getLLCAdditionalOptions() {
                return Collections.singletonList("--frame-pointer=all");
            }

            @Override
            public String getLLVMRegisterName(String register) {
                return register.replace("r", "x");
            }
        });
    }
}
