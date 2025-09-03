/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.blocking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.threads.ThreadAccess;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.espresso.vm.VM;

public final class ThreadRequests {
    private static final Thread[] EMPTY_THREAD_ARRAY = new Thread[0];

    public abstract static class Request<T> extends ThreadLocalAction {
        private final Map<Thread, T> result = new ConcurrentHashMap<>();

        /**
         * Performs the request on the given thread, and return the result of that action.
         * <p>
         * If this returns {@code null}, the
         * {@link #request(EspressoContext, Request, Node, StaticObject[], Object[]) request} will
         * use {@link #placeHolderValue()} for its result.
         */
        public abstract T action(Thread current);

        /**
         * The placeholder value for threads that are unresponsive.
         * <p>
         * Responsive threads for which {@link #action(Thread)} returns {@code null} will also use
         * this value as their result.
         */
        public T placeHolderValue() {
            return null;
        }

        @TruffleBoundary
        public Request(boolean hasSideEffects, boolean synchronous) {
            super(hasSideEffects, synchronous);
        }

        @Override
        protected final void perform(Access access) {
            T value = action(access.getThread());
            if (value != null) {
                result.put(access.getThread(), value);
            }
        }

        final Map<Thread, T> result() {
            return result;
        }
    }

    public static VM.StackTrace[] getStackTraces(EspressoContext ctx, int maxDepth, Node location, StaticObject... threads) {
        StaticObject[] threadList = getThreadList(ctx, threads);
        VM.StackTrace[] result = new VM.StackTrace[threadList.length];
        request(ctx, new GetStackTrace(maxDepth), location, threadList, result);
        return result;
    }

    public static StaticObject[] findDeadlocks(EspressoContext ctx, boolean monitorsOnly, Node location, StaticObject... threads) {
        StaticObject[] threadList = getThreadList(ctx, threads);
        FindDeadLocks findDeadLocks = new FindDeadLocks(ctx, Thread.currentThread(), monitorsOnly);
        request(ctx, findDeadLocks, location, threadList, new Void[threadList.length]);
        return findDeadLocks.getDeadLockedThreads();
    }

