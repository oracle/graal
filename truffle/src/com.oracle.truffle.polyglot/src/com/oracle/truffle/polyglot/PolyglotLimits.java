/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.graalvm.polyglot.ResourceLimitEvent;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Source;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;

/**
 * Limits objects that backs the {@link ResourceLimits} API object.
 */
final class PolyglotLimits {

    final long statementLimit;
    final Predicate<Source> statementLimitSourcePredicate;
    final Duration timeLimit;
    final Duration timeAccuracy;
    final Consumer<ResourceLimitEvent> onEvent;

    PolyglotLimits(long statementLimit, Predicate<Source> statementLimitSourcePredicate, Duration timeLimit, Duration timeAccuracy, Consumer<ResourceLimitEvent> onEvent) {
        this.statementLimit = statementLimit;
        this.statementLimitSourcePredicate = statementLimitSourcePredicate;
        this.timeLimit = timeLimit;
        this.timeAccuracy = timeAccuracy;
        this.onEvent = onEvent;
    }

    static void reset(PolyglotContextImpl context) {
        synchronized (context) {
            PolyglotLimits limits = context.config.limits;
            if (limits != null && limits.timeLimit != null) {
                context.resetTiming();
            }
            context.statementCounter = context.statementLimit;
            context.volatileStatementCounter.set(context.statementLimit);
        }
    }

    static final Object CACHED_CONTEXT = new Object() {
        @Override
        public String toString() {
            return "$$$cached_context$$$";
        }
    };

    static final class StatementIncrementNode extends ExecutionEventNode {

        final EngineLimits limits;
        final EventContext eventContext;
        final PolyglotEngineImpl engine;
        final FrameSlot readContext;
        final ConditionProfile needsLookup = ConditionProfile.create();
        final FrameDescriptor descriptor;
        @CompilationFinal private boolean seenInnerContext;

        StatementIncrementNode(EventContext context, EngineLimits limits) {
            this.limits = limits;
            this.eventContext = context;
            this.engine = limits.engine;
            if (!engine.singleThreadPerContext.isValid() || !engine.singleContext.isValid()) {
                descriptor = context.getInstrumentedNode().getRootNode().getFrameDescriptor();
                readContext = descriptor.findOrAddFrameSlot(CACHED_CONTEXT, FrameSlotKind.Object);
            } else {
                readContext = null;
                descriptor = null;
            }
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            PolyglotContextImpl currentContext;
            if (readContext == null || frame.getFrameDescriptor() != descriptor) {
                currentContext = getLimitContext();
            } else {
                try {
                    Object readValue = frame.getObject(readContext);
                    if (needsLookup.profile(readValue == descriptor.getDefaultValue())) {
                        currentContext = getLimitContext();
                        frame.setObject(readContext, currentContext);
                    } else {
                        currentContext = (PolyglotContextImpl) readValue;
                    }
                } catch (FrameSlotTypeException e) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    currentContext = getLimitContext();
                    frame.setObject(readContext, currentContext);
                }
            }

            long count;
            if (engine.singleThreadPerContext.isValid()) {
                count = --currentContext.statementCounter;
            } else {
                count = currentContext.volatileStatementCounter.decrementAndGet();
            }
            if (count < 0) { // overflowed
                CompilerDirectives.transferToInterpreterAndInvalidate();
                notifyStatementLimitReached(currentContext, currentContext.statementLimit - count, currentContext.statementLimit);
            }
        }

