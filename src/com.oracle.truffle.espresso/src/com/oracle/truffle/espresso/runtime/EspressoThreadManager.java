/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class EspressoThreadManager implements ContextAccess {

    private final EspressoContext context;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    EspressoThreadManager(EspressoContext context) {
        this.context = context;
    }

    public static int DEFAULT_THREAD_ARRAY_SIZE = 8;

    private final Set<StaticObject> activeThreads = Collections.newSetFromMap(new ConcurrentHashMap<StaticObject, Boolean>());

    private final Object threadLock = new Object();

    // Index 0 contains an Integer with the offset to substract to thread id to get the actual
    // index.
    private Object[] guestThreads = new Object[DEFAULT_THREAD_ARRAY_SIZE];

    @CompilationFinal private long mainThreadId = -1;
    @CompilationFinal private StaticObject guestMainThread = null;

    @CompilationFinal private long finalizerThreadId = -1;
    @CompilationFinal private StaticObject guestFinalizerThread = null;

    @CompilationFinal private long referenceHandlerThreadId = -1;
    @CompilationFinal private StaticObject guestReferenceHandlerThread = null;

    public void registerMainThread(Thread thread, StaticObject self) {
        mainThreadId = thread.getId();
        guestMainThread = self;
        // Accounts also for Finalizer and ReferenceHandler.
        activeThreads.add(self);
    }

    public void registerThread(Thread thread, StaticObject self) {
        activeThreads.add(self);
        // These two threads are created at VM startup, and are always active. Use special fields in
        // order not to bloat the span of the guest threads array.
        if (finalizerThreadId == -1) {
            if (getMeta().FinalizerThread.isAssignableFrom(self.getKlass())) {
                synchronized (threadLock) {
                    if (finalizerThreadId == -1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        finalizerThreadId = thread.getId();
                        guestFinalizerThread = self;
                        return;
                    }
                }
            }
        }
        if (referenceHandlerThreadId == -1) {
            if (getMeta().ReferenceHandler.isAssignableFrom(self.getKlass())) {
                synchronized (threadLock) {
                    if (finalizerThreadId == -1) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        referenceHandlerThreadId = thread.getId();
                        guestReferenceHandlerThread = self;
                        return;
                    }
                }
            }
        }
        pushThread((int) thread.getId(), self);
    }

    /**
     * Returns the guest object corresponding to the given host thread.
     *
     * @param host The host thread.
     * @return The guest thread corresponding to the given thread.
     */
    public StaticObject getGuestThreadFromHost(Thread host) {
        return getThreadFromId((int) host.getId());
    }

    /**
     * Fetches the thread corresponding to the given host thread ID without locking. The only
     * property we need to have is that fields accesses are regular (ie: a read with a concurrent
     * write will return either the old or the new written value)
     */
    private StaticObject getThreadFromId(int id) {
        if (id == mainThreadId) {
            return guestMainThread;
        }
        if (id == finalizerThreadId) {
            return guestFinalizerThread;
        }
        if (id == referenceHandlerThreadId) {
            return guestReferenceHandlerThread;
        }
        Object[] threads = guestThreads;
        int index = getThreadIndex(id, threads);
        assert index > 0 && index < guestThreads.length;
        return (StaticObject) threads[index];
    }

    private void pushThread(int id, StaticObject self) {
        synchronized (threadLock) {
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
        assert Thread.holdsLock(threadLock);
        Object[] oldThreads = guestThreads;
        int minID = id;
        int maxID = id;
        ArrayList<StaticObject> toRelocate = new ArrayList<>();
        for (int i = 1; i < oldThreads.length; i++) {
            if (oldThreads[i] != null) {
                StaticObject guestThread = (StaticObject) oldThreads[i];
                if (Target_java_lang_Thread.isAlive(guestThread)) {
                    Thread hostThread = Target_java_lang_Thread.getHostFromGuestThread(guestThread);
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
            int hostId = (int) Target_java_lang_Thread.getHostFromGuestThread(guestThread).getId();
            newThreads[hostId - newOffset] = guestThread;
        }
        newThreads[id - newOffset] = self;
        guestThreads = newThreads;
    }

    public void unregisterThread(StaticObject thread) {
        activeThreads.remove(thread);
    }

    // Thread management helpers
    private static int getThreadIndex(int id, Object[] threads) {
        return id - (int) threads[0];
    }

    public Iterable<StaticObject> activeThreads() {
        return activeThreads;
    }
}
