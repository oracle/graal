/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.guest.staging.jdk.RuntimeSupport;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;

import jdk.graal.compiler.core.CompilationWatchDog;
import jdk.graal.compiler.core.common.CompilationIdentifier;

public final class RistrettoCompilationManager {
    private static final class RistrettoCompilerThread extends Thread implements RuntimeClassLoading.NoClassLoadingThread {
        /**
         * Creates a worker without running application thread-local callbacks during startup.
         */
        RistrettoCompilerThread(Runnable target, int workerIndex) {
            super(null, target, "Ristretto Compiler-" + workerIndex, 0, false);
            setDaemon(true);
        }

        /**
         * Wakes workers in the native queue wait when generic isolate teardown interrupts them.
         */
        @Override
        public void interrupt() {
            try {
                super.interrupt();
            } finally {
                /*
                 * The JDK sets the interrupt flag before the parker allocation, which may fail.
                 * Wake native queue waiters even if that allocation throws.
                 */
                if (QUEUE_MUTEX.isOwner()) {
                    QUEUE_CONDITION.broadcast();
                } else {
                    try (var _ = QUEUE_MUTEX.lock()) {
                        QUEUE_CONDITION.broadcast();
                    }
                }
            }
        }
    }

    /**
     * Global compilation manager instance. Created lazily on first access.
     */
    private static volatile RistrettoCompilationManager MANAGER;

    /*
     * Protects compilation requests and the watchdog's live-queue view.
     */
    private static final VMMutex QUEUE_MUTEX = new VMMutex("ristrettoCompilationQueue");
    private static final VMCondition QUEUE_CONDITION = new VMCondition(QUEUE_MUTEX, "workAvailable");
    private static final VMCondition WORKERS_EXITED = new VMCondition(QUEUE_MUTEX, "workersExited");

    private static final int MAX_QUEUED_REQUESTS_TO_REPORT = 5;

    private static final CompilationWatchDog.EventHandler COMPILATION_WATCH_DOG_EVENT_HANDLER = new CompilationWatchDog.EventHandler() {
        @Override
        public void onLongCompilation(CompilationWatchDog watchDog, Thread watched, CompilationIdentifier compilation, long elapsed, StackTraceElement[] stackTrace) {
            RistrettoCompilationManager manager = MANAGER;
            if (manager == null) {
                CompilationWatchDog.EventHandler.super.onLongCompilation(watchDog, watched, compilation, elapsed, stackTrace);
            } else {
                manager.reportLongRunningCompilation(watched, compilation, elapsed, stackTrace);
            }
        }
    };

    public static CompilationWatchDog.EventHandler getCompilationWatchDogEventHandler() {
        return COMPILATION_WATCH_DOG_EVENT_HANDLER;
    }

    /**
     * Returns the isolate tear-down hook that stops the background Ristretto compiler threads.
     */
    public static RuntimeSupport.Hook getProfileSupportTearDownHook() {
        return _ -> {
            RistrettoCompilationManager manager = MANAGER;
            if (manager != null) {
                manager.shutDown();
            }
        };
    }

    public static RistrettoCompilationManager get() {
        RistrettoCompilationManager manager = MANAGER;
        if (manager == null) {
            synchronized (RistrettoCompilationManager.class) {
                manager = MANAGER;
                if (manager == null) {
                    manager = new RistrettoCompilationManager();
                    MANAGER = manager;
                }
            }
        }
        return manager;
    }

    /**
     * Compilation requests ordered by priority. Guarded by {@link #QUEUE_MUTEX}.
     */
    private final PriorityQueue<RistrettoCompilationRequest> compilationQueue;

    /**
     * First request in the watchdog's intrusive live-queue view. Guarded by {@link #QUEUE_MUTEX}.
     */
    private RistrettoCompilationRequest firstQueuedRequestToReport;

    /**
     * Last request in the watchdog's live-queue view. Guarded by {@link #QUEUE_MUTEX}.
     */
    private RistrettoCompilationRequest lastQueuedRequestToReport;

