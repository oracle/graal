/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Collects the debuggee threads. Not all debuggee threads are collected here, only those that are
 * or were once suspended.
 * <p>
 * <b>Synchronization:</b> Operations that depend on threads state and that are performed on
 * multiple threads should be done under the write lock to prevent from concurrent thread state
 * changes. Individual threads are obliged to acquire the read lock when they change state. Threads
 * have their individual locks to allow parallel state changes.
 */
public final class ThreadsCollector {

    private final Map<Long, ThreadRef> threads = new HashMap<>();
    private final ServerToResidentCallThread callToResidentThread = ServerToResidentCallThread.create();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Object vmSuspendLock = new Object();
    private int vmSuspendCount;
    private volatile boolean disposed;

    ThreadsCollector() {
    }

    ReentrantReadWriteLock getRWLock() {
        return rwLock;
    }

    ServerToResidentCallThread getCallToResidentThread() {
        return callToResidentThread;
    }

    public ThreadRef getThreadRef(long threadId) {
        Lock lock = rwLock.writeLock();
        lock.lock();
        try {
            ThreadRef thread = threads.get(threadId);
            if (thread == null) {
                thread = new ThreadRef(threadId, this);
                threads.put(threadId, thread);
            }
            return thread;
        } finally {
            lock.unlock();
        }
    }

    public ThreadRef getThreadRefIfExists(long threadId) {
        Lock lock = rwLock.readLock();
        lock.lock();
        try {
            return threads.get(threadId);
        } finally {
            lock.unlock();
        }
    }

    public void suspendAll() {
        suspendAllBut(null);
    }

    void suspendAllBut(ThreadRef ignored) {
        long[] suspendedThreadIDs = new long[2]; // Threads that are suspended already.
        int lastIndex = 0;
        // Block start of new threads that can emerge during VM suspend
        notifyVMSuspended();
        Lock lock = rwLock.writeLock();
        lock.lock();
        try {
            if (disposed) {
                return;
            }
            for (ThreadRef thread : threads.values()) {
                if (thread == ignored) {
                    continue;
                }
                if (thread.getSuspendCount() > 0) {
                    // Suspended already, notify that it was suspended again
                    thread.notifySuspended();
                    if (lastIndex + 1 >= suspendedThreadIDs.length) {
                        suspendedThreadIDs = Arrays.copyOf(suspendedThreadIDs, suspendedThreadIDs.length + suspendedThreadIDs.length / 2);
                    }
                    suspendedThreadIDs[lastIndex++] = thread.getThreadId();
                }
            }
            if (lastIndex < suspendedThreadIDs.length) {
                suspendedThreadIDs = Arrays.copyOf(suspendedThreadIDs, lastIndex);
            }
            // Suspend all other threads in the resident.
            long[] newlySuspendedThreadIDs = ServerJDWP.BRIDGE.vmSuspend(suspendedThreadIDs);
            for (long id : newlySuspendedThreadIDs) {
                ThreadRef threadRef = getThreadRef(id);
                threadRef.notifySuspended();
            }
        } finally {
            lock.unlock();
        }
    }

    void suspendAllAt(ThreadRef threadRef, SuspendedInfo suspendedInfo, Runnable eventSender) {
        // We need to be able to release the lock completely in this method.
        // Assert that we're not reentering.
        assert rwLock.getWriteHoldCount() == 0 : "Must not reenter the write lock.";
        long[] suspendedThreadIDs = new long[2]; // Threads that are suspended already.
        long skipId = threadRef.getThreadId();
        int lastIndex = 1;
        suspendedThreadIDs[0] = skipId;
        // Block start of new threads that can emerge during VM suspend
        notifyVMSuspended();
        AtomicReference<Lock> lock = new AtomicReference<>(rwLock.writeLock());
        rwLock.writeLock().lock();
        try {
            if (disposed) {
                return;
            }
            for (ThreadRef thread : threads.values()) {
                if (thread.getThreadId() == skipId) {
                    continue;
                }
                if (thread.canParkOrIncreaseSuspend()) {
                    // Suspended already
                    if (lastIndex + 1 >= suspendedThreadIDs.length) {
                        suspendedThreadIDs = Arrays.copyOf(suspendedThreadIDs, suspendedThreadIDs.length + suspendedThreadIDs.length / 2);
                    }
                    suspendedThreadIDs[lastIndex++] = thread.getThreadId();
                }
            }
            if (lastIndex < suspendedThreadIDs.length) {
                suspendedThreadIDs = Arrays.copyOf(suspendedThreadIDs, lastIndex);
            }
            // If we got suspended in the resident already, resume it. We'll park here instead.
            threadRef.resumeResidentIfSuspended();
            // Suspend all other threads in the resident.
            long[] newlySuspendedThreadIDs = callToResidentThread.vmSuspend(suspendedThreadIDs);
            for (long id : newlySuspendedThreadIDs) {
                ThreadRef thread = getThreadRef(id);
                thread.notifySuspended();
            }
            // Suspend at the threadRef now.
            // Unlock completely on event send, we must not park under the lock
            threadRef.suspendedAt(suspendedInfo, () -> {
                lock.getAndSet(null).unlock();
                assert rwLock.getWriteHoldCount() == 0;
                assert rwLock.getReadHoldCount() == 0;
                eventSender.run();
            });
        } finally {
            Lock l = lock.get();
            if (l != null) {
                l.unlock();
            }
        }
    }

