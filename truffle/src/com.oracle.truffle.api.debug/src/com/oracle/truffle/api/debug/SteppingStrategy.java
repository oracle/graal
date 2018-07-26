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

import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ProbeNode;

/**
 * Implementation of a strategy for a debugger <em>action</em> that allows execution to continue
 * until it reaches another location e.g "step in" vs. "step over".
 */
abstract class SteppingStrategy {

    /*
     * Indicates that a stepping strategy was consumed by an suspended event.
     */
    private boolean consumed;
    private SteppingStrategy next;

    void consume() {
        consumed = true;
    }

    boolean isConsumed() {
        return consumed;
    }

    void notifyCallEntry() {
    }

    void notifyCallExit() {
    }

    @SuppressWarnings("unused")
    void notifyNodeEntry(EventContext context) {
    }

    @SuppressWarnings("unused")
    void notifyNodeExit(EventContext context) {
    }

    Object notifyOnUnwind() {
        return null;
    }

    boolean isStopAfterCall() {
        return true;
    }

    boolean isCollectingInputValues() {
        return false;
    }

    /**
     * Like {@link #isActive(EventContext, SuspendAnchor)}, but is called on a node entry/return
     * only. It allows to include the node entry/return events to call entry/exit events for cases
     * when the step over/out is not determined by pushed frames only, but pushed nodes also.
     */
    final boolean isActiveOnStepTo(EventContext context, SuspendAnchor suspendAnchor) {
        if (SuspendAnchor.BEFORE == suspendAnchor) {
            notifyNodeEntry(context);
        } else {
            notifyNodeExit(context);
        }
        return isActive(context, suspendAnchor);
    }

