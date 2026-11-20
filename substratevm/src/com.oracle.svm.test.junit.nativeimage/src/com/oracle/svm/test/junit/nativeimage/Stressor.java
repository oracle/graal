/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.junit.nativeimage;

/**
 * Runs test tasks on platform threads and reports worker failures on the calling thread.
 */
public final class Stressor {
    private Stressor() {
    }

    public static void execute(int numberOfThreads, Runnable task) throws Throwable {
        Worker[] workers = new Worker[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            workers[i] = new Worker(task);
        }

        try {
            startAll(workers);
        } finally {
            joinAll(workers, 0);
        }
        checkWorkers(workers);
    }

    public static void startAll(Worker[] workers) {
        for (Worker worker : workers) {
            worker.start();
        }
    }

    /**
     * Waits up to the timeout for each worker. A timeout of zero waits indefinitely. Joining an
     * unstarted worker returns immediately, so this also works after partial startup.
     */
    public static void joinAll(Worker[] workers, long timeoutMillis) throws InterruptedException {
        for (Worker worker : workers) {
            worker.join(timeoutMillis);
        }
    }

    /**
     * Checks that no worker is still running and rethrows uncaught worker exceptions. Call this
     * after waiting for all workers so one failure does not prevent waiting for the others.
     */
    public static void checkWorkers(Worker[] workers) throws Throwable {
        for (Worker worker : workers) {
            if (worker.isAlive()) {
                throw new AssertionError("worker thread " + worker.getName() + " did not finish in time");
            }
            if (worker.failure != null) {
                throw worker.failure;
            }
        }
    }

    public static final class Worker extends Thread {
        private Throwable failure;

        public Worker(Runnable task) {
            super(task);
            setUncaughtExceptionHandler((_, throwable) -> failure = throwable);
        }
    }
}
