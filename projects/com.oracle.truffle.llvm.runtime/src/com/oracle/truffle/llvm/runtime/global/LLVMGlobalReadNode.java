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
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public final class LLVMGlobalReadNode extends LLVMNode {

    private final ConditionProfile condition = ConditionProfile.createBinaryProfile();
    @CompilationFinal private ContextReference<LLVMContext> contextRef;

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

    public static LLVMGlobalReadNode createRead() {
        return new LLVMGlobalReadNode();
    }

    private LLVMContext getContext() {
        if (contextRef == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            contextRef = LLVMLanguage.getLLVMContextReference();
        }
        return contextRef.get();
    }

    public Object get(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNative(getMemory(), getContext());
        } else {
            return global.getFrame(getContext());
        }
    }

    public boolean getI1(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeI1(getMemory(), getContext());
        } else {
            return global.getFrameI1(getMemory(), getContext());
        }
    }

    public byte getI8(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeI8(getMemory(), getContext());
        } else {
            return global.getFrameI8(getMemory(), getContext());
        }
    }

    public short getI16(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeI16(getMemory(), getContext());
        } else {
            return global.getFrameI16(getMemory(), getContext());
        }
    }

    public int getI32(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeI32(getMemory(), getContext());
        } else {
            return global.getFrameI32(getMemory(), getContext());
        }
    }

    public long getI64(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeI64(getMemory(), getContext());
        } else {
            return global.getFrameI64(getMemory(), getContext());
        }
    }

    public float getFloat(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeFloat(getMemory(), getContext());
        } else {
            return global.getFrameFloat(getMemory(), getContext());
        }
    }

    public double getDouble(LLVMGlobal global) {
        if (condition.profile(global.isNative(getContext()))) {
            return global.getNativeDouble(getMemory(), getContext());
        } else {
            return global.getFrameDouble(getMemory(), getContext());
        }
    }

}
