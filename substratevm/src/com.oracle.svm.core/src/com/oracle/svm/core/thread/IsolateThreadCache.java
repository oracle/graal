/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.thread;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.thread.PlatformThreads.ThreadLocalKey;
import com.oracle.svm.shared.Uninterruptible;

/**
 * Implements a cache for {@link IsolateThread} that is used when an {@link Isolate} is entered
 * without an {@link IsolateThread} pointer for the current OS thread. In this case, we need to look
 * up the {@link IsolateThread} in a list (see
 * {@link VMThreads#findIsolateThreadToEnterCurrentOSThreadSlowPath}) while holding the VM mutex.
 * This is therefore considered to be a slow operation. However, entering without providing the
 * {@link IsolateThread} is sometimes necessary and performance-critical (e.g. FFM API upcalls are
 * doing so).
 *
 * The cache is implemented using an unmanaged thread-local cache (e.g. a pthread key) to associate
 * the {@link IsolateThread} pointer with the OS thread. We use two thread-local keys for the whole
 * process (see {@link #ISOLATE_THREAD_CACHE_KEY} and {@link #ISOLATE_CACHE_KEY}) : (1) to store the
 * {@link IsolateThread} pointer, and (2) to store the {@link Isolates#getIsolateId() identifier} of
 * the owning {@link Isolate}. The second key is necessary because an OS thread may be attached to
 * and can enter one of several isolates, but only one {@link IsolateThread} is cached at a time.
 *
 * The whole cache is optional (build-time decision) because some platforms may not support
 * unmanaged thread-local storage. Currently, if unmanaged thread-locals are supported, we will
 * always allocate two keys and fail if that is impossible.
 *
 * The cache protocol aligns with the thread attach/detach protocol: (1) If an {@link IsolateThread}
 * is attached, the cache entry is written (in any case; even if the cache is already used by
 * another isolate). (2) If the {@link IsolateThread} detaches and the cache matches the current
 * isolate's {@link Isolates#getIsolateId() identifier} of the detaching thread, the cache is
 * cleared.
 *
 * The cache needs to be {@link IsolateThreadCache#initialize() initialized} when creating the first
 * {@link Isolate} before the initializing thread is attached.
 */
public final class IsolateThreadCache {
    private static final CGlobalData<Pointer> CACHE_INIT_STATE = CGlobalDataFactory.createWord(State.UNINITIALIZED);
    private static final CGlobalData<WordPointer> ISOLATE_THREAD_CACHE_KEY = CGlobalDataFactory.createWord();
    private static final CGlobalData<WordPointer> ISOLATE_CACHE_KEY = CGlobalDataFactory.createWord();

    private IsolateThreadCache() {
        // no instances
    }