    /**
     * Test if the strategy is active at this context. If yes,
     * {@link #step(DebuggerSession, EventContext, SuspendAnchor)} will be called.
     */
    @SuppressWarnings("unused")
    boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
        return true;
    }

    abstract boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor);

    @SuppressWarnings("unused")
    void initialize(SuspendedContext context, SuspendAnchor suspendAnchor) {
    }

    boolean isDone() {
        return false;
    }

    boolean isUnwind() {
        return false;
    }

    boolean isKill() {
        return false;
    }

    boolean isComposable() {
        return false;
    }

    @SuppressWarnings("unused")
    void add(SteppingStrategy nextStrategy) {
        throw new UnsupportedOperationException("Not composable.");
    }

    static SteppingStrategy createKill() {
        return new Kill();
    }

    static SteppingStrategy createAlwaysHalt() {
        return new AlwaysHalt();
    }

    static SteppingStrategy createContinue() {
        return new Continue();
    }

    static SteppingStrategy createStepInto(DebuggerSession session, StepConfig config) {
        return new StepInto(session, config);
    }

    static SteppingStrategy createStepOut(DebuggerSession session, StepConfig config) {
        return new StepOut(session, config);
    }

    static SteppingStrategy createStepOver(DebuggerSession session, StepConfig config) {
        return new StepOver(session, config);
    }

    static SteppingStrategy createUnwind(int depth) {
        return new Unwind(depth);
    }

    static SteppingStrategy createComposed(SteppingStrategy strategy1, SteppingStrategy strategy2) {
        return new ComposedStrategy(strategy1, strategy2);
    }

    private static final class Kill extends SteppingStrategy {

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isKill() {
            return true;
        }

        @Override
        public String toString() {
            return "KILL";
        }

    }

    private static final class AlwaysHalt extends SteppingStrategy {

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return SuspendAnchor.BEFORE == suspendAnchor;
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            return SuspendAnchor.BEFORE == suspendAnchor;
        }

        @Override
        public String toString() {
            return "HALT";
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
    private static final class Continue extends SteppingStrategy {

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public String toString() {
            return "CONTINUE";
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
    private static final class StepInto extends SteppingStrategy {

        private final DebuggerSession session;
        private final StepConfig stepConfig;
        private int stackCounter;
        private int unfinishedStepCount;

        StepInto(DebuggerSession session, StepConfig stepConfig) {
            this.session = session;
            this.stepConfig = stepConfig;
            this.unfinishedStepCount = stepConfig.getCount();
        }

        @Override
        void initialize(SuspendedContext context, SuspendAnchor suspendAnchor) {
            this.stackCounter = 0;
        }

        @Override
        void notifyCallEntry() {
            stackCounter++;
        }

        @Override
        void notifyCallExit() {
            stackCounter--;
        }

        @Override
        boolean isStopAfterCall() {
            return stackCounter < 0;
        }

        @Override
        boolean isCollectingInputValues() {
            return stepConfig.containsSourceElement(session, SourceElement.EXPRESSION);
        }

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return stepConfig.matches(session, context, suspendAnchor);
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            if (stepConfig.matches(session, context, suspendAnchor) ||
                            SuspendAnchor.AFTER == suspendAnchor && stackCounter < 0) {
                stackCounter = 0;
                if (--unfinishedStepCount <= 0) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("STEP_INTO(stackCounter=%s, stepCount=%s)", stackCounter, unfinishedStepCount);
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
    private static final class StepOut extends SteppingStrategy {

        private final DebuggerSession session;
        private final StepConfig stepConfig;
        private final boolean exprStepping;
        private int stackCounter;
        private int exprCounter;
        private int unfinishedStepCount;
        private boolean activeFrame = false;
        private boolean activeExpression = false;

        StepOut(DebuggerSession session, StepConfig stepConfig) {
            this.session = session;
            this.stepConfig = stepConfig;
            this.exprStepping = stepConfig.containsSourceElement(session, SourceElement.EXPRESSION);
            this.unfinishedStepCount = stepConfig.getCount();
        }

        @Override
        void initialize(SuspendedContext context, SuspendAnchor suspendAnchor) {
            this.stackCounter = 0;
            this.exprCounter = 0;
        }

        @Override
        void notifyCallEntry() {
            stackCounter++;
            activeFrame = false;
        }

        @Override
        void notifyCallExit() {
            boolean isOn = (--stackCounter) < 0;
            if (isOn) {
                activeFrame = true;
            }
        }

        @Override
        void notifyNodeEntry(EventContext context) {
            if (exprStepping && context.hasTag(SourceElement.EXPRESSION.getTag())) {
                exprCounter++;
                activeExpression = false;
            }
        }

        @Override
        void notifyNodeExit(EventContext context) {
            if (exprStepping && context.hasTag(SourceElement.EXPRESSION.getTag())) {
                boolean isOn = (--exprCounter) < 0;
                if (isOn) {
                    activeExpression = true;
                }
            }
        }

        @Override
        boolean isStopAfterCall() {
            return activeFrame;
        }

        @Override
        boolean isCollectingInputValues() {
            return stepConfig.containsSourceElement(session, SourceElement.EXPRESSION);
        }

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return (activeFrame || activeExpression) && stepConfig.matches(session, context, suspendAnchor);
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            stackCounter = 0;
            exprCounter = 0;
            if (--unfinishedStepCount <= 0) {
                return true;
            }
            activeFrame = false; // waiting for next call exit
            return false;
        }

        @Override
        public String toString() {
            return String.format("STEP_OUT(stackCounter=%s, stepCount=%s)", stackCounter, unfinishedStepCount);
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
    private static final class StepOver extends SteppingStrategy {

        private final DebuggerSession session;
        private final StepConfig stepConfig;
        private final boolean exprStepping;
        private int stackCounter;
        private int exprCounter;
        private int unfinishedStepCount;
        private boolean activeFrame = true;
        private boolean activeExpression = true;

        StepOver(DebuggerSession session, StepConfig stepConfig) {
            this.session = session;
            this.stepConfig = stepConfig;
            this.exprStepping = stepConfig.containsSourceElement(session, SourceElement.EXPRESSION);
            this.unfinishedStepCount = stepConfig.getCount();
        }

        @Override
        void initialize(SuspendedContext context, SuspendAnchor suspendAnchor) {
            this.stackCounter = 0;
            this.exprCounter = context.hasTag(SourceElement.EXPRESSION.getTag()) && SuspendAnchor.BEFORE == suspendAnchor ? 0 : -1;
        }

        @Override
        void notifyCallEntry() {
            stackCounter++;
            activeFrame = stackCounter <= 0;
        }

        @Override
        void notifyCallExit() {
            boolean isOn = (--stackCounter) <= 0;
            if (isOn) {
                activeFrame = true;
            }
        }

        @Override
        void notifyNodeEntry(EventContext context) {
            if (exprStepping && context.hasTag(SourceElement.EXPRESSION.getTag())) {
                exprCounter++;
                activeExpression = exprCounter <= 0;
            }
        }

        @Override
        void notifyNodeExit(EventContext context) {
            if (exprStepping && context.hasTag(SourceElement.EXPRESSION.getTag())) {
                boolean isOn = (--exprCounter) < 0;
                if (isOn) {
                    activeExpression = true;
                }
            }
        }

        @Override
        boolean isStopAfterCall() {
            return stackCounter < 0;
        }

        @Override
        boolean isCollectingInputValues() {
            return stepConfig.containsSourceElement(session, SourceElement.EXPRESSION);
        }

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return activeFrame && activeExpression && stepConfig.matches(session, context, suspendAnchor);
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            if (stepConfig.matches(session, context, suspendAnchor) ||
                            SuspendAnchor.AFTER == suspendAnchor && (stackCounter < 0 || exprCounter < 0)) {
                stackCounter = 0;
                exprCounter = context.hasTag(SourceElement.EXPRESSION.getTag()) && SuspendAnchor.BEFORE == suspendAnchor ? 0 : -1;
                return --unfinishedStepCount <= 0;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("STEP_OVER(stackCounter=%s, stepCount=%s)", stackCounter, unfinishedStepCount);
        }

    }

    static final class Unwind extends SteppingStrategy {

        private final int depth; // Negative depth
        private int stackCounter;
        ThreadDeath unwind;

        Unwind(int depth) {
            this.depth = -depth;
        }

        @Override
        void initialize(SuspendedContext contex, SuspendAnchor suspendAnchor) {
            // We're entered already, we'll be called on exit once before unwind.
            this.stackCounter = 1;
        }

        @Override
        void notifyCallEntry() {
            stackCounter++;
        }

        @Override
        void notifyCallExit() {
            stackCounter--;
        }

        @Override
        Object notifyOnUnwind() {
            if (depth == stackCounter) {
                return ProbeNode.UNWIND_ACTION_REENTER;
            } else {
                return null;
            }
        }

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return SuspendAnchor.BEFORE == suspendAnchor;
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            return true;
        }

        @Override
        boolean isUnwind() {
            return true;
        }

        @Override
        boolean isStopAfterCall() {
            return false;
        }

        @Override
        public String toString() {
            return String.format("REENTER(stackCounter=%s, depth=%s)", stackCounter, depth);
        }

    }

    static final class ComposedStrategy extends SteppingStrategy {

        private final SteppingStrategy first;
        private SteppingStrategy last;
        private SteppingStrategy current;

        private ComposedStrategy(SteppingStrategy strategy1, SteppingStrategy strategy2) {
            strategy1.next = strategy2;
            first = strategy1;
            current = first;
            last = strategy2;
        }

        @Override
        void initialize(SuspendedContext contex, SuspendAnchor suspendAnchor) {
            assert current == first;
            current.initialize(contex, suspendAnchor);
        }

        @Override
        void notifyCallEntry() {
            current.notifyCallEntry();
        }

        @Override
        void notifyCallExit() {
            current.notifyCallExit();
        }

        @Override
        void notifyNodeEntry(EventContext context) {
            current.notifyNodeEntry(context);
        }

        @Override
        void notifyNodeExit(EventContext context) {
            current.notifyNodeExit(context);
        }

        @Override
        boolean isStopAfterCall() {
            return current.isStopAfterCall();
        }

        @Override
        boolean isActive(EventContext context, SuspendAnchor suspendAnchor) {
            return current.isActive(context, suspendAnchor);
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SuspendAnchor suspendAnchor) {
            boolean hit = current.step(steppingSession, context, suspendAnchor);
            if (hit) {
                if (current == last) {
                    return true;
                } else {
                    current = current.next;
                    current.initialize(SuspendedContext.create(context, steppingSession.getDebugger().getEnv()), suspendAnchor);
                }
            }
            return false;
        }

        @Override
        void consume() {
            assert current == last;
            last.consume();
        }

        @Override
        boolean isConsumed() {
            assert current == last;
            return last.isConsumed();
        }

        @Override
        boolean isDone() {
            if (current == last) {
                return last.isDone();
            }
            return false;
        }

        @Override
        boolean isKill() {
            if (current == last) {
                return last.isKill();
            }
            return false;
        }

        @Override
        boolean isComposable() {
            return true;
        }

        @Override
        synchronized void add(SteppingStrategy nextStrategy) {
            last.next = nextStrategy;
            last = nextStrategy;
        }

        @Override
        public String toString() {
            StringBuilder all = new StringBuilder();
            for (SteppingStrategy s = first; s.next != null; s = s.next) {
                if (all.length() > 0) {
                    all.append(", ");
                }
                all.append(s.toString());
            }

            return "COMPOSED(" + all + ")";
        }
    }
}
