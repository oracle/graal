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
package com.oracle.svm.core.pgo;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.guest.staging.core.memory.UntrackedNullableNativeMemory;
import com.oracle.svm.guest.staging.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.shared.Uninterruptible;

import jdk.internal.misc.Unsafe;

/**
 * Per-thread storage for conditional branch counters.
 * <p>
 * Instrumented code increments a private native block owned by the executing thread, so hot
 * branches executed concurrently on many cores never write the same cache line. The block is
 * allocated in {@link #afterThreadAttach}, which runs before the thread can execute any Java code
 * (including constructing a foreign thread's {@code Thread} object), folded into the shared totals
 * when the thread exits, and read in place for still-running threads when a profile snapshot is
 * taken. Generated code therefore dereferences the thread-local without a null check. If the
 * allocation fails the thread-local points at the shared counters instead.
 */
public final class BranchProfileThreadCounters implements ThreadListener {

    /** The thread's private block, or the shared counters if none could be allocated. */
    static final FastThreadLocalWord<Pointer> BLOCK = FastThreadLocalFactory.createWord("BranchProfileThreadCounters.block");
    private static final CGlobalData<Pointer> THREADS_WITHOUT_BLOCK = CGlobalDataFactory.createWord();
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /** Location of the per-thread block pointer; a snippet that reads it must declare it private. */
    public static LocationIdentity blockLocation() {
        return BLOCK.getLocationIdentity();
    }

    private BranchProfileThreadCounters() {
    }

    public static BranchProfileThreadCounters create() {
        return new BranchProfileThreadCounters();
    }

    @Override
    @Uninterruptible(reason = "Thread state not set up.")
    public void afterThreadAttach(IsolateThread isolateThread) {
        allocate(isolateThread);
    }

    @Override
    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        Pointer block = blockOf(isolateThread);
        if (block.isNonNull()) {
            BranchProfileRecorder.mergeIntoShared(block);
            BLOCK.set(isolateThread, BranchProfileRecorder.sharedCounts());
            UntrackedNullableNativeMemory.free(block);
        }
    }

    /** The thread's private block, or null if it counts into the shared memory. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer blockOf(IsolateThread isolateThread) {
        Pointer block = BLOCK.get(isolateThread);
        if (block.equal(BranchProfileRecorder.sharedCounts())) {
            return Word.nullPointer();
        }
        return block;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void allocate(IsolateThread isolateThread) {
        if (BLOCK.get(isolateThread).isNonNull()) {
            return;
        }
        int slots = BranchProfileRecorder.registeredSlots();
        /* calloc keeps untouched pages unmapped, so sparse counter use costs little memory. */
        Pointer block = UntrackedNullableNativeMemory.calloc(Word.unsigned(slots).multiply(Long.BYTES));
        if (block.isNull()) {
            /*
             * Degrade to the shared counters rather than crash. Their increments are not atomic, so
             * such threads can lose counts; the dump reports how many threads did this.
             */
            block = BranchProfileRecorder.sharedCounts();
            UNSAFE.getAndAddLong(null, THREADS_WITHOUT_BLOCK.get().rawValue(), 1);
        }
        BLOCK.set(isolateThread, block);
    }

    /** Threads that count into the shared counters because their block could not be allocated. */
    public static long threadsWithoutBlock() {
        return THREADS_WITHOUT_BLOCK.get().readLong(0);
    }

    /** Adds the shared totals and every live thread's private counters into {@code target} at a safepoint. */
    public static void collect(long[] target) {
        CollectOperation operation = new CollectOperation(target);
        operation.enqueue();
    }

    private static final class CollectOperation extends JavaVMOperation {
        private final long[] target;

        CollectOperation(long[] target) {
            super(VMOperationInfos.get(CollectOperation.class, "Collect PGO branch counters", SystemEffect.SAFEPOINT));
            this.target = target;
        }

        @Override
        protected void operate() {
            int slots = BranchProfileRecorder.registeredSlots();
            /*
             * The shared totals are read at the safepoint too: a thread that exits before the
             * safepoint folds its block into them and frees the block, so totals read before the
             * safepoint would miss its counts.
             */
            Pointer shared = BranchProfileRecorder.sharedCounts();
            for (int slot = 0; slot < slots; slot++) {
                target[slot] += shared.readLong(slot * Long.BYTES);
            }
            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                Pointer block = blockOf(thread);
                if (block.isNull()) {
                    continue;
                }
                for (int slot = 0; slot < slots; slot++) {
                    target[slot] += block.readLong(slot * Long.BYTES);
                }
            }
        }
    }
}
