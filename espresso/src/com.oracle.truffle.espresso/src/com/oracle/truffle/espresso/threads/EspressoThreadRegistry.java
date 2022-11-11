/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.threads;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ContextAccessImpl;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

public final class EspressoThreadRegistry extends ContextAccessImpl {
    private static final int DEFAULT_THREAD_ARRAY_SIZE = 8;

    private final TruffleLogger logger = TruffleLogger.getLogger(EspressoLanguage.ID, EspressoThreadRegistry.class);

    private final Set<StaticObject> activeThreads = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Object activeThreadLock = new Object() {
    };

    public EspressoThreadRegistry(EspressoContext context) {
        super(context);
    }

    /**
     * Contains a mapping from host thread ID to guest thread object. The object at index 0 is an
     * Integer which corresponds to the offset we need to substract to the thread ID to get the
     * index of the corresponding guest thread. The referenced array might change, but its contents
     * will not. Obtaining the reference locally and working with the local variable storing it thus
     * corresponds to obtaining a snapshot of the state.
     * <p>
     * The offset is stored in the array, and not in a field because of concurrency issues. We want
     * to be able to obtain the current thread quickly (ie: without locking). To do that, we obtain
     * atomically a snapshot of the state (ie: the array). If we left the offset as a field, we
     * would need to guarantee atomic read of two different fields, which is quite complex without
     * locking.
     * <p>
     * By putting the offset inside the array, we make it part of the snapshot, thus guaranteeing
     * consistency.
     */
    private Object[] guestThreads = new Object[DEFAULT_THREAD_ARRAY_SIZE];

    /**
     * These three threads are a bit special. They are created at VM startup, and stay there for the
     * whole duration of execution. They are stored individually so they do not appear in the thread
     * snapshot, so as not to bloat the span of the array.
     */

    @CompilationFinal private boolean mainThreadCreated = false;
    @CompilationFinal private StaticObject mainThreadGroup = null;

    @CompilationFinal private long mainThreadId = -1;
    @CompilationFinal private StaticObject guestMainThread = null;

    @CompilationFinal private long finalizerThreadId = -1;
    @CompilationFinal private StaticObject guestFinalizerThread = null;

    @CompilationFinal private long referenceHandlerThreadId = -1;
    @CompilationFinal private StaticObject guestReferenceHandlerThread = null;

    @CompilerDirectives.TruffleBoundary
    public StaticObject[] activeThreads() {
        return activeThreads.toArray(StaticObject.EMPTY_ARRAY);
    }

    private void registerMainThread(Thread thread, StaticObject self) {
        synchronized (activeThreadLock) {
            mainThreadId = thread.getId();
            guestMainThread = self;
        }
        activeThreads.add(self);
        getContext().registerCurrentThread(self);
    }

    public final AtomicLong createdThreadCount = new AtomicLong();
    public final AtomicLong peakThreadCount = new AtomicLong();

    public void registerThread(Thread host, StaticObject guest) {
        activeThreads.add(guest);

        // Update java.lang.management counters.
        createdThreadCount.incrementAndGet();
        peakThreadCount.updateAndGet(new LongUnaryOperator() {
            @Override
            public long applyAsLong(long oldPeak) {
                return Math.max(oldPeak, activeThreads.size());
            }
        });

        if (finalizerThreadId == -1) {
            if (getMeta().java_lang_ref_Finalizer$FinalizerThread == guest.getKlass()) {
                synchronized (activeThreadLock) {
                    if (finalizerThreadId == -1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        finalizerThreadId = host.getId();
                        guestFinalizerThread = guest;
                        return;
                    }
                }
            }
        }
        if (referenceHandlerThreadId == -1) {
            if (getMeta().java_lang_ref_Reference$ReferenceHandler == guest.getKlass()) {
                synchronized (activeThreadLock) {
                    if (referenceHandlerThreadId == -1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        referenceHandlerThreadId = host.getId();
                        guestReferenceHandlerThread = guest;
                        return;
                    }
                }
            }
        }
        pushThread(Math.toIntExact(host.getId()), guest);
        if (host == Thread.currentThread()) {
            getContext().registerCurrentThread(guest);
        }
    }

