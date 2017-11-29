/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.global;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

public final class LLVMGlobalWriteNode extends LLVMNode {

    private final ConditionProfile condition = ConditionProfile.createBinaryProfile();
    @CompilationFinal private ContextReference<LLVMContext> contextRef;

    public static LLVMGlobalWriteNode createWrite() {
        return new LLVMGlobalWriteNode();
    }

    private LLVMContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = LLVMLanguage.getLLVMContextReference();
        }
        return contextRef.get();
    }

    @CompilationFinal private LLVMMemory memory;
    @CompilationFinal private boolean memoryResolved = false;

    private LLVMMemory getMemory() {
        if (!memoryResolved) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            memory = getLLVMMemory();
            memoryResolved = true;
        }
        return memory;
    }

    public void put(VirtualFrame frame, LLVMGlobal global, Object object, LLVMToNativeNode toNative) {
        if (condition.profile(global.isNative(getContext()))) {
            LLVMAddress address = toNative.executeWithTarget(frame, object);
            global.setNative(getMemory(), getContext(), address);
        } else {
            global.setFrame(getContext(), object);
        }
    }

    public void putI1(LLVMGlobal global, boolean value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeI1(getMemory(), getContext(), value);
        } else {
            global.setFrameI1(getContext(), value);
        }
    }

    public void putI8(LLVMGlobal global, byte value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeI8(getMemory(), getContext(), value);
        } else {
            global.setFrameI8(getContext(), value);
        }
    }

    public void putI16(LLVMGlobal global, short value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeI16(getMemory(), getContext(), value);
        } else {
            global.setFrameI16(getContext(), value);
        }
    }

    public void putI32(LLVMGlobal global, int value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeI32(getMemory(), getContext(), value);
        } else {
            global.setFrameI32(getContext(), value);
        }
    }

    public void putI64(LLVMGlobal global, long value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeI64(getMemory(), getContext(), value);
        } else {
            global.setFrameI64(getContext(), value);
        }
    }

    public void putFloat(LLVMGlobal global, float value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeFloat(getMemory(), getContext(), value);
        } else {
            global.setFrameFloat(getContext(), value);
        }
    }

    public void putDouble(LLVMGlobal global, double value) {
        if (condition.profile(global.isNative(getContext()))) {
            global.setNativeDouble(getMemory(), getContext(), value);
        } else {
            global.setFrameDouble(getContext(), value);
        }
    }

}