    /** Called once per isolate. Only the first invocation creates the unmanaged thread locals. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void initialize() {
        var pt = PlatformThreads.singleton();
        if (!pt.supportsUnmanagedThreadLocal()) {
            return;
        }

        Pointer statePtr = CACHE_INIT_STATE.get();
        if (statePtr.readWord(0) == State.UNINITIALIZED) {
            WordPointer threadKeyAddr = ISOLATE_THREAD_CACHE_KEY.get();
            WordPointer isolateKeyAddr = ISOLATE_CACHE_KEY.get();

            assert threadKeyAddr.read() == Word.zero();
            assert isolateKeyAddr.read() == Word.zero();

            // The thread that won the race initializes the cache
            ThreadLocalKey threadKey = pt.createUnmanagedThreadLocal();
            ThreadLocalKey isolateKey = pt.createUnmanagedThreadLocal();
            assert threadKey != isolateKey;

            assert pt.getUnmanagedThreadLocalValue(threadKey) == Word.zero();
            threadKeyAddr.write(threadKey);
            assert pt.getUnmanagedThreadLocalValue(isolateKey) == Word.zero();
            isolateKeyAddr.write(isolateKey);

            statePtr.writeWord(0, State.INITIALIZED);
        }
    }

    /**
     * Stores the provided non-null {@link IsolateThread} pointer and the current {@link Isolate
     * Isolate's} identifier into unmanaged thread-local storage for the current OS thread.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void set(IsolateThread thread) {
        var pt = PlatformThreads.singleton();
        if (pt.supportsUnmanagedThreadLocal() && CEntryPointSnippets.isIsolateInitialized()) {
            ThreadLocalKey threadKey = ISOLATE_THREAD_CACHE_KEY.get().read();
            ThreadLocalKey isolateKey = ISOLATE_CACHE_KEY.get().read();
            assert cacheKeySanityCheck(threadKey, isolateKey);
            assert thread.isNonNull();
            pt.setUnmanagedThreadLocalValue(threadKey, thread);
            long isolateId = Isolates.getIsolateId();
            pt.setUnmanagedThreadLocalValue(isolateKey, Word.signed(isolateId));
        }
    }

    /**
     * Read the cached {@link IsolateThread} pointer from the unmanaged thread-local storage if it
     * matches the current {@link Isolate}, otherwise returns {@code NULL}.
     * 
     * Isolates are compared using their {@link Isolates#getIsolateId() identifiers}. This is
     * necessary because if
     * {@link VMThreads#detachAllExternallyStartedThreadsWithoutCleanupForTearDown()} is used, an OS
     * thread may outlive the {@link Isolate} without properly detaching itself (i.e. the cache
     * won't be cleared). The {@link Isolate} pointer may be then reused which could lead to an
     * incorrect cache hit.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static IsolateThread get() {
        var pt = PlatformThreads.singleton();
        if (pt.supportsUnmanagedThreadLocal()) {
            ThreadLocalKey threadKey = ISOLATE_THREAD_CACHE_KEY.get().read();
            ThreadLocalKey isolateKey = ISOLATE_CACHE_KEY.get().read();
            assert cacheKeySanityCheck(threadKey, isolateKey);
            if (CEntryPointSnippets.isIsolateInitialized() &&
                            pt.getUnmanagedThreadLocalValue(isolateKey).rawValue() == Isolates.getIsolateId()) {
                return pt.getUnmanagedThreadLocalValue(threadKey);
            }
        }
        return Word.nullPointer();
    }

    /**
     * Reset the {@link IsolateThread} cache to {@code NULL} if it matches the current
     * {@link Isolate}.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void clear() {
        var pt = PlatformThreads.singleton();
        if (!pt.supportsUnmanagedThreadLocal()) {
            return;
        }

        ThreadLocalKey threadKey = ISOLATE_THREAD_CACHE_KEY.get().read();
        ThreadLocalKey isolateKey = ISOLATE_CACHE_KEY.get().read();
        assert cacheKeySanityCheck(threadKey, isolateKey);

        /*
         * Only clear the IsolateThread cache if the cache entry currently belongs to the current
         * isolate. This is necessary because the same OS thread could have entered another Isolate
         * since the OS thread entered this Isolate. This avoids that we clear the cache for the
         * other Isolate.
         */
        if (pt.getUnmanagedThreadLocalValue(isolateKey).rawValue() == Isolates.getIsolateId()) {
            assert CEntryPointSnippets.isIsolateInitialized();
            /*
             * If the cache entry belongs to the current isolate, we expect that the IsolateThread
             * pointer is non-null. There is one exception: The reference handler thread for the
             * first isolate (with isolate ID == 0) may have been started before the initialization
             * of the Isolate was finished. On isolate tear down, there may be a cache "hit" because
             * the cache was actually never written and its value was initialized with 0.
             */
            assert Isolates.isCurrentFirst() || pt.getUnmanagedThreadLocalValue(threadKey) != Word.nullPointer();
            pt.setUnmanagedThreadLocalValue(threadKey, Word.nullPointer());
            pt.setUnmanagedThreadLocalValue(isolateKey, Word.signed(-1L));
        }
    }

    /**
     * Verifies that the two unmanaged thread-local keys are distinct. Before initialization both
     * keys are zero; after successful initialization they differ.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean cacheKeySanityCheck(ThreadLocalKey threadKey, ThreadLocalKey isolateKey) {
        UnsignedWord state = CACHE_INIT_STATE.get().readWord(0);
        return state == State.INITIALIZED && threadKey != isolateKey;
    }

    private static final class State {
        private static final UnsignedWord UNINITIALIZED = Word.unsigned(0);
        private static final UnsignedWord INITIALIZED = Word.unsigned(1);
    }
}
