/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

@GenerateUncached
public abstract class LLVMGetStackFromThreadNode extends LLVMNode {

    public static LLVMGetStackFromThreadNode create() {
        return LLVMGetStackFromThreadNodeGen.create();
    }

    public abstract LLVMStack executeWithTarget(LLVMThreadingStack threadingStack, Thread currentThread);

    protected LLVMStack getStack(LLVMThreadingStack threadingStack, Thread cachedThread) {
        if (Thread.currentThread() == cachedThread) {
            return threadingStack.getStack();
        }
        throw CompilerDirectives.shouldNotReachHere();
    }

    /**
     * @param stack
     * @param currentThread
     * @see #executeWithTarget(LLVMThreadingStack, Thread)
     */
    @Specialization(limit = "3", guards = "currentThread == cachedThread", assumptions = "singleContextAssumption()")
    protected LLVMStack cached(LLVMThreadingStack stack, Thread currentThread,
                    @Cached("currentThread") @SuppressWarnings("unused") Thread cachedThread,
                    @Cached("getStack(stack, cachedThread)") LLVMStack cachedStack) {
        return cachedStack;
    }

    /**
     * @param currentThread
     * @see #executeWithTarget(LLVMThreadingStack, Thread)
     */
    @Specialization(replaces = "cached")
    static LLVMStack generic(LLVMThreadingStack stack, Thread currentThread,
                    @Cached ConditionProfile profile) {
        return stack.getStackProfiled(currentThread, profile);
    }
}
