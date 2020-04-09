/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugContext.Scope;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.graal.pointsto.BigBang;

import jdk.vm.ci.common.JVMCIError;

/**
 * An extended version of a {@link ThreadPoolExecutor} that can block until all posted operations
 * are completed.
 */
public final class CompletionExecutor {

    private enum State {
        BEFORE_START,
        STARTED,
        UNUSED
    }

    private final AtomicReference<State> state;
    private final LongAdder postedOperations;
    private final LongAdder completedOperations;
    private final List<DebugContextRunnable> postedBeforeStart;
    private volatile CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

    private final ForkJoinPool executorService;
    private final Runnable heartbeatCallback;

    private BigBang bb;
    private Timing timing;
    private Object vmConfig;

    public interface Timing {
        long getPrintIntervalNanos();

        void addScheduled(DebugContextRunnable r);

        void addCompleted(DebugContextRunnable r, long nanos);

        void printHeader();

        void print();
    }

    public CompletionExecutor(BigBang bb, ForkJoinPool forkJoin, Runnable heartbeatCallback) {
        this.bb = bb;
        this.heartbeatCallback = heartbeatCallback;
        executorService = forkJoin;
        state = new AtomicReference<>(State.UNUSED);
        postedOperations = new LongAdder();
        completedOperations = new LongAdder();
        postedBeforeStart = new ArrayList<>();
    }

    public void init() {
        init(null);
    }

    public void init(Timing newTiming) {
        assert isSequential() || !executorService.hasQueuedSubmissions();

        timing = newTiming;
        setState(State.BEFORE_START);
        postedOperations.reset();
        completedOperations.reset();
        postedBeforeStart.clear();
        vmConfig = bb.getHostVM().getConfiguration();
    }

    /**
     * Interface implemented by tasks that want to be run via
     * {@link CompletionExecutor#execute(DebugContextRunnable)}.
     */
    public interface DebugContextRunnable {
        void run(DebugContext debug);

        /**
         * Gets a description for the {@link DebugContext} used for this task.
         */
        default Description getDescription() {
            return null;
        }

        /**
         * Gets a {@link DebugContext} the executor will use for this task.
         *
         * A task can override this and return {@link DebugContext#disabled} to avoid the cost of
         * creating a {@link DebugContext} if one is not needed.
         */
        default DebugContext getDebug(OptionValues options, List<DebugHandlersFactory> factories) {
            return new Builder(options, factories).description(getDescription()).build();
        }
    }

    @SuppressWarnings("try")
    public void execute(DebugContextRunnable command) {
        if (!exceptions.isEmpty()) {
            // Don't add more work if a task already failed with an exception.
            return;
        }

        switch (state.get()) {
            case UNUSED:
                throw JVMCIError.shouldNotReachHere();
            case BEFORE_START:
                postedBeforeStart.add(command);
                break;
            case STARTED:
                postedOperations.increment();
                if (timing != null) {
                    timing.addScheduled(command);
                }

                if (isSequential()) {
                    heartbeatCallback.run();
                    try (DebugContext debug = command.getDebug(bb.getOptions(), bb.getDebugHandlerFactories());
                                    Scope s = debug.scope("Operation")) {
                        command.run(debug);
                    }
                    completedOperations.increment();
                } else {
                    executorService.execute(() -> {
                        bb.getHostVM().installInThread(vmConfig);
                        long startTime = 0L;
                        if (timing != null) {
                            startTime = System.nanoTime();
                        }
                        heartbeatCallback.run();
                        Throwable thrown = null;
                        try (DebugContext debug = command.getDebug(bb.getOptions(), bb.getDebugHandlerFactories());
                                        Scope s = debug.scope("Operation");
                                        Activation a = debug.activate()) {
                            command.run(debug);
                        } catch (Throwable x) {
                            thrown = x;
                        } finally {
                            bb.getHostVM().clearInThread();
                            if (timing != null) {
                                long taskTime = System.nanoTime() - startTime;
                                timing.addCompleted(command, taskTime);
                            }

                            if (thrown != null) {
                                exceptions.add(thrown);
                            }
                            completedOperations.increment();
                        }
                    });
                }

                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    public void start() {
        assert state.get() == State.BEFORE_START;

        setState(State.STARTED);
        postedBeforeStart.forEach(this::execute);
        postedBeforeStart.clear();
    }

    private void setState(State newState) {
        state.set(newState);
    }

    public long complete() throws InterruptedException {

        if (isSequential()) {
            long completed = completedOperations.sum();
            long posted = postedOperations.sum();
            assert completed == posted;
            return posted;
        }

        long lastPrint = 0;
        if (timing != null) {
            timing.printHeader();
            timing.print();
            lastPrint = System.nanoTime();
        }

        while (true) {
            assert state.get() == State.STARTED;

            boolean quiescent = executorService.awaitTermination(100, TimeUnit.MILLISECONDS);
            if (timing != null && !quiescent) {
                long curTime = System.nanoTime();
                if (curTime - lastPrint > timing.getPrintIntervalNanos()) {
                    timing.print();
                    lastPrint = curTime;
                }
            }

            long completed = completedOperations.sum();
            long posted = postedOperations.sum();
            assert completed <= posted;
            if (completed == posted && exceptions.isEmpty()) {
                if (timing != null) {
                    timing.print();
                }

                return posted;
            }
            if (!exceptions.isEmpty()) {
                setState(State.UNUSED);
                throw new ParallelExecutionException(exceptions);
            }
        }
    }

    public long getPostedOperations() {
        return postedOperations.sum() + postedBeforeStart.size();
    }

    public boolean isSequential() {
        return executorService == null;
    }

    public void shutdown() {
        assert isSequential() || !executorService.hasQueuedSubmissions() : "There should be no queued submissions on shutdown.";
        assert completedOperations.sum() == postedOperations.sum() : "Posted operations (" + postedOperations.sum() + ") must match completed (" + completedOperations.sum() + ") operations";
        setState(State.UNUSED);
    }

    public boolean isStarted() {
        return state.get() == State.STARTED;
    }

    public ForkJoinPool getExecutorService() {
        return executorService;
    }
}
