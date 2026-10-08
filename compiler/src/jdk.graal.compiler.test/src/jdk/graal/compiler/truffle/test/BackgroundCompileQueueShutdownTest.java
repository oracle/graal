/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.oracle.truffle.runtime.BackgroundCompileQueue;
import com.oracle.truffle.runtime.BackgroundCompileQueue.JoinableThreadFactory;

public class BackgroundCompileQueueShutdownTest {

    @Test(timeout = 15000)
    public void testAlreadyInterrupted() throws Exception {
        checkInterruptedWait(true);
    }

    @Test(timeout = 15000)
    public void testInterruptedWhileWaiting() throws Exception {
        checkInterruptedWait(false);
    }

    private static void checkInterruptedWait(boolean alreadyInterrupted) throws Exception {
        CountDownLatch releaseWorker = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                releaseWorker.await();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        });
        worker.setDaemon(true);
        CountDownLatch firstWait = new CountDownLatch(1);
        CountDownLatch secondWait = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        BackgroundCompileQueue queue = newQueue((timeoutNanos) -> {
            if (attempts.incrementAndGet() == 1) {
                firstWait.countDown();
            } else {
                secondWait.countDown();
            }
            TimeUnit.NANOSECONDS.timedJoin(worker, timeoutNanos);
            return !worker.isAlive();
        });
        FutureTask<Boolean> shutdown = new FutureTask<>(() -> {
            if (alreadyInterrupted) {
                Thread.currentThread().interrupt();
            }
            queue.shutdownAndAwaitTermination(10000);
            return Thread.currentThread().isInterrupted();
        });
        Thread waiter = new Thread(shutdown);
        waiter.setDaemon(true);
        worker.start();
        waiter.start();
        try {
            assertTrue(firstWait.await(5, TimeUnit.SECONDS));
            if (!alreadyInterrupted) {
                waiter.interrupt();
            }
            assertTrue("Shutdown must retry the interrupted join", secondWait.await(5, TimeUnit.SECONDS));
            assertThrows("Shutdown must wait for the compiler thread", TimeoutException.class, () -> shutdown.get(50, TimeUnit.MILLISECONDS));
            releaseWorker.countDown();
            assertTrue("Shutdown must restore the interrupted status", shutdown.get(5, TimeUnit.SECONDS));
        } finally {
            releaseWorker.countDown();
            worker.join(5000);
            waiter.join(5000);
        }
    }

    @Test(timeout = 5000)
    public void testInterruptsDoNotExtendTimeout() throws Exception {
        AtomicLong previousTimeout = new AtomicLong(Long.MAX_VALUE);
        BackgroundCompileQueue queue = newQueue((timeoutNanos) -> {
            assertTrue("Retries must use the remaining timeout", timeoutNanos < previousTimeout.getAndSet(timeoutNanos));
            Thread.sleep(20);
            throw new InterruptedException();
        });
        try {
            queue.shutdownAndAwaitTermination(200);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @FunctionalInterface
    private interface JoinOperation {
        boolean join(long timeoutNanos) throws InterruptedException;
    }

    private static BackgroundCompileQueue newQueue(JoinOperation operation) throws Exception {
        JoinableThreadFactory factory = new JoinableThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r);
            }

            @Override
            public boolean joinOtherThreads(long timeout, TimeUnit unit) throws InterruptedException {
                return operation.join(unit.toNanos(timeout));
            }
        };
        Class<?> executorClass = Class.forName(BackgroundCompileQueue.class.getName() + "$TruffleThreadPoolExecutor");
        Class<?> thresholdsClass = Class.forName(BackgroundCompileQueue.class.getName() + "$DynamicCompilationThresholds");
        Constructor<?> constructor = executorClass.getDeclaredConstructor(int.class, int.class, long.class, TimeUnit.class, BlockingQueue.class, ThreadFactory.class, thresholdsClass);
        constructor.setAccessible(true);
        Object executor = constructor.newInstance(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>(), factory, null);
        BackgroundCompileQueue queue = new BackgroundCompileQueue(null);
        Field field = BackgroundCompileQueue.class.getDeclaredField("executor");
        field.setAccessible(true);
        field.set(queue, executor);
        return queue;
    }
}
