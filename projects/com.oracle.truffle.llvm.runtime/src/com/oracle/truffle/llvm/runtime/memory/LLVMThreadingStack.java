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
import java.util.HashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class LLVMThreadingStack {

    private final Assumption singleThreading = Truffle.getRuntime().createAssumption();
    private final Thread defaultThread;
    private final LLVMStack defaultStack;

    private final HashMap<Long, LLVMStack> threadToStack = new HashMap<>();

    private final ReferenceQueue<Thread> threadsQueue = new ReferenceQueue<>();

    private final int stackSize;

    public LLVMThreadingStack(int stackSize) {
        this.stackSize = stackSize;
        this.defaultThread = Thread.currentThread();
        this.defaultStack = new LLVMStack(stackSize);
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

    public Thread getDefaultThread() {
        return defaultThread;
    }

    public void initializeThread() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        synchronized (this) {
            // recheck under lock as a race condition can still happen
            if (singleThreading.isValid()) {
                singleThreading.invalidate();

                Thread stackGC = new Thread(new StackGCThread(), "sulongStackGC");
                stackGC.setDaemon(true);
                stackGC.start();
            }
        }
    }

    public LLVMStack getStack() {
        if (singleThreading.isValid()) {
            assert Thread.currentThread() == defaultThread;
            return defaultStack;
        } else {
            Thread currentThread = Thread.currentThread();
            if (currentThread == defaultThread) {
                return defaultStack;
            } else {
                synchronized (this) {
                    if (isKnownThread(currentThread)) {
                        return getKnownThread(currentThread);
                    } else {
                        return addNewThread(currentThread);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unused")
    @TruffleBoundary
    private LLVMStack addNewThread(Thread currentThread) {
        LLVMStack newStack = new LLVMStack(stackSize);
        threadToStack.put(currentThread.getId(), newStack);
        new ReferenceWithCleanup(currentThread);
        return newStack;
    }

    @TruffleBoundary
    private LLVMStack getKnownThread(Thread currentThread) {
        return threadToStack.get(currentThread.getId());
    }

    @TruffleBoundary
    private boolean isKnownThread(Thread currentThread) {
        return threadToStack.containsKey(currentThread.getId());
    }

    public void freeStacks() {
        CompilerAsserts.neverPartOfCompilation();
        synchronized (this) {
            defaultStack.free();
            for (LLVMStack s : threadToStack.values()) {
                if (!s.isFreed()) {
                    s.free();
                }
            }
        }
    }

}
