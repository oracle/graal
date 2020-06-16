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
package com.oracle.truffle.llvm.runtime.pthread;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading.LLVMPThreadStart;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LLVMPThreadContext {

    // associated context for creating threads
    private final LLVMContext context;

    // the long-key is the thread-id
    private final Object threadLock;
    private final ConcurrentMap<Long, Object> threadReturnValueStorage;
    private final ConcurrentMap<Long, Thread> threadStorage;
    private volatile boolean isCreateThreadAllowed;

    private int pThreadKey;
    private final Object pThreadKeyLock;
    private final ConcurrentMap<Integer, ConcurrentMap<Long, LLVMPointer>> pThreadKeyStorage;
    private final ConcurrentMap<Integer, LLVMPointer> pThreadDestructorStorage;

    private final CallTarget pthreadCallTarget;

    public LLVMPThreadContext(LLVMContext context) {
        this.context = context;

        // pthread storages
        this.threadLock = new Object();
        this.threadReturnValueStorage = new ConcurrentHashMap<>();
        this.threadStorage = new ConcurrentHashMap<>();
        this.pThreadKey = 0;
        this.pThreadKeyLock = new Object();
        this.pThreadKeyStorage = new ConcurrentHashMap<>();
        this.pThreadDestructorStorage = new ConcurrentHashMap<>();
        this.pthreadCallTarget = Truffle.getRuntime().createCallTarget(new LLVMPThreadStart.LLVMPThreadFunctionRootNode(context.getLanguage()));
        this.isCreateThreadAllowed = true;
    }

    @TruffleBoundary
    public void joinAllThreads() {
        final Collection<Thread> threadsToJoin;
        synchronized (threadLock) {
            this.isCreateThreadAllowed = false;
            threadsToJoin = threadStorage.values();
        }
        for (Thread createdThread : threadsToJoin) {
            try {
                createdThread.join();
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }

    public int createPThreadKey(LLVMPointer destructor) {
        synchronized (pThreadKeyLock) {
            // create new key
            pThreadKey++;

            // register the key
            registerPThreadKey(pThreadKey, destructor);

            // return the created key
            return pThreadKey;
        }
    }

    @TruffleBoundary
    private void registerPThreadKey(int key, LLVMPointer destructor) {
        // register destructor with new key
        pThreadDestructorStorage.put(key, destructor);

        // register key storage
        pThreadKeyStorage.put(key, new ConcurrentHashMap<>());
    }

    public int getNumberOfPthreadKeys() {
        return pThreadKey;
    }

    @TruffleBoundary
    public void deletePThreadKey(int keyId) {
        synchronized (pThreadKeyLock) {
            pThreadKeyStorage.remove(keyId);
            pThreadDestructorStorage.remove(keyId);
        }
    }

    @TruffleBoundary
    public LLVMPointer getSpecific(int keyId) {
        final ConcurrentMap<Long, LLVMPointer> value = pThreadKeyStorage.get(keyId);
        if (value != null) {
            final long threadId = Thread.currentThread().getId();
            return value.get(threadId);
        }
        return null;
    }

    @TruffleBoundary
    public boolean setSpecific(int keyId, LLVMPointer value) {
        final ConcurrentMap<Long, LLVMPointer> specificStore = pThreadKeyStorage.get(keyId);
        if (specificStore != null) {
            specificStore.put(Thread.currentThread().getId(), value);
            return true;
        }
        return false;
    }

    @TruffleBoundary
    public LLVMPointer getAndRemoveSpecificUnlessNull(int keyId) {
        final ConcurrentMap<Long, LLVMPointer> value = pThreadKeyStorage.get(keyId);
        if (value != null) {
            final long threadId = Thread.currentThread().getId();
            final LLVMPointer keyMapping = value.get(threadId);
            if (keyMapping != null && !keyMapping.isNull()) {
                value.remove(threadId);
                return keyMapping;
            }
        }
        return null;
    }

    @TruffleBoundary
    public LLVMPointer getDestructor(int keyId) {
        return pThreadDestructorStorage.get(keyId);
    }

    @TruffleBoundary
    public Thread createThread(Runnable runnable) {
        synchronized (threadLock) {
            if (isCreateThreadAllowed) {
                final Thread thread = context.getEnv().createThread(runnable);
                threadStorage.put(thread.getId(), thread);
                return thread;
            } else {
                return null;
            }
        }
    }

    @TruffleBoundary
    public Thread getThread(long threadID) {
        return threadStorage.get(threadID);
    }

    @TruffleBoundary
    public void clearThreadId() {
        threadStorage.remove(Thread.currentThread().getId());
    }

    @TruffleBoundary
    public void setThreadReturnValue(long threadId, Object value) {
        threadReturnValueStorage.put(threadId, value);
    }

    @TruffleBoundary
    public Object getThreadReturnValue(long threadId) {
        return threadReturnValueStorage.get(threadId);
    }

    public CallTarget getPthreadCallTarget() {
        return pthreadCallTarget;
    }
}