    /**
     * Number of requests in the watchdog's live-queue view. Guarded by {@link #QUEUE_MUTEX}.
     */
    private int queuedRequestsToReportCount;

    /**
     * The platform threads that run the compilation queue.
     */
    private final List<Thread> compilerThreads;

    /**
     * Number of worker loops admitted to the queue that have yet to exit. Guarded by
     * {@link #QUEUE_MUTEX}.
     */
    private int activeCompilerWorkers;

    /**
     * Guarded by {@link #QUEUE_MUTEX}; false once shutdown has started.
     */
    private boolean acceptingRequests;

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

    private final AtomicLong compilationWatchDogReportCount;

    /**
     * The daemon reporter thread that periodically dumps compiler statistics.
     */
    private final Thread statisticsReporterThread;

    /**
     * Period, in seconds, between statistics dumps.
     */
    private final long statisticsReporterPeriodSeconds;

    private RistrettoCompilationManager() {
        compilerExceptions = Collections.synchronizedList(new ArrayList<>());
        final int compilerThreadCount = RistrettoOptions.JITCompilerThreadCount.getValue();
        statisticsReporterPeriodSeconds = RistrettoOptions.JITTraceCompilerStatisticsPeriodSeconds.getValue();
        compilerThreads = new ArrayList<>(compilerThreadCount);
        acceptingRequests = true;
        compilationQueue = new PriorityQueue<>();
        performedCompilations = Collections.synchronizedList(new ArrayList<>());
        submittedRequests = new AtomicLong();
        startedRequests = new AtomicLong();
        finishedRequests = new AtomicLong();
        compilationWatchDogReportCount = new AtomicLong();
        try {
            for (int i = 0; i < compilerThreadCount; i++) {
                Thread worker = new RistrettoCompilerThread(this::runCompilerWorker, i);
                worker.start();
                compilerThreads.add(worker);
            }
            statisticsReporterThread = startStatisticsReporterThread();
        } catch (Throwable failure) {
            try {
                abortStartup();
            } catch (Throwable cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * Stops a partially started manager before initialization can retry. Its workers must leave the
     * shared queue condition so they cannot consume notifications intended for a replacement
     * manager. The native condition waits without consuming the application thread's park permit
     * or interrupt status.
     */
    private void abortStartup() {
        try (var _ = QUEUE_MUTEX.lock()) {
            acceptingRequests = false;
            QUEUE_CONDITION.broadcast();
            while (activeCompilerWorkers != 0) {
                WORKERS_EXITED.block();
            }
        }
    }

    /**
     * Processes queued requests until this worker is interrupted or the manager closes admission.
     */
    private void runCompilerWorker() {
        try (var _ = QUEUE_MUTEX.lock()) {
            /* Startup rollback may have closed admission before this thread entered its target. */
            if (!acceptingRequests) {
                return;
            }
            activeCompilerWorkers++;
        }
        try {
            while (true) {
                RistrettoCompilationRequest request = takeCompilationRequest();
                if (request == null) {
                    return;
                }
                processCompilationRequest(request);
            }
        } finally {
            compilerWorkerExited();
        }
    }

    /**
     * Records the exit of a worker that entered the loop.
     */
    private void compilerWorkerExited() {
        try (var _ = QUEUE_MUTEX.lock()) {
            if (--activeCompilerWorkers == 0) {
                WORKERS_EXITED.broadcast();
            }
        }
    }

    /**
     * Waits for work until shutdown starts or the worker is interrupted.
     */
    private RistrettoCompilationRequest takeCompilationRequest() {
        Thread worker = Thread.currentThread();
        try (var _ = QUEUE_MUTEX.lock()) {
            while (compilationQueue.isEmpty() && acceptingRequests && !worker.isInterrupted()) {
                QUEUE_CONDITION.block();
            }
            if (!acceptingRequests || worker.isInterrupted()) {
                return null;
            }
            return pollCompilationRequest();
        }
    }

    /**
     * Removes the next compilation request and unlinks it from watchdog tracking if needed.
     */
    private RistrettoCompilationRequest pollCompilationRequest() {
        assert QUEUE_MUTEX.isOwner() : "Polling a compilation request requires the compilation queue mutex.";
        RistrettoCompilationRequest request = compilationQueue.poll();
        if (request != null && request.sampledForCompilationWatchdog) {
            removeQueuedRequestToReport(request);
        }
        return request;
    }

    /**
     * Returns the number of queued compilation requests.
     */
    private int compilationQueueSize() {
        try (var _ = QUEUE_MUTEX.lock()) {
            return compilationQueue.size();
        }
    }

    /**
     * Returns a stable queue snapshot for test diagnostics.
     */
    private List<RistrettoCompilationRequest> compilationQueueSnapshot() {
        try (var _ = QUEUE_MUTEX.lock()) {
            return new ArrayList<>(compilationQueue);
        }
    }

    private void processCompilationRequest(RistrettoCompilationRequest task) {
        startedRequests.incrementAndGet();
        try {
            if (task.call() != null) {
                RistrettoDiagnostics.SuccessfulCompiles.incrementAndGet();
            }
        } catch (Throwable e) {
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilation, "[Ristretto Compiler]Compiler saw exception %s %s", Thread.currentThread(), e.getMessage());
            if (RistrettoOptions.JITPrintExceptions.getValue()) {
                Log.log().exception(e);
            }
            RistrettoDiagnostics.CompilerExceptionsSeen.incrementAndGet();
            compilerExceptions.add(e);
        } finally {
            // even if we fail we want to record the compilation
            if (TestingBackdoor.recordUninstallTasks()) {
                performedCompilations.add(task);
            }
            task.markCompleted();
            finishedRequests.incrementAndGet();
        }
    }

    private void reportLongRunningCompilation(Thread watched, CompilationIdentifier compilation, long elapsedNanos, StackTraceElement[] stackTrace) {
        Log log = Log.log();
        log.string("[Ristretto Compilation Watchdog] Long-running compilation observedMs=").signed(TimeUnit.NANOSECONDS.toMillis(elapsedNanos)).string(" compilation=")
                        .string(compilation.toString()).string("; ").string(describeStatistics()).newline();
        if (watched != null) {
            log.string("[Ristretto Compilation Watchdog] Compiler thread name=\"").string(watched.getName()).string("\" state=").string(watched.getState().name()).newline();
        }
        if (stackTrace != null) {
            for (StackTraceElement element : stackTrace) {
                log.string("    at ").string(element.toString()).newline();
            }
        }
        List<RistrettoCompilationRequest> queueSample;
        int trackedQueueSize;
        int queueSize;
        try (var _ = QUEUE_MUTEX.lock()) {
            queueSample = queuedRequestsToReportSnapshot();
            trackedQueueSize = queuedRequestsToReportCount;
            queueSize = compilationQueue.size();
        }
        long nowNanos = System.nanoTime();
        log.string("[Ristretto Compilation Watchdog] Queue sample tracked=").signed(trackedQueueSize).string(" total=").signed(queueSize).string(" complete=")
                        .bool(trackedQueueSize == queueSize).newline();
        int queuedRequestIndex = 0;
        for (RistrettoCompilationRequest request : queueSample) {
            long submittedAtNanos = request.getSubmittedAtNanos();
            log.string("[Ristretto Compilation Watchdog] Queued request index=").signed(queuedRequestIndex).string(" ageMs=");
            if (submittedAtNanos == 0) {
                log.string("unknown");
            } else {
                log.signed(TimeUnit.NANOSECONDS.toMillis(nowNanos - submittedAtNanos));
            }
            log.string(" request=").string(request.toString()).newline();
            queuedRequestIndex++;
        }
        log.flush();
        compilationWatchDogReportCount.incrementAndGet();
    }

    private void addQueuedRequestToReport(RistrettoCompilationRequest request) {
        assert QUEUE_MUTEX.isOwner() : "Adding a queued request requires the compilation queue mutex.";
        request.sampledForCompilationWatchdog = true;
        request.previousQueuedRequestToReport = lastQueuedRequestToReport;
        if (lastQueuedRequestToReport == null) {
            firstQueuedRequestToReport = request;
        } else {
            lastQueuedRequestToReport.nextQueuedRequestToReport = request;
        }
        lastQueuedRequestToReport = request;
        queuedRequestsToReportCount++;
    }

    private void removeQueuedRequestToReport(RistrettoCompilationRequest request) {
        assert QUEUE_MUTEX.isOwner() : "Removing a queued request requires the compilation queue mutex.";
        RistrettoCompilationRequest previous = request.previousQueuedRequestToReport;
        RistrettoCompilationRequest next = request.nextQueuedRequestToReport;
        if (previous == null) {
            firstQueuedRequestToReport = next;
        } else {
            previous.nextQueuedRequestToReport = next;
        }
        if (next == null) {
            lastQueuedRequestToReport = previous;
        } else {
            next.previousQueuedRequestToReport = previous;
        }
        request.previousQueuedRequestToReport = null;
        request.nextQueuedRequestToReport = null;
        request.sampledForCompilationWatchdog = false;
        queuedRequestsToReportCount--;
    }

    /**
     * Copies the bounded watchdog sample while the caller holds the queue mutex.
     */
    private List<RistrettoCompilationRequest> queuedRequestsToReportSnapshot() {
        assert QUEUE_MUTEX.isOwner() : "Sampling queued requests requires the compilation queue mutex.";
        List<RistrettoCompilationRequest> snapshot = new ArrayList<>(MAX_QUEUED_REQUESTS_TO_REPORT);
        RistrettoCompilationRequest request = firstQueuedRequestToReport;
        while (request != null && snapshot.size() < MAX_QUEUED_REQUESTS_TO_REPORT) {
            snapshot.add(request);
            request = request.nextQueuedRequestToReport;
        }
        return snapshot;
    }

    private void clearQueuedRequestsToReport() {
        try (var _ = QUEUE_MUTEX.lock()) {
            RistrettoCompilationRequest request = firstQueuedRequestToReport;
            while (request != null) {
                RistrettoCompilationRequest next = request.nextQueuedRequestToReport;
                request.previousQueuedRequestToReport = null;
                request.nextQueuedRequestToReport = null;
                request.sampledForCompilationWatchdog = false;
                request = next;
            }
            firstQueuedRequestToReport = null;
            lastQueuedRequestToReport = null;
            queuedRequestsToReportCount = 0;
        }
    }

    private Thread startStatisticsReporterThread() {
        if (!RistrettoOptions.JITTraceCompilerStatistics.getValue()) {
            return null;
        }
        Thread reporter = new Thread(null, () -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    TimeUnit.SECONDS.sleep(statisticsReporterPeriodSeconds);
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    reportStatistics();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                Log.log().exception(t);
            }
        }, "Ristretto Compiler Statistics Reporter", 0, false);
        reporter.setDaemon(true);
        reporter.start();
        return reporter;
    }

