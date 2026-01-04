/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import com.oracle.graal.pointsto.BigBang;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Activation;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugContext.Scope;
import jdk.graal.compiler.debug.DebugDumpHandlersFactory;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.common.JVMCIError;

/**
 * An extended version of a {@link ThreadPoolExecutor} that can block until all posted operations
 * are completed.
 */
public class CompletionExecutor {

    private enum State {
        BEFORE_START,
        STARTED,
        UNUSED
    }

    private final AtomicReference<State> state;
    private final LongAdder postedOperations;
    private final LongAdder completedOperations;
    private List<DebugContextRunnable> postedBeforeStart;
    private final CopyOnWriteArrayList<Throwable> exceptions = new CopyOnWriteArrayList<>();

    private final DebugContext debug;
    private final BigBang bb;
    private Timing timing;

    public interface Timing {
        long getPrintIntervalNanos();

        void addScheduled(DebugContextRunnable r);

        void addCompleted(DebugContextRunnable r, long nanos);

        void printHeader();

        void print();
    }

    public CompletionExecutor(DebugContext debugContext, BigBang bb) {
        this.debug = debugContext.areScopesEnabled() || debugContext.areMetricsEnabled() ? debugContext : null;
        this.bb = bb;
        state = new AtomicReference<>(State.UNUSED);
        postedOperations = new LongAdder();
        completedOperations = new LongAdder();
    }

    public void init() {
        init(null);
    }

    public void init(Timing newTiming) {
        timing = newTiming;
        setState(State.BEFORE_START);
        postedOperations.reset();
        completedOperations.reset();
        postedBeforeStart = Collections.synchronizedList(new ArrayList<>());
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
         * {@link DebugContext#disabled} is used by default to avoid the cost of creating a
         * {@link DebugContext}, so the task should override this if one is needed.
         */
        default DebugContext getDebug(@SuppressWarnings("unused") OptionValues options, @SuppressWarnings("unused") List<DebugDumpHandlersFactory> factories) {
            return DebugContext.disabled(null);
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
                executeService(command);
                break;
            default:
                throw JVMCIError.shouldNotReachHere();
        }
    }

    private void executeService(DebugContextRunnable command) {
        ForkJoinPool.commonPool().execute(() -> executeCommand(command));
    }

    @SuppressWarnings("try")
    private void executeCommand(DebugContextRunnable command) {
        long startTime = 0L;
        if (timing != null) {
            startTime = System.nanoTime();
        }
        bb.getHostVM().recordActivity();
        Throwable thrown = null;
        try (DebugContext localDebug = command.getDebug(bb.getOptions(), bb.getDebugHandlerFactories());
                        Scope s = localDebug.scope("Operation");
                        Activation a = localDebug.activate()) {
            command.run(localDebug);
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
    }

    public void start() {
        assert state.get() == State.BEFORE_START : state.get();

        setState(State.STARTED);
        postedBeforeStart.forEach(this::execute);
        postedBeforeStart = null;
    }

    private void setState(State newState) {
        state.set(newState);
    }

    @SuppressWarnings("unused")
    public long complete() throws InterruptedException {
        long lastPrint = 0;
        if (timing != null) {
            timing.printHeader();
            timing.print();
            lastPrint = System.nanoTime();
        }

        try {
            while (true) {
                assert state.get() == State.STARTED : state.get();

                boolean quiescent = ForkJoinPool.commonPool().awaitQuiescence(100, TimeUnit.MILLISECONDS);
                if (timing != null && !quiescent) {
                    long curTime = System.nanoTime();
                    if (curTime - lastPrint > timing.getPrintIntervalNanos()) {
                        timing.print();
                        lastPrint = curTime;
                    }
                }

                long completed = completedOperations.sum();
                long posted = postedOperations.sum();
                assert completed <= posted : completed + ", " + posted;
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
        } finally {
            if (debug != null) {
                debug.closeDumpHandlers(true);
            }
        }
    }

    public long getPostedOperations() {
        return postedOperations.sum() + (postedBeforeStart == null ? 0 : postedBeforeStart.size());
    }

    public void shutdown() {
        assert !ForkJoinPool.commonPool().hasQueuedSubmissions() : "There should be no queued submissions on shutdown.";
        assert completedOperations.sum() == postedOperations.sum() : "Posted operations (" + postedOperations.sum() + ") must match completed (" + completedOperations.sum() + ") operations";
        setState(State.UNUSED);
    }

    public boolean isBeforeStart() {
        return state.get() == State.BEFORE_START;
    }

    public boolean isStarted() {
        return state.get() == State.STARTED;
    }

    public State getState() {
        return state.get();
    }
}