    /**
     * Once a thread is truffle-disposed through {@code TruffleLanguage#disposeThread}, remove it
     * from the active set, and notify any thread waiting for VM teardown that it should check again
     * for all non-daemon thread completion.
     */
    @SuppressFBWarnings(value = "NN", justification = "Removing a thread from the active set is the state change we need.")
    public boolean unregisterThread(StaticObject thread) {
        if (!activeThreads.remove(thread)) {
            // Already unregistered
            return false;
        }
        logger.fine(() -> {
            String guestName = getThreadAccess().getThreadName(thread);
            long guestId = getThreadAccess().getThreadId(thread);
            return String.format("unregisterThread([GUEST:%s, %d])", guestName, guestId);
        });
        Thread hostThread = getThreadAccess().getHost(thread);
        int id = Math.toIntExact(hostThread.getId());
        synchronized (activeThreadLock) {
            if (id == mainThreadId) {
                mainThreadId = -1;
                guestMainThread = null;
            } else if (id == finalizerThreadId) {
                guestFinalizerThread = null;
                finalizerThreadId = -1;
            } else if (id == referenceHandlerThreadId) {
                guestReferenceHandlerThread = null;
                referenceHandlerThreadId = -1;
            } else {
                Object[] threads = guestThreads;
                int threadIndex = getThreadIndex(id, threads);
                if (getThreadAccess().isAlive(thread)) {
                    assert threads[threadIndex] == thread;
                    threads[threadIndex] = null;
                } else {
                    /*
                     * Non-alive threads may have been removed from guestThreads by
                     * refactorGuestThreads => threadIndex may be invalid/outdated and the slot
                     * could be populated by another thread.
                     */
                    if (0 <= threadIndex && threadIndex < threads.length) {
                        if (threads[threadIndex] == thread) {
                            threads[threadIndex] = null;
                        }
                    }
                }
            }
        }
        getLanguage().getThreadLocalState().clearCurrentThread(thread);
        getContext().notifyShutdownSynchronizer();
        return true;
    }

    /**
     * Returns the guest object corresponding to the given host thread. For it to work as intended,
     * there must be an invariant throughout all espresso:
     * <p>
     * Any call to this method must use a thread that is alive as argument.
     * <p>
     * Currently, this invariant is trivially verified, as it is always used with
     * Thread.currentThread() as input.
     *
     * @param host The host thread.
     * @return The guest thread corresponding to the given thread.
     */
    public StaticObject getGuestThreadFromHost(Thread host) {
        int id = (int) host.getId();
        if (id == mainThreadId) {
            return guestMainThread;
        }
        /*
         * Fetches the thread corresponding to the given host thread ID without locking (for fresh
         * threads). The only property we need to have is that fields accesses are regular (ie: a
         * read with a concurrent write will return either the old or the new written value)
         */
        if (id == finalizerThreadId) {
            return guestFinalizerThread;
        }
        if (id == referenceHandlerThreadId) {
            return guestReferenceHandlerThread;
        }
        Object[] threads = guestThreads;
        if (threads[0] == null) {
            // quick check if no registered threads yet
            return null;
        }
        int index = getThreadIndex(id, threads);
        if (index <= 0 || index >= guestThreads.length) {
            // no guest thread created for this host thread
            return null;
        }
        return (StaticObject) threads[index];
    }

    public StaticObject getMainThread() {
        return guestMainThread;
    }

    public StaticObject createGuestThreadFromHost(Thread hostThread, Meta meta, VM vm, String name, StaticObject threadGroup, boolean managedByEspresso) {
        if (meta == null) {
            // initial thread used to initialize the context and spawn the VM.
            // Don't attempt guest thread creation
            return null;
        }
        synchronized (activeThreadLock) {
            StaticObject exisitingThread = getGuestThreadFromHost(hostThread);
            if (exisitingThread != null) {
                // already a live guest thread for this host thread
                return exisitingThread;
            }
            vm.attachThread(hostThread);
            StaticObject guestThread = meta.java_lang_Thread.allocateInstance(getContext());
            // Allow guest Thread.currentThread() to work.
            meta.java_lang_Thread_priority.setInt(guestThread, Thread.NORM_PRIORITY);
            getThreadAccess().initializeHiddenFields(guestThread, hostThread, managedByEspresso);

            // register the new guest thread
            registerThread(hostThread, guestThread);
            // Set runnable status before executing guest code.
            meta.java_lang_Thread_threadStatus.setInt(guestThread, State.RUNNABLE.value);

            if (name == null) {
                meta.java_lang_Thread_init_ThreadGroup_Runnable.invokeDirect(guestThread, mainThreadGroup, StaticObject.NULL);
            } else {
                meta.java_lang_Thread_init_ThreadGroup_String.invokeDirect(guestThread, threadGroup, meta.toGuestString(name));
            }

            // now add to the main thread group
            meta.java_lang_ThreadGroup_add.invokeDirect(threadGroup, guestThread);

            logger.fine(() -> {
                String guestName = getThreadAccess().getThreadName(guestThread);
                long guestId = getThreadAccess().getThreadId(guestThread);
                return String.format("createGuestThreadFromHost: [HOST:%s, %d], [GUEST:%s, %d]", hostThread.getName(), hostThread.getId(), guestName, guestId);
            });

            return guestThread;
        }
    }