    public void resumeAll() {
        resumeAllBut(null);
    }

    void resumeAllBut(ThreadRef ignored) {
        resumeAllBut(ignored, false);
    }

    private void resumeAllBut(ThreadRef ignored, boolean forceRelease) {
        // The order of which to resume threads is not specified, however when RESUME_ALL command is
        // sent while performing a stepping request, some debuggers (IntelliJ is a known case) will
        // expect all other threads but the current stepping thread to be resumed first.

        // Distinguish which threads were suspended in the resident and which threads are parked.
        // The suspended threads are resumed first, the parked ones afterwards.
        long[] resumeThreadIDs = new long[8]; // Threads that will be resumed
        Set<ThreadRef> parkedThreads = null;
        int lastIndex = 0;
        Lock lock = rwLock.writeLock();
        lock.lock();
        try {
            for (ThreadRef thread : threads.values()) {
                if (thread == ignored) {
                    continue;
                }
                int suspendCount = thread.getSuspendCount();
                if (suspendCount == 1 || forceRelease && suspendCount > 1) {
                    // Suspended once
                    if (!thread.isParked()) {
                        // This thread will be resumed in the resident
                        thread.notifyResumed();
                        if (lastIndex + 1 >= resumeThreadIDs.length) {
                            resumeThreadIDs = Arrays.copyOf(resumeThreadIDs, resumeThreadIDs.length + resumeThreadIDs.length / 2);
                        }
                        resumeThreadIDs[lastIndex++] = thread.getThreadId();

                    } else {
                        // Parked thread
                        if (parkedThreads == null) {
                            parkedThreads = new HashSet<>();
                        }
                        parkedThreads.add(thread);
                    }
                } else if (suspendCount > 1) {
                    // Suspended multiple times. We will not resume this, only reduce suspend count.
                    thread.notifyResumed();
                }
            }
            ServerJDWP.BRIDGE.vmResume(resumeThreadIDs);
            if (parkedThreads != null) {
                // Resume the parked threads
                for (ThreadRef t : parkedThreads) {
                    t.resume(forceRelease);
                }
            }
            if (forceRelease) {
                threads.clear();
            }
        } finally {
            lock.unlock();
        }
        notifyVMResumed();
    }

    public void releaseAllThreadsAndDispose() {
        disposed = true;
        resumeAllBut(null, true);
        synchronized (vmSuspendLock) {
            vmSuspendCount = 0;
            vmSuspendLock.notifyAll();
        }
    }

    private void notifyVMSuspended() {
        synchronized (vmSuspendLock) {
            vmSuspendCount++;
        }
    }

    private void notifyVMResumed() {
        synchronized (vmSuspendLock) {
            if (vmSuspendCount == 0) {
                return; // An extra resume
            }
            vmSuspendCount--;
            if (vmSuspendCount == 0) {
                vmSuspendLock.notifyAll();
            }
        }
    }

    void blockIfVMSuspended() {
        try {
            synchronized (vmSuspendLock) {
                while (!disposed && vmSuspendCount > 0) {
                    vmSuspendLock.wait();
                }
            }
        } catch (InterruptedException ex) {
            // Interrupted
        }
    }
}
