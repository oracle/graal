/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.runtime;

import java.lang.ref.ReferenceQueue;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.espresso.blocking.EspressoLock;
import com.oracle.truffle.espresso.blocking.GuestInterruptedException;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.ref.EspressoReference;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

final class EspressoReferenceDrainer extends ContextAccessImpl {
    private volatile Thread hostToGuestReferenceDrainThread;
    private volatile ReferenceDrain drain;

    private final ReferenceQueue<StaticObject> referenceQueue = new ReferenceQueue<>();
    private volatile StaticObject referencePendingList = StaticObject.NULL;
    @CompilationFinal private volatile EspressoLock pendingLock;

    EspressoReferenceDrainer(EspressoContext context) {
        super(context);
    }

    private EspressoLock getLock() {
        if (pendingLock == null) {
            synchronized (this) {
                if (pendingLock == null) {
                    pendingLock = EspressoLock.create(getContext().getBlockingSupport());
                }
            }
        }
        return pendingLock;
    }

    void initReferenceDrain() {
        TruffleLanguage.Env env = getContext().getEnv();
        Meta meta = getMeta();

        if (getJavaVersion().java8OrEarlier()) {
            // Initialize reference queue

            this.drain = new ReferenceDrain() {
                @SuppressWarnings("rawtypes")
                @Override
                protected void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock) {
                    StaticObject obj = meta.java_lang_ref_Reference_pending.getAndSetObject(meta.java_lang_ref_Reference.getStatics(), head.getGuestReference());
                    meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), obj);
                    getVM().JVM_MonitorNotify(lock, profiler);

                }
            };
        } else if (getJavaVersion().java9OrLater()) {
            // Initialize reference queue
            this.drain = new ReferenceDrain() {
                @SuppressWarnings("rawtypes")
                @Override
                protected void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock) {
                    EspressoLock pLock = getLock();
                    pLock.lock();
                    try {
                        StaticObject obj = referencePendingList;
                        referencePendingList = head.getGuestReference();
                        meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), obj);
                        getVM().JVM_MonitorNotify(lock, profiler);
                        pLock.signalAll();
                    } finally {
                        pLock.unlock();
                    }
                }
            };
        } else {
            throw EspressoError.shouldNotReachHere();
        }
        if (getContext().multiThreadingEnabled()) {
            hostToGuestReferenceDrainThread = env.createThread(drain);
            hostToGuestReferenceDrainThread.setName("Reference Drain");
        }
    }

    void startReferenceDrain() {
        if (hostToGuestReferenceDrainThread != null) {
            hostToGuestReferenceDrainThread.setDaemon(true);
            hostToGuestReferenceDrainThread.start();
        }
    }

    void shutdownAndWaitReferenceDrain() throws InterruptedException {
        if (hostToGuestReferenceDrainThread != null) {
            while (hostToGuestReferenceDrainThread.isAlive()) {
                getContext().getEnv().submitThreadLocal(new Thread[]{hostToGuestReferenceDrainThread}, new ExitTLA());
                hostToGuestReferenceDrainThread.interrupt();
                hostToGuestReferenceDrainThread.join(10);
            }
        }
    }

    Thread drainHostThread() {
        return hostToGuestReferenceDrainThread;
    }

    ReferenceQueue<StaticObject> getReferenceQueue() {
        return referenceQueue;
    }

    StaticObject getAndClearReferencePendingList() {
        // Should be under guest lock
        EspressoLock pLock = getLock();
        pLock.lock();
        try {
            StaticObject res = referencePendingList;
            referencePendingList = StaticObject.NULL;
            return res;
        } finally {
            pLock.unlock();
        }
    }

    boolean hasReferencePendingList() {
        return !StaticObject.isNull(referencePendingList);
    }

    void waitForReferencePendingList() {
        if (hasReferencePendingList()) {
            return;
        }
        doWaitForReferencePendingList();
    }

    void triggerDrain() {
        Meta meta = getMeta();
        drain.drain(meta, getGuestLock(meta), false);
    }

    @TruffleBoundary
    private void doWaitForReferencePendingList() {
        try {
            EspressoLock pLock = getLock();
            pLock.lock();
            try {
                // Wait until the reference drain updates the list.
                while (!hasReferencePendingList()) {
                    pLock.await(0L);
                }
            } finally {
                pLock.unlock();
            }
        } catch (GuestInterruptedException e) {
            /*
             * The guest handler thread will attempt emptying the reference list by re-obtaining it.
             * If the list is not null, then everything will proceed as normal. In the case it is
             * empty, the guest handler will simply loop back into waiting. This looping back into
             * waiting done in guest code gives us a chance to reach an espresso safe point (a back
             * edge), thus giving us the possibility to stop this thread when tearing down the VM.
             */
        }
    }

    private void casNextIfNullAndMaybeClear(EspressoReference wrapper) {
        StaticObject ref = wrapper.getGuestReference();
        // Cleaner references extends PhantomReference but are cleared.
        // See HotSpot's ReferenceProcessor::process_discovered_references in referenceProcessor.cpp
        if (InterpreterToVM.instanceOf(ref, getMeta().sun_misc_Cleaner)) {
            wrapper.clear();
        }
        getMeta().java_lang_ref_Reference_next.compareAndSwapObject(ref, StaticObject.NULL, ref);
    }

    private static final class ExitTLA extends ThreadLocalAction {
        private ExitTLA() {
            super(true, false);
        }

        @Override
        protected void perform(Access access) {
            throw new EspressoExitException(0);
        }
    }

    private abstract class ReferenceDrain implements Runnable {

        protected final SubstitutionProfiler profiler = new SubstitutionProfiler();

        private void safepoint() {
            TruffleSafepoint.poll(profiler);
        }

        @SuppressWarnings("rawtypes")
        @Override
        public final void run() {
            Meta meta = getMeta();
            try {
                getVM().attachThread(Thread.currentThread());
                final StaticObject lock = getGuestLock(meta);
                for (;;) {
                    drain(meta, lock, true);
                }
            } finally {
                getContext().getThreadAccess().terminate(getContext().getCurrentThread());
                if (getContext().isClosing()) {
                    // Ignore exceptions that arise during closing.
                    return;
                }
            }
        }

        private void drain(Meta meta, StaticObject lock, boolean block) {
            // Based on HotSpot's ReferenceProcessor::enqueue_discovered_reflist.
            // HotSpot's "new behavior": Walk down the list, self-looping the next field
            // so that the References are not considered active.
            EspressoReference head;
            do {
                safepoint();
                head = popQueue(block);
                if (head == null) {
                    assert !block;
                    return;
                }
            } while (StaticObject.notNull(meta.java_lang_ref_Reference_next.getObject(head.getGuestReference())));

            lock.getLock(getContext()).lock();
            try {
                assert Target_java_lang_Thread.holdsLock(lock, meta) : "must hold Reference.lock at the guest level";
                casNextIfNullAndMaybeClear(head);

                EspressoReference prev = head;
                EspressoReference ref;
                while ((ref = (EspressoReference) referenceQueue.poll()) != null) {
                    if (StaticObject.notNull(meta.java_lang_ref_Reference_next.getObject(ref.getGuestReference()))) {
                        continue;
                    }
                    meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), ref.getGuestReference());
                    casNextIfNullAndMaybeClear(ref);
                    prev = ref;
                    safepoint();
                }

                meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), prev.getGuestReference());
                updateReferencePendingList(head, prev, lock);
            } finally {
                lock.getLock(getContext()).unlock();
            }
        }

        @TruffleBoundary
        private EspressoReference popQueue(boolean block) {
            if (block) {
                EspressoReference[] box = new EspressoReference[1];
                TruffleSafepoint.setBlockedThreadInterruptible(profiler, (queue) -> {
                    box[0] = (EspressoReference) queue.remove();
                }, referenceQueue);
                return box[0];
            } else {
                return (EspressoReference) referenceQueue.poll();
            }
        }

        @SuppressWarnings("rawtypes")
        protected abstract void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock);
    }

    private static StaticObject getGuestLock(Meta meta) {
        return meta.java_lang_ref_Reference_lock.getObject(meta.java_lang_ref_Reference.tryInitializeAndGetStatics());
    }
}
