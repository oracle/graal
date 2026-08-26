/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.code;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.NumUtil;

import jdk.graal.compiler.api.replacements.Fold;

/* Helper class to set ABI specific data */
public interface InterpreterAccessStubData {
    String REASON_RAW_POINTER = "raw pointer to object";

    /*
     * Maximum number of parameters that can be passed according to 4.3.3 in the JVM spec. Could be
     * optimized, see GR-71907.
     */
    int MAX_ARGUMENT_HANDLES = 255;
    int JNI_LEAVE_STUB_PREFIX_ARGUMENTS = 2;
    int JAVA_LEAVE_STUB_DEOPT_SLOT = 1;
    int AMD64_WINDOWS_NATIVE_SHADOW_ARGUMENTS = 4;

    @Fold
    static int getStackBufferSize() {
        int windowsAMD64ShadowArguments = Platform.includedIn(InternalPlatform.WINDOWS_BASE.class) ? AMD64_WINDOWS_NATIVE_SHADOW_ARGUMENTS : 0;
        int stackBufferSlots = MAX_ARGUMENT_HANDLES + JNI_LEAVE_STUB_PREFIX_ARGUMENTS + JAVA_LEAVE_STUB_DEOPT_SLOT + windowsAMD64ShadowArguments;
        return NumUtil.roundUp(stackBufferSlots * Long.BYTES, SubstrateTarget.singleton().stackAlignment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setSp(Pointer data, Pointer stackBuffer);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setStackSize(Pointer data, int stackSize, boolean saveStackSizeInDeoptSlot);

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    long getGpArgumentAt(int cArgType, Pointer data, int pos);

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    default void setGpArgumentAtOutgoing(int cArgType, Pointer data, int pos, long val) {
        setGpArgumentAt(cArgType, data, pos, val, false);
    }

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    default void setGpArgumentAtOutgoingNative(int cArgType, Pointer data, int pos, long val) {
        setGpArgumentAtNative(cArgType, data, pos, val, false);
    }

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    default void setGpArgumentAtNative(int cArgType, Pointer data, int pos, long val, boolean incoming) {
        setGpArgumentAt(cArgType, data, pos, val, incoming);
    }

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    default void setGpArgumentAtIncoming(int cArgType, Pointer data, int pos, long val) {
        setGpArgumentAt(cArgType, data, pos, val, true);
    }

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    void setGpArgumentAt(int cArgType, Pointer data, int pos, long val, boolean incoming);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getFpArgumentAt(int cArgType, Pointer data, int pos);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setFpArgumentAt(int cArgType, Pointer data, int pos, long val);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    default void setFpArgumentAtNative(int cArgType, Pointer data, int pos, long val) {
        setFpArgumentAt(cArgType, data, pos, val);
    }

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    long getGpReturn(Pointer data);

    @Uninterruptible(reason = REASON_RAW_POINTER, callerMustBe = true)
    void setGpReturn(Pointer data, long gpReturn);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getFpReturn(Pointer data);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setFpReturn(Pointer data, long fpReturn);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getGpResultAt(Pointer data, int index);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getFpResultAt(Pointer data, int index);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int allocateStubDataSize();
}