    /**
     * The order in which methods are called and fields are set here is important, it mimics
     * HotSpot's implementation.
     */
    public void createMainThread(Meta meta) {
        StaticObject systemThreadGroup = meta.java_lang_ThreadGroup.allocateInstance(getContext());
        meta.java_lang_ThreadGroup.lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void) // private
                        // ThreadGroup()
                        .invokeDirect(systemThreadGroup);
        Thread hostThread = Thread.currentThread();
        StaticObject mainThread = meta.java_lang_Thread.allocateInstance(getContext());
        // Allow guest Thread.currentThread() to work.
        meta.java_lang_Thread_priority.setInt(mainThread, Thread.NORM_PRIORITY);
        getThreadAccess().initializeHiddenFields(mainThread, hostThread, false);
        mainThreadGroup = meta.java_lang_ThreadGroup.allocateInstance(getContext());

        registerMainThread(hostThread, mainThread);

        // Guest Thread.currentThread() must work as this point.
        meta.java_lang_ThreadGroup // public ThreadGroup(ThreadGroup parent, String name)
                        .lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThreadGroup,
                                        /* parent */ systemThreadGroup,
                                        /* name */ meta.toGuestString("main"));

        meta.java_lang_Thread // public Thread(ThreadGroup group, String name)
                        .lookupDeclaredMethod(Symbol.Name._init_, Symbol.Signature._void_ThreadGroup_String) //
                        .invokeDirect(mainThread,
                                        /* group */ mainThreadGroup,
                                        /* name */ meta.toGuestString("main"));
        meta.java_lang_Thread_threadStatus.setInt(mainThread, State.RUNNABLE.value);

        // Notify native backend about main thread.
        getNativeAccess().prepareThread();

        mainThreadCreated = true;
        logger.fine(() -> {
            String guestName = getThreadAccess().getThreadName(mainThread);
            long guestId = getThreadAccess().getThreadId(mainThread);
            return String.format("createMainThread: [HOST:%s, %d], [GUEST:%s, %d]", hostThread.getName(), hostThread.getId(), guestName, guestId);
        });
    }

    public boolean isMainThreadCreated() {
        return mainThreadCreated;
    }

    public StaticObject getMainThreadGroup() {
        return mainThreadGroup;
    }

    private void pushThread(int id, StaticObject self) {
        synchronized (activeThreadLock) {
            Object[] threads = guestThreads;
            if (threads[0] == null) {
                // First registered thread;
                threads[0] = id - 1;
            }
            int threadIndex = getThreadIndex(id, threads);
            if (threadIndex >= threads.length || threadIndex < 1) {
                refactorGuestThreads(id, self);
                return;
            }
            assert threads[threadIndex] == null;
            threads[threadIndex] = self;
        }
    }

    private void refactorGuestThreads(int id, StaticObject self) {
        assert Thread.holdsLock(activeThreadLock);
        Object[] oldThreads = guestThreads;
        int minID = id;
        int maxID = id;
        ArrayList<StaticObject> toRelocate = new ArrayList<>();
        for (int i = 1; i < oldThreads.length; i++) {
            if (oldThreads[i] != null) {
                StaticObject guestThread = (StaticObject) oldThreads[i];
                if (getThreadAccess().isAlive(guestThread)) {
                    Thread hostThread = getThreadAccess().getHost(guestThread);
                    int hostID = (int) hostThread.getId();
                    if (hostID < minID) {
                        minID = hostID;
                    }
                    if (hostID > maxID) {
                        maxID = hostID;
                    }
                    toRelocate.add(guestThread);
                }
            }
        }
        int span = maxID - minID;
        // To store the offset.
        int newLength = 1;
        // To store what we currently have.
        newLength += span;
        // Some leeway
        newLength += Math.max(toRelocate.size() + 1, span / 2);
        // If all other threads are terminated, span is 0. have at least the original default size.
        newLength = Math.max(newLength, DEFAULT_THREAD_ARRAY_SIZE);

        // Create the new thread array.
        Object[] newThreads = new Object[newLength];
        int newOffset = minID - 1;
        newThreads[0] = newOffset;
        for (StaticObject guestThread : toRelocate) {
            int hostId = (int) getThreadAccess().getHost(guestThread).getId();
            newThreads[hostId - newOffset] = guestThread;
        }
        newThreads[id - newOffset] = self;
        guestThreads = newThreads;
    }

    // Thread management helpers
    private static int getThreadIndex(int id, Object[] threads) {
        return id - (int) threads[0];
    }
}
