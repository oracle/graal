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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;

public final class LLVMThreadingStack {

    private final Assumption singleThreading = Truffle.getRuntime().createAssumption();
    private final Thread defaultThread;
    private final LLVMStack defaultStack;

    private final ConcurrentHashMap<Long, LLVMStack> threadToStack = new ConcurrentHashMap<>();

    private final ReferenceQueue<Thread> threadsQueue = new ReferenceQueue<>();

    public LLVMThreadingStack() {
        this.defaultThread = Thread.currentThread();
        this.defaultStack = new LLVMStack();
    }

    private class ReferenceWithCleanup extends WeakReference<Thread> {
        private final long threadID;

        ReferenceWithCleanup(Thread thread) {
            super(thread, threadsQueue);
            this.threadID = thread.getId();
        }

        public void cleanUp() {
            LLVMStack stack = threadToStack.get(threadID);
            stack.free();
            threadToStack.remove(threadID);
        }
    }

    private class StackGCThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    ReferenceWithCleanup ref = (ReferenceWithCleanup) threadsQueue.remove();
                    ref.cleanUp();
                } catch (InterruptedException ex) {
                    // ignore
                }
            }
        }
    }

    @SuppressWarnings("unused")
    public synchronized LLVMStack getStack() {
        Thread currentThread = Thread.currentThread();
        if (singleThreading.isValid()) {
            if (currentThread == defaultThread) {
                return defaultStack;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                singleThreading.invalidate();
                LLVMStack newStack = new LLVMStack();
                threadToStack.put(currentThread.getId(), newStack);
                new ReferenceWithCleanup(currentThread);

                Thread stackGC = new Thread(new StackGCThread(), "sulongStackGC");
                stackGC.setDaemon(true);
                stackGC.start();

                return newStack;
            }
        } else {
            if (currentThread == defaultThread) {
                return defaultStack;
            } else if (threadToStack.containsKey(currentThread.getId())) {
                return threadToStack.get(currentThread.getId());
            } else {
                LLVMStack newStack = new LLVMStack();
                threadToStack.put(currentThread.getId(), newStack);
                new ReferenceWithCleanup(currentThread);
                return newStack;
            }
        }
    }

    public synchronized void freeStacks() {
        CompilerAsserts.neverPartOfCompilation();
        defaultStack.free();
        for (LLVMStack s : threadToStack.values()) {
            if (!s.isFreed()) {
                s.free();
            }
        }
    }

}
