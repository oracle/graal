/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;

import java.util.concurrent.ConcurrentHashMap;

public final class UtilCConstants {

    private final ConcurrentHashMap<CConstant, Integer> valueMap;
    private final LLVMContext ctx;

    public UtilCConstants(LLVMContext ctx) {
        this.ctx = ctx;
        this.valueMap = new ConcurrentHashMap<>();
    }

    public enum CConstant {
        EBUSY("EBUSY"),
        EDEADLK("EDEADLK"),
        EINVAL("EINVAL"),
        EPERM("EPERM"),
        PTHREAD_MUTEX_DEFAULT("PTHREAD_MUTEX_DEFAULT"),
        PTHREAD_MUTEX_ERRORCHECK("PTHREAD_MUTEX_ERRORCHECK"),
        PTHREAD_MUTEX_NORMAL("PTHREAD_MUTEX_NORMAL"),
        PTHREAD_MUTEX_RECURSIVE("PTHREAD_MUTEX_RECURSIVE");

        public final String value;

        CConstant(String val) {
            this.value = val;
        }
    }

    @TruffleBoundary
    public int getConstant(CConstant constant) {
        if (valueMap.containsKey(constant)) {
            return valueMap.get(constant);
        }
        int value;
        RootCallTarget callTarget = ctx.getGlobalScope().getFunction("__sulong_get" + constant.value).getLLVMIRFunction();
        try (LLVMStack.StackPointer sp = ctx.getThreadingStack().getStack().newFrame()) {
            value = (int) callTarget.call(sp);
        }
        valueMap.put(constant, value);
        return value;
    }
}
