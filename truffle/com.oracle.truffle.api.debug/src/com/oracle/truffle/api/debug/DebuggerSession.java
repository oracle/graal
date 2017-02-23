/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.debug.Breakpoint.BreakpointConditionFailure;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Client access to {@link PolyglotEngine} {@linkplain Debugger debugging services}.
 *
 * <h4>Session lifetime</h4>
 * <p>
 * <ul>
 * <li>A {@link PolyglotEngine} debugging client
 * {@linkplain Debugger#startSession(SuspendedCallback) requests} a new {@linkplain DebuggerSession
 * session} from the {@linkplain Debugger#find(PolyglotEngine) engine's Debugger}.</li>
 *
 * <li>A client uses a session to request suspension of guest language execution threads, for
 * example by setting breakpoints or stepping.</li>
 *
 * <li>When a session suspends a guest language execution thread, it passes its client a new
 * {@link SuspendedEvent} via synchronous {@linkplain SuspendedCallback callback} on the execution
 * thread.</li>
 *
 * <li>A suspended guest language execution thread resumes language execution only after the client
 * callback returns.</li>
 *
 * <li>Sessions that are no longer needed should be {@linkplain #close() closed}; a closed session
 * has no further affect on engine execution.</li>
 * </ul>
 * </p>
 *
 * <h4>Debugging requests</h4>
 * <p>
 * Session clients can manage guest language execution in several ways:
 * <ul>
 * <li>{@linkplain #install(Breakpoint) Install} a newly created {@link Breakpoint}.</li>
 *
 * <li>{@linkplain #suspendNextExecution() Request} suspension of the next execution on the first
 * thread that is encountered.</li>
 *
 * <li>Request a stepping action (e.g. {@linkplain SuspendedEvent#prepareStepInto(int) step into},
 * {@linkplain SuspendedEvent#prepareStepOver(int) step over},
 * {@linkplain SuspendedEvent#prepareKill() kill}) on a suspended execution thread, to take effect
 * after the client callback returns.</li>
 * </ul>
 * </p>
 *
 * <h4>Event merging</h4>
 * <p>
 * A session may suspend a guest language execution thread in response to more than one request from
 * its client. For example:
 * <ul>
 * <li>A stepping action may land where a breakpoint is installed.</li>
 * <li>Multiple installed breakpoints may apply to a particular location.</li>
 * </ul>
 * In such cases the client receives a single <em>merged</em> event. A call to
 * {@linkplain SuspendedEvent#getBreakpoints()} lists all breakpoints (possibly none) that apply to
 * the suspended event's location.</li>
 * </p>
 *
 * <h4>Multiple sessions</h4>
 * <p>
 * There can be multiple sessions associated with a single engine, which are independent of one
 * another in the following ways:
 * <ul>
 * <li>Breakpoints created by a session are not visible to clients of other sessions.</li>
 *
 * <li>A client receives no notification when guest language execution threads are suspended by
 * sessions other than its own.</li>
 *
 * <li>Events are <em>not merged</em> across sessions. For example, when a guest language execution
 * thread hits a location where two sessions have installed breakpoints, each session notifies its
 * client with a new {@link SuspendedEvent} instance.</li>
 * </ul>
 * Because all sessions can control engine execution, some interactions are inherently possible. For
 * example:
 * <ul>
 * <li>A session's client can {@linkplain SuspendedEvent#prepareKill() kill} an execution at just
 * about any time.</li>
 * <li>A session's client can <em>starve</em> execution by not returning from the synchronous
 * {@linkplain SuspendedCallback callback} on the guest language execution thread.</li>
 * </ul>
 * </p>
 * <p>
 * Usage example: {@link DebuggerSessionSnippets#example}
 *
 * @since 0.17
 */
/*
 * Javadoc for package-protected APIs:
 *
 * <li>{@link #suspend(Thread)} suspends the next or current execution on a particular thread.</li>
 * <li>{@link #suspendAll()} suspends the next or current execution on all threads.</li>
 */
public final class DebuggerSession implements Closeable {

    private static final AtomicInteger SESSIONS = new AtomicInteger(0);

    enum SteppingLocation {
        AFTER_CALL,
        BEFORE_STATEMENT
    }

    private final Debugger debugger;
    private final SuspendedCallback callback;
    private final Set<Breakpoint> breakpoints = Collections.synchronizedSet(new HashSet<Breakpoint>());
    private final Breakpoint alwaysHaltBreakpoint;

    private EventBinding<? extends ExecutionEventNodeFactory> callBinding;
    private EventBinding<? extends ExecutionEventNodeFactory> statementBinding;

    private final ConcurrentHashMap<Thread, SuspendedEvent> currentSuspendedEventMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, SteppingStrategy> strategyMap = new ConcurrentHashMap<>();
    private volatile boolean suspendNext;
    private boolean suspendAll;
    private final StableBoolean stepping = new StableBoolean(false);
    private final StableBoolean breakpointsActive = new StableBoolean(true);

    /*
     * Legacy mode for backwards compatibility. Legacy mode means that recursive events will be
     * dispatched and stepping bindings will be added and removed when they are needed. Since the
     * legacy session is always active we don't want to keep the stepping bindings active all the
     * time as we want for regular sessions, which can be closed.
     */
    // TODO remove when deprecated event dispatching is removed
    private final boolean legacy;
    private final int sessionId;

    private volatile boolean closed;

    DebuggerSession(Debugger debugger, SuspendedCallback callback, boolean legacy) {
        this.sessionId = SESSIONS.incrementAndGet();
        this.debugger = debugger;
        this.callback = callback;
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(DebuggerTags.AlwaysHalt.class).build();
        this.alwaysHaltBreakpoint = new Breakpoint(BreakpointLocation.ANY, filter, false);
        this.alwaysHaltBreakpoint.setEnabled(true);
        this.alwaysHaltBreakpoint.install(this);
        this.legacy = legacy;
        if (Debugger.TRACE) {
            trace("open with callback %s", callback);
        }
        if (!legacy) {
            addBindings();
        }
    }

    private void trace(String msg, Object... parameters) {
        Debugger.trace(this + ": " + msg, parameters);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.17
     */
    @Override
    public String toString() {
        return String.format("Session[id=%s]", sessionId);
    }

    /**
     * Returns the {@link Debugger debugger} instance that this session is associated with. Can be
     * used also after the session has already been closed.
     *
     * @since 0.17
     */
    public Debugger getDebugger() {
        return debugger;
    }

    /**
     * Suspends the next execution on the first thread that is encountered. After the first thread
     * was suspended no further executions are suspended unless {@link #suspendNextExecution()} is
     * called again. If multiple threads are executing at the same time then there are no guarantees
     * on which thread is going to be suspended. Will throw an {@link IllegalStateException} if the
     * session is already closed.
     *
     * @since 0.17
     */
    public synchronized void suspendNextExecution() {
        if (Debugger.TRACE) {
            trace("suspend next execution");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        suspendNext = true;
        updateStepping();
    }

    /**
     * Suspends the current or the next execution of a given thread. Will throw an
     * {@link IllegalStateException} if the session is already closed.
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    void suspend(Thread t) {
        if (Debugger.TRACE) {
            trace("suspend thread %s ", t);
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        setSteppingStrategy(t, SteppingStrategy.createAlwaysHalt(), true);
    }

    /**
     * Suspends the current or the next execution on all threads. All new executing threads will
     * start suspended until {@link #resumeAll()} is called or the session is closed. Will throw an
     * {@link IllegalStateException} if the session is already closed.
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    synchronized void suspendAll() {
        if (Debugger.TRACE) {
            trace("suspend all threads");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        suspendAll = true;
        // iterating concurrent hashmap should be save
        for (Thread t : strategyMap.keySet()) {
            SteppingStrategy s = strategyMap.get(t);
            assert s != null;
            if (s.isDone() || s.isConsumed()) {
                setSteppingStrategy(t, SteppingStrategy.createAlwaysHalt(), false);
            }
        }
        updateStepping();
    }

    /**
     * Resumes all suspended executions that have not yet been notified.
     *
     * @since 0.17
     */
    public synchronized void resumeAll() {
        if (Debugger.TRACE) {
            trace("resume all threads");
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        clearStrategies();
    }

    /**
     * Resumes the execution on a given thread if it has not been suspended yet.
     *
     * @param t the thread to resume
     */
    // TODO make part of public API as soon as PolyglotEngine is thread-safe
    synchronized void resume(Thread t) {
        if (Debugger.TRACE) {
            trace("resume threads", t);
        }
        if (closed) {
            throw new IllegalStateException("session closed");
        }

        setSteppingStrategy(t, SteppingStrategy.createContinue(), true);
    }

    private synchronized void setSteppingStrategy(Thread thread, SteppingStrategy strategy, boolean updateStepping) {
        if (closed) {
            return;
        }
        assert strategy != null;
        SteppingStrategy oldStrategy = this.strategyMap.put(thread, strategy);
        if (oldStrategy != strategy) {
            if (Debugger.TRACE) {
                trace("set stepping for thread: %s with strategy: %s", thread, strategy);
            }
            if (updateStepping) {
                updateStepping();
            }
        }
    }

    private synchronized void clearStrategies() {
        suspendAll = false;
        suspendNext = false;
        strategyMap.clear();
        updateStepping();
    }

    private SteppingStrategy getSteppingStrategy(Object value) {
        return strategyMap.get(value);
    }

    private void updateStepping() {
        assert Thread.holdsLock(this);

        boolean needsStepping = suspendNext || suspendAll;
        if (!needsStepping) {
            // iterating concurrent hashmap should be save
            for (Object t : strategyMap.keySet()) {
                SteppingStrategy s = strategyMap.get(t);
                assert s != null;
                if (!s.isDone()) {
                    needsStepping = true;
                    break;
                }
            }
        }

        stepping.set(needsStepping);

        if (legacy) {
            if (needsStepping) {
                addBindings();
            } else {
                removeBindings();
            }
        }
    }

    private void addBindings() {
        if (statementBinding == null) {
            Builder builder = SourceSectionFilter.newBuilder().tagIs(CallTag.class);
            this.callBinding = debugger.getInstrumenter().attachFactory(builder.build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return new CallSteppingNode(context);
                }
            });
            builder = SourceSectionFilter.newBuilder().tagIs(StatementTag.class);
            this.statementBinding = debugger.getInstrumenter().attachFactory(builder.build(), new ExecutionEventNodeFactory() {
                public ExecutionEventNode create(EventContext context) {
                    return new StatementSteppingNode(context);
                }
            });
        }
    }

    private void removeBindings() {
        if (statementBinding != null) {
            callBinding.dispose();
            statementBinding.dispose();
            callBinding = null;
            statementBinding = null;
            if (Debugger.TRACE) {
                trace("disabled stepping");
            }
        }
    }

    /**
     * Closes the current debugging session and disposes all installed breakpoints.
     *
     * @since 0.17
     */
    public synchronized void close() {
        if (Debugger.TRACE) {
            trace("close session");
        }
        if (closed) {
            throw new IllegalStateException("session already closed");
        }

        clearStrategies();
        removeBindings();
        for (Breakpoint breakpoint : getBreakpoints()) {
            breakpoint.dispose();
        }
        alwaysHaltBreakpoint.dispose();
        currentSuspendedEventMap.clear();
        closed = true;
    }

    /**
     * Returns all breakpoints in the order they were installed. {@link Breakpoint#dispose()
     * Disposed} breakpoints are automatically removed from this list.
     *
     * @since 0.17
     */
    public List<Breakpoint> getBreakpoints() {
        if (closed) {
            throw new IllegalStateException("session already closed");
        }

        List<Breakpoint> b;
        synchronized (this.breakpoints) {
            // need to synchronize manually breakpoints are iterated which is not
            // synchronized by default.
            b = new ArrayList<>(this.breakpoints);
        }
        return Collections.unmodifiableList(b);
    }

    /**
     * Set whether breakpoints are active in this session. This has no effect on breakpoints
     * enabled/disabled state. Breakpoints need to be active to actually break the execution. The
     * breakpoints are active by default.
     *
     * @param active <code>true</code> to make all breakpoints active, <code>false</code> to make
     *            all breakpoints inactive.
     * @since 0.24
     */
    public void setBreakpointsActive(boolean active) {
        breakpointsActive.set(active);
    }

    /**
     * Test whether breakpoints are active in this session. Breakpoints do not break execution when
     * not active.
     *
     * @since 0.24
     */
    public boolean isBreakpointsActive() {
        return breakpointsActive.get();
    }

    /*
     * Deprecation Note: Usually you want to return unmodifiable collections instead of mutable
     * collections in APIs. Also sorting does not really make sense, in the new session based API
     * installation order is a lot simpler and the client can use its own breakpoint sorting if they
     * want.
     *
     * TODO remove this when deprecated APIs are removed.
     */
    Collection<Breakpoint> getLegacyBreakpoints() {
        if (closed) {
            throw new IllegalStateException("session already closed");
        }

        List<Breakpoint> sortedBreakpoints;
        synchronized (breakpoints) {
            // need to synchronize manually breakpoints are iterated which is not
            // synchronized by default.
            sortedBreakpoints = new ArrayList<>(this.breakpoints);
        }
        Collections.sort(sortedBreakpoints, Breakpoint.COMPARATOR);

        // unfortunately spec says that we need to return a modifiable list
        // should we deprecate that?
        return sortedBreakpoints;
    }

    /**
     * Adds a new breakpoint to this session and makes it capable of suspending execution.
     * <p>
     * The breakpoint suspends execution by making a {@link SuspendedCallback callback} to this
     * session, together with an event description that includes
     * {@linkplain SuspendedEvent#getBreakpoints() which breakpoint(s)} were hit.
     *
     * @param breakpoint a new breakpoint
     * @return the installed breakpoint
     * @throws IllegalStateException if the session has been closed
     *
     * @since 0.17
     */
    public synchronized Breakpoint install(Breakpoint breakpoint) {
        if (closed) {
            throw new IllegalStateException("Debugger session is already closed. Cannot install new breakpoints.");
        }
        if (breakpoint.isDisposed()) {
            throw new IllegalArgumentException("Cannot install breakpoint, it is already disposed.");
        }
        if (breakpoint.getSession() != null) {
            throw new IllegalArgumentException("Cannot install breakpoint, it is already installed in different debugger session.");
        }
        breakpoint.install(this);
        this.breakpoints.add(breakpoint);
        breakpoint.setEnabled(true);
        if (Debugger.TRACE) {
            trace("installed breakpoint %s", breakpoint);
        }
        return breakpoint;
    }

    synchronized void disposeBreakpoint(Breakpoint breakpoint) {
        breakpoints.remove(breakpoint);
        debugger.disposeBreakpoint(breakpoint);
        if (Debugger.TRACE) {
            trace("disposed breakpoint %s", breakpoint);
        }
    }

    @TruffleBoundary
    void notifyCallback(DebuggerNode source, MaterializedFrame frame, Object returnValue, BreakpointConditionFailure conditionFailure) {
        Thread currentThread = Thread.currentThread();
        SuspendedEvent event = currentSuspendedEventMap.get(currentThread);
        if (!legacy && event != null) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: recursive from source:%s context:%s location:%s", source, source.getContext(), source.getSteppingLocation());
            }
            // avoid recursive suspensions in non legacy mode.
            return;
        }

        if (source.consumeIsDuplicate()) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: duplicate from source:%s context:%s location:%s", source, source.getContext(), source.getSteppingLocation());
            }
            return;
        }

        // only the first DebuggerNode for a source location and thread will reach here.

        // mark all other nodes at this source location as duplicates
        List<DebuggerNode> nodes = collectDebuggerNodes(source);
        for (DebuggerNode node : nodes) {
            if (node == source) {
                // for the current one we won't call isDuplicate
                continue;
            }
            node.markAsDuplicate();
        }

        SteppingStrategy s = getSteppingStrategy(currentThread);
        if (suspendNext) {
            synchronized (this) {
                // double checked locking to avoid more than one suspension
                if (suspendNext) {
                    s = SteppingStrategy.createAlwaysHalt();
                    setSteppingStrategy(currentThread, s, true);
                    suspendNext = false;
                }
            }
        }

        if (s == null) {
            // a new Thread just appeared
            s = notifyNewThread(currentThread);
        }

        Map<Breakpoint, Throwable> breakpointFailures = null;
        if (conditionFailure != null) {
            breakpointFailures = new HashMap<>();
            breakpointFailures.put(conditionFailure.getBreakpoint(), conditionFailure.getConditionFailure());
        }

        List<Breakpoint> breaks = null;
        for (DebuggerNode node : nodes) {
            Breakpoint breakpoint = node.getBreakpoint();
            if (breakpoint == null || !isBreakpointsActive()) {
                continue; // not a breakpoint node
            }
            boolean hit = true;
            BreakpointConditionFailure failure = null;
            try {
                hit = breakpoint.notifyIndirectHit(source, node, frame);
            } catch (BreakpointConditionFailure e) {
                failure = e;
            }
            if (hit) {
                if (breaks == null) {
                    breaks = new ArrayList<>();
                }
                breaks.add(breakpoint);
            }
            if (failure != null) {
                if (breakpointFailures == null) {
                    breakpointFailures = new HashMap<>();
                }
                breakpointFailures.put(failure.getBreakpoint(), failure.getConditionFailure());
            }
        }

        boolean hitStepping = s.step(this, source.getContext(), source.getSteppingLocation());
        boolean hitBreakpoint = breaks != null && !breaks.isEmpty();
        if (hitStepping || hitBreakpoint) {
            s.consume();
            doSuspend(source, frame, returnValue, breaks, breakpointFailures);
        } else {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: strategy(%s) from source:%s context:%s location:%s", s, source, source.getContext(), source.getSteppingLocation());
            }
        }
    }

    private void doSuspend(DebuggerNode source, MaterializedFrame frame, Object returnValue, List<Breakpoint> breaks, Map<Breakpoint, Throwable> conditionFailures) {
        CompilerAsserts.neverPartOfCompilation();
        Thread currentThread = Thread.currentThread();

        SuspendedEvent suspendedEvent;
        try {
            suspendedEvent = new SuspendedEvent(this, currentThread, source.getContext(), frame, source.getSteppingLocation(), returnValue, breaks, conditionFailures);
            currentSuspendedEventMap.put(currentThread, suspendedEvent);
            try {
                callback.onSuspend(suspendedEvent);
            } finally {
                currentSuspendedEventMap.remove(currentThread);
                /*
                 * In case the debug client did not behave and did store the suspended event.
                 */
                suspendedEvent.clearLeakingReferences();
            }
        } catch (Throwable t) {
            // let the instrumentation handle this
            throw t;
        }

        if (closed) {
            // session got closed in the meantime
            return;
        }

        SteppingStrategy strategy = suspendedEvent.getNextStrategy();
        if (!legacy && !strategy.isKill()) {
            // suspend(...) has been called during SuspendedEvent notification. this is only
            // possible in non-legacy mode.
            SteppingStrategy currentStrategy = getSteppingStrategy(currentThread);
            if (currentStrategy != null && !currentStrategy.isConsumed()) {
                strategy = currentStrategy;
            }
        }
        strategy.initialize();

        if (Debugger.TRACE) {
            trace("end suspend with strategy %s at %s location %s", strategy, source.getContext(), source.getSteppingLocation());
        }

        setSteppingStrategy(currentThread, strategy, true);
        if (strategy.isKill()) {
            throw new KillException();
        }
    }

    private List<DebuggerNode> collectDebuggerNodes(DebuggerNode source) {
        List<DebuggerNode> nodes = new ArrayList<>();
        if (source.getSteppingLocation() == SteppingLocation.BEFORE_STATEMENT) {
            EventContext context = source.getContext();

            if (stepping.get()) {
                EventBinding<? extends ExecutionEventNodeFactory> localStatementBinding = statementBinding;
                if (localStatementBinding != null) {
                    nodes.add((DebuggerNode) context.lookupExecutionEventNode(localStatementBinding));
                }
            }
            if (!breakpoints.isEmpty()) {
                synchronized (breakpoints) {
                    for (Breakpoint b : breakpoints) {
                        DebuggerNode node = b.lookupNode(context);
                        if (node != null) {
                            nodes.add(node);
                        }
                    }
                }
            }
            DebuggerNode node = alwaysHaltBreakpoint.lookupNode(context);
            if (node != null) {
                nodes.add(node);
            }
        } else {
            assert source.getSteppingLocation() == SteppingLocation.AFTER_CALL;
            // there is only one binding that can lead to a after event
            if (stepping.get()) {
                assert source.getContext().lookupExecutionEventNode(callBinding) == source;
                nodes.add(source);
            }
        }
        return nodes;
    }

    private synchronized SteppingStrategy notifyNewThread(Thread currentThread) {
        SteppingStrategy s = getSteppingStrategy(currentThread);
        // double checked locking
        if (s == null) {
            if (suspendAll) {
                // all suspended
                s = SteppingStrategy.createAlwaysHalt();
            } else {
                // not suspended continue execution for this thread
                s = SteppingStrategy.createContinue();
            }
            setSteppingStrategy(currentThread, s, true);
        }
        assert s != null;
        return s;

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
    static Object evalInContext(SuspendedEvent ev, String code, FrameInstance frameInstance) throws IOException {
        try {
            Node node;
            MaterializedFrame frame;
            if (frameInstance == null) {
                node = ev.getContext().getInstrumentedNode();
                frame = ev.getMaterializedFrame();
            } else {
                node = frameInstance.getCallNode();
                frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
            }
            return Debugger.ACCESSOR.evalInContext(ev.getSession().getDebugger().getSourceVM(), node, frame, code);
        } catch (KillException kex) {
            throw new IOException("Evaluation was killed.", kex);
        }
    }

    private final class StatementSteppingNode extends DebuggerNode {

        StatementSteppingNode(EventContext context) {
            super(context);
        }

        @Override
        EventBinding<?> getBinding() {
            return statementBinding;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (stepping.get()) {
                notifyCallback(this, frame.materialize(), null, null);
            }
        }

        @Override
        SteppingLocation getSteppingLocation() {
            return SteppingLocation.BEFORE_STATEMENT;
        }
    }

    private final class CallSteppingNode extends DebuggerNode {

        CallSteppingNode(EventContext context) {
            super(context);
        }

        @Override
        EventBinding<?> getBinding() {
            return callBinding;
        }

        @Override
        public void onReturnValue(VirtualFrame frame, Object result) {
            if (stepping.get()) {
                notifyCallback(this, frame.materialize(), result, null);
            }
        }

        @Override
        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (stepping.get()) {
                notifyCallback(this, frame.materialize(), null, null);
            }
        }

        @Override
        SteppingLocation getSteppingLocation() {
            return SteppingLocation.AFTER_CALL;
        }

    }

    /**
     * Helper class that uses an assumption to switch between stepping mode and non-stepping mode
     * efficiently.
     */
    private static final class StableBoolean {

        @CompilationFinal private volatile Assumption unchanged;
        @CompilationFinal private volatile boolean value;

        StableBoolean(boolean initialValue) {
            this.value = initialValue;
            this.unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
        }

        boolean get() {
            if (unchanged.isValid()) {
                return value;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return value;
            }
        }

        void set(boolean value) {
            if (this.value != value) {
                this.value = value;
                Assumption old = this.unchanged;
                unchanged = Truffle.getRuntime().createAssumption("Unchanged boolean");
                old.invalidate();
            }
        }

    }
}

class DebuggerSessionSnippets {

    public void example() {
        // @formatter:off
        // BEGIN: DebuggerSessionSnippets#example
        PolyglotEngine engine = PolyglotEngine.newBuilder().build();

        try (DebuggerSession session = Debugger.find(engine).
                        startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                // step into the next event
                event.prepareStepInto(1);
            }
        })) {
            Source someCode = Source.newBuilder("...").
                            mimeType("...").
                            name("example").build();

            // install line breakpoint
            session.install(Breakpoint.newBuilder(someCode).lineIs(3).build());

            // should print suspended at for each debugger step.
            engine.eval(someCode);
        }

        // END: DebuggerSessionSnippets#example
        // @formatter:on
    }
}
