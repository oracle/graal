/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;

/**
 * Limits objects that backs the {@link ResourceLimits} API object.
 */
final class PolyglotLimits {

    final long statementLimit;
    final Predicate<Object> statementLimitSourcePredicate;
    final Consumer<Object> onEvent;

    PolyglotLimits(long statementLimit, Predicate<Object> statementLimitSourcePredicate, Consumer<Object> onEvent) {
        this.statementLimit = statementLimit;
        this.statementLimitSourcePredicate = statementLimitSourcePredicate;
        this.onEvent = onEvent;
    }

    static void reset(PolyglotContextImpl context) {
        synchronized (context) {
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
        @CompilationFinal private boolean seenInnerContext;

        StatementIncrementNode(EventContext context, EngineLimits limits) {
            this.limits = limits;
            this.eventContext = context;
            this.engine = limits.engine;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            PolyglotContextImpl currentContext = getLimitContext();
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
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContextWithEngine(engine);
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
                context.cancel(true, String.format("Statement count limit of %s exceeded. Statements executed %s.", limit, actualCount));
                RuntimeException e = limits.notifyEvent(context);
                if (e != null) {
                    throw e;
                }
                TruffleSafepoint.pollHere(eventContext.getInstrumentedNode());
            }

        }

    }

    /**
     * Resource limit related data for each engine. Lazily constructed.
     */
    static final class EngineLimits {

        private static final Predicate<Object> NO_PREDICATE = new Predicate<>() {
            public boolean test(Object t) {
                return true;
            }
        };

        final PolyglotEngineImpl engine;
        @CompilationFinal long statementLimit = -1;
        @CompilationFinal Assumption sameStatementLimit;
        @CompilationFinal Predicate<Object> statementLimitSourcePredicate;
        EventBinding<?> statementLimitBinding;

        EngineLimits(PolyglotEngineImpl engine) {
            this.engine = engine;
        }

        void validate(PolyglotLimits limits) {
            if (limits != null && limits.statementLimit != 0) {
                Predicate<Object> newPredicate = limits.statementLimitSourcePredicate;
                if (newPredicate == null) {
                    newPredicate = NO_PREDICATE;
                }
                if (this.statementLimitSourcePredicate != null && newPredicate != statementLimitSourcePredicate) {
                    throw PolyglotEngineException.illegalArgument("Using multiple source predicates per engine is not supported. " +
                                    "The same statement limit source predicate must be used for all polyglot contexts that are assigned to the same engine. " +
                                    "Resolve this by using the same predicate instance when constructing the limits object with ResourceLimits.Builder.statementLimit(long, Predicate).");
                }
            }
        }

        void initialize(PolyglotLimits limits, PolyglotContextImpl context) {
            assert Thread.holdsLock(engine.lock);

            if (limits.statementLimit != 0) {
                Predicate<Object> newPredicate = limits.statementLimitSourcePredicate;
                if (newPredicate == null) {
                    newPredicate = NO_PREDICATE;
                }
                if (this.statementLimitSourcePredicate == null) {
                    this.statementLimitSourcePredicate = newPredicate;
                }
                // ensured by validate
                assert this.statementLimitSourcePredicate == newPredicate;

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
                                    return statementLimitSourcePredicate.test(PolyglotImpl.getOrCreatePolyglotSource(engine.getImpl(), s));
                                } catch (Throwable e) {
                                    throw context.engine.host.toHostException(context.getHostContextImpl(), e);
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
            Consumer<Object> onEvent = limits.onEvent;
            if (onEvent == null) {
                return null;
            }
            Object event = engine.getImpl().getAPIAccess().newResourceLimitsEvent(context.api);
            try {
                onEvent.accept(event);
            } catch (Throwable t) {
                throw context.engine.host.toHostException(context.getHostContextImpl(), t);
            }
            return null;
        }
    }
}