    /**
     * From the given {@code threads} array, finds the threads which are
     * {@link ThreadAccess#isResponsive(StaticObject) responsive}, then submits the {@code request}
     * as a {@link ThreadLocalAction} only on these responsive threads. Unresponsive threads will be
     * sent the request separately, but this method will wait no more than
     * {@link EspressoOptions#ThreadRequestGracePeriod} for those to complete.
     * <p>
     * The {@code result} array will then be filled with the result of the request, with
     * {@code result[i]} corresponding to the result of running the action on the thread in
     * {@code threads[i]}.
     * <p>
     * The passed {@code threads} array must be non-null. Consider using
     * {@link EspressoContext#getActiveThreads()} for requesting on all threads.
     */
    @TruffleBoundary
    public static <T> void request(EspressoContext ctx, Request<T> request, Node location, StaticObject[] threads, T[] result) {
        Objects.requireNonNull(threads);
        Objects.requireNonNull(result);
        if (threads.length != result.length) {
            throw EspressoError.shouldNotReachHere("Wrong usage of ThreadRequests.request");
        }
        try {
            // Prevent threads from entering native.
            freeze(ctx, threads, true);

            // Filter to separate responsive threads from unresponsive ones.
            ThreadAccess access = ctx.getThreadAccess();
            List<Thread> running = new ArrayList<>();
            List<Thread> unresponsive = new ArrayList<>();

            for (int i = 0; i < threads.length; i++) {
                StaticObject t = threads[i];
                if (StaticObject.notNull(t)) {
                    if (isResponsive(access, t) ||
                                    t == access.getCurrentGuestThread() /*- current thread is always responsive. */) {
                        running.add(access.getHost(t));
                    } else {
                        unresponsive.add(access.getHost(t));
                    }
                }
            }

            // Submit the request to "unresponsive" threads, but do not wait yet.
            Future<Void> unresponsiveFuture = ctx.getEnv().submitThreadLocal(unresponsive.toArray(EMPTY_THREAD_ARRAY), request);
            long submitTime = System.currentTimeMillis();

            // Submit the request to responsive threads, and wait for completion.
            Future<Void> runningFuture = ctx.getEnv().submitThreadLocal(running.toArray(EMPTY_THREAD_ARRAY), request);
            TruffleSafepoint.setBlockedThreadInterruptible(location, f -> {
                try {
                    runningFuture.get();
                } catch (ExecutionException e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }, runningFuture);

            // Give the 'unresponsive' threads some grace period to complete the TLA.
            long elapsed = submitTime - System.currentTimeMillis();
            int gracePeriod = ctx.getEnv().getOptions().get(EspressoOptions.ThreadRequestGracePeriod);

            if (!unresponsiveFuture.isDone() && elapsed < gracePeriod) {
                TruffleSafepoint.setBlockedThreadInterruptible(location, f -> {
                    try {
                        unresponsiveFuture.get(gracePeriod - elapsed, TimeUnit.MILLISECONDS);
                    } catch (ExecutionException e) {
                        throw EspressoError.shouldNotReachHere(e);
                    } catch (TimeoutException e) {
                        // Ignore
                    }
                }, unresponsiveFuture);
            }

            // Build the result map.
            Map<Thread, T> tlaResult = request.result();
            for (int i = 0; i < threads.length; i++) {
                StaticObject t = threads[i];
                if (StaticObject.notNull(t)) {
                    Thread host = access.getHost(t);
                    if (tlaResult.containsKey(host)) {
                        result[i] = tlaResult.get(host);
                        continue;
                    }
                }
                result[i] = request.placeHolderValue();
            }
            // No longer relevant.
            unresponsiveFuture.cancel(false);
        } finally {
            // Re-allow threads to enter native.
            freeze(ctx, threads, false);
        }
    }

    private static StaticObject[] getThreadList(EspressoContext ctx, StaticObject[] threads) {
        StaticObject[] threadList = threads;
        if (threadList == null) {
            threadList = ctx.getActiveThreads();
        }
        return threadList;
    }

    private static boolean isResponsive(ThreadAccess access, StaticObject thread) {
        return StaticObject.notNull(thread) &&
                        access.isAlive(thread) &&
                        !access.isVirtualThread(thread) &&
                        access.isResponsive(thread);
    }

    @SuppressFBWarnings(value = {"IMSE"}, justification = "Not dubious, may happen if failed to lock all native transitions.")
    private static void freeze(EspressoContext ctx, StaticObject[] threads, boolean block) {
        for (StaticObject t : threads) {
            if (StaticObject.notNull(t)) {
                try {
                    ctx.getThreadAccess().blockNativeTransitions(t, block);
                } catch (IllegalMonitorStateException e) {
                    // This may happen on unlock if a safepoint threw while we were locking at the
                    // start of the request.
                }
            }
        }
    }

    private static final class GetStackTrace extends Request<VM.StackTrace> {
        GetStackTrace(int maxDepth) {
            super(false, false);
            this.maxDepth = maxDepth;
        }

        private final int maxDepth;

        @Override
        public VM.StackTrace action(Thread current) {
            return InterpreterToVM.getStackTrace(InterpreterToVM.DefaultHiddenFramesFilter.INSTANCE, maxDepth);
        }
    }

    private static final class FindDeadLocks extends Request<Void> {
        FindDeadLocks(EspressoContext ctx, Thread initiatingThread, boolean monitorsOnly) {
            super(false, true);
            this.context = ctx;
            this.initiatingThread = initiatingThread;
            this.monitorsOnly = monitorsOnly;
        }

        private final EspressoContext context;
        private final Thread initiatingThread;
        private final boolean monitorsOnly;

        private volatile StaticObject[] deadLockedThreads = null;

        @Override
        public Void action(Thread current) {
            if (current == initiatingThread) {
                deadLockedThreads = context.getEspressoEnv().getThreadRegistry().findDeadlocks(monitorsOnly);
            }
            return null;
        }

        public StaticObject[] getDeadLockedThreads() {
            return deadLockedThreads;
        }
    }
}
