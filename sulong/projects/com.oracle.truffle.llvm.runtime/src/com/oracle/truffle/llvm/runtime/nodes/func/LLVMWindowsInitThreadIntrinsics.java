/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMContextWindows;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pthread.LLVMThreadException;

public class LLVMWindowsInitThreadIntrinsics {
    public abstract static class InitThreadLock extends LLVMExpressionNode {
        abstract Object execute();

        @Specialization
        @TruffleBoundary
        public Object doLock() {
            getContext().getWindowsContext().getInitThreadLock().lock();
            return null;
        }
    }

    public abstract static class InitThreadUnlock extends LLVMExpressionNode {
        abstract Object execute();

        @Specialization
        @TruffleBoundary
        public Object doUnlock() {
            getContext().getWindowsContext().getInitThreadLock().unlock();
            return null;
        }
    }

    @NodeChild("timeout")
    public abstract static class InitThreadWait extends LLVMExpressionNode {
        abstract Object execute(int timeout);

        @Specialization
        @TruffleBoundary
        public Object doWait(int timeout) {
            try {
                getContext().getWindowsContext().getInitThreadCondition().await(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                throw new LLVMThreadException(this, "Thread interrupted during _Init_thread_wait", ex);
            }
            return null;
        }
    }

    public abstract static class InitThreadNotify extends LLVMExpressionNode {
        abstract Object execute();

        @Specialization
        @TruffleBoundary
        public Object doWait() {
            LLVMContextWindows context = getContext().getWindowsContext();
            Lock lock = context.getInitThreadLock();
            try {
                lock.lock();
                context.getInitThreadCondition().signalAll();
            } finally {
                lock.unlock();
            }

            return null;
        }
    }

}
