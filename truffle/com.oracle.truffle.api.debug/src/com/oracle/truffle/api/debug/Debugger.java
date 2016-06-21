/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Represents debugging related state of a {@link PolyglotEngine}.
 * <p>
 * Access to the (singleton) instance in an engine, once enabled, is available via:
 * <ul>
 * <li>{@link Debugger#find(PolyglotEngine)}</li>
 * <li>{@link SuspendedEvent#getDebugger()} and</li>
 * <li>{@link ExecutionEvent#getDebugger()} events.</li>
 * </ul>
 *
 * @since 0.9
 */
public final class Debugger {

    /**
     * @since 0.9
     * @deprecated use class literal {@link StatementTag} instead for tagging
     */
    @Deprecated public static final String HALT_TAG = "debug-HALT";

    /**
     * @since 0.9
     * @deprecated use class literal {@link CallTag} instead for tagging
     */
    @Deprecated public static final String CALL_TAG = "debug-CALL";

    private static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");
    private static final String TRACE_PREFIX = "Debug";
    private static final PrintStream OUT = System.out;

    private static final SourceSectionFilter CALL_FILTER = SourceSectionFilter.newBuilder().tagIs(CallTag.class).build();
    private static final SourceSectionFilter HALT_FILTER = SourceSectionFilter.newBuilder().tagIs(StatementTag.class).build();
    private static final Assumption NO_DEBUGGER = Truffle.getRuntime().createAssumption("No debugger assumption");

    private static boolean matchesHaltFilter(EventContext eventContext) {
        return AccessorDebug.nodesAccess().isTaggedWith(eventContext.getInstrumentedNode(), StatementTag.class);
    }

    private static final Set<Debugger> EXISTING_DEBUGGERS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Debugger, Boolean>()));

    /** Counter for externally requested step actions. */
    private static int nextActionID = 0;

    /**
     * Describes where an execution is halted relative to the instrumented node.
     */
    enum HaltPosition {
        BEFORE,
        AFTER;
    }

    /**
     * Finds debugger associated with given engine. There is at most one debugger associated with
     * any {@link PolyglotEngine}. One can access it by calling this static method. Once the
     * debugger is initialized, events like {@link SuspendedEvent} or {@link ExecutionEvent} are
     * delivered to
     * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#onEvent(com.oracle.truffle.api.vm.EventConsumer)
     * registered event handlers} whenever an important event (related to debugging) occurs in the
     * engine.
     *
     *
     * @param engine the engine to find debugger for
     * @return an instance of associated debugger, never <code>null</code>
     * @since 0.9
     */
    public static Debugger find(PolyglotEngine engine) {
        return find(engine, true);
    }

    private static final DebuggerInstrument.Factory FACTORY = new DebuggerInstrument.Factory() {
        @Override
        public Debugger create(PolyglotEngine engine, Instrumenter instrumenter) {
            Debugger newDebugger = new Debugger(engine, instrumenter);
            AccessorDebug.engineAccess().registerDebugger(engine, newDebugger);
            return newDebugger;
        }
    };

    static Debugger find(PolyglotEngine engine, boolean create) {
        PolyglotEngine.Instrument instrument = engine.getInstruments().get(DebuggerInstrument.ID);
        if (instrument == null) {
            throw new IllegalStateException();
        }
        if (create) {
            instrument.setEnabled(true);
        }
        final DebuggerInstrument debugInstrument = instrument.lookup(DebuggerInstrument.class);
        if (debugInstrument == null) {
            return null;
        }
        return debugInstrument.getDebugger(engine, create ? FACTORY : null);
    }

    private final PolyglotEngine engine;
    private final Instrumenter instrumenter;
    private final BreakpointFactory breakpoints;

    private Source lastSource;

    Debugger(PolyglotEngine engine, Instrumenter instrumenter) {
        this.engine = engine;
        this.instrumenter = instrumenter;
        this.breakpoints = new BreakpointFactory(instrumenter, breakpointCallback, warningLog);
        NO_DEBUGGER.invalidate();
        EXISTING_DEBUGGERS.add(this);
    }

    interface BreakpointCallback {

        /**
         * Passes control to the debugger with execution suspended.
         */
        void haltedAt(EventContext eventContext, MaterializedFrame mFrame, Breakpoint breakpoint);
    }

    interface WarningLog {

        /**
         * Logs a warning that is kept until the start of the next execution.
         */
        void addWarning(String warning);
    }

    private final BreakpointCallback breakpointCallback = new BreakpointCallback() {

        @TruffleBoundary
        public void haltedAt(EventContext eventContext, MaterializedFrame mFrame, Breakpoint breakpoint) {
            if (currentDebugContext == null) {
                final SourceSection sourceSection = eventContext.getInstrumentedNode().getSourceSection();
                assert sourceSection != null;
                currentDebugContext = new DebugExecutionContext(sourceSection.getSource(), null, 0);
            }
            final StepStrategy strategy = currentDebugContext.strategy;
            /*
             * Check to see if this breakpoint is at a location where the current stepping strategy
             * underway, if any, would halt. If so, then avoid the double-halt by ignoring the
             * breakpoint now and letting execution resume. The expectation in this situation is
             * that the current stepping strategy will halt during the same execution event
             * notification, at which time clients will be notified of the halt.
             *
             * Note that the breakpoint's hit count is decremented before this notification, so that
             * it counts as a hit whether or not it is ignored because of a double-halt.
             *
             * IMPORTANT: this implementation relies on the guarantee made by
             * ExecutionEventListener.onEnter() about the order of notification. In particular, it
             * is assumed here that any breakpoint halt at a particular code location will always
             * take place before a halt at the same location caused by a stepping strategy.
             */
            if (strategy != null && strategy.wouldHaltAt(eventContext)) {
                currentDebugContext.trace("REDUNDANT HALT, breakpoint@" + breakpoint.getLocationDescription());
            } else {
                currentDebugContext.halt(eventContext, mFrame, HaltPosition.BEFORE, breakpoint);
            }
        }
    };

    private WarningLog warningLog = new WarningLog() {

        public void addWarning(String warning) {
            assert currentDebugContext != null;
            currentDebugContext.logWarning(warning);
        }
    };

    /**
     * Head of the stack of executions.
     */
    private DebugExecutionContext currentDebugContext;

    /**
     * Sets a breakpoint to halt at a source line.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param lineLocation where to set the breakpoint (source, line number)
     * @param oneShot breakpoint disposes itself after fist hit, if {@code true}
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint can not be set.
     * @since 0.9
     */
    @TruffleBoundary
    public Breakpoint setLineBreakpoint(int ignoreCount, LineLocation lineLocation, boolean oneShot) throws IOException {
        return breakpoints.create(ignoreCount, lineLocation, oneShot);
    }

    /**
     * Sets a breakpoint to halt at a source line.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param sourceUri URI of the source to set the breakpoint into
     * @param line line number of the breakpoint
     * @param oneShot breakpoint disposes itself after fist hit, if {@code true}
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint can not be set.
     * @since 0.14
     */
    @TruffleBoundary
    public Breakpoint setLineBreakpoint(int ignoreCount, URI sourceUri, int line, boolean oneShot) throws IOException {
        return breakpoints.create(ignoreCount, sourceUri, line, -1, oneShot);
    }

    /**
     * Sets a breakpoint to halt at any node holding a specified <em>tag</em>.
     * <p>
     * If a breakpoint <em>condition</em> is applied to the breakpoint, then the condition will be
     * assumed to be in the same language as the code location where attached.
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param tag
     * @param oneShot if {@code true} breakpoint removes it self after a hit
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint already set
     * @since 0.9
     */
    @SuppressWarnings({"static-method", "deprecation"})
    @Deprecated
    @TruffleBoundary
    public Breakpoint setTagBreakpoint(int ignoreCount, com.oracle.truffle.api.instrument.SyntaxTag tag, boolean oneShot) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets all existing breakpoints, whatever their status, in natural sorted order. Modification
     * save.
     *
     * @since 0.9
     */
    @TruffleBoundary
    public Collection<Breakpoint> getBreakpoints() {
        return breakpoints.getAll();
    }

    /**
     * Request a pause. As soon as the execution arrives at a node holding a debugger tag,
     * {@link SuspendedEvent} is emitted.
     * <p>
     * This method can be called in any thread. When called from the {@link SuspendedEvent} callback
     * thread, execution is paused on a nearest next node holding a debugger tag.
     *
     * @return <code>true</code> when pause was requested on the current execution,
     *         <code>false</code> when there is no running execution to pause.
     * @since 0.14
     */
    public boolean pause() {
        DebugExecutionContext dc = currentDebugContext;
        if (dc != null) {
            dc.doPause();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Prepare to <em>Continue</em> when guest language program execution resumes. In this mode:
     * <ul>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node to which an enabled breakpoint is attached,
     * <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     */
    @TruffleBoundary
    void prepareContinue(int depth) {
        currentDebugContext.setAction(depth, new Continue());
    }

    /**
     * Prepare to <em>StepInto</em> when guest language program execution resumes. In this mode:
     * <ul>
     * <li>User breakpoints are disabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node holding {@link #HALT_TAG}, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * <li>StepInto mode persists only through one resumption (i.e. {@code stepIntoCount} steps),
     * and reverts by default to Continue mode.</li>
     * </ul>
     *
     * @param stepCount the number of times to perform StepInto before halting
     * @throws IllegalArgumentException if the specified number is {@code <= 0}
     */
    @TruffleBoundary
    void prepareStepInto(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException();
        }
        currentDebugContext.setAction(new StepInto(stepCount));
    }

    /**
     * Prepare to <em>StepOut</em> when guest language program execution resumes. In this mode:
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at the nearest enclosing call site on the stack, <strong>or</strong>
     * </li>
     * <li>execution completes.</li>
     * </ol>
     * <li>StepOut mode persists only through one resumption, and reverts by default to Continue
     * mode.</li>
     * </ul>
     */
    @TruffleBoundary
    void prepareStepOut() {
        currentDebugContext.setAction(new StepOut());
    }

    /**
     * Prepare to <em>StepOver</em> when guest language program execution resumes. In this mode:
     * <ul>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node holding {@link #HALT_TAG} when not nested in one or more
     * function/method calls, <strong>or:</strong></li>
     * <li>execution arrives at a node to which a breakpoint is attached and when nested in one or
     * more function/method calls, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * <li>StepOver mode persists only through one resumption (i.e. {@code stepOverCount} steps),
     * and reverts by default to Continue mode.</li>
     * </ul>
     *
     * @param stepCount the number of times to perform StepInto before halting
     * @throws IllegalArgumentException if the specified number is {@code <= 0}
     */
    @TruffleBoundary
    void prepareStepOver(int stepCount) {
        if (stepCount <= 0) {
            throw new IllegalArgumentException();
        }
        currentDebugContext.setAction(new StepOver(stepCount));
    }

    Instrumenter getInstrumenter() {
        return instrumenter;
    }

    /**
     * Implementation of a strategy for a debugger <em>action</em> that allows execution to continue
     * until it reaches another location e.g "step in" vs. "step over". Instances are numbered and
     * usable exactly once.
     */
    private abstract class StepStrategy {

        private final String name;
        private final int actionID;
        private DebugExecutionContext debugContext;
        private boolean disposed;

        protected StepStrategy() {
            this.name = getClass().getSimpleName();
            this.actionID = nextActionID++;
        }

        /**
         * Reconfigure the debugger so that when execution continues the program will halt at the
         * location specified by this strategy.
         */
        final void enable(DebugExecutionContext c, int stackDepth) {
            if (disposed) {
                throw new IllegalStateException("Debugger strategies are single-use");
            }
            this.debugContext = c;
            setStrategy(stackDepth);
        }

        /**
         * Return the debugger to the default navigation strategy.
         */
        final void disable() {
            unsetStrategy();
            disposed = true;
        }

        abstract boolean wouldHaltAt(EventContext eventContext);

        @TruffleBoundary
        protected final void halt(EventContext eventContext, MaterializedFrame mFrame, HaltPosition haltPosition) {
            debugContext.halt(eventContext, mFrame, haltPosition, this);
        }

        @TruffleBoundary
        protected final void replaceStrategy(StepStrategy newStrategy) {
            debugContext.setAction(newStrategy);
        }

        @TruffleBoundary
        protected final void traceAction(String action, int startStackDepth, int unfinishedStepCount) {
            if (TRACE) {
                debugContext.trace("%s (%s) stack=%d,%d unfinished=%d", action, description(), startStackDepth, computeStackDepth(), unfinishedStepCount);
            }
        }

        /**
         * Reconfigures debugger so that this strategy will be in effect when execution continues.
         */
        protected abstract void setStrategy(int stackDepth);

        /**
         * Restores debugger to default configuration.
         */
        protected abstract void unsetStrategy();

        String description() {
            return name + "<" + actionID + ">";
        }
    }

    /**
     * Strategy: the null stepping strategy.
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a node with attached user breakpoint, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     */
    private final class Continue extends StepStrategy {

        @Override
        protected void setStrategy(int stackDepth) {
        }

        @Override
        protected void unsetStrategy() {
        }

        @Override
        boolean wouldHaltAt(EventContext eventContext) {
            return false;
        }
    }

    /**
     * Strategy: per-{@link #HALT_TAG} stepping.
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution <em>arrives</em> at a {@link #HALT_TAG} node, <strong>or:</strong></li>
     * <li>execution <em>returns</em> to a {@link #CALL_TAG} node and the call stack is smaller then
     * when execution started, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     *
     * @see Debugger#prepareStepInto(int)
     */
    private final class StepInto extends StepStrategy {
        private int startStackDepth;
        private int unfinishedStepCount;
        private EventBinding<?> beforeHaltBinding;
        private EventBinding<?> afterCallBinding;

        StepInto(int stepCount) {
            super();
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int startStackDepth) {
            this.startStackDepth = startStackDepth;
            traceAction("SET ACTION", startStackDepth, unfinishedStepCount);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                    // Normal step, "before" halt location
                    traceAction("BEGIN onEnter()", startStackDepth, unfinishedStepCount);
                    if (--unfinishedStepCount <= 0) {
                        halt(eventContext, frame.materialize(), HaltPosition.BEFORE);
                    }
                    traceAction("END onEnter()", startStackDepth, unfinishedStepCount);
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                }
            });
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                    // Stepped out, "after" call location
                    traceAction("BEGIN onReturnValue()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() < startStackDepth) {
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.AFTER);
                        }
                    }
                    traceAction("END onReturnValue()", startStackDepth, unfinishedStepCount);
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                    // Program exception, "after" call location
                    traceAction("BEGIN onReturnExceptional()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() < startStackDepth) {
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.AFTER);
                        }
                    }
                    traceAction("END onReturnExceptional()", startStackDepth, unfinishedStepCount);
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            if (beforeHaltBinding == null || afterCallBinding == null) {
                // Instrumentation/language failure
                return;
            }
            traceAction("CLEAR ACTION", startStackDepth, unfinishedStepCount);
            beforeHaltBinding.dispose();
            afterCallBinding.dispose();
        }

        @Override
        boolean wouldHaltAt(EventContext eventContext) {
            return matchesHaltFilter(eventContext) && unfinishedStepCount <= 1;
        }
    }

    /**
     * Strategy: execution to nearest enclosing call site.
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a node with attached user breakpoint, <strong>or:</strong></li>
     * <li>execution <em>returns</em> to a CALL node and the call stack is smaller than when
     * execution started, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     *
     * @see Debugger#prepareStepOut()
     */
    private final class StepOut extends StepStrategy {

        private int unfinishedStepCount;
        private EventBinding<?> afterCallBinding;

        StepOut() {
            super();
            this.unfinishedStepCount = 1;
        }

        // TODO (mlvdv) not yet fully supported
        @SuppressWarnings("unused")
        StepOut(int stepCount) {
            super();
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int startStackDepth) {
            traceAction("SET STRATEGY", startStackDepth, unfinishedStepCount);
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                    // Stepped out, "after" call location
                    traceAction("BEGIN onReturnValue()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() < startStackDepth) {
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.AFTER);
                        }
                    }
                    traceAction("END onReturnValue()", startStackDepth, unfinishedStepCount);
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                    // Program exception, "after" call location
                    traceAction("BEGIN onReturnExceptional()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() < startStackDepth) {
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.AFTER);
                        }
                    }
                    traceAction("END onReturnExceptional()", startStackDepth, unfinishedStepCount);
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            if (afterCallBinding == null) {
                // Instrumentation/language failure
                return;
            }
            afterCallBinding.dispose();
        }

        @Override
        boolean wouldHaltAt(EventContext eventContext) {
            return AccessorDebug.nodesAccess().isTaggedWith(eventContext.getInstrumentedNode(), StatementTag.class);
        }
    }

    /**
     * Strategy: per-{@link #HALT_TAG} stepping, so long as not nested in method calls (i.e. at
     * original stack depth).
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a node holding {@link #HALT_TAG}, with stack depth no more than when
     * started <strong>or:</strong></li>
     * <li>the program completes.</li>
     * </ol>
     * </ul>
     */
    private final class StepOver extends StepStrategy {
        private int unfinishedStepCount;
        private EventBinding<?> beforeHaltBinding;
        private EventBinding<?> afterCallBinding;

        StepOver(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int startStackDepth) {
            traceAction("SET STRATEGY", startStackDepth, unfinishedStepCount);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                    // "before" halt location
                    if (computeStackDepth() <= startStackDepth) {
                        traceAction("BEGIN onEnter()", startStackDepth, unfinishedStepCount);
                        // stack depth unchanged or smaller; treat like StepInto
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.BEFORE);
                        }
                        traceAction("END onEnter()", startStackDepth, unfinishedStepCount);
                    } else {
                        // Stack depth increased; don't count as a step
                        traceAction("BEGIN onEnter() STEPPPED INTO CALL", startStackDepth, unfinishedStepCount);
                        // Stop treating like StepInto, start treating like StepOut
                        replaceStrategy(new StepOverNested(unfinishedStepCount, startStackDepth));
                        traceAction("END onEnter() STEPPPED INTO CALL", startStackDepth, unfinishedStepCount);
                    }
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                }

            });
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                    // Stepped out, "after" call location
                    traceAction("BEGIN onReturnValue()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() < startStackDepth) {
                        --unfinishedStepCount;
                    }
                    if (unfinishedStepCount <= 0) {
                        halt(eventContext, frame.materialize(), HaltPosition.AFTER);
                    }
                    traceAction("END onReturnValue()", startStackDepth, unfinishedStepCount);
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                }
            });

        }

        @Override
        protected void unsetStrategy() {
            if (beforeHaltBinding == null || afterCallBinding == null) {
                // Instrumentation/language failure
                return;
            }
            beforeHaltBinding.dispose();
            afterCallBinding.dispose();
        }

        @Override
        boolean wouldHaltAt(EventContext eventContext) {
            return matchesHaltFilter(eventContext) && unfinishedStepCount <= 1;
        }
    }

    /**
     * Strategy: per-{@link #HALT_TAG} stepping, not into method calls, in effect while at increased
     * stack depth
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a node holding {@link #HALT_TAG}, with stack depth no more than when
     * started <strong>or:</strong></li>
     * <li>the program completes <strong>or:</strong></li>
     * </ol>
     * </ul>
     */
    private final class StepOverNested extends StepStrategy {
        private final int startStackDepth;
        private int unfinishedStepCount;
        private EventBinding<?> beforeHaltBinding;

        StepOverNested(int stepCount, int startStackDepth) {
            this.startStackDepth = startStackDepth;
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int stackDepth) {
            traceAction("SET STRATEGY", startStackDepth, unfinishedStepCount);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext eventContext, VirtualFrame frame) {
                    // Normal step, "before" halt location
                    traceAction("BEGIN onEnter()", startStackDepth, unfinishedStepCount);
                    if (computeStackDepth() <= startStackDepth) {
                        // At original step depth (or smaller) after being nested
                        if (--unfinishedStepCount <= 0) {
                            halt(eventContext, frame.materialize(), HaltPosition.BEFORE);
                        }
                        // TODO (mlvdv) fixme for multiple steps
                    }
                    traceAction("END onEnter()", startStackDepth, unfinishedStepCount);
                }

                public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                }

            });
        }

        @Override
        protected void unsetStrategy() {
            if (beforeHaltBinding == null) {
                // Instrumentation/language failure
                return;
            }
            beforeHaltBinding.dispose();
        }

        @Override
        boolean wouldHaltAt(EventContext eventContext) {
            return AccessorDebug.nodesAccess().isTaggedWith(eventContext.getInstrumentedNode(), StatementTag.class);
        }
    }

    private final class PauseHandler {

        private EventBinding<?>[] bindings;

        @TruffleBoundary
        PauseHandler(final DebugExecutionContext debugContext) {
            if (TRACE) {
                debugContext.trace("PAUSE requested.");
            }
            ExecutionEventListener execListener = new ExecutionEventListener() {
                @Override
                public void onEnter(EventContext context, VirtualFrame frame) {
                    debugContext.halt(context, frame.materialize(), HaltPosition.BEFORE, "Paused");
                }

                @Override
                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    debugContext.halt(context, frame.materialize(), HaltPosition.AFTER, "Paused");
                }

                @Override
                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    debugContext.halt(context, frame.materialize(), HaltPosition.AFTER, "Paused");
                }
            };
            bindings = new EventBinding<?>[]{
                            instrumenter.attachListener(HALT_FILTER, execListener),
                            instrumenter.attachListener(CALL_FILTER, execListener),
            };
        }

        private void disable() {
            for (EventBinding<?> eb : bindings) {
                eb.dispose();
            }
            bindings = null;
        }

    }

    /**
     * Information and debugging state for a single Truffle execution (which make take place over
     * one or more suspended executions). This holds interaction state, for example what is
     * executing (e.g. some {@link Source}), what the execution mode is ("stepping" or
     * "continuing"). When not running, this holds a cache of the Truffle stack for this particular
     * execution, effectively hiding the Truffle stack for any currently suspended executions (down
     * the stack).
     * <p>
     * Each instance is single-use.
     */
    private final class DebugExecutionContext {

        // Previous halted context in stack
        private final DebugExecutionContext predecessor;

        // The current execution level; first is 0.
        private final int level;  // Number of contexts suspended below
        private final Source source;
        private final int contextStackBase;  // Where the stack for this execution starts
        private final List<String> warnings = new ArrayList<>();

        private boolean disposed;
        private boolean running;

        /** Currently configured strategy. */
        private StepStrategy strategy;

        /** Static context where halted; null if running. */
        private EventContext haltedEventContext;

        /** Frame where halted; null if running. */
        private MaterializedFrame haltedFrame;

        /** Where halted relative to the instrumented node. */
        private HaltPosition haltedPosition;

        private final Object pauseHandlerLock = new Object();
        /** Handler of pause requests. */
        private PauseHandler pauseHandler;

        /** Subset of the Truffle stack corresponding to the current execution. */
        private List<FrameInstance> contextStack;

        private DebugExecutionContext(Source executionSource, DebugExecutionContext previousContext) {
            this(executionSource, previousContext, -1);
        }

        private DebugExecutionContext(Source executionSource, DebugExecutionContext previousContext, int depth) {
            this.source = executionSource;
            this.predecessor = previousContext;
            this.level = previousContext == null ? 0 : previousContext.level + 1;

            // "Base" is the number of stack frames for all nested (halted) executions.
            this.contextStackBase = depth == -1 ? computeStackDepth() : depth;
            this.running = true;
            if (TRACE) {
                trace("NEW DEBUG CONTEXT level=" + level);
            }
        }

        /**
         * Sets up the action for the next resumption of execution.
         *
         * @param stepStrategy
         */
        private void setAction(StepStrategy stepStrategy) {
            setAction(computeStackDepth(), stepStrategy);
        }

        private void setAction(int depth, StepStrategy newStrategy) {
            if (disposed) {
                throw new IllegalStateException("DebugExecutionContexts are single-use.");
            }
            assert newStrategy != null;
            if (this.strategy == null) {
                strategy = newStrategy;
                strategy.enable(this, depth);

            } else {
                strategy.disable();
                strategy = newStrategy;
                strategy.enable(this, computeStackDepth());
            }
        }

        private void clearAction() {
            if (strategy != null) {
                strategy.disable();
                strategy = null;

            }
        }

        void doPause() {
            synchronized (pauseHandlerLock) {
                if (pauseHandler != null) {
                    // Pause was requested already
                    return;
                }
                pauseHandler = new PauseHandler(this);
            }
        }

        private void clearPause() {
            boolean cleared = false;
            synchronized (pauseHandlerLock) {
                if (pauseHandler != null) {
                    pauseHandler.disable();
                    pauseHandler = null;
                    cleared = true;
                }
            }
            if (TRACE && cleared) {
                trace("CLEAR PAUSE");
            }
        }

        /**
         * Handle a program halt, caused by a breakpoint, stepping action, or other cause.
         *
         * @param eventContext information about the guest language node at which execution is
         *            halted
         * @param mFrame the current execution frame where execution is halted
         * @param position execution position relative to the instrumented node where halted
         * @param haltReason what caused the halt
         */
        @TruffleBoundary
        private void halt(EventContext eventContext, MaterializedFrame mFrame, HaltPosition position, Object cause) {
            if (disposed) {
                throw new IllegalStateException("DebugExecutionContexts are single-use.");
            }
            assert running;
            assert haltedEventContext == null;
            assert haltedFrame == null;

            haltedEventContext = eventContext;
            haltedFrame = mFrame;
            haltedPosition = position;
            running = false;

            if (cause instanceof StepStrategy) {
                clearAction();
            }
            clearPause();

            // Clean up, just in cased the one-shot breakpoints got confused
            breakpoints.disposeOneShots();

            final List<String> recentWarnings = new ArrayList<>(warnings);
            warnings.clear();

            final int contextStackDepth = (computeStackDepth() - contextStackBase) + 1;
            final List<FrameInstance> frames = new ArrayList<>();
            // Map the Truffle stack for this execution, ignore nested executions
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                int stackIndex = 1;

                @Override
                public FrameInstance visitFrame(FrameInstance frameInstance) {
                    if (stackIndex < contextStackDepth) {
                        if (TRACE && frameInstance.getCallNode() == null) {
                            trace("frame %d null callNode: %s", stackIndex, frameInstance.getFrame(FrameAccess.READ_ONLY, true));
                        }
                        frames.add(frameInstance);
                        stackIndex++;
                        return null;
                    }
                    return frameInstance;
                }
            });
            contextStack = Collections.unmodifiableList(frames);
            String haltReason = null;

            if (TRACE) {
                if (cause instanceof StepStrategy) {
                    haltReason = ((StepStrategy) cause).description();
                } else if (cause instanceof Breakpoint) {
                    haltReason = "breakpoint@" + ((Breakpoint) cause).getLocationDescription();
                } else {
                    haltReason = cause.toString();
                }
                trace("HALT %s: (%s) stack base=%d", haltedPosition.toString(), haltReason, contextStackBase);
            }

            try {
                // Pass control to the debug client with current execution suspended
                SuspendedEvent event = new SuspendedEvent(Debugger.this, haltedEventContext.getInstrumentedNode(), haltedPosition, haltedFrame, contextStack, recentWarnings);
                AccessorDebug.engineAccess().dispatchEvent(engine, event, Accessor.EngineSupport.SUSPENDED_EVENT);
                if (event.isKillPrepared()) {
                    trace("KILL");
                    throw new KillException();
                }
                // Debug client finished normally, execution resumes
                // Presume that the client has set a new strategy (or default to Continue)
                running = true;
                if (TRACE) {
                    trace("RESUME %s : (%s) stack base=%d", haltedPosition.toString(), haltReason, contextStackBase);
                }
            } finally {
                haltedEventContext = null;
                haltedFrame = null;
                haltedPosition = null;
            }
        }

        private void dispose() {
            breakpoints.disposeOneShots();
            clearAction();
            disposed = true;
            if (TRACE) {
                trace("DISPOSE DEBUG CONTEXT level=" + level);
            }
        }

        private void logWarning(String warning) {
            warnings.add(warning);
        }

        private void trace(String format, Object... args) {
            if (TRACE) {
                String location = "";
                if (haltedEventContext != null && haltedEventContext.getInstrumentedNode().getSourceSection() != null) {
                    location = haltedEventContext.getInstrumentedNode().getSourceSection().getShortDescription();
                } else if (source != null) {
                    location = source.getName();
                } else {
                    location = "no source";
                }
                final String message = String.format(format, args);
                Debugger.OUT.println(String.format("%s<%d>: %s [%s]", Debugger.TRACE_PREFIX, level, message, location));
            }
        }
    }

    // TODO (mlvdv) wish there were fast-path access to stack depth
    /**
     * Depth of current Truffle stack, including nested executions. Includes the top/current frame,
     * which the standard iterator does not count: {@code 0} if no executions.
     */
    @TruffleBoundary
    private static int computeStackDepth() {
        final int[] count = {0};
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Void>() {
            @Override
            public Void visitFrame(FrameInstance frameInstance) {
                count[0] = count[0] + 1;
                return null;
            }
        });
        return count[0] == 0 ? 0 : count[0] + 1;
    }

    void executionStarted(int depth, Source source) {
        Source execSource = source;
        if (execSource == null) {
            execSource = lastSource;
        } else {
            lastSource = execSource;
        }
        // Push a new execution context onto stack
        currentDebugContext = new DebugExecutionContext(execSource, currentDebugContext, depth);
        breakpoints.notifySourceLoaded(source);
        prepareContinue(depth);
        currentDebugContext.trace("BEGIN EXECUTION");
    }

    private void executionEnded() {
        currentDebugContext.trace("END EXECUTION");
        currentDebugContext.dispose();
        // Pop the stack of execution contexts.
        currentDebugContext = currentDebugContext.predecessor;
    }

    /**
     * Evaluates a snippet of code in a halted execution context. Assumes frame is part of the
     * current execution stack, behavior is undefined if not.
     *
     * @param ev event notification where execution is halted
     * @param code text of the code to be executed
     * @param frameInstance frame where execution is halted
     * @return
     * @throws IOException
     */
    Object evalInContext(SuspendedEvent ev, String code, FrameInstance frameInstance) throws IOException {
        try {
            if (frameInstance == null) {
                return AccessorDebug.langs().evalInContext(engine, ev, code, currentDebugContext.haltedEventContext.getInstrumentedNode(), currentDebugContext.haltedFrame);
            } else {
                return AccessorDebug.langs().evalInContext(engine, ev, code, frameInstance.getCallNode(), frameInstance.getFrame(FrameAccess.MATERIALIZE, true).materialize());
            }
        } catch (KillException kex) {
            throw new IOException("Evaluation was killed.", kex);
        }
    }

    static final class AccessorDebug extends Accessor {
        static Accessor.Nodes nodesAccess() {
            return ACCESSOR.nodes();
        }

        static Accessor.LanguageSupport langs() {
            return ACCESSOR.languageSupport();
        }

        static Accessor.EngineSupport engineAccess() {
            return ACCESSOR.engineSupport();
        }

        @Override
        protected DebugSupport debugSupport() {
            return new DebugImpl();
        }

        private static final class DebugImpl extends DebugSupport {
            @Override
            public void executionStarted(Object vm, final int currentDepth, final Object[] debugger, final Source s) {
                final PolyglotEngine engine = (PolyglotEngine) vm;
                if (debugger[0] != null) {
                    final Debugger dbg = (Debugger) debugger[0];
                    dbg.executionStarted(currentDepth, s);
                }
                ExecutionEvent event = new ExecutionEvent(engine, currentDepth, debugger, s);
                engineAccess().dispatchEvent(engine, event, EngineSupport.EXECUTION_EVENT);
                event.dispose();
            }

            @Override
            public void executionEnded(Object vm, Object[] debugger) {
                if (debugger[0] != null) {
                    ((Debugger) debugger[0]).executionEnded();
                }
            }

            @Override
            public void executionSourceSection(SourceSection ss) {
                Source source = ss.getSource();
                for (Debugger debugger : EXISTING_DEBUGGERS) {
                    debugger.breakpoints.notifySourceLoaded(source);
                }
            }

            @Override
            public Assumption assumeNoDebugger() {
                return NO_DEBUGGER;
            }
        }

        @SuppressWarnings("rawtypes")
        protected CallTarget parse(Class<? extends TruffleLanguage> languageClass, Source code, Node context, String... argumentNames) throws IOException {
            final TruffleLanguage<?> truffleLanguage = engineSupport().findLanguageImpl(null, languageClass, code.getMimeType());
            return languageSupport().parse(truffleLanguage, code, context, argumentNames);
        }

        @SuppressWarnings({"rawtypes", "static-method"})
        String toStringInContext(RootNode rootNode, Object value) {
            final Class<? extends TruffleLanguage> languageClass = nodesAccess().findLanguage(rootNode);
            final TruffleLanguage.Env env = engineAccess().findEnv(languageClass);
            final TruffleLanguage<?> language = langs().findLanguage(env);
            return AccessorDebug.langs().toString(language, env, value);
        }
    }

    // registers into Accessor.DEBUG
    static final AccessorDebug ACCESSOR = new AccessorDebug();
}
