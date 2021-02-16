/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64Error;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.pthread.LLVMThreadException;
import com.oracle.truffle.llvm.runtime.pthread.PThreadExitException;

public final class LLVMPThreadThreadIntrinsics {

    @NodeChild(type = LLVMExpressionNode.class, value = "thread")
    @NodeChild(type = LLVMExpressionNode.class, value = "startRoutine")
    @NodeChild(type = LLVMExpressionNode.class, value = "arg")
    public abstract static class LLVMPThreadCreate extends LLVMBuiltin {

        @Specialization
        @TruffleBoundary
        protected int doIntrinsic(LLVMPointer thread, LLVMPointer startRoutine, LLVMPointer arg,
                        @Cached LLVMI64StoreNode store,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            LLVMPThreadStart.LLVMPThreadRunnable init = new LLVMPThreadStart.LLVMPThreadRunnable(startRoutine, arg, context, true);
            final Thread t = context.getpThreadContext().createThread(init);
            if (t == null) {
                return LLVMAMD64Error.EAGAIN;
            }
            store.executeWithTarget(thread, t.getId());
            t.start();
            return 0;
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "retval")
    public abstract static class LLVMPThreadExit extends LLVMBuiltin {

        @Specialization
        protected int doIntrinsic(Object returnValue,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            setThreadReturnValue(returnValue, context);
            throw new PThreadExitException();
        }

        @TruffleBoundary
        private static void setThreadReturnValue(Object returnValue, LLVMContext context) {
            context.getpThreadContext().setThreadReturnValue(Thread.currentThread().getId(), returnValue);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "threadId")
    public abstract static class LLVMPThreadJoin extends LLVMBuiltin {

        @Specialization
        @TruffleBoundary
        protected Object doIntrinsic(long threadId,
                        @CachedContext(LLVMLanguage.class) LLVMContext context) {
            final Thread thread = context.getpThreadContext().getThread(threadId);
            if (thread != null) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new LLVMThreadException(this, "Failed to join thread", e);
                }
            }

            return context.getpThreadContext().getThreadReturnValue(threadId);
        }
    }

    public abstract static class LLVMPThreadSelf extends LLVMBuiltin {

        @Specialization
        protected LLVMNativePointer doIntrinsic() {
            return LLVMNativePointer.create(getThreadId());
        }

        @TruffleBoundary
        private static long getThreadId() {
            return Thread.currentThread().getId();
        }
    }
}
