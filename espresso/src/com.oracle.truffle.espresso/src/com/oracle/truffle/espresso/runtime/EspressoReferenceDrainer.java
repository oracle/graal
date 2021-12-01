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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.substitutions.EspressoReference;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

class EspressoReferenceDrainer implements ContextAccess {
    private final EspressoContext context;
    private Thread hostToGuestReferenceDrainThread;

    private final ReferenceQueue<StaticObject> referenceQueue = new ReferenceQueue<>();
    private volatile StaticObject referencePendingList = StaticObject.NULL;
    private final Object pendingLock = new Object() {
    };

    EspressoReferenceDrainer(EspressoContext context) {
        this.context = context;
    }

    @Override
    public EspressoContext getContext() {
        return context;
    }

    void initReferenceDrain() {
        TruffleLanguage.Env env = getContext().getEnv();
        Meta meta = getMeta();
        if (getContext().multiThreadingEnabled()) {
            if (getJavaVersion().java8OrEarlier()) {
                // Initialize reference queue
                this.hostToGuestReferenceDrainThread = env.createThread(new ReferenceDrain() {
                    @SuppressWarnings("rawtypes")
                    @Override
                    protected void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock) {
                        StaticObject obj = meta.java_lang_ref_Reference_pending.getAndSetObject(meta.java_lang_ref_Reference.getStatics(), head.getGuestReference());
                        meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), obj);
                        getVM().JVM_MonitorNotify(lock, profiler);

                    }
                });
            } else if (getJavaVersion().java9OrLater()) {
                // Initialize reference queue
                this.hostToGuestReferenceDrainThread = env.createThread(new ReferenceDrain() {
                    @SuppressWarnings("rawtypes")
                    @Override
                    protected void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock) {
                        synchronized (pendingLock) {
                            StaticObject obj = referencePendingList;
                            referencePendingList = head.getGuestReference();
                            meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), obj);
                            getVM().JVM_MonitorNotify(lock, profiler);
                            pendingLock.notifyAll();
                        }
                    }
                });
            } else {
                throw EspressoError.shouldNotReachHere();
            }
        }
    }

    void startReferenceDrain() {
        if (hostToGuestReferenceDrainThread != null) {
            hostToGuestReferenceDrainThread.setDaemon(true);
            hostToGuestReferenceDrainThread.start();
        }
    }

    void joinReferenceDrain() throws InterruptedException {
        if (hostToGuestReferenceDrainThread != null) {
            hostToGuestReferenceDrainThread.interrupt();
            hostToGuestReferenceDrainThread.join();
        }
    }

    ReferenceQueue<StaticObject> getReferenceQueue() {
        return referenceQueue;
    }

    StaticObject getAndClearReferencePendingList() {
        // Should be under guest lock
        synchronized (pendingLock) {
            StaticObject res = referencePendingList;
            referencePendingList = StaticObject.NULL;
            return res;
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

    @CompilerDirectives.TruffleBoundary
    private void doWaitForReferencePendingList() {
        try {
            synchronized (pendingLock) {
                // Wait until the reference drain updates the list.
                while (!hasReferencePendingList()) {
                    pendingLock.wait();
                }
            }
        } catch (InterruptedException e) {
            /*
             * The guest handler thread will attempt emptying the reference list by re-obtaining it.
             * If the list is not null, then everything will proceed as normal. In the case it is
             * empty, the guest handler will simply loop back into waiting. This looping back into
             * waiting done in guest code gives us a chance to reach an espresso safe point (a back
             * edge), thus giving us the possibility to stop this thread when tearing down the VM.
             */
        }
    }

    private void casNextIfNullAndMaybeClear(@SuppressWarnings("rawtypes") EspressoReference wrapper) {
        StaticObject ref = wrapper.getGuestReference();
        // Cleaner references extends PhantomReference but are cleared.
        // See HotSpot's ReferenceProcessor::process_discovered_references in referenceProcessor.cpp
        if (InterpreterToVM.instanceOf(ref, getMeta().sun_misc_Cleaner)) {
            wrapper.clear();
        }
        getMeta().java_lang_ref_Reference_next.compareAndSwapObject(ref, StaticObject.NULL, ref);
    }

    private abstract class ReferenceDrain implements Runnable {

        SubstitutionProfiler profiler = new SubstitutionProfiler();

        @SuppressWarnings("rawtypes")
        @Override
        public void run() {
            Meta meta = getMeta();
            try {
                getVM().attachThread(Thread.currentThread());
                final StaticObject lock = meta.java_lang_ref_Reference_lock.getObject(meta.java_lang_ref_Reference.tryInitializeAndGetStatics());
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        // Based on HotSpot's ReferenceProcessor::enqueue_discovered_reflist.
                        // HotSpot's "new behavior": Walk down the list, self-looping the next field
                        // so that the References are not considered active.
                        EspressoReference head;
                        do {
                            head = (EspressoReference) referenceQueue.remove();
                            assert head != null;
                        } while (StaticObject.notNull(meta.java_lang_ref_Reference_next.getObject(head.getGuestReference())));

                        lock.getLock().lock();
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
                            }

                            meta.java_lang_ref_Reference_discovered.set(prev.getGuestReference(), prev.getGuestReference());
                            updateReferencePendingList(head, prev, lock);
                        } finally {
                            lock.getLock().unlock();
                        }
                    } catch (InterruptedException e) {
                        // ignore
                        return;
                    }
                }
            } finally {
                context.getThreadAccess().terminate(context.getCurrentThread());
                if (context.isClosing()) {
                    // Ignore exceptions that arise during closing.
                    return;
                }
            }
        }

        @SuppressWarnings("rawtypes")
        protected abstract void updateReferencePendingList(EspressoReference head, EspressoReference prev, StaticObject lock);

    }
}
