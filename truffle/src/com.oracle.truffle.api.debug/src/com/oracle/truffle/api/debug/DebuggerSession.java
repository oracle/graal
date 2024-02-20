/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.net.URI;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.debug.Breakpoint.BreakpointConditionFailure;
import com.oracle.truffle.api.debug.DebuggerNode.InputValuesProvider;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Represents a single debugging session of a Debugger.
 *
 * <h4>Session lifetime</h4>
 * <p>
 * <ul>
 * <li>A debugging client {@linkplain Debugger#startSession(SuspendedCallback) requests} a new
 * {@linkplain DebuggerSession session} from the {@linkplain Debugger Debugger}.</li>
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
    private static final ThreadLocal<Boolean> inEvalInContext = new ThreadLocal<>();

    static final Set<SuspendAnchor> ANCHOR_SET_BEFORE = Collections.singleton(SuspendAnchor.BEFORE);
    static final Set<SuspendAnchor> ANCHOR_SET_AFTER = Collections.singleton(SuspendAnchor.AFTER);
    static final Set<SuspendAnchor> ANCHOR_SET_ALL = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(SuspendAnchor.BEFORE, SuspendAnchor.AFTER)));

    private final Debugger debugger;
    private final SuspendedCallback callback;
    private final Set<SourceElement> sourceElements;
    private final boolean hasExpressionElement;
    private final boolean hasRootElement;
    private final List<Breakpoint> breakpoints = Collections.synchronizedList(new ArrayList<>());

    private EventBinding<? extends ExecutionEventNodeFactory> syntaxElementsBinding;
    final Set<EventBinding<? extends ExecutionEventNodeFactory>> allBindings = Collections.synchronizedSet(new HashSet<>());

    private final ConcurrentHashMap<Thread, SuspendedEvent> currentSuspendedEventMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Thread, SteppingStrategy> strategyMap = new ConcurrentHashMap<>();
    private volatile boolean suspendNext;
    private volatile boolean suspendAll;
    private final StableBoolean stepping = new StableBoolean(false);
    private final StableBoolean ignoreLanguageContextInitialization = new StableBoolean(false);
    private volatile boolean includeInternal = false;
    private volatile boolean showHostStackFrames = false;
    private Predicate<Source> sourceFilter;
    @CompilationFinal private volatile Assumption suspensionFilterUnchanged = Truffle.getRuntime().createAssumption("Unchanged suspension filter");
    private final StableBoolean alwaysHaltBreakpointsActive = new StableBoolean(true);
    private final StableBoolean locationBreakpointsActive = new StableBoolean(true);
    private final StableBoolean exceptionBreakpointsActive = new StableBoolean(true);
    private final DebuggerExecutionLifecycle executionLifecycle;
    final ThreadLocal<ThreadSuspension> threadSuspensions = new ThreadLocal<>();
    private final DebugSourcesResolver sources;
    private final ThreadLocal<Set<Integer>> steppingEnabledSlots = new ThreadLocal<>();

    private final int sessionId;

    private volatile boolean closed;

    DebuggerSession(Debugger debugger, SuspendedCallback callback, SourceElement... sourceElements) {
        this.sessionId = SESSIONS.incrementAndGet();
        this.debugger = debugger;
        this.callback = callback;
        switch (sourceElements.length) {
            case 0:
                this.sourceElements = Collections.emptySet();
                break;
            case 1:
                this.sourceElements = Collections.singleton(sourceElements[0]);
                break;
            default:
                this.sourceElements = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(sourceElements)));
                break;
        }
        this.hasExpressionElement = this.sourceElements.contains(SourceElement.EXPRESSION);
        this.hasRootElement = this.sourceElements.contains(SourceElement.ROOT);
        if (Debugger.TRACE) {
            trace("open with callback %s", callback);
        }
        sources = new DebugSourcesResolver(debugger.getEnv());
        addBindings(includeInternal, sourceFilter);
        executionLifecycle = new DebuggerExecutionLifecycle(this);
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
     * Returns a language top scope. The top scopes have global validity and unlike
     * {@link DebugStackFrame#getScope()} have no relation to the suspended location.
     *
     * @throws DebugException when guest language code throws an exception
     * @since 0.30
     */
    public DebugScope getTopScope(String languageId) throws DebugException {
        LanguageInfo info = debugger.getEnv().getLanguages().get(languageId);
        if (info == null) {
            return null;
        }
        try {
            Object scope = debugger.getEnv().getScope(info);
            if (scope == null) {
                return null;
            }
            return new DebugScope(scope, this, info);
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable ex) {
            throw DebugException.create(this, ex, info);
        }
    }

    /**
     * Returns a polyglot scope - symbols explicitly exported by languages.
     *
     * @since 0.30
     */
    public Map<String, ? extends DebugValue> getExportedSymbols() {
        return new AbstractMap<>() {
            private final DebugValue polyglotBindings = new DebugValue.HeapValue(DebuggerSession.this, "polyglot", debugger.getEnv().getPolyglotBindings());

            @Override
            public Set<Map.Entry<String, DebugValue>> entrySet() {
                Set<Map.Entry<String, DebugValue>> entries = new LinkedHashSet<>();
                for (DebugValue property : polyglotBindings.getProperties()) {
                    entries.add(new SimpleImmutableEntry<>(property.getName(), property));
                }
                return Collections.unmodifiableSet(entries);
            }

            @Override
            public DebugValue get(Object key) {
                if (!(key instanceof String)) {
                    return null;
                }
                String name = (String) key;
                return polyglotBindings.getProperty(name);
            }
        };
    }

    /**
     * Set to provide host information in stack traces. When <code>true</code>,
     * {@link DebugStackFrame#isHost() host frames} and {@link DebugStackTraceElement#isHost() host
     * trace elements} are provided, when available.
     *
     * @since 20.3
     * @see DebugStackFrame#isHost()
     * @see DebugStackTraceElement#isHost()
     */
    public void setShowHostStackFrames(boolean showHostStackFrames) {
        this.showHostStackFrames = showHostStackFrames;
    }

    boolean isShowHostStackFrames() {
        return showHostStackFrames;
    }

    /**
     * Set a stepping suspension filter. Prepared steps skip code that does not match this filter.
     *
     * @since 0.26
     */
    public void setSteppingFilter(SuspensionFilter steppingFilter) {
        this.ignoreLanguageContextInitialization.set(steppingFilter.isIgnoreLanguageContextInitialization());
        synchronized (this) {
            boolean oldIncludeInternal = this.includeInternal;
            this.includeInternal = steppingFilter.isInternalIncluded();
            Predicate<Source> oldSourceFilter = this.sourceFilter;
            this.sourceFilter = steppingFilter.getSourcePredicate();
            this.suspensionFilterUnchanged.invalidate();
            this.suspensionFilterUnchanged = Truffle.getRuntime().createAssumption("Unchanged suspension filter");
            if (oldIncludeInternal != this.includeInternal || oldSourceFilter != this.sourceFilter) {
                removeBindings();
                addBindings(this.includeInternal, this.sourceFilter);
            }
        }
    }

    boolean isIncludeInternal() {
        return includeInternal;
    }

    boolean isSourceFilteredOut(Source source) {
        Predicate<Source> filter = sourceFilter;
        if (filter != null) {
            return !filter.test(source);
        } else {
            return false;
        }
    }

    Assumption getSuspensionFilterUnchangedAssumption() {
        return suspensionFilterUnchanged;
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
     * Suspend immediately at the current location of the current execution thread. A {@link Node}
     * argument can be provided as an exact current location, if known. This method can be called on
     * the guest execution thread only.
     * <p>
     * This method calls {@link SuspendedCallback#onSuspend(SuspendedEvent)} synchronously, with
     * {@link SuspendedEvent} created at the actual location of the current thread. This method can
     * not be called from an existing {@link SuspendedCallback#onSuspend(SuspendedEvent) callback}.
     *
     * @param node the top Node of the execution, or <code>null</code>
     * @return <code>true</code> when there is a guest code execution on the current thread and
     *         {@link SuspendedCallback} was called, <code>false</code> otherwise.
     * @throws IllegalStateException when the current thread is suspended already
     * @throws IllegalArgumentException when a node with no {@link RootNode} is provided, or it's
     *             root node does not match the current execution root, or the node does not match
     *             the current call node, if known.
     * @since 20.3
     */
    public boolean suspendHere(Node node) {
        SuspendedEvent event = currentSuspendedEventMap.get(Thread.currentThread());
        if (event != null) {
            throw new IllegalStateException("Suspended already");
        }
        RootNode nodeRoot;
        if (node != null) {
            nodeRoot = node.getRootNode();
            if (nodeRoot == null) {
                throw new IllegalArgumentException(String.format("The node %s does not have a root.", node));
            }
        } else {
            nodeRoot = null;
        }

        SuspendContextAndFrame result = Truffle.getRuntime().iterateFrames((frameInstance) -> {
            RootNode root = ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
            if (!includeInternal) {
                if (root.isInternal()) {
                    return null;
                }
            }
            if (nodeRoot != null && nodeRoot != root) {
                throw new IllegalArgumentException(String.format("The node %s belongs to a root %s, which is different from the current root %s.", node, nodeRoot, root));
            }
            Node callNode = frameInstance.getCallNode();
            if (callNode == null) {
                callNode = node;
                if (callNode == null) {
                    // We have no idea where in the function we are.
                    callNode = root;
                }
            }
            if (node != null && node != callNode) {
                throw new IllegalArgumentException(String.format("The node %s does not match the current known call node %s.", node, callNode));
            }
            Node icallNode = InstrumentableNode.findInstrumentableParent(callNode);
            if (icallNode != null) {
                callNode = icallNode;
            }
            MaterializedFrame frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
            SuspendedContext context = SuspendedContext.create(callNode, null);
            return new SuspendContextAndFrame(context, frame);
        });
        if (result == null) {
            return false;
        }
        doSuspend(result.context, SuspendAnchor.BEFORE, result.frame, null);
        return true;
    }

    // Session-specific stepping control.
    void restoreSteppingOnCurrentThread() {
        CompilerAsserts.neverPartOfCompilation();
        assert debugger.getEnv().getEnteredContext() != null : "Need to be called on a context thread";
        int count = debugger.getSteppingDisabledCount();
        if (count == 0) {
            // There is nothing to restore
            return;
        }
        Set<Integer> enabledSlots = steppingEnabledSlots.get();
        if (enabledSlots == null) {
            enabledSlots = new HashSet<>();
            steppingEnabledSlots.set(enabledSlots);
        }
        enabledSlots.add(count);
    }

    // Session-specific stepping control, delegates to Debugger.getSteppingDisabledCount().
    boolean isSteppingEnabledOnCurrentThread() {
        CompilerAsserts.neverPartOfCompilation();
        assert debugger.getEnv().getEnteredContext() != null : "Need to be called on a context thread";
        int count = debugger.getSteppingDisabledCount();
        if (count == 0) {
            return true;
        }
        Set<Integer> enabledSlots = steppingEnabledSlots.get();
        return enabledSlots != null && enabledSlots.contains(count);
    }

    // Clear session-specific stepping control
    void clearDisabledSteppingOnCurrentThread(int count) {
        CompilerAsserts.neverPartOfCompilation();
        assert debugger.getEnv().getEnteredContext() != null : "Need to be called on a context thread";
        assert count > 0 : "Wrong count = " + count;
        Set<Integer> enabledSlots = steppingEnabledSlots.get();
        if (enabledSlots != null) {
            enabledSlots.remove(count);
            if (enabledSlots.isEmpty()) {
                steppingEnabledSlots.remove();
            }
        }
    }

    static final class SuspendContextAndFrame {

        final SuspendedContext context;
        final MaterializedFrame frame;

        SuspendContextAndFrame(SuspendedContext context, MaterializedFrame frame) {
            this.context = context;
            this.frame = frame;
        }
    }

    /**
     * Suspends the current or the next execution of a given thread. Will throw an
     * {@link IllegalStateException} if the session is already closed.
     *
     * @since 20.0
     */
    public void suspend(Thread t) {
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
     *
     * @since 20.0
     */
    public synchronized void suspendAll() {
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
     *
     * @since 20.0
     */
    public synchronized void resume(Thread t) {
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

    private SteppingStrategy getSteppingStrategy(Thread value) {
        return strategyMap.get(value);
    }

    private void updateStepping() {
        assert Thread.holdsLock(this);

        boolean needsStepping = suspendNext || suspendAll;
        if (!needsStepping) {
            // iterating concurrent hashmap should be save
            for (Thread t : strategyMap.keySet()) {
                SteppingStrategy s = strategyMap.get(t);
                assert s != null;
                if (!s.isDone()) {
                    needsStepping = true;
                    break;
                }
            }
        }

        stepping.set(needsStepping);
    }

    @TruffleBoundary
    void setThreadSuspendEnabled(boolean enabled) {
        if (!enabled) {
            // temporarily disable suspensions in the given thread
            threadSuspensions.set(ThreadSuspension.DISABLED);
        } else {
            threadSuspensions.remove();
        }
    }

    private void addBindings(boolean includeInternalCode, Predicate<Source> sFilter) {
        if (syntaxElementsBinding == null) {
            if (!sourceElements.isEmpty()) {
                Class<?>[] syntaxTags = new Class<?>[this.sourceElements.size() + (hasRootElement ? 0 : 1)];
                int i = 0;
                for (SourceElement element : this.sourceElements) {
                    syntaxTags[i++] = element.getTag();
                }
                assert i == sourceElements.size();
                if (!hasRootElement) {
                    syntaxTags[i] = RootTag.class;
                }
                this.syntaxElementsBinding = createBinding(includeInternalCode, sFilter, new ExecutionEventNodeFactory() {
                    @Override
                    public ExecutionEventNode create(EventContext context) {
                        if (context.hasTag(RootTag.class)) {
                            return new RootSteppingDepthNode(context);
                        } else {
                            return new SteppingNode(context);
                        }
                    }
                }, hasExpressionElement, syntaxTags);
                allBindings.add(syntaxElementsBinding);
            }
        }
    }

    private EventBinding<? extends ExecutionEventNodeFactory> createBinding(boolean includeInternalCode, Predicate<Source> sFilter, ExecutionEventNodeFactory factory, boolean onInput,
                    Class<?>... tags) {
        Builder builder = SourceSectionFilter.newBuilder().tagIs(tags);
        builder.includeInternal(includeInternalCode);
        if (sFilter != null) {
            builder.sourceIs(new SourceSectionFilter.SourcePredicate() {
                @Override
                public boolean test(Source source) {
                    return sFilter.test(source);
                }
            });
        }
        SourceSectionFilter ssf = builder.build();
        if (onInput) {
            return debugger.getInstrumenter().attachExecutionEventFactory(ssf, ssf, factory);
        } else {
            return debugger.getInstrumenter().attachExecutionEventFactory(ssf, factory);
        }
    }

    private void removeBindings() {
        assert Thread.holdsLock(this);
        if (syntaxElementsBinding != null) {
            allBindings.remove(syntaxElementsBinding);
            syntaxElementsBinding.dispose();
            syntaxElementsBinding = null;
            if (Debugger.TRACE) {
                trace("disabled stepping");
            }
        }
    }

    Set<SourceElement> getSourceElements() {
        return sourceElements;
    }

    /**
     * Creates a {@link DebugValue} object that wraps a primitive value. Strings and boxed Java
     * primitive types are considered primitive. Throws {@link IllegalArgumentException} if the
     * value is not primitive.
     *
     * @param primitiveValue a primitive value
     * @param language guest language this value is value is associated with. Some value attributes
     *            depend on the language, like {@link DebugValue#getMetaObject()}. Can be
     *            <code>null</code>.
     * @return a {@link DebugValue} that wraps the primitive value.
     * @throws IllegalArgumentException if the value is not a boxed Java primitive or a String.
     * @since 21.2
     */
    public DebugValue createPrimitiveValue(Object primitiveValue, LanguageInfo language) throws IllegalArgumentException {
        DebugValue.checkPrimitive(primitiveValue);
        return new DebugValue.HeapValue(this, language, null, primitiveValue);
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
        for (Breakpoint breakpoint : this.breakpoints) {
            breakpoint.sessionClosed(this);
        }
        currentSuspendedEventMap.clear();
        allBindings.clear();
        debugger.disposedSession(this);
        closed = true;
    }

    /**
     * Returns all breakpoints {@link #install(com.oracle.truffle.api.debug.Breakpoint) installed}
     * in this session, in the install order. The returned list contains a current snapshot of
     * breakpoints, those that were {@link Breakpoint#dispose() disposed}, or
     * {@link Debugger#install(com.oracle.truffle.api.debug.Breakpoint) installed on Debugger} are
     * not included.
     *
     * @since 0.17
     * @see Debugger#getBreakpoints()
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

    void visitBreakpoints(Consumer<Breakpoint> consumer) {
        synchronized (this.breakpoints) {
            for (Breakpoint b : this.breakpoints) {
                consumer.accept(b);
            }
        }
    }

    /**
     * Set whether breakpoints are active in this session. This has no effect on breakpoints
     * enabled/disabled state. Breakpoints need to be active to actually break the execution. The
     * breakpoints are active by default.
     *
     * @param active <code>true</code> to make all breakpoints active, <code>false</code> to make
     *            all breakpoints inactive.
     * @since 0.24
     * @deprecated Use {@link #setBreakpointsActive(Breakpoint.Kind, boolean)} instead.
     */
    @Deprecated(since = "19.0")
    public void setBreakpointsActive(boolean active) {
        for (Breakpoint.Kind kind : Breakpoint.Kind.VALUES) {
            setBreakpointsActive(kind, active);
        }
    }

    /**
     * Set whether breakpoints of the given kind are active in this session. This has no effect on
     * breakpoints enabled/disabled state. Breakpoints need to be active to actually break the
     * execution. The breakpoints are active by default.
     *
     * @param breakpointKind the kind of breakpoints to activate/deactivate
     * @param active <code>true</code> to make breakpoints active, <code>false</code> to make
     *            breakpoints inactive.
     * @since 19.0
     */
    public void setBreakpointsActive(Breakpoint.Kind breakpointKind, boolean active) {
        switch (breakpointKind) {
            case SOURCE_LOCATION:
                locationBreakpointsActive.set(active);
                break;
            case EXCEPTION:
                exceptionBreakpointsActive.set(active);
                break;
            case HALT_INSTRUCTION:
                alwaysHaltBreakpointsActive.set(active);
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Unhandled breakpoint kind: " + breakpointKind);
        }
    }

    /**
     * Test whether breakpoints are active in this session. Breakpoints do not break execution when
     * not active.
     *
     * @since 0.24
     * @deprecated Use {@link #isBreakpointsActive(Breakpoint.Kind)} instead.
     */
    @Deprecated(since = "19.0")
    public boolean isBreakpointsActive() {
        for (Breakpoint.Kind kind : Breakpoint.Kind.VALUES) {
            if (isBreakpointsActive(kind)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Test whether breakpoints of the given kind are active in this session. Breakpoints do not
     * break execution when not active.
     *
     * @param breakpointKind the kind of breakpoints to test
     * @since 19.0
     */
    public boolean isBreakpointsActive(Breakpoint.Kind breakpointKind) {
        switch (breakpointKind) {
            case SOURCE_LOCATION:
                return locationBreakpointsActive.get();
            case EXCEPTION:
                return exceptionBreakpointsActive.get();
            case HALT_INSTRUCTION:
                return alwaysHaltBreakpointsActive.get();
            default:
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Unhandled breakpoint kind: " + breakpointKind);
        }
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
        install(breakpoint, false);
        return breakpoint;
    }

    synchronized void install(Breakpoint breakpoint, boolean global) {
        if (closed) {
            if (!global) {
                throw new IllegalStateException("Debugger session is already closed. Cannot install new breakpoints.");
            } else {
                return;
            }
        }
        if (!breakpoint.install(this, !global)) {
            return;
        }
        if (!global) { // Do not keep global breakpoints in the list
            this.breakpoints.add(breakpoint);
        }
        if (Debugger.TRACE) {
            trace("installed session breakpoint %s", breakpoint);
        }
    }

    synchronized void disposeBreakpoint(Breakpoint breakpoint) {
        breakpoints.remove(breakpoint);
        if (Debugger.TRACE) {
            trace("disposed session breakpoint %s", breakpoint);
        }
    }

    /**
     * Request for languages to provide stack frames of scheduled asynchronous execution. Languages
     * might not provide asynchronous stack frames by default for performance reasons. At most
     * <code>depth</code> asynchronous stack frames are asked for. When multiple debugger sessions
     * or other instruments call this method, the languages get a maximum depth of these calls and
     * may therefore provide longer asynchronous stacks than requested. Also, languages may provide
     * asynchronous stacks if it's of no performance penalty, or if requested by other options.
     * <p/>
     * Asynchronous stacks can then be accessed via {@link SuspendedEvent#getAsynchronousStacks()},
     * or {@link DebugException#getDebugAsynchronousStacks()}.
     *
     * @param depth the requested stack depth, 0 means no asynchronous stack frames are required.
     * @see SuspendedEvent#getAsynchronousStacks()
     * @see DebugException#getDebugAsynchronousStacks()
     * @since 20.1.0
     */
    public void setAsynchronousStackDepth(int depth) {
        debugger.getEnv().setAsynchronousStackDepth(depth);
    }

    /**
     * Set a {@link DebugContextsListener listener} to be notified about changes in contexts in
     * guest language application. One listener can be set at a time, call with <code>null</code> to
     * remove the current listener.
     *
     * @param listener a listener to receive the context events, or <code>null</code> to reset it
     * @param includeActiveContexts whether or not this listener should be notified for present
     *            active contexts
     * @since 0.30
     */
    public void setContextsListener(DebugContextsListener listener, boolean includeActiveContexts) {
        executionLifecycle.setContextsListener(listener, includeActiveContexts);
    }

    /**
     * Set a {@link DebugThreadsListener listener} to be notified about changes in threads in guest
     * language application. One listener can be set at a time, call with <code>null</code> to
     * remove the current listener.
     *
     * @param listener a listener to receive the context events
     * @param includeInitializedThreads whether or not this listener should be notified for present
     *            initialized threads
     * @since 0.30
     */
    public void setThreadsListener(DebugThreadsListener listener, boolean includeInitializedThreads) {
        executionLifecycle.setThreadsListener(listener, includeInitializedThreads);
    }

    /**
     * Set a list of source path roots that are used to resolve relative {@link Source#getURI()
     * source URIs}. All debugger methods that provide {@link Source} object, resolve relative
     * sources with respect to this source-path. When the resolution does not succeed (the relative
     * path does not exist under any of the supplied source-path elements), the original relative
     * {@link Source} is provided.
     *
     * @param uris a list of absolute URIs
     * @throws IllegalArgumentException when an URI is not absolute
     * @see #resolveSource(Source)
     * @since 19.0
     */
    public void setSourcePath(Iterable<URI> uris) {
        sources.setSourcePath(uris);
    }

    /**
     * Resolve the source with respect to the actual {@link #setSourcePath(Iterable) source path}.
     * Sources with relative {@link Source#getURI() URI} are subject to resolution to an existing
     * absolute location. The first source-path URI that resolves to an existing location is used.
     *
     * @param source the source to resolve
     * @return the provided source if no resolution is necessary, or the resolved source, or
     *         <code>null</code> when it's not possible to resolve the provided source
     * @since 19.0
     */
    public Source resolveSource(Source source) {
        return sources.resolve(source);
    }

    /**
     * Resolve the {@link SourceSection}, or return the original when resolution is not possible.
     */
    SourceSection resolveSection(SourceSection section) {
        return sources.resolve(section);
    }

    SourceSection resolveSection(Node node) {
        return sources.resolve(DebugSourcesResolver.findEncapsulatedSourceSection(node));
    }

    @TruffleBoundary
    Object notifyCallback(EventContext context, DebuggerNode source, MaterializedFrame frame, SuspendAnchor suspendAnchor,
                    InputValuesProvider inputValuesProvider, Object returnValue, DebugException exception,
                    BreakpointConditionFailure conditionFailure) {
        ThreadSuspension suspensionDisabled = threadSuspensions.get();
        if (suspensionDisabled != null && !suspensionDisabled.enabled) {
            return returnValue;
        }
        // SuspensionFilter:
        if (source.isStepNode()) {
            if (ignoreLanguageContextInitialization.get() && !source.getContext().isLanguageContextInitialized()) {
                return returnValue;
            }
        }
        Thread currentThread = Thread.currentThread();
        SuspendedEvent event = currentSuspendedEventMap.get(currentThread);
        if (event != null) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: recursive from source:%s context:%s location:%s", source, source.getContext(), source.getSuspendAnchors());
            }
            // avoid recursive suspensions in non legacy mode.
            return returnValue;
        }

        if (source.consumeIsDuplicate(this)) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: duplicate from source:%s context:%s location:%s", source, source.getContext(), source.getSuspendAnchors());
            }
            return returnValue;
        }

        // only the first DebuggerNode for a source location and thread will reach here.

        // mark all other nodes at this source location as duplicates
        List<DebuggerNode> nodes = collectDebuggerNodes(source, suspendAnchor);
        for (DebuggerNode node : nodes) {
            if (node == source) {
                // for the current one we won't call isDuplicate
                continue;
            }
            node.markAsDuplicate(this);
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
            Breakpoint fb = conditionFailure.getBreakpoint();
            if (fb.isGlobal()) {
                fb = fb.getROWrapper();
            }
            breakpointFailures.put(fb, conditionFailure.getConditionFailure());
        }

        Object newReturnValue = processBreakpointsAndStep(context, nodes, s, source, frame, suspendAnchor,
                        inputValuesProvider, returnValue, exception, breakpointFailures,
                        new Supplier<SuspendedContext>() {
                            @Override
                            public SuspendedContext get() {
                                return SuspendedContext.create(source.getContext(), debugger.getEnv());
                            }
                        });
        return newReturnValue;
    }

    private static void clearFrame(RootNode root, MaterializedFrame frame) {
        FrameDescriptor descriptor = frame.getFrameDescriptor();
        if (root.getFrameDescriptor() == descriptor) {
            // Clear only those frames that correspond to the current root
            Object value = descriptor.getDefaultValue();
            for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
                if (frame.isStatic(slot)) {
                    frame.setObjectStatic(slot, value);
                } else {
                    frame.setObject(slot, value);
                }
            }
            for (int slot = 0; slot < descriptor.getNumberOfAuxiliarySlots(); slot++) {
                frame.setAuxiliarySlot(slot, null);
            }
        }
    }

    private void notifyUnwindCallback(MaterializedFrame frame, InsertableNode insertableNode) {
        Thread currentThread = Thread.currentThread();
        SteppingStrategy s = getSteppingStrategy(currentThread);
        // We must have an active stepping strategy on this thread when unwind finished
        assert s != null;
        assert s.isUnwind();
        assert s.step(this, null, null);
        s.consume();
        // Clear the frame that is to be re-entered
        clearFrame(((Node) insertableNode).getRootNode(), frame);
        // Fake the caller context
        Caller caller = findCurrentCaller(this, includeInternal);
        SuspendedContext context = SuspendedContext.create(caller.node, ((SteppingStrategy.Unwind) s).unwind);
        doSuspend(context, SuspendAnchor.AFTER, caller.frame, insertableNode);
    }

    static Caller findCurrentCaller(DebuggerSession session, boolean includeInternal) {
        return Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Caller>() {
            private int depth = 0;

            @Override
            public Caller visitFrame(FrameInstance frameInstance) {
                // we stop at eval root stack frames
                if (!SuspendedEvent.isEvalRootStackFrame(session, frameInstance) && (depth++ == 0)) {
                    return null;
                }
                Node callNode = frameInstance.getCallNode();
                while (callNode != null && !SourceSectionFilter.ANY.includes(callNode)) {
                    callNode = callNode.getParent();
                }
                RootNode root = callNode != null ? callNode.getRootNode() : ((RootCallTarget) frameInstance.getCallTarget()).getRootNode();
                if (root == null || !includeInternal && root.isInternal()) {
                    return null;
                }
                if (callNode == null) {
                    callNode = root.getLeafNodeByFrame(frameInstance);
                }
                if (callNode == null) {
                    return null;
                }
                return new Caller(frameInstance, callNode);
            }
        });
    }

    private Object notifyCallerReturn(EventContext context, SteppingStrategy s, DebuggerNode source, SuspendAnchor suspendAnchor, Object returnValue) {
        // SuspensionFilter:
        if (source.isStepNode()) {
            if (ignoreLanguageContextInitialization.get() && !source.getContext().isLanguageContextInitialized()) {
                return returnValue;
            }
        }
        // Fake the caller context
        Caller caller = findCurrentCaller(this, includeInternal);
        if (caller == null) {
            // We did not find a caller node
            return returnValue;
        }
        return notifyAtCaller(context, caller, s, source, suspendAnchor, returnValue, null, null);
    }

    Object notifyAtCaller(EventContext context, Caller caller, SteppingStrategy s, DebuggerNode source, SuspendAnchor suspendAnchor, Object returnValue, DebugException exception,
                    BreakpointConditionFailure conditionFailure) {
        ThreadSuspension suspensionDisabled = threadSuspensions.get();
        if (suspensionDisabled != null && !suspensionDisabled.enabled) {
            return returnValue;
        }

        Thread currentThread = Thread.currentThread();
        SuspendedEvent event = currentSuspendedEventMap.get(currentThread);
        if (event != null) {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: recursive from source:%s context:%s location:%s", source, source.getContext(), source.getSuspendAnchors());
            }
            // avoid recursive suspensions in non legacy mode.
            return returnValue;
        }

        List<DebuggerNode> nodes = collectDebuggerNodes(caller.node, suspendAnchor);
        for (DebuggerNode node : nodes) {
            Breakpoint breakpoint = node.getBreakpoint();
            if (breakpoint == null || isBreakpointsActive(breakpoint.getKind()) && breakpoint.getCondition() == null) {
                // Not a breakpoint node, nor unconditional breakpoint.
                // We will suspend there later on.
                return returnValue;
            }
        }
        // Suspend on the return from the caller and mark all existing nodes as duplicate
        for (DebuggerNode node : nodes) {
            node.markAsDuplicate(this);
        }
        nodes.add(source);

        SteppingStrategy strategy = s;
        if (strategy == null) {
            strategy = getSteppingStrategy(currentThread);
            if (strategy == null) {
                // a new Thread just appeared
                strategy = notifyNewThread(currentThread);
            }
        }

        Map<Breakpoint, Throwable> breakpointFailures = null;
        if (conditionFailure != null) {
            breakpointFailures = new HashMap<>();
            Breakpoint fb = conditionFailure.getBreakpoint();
            if (fb.isGlobal()) {
                fb = fb.getROWrapper();
            }
            breakpointFailures.put(fb, conditionFailure.getConditionFailure());
        }

        Object newReturnValue = processBreakpointsAndStep(context, nodes, strategy, source, caller.frame, suspendAnchor, null, returnValue, exception, breakpointFailures,
                        new Supplier<SuspendedContext>() {
                            @Override
                            public SuspendedContext get() {
                                return SuspendedContext.create(caller.node, null);
                            }
                        });
        return newReturnValue;
    }

    @SuppressWarnings("all") // The parameter breakpointFailures should not be assigned
    private Object processBreakpointsAndStep(EventContext context, List<DebuggerNode> nodes, SteppingStrategy s, DebuggerNode source, MaterializedFrame frame,
                    SuspendAnchor suspendAnchor, InputValuesProvider inputValuesProvider, Object returnValue, DebugException exception,
                    Map<Breakpoint, Throwable> breakpointFailures, Supplier<SuspendedContext> contextSupplier) {
        List<Breakpoint> breaks = null;
        for (DebuggerNode node : nodes) {
            Breakpoint breakpoint = node.getBreakpoint();
            if (breakpoint == null || !isBreakpointsActive(breakpoint.getKind())) {
                continue; // not a breakpoint node
            }
            boolean hit = true;
            BreakpointConditionFailure failure = null;
            try {
                hit = breakpoint.notifyIndirectHit(context, source, node, frame, exception);
            } catch (BreakpointConditionFailure e) {
                failure = e;
            }
            if (hit) {
                if (breaks == null) {
                    breaks = new ArrayList<>();
                }
                breaks.add(breakpoint.isGlobal() ? breakpoint.getROWrapper() : breakpoint);
            }
            if (failure != null) {
                if (breakpointFailures == null) {
                    breakpointFailures = new HashMap<>();
                }
                Breakpoint fb = failure.getBreakpoint();
                if (fb.isGlobal()) {
                    fb = fb.getROWrapper();
                }
                breakpointFailures.put(fb, failure.getConditionFailure());
            }
        }
        if (breaks == null) {
            breaks = Collections.emptyList();
        }
        if (breakpointFailures == null) {
            breakpointFailures = Collections.emptyMap();
        }

        boolean hitStepping = s.step(this, source.getContext(), suspendAnchor);
        boolean hitBreakpoint = !breaks.isEmpty();
        Object newReturnValue = returnValue;
        if (hitStepping || hitBreakpoint) {
            s.consume();
            newReturnValue = doSuspend(contextSupplier.get(), suspendAnchor, frame, source, inputValuesProvider, returnValue, exception, breaks,
                            breakpointFailures);
        } else {
            if (Debugger.TRACE) {
                trace("ignored suspended reason: strategy(%s) from source:%s context:%s location:%s", s, source, source.getContext(), source.getSuspendAnchors());
            }
        }
        if (s.isKill()) {   // ComposedStrategy can become kill
            performKill(source.getContext().getInstrumentedNode());
        }
        return newReturnValue;
    }

    private Object doSuspend(SuspendedContext context, SuspendAnchor suspendAnchor, MaterializedFrame frame, InsertableNode insertableNode) {
        return doSuspend(context, suspendAnchor, frame, insertableNode, null, null, null, Collections.emptyList(), Collections.emptyMap());
    }

    private Object doSuspend(SuspendedContext context, SuspendAnchor suspendAnchor, MaterializedFrame frame,
                    InsertableNode insertableNode, InputValuesProvider inputValuesProvider, Object returnValue, DebugException exception,
                    List<Breakpoint> breaks, Map<Breakpoint, Throwable> conditionFailures) {
        CompilerAsserts.neverPartOfCompilation();
        Thread currentThread = Thread.currentThread();

        SuspendedEvent suspendedEvent;
        Object newReturnValue;
        try {
            suspendedEvent = new SuspendedEvent(this, currentThread, context, frame, suspendAnchor, insertableNode, inputValuesProvider, returnValue, exception, breaks, conditionFailures);
            if (exception != null) {
                exception.setSuspendedEvent(suspendedEvent);
            }
            currentSuspendedEventMap.put(currentThread, suspendedEvent);
            try {
                callback.onSuspend(suspendedEvent);
            } finally {
                currentSuspendedEventMap.remove(currentThread);
                newReturnValue = suspendedEvent.getReturnObject();
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
            return newReturnValue;
        }

        SteppingStrategy strategy = suspendedEvent.getNextStrategy();
        if (!strategy.isKill()) {
            // suspend(...) has been called during SuspendedEvent notification. this is only
            // possible in non-legacy mode.
            SteppingStrategy currentStrategy = getSteppingStrategy(currentThread);
            if (currentStrategy != null && !currentStrategy.isConsumed()) {
                strategy = currentStrategy;
            }
        }
        strategy.initialize(context, suspendAnchor);

        if (Debugger.TRACE) {
            trace("end suspend with strategy %s at %s location %s", strategy, context, suspendAnchor);
        }

        setSteppingStrategy(currentThread, strategy, true);
        if (strategy.isKill()) {
            performKill(context.getInstrumentedNode());
        } else if (strategy.isUnwind()) {
            ThreadDeath unwind = context.createUnwind(null, syntaxElementsBinding);
            ((SteppingStrategy.Unwind) strategy).unwind = unwind;
            throw unwind;
        }
        return newReturnValue;
    }

    private void performKill(Node location) {
        if (Boolean.TRUE.equals(inEvalInContext.get())) {
            throw new KillException(location);
        } else {
            TruffleContext truffleContext = debugger.getEnv().getEnteredContext();
            truffleContext.closeCancelled(location, KillException.MESSAGE);
        }
    }

    private List<DebuggerNode> collectDebuggerNodes(DebuggerNode source, SuspendAnchor suspendAnchor) {
        EventContext context = source.getContext();
        List<DebuggerNode> nodes = new ArrayList<>();
        nodes.add(source);
        Iterator<ExecutionEventNode> nodesIterator = context.lookupExecutionEventNodes(allBindings);
        if (SuspendAnchor.BEFORE.equals(suspendAnchor)) {
            // We collect nodes following the source (these nodes remain to be executed)
            boolean after = false;
            while (nodesIterator.hasNext()) {
                DebuggerNode node = (DebuggerNode) nodesIterator.next();
                if (after) {
                    if (node.isActiveAt(suspendAnchor)) {
                        nodes.add(node);
                    }
                } else {
                    after = node == source;
                }
            }
        } else {
            // We collect nodes preceding the source (these nodes remain to be executed)
            while (nodesIterator.hasNext()) {
                DebuggerNode node = (DebuggerNode) nodesIterator.next();
                if (node == source) {
                    break;
                }
                if (node.isActiveAt(suspendAnchor)) {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    private List<DebuggerNode> collectDebuggerNodes(Node iNode, SuspendAnchor suspendAnchor) {
        List<DebuggerNode> nodes = new ArrayList<>();
        for (EventBinding<?> binding : allBindings) {
            DebuggerNode node = (DebuggerNode) debugger.getInstrumenter().lookupExecutionEventNode(iNode, binding);
            if (node != null && node.isActiveAt(suspendAnchor)) {
                nodes.add(node);
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
     * @throws DebugException
     */
    static Object evalInContext(SuspendedEvent ev, String code, FrameInstance frameInstance) throws DebugException {
        Node node;
        MaterializedFrame frame;
        if (frameInstance == null) {
            node = ev.getContext().getInstrumentedNode();
            frame = ev.getMaterializedFrame();
        } else {
            node = frameInstance.getCallNode();
            frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
        }
        try {
            inEvalInContext.set(Boolean.TRUE);
            return evalInContext(ev, node, frame, code);
        } catch (KillException kex) {
            throw DebugException.create(ev.getSession(), "Evaluation was killed.");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Throwable ex) {
            LanguageInfo language = null;
            RootNode root = node.getRootNode();
            if (root != null) {
                language = root.getLanguageInfo();
            }
            throw DebugException.create(ev.getSession(), ex, language);
        } finally {
            inEvalInContext.remove();
        }
    }

    private static Object evalInContext(SuspendedEvent ev, Node node, MaterializedFrame frame, String code) {
        RootNode rootNode = node.getRootNode();
        if (rootNode == null) {
            throw new IllegalArgumentException("Cannot evaluate in context using a node that is not yet adopted using a RootNode.");
        }

        LanguageInfo info = rootNode.getLanguageInfo();
        if (info == null) {
            throw new IllegalArgumentException("Cannot evaluate in context using a without an associated TruffleLanguage.");
        }

        final Source source = Source.newBuilder(info.getId(), code, "eval in context").internal(false).build();
        ExecutableNode fragment = ev.getSession().getDebugger().getEnv().parseInline(source, node, frame);
        if (fragment != null) {
            ev.getInsertableNode().setParentOf(fragment);
            return fragment.execute(frame);
        } else {
            if (!info.isInteractive()) {
                throw new IllegalStateException("Can not evaluate in a non-interactive language.");
            }
            return Debugger.ACCESSOR.evalInContext(source, node, frame);
        }
    }

    /**
     * Information about a caller node.
     */
    static final class Caller {

        final Node node;
        final MaterializedFrame frame;

        Caller(FrameInstance frameInstance) {
            this.node = frameInstance.getCallNode();
            this.frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
        }

        Caller(FrameInstance frameInstance, Node callNode) {
            this.node = callNode;
            this.frame = frameInstance.getFrame(FrameAccess.MATERIALIZE).materialize();
        }

    }

    static final class ThreadSuspension {

        static final ThreadSuspension ENABLED = new ThreadSuspension(true);
        static final ThreadSuspension DISABLED = new ThreadSuspension(false);

        boolean enabled;

        ThreadSuspension(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private class SteppingNode extends DebuggerNode implements InputValuesProvider {

        SteppingNode(EventContext context) {
            super(context);
        }

        @Override
        boolean isStepNode() {
            return true;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (stepping.get()) {
                doStepBefore(frame.materialize());
            }
        }

        @Override
        protected void onReturnValue(VirtualFrame frame, Object result) {
            if (stepping.get()) {
                Object newResult = doStepAfter(frame.materialize(), result);
                if (newResult != result) {
                    CompilerDirectives.transferToInterpreter();
                    throw getContext().createUnwind(new ChangedReturnInfo(newResult));
                }
            }
        }

        @Override
        protected void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (stepping.get()) {
                doStepAfter(frame.materialize(), exception);
            }
        }

        @Override
        protected void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
            if (stepping.get() && hasExpressionElement) {
                saveInputValue(frame, inputIndex, inputValue);
            }
        }

        @TruffleBoundary
        private void doStepBefore(MaterializedFrame frame) {
            SuspendAnchor anchor = SuspendAnchor.BEFORE;
            boolean doCallback;
            if (suspendNext || suspendAll) {
                doCallback = isSteppingEnabledOnCurrentThread();
            } else {
                SteppingStrategy steppingStrategy = getSteppingStrategy(Thread.currentThread());
                doCallback = steppingStrategy != null && isSteppingEnabledOnCurrentThread() && steppingStrategy.isActiveOnStepTo(context, anchor);
            }
            if (doCallback) {
                notifyCallback(context, this, frame, anchor, null, null, null, null);
            }
        }

        @TruffleBoundary
        protected final Object doStepAfter(MaterializedFrame frame, Object result) {
            SuspendAnchor anchor = SuspendAnchor.AFTER;
            SteppingStrategy steppingStrategy = getSteppingStrategy(Thread.currentThread());
            if (steppingStrategy != null && isSteppingEnabledOnCurrentThread() && steppingStrategy.isActiveOnStepTo(context, anchor)) {
                return notifyCallback(context, this, frame, anchor, this, result, null, null);
            }
            return result;
        }

        @Override
        public Object[] getDebugInputValues(MaterializedFrame frame) {
            return getSavedInputValues(frame);
        }

        @Override
        Set<SuspendAnchor> getSuspendAnchors() {
            return DebuggerSession.ANCHOR_SET_ALL;
        }

        @Override
        boolean isActiveAt(SuspendAnchor anchor) {
            SteppingStrategy steppingStrategy = getSteppingStrategy(Thread.currentThread());
            if (steppingStrategy != null) {
                return steppingStrategy.isActive(context, anchor);
            } else {
                return false;
            }
        }

    }

    /**
     * Combines stepping with stack depth control, stop after a call and unwind.
     */
    private final class RootSteppingDepthNode extends SteppingNode {

        RootSteppingDepthNode(EventContext context) {
            super(context);
        }

        @Override
        boolean isStepNode() {
            return hasRootElement;
        }

        @Override
        protected void onEnter(VirtualFrame frame) {
            if (stepping.get()) {
                doEnter();
                if (hasRootElement) {
                    super.onEnter(frame);
                }
            }
        }

        @Override
        public void onReturnValue(VirtualFrame frame, Object result) {
            if (stepping.get()) {
                doReturn(frame.materialize(), result);
            }
        }

        @Override
        public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
            if (stepping.get()) {
                doReturn();
            }
        }

        @Override
        protected Object onUnwind(VirtualFrame frame, Object info) {
            Object ret = super.onUnwind(frame, info);
            if (ret != null) {
                return ret;
            }
            if (stepping.get()) {
                return doUnwind(frame.materialize());
            } else {
                return null;
            }
        }

        @Override
        public void setParentOf(Node child) {
            insert(child);
        }

        @TruffleBoundary
        private void doEnter() {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                steppingStrategy.notifyCallEntry();
            }
        }

        @TruffleBoundary
        private void doReturn(MaterializedFrame frame, Object result) {
            SteppingStrategy steppingStrategy;
            Object newResult = null;
            try {
                if (hasRootElement) {
                    newResult = doStepAfter(frame, result);
                }
            } finally {
                steppingStrategy = strategyMap.get(Thread.currentThread());
                if (steppingStrategy != null) {
                    // Stepping out of a function.
                    steppingStrategy.notifyCallExit();
                }
            }
            if (steppingStrategy != null && steppingStrategy.isStopAfterCall()) {
                newResult = notifyCallerReturn(context, steppingStrategy, this, SuspendAnchor.AFTER, newResult != null ? newResult : result);
                if (newResult != result) {
                    throw getContext().createUnwind(new ChangedReturnInfo(newResult));
                }
            }
        }

        @TruffleBoundary
        private void doReturn() {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                steppingStrategy.notifyCallExit();
            }
        }

        @TruffleBoundary
        private Object doUnwind(MaterializedFrame frame) {
            SteppingStrategy steppingStrategy = strategyMap.get(Thread.currentThread());
            if (steppingStrategy != null) {
                Object info = steppingStrategy.notifyOnUnwind();
                if (info == ProbeNode.UNWIND_ACTION_REENTER) {
                    notifyUnwindCallback(frame, this);
                }
                return info;
            } else {
                return null;
            }
        }

        @Override
        Set<SuspendAnchor> getSuspendAnchors() {
            return DebuggerSession.ANCHOR_SET_ALL;
        }

        @Override
        boolean isActiveAt(SuspendAnchor anchor) {
            return hasRootElement;
        }

    }

    /**
     * Helper class that uses an assumption to switch between stepping mode and non-stepping mode
     * efficiently.
     */
    static final class StableBoolean {

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

    @SuppressFBWarnings("")
    public void example() {
        // @formatter:off
        TruffleInstrument.Env instrumentEnv = null;
        // BEGIN: DebuggerSessionSnippets#example
        try (DebuggerSession session = Debugger.find(instrumentEnv).
                        startSession(new SuspendedCallback() {
            public void onSuspend(SuspendedEvent event) {
                // step into the next event
                event.prepareStepInto(1);
            }
        })) {
            Source someCode = Source.newBuilder("...",
                            "...", "example").build();

            // install line breakpoint
            session.install(Breakpoint.newBuilder(someCode).lineIs(3).build());
        }
        // END: DebuggerSessionSnippets#example
        // @formatter:on
    }
}
