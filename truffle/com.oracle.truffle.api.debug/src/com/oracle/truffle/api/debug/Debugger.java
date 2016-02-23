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

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.KillException;
import com.oracle.truffle.api.QuitException;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.impl.DebuggerInstrument;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Represents debugging related state of a {@link com.oracle.truffle.api.vm.PolyglotEngine}.
 * Instance of this class is delivered via {@link SuspendedEvent#getDebugger()} and
 * {@link ExecutionEvent#getDebugger()} events, once {@link com.oracle.truffle.api.debug debugging
 * is turned on}.
 * <p>
 * Debugger <em>stepping</em> behavior is configured by each {@link TruffleLanguage language
 * implementation} through application of {@linkplain SourceSection#withTags(java.lang.String...)
 * tags} at specific source language locations that have {@link SourceSection source information}
 * attached.
 * <ul>
 * <li>For most stepping situations, the debugger will halt just <em>before</em> a code location is
 * executed that is marked with {@link #HALT_TAG}.</li>
 * <li>When when stepping out of a call, the debugger will halt at the code location just executed
 * that has been marked with {@link #CALL_TAG}.</li>
 * </ul>
 */
public final class Debugger {

    /**
     * A {@link SourceSection#withTags(java.lang.String...) tag} used to mark program locations
     * where the debugger will halt during normal stepping.
     */
    public static final String HALT_TAG = "debug-HALT";

    /**
     * A {@link SourceSection#withTags(java.lang.String...) tag} used to mark program locations
     * where the debugger will halt when <em>stepping out</em> of a call.
     *
     * @see #HALT_TAG
     */
    public static final String CALL_TAG = "debug-CALL";

    private static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");
    private static final String TRACE_PREFIX = "Debug: ";
    private static final PrintStream OUT = System.out;

    private static final SourceSectionFilter CALL_FILTER = SourceSectionFilter.newBuilder().tagIs(CALL_TAG).build();
    private static final SourceSectionFilter HALT_FILTER = SourceSectionFilter.newBuilder().tagIs(HALT_TAG).build();

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
     */
    public static Debugger find(PolyglotEngine engine) {
        return find(engine, true);
    }

    private static final DebuggerInstrument.Factory FACTORY = new DebuggerInstrument.Factory() {
        @Override
        public Debugger create(PolyglotEngine engine, Instrumenter instrumenter) {
            return new Debugger(engine, instrumenter);
        }
    };

    private static Debugger find(PolyglotEngine engine, boolean create) {
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

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private final PolyglotEngine engine;
    private final Instrumenter instrumenter;
    private final BreakpointFactory breakpoints;

    private Source lastSource;

    Debugger(PolyglotEngine engine, Instrumenter instrumenter) {
        this.engine = engine;
        this.instrumenter = instrumenter;
        this.breakpoints = new BreakpointFactory(instrumenter, breakpointCallback, warningLog);
    }

    interface BreakpointCallback {

        /**
         * Passes control to the debugger with execution suspended.
         */
        void haltedAt(Node astNode, MaterializedFrame mFrame, String haltReason);
    }

    interface WarningLog {

        /**
         * Logs a warning that is kept until the start of the next execution.
         */
        void addWarning(String warning);
    }

    private final BreakpointCallback breakpointCallback = new BreakpointCallback() {

        @TruffleBoundary
        public void haltedAt(Node astNode, MaterializedFrame mFrame, String haltReason) {
            debugContext.halt(astNode, mFrame, true, haltReason);
        }
    };

    private WarningLog warningLog = new WarningLog() {

        public void addWarning(String warning) {
            assert debugContext != null;
            debugContext.logWarning(warning);
        }
    };

    /**
     * Head of the stack of executions.
     */
    private DebugExecutionContext debugContext;

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
     */
    @TruffleBoundary
    public Breakpoint setLineBreakpoint(int ignoreCount, LineLocation lineLocation, boolean oneShot) throws IOException {
        return breakpoints.create(ignoreCount, lineLocation, oneShot);
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
     */
    @SuppressWarnings("static-method")
    @Deprecated
    @TruffleBoundary
    public Breakpoint setTagBreakpoint(int ignoreCount, SyntaxTag tag, boolean oneShot) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets all existing breakpoints, whatever their status, in natural sorted order. Modification
     * save.
     */
    @TruffleBoundary
    public Collection<Breakpoint> getBreakpoints() {
        return breakpoints.getAll();
    }

    /**
     * Prepare to execute in Continue mode when guest language program execution resumes. In this
     * mode:
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
        debugContext.setStrategy(depth, new Continue());
    }

    /**
     * Prepare to execute in StepInto mode when guest language program execution resumes. In this
     * mode:
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
        debugContext.setStrategy(new StepInto(stepCount));
    }

    /**
     * Prepare to execute in StepOut mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at the nearest enclosing call site on the stack, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * <li>StepOut mode persists only through one resumption, and reverts by default to Continue
     * mode.</li>
     * </ul>
     */
    @TruffleBoundary
    void prepareStepOut() {
        debugContext.setStrategy(new StepOut());
    }

    /**
     * Prepare to execute in StepOver mode when guest language program execution resumes. In this
     * mode:
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
        debugContext.setStrategy(new StepOver(stepCount));
    }

    Instrumenter getInstrumenter() {
        return instrumenter;
    }

    /**
     * A mode of user navigation from a current code location to another, e.g "step in" vs.
     * "step over".
     */
    private abstract class StepStrategy {

        private DebugExecutionContext context;
        protected final String strategyName;

        protected StepStrategy() {
            this.strategyName = getClass().getSimpleName();
        }

        final String getName() {
            return strategyName;
        }

        /**
         * Reconfigure the debugger so that when execution continues the program will halt at the
         * location specified by this strategy.
         */
        final void enable(DebugExecutionContext c, int stackDepth) {
            this.context = c;
            setStrategy(stackDepth);
        }

        /**
         * Return the debugger to the default navigation mode.
         */
        final void disable() {
            unsetStrategy();
        }

        @TruffleBoundary
        final void halt(Node astNode, MaterializedFrame mFrame, boolean before) {
            context.halt(astNode, mFrame, before, this.getClass().getSimpleName());
        }

        @TruffleBoundary
        final void replaceStrategy(StepStrategy newStrategy) {
            context.setStrategy(newStrategy);
        }

        @TruffleBoundary
        protected final void strategyTrace(String action, String format, Object... args) {
            if (TRACE) {
                context.contextTrace("%s (%s) %s", action, strategyName, String.format(format, args));
            }
        }

        @TruffleBoundary
        protected final void suspendUserBreakpoints() {
            breakpoints.setActive(false);
        }

        @SuppressWarnings("unused")
        protected final void restoreUserBreakpoints() {
            breakpoints.setActive(true);
        }

        /**
         * Reconfigure the debugger so that when execution continues, it will do so using this mode
         * of navigation.
         */
        protected abstract void setStrategy(int stackDepth);

        /**
         * Return to the debugger to the default mode of navigation.
         */
        protected abstract void unsetStrategy();
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
        private int unfinishedStepCount;
        private int startStackDepth;
        private EventBinding<?> beforeHaltBinding;
        private EventBinding<?> afterCallBinding;

        StepInto(int stepCount) {
            super();
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int startStackDepth) {
            this.startStackDepth = startStackDepth;
            strategyTrace("STRATEGY", "repeat=%d stack=%d", unfinishedStepCount, startStackDepth);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                    // HALT: just before {@link #HALT_TAG}
                    --unfinishedStepCount;
                    if (TRACE) {
                        strategyTrace("HALT BEFORE", "stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth(), unfinishedStepCount);
                    }
                    // Should run in fast path
                    if (unfinishedStepCount <= 0) {
                        halt(context.getInstrumentedNode(), frame.materialize(), true);
                    }
                    if (TRACE) {
                        strategyTrace("RESUME BEFORE", "stack=%d,%d", startStackDepth, currentStackDepth());
                    }
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });
            // When stepping causes a return, expected behavior is to halt again at the call
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    haltAfter(context, frame.materialize());
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });
        }

        @TruffleBoundary
        private void haltAfter(EventContext context, MaterializedFrame frame) {
            --unfinishedStepCount;
            final int currentStackDepth = currentStackDepth();
            strategyTrace(null, "HALT AFTER stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth, unfinishedStepCount);
            if (currentStackDepth < startStackDepth) {
                // HALT: just "stepped out"
                if (unfinishedStepCount <= 0) {
                    halt(context.getInstrumentedNode(), frame, false);
                }
            }
            if (TRACE) {
                strategyTrace("RESUME AFTER", "stack=%d,%d", startStackDepth, currentStackDepth());
            }
        }

        @Override
        protected void unsetStrategy() {
            beforeHaltBinding.dispose();
            afterCallBinding.dispose();
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
        private int startStackDepth;
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
            this.startStackDepth = startStackDepth;
            strategyTrace("STRATEGY", "repeat=%d stack=%d", unfinishedStepCount, startStackDepth);
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    haltAfter(context, frame.materialize());
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    haltAfter(context, frame.materialize());
                }
            });
        }

        @TruffleBoundary
        private void haltAfter(EventContext context, MaterializedFrame frame) {
            --unfinishedStepCount;
            final int currentStackDepth = currentStackDepth();
            strategyTrace(null, "HALT AFTER stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth, unfinishedStepCount);
            if (currentStackDepth < startStackDepth) {
                // HALT: just "stepped out"
                if (unfinishedStepCount <= 0) {
                    halt(context.getInstrumentedNode(), frame, false);
                }
            }
            if (TRACE) {
                strategyTrace("RESUME AFTER", "stack=%d,%d", startStackDepth, currentStackDepth());
            }
        }

        @Override
        protected void unsetStrategy() {
            afterCallBinding.dispose();
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
        @SuppressWarnings("unused") private int startStackDepth;
        private EventBinding<?> beforeHaltBinding;
        private EventBinding<?> afterCallBinding;

        StepOver(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int startStackDepth) {
            this.startStackDepth = startStackDepth;
            strategyTrace("STRATEGY", "repeat=%d stack=%d", unfinishedStepCount, startStackDepth);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                    final int currentStackDepth = currentStackDepth();
                    if (currentStackDepth <= startStackDepth) {
                        // HALT: stack depth unchanged or smaller; treat like StepInto
                        --unfinishedStepCount;
                        if (TRACE) {
                            strategyTrace("HALT BEFORE", "stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth(), unfinishedStepCount);
                        }
                        // Test should run in fast path
                        if (unfinishedStepCount <= 0) {
                            halt(context.getInstrumentedNode(), frame.materialize(), true);
                        }
                    } else {
                        // CONTINUE: Stack depth increased; don't count as a step
                        if (TRACE) {
                            strategyTrace("STEP INTO CALL", "stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth(), unfinishedStepCount);
                        }
                        // Stop treating like StepInto, start treating like StepOut
                        replaceStrategy(new StepOverNested(unfinishedStepCount, startStackDepth));
                    }
                    strategyTrace("RESUME BEFORE", "");
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

            });
            afterCallBinding = instrumenter.attachListener(CALL_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    final int currentStackDepth = currentStackDepth();
                    strategyTrace(null, "HALT AFTER stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth, unfinishedStepCount);
                    if (currentStackDepth < startStackDepth) {
                        // HALT: just "stepped out"
                        --unfinishedStepCount;
                    }
                    // Test should run in fast path
                    if (unfinishedStepCount <= 0) {
                        halt(context.getInstrumentedNode(), frame.materialize(), false);
                    }
                    if (TRACE) {
                        strategyTrace("RESUME AFTER", "stack=%d,%d", startStackDepth, currentStackDepth());
                    }
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }
            });

        }

        @Override
        protected void unsetStrategy() {
            beforeHaltBinding.dispose();
            afterCallBinding.dispose();
        }
    }

    // beforeTagInstrument = instrumenter.attach(STEPPING_TAG, new
    // StandardBeforeInstrumentListener() {
    //
    // @TruffleBoundary
    // @Override
    // public void onEnter(Probe probe, Node node, VirtualFrame vFrame) {
    // final int currentStackDepth = currentStackDepth();
    // if (currentStackDepth <= stackDepth) {
    // // HALT: stack depth unchanged or smaller; treat like StepInto
    // --unfinishedStepCount;
    // if (TRACE) {
    // strategyTrace("HALT BEFORE", "unfinished steps=%d stackDepth start=%d current=%d",
    // unfinishedStepCount, stackDepth, currentStackDepth);
    // }
    // // Test should run in fast path
    // if (unfinishedStepCount <= 0) {
    // halt(node, vFrame.materialize(), true);
    // }
    // } else {
    // // CONTINUE: Stack depth increased; don't count as a step
    // strategyTrace("STEP INTO", "unfinished steps=%d stackDepth start=%d current=%d",
    // unfinishedStepCount, stackDepth, currentStackDepth);
    // // Stop treating like StepInto, start treating like StepOut
    // replaceStrategy(new StepOverNested(unfinishedStepCount, stackDepth));
    // }
    // strategyTrace("RESUME BEFORE", "");
    // }
    // }, "Debugger StepOver");

    // afterTagInstrument = instrumenter.attach(CALL_TAG, new StandardAfterInstrumentListener() {
    //
    // public void onReturnVoid(Probe probe, Node node, VirtualFrame vFrame) {
    // doHalt(node, vFrame.materialize());
    // }
    //
    // public void onReturnValue(Probe probe, Node node, VirtualFrame vFrame, Object result) {
    // doHalt(node, vFrame.materialize());
    // }
    //
    // public void onReturnExceptional(Probe probe, Node node, VirtualFrame vFrame, Throwable
    // exception)
    // {
    // doHalt(node, vFrame.materialize());
    // }
    //
    // @TruffleBoundary
    // private void doHalt(Node node, MaterializedFrame mFrame) {
    // final int currentStackDepth = currentStackDepth();
    // if (currentStackDepth < stackDepth) {
    // // HALT: just "stepped out"
    // --unfinishedStepCount;
    // strategyTrace("HALT AFTER", "unfinished steps=%d stackDepth: start=%d current=%d",
    // unfinishedStepCount, stackDepth, currentStackDepth);
    // // Should run in fast path
    // if (unfinishedStepCount <= 0) {
    // halt(node, mFrame, false);
    // }
    // strategyTrace("RESUME AFTER", "");
    // }
    // }
    // }, "Debugger StepOver");

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
        private int unfinishedStepCount;
        private final int startStackDepth;
        private EventBinding<?> beforeHaltBinding;

        StepOverNested(int stepCount, final int startStackDepth) {
            this.unfinishedStepCount = stepCount;
            this.startStackDepth = startStackDepth;
        }

        @Override
        protected void setStrategy(final int stackDepth) {
            strategyTrace("STRATEGY", "repeat=%d stack=%d", unfinishedStepCount, startStackDepth);
            beforeHaltBinding = instrumenter.attachListener(HALT_FILTER, new ExecutionEventListener() {

                public void onEnter(EventContext context, VirtualFrame frame) {
                    final int currentStackDepth = currentStackDepth();
                    if (currentStackDepth <= startStackDepth) {
                        // At original step depth (or smaller) after being nested
                        --unfinishedStepCount;
                        if (TRACE) {
                            strategyTrace("HALT BEFORE", "stack=%d,%d unfinished=%d", startStackDepth, currentStackDepth(), unfinishedStepCount);
                        }
                        if (unfinishedStepCount <= 0) {
                            halt(context.getInstrumentedNode(), frame.materialize(), true);
                        }
                        // TODO (mlvdv) fixme for multiple steps
                    }
                    if (TRACE) {
                        strategyTrace("RESUME BEFORE", "stack=%d,%d", startStackDepth, currentStackDepth());
                    }
                }

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                }

            });
        }

        @Override
        protected void unsetStrategy() {
            beforeHaltBinding.dispose();
        }
    }

    // beforeTagInstrument = instrumenter.attach(STEPPING_TAG, new
    // StandardBeforeInstrumentListener() {
    // @TruffleBoundary
    // @Override
    // public void onEnter(Probe probe, Node node, VirtualFrame vFrame) {
    // final int currentStackDepth = currentStackDepth();
    // if (currentStackDepth <= startStackDepth) {
    // // At original step depth (or smaller) after being nested
    // --unfinishedStepCount;
    // strategyTrace("HALT AFTER", "unfinished steps=%d stackDepth start=%d current=%d",
    // unfinishedStepCount, stackDepth, currentStackDepth);
    // if (unfinishedStepCount <= 0) {
    // halt(node, vFrame.materialize(), false);
    // }
    // // TODO (mlvdv) fixme for multiple steps
    // strategyTrace("RESUME BEFORE", "");
    // }
    // }
    // }, "Debugger StepOverNested");

    /**
     * Information and debugging state for a single Truffle execution (which make take place over
     * one or more suspended executions). This holds interaction state, for example what is
     * executing (e.g. some {@link Source}), what the execution mode is ("stepping" or
     * "continuing"). When not running, this holds a cache of the Truffle stack for this particular
     * execution, effectively hiding the Truffle stack for any currently suspended executions (down
     * the stack).
     */
    private final class DebugExecutionContext {

        // Previous halted context in stack
        private final DebugExecutionContext predecessor;

        // The current execution level; first is 0.
        private final int level;  // Number of contexts suspended below
        private final Source source;
        private final int contextStackBase;  // Where the stack for this execution starts
        private final List<String> warnings = new ArrayList<>();

        private boolean running;

        /**
         * The stepping strategy currently configured in the debugger.
         */
        private StepStrategy strategy;

        /**
         * Where halted; null if running.
         */
        private Node haltedNode;

        /**
         * Where halted; null if running.
         */
        private MaterializedFrame haltedFrame;

        /**
         * Subset of the Truffle stack corresponding to the current execution, not including the
         * current frame.
         */
        private List<FrameInstance> contextStack;

        private DebugExecutionContext(Source executionSource, DebugExecutionContext previousContext) {
            this(executionSource, previousContext, -1);
        }

        private DebugExecutionContext(Source executionSource, DebugExecutionContext previousContext, int depth) {
            this.source = executionSource;
            this.predecessor = previousContext;
            this.level = previousContext == null ? 0 : previousContext.level + 1;

            // "Base" is the number of stack frames for all nested (halted) executions.
            this.contextStackBase = depth == -1 ? currentStackDepth() : depth;
            this.running = true;
            contextTrace("NEW CONTEXT");
        }

        /**
         * Sets up a strategy for the next resumption of execution.
         *
         * @param stepStrategy
         */
        void setStrategy(StepStrategy stepStrategy) {
            setStrategy(currentStackDepth(), stepStrategy);
        }

        void setStrategy(int depth, StepStrategy stepStrategy) {
            if (this.strategy == null) {
                this.strategy = stepStrategy;
                this.strategy.enable(this, depth);
                if (TRACE) {
                    contextTrace("SET MODE <none>-->" + stepStrategy.getName());
                }
            } else {
                strategy.disable();
                strategy = stepStrategy;
                strategy.enable(this, currentStackDepth());
                contextTrace("SWITCH MODE %s-->%s", strategy.getName(), stepStrategy.getName());
            }
        }

        void clearStrategy() {
            if (strategy != null) {
                final StepStrategy oldStrategy = strategy;
                strategy.disable();
                strategy = null;
                contextTrace("CLEAR MODE %s--><none>", oldStrategy.getName());
            }
        }

        /**
         * Handle a program halt, caused by a breakpoint, stepping strategy, or other cause.
         *
         * @param astNode the guest language node at which execution is halted
         * @param mFrame the current execution frame where execution is halted
         * @param before {@code true} if halted <em>before</em> the node, else <em>after</em>.
         */
        @TruffleBoundary
        void halt(Node astNode, MaterializedFrame mFrame, boolean before, String haltReason) {
            assert running;
            assert haltedNode == null;
            assert haltedFrame == null;

            haltedNode = astNode;
            haltedFrame = mFrame;
            running = false;

            clearStrategy();

            // Clean up, just in cased the one-shot breakpoints got confused
            breakpoints.disposeOneShots();

            // Includes the "caller" frame (not iterated)
            final int contextStackDepth = (currentStackDepth() - contextStackBase) + 1;

            final List<String> recentWarnings = new ArrayList<>(warnings);
            warnings.clear();

            final List<FrameInstance> frames = new ArrayList<>();
            // Map the Truffle stack for this execution, ignore nested executions
            // Ignore frames for which no CallNode is available.
            // The top/current/0 frame is not produced by the iterator; reported separately
            Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                int stackIndex = 1;

                @Override
                public FrameInstance visitFrame(FrameInstance frameInstance) {
                    if (stackIndex < contextStackDepth) {
                        final Node callNode = frameInstance.getCallNode();
                        if (callNode != null) {
                            final SourceSection sourceSection = callNode.getEncapsulatingSourceSection();
                            if (sourceSection != null && !sourceSection.getIdentifier().equals("<unknown>")) {
                                frames.add(frameInstance);
                            } else if (TRACE) {
                                contextTrace("HIDDEN frame added: " + callNode);
                                frames.add(frameInstance);
                            }
                        } else if (TRACE) {
                            contextTrace("HIDDEN frame added");
                            frames.add(frameInstance);
                        }
                        stackIndex++;
                        return null;
                    }
                    return frameInstance;
                }
            });
            contextStack = Collections.unmodifiableList(frames);

            if (TRACE) {
                final String reason = haltReason == null ? "" : haltReason + "";
                final String where = before ? "BEFORE" : "AFTER";
                contextTrace("HALT %s : (%s) stack base=%d", where, reason, contextStackBase);
            }

            try {
                // Pass control to the debug client with current execution suspended
                ACCESSOR.dispatchEvent(engine, new SuspendedEvent(Debugger.this, haltedNode, haltedFrame, contextStack, recentWarnings));
                // Debug client finished normally, execution resumes
                // Presume that the client has set a new strategy (or default to Continue)
                running = true;
            } catch (KillException e) {
                contextTrace("KILL");
                throw e;
            } catch (QuitException e) {
                contextTrace("QUIT");
                throw e;
            } finally {
                haltedNode = null;
                haltedFrame = null;
            }

        }

        void logWarning(String warning) {
            warnings.add(warning);
        }

        /*
         * private void printStack(PrintStream stream) { getFrames(); if (frames == null) {
         * stream.println("<empty stack>"); } else { final Visualizer visualizer =
         * provider.getVisualizer(); for (FrameDebugDescription frameDesc : frames) { final
         * StringBuilder sb = new StringBuilder("    frame " + Integer.toString(frameDesc.index()));
         * sb.append(":at " + visualizer.displaySourceLocation(frameDesc.node())); sb.append(":in '"
         * + visualizer.displayMethodName(frameDesc.node()) + "'"); stream.println(sb.toString()); }
         * } }
         */

        void contextTrace(String format, Object... args) {
            if (TRACE) {
                final String srcName = (source != null) ? source.getName() : "no source";
                Debugger.trace("<%d> %s (%s)", level, String.format(format, args), srcName);
            }
        }
    }

    // TODO (mlvdv) wish there were fast-path access to stack depth
    /**
     * Depth of current Truffle stack, including nested executions. Includes the top/current frame,
     * which the standard iterator does not count: {@code 0} if no executions.
     */
    @TruffleBoundary
    private static int currentStackDepth() {
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
        debugContext = new DebugExecutionContext(execSource, debugContext, depth);
        prepareContinue(depth);
        debugContext.contextTrace("START EXEC ");
        ACCESSOR.dispatchEvent(engine, new ExecutionEvent(this));
    }

    void executionEnded() {
        breakpoints.disposeOneShots();
        debugContext.clearStrategy();
        debugContext.contextTrace("END EXEC ");
        // Pop the stack of execution contexts.
        debugContext = debugContext.predecessor;
    }

    /**
     * Evaluates a snippet of code in a halted execution context.
     *
     * @param ev
     * @param code
     * @param frameInstance
     * @return
     * @throws IOException
     */
    Object evalInContext(SuspendedEvent ev, String code, FrameInstance frameInstance) throws IOException {
        if (frameInstance == null) {
            return ACCESSOR.evalInContext(engine, ev, code, debugContext.haltedNode, debugContext.haltedFrame);
        } else {
            return ACCESSOR.evalInContext(engine, ev, code, frameInstance.getCallNode(), frameInstance.getFrame(FrameAccess.MATERIALIZE, true).materialize());
        }
    }

    static final class AccessorDebug extends Accessor {

        @Override
        protected Closeable executionStart(Object vm, int currentDepth, final boolean initializeDebugger, Source s) {
            final Debugger debugger = find((PolyglotEngine) vm, initializeDebugger);
            if (debugger == null) {
                return new Closeable() {
                    @Override
                    public void close() throws IOException {
                    }
                };
            }
            debugger.executionStarted(currentDepth, s);
            return new Closeable() {
                @Override
                public void close() throws IOException {
                    debugger.executionEnded();
                }
            };
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Class<? extends TruffleLanguage> findLanguage(Node node) {
            return super.findLanguage(node);
        }

        @Override
        protected void dispatchEvent(Object vm, Object event) {
            super.dispatchEvent(vm, event);
        }

        @Override
        protected Object evalInContext(Object vm, Object ev, String code, Node node, MaterializedFrame frame) throws IOException {
            return super.evalInContext(vm, ev, code, node, frame);
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected CallTarget parse(Class<? extends TruffleLanguage> languageClass, Source code, Node context, String... argumentNames) throws IOException {
            return super.parse(languageClass, code, context, argumentNames);
        }
    }

    // registers into Accessor.DEBUG
    static final AccessorDebug ACCESSOR = new AccessorDebug();
}
