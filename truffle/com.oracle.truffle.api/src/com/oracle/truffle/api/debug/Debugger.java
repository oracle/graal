/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.vm.TruffleVM;

/**
 * Language-agnostic engine for running Truffle languages under debugging control.
 */
public final class Debugger {

    private static final boolean TRACE = false;
    private static final String TRACE_PREFIX = "DEBUG ENGINE: ";

    private static final PrintStream OUT = System.out;

    private static final SyntaxTag STEPPING_TAG = StandardSyntaxTag.STATEMENT;
    private static final SyntaxTag CALL_TAG = StandardSyntaxTag.CALL;

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private final TruffleVM vm;
    private Source lastSource;

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

    /**
     * Implementation of line-oriented breakpoints.
     */
    private final LineBreakpointFactory lineBreaks;

    /**
     * Implementation of tag-oriented breakpoints.
     */
    private final TagBreakpointFactory tagBreaks;

    /**
     * Head of the stack of executions.
     */
    private DebugExecutionContext debugContext;

    Debugger(TruffleVM vm) {
        this.vm = vm;

        Source.setFileCaching(true);

        // Initialize execution context stack
        debugContext = new DebugExecutionContext(null, null);
        prepareContinue();
        debugContext.contextTrace("START EXEC DEFAULT");

        final BreakpointCallback breakpointCallback = new BreakpointCallback() {

            @TruffleBoundary
            public void haltedAt(Node astNode, MaterializedFrame mFrame, String haltReason) {
                debugContext.halt(astNode, mFrame, true, haltReason);
            }
        };

        final WarningLog warningLog = new WarningLog() {

            public void addWarning(String warning) {
                assert debugContext != null;
                debugContext.logWarning(warning);
            }
        };

        this.lineBreaks = new LineBreakpointFactory(this, breakpointCallback, warningLog);
        this.tagBreaks = new TagBreakpointFactory(this, breakpointCallback, warningLog);
    }

    TruffleVM vm() {
        return vm;
    }

    /**
     * Sets a breakpoint to halt at a source line.
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param lineLocation where to set the breakpoint (source, line number)
     * @param oneShot breakpoint disposes itself after fist hit, if {@code true}
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint can not be set.
     */
    @TruffleBoundary
    public Breakpoint setLineBreakpoint(int ignoreCount, LineLocation lineLocation, boolean oneShot) throws IOException {
        return lineBreaks.create(ignoreCount, lineLocation, oneShot);
    }

    /**
     * Sets a breakpoint to halt at any node holding a specified {@link SyntaxTag}.
     *
     * @param ignoreCount number of hits to ignore before halting
     * @param oneShot if {@code true} breakpoint removes it self after a hit
     * @return a new breakpoint, initially enabled
     * @throws IOException if the breakpoint already set
     */
    @TruffleBoundary
    public Breakpoint setTagBreakpoint(int ignoreCount, SyntaxTag tag, boolean oneShot) throws IOException {
        return tagBreaks.create(ignoreCount, tag, oneShot);
    }

