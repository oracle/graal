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
package com.oracle.svm.core;

import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMapBuilder;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.Register;

/**
 * This class provides support for callee-saved registers to the rest of the VM.
 *
 * For normal Java-to-Java calls, all registers are caller saved. That simplifies many things
 * related to stack walking because caller and callee frames are independent: all values in the
 * caller frame at a call site are spilled at a location known when compiling the caller frame.
 *
 * But for slow-path stub calls, caller-saved registers have severe performance implications: all
 * registers must be spilled at the call site. This means every loop has at least one call (the
 * safepoint check) where all values are spilled. This degrades code quality. For stub calls,
 * therefore all registers should be callee saved. This means that for all practical use cases
 * either none or all registers are callee saved, which makes the implementation simpler.
 *
 * For all methods with callee-saved registers, the memory area where the callee saves the register
 * to is located at the {@link #saveAreaOffsetInFrame same offset} relative to the caller frame, and
 * has the {@link #saveAreaSize same size}. This ensures that caller and callee are still
 * independent: the caller knows at which offset (relative to the stack pointer) a register is saved
 * by the callee. This is a {@link #getOffsetInFrame negative offset} relative to the caller frame's
 * stack pointer.
 *
 * Callee-saved registers must be supported in reference maps for the GC, and in deoptimization
 * information. Because of the fixed offsets, there is no difference between a caller-spilled value
 * (a stack slot with a positive offset relative to the caller frame's stack pointer) and a
 * callee-saved value (a negative offset relative to the caller frame's stack pointer). So as long
 * as reference maps and deoptimization information support negative stack slot offsets, no special
 * handling for callee saved registers is necessary. Both
 * {@link SubstrateReferenceMapBuilder#addLiveValue} and {@link FrameInfoEncoder} use
 * {@link #getOffsetInFrame} to look up the callee-save offset for a register.
 */
public class CalleeSavedRegisters {

    @Fold
    public static boolean supportedByPlatform() {
        return SubstrateOptions.UseCalleeSavedRegisters.getValue() && ImageSingletons.contains(CalleeSavedRegisters.class);
    }

    @Fold
    public static CalleeSavedRegisters singleton() {
        return ImageSingletons.lookup(CalleeSavedRegisters.class);
    }

    protected final Register frameRegister;
    protected final List<Register> calleeSavedRegisters;
    protected final Map<Register, Integer> offsetsInSaveArea;
    protected final int saveAreaSize;
    protected final int saveAreaOffsetInFrame;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CalleeSavedRegisters(Register frameRegister, List<Register> calleeSavedRegisters, Map<Register, Integer> offsetsInSaveArea, int saveAreaSize, int saveAreaOffsetInFrame) {
        this.frameRegister = frameRegister;
        this.calleeSavedRegisters = calleeSavedRegisters;
        this.offsetsInSaveArea = offsetsInSaveArea;
        this.saveAreaSize = saveAreaSize;
        this.saveAreaOffsetInFrame = saveAreaOffsetInFrame;
    }

    public void verifySaveAreaOffsetInFrame(int checkedSaveAreaOffsetInFrame) {
        VMError.guarantee(saveAreaOffsetInFrame == checkedSaveAreaOffsetInFrame, "Must have a single value for the callee save register area");
    }

    public int getSaveAreaSize() {
        return saveAreaSize;
    }

    public int getOffsetInFrame(Register register) {
        int result = saveAreaOffsetInFrame + offsetsInSaveArea.get(register);
        assert result < 0 : "Note that the offset of a callee save register is negative, because it is located in the callee frame";
        return result;
    }
}
