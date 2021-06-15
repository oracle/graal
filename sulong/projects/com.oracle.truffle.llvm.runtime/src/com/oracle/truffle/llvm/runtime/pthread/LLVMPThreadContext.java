/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.multithreading.LLVMPThreadStart;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public final class LLVMPThreadContext {

    // associated context for creating threads
    private final TruffleLanguage.Env env;

    // the long-key is the thread-id
    private final Object threadLock;

    /**
     * At pthread_join, return values shall be cleared from this map of return values.
     */
    private final ConcurrentMap<Long, Object> threadReturnValueStorage;

    /**
     * See doc on TruffleLanguage.initializeThread(Object, Thread).
     * <p>
     * When a thread is created with pthread_create and it completes execution, there are no more
     * references held to the thread object and the GC will free it. It might happen that at some
     * later point the user calls pthread_join on the thread but by that time there are no
     * references held to the thread object anymore, so it cannot be joined on. If that is the case,
     * the thread must have already terminated, and the join does not need to wait. The return value
     * of the thread is stored in a separate map as well, so any reference to the thread object is
     * really not needed anymore.
     */
    private final ConcurrentMap<Long, WeakReference<Thread>> threadStorage;
    private volatile boolean isCreateThreadAllowed;

    private int pThreadKey;
    private final Object pThreadKeyLock;
    private final ConcurrentMap<Integer, ConcurrentMap<Long, LLVMPointer>> pThreadKeyStorage;
    private final ConcurrentMap<Integer, LLVMPointer> pThreadDestructorStorage;

    private final CallTarget pthreadCallTarget;

    public LLVMPThreadContext(TruffleLanguage.Env env, LLVMLanguage language, DataLayout dataLayout) {
        this.env = env;

        // pthread storages
        this.threadLock = new Object();
        this.threadReturnValueStorage = new ConcurrentHashMap<>();
        this.threadStorage = new ConcurrentHashMap<>();
        this.pThreadKey = 0;
        this.pThreadKeyLock = new Object();
        this.pThreadKeyStorage = new ConcurrentHashMap<>();
        this.pThreadDestructorStorage = new ConcurrentHashMap<>();

        this.pthreadCallTarget = language.createCachedCallTarget(LLVMPThreadStart.LLVMPThreadFunctionRootNode.class,
                        l -> LLVMPThreadStart.LLVMPThreadFunctionRootNode.create(l, l.getActiveConfiguration().createNodeFactory(l, dataLayout)));
        this.isCreateThreadAllowed = true;
    }

    @TruffleBoundary
    public void joinAllThreads() {
        final Collection<WeakReference<Thread>> threads;

        synchronized (threadLock) {
            this.isCreateThreadAllowed = false;
            threads = threadStorage.values();
        }

        for (WeakReference<Thread> thread : threads) {
            try {
                Thread t = thread.get();
                if (t != null) {
                    t.join();
                }
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
                final Thread thread = env.createThread(runnable);
                threadStorage.put(thread.getId(), new WeakReference<>(thread));
                return thread;
            } else {
                return null;
            }
        }
    }

    @TruffleBoundary
    public Thread getThread(long threadID) {
        WeakReference<Thread> thread = threadStorage.get(threadID);
        if (thread == null) {
            return null;
        }
        return thread.get();
    }

    @TruffleBoundary
    public void clearThreadID(long threadID) {
        threadStorage.remove(threadID);
    }

    @TruffleBoundary
    public void setThreadReturnValue(long threadID, Object value) {
        threadReturnValueStorage.put(threadID, value);
    }

    @TruffleBoundary
    public Object getThreadReturnValue(long threadID) {
        return threadReturnValueStorage.get(threadID);
    }

    @TruffleBoundary
    public void clearThreadReturnValue(long threadID) {
        threadReturnValueStorage.remove(threadID);
    }

    public CallTarget getPthreadCallTarget() {
        return pthreadCallTarget;
    }
}