    /**
     * Gets all existing breakpoints, whatever their status, in natural sorted order. Modification
     * save.
     */
    @TruffleBoundary
    public Collection<Breakpoint> getBreakpoints() {
        final Collection<Breakpoint> result = new ArrayList<>();
        result.addAll(lineBreaks.getAll());
        result.addAll(tagBreaks.getAll());
        return result;
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
    void prepareContinue() {
        debugContext.setStrategy(new Continue());
    }

    /**
     * Prepare to execute in StepInto mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>User breakpoints are disabled.</li>
     * <li>Execution will continue until either:
     * <ol>
     * <li>execution arrives at a node with the tag {@linkplain StandardSyntaxTag#STATEMENT
     * STATMENT}, <strong>or:</strong></li>
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
     * <li>execution arrives at a node with the tag {@linkplain StandardSyntaxTag#STATEMENT
     * STATEMENT} when not nested in one or more function/method calls, <strong>or:</strong></li>
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

    /**
     * Creates a language-specific factory to produce instances of {@link AdvancedInstrumentRoot}
     * that, when executed, computes the result of a textual expression in the language; used to
     * create an
     * {@linkplain Instrument#create(AdvancedInstrumentResultListener, AdvancedInstrumentRootFactory, Class, String)
     * Advanced Instrument}.
     *
     * @param expr a guest language expression
     * @param resultListener optional listener for the result of each evaluation.
     * @return a new factory
     * @throws IOException if the factory cannot be created, for example if the expression is badly
     *             formed.
     */
    AdvancedInstrumentRootFactory createAdvancedInstrumentRootFactory(Probe probe, String expr, AdvancedInstrumentResultListener resultListener) throws IOException {
        try {
            Class<? extends TruffleLanguage> langugageClass = ACCESSOR.findLanguage(probe);
            TruffleLanguage l = ACCESSOR.findLanguage(vm, langugageClass);
            DebugSupportProvider dsp = ACCESSOR.getDebugSupport(l);
            return dsp.createAdvancedInstrumentRootFactory(expr, resultListener);
        } catch (DebugSupportException ex) {
            throw new IOException(ex);
        }
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
            lineBreaks.setActive(false);
            tagBreaks.setActive(false);
        }

        @SuppressWarnings("unused")
        protected final void restoreUserBreakpoints() {
            lineBreaks.setActive(true);
            tagBreaks.setActive(true);
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
     * Strategy: per-statement stepping.
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution <em>arrives</em> at a STATEMENT node, <strong>or:</strong></li>
     * <li>execution <em>returns</em> to a CALL node and the call stack is smaller then when
     * execution started, <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </ul>
     *
     * @see Debugger#prepareStepInto(int)
     */
    private final class StepInto extends StepStrategy {
        private int unfinishedStepCount;

        StepInto(int stepCount) {
            super();
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int stackDepth) {
            Probe.setBeforeTagTrap(new SyntaxTagTrap(STEPPING_TAG) {

                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    // HALT: just before statement
                    --unfinishedStepCount;
                    strategyTrace("TRAP BEFORE", "unfinished steps=%d", unfinishedStepCount);
                    // Should run in fast path
                    if (unfinishedStepCount <= 0) {
                        halt(node, mFrame, true);
                    }
                    strategyTrace("RESUME BEFORE", "");
                }
            });
            Probe.setAfterTagTrap(new SyntaxTagTrap(CALL_TAG) {

                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    --unfinishedStepCount;
                    strategyTrace(null, "TRAP AFTER unfinished steps=%d", unfinishedStepCount);
                    if (currentStackDepth() < stackDepth) {
                        // HALT: just "stepped out"
                        if (unfinishedStepCount <= 0) {
                            halt(node, mFrame, false);
                        }
                    }
                    strategyTrace("RESUME AFTER", "");
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            Probe.setBeforeTagTrap(null);
            Probe.setAfterTagTrap(null);
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

        @Override
        protected void setStrategy(final int stackDepth) {
            Probe.setAfterTagTrap(new SyntaxTagTrap(CALL_TAG) {

                @TruffleBoundary
                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    // HALT:
                    final int currentStackDepth = currentStackDepth();
                    strategyTrace("TRAP AFTER", "stackDepth: start=%d current=%d", stackDepth, currentStackDepth);
                    if (currentStackDepth < stackDepth) {
                        halt(node, mFrame, false);
                    }
                    strategyTrace("RESUME AFTER", "");
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            Probe.setAfterTagTrap(null);
        }
    }

    /**
     * Strategy: per-statement stepping, so long as not nested in method calls (i.e. at original
     * stack depth).
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a STATEMENT node with stack depth no more than when started
     * <strong>or:</strong></li>
     * <li>the program completes.</li>
     * </ol>
     * </ul>
     */
    private final class StepOver extends StepStrategy {
        private int unfinishedStepCount;

        StepOver(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        protected void setStrategy(final int stackDepth) {
            Probe.setBeforeTagTrap(new SyntaxTagTrap(STEPPING_TAG) {

                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    final int currentStackDepth = currentStackDepth();
                    if (currentStackDepth <= stackDepth) {
                        // HALT: stack depth unchanged or smaller; treat like StepInto
                        --unfinishedStepCount;
                        if (TRACE) {
                            strategyTrace("TRAP BEFORE", "unfinished steps=%d stackDepth start=%d current=%d", unfinishedStepCount, stackDepth, currentStackDepth);
                        }
                        // Test should run in fast path
                        if (unfinishedStepCount <= 0) {
                            halt(node, mFrame, true);
                        }
                    } else {
                        // CONTINUE: Stack depth increased; don't count as a step
                        strategyTrace("STEP INTO", "unfinished steps=%d stackDepth start=%d current=%d", unfinishedStepCount, stackDepth, currentStackDepth);
                        // Stop treating like StepInto, start treating like StepOut
                        replaceStrategy(new StepOverNested(unfinishedStepCount, stackDepth));
                    }
                    strategyTrace("RESUME BEFORE", "");
                }
            });

            Probe.setAfterTagTrap(new SyntaxTagTrap(CALL_TAG) {

                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    final int currentStackDepth = currentStackDepth();
                    if (currentStackDepth < stackDepth) {
                        // HALT: just "stepped out"
                        --unfinishedStepCount;
                        strategyTrace("TRAP AFTER", "unfinished steps=%d stackDepth: start=%d current=%d", unfinishedStepCount, stackDepth, currentStackDepth);
                        // Should run in fast path
                        if (unfinishedStepCount <= 0) {
                            halt(node, mFrame, false);
                        }
                        strategyTrace("RESUME AFTER", "");
                    }
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            Probe.setBeforeTagTrap(null);
            Probe.setAfterTagTrap(null);
        }
    }

    /**
     * Strategy: per-statement stepping, not into method calls, in effect while at increased stack
     * depth
     * <ul>
     * <li>User breakpoints are enabled.</li>
     * <li>Execution continues until either:
     * <ol>
     * <li>execution arrives at a STATEMENT node with stack depth no more than when started
     * <strong>or:</strong></li>
     * <li>the program completes <strong>or:</strong></li>
     * </ol>
     * </ul>
     */
    private final class StepOverNested extends StepStrategy {
        private int unfinishedStepCount;
        private final int startStackDepth;

        StepOverNested(int stepCount, int startStackDepth) {
            this.unfinishedStepCount = stepCount;
            this.startStackDepth = startStackDepth;
        }

        @Override
        protected void setStrategy(final int stackDepth) {
            Probe.setBeforeTagTrap(new SyntaxTagTrap(STEPPING_TAG) {

                @Override
                public void tagTrappedAt(Node node, MaterializedFrame mFrame) {
                    final int currentStackDepth = currentStackDepth();
                    if (currentStackDepth <= startStackDepth) {
                        // At original step depth (or smaller) after being nested
                        --unfinishedStepCount;
                        strategyTrace("TRAP AFTER", "unfinished steps=%d stackDepth start=%d current=%d", unfinishedStepCount, stackDepth, currentStackDepth);
                        if (unfinishedStepCount <= 0) {
                            halt(node, mFrame, false);
                        }
                        // TODO (mlvdv) fixme for multiple steps
                        strategyTrace("RESUME BEFORE", "");
                    }
                }
            });
        }

        @Override
        protected void unsetStrategy() {
            Probe.setBeforeTagTrap(null);
        }
    }

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

        private DebugExecutionContext(Source executionSource, DebugExecutionContext previousContext) {
            this.source = executionSource;
            this.predecessor = previousContext;
            this.level = previousContext == null ? 0 : previousContext.level + 1;

            // "Base" is the number of stack frames for all nested (halted) executions.
            this.contextStackBase = currentStackDepth();
            this.running = true;
            contextTrace("NEW CONTEXT");
        }

        /**
         * Sets up a strategy for the next resumption of execution.
         *
         * @param stepStrategy
         */
        void setStrategy(StepStrategy stepStrategy) {
            if (this.strategy == null) {
                this.strategy = stepStrategy;
                this.strategy.enable(this, currentStackDepth());
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
            lineBreaks.disposeOneShots();

            final int contextStackDepth = currentStackDepth() - contextStackBase;
            if (TRACE) {
                final String reason = haltReason == null ? "" : haltReason + "";
                final String where = before ? "BEFORE" : "AFTER";
                contextTrace("HALT %s : (%s) stack base=%d", where, reason, contextStackBase);
                contextTrace("CURRENT STACK:");
                // printStack(OUT);
            }

            final List<String> recentWarnings = new ArrayList<>(warnings);
            warnings.clear();

            try {
                // Pass control to the debug client with current execution suspended
                ACCESSOR.dispatchEvent(vm, new SuspendedEvent(Debugger.this, astNode, mFrame, recentWarnings, contextStackDepth));
                // Debug client finished normally, execution resumes
                // Presume that the client has set a new strategy (or default to Continue)
                running = true;
            } catch (KillException e) {
                contextTrace("KILL");
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

    void executionStarted(Source source) {
        Source execSource = source;
        if (execSource == null) {
            execSource = lastSource;
        } else {
            lastSource = execSource;
        }
        // Push a new execution context onto stack
        debugContext = new DebugExecutionContext(execSource, debugContext);
        prepareContinue();
        debugContext.contextTrace("START EXEC ");
        ACCESSOR.dispatchEvent(vm, new ExecutionEvent(this));
    }

    void executionEnded() {
        lineBreaks.disposeOneShots();
        tagBreaks.disposeOneShots();
        debugContext.clearStrategy();
        debugContext.contextTrace("END EXEC ");
        // Pop the stack of execution contexts.
        debugContext = debugContext.predecessor;
    }

    private static final class AccessorDebug extends Accessor {
        @Override
        protected Closeable executionStart(TruffleVM vm, Debugger[] fillIn, Source s) {
            final Debugger d;
            if (fillIn[0] == null) {
                d = fillIn[0] = new Debugger(vm);
            } else {
                d = fillIn[0];
            }
            d.executionStarted(s);
            return new Closeable() {
                @Override
                public void close() throws IOException {
                    d.executionEnded();
                }
            };
        }

        @Override
        protected Class<? extends TruffleLanguage> findLanguage(Probe probe) {
            return super.findLanguage(probe);
        }

        @Override
        protected TruffleLanguage findLanguage(TruffleVM vm, Class<? extends TruffleLanguage> languageClass) {
            return super.findLanguage(vm, languageClass);
        }

        @Override
        protected DebugSupportProvider getDebugSupport(TruffleLanguage l) {
            return super.getDebugSupport(l);
        }

        @Override
        protected void dispatchEvent(TruffleVM vm, Object event) {
            super.dispatchEvent(vm, event);
        }
    }

    // registers into Accessor.DEBUG
    private static final AccessorDebug ACCESSOR = new AccessorDebug();
}
