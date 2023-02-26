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
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.LLVMThreadLocalValue;

/**
 * Holds the (lazily allocated) stacks of all threads that are active in one particular LLVMContext.
 */
public final class LLVMThreadingStack {
    // we are not able to clean up a thread local properly, so we are using a map instead
    private final long stackSize;
    private final Thread mainThread;
    @CompilationFinal private LLVMStack mainThreadStack;

    public LLVMThreadingStack(Thread mainTread, long stackSize) {
        this.mainThread = mainTread;
        this.stackSize = stackSize;
    }

    public LLVMStack getStack(LLVMLanguage language) {
        LLVMStack s = getCurrentStack(language);
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    public LLVMStack getStack(Node node) {
        LLVMStack s = getCurrentStack(node);
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    public LLVMStack getStackProfiled(Thread thread, ConditionProfile profile, Node node) {
        if (profile.profile(thread == mainThread)) {
            assert mainThreadStack != null;
            return mainThreadStack;
        }
        LLVMStack s = getCurrentStack(node);
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    private static LLVMStack getCurrentStack(Node node) {
        return LLVMLanguage.get(node).contextThreadLocal.get().getLLVMStack();
    }

    private static LLVMStack getCurrentStack(LLVMLanguage language) {
        return language.contextThreadLocal.get().getLLVMStack();
    }

    @TruffleBoundary
    private LLVMStack createNewStack() {
        LLVMStack s = new LLVMStack(stackSize, LLVMLanguage.getContext());
        Thread currentThread = Thread.currentThread();
        if (currentThread == mainThread) {
            mainThreadStack = s;
        }
        LLVMLanguage.get(null).contextThreadLocal.get().setLLVMStack(s);
        return s;
    }

    @TruffleBoundary
    public void freeStack(LLVMMemory memory, Thread thread) {
        /*
         * Do not free the stack of the main thread: Sulong#disposeThread runs before
         * Sulong#disposeContext, which needs to call destructors that need a SP.
         */
        if (mainThread != Thread.currentThread()) {
            free(memory, thread);
        }
    }

    @TruffleBoundary
    public void freeMainStack(LLVMMemory memory) {
        mainThreadStack = null;
        free(memory, mainThread);
    }

    private static void free(LLVMMemory memory, Thread thread) {
        ContextThreadLocal<LLVMThreadLocalValue> context = LLVMLanguage.get(null).contextThreadLocal;
        LLVMThreadLocalValue value = context.get(thread);
        if (value != null) {
            LLVMStack s = value.removeLLVMStack();
            if (s != null) {
                s.free(memory);
            }
        }
    }
}