        private PolyglotContextImpl getLimitContext() {
            PolyglotContextImpl context = PolyglotContextImpl.currentEntered(engine);
            if (engine.noInnerContexts.isValid() || context.parent == null) {
                // fast path for no inner contexts
                return context;
            }
            if (!seenInnerContext) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                seenInnerContext = true;
            }
            while (context.parent != null) {
                context = context.parent;
            }
            return context;
        }

        private void notifyStatementLimitReached(PolyglotContextImpl context, long actualCount, long limit) {
            boolean limitReached = false;
            synchronized (context) {
                // reset statement counter
                if (limits.engine.singleThreadPerContext.isValid()) {
                    if (context.statementCounter < 0) {
                        context.statementCounter = limit;
                        limitReached = true;
                    }
                } else {
                    if (context.volatileStatementCounter.get() < 0) {
                        context.volatileStatementCounter.set(limit);
                        limitReached = true;
                    }
                }
            }
            if (limitReached) {
                String message = String.format("Statement count limit of %s exceeded. Statements executed %s.",
                                limit, actualCount);
                boolean invalidated = context.invalidate(message);
                if (invalidated) {
                    context.close(context.creatorApi, true);
                    RuntimeException e = limits.notifyEvent(context);
                    if (e != null) {
                        throw e;
                    }
                    throw new CancelExecution(eventContext, message);
                }
            }

        }

    }

    static final class TimeLimitChecker implements Runnable {

        private final WeakReference<PolyglotContextImpl> context;
        private final long timeLimitNS;
        private final EngineLimits limits;
        private FutureTask<?> cancelResult;

        TimeLimitChecker(PolyglotContextImpl context, EngineLimits limits) {
            this.context = new WeakReference<>(context);
            this.timeLimitNS = context.config.limits.timeLimit.toNanos();
            this.limits = limits;
        }

        private static void cancel() {
            throw new RuntimeException("Time Limit Checker Task Cancelled!");
        }

        @Override
        public void run() {
            PolyglotContextImpl c = this.context.get();
            if (cancelResult != null) {
                if (cancelResult.isDone()) {
                    try {
                        cancelResult.get();
                    } catch (Exception e) {
                    }
                    cancel();
                }
                return;
            } else if (c == null || c.closed) {
                cancel();
                return;
            }
            long timeActiveNS = c.getTimeActive();
            if (timeActiveNS > timeLimitNS) {
                if (!c.invalid) {
                    String message = String.format("Time resource limit of %sms exceeded. Time executed %sms.",
                                    c.config.limits.timeLimit.toMillis(),
                                    Duration.ofNanos(timeActiveNS).toMillis());
                    boolean invalidated = c.invalidate(message);
                    /*
                     * We immediately set the context invalid so it can no longer be entered. The
                     * cancel executor closes the context on a parallel thread and closes the
                     * context properly. If necessary the cancel instrumentation needs to be
                     * restored after the context was successfully cancelled so we need a thread
                     * that waits for the cancel to be complete.
                     */
                    if (invalidated) {
                        limits.notifyEvent(c);
                        cancelResult = (FutureTask<?>) EngineLimits.getCancelExecutor().submit(new Runnable() {
                            public void run() {
                                if (!c.closed) {
                                    c.close(c.creatorApi, true);
                                }
                            }
                        });
                    }
                }
            }
        }
    }

    /**
     * Resource limit related data for each engine. Lazily constructed.
     */
    static final class EngineLimits {

        private static volatile ScheduledThreadPoolExecutor limitExecutor;
        private static volatile ThreadPoolExecutor cancelExecutor;

        private static final Predicate<Source> NO_PREDICATE = new Predicate<Source>() {
            public boolean test(Source t) {
                return true;
            }
        };

        final PolyglotEngineImpl engine;
        @CompilationFinal boolean timeLimitEnabled;
        @CompilationFinal long statementLimit = -1;
        @CompilationFinal Assumption sameStatementLimit;
        @CompilationFinal Predicate<Source> statementLimitSourcePredicate;
        EventBinding<?> statementLimitBinding;

        EngineLimits(PolyglotEngineImpl engine) {
            this.engine = engine;
        }

        void validate(PolyglotLimits limits) {
            Predicate<Source> newPredicate = limits != null ? limits.statementLimitSourcePredicate : null;
            if (newPredicate == null) {
                newPredicate = NO_PREDICATE;
            }
            if (this.statementLimitSourcePredicate != null && newPredicate != statementLimitSourcePredicate) {
                throw PolyglotEngineException.illegalArgument("Using multiple source predicates per engine is not supported. " +
                                "The same statement limit source predicate must be used for all polyglot contexts that are assigned to the same engine. " +
                                "Resolve this by using the same predicate instance when constructing the limits object with ResourceLimits.Builder.statementLimit(long, Predicate).");
            }

            if (limits != null && limits.timeLimit != null) {
                long time = -1;
                RuntimeException cause = null;
                if (!TruffleOptions.AOT) {
                    // SVM support GR-10551
                    try {
                        ManagementFactory.getThreadMXBean().setThreadCpuTimeEnabled(true);
                        time = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
                    } catch (UnsupportedOperationException e) {
                        // fallthrough not supported
                        cause = e;
                    }
                }
                if (time == -1) {
                    throw PolyglotEngineException.unsupported("ThreadMXBean.getCurrentThreadCpuTime() is not supported or enabled by the host VM but required for time limits.", cause);
                }
            }
        }

        void initialize(PolyglotLimits limits, PolyglotContextImpl context) {
            assert Thread.holdsLock(engine);
            Predicate<Source> newPredicate = limits.statementLimitSourcePredicate;
            if (newPredicate == null) {
                newPredicate = NO_PREDICATE;
            }
            if (this.statementLimitSourcePredicate == null) {
                this.statementLimitSourcePredicate = newPredicate;
            }
            // ensured by validate
            assert this.statementLimitSourcePredicate == newPredicate;

            if (limits.statementLimit != 0) {
                Assumption sameLimit = this.sameStatementLimit;
                if (sameLimit != null && sameLimit.isValid() && limits.statementLimit != statementLimit) {
                    sameLimit.invalidate();
                } else if (sameLimit == null) {
                    this.sameStatementLimit = Truffle.getRuntime().createAssumption("Same statement limit.");
                    this.statementLimit = limits.statementLimit;
                }

                if (statementLimitBinding == null) {
                    Instrumenter instrumenter = (Instrumenter) EngineAccessor.INSTRUMENT.getEngineInstrumenter(engine.instrumentationHandler);
                    SourceSectionFilter.Builder filter = SourceSectionFilter.newBuilder().tagIs(StatementTag.class);
                    if (statementLimitSourcePredicate != null) {
                        filter.sourceIs(new SourcePredicate() {
                            @Override
                            public boolean test(com.oracle.truffle.api.source.Source s) {
                                try {
                                    return statementLimitSourcePredicate.test(engine.getImpl().getPolyglotSource(s));
                                } catch (Throwable e) {
                                    throw PolyglotImpl.hostToGuestException(context, e);
                                }
                            }
                        });
                    }
                    statementLimitBinding = instrumenter.attachExecutionEventFactory(filter.build(), new ExecutionEventNodeFactory() {
                        public ExecutionEventNode create(EventContext eventContext) {
                            return new StatementIncrementNode(eventContext, EngineLimits.this);
                        }
                    });
                }
            }
            if (limits.timeLimit != null) {
                engine.noThreadTimingNeeded.invalidate();
                engine.noPriorityChangeNeeded.invalidate();
                long timeLimitMillis = limits.timeLimit.toMillis();
                assert timeLimitMillis > 0; // needs to verified before
                TimeLimitChecker task = new TimeLimitChecker(context, this);
                long accuracy = Math.max(10, limits.timeAccuracy.toMillis());
                getLimitTimer().scheduleAtFixedRate(task, accuracy, accuracy, TimeUnit.MILLISECONDS);
            }

            reset(context);
        }

        long getStatementLimit() {
            return statementLimit;
        }

        RuntimeException notifyEvent(PolyglotContextImpl context) {
            PolyglotLimits limits = context.config.limits;
            if (limits == null) {
                return null;
            }
            Consumer<ResourceLimitEvent> onEvent = limits.onEvent;
            if (onEvent == null) {
                return null;
            }
            try {
                onEvent.accept(engine.getImpl().getAPIAccess().newResourceLimitsEvent(context.creatorApi));
            } catch (Throwable t) {
                return PolyglotImpl.hostToGuestException(context, t);
            }
            return null;
        }

        static ExecutorService getCancelExecutor() {
            ThreadPoolExecutor executor = cancelExecutor;
            if (executor == null) {
                synchronized (EngineLimits.class) {
                    executor = cancelExecutor;
                    if (executor == null) {
                        cancelExecutor = executor = (ThreadPoolExecutor) Executors.newCachedThreadPool(
                                        new HighPriorityThreadFactory("Polyglot Cancel Thread"));
                        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
                    }
                }
            }
            return executor;
        }

        static ScheduledExecutorService getLimitTimer() {
            ScheduledThreadPoolExecutor executor = limitExecutor;
            if (executor == null) {
                synchronized (EngineLimits.class) {
                    executor = limitExecutor;
                    if (executor == null) {
                        executor = new ScheduledThreadPoolExecutor(0, new HighPriorityThreadFactory("Polyglot Limit Timer"));
                        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
                        limitExecutor = executor;
                    }
                }
            }
            return executor;
        }

        static final class HighPriorityThreadFactory implements ThreadFactory {
            private final AtomicLong threadCounter = new AtomicLong();
            private final String baseName;

            HighPriorityThreadFactory(String baseName) {
                this.baseName = baseName;
            }

            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(baseName + "-" + threadCounter.incrementAndGet());
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            }

        }
    }
}
