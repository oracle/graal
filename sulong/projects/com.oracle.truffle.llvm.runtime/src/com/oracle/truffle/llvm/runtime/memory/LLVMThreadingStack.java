/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

/**
 * Holds the (lazily allocated) stacks of all threads that are active in one particular LLVMContext.
 */
public final class LLVMThreadingStack {

    private final long stackSize;
    private final Thread mainThread;
    @CompilationFinal private LLVMStack mainThreadStack;

    public LLVMThreadingStack(Thread mainTread, long stackSize) {
        this.mainThread = mainTread;
        this.stackSize = stackSize;
    }

    public LLVMStack getStack() {
        LLVMStack s = getCurrentStack();
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    public LLVMStack getStackProfiled(Thread thread, ConditionProfile profile) {
        if (profile.profile(thread == mainThread)) {
            assert mainThreadStack != null;
            return mainThreadStack;
        }
        LLVMStack s = getCurrentStack();
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    @TruffleBoundary
    private static LLVMStack getCurrentStack() {
        return LLVMLanguage.get(null).contextThreadLocal.get(Thread.currentThread()).getLLVMStack();
    }

    @TruffleBoundary
    private LLVMStack createNewStack() {
        LLVMStack s = new LLVMStack(stackSize, LLVMLanguage.getContext());
        Thread currentThread = Thread.currentThread();
        if (currentThread == mainThread) {
            mainThreadStack = s;
        }
        LLVMLanguage.get(null).contextThreadLocal.get(currentThread).setLLVMStack(s);
        return s;
    }

    @TruffleBoundary
    public static void freeStack(LLVMMemory memory, Thread thread) {
        free(memory, thread);
    }

    private static void free(LLVMMemory memory, Thread thread) {
        LLVMThreadLocalValue value = LLVMLanguage.get(null).contextThreadLocal.get(thread);
        assert value != null;
        LLVMStack s = value.removeLLVMStack();
        if (s != null) {
            s.free(memory);
        }
    }
}
