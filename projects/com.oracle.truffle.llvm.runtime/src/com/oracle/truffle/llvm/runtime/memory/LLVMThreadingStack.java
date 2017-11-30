/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LLVMThreadingStack {

    private final Map<Thread, LLVMStack> threadMap;
    private final ThreadLocal<LLVMStack> stack;
    private final int stackSize;
    private final Thread mainThread;

    public LLVMThreadingStack(Thread mainTread, int stackSize) {
        this.mainThread = mainTread;
        this.stackSize = stackSize;
        this.stack = new ThreadLocal<>();
        this.threadMap = new HashMap<>();
    }

    public LLVMStack getStack() {
        LLVMStack s = getCurrentStack();
        if (s == null) {
            s = createNewStack();
        }
        return s;
    }

    @TruffleBoundary
    private LLVMStack getCurrentStack() {
        return stack.get();
    }

    @TruffleBoundary
    private synchronized LLVMStack createNewStack() {
        LLVMStack s = new LLVMStack(stackSize);
        stack.set(s);
        threadMap.put(Thread.currentThread(), s);
        return s;
    }

    @TruffleBoundary
    public void freeStack(LLVMMemory memory, Thread thread) {
        /*
         * Do not free the main thread: Sulong#disposeThread runs before Sulong#disposeContext,
         * which needs to call destructors that need a SP.
         */
        if (mainThread != Thread.currentThread()) {
            LLVMStack s = threadMap.get(thread);
            free(memory, s);
        }
    }

    private static void free(LLVMMemory memory, LLVMStack s) {
        if (s != null) {
            s.free(memory);
        }
    }

    @TruffleBoundary
    public void freeMainStack(LLVMMemory memory) {
        assert mainThread == Thread.currentThread();
        assert stack.get() == threadMap.get(Thread.currentThread());
        LLVMStack s = threadMap.get(Thread.currentThread());
        free(memory, s);
    }
}
