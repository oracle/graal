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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.DebuggerSession.SteppingLocation;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.EventContext;

/**
 * Implementation of a strategy for a debugger <em>action</em> that allows execution to continue
 * until it reaches another location e.g "step in" vs. "step over".
 */
abstract class SteppingStrategy {

    /*
     * Indicates that a stepping strategy was consumed by an suspended event.
     */
    private boolean consumed;

    public void consume() {
        consumed = true;
    }

    public boolean isConsumed() {
        return consumed;
    }

    abstract boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location);

    abstract void initialize();

    public boolean isDone() {
        return false;
    }

    public boolean isKill() {
        return false;
    }

    public static SteppingStrategy createKill() {
        return new Kill();
    }

    public static SteppingStrategy createAlwaysHalt() {
        return new AlwaysHalt();
    }

    public static SteppingStrategy createContinue() {
        return new Continue();
    }

    public static SteppingStrategy createStepInto(int stepCount) {
        return new StepInto(stepCount);
    }

    public static SteppingStrategy createStepOut() {
        return new StepOut();
    }

    public static SteppingStrategy createStepOver(int stepCount) {
        return new StepOver(stepCount);
    }

    // TODO (mlvdv) wish there were fast-path access to stack depth
    // TODO (chumer) wish so too
    @TruffleBoundary
    private static int computeStackDepth() {
        FrameInstanceCounter counter = new FrameInstanceCounter();
        Truffle.getRuntime().iterateFrames(counter);
        return counter.getCount() + 1;
    }

    private static class FrameInstanceCounter implements FrameInstanceVisitor<Void> {

        private int count;

        public Void visitFrame(FrameInstance frameInstance) {
            count++;
            return null;
        }

        public int getCount() {
            return count;
        }
    }

    private static final class Kill extends SteppingStrategy {

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            return true;
        }

        @Override
        void initialize() {

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
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            return true;
        }

        @Override
        void initialize() {
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
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            return false;
        }

        @Override
        void initialize() {

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

        private int startStackDepth;
        private int unfinishedStepCount;

        StepInto(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        void initialize() {
            this.startStackDepth = computeStackDepth();
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            if (location == SteppingLocation.BEFORE_STATEMENT) {
                if (--unfinishedStepCount <= 0) {
                    return true;
                }
            } else if (location == SteppingLocation.AFTER_CALL) {
                if (computeStackDepth() < startStackDepth) {
                    if (--unfinishedStepCount <= 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("STEP_INTO(startStackDepth=%s, stepCount=%s)", startStackDepth, unfinishedStepCount);
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

        private int unfinishedStepCount;
        private int startStackDepth;

        StepOut() {
            this(1);
        }

        // TODO (mlvdv) not yet fully supported
        StepOut(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        void initialize() {
            this.startStackDepth = computeStackDepth();
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            if (location == SteppingLocation.AFTER_CALL) {
                if (computeStackDepth() < startStackDepth) {
                    if (--unfinishedStepCount <= 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("STEP_OUT(startStackDepth=%s, stepCount=%s)", startStackDepth, unfinishedStepCount);
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

        private int startStackDepth;
        private int unfinishedStepCount;

        StepOver(int stepCount) {
            this.unfinishedStepCount = stepCount;
        }

        @Override
        void initialize() {
            this.startStackDepth = computeStackDepth();
        }

        @Override
        boolean step(DebuggerSession steppingSession, EventContext context, SteppingLocation location) {
            if (location == SteppingLocation.BEFORE_STATEMENT) {
                if (computeStackDepth() <= startStackDepth) {
                    if (--unfinishedStepCount <= 0) {
                        return true;
                    }
                }
            } else if (location == SteppingLocation.AFTER_CALL) {
                if (computeStackDepth() < startStackDepth) {
                    if (--unfinishedStepCount <= 0) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("STEP_OVER(startStackDepth=%s, stepCount=%s)", startStackDepth, unfinishedStepCount);
        }

    }

}
