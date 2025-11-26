/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoRuntimeOptions;

public class RistrettoCompilationManager {
    /**
     * The global compilation manager - initialized once at startup in a startup hook and teared
     * down in a teardown hook. Note that this field is NOT volatile and that it could be written
     * from other places. However, we expect this is not going to happen, it is not exposed and only
     * a single well-defined place writes to it.
     */
    private static volatile RistrettoCompilationManager MANAGER;

    public static RuntimeSupport.Hook getProfileSupportShutdownHook() {
        return _ -> {
            get().shutDown();
        };
    }

    public static RuntimeSupport.Hook getProfileSupportStartupHook() {
        return _ -> {
            MANAGER = new RistrettoCompilationManager();
        };
    }

    public static RistrettoCompilationManager get() {
        return MANAGER;
    }

    /**
     * The actual queue of compilation requests, propagated by profiling logic called by the
     * interpreter.
     */
    private final PriorityBlockingQueue<RistrettoCompilationRequest> compilationQueue;
    /**
     * The background threadpool that works off all compilation tasks.
     */
    private final ExecutorService compilerExecutorService;
    /**
     * A list of all compilations performed so far. Normally not used. ONLY USED BY TEST CODE.
     */
    private final List<RistrettoCompilationRequest> performedCompilations;
    /**
     * A list of all exceptions seen during compilation. Normally not used. ONLY USED BY TEST CODE.
     */
    private final List<Throwable> compilerExceptions;
    /**
     * The number of all compilation requests submitted.
     */
    private final AtomicLong submittedRequests;
    /**
     * The number of all compilation requests started.
     */
    private final AtomicLong startedRequests;
    /**
     * The number of all compilation requests finished.
     */
    private final AtomicLong finishedRequests;

    public RistrettoCompilationManager() {
        compilerExceptions = Collections.synchronizedList(new ArrayList<>());
        final int compilerThreadCount = RistrettoRuntimeOptions.JITCompilerThreadCount.getValue();
        compilerExecutorService = Executors.newFixedThreadPool(compilerThreadCount);
        compilationQueue = new PriorityBlockingQueue<>();
        performedCompilations = Collections.synchronizedList(new ArrayList<>());
        submittedRequests = new AtomicLong();
        startedRequests = new AtomicLong();
        finishedRequests = new AtomicLong();

        for (int i = 0; i < compilerThreadCount; i++) {
            compilerExecutorService.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        var task = compilationQueue.take();
                        startedRequests.incrementAndGet();
                        try {
                            task.call();
                        } catch (Throwable e) {
                            RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilation, "[Ristretto Compiler]Compiler saw exception %s %s", Thread.currentThread(), e.getMessage());
                            compilerExceptions.add(e);
                        } finally {
                            // even if we fail we want to record the compilation
                            if (TestingBackdoor.recordUninstallTasks()) {
                                performedCompilations.add(task);
                            }
                            finishedRequests.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        // swallow and abort, we must break because the interrupted exception
                        // clears the interrupted flag and we would be waiting indefinitely else
                        break;
                    }
                }
            });
        }
    }

    public void submitCompilationRequest(RistrettoCompilationRequest request) {
        submittedRequests.incrementAndGet();
        RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Submitting compilation request %s %n", request);
        compilationQueue.add(request);
        RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Queue size after submitting %s =%s %n", request, compilationQueue.size());
    }

    public void shutDown() {
        // we want this to be fast, tear down the world
        compilerExecutorService.shutdownNow();
        try {
            while (!compilerExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                Log.log().string("Termination of JIT Compilation Manager does not work, waiting").newline();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class TestingBackdoor {
        private TestingBackdoor() {
            // do not allow an allocation of the backdoor
        }

        public static List<Throwable> getCompilerExceptions(RistrettoCompilationManager compilationManager) {
            return compilationManager.compilerExceptions;
        }

        public static synchronized void reset() {
            RistrettoCompilationManager m = get();
            RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "Invalidating and resetting %s compilations%n",
                            m.performedCompilations.size());

            for (var task : m.performedCompilations) {
                RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "Invalidating and resetting %s%n", task.getRMethod());
                task.getRMethod().compilationState = RistrettoConstants.COMPILE_STATE_INIT_VAL;
                if (task.getRMethod().installedCode != null) {
                    /*
                     * TODO GR-71160 - revert code installation, same as what deoptimization has to
                     * do.
                     */
                    task.getRMethod().installedCode.invalidate();
                }
                task.getRMethod().installedCode = null;
                task.getRMethod().profile = null;
            }
            m.performedCompilations.clear();
            m.submittedRequests.set(0);
            m.startedRequests.set(0);
            m.finishedRequests.set(0);
            if (!UNINSTALL_TASKS.isEmpty()) {
                Log.log().string("Uninstalling ").signed(UNINSTALL_TASKS.size()).string(" tasks").newline();
                for (Runnable task : UNINSTALL_TASKS) {
                    task.run();
                }
                UNINSTALL_TASKS.clear();
            }
        }

        /**
         * Here are dragons - this waits until the queue is empty, only use in test scenarios.
         */
        public static void blockUntilCompileQueueDrained() {
            final RistrettoCompilationManager m = get();
            RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Draining compile queue, submitted=%s, started=%s, finished=%s%n",
                            m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

            final boolean startLogDuringDrain = RistrettoRuntimeOptions.JITTraceCompilationQueuing.getValue();
            long msWaited = 0;
            final long msWaitTime = 50/* ms */;
            while (m.finishedRequests.get() != m.submittedRequests.get()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(msWaitTime);
                    msWaited += msWaitTime;
                    if (startLogDuringDrain && msWaited > 0 && msWaited % 5_000L == 0) {
                        RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing,
                                        "[Ristretto Compile Queue]Still draining compile queue, submitted=%s, started=%s, finished=%s%n",
                                        m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            RistrettoProfileSupport.trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing,
                            "[Ristretto Compile Queue]Done draining compile queue, submitted=%s, started=%s, finished=%s%n",
                            m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

        }

        public static final List<Runnable> UNINSTALL_TASKS = Collections.synchronizedList(new ArrayList<>());

        public static synchronized void recordUninstallTask(Runnable task) {
            UNINSTALL_TASKS.add(task);
        }

        /**
         * Install code and not only compile it.
         */
        public static boolean installCode() {
            String prop = System.getProperty("com.oracle.svm.interpreter.ristretto.profile.backdoor.InstallCode", "true");
            return Boolean.parseBoolean(prop);
        }

        /**
         * Determines if the compiler should record tasks to be able to revert code installation.
         */
        public static boolean recordUninstallTasks() {
            String prop = System.getProperty("com.oracle.svm.interpreter.ristretto.profile.backdoor.UninstallTasks", "false");
            return Boolean.parseBoolean(prop);
        }
    }

}
