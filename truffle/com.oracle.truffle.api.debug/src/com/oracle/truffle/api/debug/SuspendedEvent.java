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
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.debug.Debugger.HaltPosition;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * This event is delivered to all
 * {@link com.oracle.truffle.api.vm.PolyglotEngine.Builder#onEvent(com.oracle.truffle.api.vm.EventConsumer)
 * registered event handlers} when an execution is suspended on a
 * {@link Debugger#setLineBreakpoint(int, com.oracle.truffle.api.source.LineLocation, boolean)
 * breakpoint} or during {@link #prepareStepInto(int) stepping}. Methods in this event can only be
 * used while the handlers process the event. Then the state of the event becomes invalid and
 * subsequent calls to the event methods yield {@link IllegalStateException}. One can call
 * {@link #getDebugger()} and keep reference to it for as long as necessary.
 *
 * @since 0.9
 */
@SuppressWarnings("javadoc")
public final class SuspendedEvent {

    private static final boolean TRACE = Boolean.getBoolean("truffle.debug.trace");
    private static final String TRACE_PREFIX = "Suspnd: ";
    private static final PrintStream OUT = System.out;

    private static void trace(String format, Object... args) {
        if (TRACE) {
            OUT.println(TRACE_PREFIX + String.format(format, args));
        }
    }

    private final Debugger debugger;
    private final Node haltedNode;
    private final HaltPosition haltedPosition;
    private final MaterializedFrame haltedFrame;
    private final List<FrameInstance> stack;
    private final List<String> warnings;
    private volatile boolean kill;

    SuspendedEvent(Debugger debugger, Node haltedNode, HaltPosition haltedPosition, MaterializedFrame haltedFrame, List<FrameInstance> stack, List<String> warnings) {
        this.debugger = debugger;
        this.haltedNode = haltedNode;
        this.haltedPosition = haltedPosition;
        this.haltedFrame = haltedFrame;
        this.stack = stack;
        this.warnings = warnings;
        if (TRACE) {
            trace("Execution suspended at Node=" + haltedNode);
        }
    }

    /**
     * Debugger associated with the just suspended execution. This debugger remains valid after the
     * event is processed, it is possible and suggested to keep a reference to it and use it any
     * time later when evaluating sources in the {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     *
     * @return instance of debugger associated with the just suspended execution and any subsequent
     *         ones in the same {@link com.oracle.truffle.api.vm.PolyglotEngine}.
     * @since 0.9
     */
    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * The Guest Language AST node where execution is suspended. Depending on the value of
     * {@link #isHaltedBefore()}, the node is either:
     * <ul>
     * <li>{@code true}: the next (non-instrumentation) node to be executed, or</li>
     * <li>{@code false}: the most recent (non-instrumentation) node that has been executed.</li>
     * </ul>
     *
     * @see ExecutionEventListener
     * @since 0.9
     */
    public Node getNode() {
        return haltedNode;
    }

    /**
     * Is the suspended execution halted just <em>before</em> executing the instrumented (halted)
     * {@linkplain #getNode() node}? If {@code false} then the instrumented node was the most recent
     * (non-instrumentation) node executed.
     *
     * @since 0.14
     */
    public boolean isHaltedBefore() {
        return haltedPosition == HaltPosition.BEFORE;
    }

    /** @since 0.9 */
    public MaterializedFrame getFrame() {
        return haltedFrame;
    }

    /** @since 0.9 */
    public List<String> getRecentWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * Gets the stack frames from the currently halted
     * {@link com.oracle.truffle.api.vm.PolyglotEngine} execution, not counting the Node and Frame
     * where halted.
     *
     * @return list of stack frames
     * @since 0.9
     */
    @CompilerDirectives.TruffleBoundary
    public List<FrameInstance> getStack() {
        return stack;
    }

    /**
     * Prepare to execute in Continue mode when guest language program execution resumes. In this
     * mode execution will continue until either:
     * <ul>
     * <li>execution arrives at a node to which an enabled breakpoint is attached,
     * <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ul>
     *
     * @since 0.9
     */
    public void prepareContinue() {
        debugger.prepareContinue(-1);
    }

    /**
     * Prepare to execute in <strong>StepInto</strong> mode when guest language program execution
     * resumes. In this mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at at the <em>nth</em> node (specified by {@code stepCount}) with the
     * tag {@link StatementTag}, <strong>or</strong></li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * <li>A breakpoint set at a location where execution would halt is treated specially to avoid a
     * "double halt" during stepping:
     * <ul>
     * <li>execution halts only <em>once</em> at the location;</li>
     * <li>the halt counts as a breakpoint {@link Breakpoint#getHitCount() hit};</li>
     * <li>the mode reverts to {@linkplain #prepareContinue() Continue}, as if there were no
     * breakpoint; and</li>
     * <li>this special treatment applies only for breakpoints created <strong>before</strong> the
     * mode is set.</li>
     * </ul>
     * </ul>
     *
     * @param stepCount the number of times to perform StepInto before halting
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @since 0.9
     */
    public void prepareStepInto(int stepCount) {
        debugger.prepareStepInto(stepCount);
    }

    /**
     * Prepare to execute in <strong>StepOut</strong> mode when guest language program execution
     * resumes. In this mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at the nearest enclosing call site on the stack, <strong>or</strong>
     * </li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * </ul>
     *
     * @since 0.9
     */
    public void prepareStepOut() {
        debugger.prepareStepOut();
    }

    /**
     * Prepare to execute in StepOver mode when guest language program execution resumes. In this
     * mode:
     * <ul>
     * <li>Execution, when resumed, continues until either:
     * <ol>
     * <li>execution arrives at at the <em>nth</em> node (specified by {@code stepCount}) with the
     * tag {@link StatementTag}, ignoring nodes nested in function/method calls, <strong>or</strong>
     * </li>
     * <li>execution arrives at a {@link Breakpoint}, <strong>or</strong></li>
     * <li>execution completes.</li>
     * </ol>
     * </li>
     * <li>The mode persists only until either:
     * <ol>
     * <li>program execution resumes and then halts, at which time the mode reverts to
     * {@linkplain #prepareContinue() Continue}, <strong>or</strong></li>
     * <li>execution completes, at which time the mode reverts to {@linkplain #prepareContinue()
     * Continue}, <strong>or</strong></li>
     * <li>another mode is chosen.</li>
     * </ol>
     * </li>
     * <li>A breakpoint set at a location where execution would halt is treated specially to avoid a
     * "double halt" during stepping:
     * <ul>
     * <li>execution halts only <em>once</em> at the location;</li>
     * <li>the halt counts as a breakpoint {@link Breakpoint#getHitCount() hit};</li>
     * <li>the mode reverts to {@linkplain #prepareContinue() Continue}, as if there were no
     * breakpoint; and</li>
     * <li>this special treatment applies only for breakpoints created <strong>before</strong> the
     * mode is set.</li>
     * </ul>
     *
     * @param stepCount the number of times to perform StepOver before halting
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @since 0.9
     */
    public void prepareStepOver(int stepCount) {
        debugger.prepareStepOver(stepCount);
    }

    /**
     * Evaluates given code snippet in the context of currently suspended execution.
     *
     * @param code the snippet to evaluate
     * @param frameInstance the frame in which to evaluate the code; {@code null} means the current
     *            frame at the halted location.
     * @return the computed value
     * @throws IllegalArgumentException if the frame is not part of current execution stack
     * @throws IOException in case an evaluation goes wrong
     * @throws KillException if the evaluation is killed by the debugger
     * @since 0.9
     */
    public Object eval(String code, FrameInstance frameInstance) throws IOException {
        if (!stack.contains(frameInstance)) {
            throw new IllegalArgumentException();
        }
        return debugger.evalInContext(this, code, frameInstance);
    }

    /**
     * Generates a (potentially language-specific) description of an execution value in a part of
     * the current execution context, for example the value stored in a frame slot. The description
     * is intended to be useful to a guest language programmer.
     *
     * @param value an object presumed to represent a <em>value</em> managed by the language of the
     *            AST where execution is halted.
     * @param frameInstance the frame in which to evaluate the code;
     *
     * @return a user-oriented description of a possibly language-specific value
     * @throws IllegalArgumentException if the frame is not part of current execution stack
     * @since 0.15
     */
    public String toString(Object value, FrameInstance frameInstance) {
        if (!stack.contains(frameInstance)) {
            throw new IllegalArgumentException();
        }
        RootNode rootNode = null;
        if (frameInstance == stack.get(0)) {
            rootNode = haltedNode.getRootNode();
        } else if (frameInstance.getCallTarget() instanceof RootCallTarget) {
            rootNode = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
        }
        if (rootNode == null) {
            // Unknown language
            return value.toString();
        }
        return Debugger.ACCESSOR.toStringInContext(rootNode, value);
    }

    /**
     * Prepare to terminate the suspended execution represented by this event.
     *
     * @since 0.12
     */
    public void prepareKill() {
        kill = true;
    }

    boolean isKillPrepared() {
        return kill;
    }
}
