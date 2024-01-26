/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class JoinableExecutors {
    private JoinableExecutors() {
    }

    static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
        return new JoinableThreadPoolExecutor(1, 1,
                        0L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        threadFactory);
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(ThreadFactory threadFactory) {
        return new JoinableScheduledThreadPoolExecutor(1, threadFactory);
    }

    private static final class JoinableThreadPoolExecutor extends ThreadPoolExecutor {
        JoinableThreadPoolExecutor(int corePoolSize,
                        int maximumPoolSize,
                        long keepAliveTime,
                        TimeUnit unit,
                        BlockingQueue<Runnable> workQueue,
                        ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new ThreadCollectingFactory(threadFactory));
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return ((ThreadCollectingFactory) getThreadFactory()).join(timeout, unit);
        }
    }

    private static final class JoinableScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
        JoinableScheduledThreadPoolExecutor(int corePoolSize, ThreadFactory threadFactory) {
            super(corePoolSize, new ThreadCollectingFactory(threadFactory));
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            return ((ThreadCollectingFactory) getThreadFactory()).join(timeout, unit);
        }
    }

    private static final class ThreadCollectingFactory implements ThreadFactory {

        private final ThreadFactory delegate;
        private final Set<Thread> threads = Collections.newSetFromMap(new ConcurrentHashMap<>());

        ThreadCollectingFactory(ThreadFactory delegate) {
            this.delegate = delegate;
        }

        boolean join(long timeout, TimeUnit unit) throws InterruptedException {
            long timeoutNanos = unit.toNanos(timeout);
            for (Thread thread : threads) {
                long joinStart = System.nanoTime();
                TimeUnit.NANOSECONDS.timedJoin(thread, timeoutNanos);
                long joinEnd = System.nanoTime();
                timeoutNanos -= (joinEnd - joinStart);
                if (timeoutNanos <= 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread result = delegate.newThread(r);
            threads.add(result);
            return result;
        }
    }
}