    private void reportStatistics() {
        Log.log().string(describeStatistics()).newline();
    }

    public StatisticsSnapshot snapshotStatistics() {
        return new StatisticsSnapshot(
                        submittedRequests.get(),
                        startedRequests.get(),
                        finishedRequests.get(),
                        RistrettoDiagnostics.SuccessfulCompiles.get(),
                        RistrettoDiagnostics.CompilerExceptionsSeen.get(),
                        compilationQueueSize(),
                        RistrettoDiagnostics.DeoptimizationsTaken.get(),
                        RistrettoDiagnostics.InvalidatedCode.get(),
                        RistrettoDiagnostics.ReprofileRequested.get());
    }

    public String describeStatistics() {
        return snapshotStatistics().format();
    }

    public record StatisticsSnapshot(long submittedRequests, long startedRequests, long finishedRequests, long successfulCompiles, long compilerExceptionsSeen, int queueSize,
                    long deoptimizationsTaken, long invalidatedCode, long reprofileRequested) {
        long nonSuccessfulCompletions() {
            return Math.max(0L, finishedRequests - successfulCompiles);
        }

        long inflightRequests() {
            return Math.max(0L, startedRequests - finishedRequests);
        }

        String format() {
            return new StringBuilder(256)
                            .append("[Ristretto Compiler Stats] submitted=").append(submittedRequests)
                            .append(" started=").append(startedRequests)
                            .append(" finished=").append(finishedRequests)
                            .append(" successful=").append(successfulCompiles)
                            .append(" failed=").append(nonSuccessfulCompletions())
                            .append(" queue=").append(queueSize)
                            .append(" inflight=").append(inflightRequests())
                            .append(" compilerExceptions=").append(compilerExceptionsSeen)
                            .append(" deopts=").append(deoptimizationsTaken)
                            .append(" invalidations=").append(invalidatedCode)
                            .append(" reprofiles=").append(reprofileRequested)
                            .toString();
        }
    }

