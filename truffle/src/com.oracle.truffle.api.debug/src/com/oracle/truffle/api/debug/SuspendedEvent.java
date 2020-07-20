/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleStackTrace;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.debug.DebuggerNode.InputValuesProvider;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Access for {@link Debugger} clients to the state of a guest language execution thread that has
 * been suspended, for example by a {@link Breakpoint} or stepping action.
 * <p>
 * <h4>Event lifetime</h4>
 * <ul>
 * <li>A {@link DebuggerSession} {@link Instrumenter instruments} guest language code in order to
 * implement {@linkplain Breakpoint breakpoints}, stepping actions, or other debugging actions on
 * behalf of the session's {@linkplain Debugger debugger} client.</li>
 *
 * <li>A session may choose to suspend a guest language execution thread when it receives
 * (synchronous) notification on the execution thread that it has reached an AST location
 * instrumented by the session.</li>
 *
 * <li>The session passes a new {@link SuspendedEvent} to the debugger client (synchronously) in a
 * {@linkplain SuspendedCallback#onSuspend(SuspendedEvent) callback} on the guest language execution
 * thread.</li>
 *
 * <li>Clients may access certain event state only on the execution thread where the event was
 * created and notification received; access from other threads can throws
 * {@link IllegalStateException}. Please see the javadoc of the individual method for details.</li>
 *
 * <li>A suspended thread resumes guest language execution after the client callback returns and the
 * thread unwinds back to instrumentation code in the AST.</li>
 *
 * <li>All event methods throw {@link IllegalStateException} after the suspended thread resumes
 * guest language execution.</li>
 * </ul>
 * </p>
 * <h4>Access to execution state</h4>
 * <p>
 * <ul>
 * <li>Method {@link #getStackFrames()} describes the suspended thread's location in guest language
 * code. This information becomes unusable beyond the lifetime of the event and must not be stored.
 * </li>
 *
 * <li>Method {@link #getReturnValue()} describes a local result when the thread is suspended just
 * {@link SuspendAnchor#AFTER after} the code source section.</li>
 * </ul>
 * </p>
 * <h4>Next debugging action</h4>
 * <p>
 * Clients use the following methods to request the debugging action(s) that will take effect when
 * the event's thread resumes guest language execution. All prepare requests accumulate until
 * resumed.
 * <ul>
 * <li>{@link #prepareStepInto(int)}</li>
 * <li>{@link #prepareStepOut(int)}</li>
 * <li>{@link #prepareStepOver(int)}</li>
 * <li>{@link #prepareKill()}</li>
 * <li>{@link #prepareContinue()}</li>
 * </ul>
 * If no debugging action is requested then {@link #prepareContinue() continue} is assumed.
 * </p>
 *
 * @since 0.9
 */

public final class SuspendedEvent {

    private final SourceSection sourceSection;
    private final SuspendAnchor suspendAnchor;

    private final Thread thread;

    private DebuggerSession session;
    private SuspendedContext context;
    private MaterializedFrame materializedFrame;
    private InsertableNode insertableNode;
    private List<Breakpoint> breakpoints;
    private InputValuesProvider inputValuesProvider;
    private volatile Object returnValue;
    private DebugException exception;

    private volatile boolean disposed;
    private volatile SteppingStrategy nextStrategy;

    private final Map<Breakpoint, Throwable> conditionFailures;
    private DebugStackFrameIterable cachedFrames;
    private List<List<DebugStackTraceElement>> cachedAsyncFrames;

    SuspendedEvent(DebuggerSession session, Thread thread, SuspendedContext context, MaterializedFrame frame, SuspendAnchor suspendAnchor,
                    InsertableNode insertableNode, InputValuesProvider inputValuesProvider, Object returnValue, DebugException exception,
                    List<Breakpoint> breakpoints, Map<Breakpoint, Throwable> conditionFailures) {
        this.session = session;
        this.context = context;
        this.suspendAnchor = suspendAnchor;
        this.materializedFrame = frame;
        this.insertableNode = insertableNode;
        this.inputValuesProvider = inputValuesProvider;
        this.returnValue = returnValue;
        this.exception = exception;
        this.conditionFailures = conditionFailures;
        this.breakpoints = breakpoints == null ? Collections.<Breakpoint> emptyList() : Collections.<Breakpoint> unmodifiableList(breakpoints);
        this.thread = thread;
        this.sourceSection = context.getInstrumentedSourceSection();
    }

    boolean isDisposed() {
        return disposed;
    }

    void clearLeakingReferences() {
        this.disposed = true;

        // cleanup data for potential memory leaks
        this.inputValuesProvider = null;
        this.returnValue = null;
        this.exception = null;
        this.breakpoints = null;
        this.materializedFrame = null;
        this.cachedFrames = null;
        this.session = null;
        this.context = null;
        this.insertableNode = null;
    }

    void verifyValidState(boolean allowDifferentThread) {
        if (disposed) {
            throw new IllegalStateException("Not in a suspended state.");
        }
        if (!allowDifferentThread && Thread.currentThread() != thread) {
            throw new IllegalStateException("Illegal thread access.");
        }
    }

    SteppingStrategy getNextStrategy() {
        SteppingStrategy strategy = nextStrategy;
        if (strategy == null) {
            return SteppingStrategy.createContinue();
        }
        return strategy;
    }

    private synchronized void setNextStrategy(SteppingStrategy nextStrategy) {
        verifyValidState(true);
        if (this.nextStrategy == null) {
            this.nextStrategy = nextStrategy;
        } else if (this.nextStrategy.isKill()) {
            throw new IllegalStateException("Calls to prepareKill() cannot be followed by any other preparation call.");
        } else if (this.nextStrategy.isDone()) {
            throw new IllegalStateException("Calls to prepareContinue() cannot be followed by any other preparation call.");
        } else if (this.nextStrategy.isComposable()) {
            this.nextStrategy.add(nextStrategy);
        } else {
            this.nextStrategy = SteppingStrategy.createComposed(this.nextStrategy, nextStrategy);
        }
    }

    /**
     * Returns the debugger session this suspended event was created for.
     * <p>
     * This method is thread-safe.
     *
     * @since 0.17
     */
    public DebuggerSession getSession() {
        verifyValidState(true);
        return session;
    }

    Thread getThread() {
        return thread;
    }

    SuspendedContext getContext() {
        return context;
    }

    InsertableNode getInsertableNode() {
        return insertableNode;
    }

    /**
     * Returns the guest language source section of the AST node before/after the execution is
     * suspended. Returns <code>null</code> if no source section information is available.
     * <p>
     * This method is thread-safe.
     *
     * @since 0.17
     */
    public SourceSection getSourceSection() {
        verifyValidState(true);
        return session.resolveSection(sourceSection);
    }

    /**
     * Returns where, within the guest language {@link #getSourceSection() source section}, the
     * suspended position is.
     *
     * @since 0.32
     */
    public SuspendAnchor getSuspendAnchor() {
        verifyValidState(true);
        return suspendAnchor;
    }

    /**
     * Returns <code>true</code> if the underlying guest language source location is denoted as the
     * source element.
     *
     * @param sourceElement the source element to check, must not be <code>null</code>.
     * @since 0.33
     */
    public boolean hasSourceElement(SourceElement sourceElement) {
        return context.hasTag(sourceElement.getTag());
    }

    /**
     * Test if the language context of the source of the event is initialized.
     *
     * @since 0.26
     */
    public boolean isLanguageContextInitialized() {
        verifyValidState(true);
        return context.isLanguageContextInitialized();
    }

    /**
     * Returns the input values of the current source element gathered from return values of it's
     * executed children. The input values are available only during stepping through the source
     * elements hierarchy and only on {@link SuspendAnchor#AFTER AFTER} {@link #getSuspendAnchor()
     * suspend anchor}. There can be <code>null</code> values in the returned array for children we
     * did not intercept return values from.
     *
     * @return the array of input values, or <code>null</code> when no input is available.
     * @since 0.33
     */
    public DebugValue[] getInputValues() {
        if (inputValuesProvider == null) {
            return null;
        }
        Object[] inputValues = inputValuesProvider.getDebugInputValues(materializedFrame);
        int n = inputValues.length;
        DebugValue[] values = new DebugValue[n];
        for (int i = 0; i < n; i++) {
            if (inputValues[i] != null) {
                values[i] = getTopStackFrame().wrapHeapValue(inputValues[i]);
            } else {
                values[i] = null;
            }
        }
        return values;
    }

    /**
     * Returns the return value of the currently executed source location. Returns <code>null</code>
     * if the execution is suspended {@link SuspendAnchor#BEFORE before} a guest language location.
     * The returned value is <code>null</code> if an exception occurred during execution of the
     * instrumented source element, the exception is provided by {@link #getException()}.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @since 0.17
     */
    public DebugValue getReturnValue() {
        verifyValidState(false);
        Object ret = returnValue;
        if (ret == null) {
            return null;
        }
        return getTopStackFrame().wrapHeapValue(ret);
    }

    Object getReturnObject() {
        return returnValue;
    }

    /**
     * Change the return value. When there is a {@link #getReturnValue() return value} at the
     * current location, this method modifies the return value to a new one.
     *
     * @param newValue the new return value, can not be <code>null</code>
     * @throws IllegalStateException when {@link #getReturnValue()} returns <code>null</code>
     * @since 19.0
     */
    public void setReturnValue(DebugValue newValue) {
        verifyValidState(false);
        if (returnValue == null) {
            throw new IllegalStateException("Can not set return value when there is no current return value.");
        }
        this.returnValue = newValue.get();
    }

    /**
     * Returns the debugger representation of a guest language exception that caused this suspended
     * event (via an exception breakpoint, for instance). Returns <code>null</code> when no
     * exception occurred.
     *
     * @since 19.0
     */
    public DebugException getException() {
        return exception;
    }

    MaterializedFrame getMaterializedFrame() {
        return materializedFrame;
    }

    /**
     * Returns the cause of failure, if any, during evaluation of a breakpoint's
     * {@linkplain Breakpoint#setCondition(String) condition}.
     *
     * <p>
     * This method is thread-safe.
     *
     * @param breakpoint a breakpoint associated with this event
     * @return the cause of condition failure
     *
     * @since 0.17
     */
    public Throwable getBreakpointConditionException(Breakpoint breakpoint) {
        verifyValidState(true);
        if (conditionFailures == null) {
            return null;
        }
        return conditionFailures.get(breakpoint);
    }

    /**
     * Returns the {@link Breakpoint breakpoints} that individually would cause the "hit" where
     * execution is suspended. If {@link Debugger#install(com.oracle.truffle.api.debug.Breakpoint)
     * Debugger-associated} breakpoint was hit, it is not possible to change the state of returned
     * breakpoint.
     * <p>
     * This method is thread-safe.
     *
     * @return an unmodifiable list of breakpoints
     *
     * @since 0.17
     */
    public List<Breakpoint> getBreakpoints() {
        verifyValidState(true);
        return breakpoints;
    }

    /**
     * Returns the topmost stack frame returned by {@link #getStackFrames()}.
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @see #getStackFrames()
     * @since 0.17
     */
    public DebugStackFrame getTopStackFrame() {
        // there must be always a top stack frame.
        return getStackFrames().iterator().next();
    }

    /**
     * Returns a list of guest language stack frame objects that indicate the current guest language
     * location. There is always at least one, the topmost, stack frame available. The returned
     * stack frames are usable only during {@link SuspendedCallback#onSuspend(SuspendedEvent)
     * suspend} and should not be stored permanently.
     *
     * <p>
     * This method is not thread-safe and will throw an {@link IllegalStateException} if called on
     * another thread than it was created with.
     *
     * @since 0.17
     */
    public Iterable<DebugStackFrame> getStackFrames() {
        verifyValidState(false);
        if (cachedFrames == null) {
            cachedFrames = new DebugStackFrameIterable();
        }
        return cachedFrames;
    }

    /**
     * Get a list of asynchronous stack traces that led to scheduling of the current execution.
     * Returns an empty list if no asynchronous stack is known. The first asynchronous stack is at
     * the first index in the list. A possible next asynchronous stack (that scheduled execution of
     * the previous one) is at the next index in the list.
     * <p>
     * Languages might not provide asynchronous stack traces by default for performance reasons.
     * Call {@link DebuggerSession#setAsynchronousStackDepth(int)} to request asynchronous stacks.
     * Languages may provide asynchronous stacks if it's of no performance penalty, or if requested
     * by other options.
     *
     * @see DebuggerSession#setAsynchronousStackDepth(int)
     * @since 20.1.0
     */
    public List<List<DebugStackTraceElement>> getAsynchronousStacks() {
        verifyValidState(false);
        if (cachedAsyncFrames == null) {
            cachedAsyncFrames = new DebugAsyncStackFrameLists(session, getStackFrames());
        }
        return cachedAsyncFrames;
    }

    static boolean isEvalRootStackFrame(DebuggerSession session, FrameInstance instance) {
        CallTarget target = instance.getCallTarget();
        RootNode root = null;
        if (target instanceof RootCallTarget) {
            root = ((RootCallTarget) target).getRootNode();
        }
        if (root != null && session.getDebugger().getEnv().isEngineRoot(root)) {
            return true;
        }
        return false;
    }

    /**
     * Prepare to execute in Continue mode when guest language program execution resumes. In this
     * mode execution will continue until either:
     * <ul>
     * <li>execution arrives at a node to which an enabled breakpoint is attached,
     * <strong>or:</strong></li>
     * <li>execution completes.</li>
     * </ul>
     * <p>
     * This method is thread-safe and the prepared Continue mode is appended to any other previously
     * prepared modes. No further modes can be prepared after continue.
     *
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already.
     * @since 0.9
     */
    public void prepareContinue() {
        setNextStrategy(SteppingStrategy.createContinue());
    }

    /**
     * Prepare to execute in <strong>step into</strong> mode when guest language program execution
     * resumes. See the description of {@link #prepareStepInto(StepConfig)} for details, calling
     * this is identical to
     * <code>{@link #prepareStepInto(StepConfig) prepareStepInto}.({@link StepConfig StepConfig}.{@link StepConfig#newBuilder() newBuilder}().{@link StepConfig.Builder#count(int) count}(stepCount).{@link StepConfig.Builder#build() build}())</code>
     * .
     *
     * @param stepCount the number of times to perform StepInto before halting
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already.
     * @since 0.9
     */
    public SuspendedEvent prepareStepInto(int stepCount) {
        return prepareStepInto(StepConfig.newBuilder().count(stepCount).build());
    }

    /**
     * Prepare to execute in <strong>step out</strong> mode when guest language program execution
     * resumes. See the description of {@link #prepareStepOut(StepConfig)} for details, calling this
     * is identical to
     * <code>{@link #prepareStepOut(StepConfig) prepareStepOut}.({@link StepConfig StepConfig}.{@link StepConfig#newBuilder() newBuilder}().{@link StepConfig.Builder#count(int) count}(stepCount).{@link StepConfig.Builder#build() build}())</code>
     * .
     *
     * @param stepCount the number of times to perform StepOver before halting
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already.
     * @since 0.26
     */
    public SuspendedEvent prepareStepOut(int stepCount) {
        return prepareStepOut(StepConfig.newBuilder().count(stepCount).build());
    }

    /**
     * Prepare to execute in <strong>step over</strong> mode when guest language program execution
     * resumes. See the description of {@link #prepareStepOver(StepConfig)} for details, calling
     * this is identical to
     * <code>{@link #prepareStepOver(StepConfig) prepareStepOver}.({@link StepConfig StepConfig}.{@link StepConfig#newBuilder() newBuilder}().{@link StepConfig.Builder#count(int) count}(stepCount).{@link StepConfig.Builder#build() build}())</code>
     * .
     *
     * @param stepCount the number of times to perform step over before halting
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalArgumentException if {@code stepCount <= 0}
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already.
     * @since 0.9
     */
    public SuspendedEvent prepareStepOver(int stepCount) {
        return prepareStepOver(StepConfig.newBuilder().count(stepCount).build());
    }

    /**
     * Prepare to execute in <strong>step into</strong> mode when guest language program execution
     * resumes. In this mode, the current thread continues until it arrives to a code location with
     * one of the enabled {@link StepConfig.Builder#sourceElements(SourceElement...) source
     * elements} and repeats that process {@link StepConfig.Builder#count(int) step count} times.
     * See {@link StepConfig} for the details about the stepping behavior.
     * <p>
     * This mode persists until the thread resumes and then suspends, at which time the mode reverts
     * to {@linkplain #prepareContinue() Continue}, or the thread dies.
     * <p>
     * A breakpoint set at a location where execution would suspend is treated specially as a single
     * event, to avoid multiple suspensions at a single location.
     * <p>
     * This method is thread-safe and the prepared StepInto mode is appended to any other previously
     * prepared modes.
     *
     * @param stepConfig the step configuration
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already, or when the current debugger
     *             session has no source elements enabled for stepping.
     * @throws IllegalArgumentException when the {@link StepConfig} contains source elements not
     *             enabled for stepping in the current debugger session.
     * @since 0.33
     */
    public SuspendedEvent prepareStepInto(StepConfig stepConfig) {
        verifyConfig(stepConfig);
        setNextStrategy(SteppingStrategy.createStepInto(session, stepConfig));
        return this;
    }

    /**
     * Prepare to execute in <strong>step out</strong> mode when guest language program execution
     * resumes. In this mode, the current thread continues until it arrives to an enclosing code
     * location with one of the enabled {@link StepConfig.Builder#sourceElements(SourceElement...)
     * source elements} and repeats that process {@link StepConfig.Builder#count(int) step count}
     * times. See {@link StepConfig} for the details about the stepping behavior.
     * <p>
     * This mode persists until the thread resumes and then suspends, at which time the mode reverts
     * to {@linkplain #prepareContinue() Continue}, or the thread dies.
     * <p>
     * A breakpoint set at a location where execution would suspend is treated specially as a single
     * event, to avoid multiple suspensions at a single location.
     * <p>
     * This method is thread-safe and the prepared StepInto mode is appended to any other previously
     * prepared modes.
     *
     * @param stepConfig the step configuration
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already, or when the current debugger
     *             session has no source elements enabled for stepping.
     * @throws IllegalArgumentException when the {@link StepConfig} contains source elements not
     *             enabled for stepping in the current debugger session.
     * @since 0.33
     */
    public SuspendedEvent prepareStepOut(StepConfig stepConfig) {
        verifyConfig(stepConfig);
        setNextStrategy(SteppingStrategy.createStepOut(session, stepConfig));
        return this;
    }

    /**
     * Prepare to execute in <strong>step out</strong> mode when guest language program execution
     * resumes. In this mode, the current thread continues until it arrives to a code location with
     * one of the enabled {@link StepConfig.Builder#sourceElements(SourceElement...) source
     * elements}, ignoring any nested ones, and repeats that process
     * {@link StepConfig.Builder#count(int) step count} times. See {@link StepConfig} for the
     * details about the stepping behavior.
     * <p>
     * This mode persists until the thread resumes and then suspends, at which time the mode reverts
     * to {@linkplain #prepareContinue() Continue}, or the thread dies.
     * <p>
     * A breakpoint set at a location where execution would suspend is treated specially as a single
     * event, to avoid multiple suspensions at a single location.
     * <p>
     * This method is thread-safe and the prepared StepInto mode is appended to any other previously
     * prepared modes.
     *
     * @param stepConfig the step configuration
     * @return this event instance for an easy concatenation of method calls
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already, or when the current debugger
     *             session has no source elements enabled for stepping.
     * @throws IllegalArgumentException when the {@link StepConfig} contains source elements not
     *             enabled for stepping in the current debugger session.
     * @since 0.33
     */
    public SuspendedEvent prepareStepOver(StepConfig stepConfig) {
        verifyConfig(stepConfig);
        setNextStrategy(SteppingStrategy.createStepOver(session, stepConfig));
        return this;
    }

    private void verifyConfig(StepConfig stepConfig) {
        Set<SourceElement> sessionElements = session.getSourceElements();
        if (sessionElements.isEmpty()) {
            throw new IllegalStateException("No source elements are enabled for stepping in the debugger session.");
        }
        Set<SourceElement> stepElements = stepConfig.getSourceElements();
        if (stepElements != null && !sessionElements.containsAll(stepElements)) {
            Set<SourceElement> extraElements = new HashSet<>(stepElements);
            extraElements.removeAll(sessionElements);
            throw new IllegalArgumentException("The step source elements " + extraElements + " are not enabled in the session.");
        }
    }

    /**
     * Prepare to unwind a frame. This frame and all frames above it are unwound off the execution
     * stack. The frame needs to be on the {@link #getStackFrames() execution stack of this event}.
     *
     * @param frame the frame to unwind
     * @throws IllegalArgumentException when the frame is not on the execution stack of this event
     * @since 0.31
     */
    public void prepareUnwindFrame(DebugStackFrame frame) throws IllegalArgumentException {
        if (frame.event != this) {
            throw new IllegalArgumentException("The stack frame is not in the scope of this event.");
        }
        setNextStrategy(SteppingStrategy.createUnwind(frame.getDepth()));
    }

    /**
     * Prepare to terminate the suspended execution represented by this event. One use-case for this
     * method is to shield an execution of an unknown code with a timeout:
     *
     * <p>
     * This method is thread-safe and the prepared termination is appended to any other previously
     * prepared modes. No further modes can be prepared after kill.
     *
     * @throws IllegalStateException when {@link #prepareContinue() continue} or
     *             {@link #prepareKill() kill} is prepared already.
     * @since 0.12
     */
    public void prepareKill() {
        setNextStrategy(SteppingStrategy.createKill());
    }

    /**
     * @since 0.17
     */
    @Override
    public String toString() {
        return "Suspended at " + getSourceSection() + " for thread " + getThread();
    }

    private final class DebugStackFrameIterable implements Iterable<DebugStackFrame> {

        private DebugStackFrame topStackFrame;
        private List<DebugStackFrame> otherFrames;

        private DebugStackFrame getTopStackFrame() {
            if (topStackFrame == null) {
                topStackFrame = new DebugStackFrame(SuspendedEvent.this, null, 0);
            }
            return topStackFrame;
        }

        private List<DebugStackFrame> getOtherFrames() {
            if (otherFrames == null) {
                final List<DebugStackFrame> frameInstances = new ArrayList<>();
                Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<FrameInstance>() {
                    private int depth = -context.getStackDepth() - 1 + getTopFrameIndex();

                    @Override
                    public FrameInstance visitFrame(FrameInstance frameInstance) {
                        if (isEvalRootStackFrame(session, frameInstance)) {
                            // we stop at eval root stack frames
                            return frameInstance;
                        }
                        Node callNode = frameInstance.getCallNode();
                        if (callNode != null && !hasRootTag(callNode)) {
                            return null;
                        }
                        if (++depth <= 0) {
                            return null;
                        }
                        frameInstances.add(new DebugStackFrame(SuspendedEvent.this, frameInstance, depth));
                        return null;
                    }
                });
                otherFrames = frameInstances;
            }
            return otherFrames;
        }

        private boolean hasRootTag(Node callNode) {
            Node node = callNode;
            do {
                if (node instanceof InstrumentableNode && ((InstrumentableNode) node).hasTag(RootTag.class)) {
                    return true;
                }
                node = node.getParent();
            } while (node != null);
            return false;
        }

        private int getTopFrameIndex() {
            if (context.getStackDepth() == 0) {
                return 0;
            }
            if (hasRootTag(context.getInstrumentedNode())) {
                return 0;
            } else {
                return 1; // Skip synthetic frame
            }
        }

        public Iterator<DebugStackFrame> iterator() {
            return new Iterator<DebugStackFrame>() {

                private int index = getTopFrameIndex();
                private Iterator<DebugStackFrame> otherIterator;

                public boolean hasNext() {
                    verifyValidState(false);
                    if (index == 0) {
                        return true;
                    } else {
                        return getOtherStackFrames().hasNext();
                    }
                }

                public DebugStackFrame next() {
                    verifyValidState(false);
                    if (index == 0) {
                        index++;
                        return getTopStackFrame();
                    } else {
                        return getOtherStackFrames().next();
                    }
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                private Iterator<DebugStackFrame> getOtherStackFrames() {
                    if (otherIterator == null) {
                        otherIterator = getOtherFrames().iterator();
                    }
                    return otherIterator;
                }

            };
        }

    }

    static final class DebugAsyncStackFrameLists extends AbstractList<List<DebugStackTraceElement>> {

        private final DebuggerSession session;
        private final List<List<DebugStackTraceElement>> stacks = new LinkedList<>();
        private int size = -1;

        DebugAsyncStackFrameLists(DebuggerSession session, Iterable<DebugStackFrame> callStack) {
            this.session = session;
            for (DebugStackFrame dFrame : callStack) {
                RootCallTarget target = dFrame.getCallTarget();
                Frame frame = dFrame.findTruffleFrame(FrameInstance.FrameAccess.READ_ONLY);
                List<DebugStackTraceElement> asyncStack = getAsynchronousStackFrames(session, target, frame);
                if (asyncStack != null && !asyncStack.isEmpty()) {
                    stacks.add(asyncStack);
                    break;
                }
            }
            if (stacks.isEmpty()) {
                size = 0;
            }
        }

        DebugAsyncStackFrameLists(DebuggerSession session, List<DebugStackTraceElement> stackTrace) {
            this.session = session;
            for (DebugStackTraceElement tElement : stackTrace) {
                RootCallTarget target = tElement.traceElement.getTarget();
                Frame frame = tElement.traceElement.getFrame();
                List<DebugStackTraceElement> asyncStack = getAsynchronousStackFrames(session, target, frame);
                if (asyncStack != null && !asyncStack.isEmpty()) {
                    stacks.add(asyncStack);
                    break;
                }
            }
            if (stacks.isEmpty()) {
                size = 0;
            }
        }

        @Override
        public List<DebugStackTraceElement> get(int index) {
            int filledLevel = fillStacks(index);
            if (filledLevel >= index) {
                return stacks.get(index);
            } else {
                throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
            }
        }

        @Override
        public int size() {
            if (size < 0) {
                fillStacks(Integer.MAX_VALUE);
            }
            return size;
        }

        @Override
        public Iterator<List<DebugStackTraceElement>> iterator() {
            return new Itr();
        }

        private int fillStacks(int level) {
            int lastLevel = stacks.size() - 1;
            if (size > 0 && level >= size) {
                return size - 1;
            }
            if (lastLevel >= level) {
                return level;
            } else {
                while (lastLevel < level) {
                    boolean added = false;
                    for (DebugStackTraceElement tElement : stacks.get(lastLevel)) {
                        RootCallTarget target = tElement.traceElement.getTarget();
                        Frame frame = tElement.traceElement.getFrame();
                        List<DebugStackTraceElement> asyncStack = getAsynchronousStackFrames(session, target, frame);
                        if (asyncStack != null && !asyncStack.isEmpty()) {
                            stacks.add(asyncStack);
                            added = true;
                            break;
                        }
                    }
                    if (added) {
                        lastLevel++;
                    } else {
                        size = lastLevel + 1;
                        break;
                    }
                }
                return lastLevel;
            }
        }

        private static List<DebugStackTraceElement> getAsynchronousStackFrames(DebuggerSession session, RootCallTarget target, Frame frame) {
            if (frame == null) {
                return null;
            }
            List<TruffleStackTraceElement> stack = TruffleStackTrace.getAsynchronousStackTrace(target, frame);
            if (stack == null) {
                return null;
            }
            Iterator<TruffleStackTraceElement> stackIterator = stack.iterator();
            if (!stackIterator.hasNext()) {
                return Collections.emptyList();
            }
            List<DebugStackTraceElement> debugStack = new ArrayList<>();
            while (stackIterator.hasNext()) {
                TruffleStackTraceElement tframe = stackIterator.next();
                debugStack.add(new DebugStackTraceElement(session, tframe));
            }
            return Collections.unmodifiableList(debugStack);
        }

        // This implementation prevents from calling size()
        private class Itr implements Iterator<List<DebugStackTraceElement>> {
            int cursor = 0;

            @Override
            public boolean hasNext() {
                return fillStacks(cursor) == cursor;
            }

            @Override
            public List<DebugStackTraceElement> next() {
                try {
                    int i = cursor;
                    List<DebugStackTraceElement> next = get(i);
                    cursor = i + 1;
                    return next;
                } catch (IndexOutOfBoundsException e) {
                    throw new NoSuchElementException();
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        }

    }
}