    public void submitCompilationRequest(RistrettoCompilationRequest request) {
        int queueSize;
        try (var _ = QUEUE_MUTEX.lock()) {
            boolean compilationWatchdogEnabled = RistrettoOptions.JITCompilationWatchdogTimeoutSeconds.getValue() > 0;
            request.markSubmitted(compilationWatchdogEnabled ? System.nanoTime() : 0);
            submittedRequests.incrementAndGet();
            if (!acceptingRequests) {
                cancelRequestBeforeExecution(request);
                return;
            }
            try {
                compilationQueue.add(request);
            } catch (RuntimeException | Error failure) {
                cancelRequestBeforeExecution(request);
                throw failure;
            }
            if (compilationWatchdogEnabled) {
                addQueuedRequestToReport(request);
            }
            QUEUE_CONDITION.signal();
            queueSize = compilationQueue.size();
        }
        RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Queue size after submitting %s =%s %n", request, queueSize);
    }

    /**
     * Submits one compilation request and waits until it has either installed code, failed, or been
     * cancelled before execution. Once this method returns, that request can no longer publish code.
     */
    public void submitCompilationRequestAndWait(RistrettoCompilationRequest request) {
        submitCompilationRequest(request);
        request.awaitCompletion();
    }

    /**
     * Submits one invocation-entry or OSR compilation request. Ordinary mixed mode returns after
     * queuing; Xbatch and Xcomp wait for the request's completion before returning to interpreted
     * execution.
     */
    public void submitCompilationRequestForCurrentMode(RistrettoCompilationRequest request) {
        if (RistrettoOptions.useBlockingCompilation()) {
            submitCompilationRequestAndWait(request);
        } else {
            submitCompilationRequest(request);
        }
    }

    private void cancelRequestBeforeExecution(RistrettoCompilationRequest request) {
        request.cancelBeforeExecution();
        finishedRequests.incrementAndGet();
    }

    public void shutDown() {
        // we want this to be fast, tear down the world
        try (var _ = QUEUE_MUTEX.lock()) {
            acceptingRequests = false;
            QUEUE_CONDITION.broadcast();
            RistrettoCompilationRequest request;
            while ((request = pollCompilationRequest()) != null) {
                cancelRequestBeforeExecution(request);
            }
        }
        for (int i = 0; i < compilerThreads.size(); i++) {
            compilerThreads.get(i).interrupt();
        }
        if (statisticsReporterThread != null) {
            statisticsReporterThread.interrupt();
        }
        try {
            for (int i = 0; i < compilerThreads.size(); i++) {
                Thread worker = compilerThreads.get(i);
                while (worker.isAlive()) {
                    worker.join(TimeUnit.SECONDS.toMillis(5));
                    if (worker.isAlive()) {
                        Log.log().string("Termination of JIT Compilation Manager does not work, waiting").newline();
                    }
                }
            }
            if (statisticsReporterThread != null) {
                statisticsReporterThread.join(TimeUnit.SECONDS.toMillis(5));
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

        public static long getProgressWatchdogReportCount() {
            return get().compilationWatchDogReportCount.get();
        }

        public static int getQueuedRequestsToReportCount() {
            RistrettoCompilationManager manager = get();
            try (var _ = QUEUE_MUTEX.lock()) {
                return manager.queuedRequestsToReportSnapshot().size();
            }
        }

        public static int getCompletionWaiterCount(RistrettoCompilationRequest request) {
            return request.completionWaiterCount();
        }

        public static void completeRequestForTesting(RistrettoCompilationRequest request) {
            request.markCompleted();
        }

        /**
         * Drains pending requests and resets recorded test compilations, invalidating their
         * invocation-entry and OSR code and resetting their profiles. Callers must prevent new
         * requests and concurrent guest execution during cleanup.
         *
         * Only recorded compilation requests are reset. Recording is controlled by the
         * {@code com.oracle.svm.interpreter.ristretto.profile.backdoor.UninstallTasks} system property.
         * Tests must separately clean up directly installed code. Profiles of methods
         * that only ran interpreted and never submitted a recorded compilation are not reset.
         */
        public static synchronized void reset() {
            RistrettoCompilationManager m = get();
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "Invalidating and resetting %s compilations%n",
                            m.performedCompilations.size());

            // wait until we finished everything, else resetting state bits might cause problems
            blockUntilCompileQueueDrained();

            for (var task : m.performedCompilations) {
                RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "Invalidating and resetting %s%n", task.getRMethod());
                task.getRMethod().resetCompilationStateForTesting();
                if (task.getRMethod().installedCode != null) {
                    /*
                     * TODO GR-71160 - revert code installation, same as what deoptimization has to
                     * do.
                     */
                    task.getRMethod().installedCode.invalidate();
                }
                task.getRMethod().installedCode = null;
                task.getRMethod().invalidateOSRInstalledCode();
                task.getRMethod().resetProfile();
            }
            m.compilerExceptions.clear();
            m.performedCompilations.clear();
            m.submittedRequests.set(0);
            m.startedRequests.set(0);
            m.finishedRequests.set(0);
            m.compilationWatchDogReportCount.set(0);
            m.clearQueuedRequestsToReport();
            RistrettoDiagnostics.reset();
        }

        /**
         * Here are dragons - this waits until the queue is empty, only use in test scenarios.
         */
        public static void blockUntilCompileQueueDrained() {
            final RistrettoCompilationManager m = get();
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Draining compile queue, submitted=%s, started=%s, finished=%s%n",
                            m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

            final boolean startLogDuringDrain = RistrettoOptions.JITTraceCompilationQueuing.getValue();
            long msWaited = 0;
            final long msWaitTime = 50/* ms */;
            while (m.finishedRequests.get() != m.submittedRequests.get()) {
                try {
                    TimeUnit.MILLISECONDS.sleep(msWaitTime);
                    msWaited += msWaitTime;
                    if (startLogDuringDrain && msWaited > 0 && msWaited % 5_000L == 0) {
                        RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing,
                                        "[Ristretto Compile Queue]Still draining compile queue, submitted=%s, started=%s, finished=%s%n",
                                        m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

                        for (var q : m.compilationQueueSnapshot()) {
                            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing,
                                            "\t[Ristretto Compile Queue]Queued entry %s%n", q);
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing,
                            "[Ristretto Compile Queue]Done draining compile queue, submitted=%s, started=%s, finished=%s%n",
                            m.submittedRequests.get(), m.startedRequests.get(), m.finishedRequests.get());

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
