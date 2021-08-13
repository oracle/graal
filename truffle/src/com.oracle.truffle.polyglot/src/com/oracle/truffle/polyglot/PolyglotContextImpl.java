/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;
import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;
import static com.oracle.truffle.polyglot.PolyglotValueDispatch.hostEnter;
import static com.oracle.truffle.polyglot.PolyglotValueDispatch.hostLeave;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostService;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.StableLocalLocations;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ValueMigrationException;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;
import com.oracle.truffle.polyglot.PolyglotThreadLocalActions.HandshakeConfig;

final class PolyglotContextImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    private static final TruffleLogger LOG = TruffleLogger.getLogger(PolyglotEngineImpl.OPTION_GROUP_ENGINE, PolyglotContextImpl.class);
    private static final InteropLibrary UNCACHED = InteropLibrary.getFactory().getUncached();
    private static final Object[] DISPOSED_CONTEXT_THREAD_LOCALS = new Object[0];

    final WeakAssumedValue<PolyglotThreadInfo> singleThreadValue = new WeakAssumedValue<>("Single thread");
    volatile boolean singleThreaded = true;

    private final Map<Thread, PolyglotThreadInfo> threads = new WeakHashMap<>();

    /*
     * Do not modify only read. Use setCachedThreadInfo to modify.
     */
    private volatile PolyglotThreadInfo cachedThreadInfo = PolyglotThreadInfo.NULL;

    volatile Context api;

    private static final Map<State, State[]> VALID_TRANSITIONS = new EnumMap<>(State.class);

    static {
        VALID_TRANSITIONS.put(State.DEFAULT, new State[]{
                        State.CLOSING,
                        State.INTERRUPTING,
                        State.CANCELLING
        });
        VALID_TRANSITIONS.put(State.CLOSING, new State[]{
                        State.CLOSED,
                        State.CLOSING_INTERRUPTING,
                        State.CLOSING_CANCELLING,
                        State.DEFAULT
        });
        VALID_TRANSITIONS.put(State.INTERRUPTING, new State[]{
                        State.DEFAULT,
                        State.CLOSING_INTERRUPTING,
                        State.CANCELLING,
        });
        VALID_TRANSITIONS.put(State.CANCELLING, new State[]{
                        State.CLOSING_CANCELLING
        });
        VALID_TRANSITIONS.put(State.CLOSING_INTERRUPTING, new State[]{
                        State.CLOSED,
                        State.CLOSING,
                        State.CLOSING_CANCELLING,
                        State.INTERRUPTING
        });
        VALID_TRANSITIONS.put(State.CLOSING_CANCELLING, new State[]{
                        State.CLOSED_CANCELLED,
                        State.CANCELLING
        });
        VALID_TRANSITIONS.put(State.CLOSED,
                        new State[0]);
        VALID_TRANSITIONS.put(State.CLOSED_CANCELLED,
                        new State[0]);
    }

    enum State {
        /*
         * Initial state. Context is valid and ready for use.
         */
        DEFAULT,
        /*
         * Interrupt operation has been started. Threads are being interrupted.
         */
        INTERRUPTING,
        /*
         * Cancel operation has been initiated. Threads are being stopped. The CANCELLING state
         * overrides the INTERRUPTING state.
         */
        CANCELLING,
        /*
         * Close operation has been initiated in the DEFAULT state, or it has been initiated in the
         * INTERRUPTING state and the interrupt operation stopped during closing. The thread that
         * initiated the operation is stored in the closingThread field. The close operation either
         * finishes successfully and the context goes into one of the closed states, or the close
         * operation fails and the context goes back to the DEFAULT state.
         */
        CLOSING,
        /*
         * Close operation has been initiated in the INTERRUPTING state and the interrupt operation
         * is still in progress, or it has been initiated in the DEFAULT state and the interrupt
         * operation started during closing, i.e., the transition to this state can either be from
         * the CLOSING or the INTERRUPTING state. Even if the transition is from the CLOSING state
         * the closingThread is still the one that initiated the close operation, not the one that
         * initiated the interrupt operation. The close operation either finishes successfully and
         * the context goes into one of the closed states, or the close operation fails and the
         * context goes back to the INTERRUPTING state.
         */
        CLOSING_INTERRUPTING,
        /*
         * Close operation has been initiated and at the same time the cancel operation is in
         * progress. Transition to this state can either be from the CLOSING, the CANCELLING, or the
         * CLOSING_INTERRUPTING state. Even if the transition is from one of the closing states the
         * closingThread is still the one that initiated the close operation. The CLOSING_CANCELLING
         * state overrides the CLOSING and the CLOSING_INTERRUPTING states. Close operation that
         * started in the CLOSING_CANCELLING state must finish successfully, otherwise it is an
         * internal error. Close operation that did not start in the CLOSING_CANCELLING state and
         * the state was overridden by CLOSING_CANCELLING during the operation can fail in which
         * case the state goes back to CANCELLING.
         */
        CLOSING_CANCELLING,
        /*
         * Closing operation in the CLOSING or the CLOSING_INTERRUPTING state has finished
         * successfully.
         */
        CLOSED,
        /*
         * Closing operation in the CLOSING_CANCELLING state has finished successfully.
         */
        CLOSED_CANCELLED;

        /*
         * The context is not usable and may be in an inconsistent state.
         */
        boolean isInvalidOrClosed() {
            switch (this) {
                case CANCELLING:
                case CLOSING_CANCELLING:
                case CLOSED:
                case CLOSED_CANCELLED:
                    return true;
                default:
                    return false;
            }
        }

        boolean isInterrupting() {
            switch (this) {
                case INTERRUPTING:
                case CLOSING_INTERRUPTING:
                    return true;
                default:
                    return false;
            }
        }

        boolean isCancelling() {
            switch (this) {
                case CANCELLING:
                case CLOSING_CANCELLING:
                    return true;
                default:
                    return false;
            }
        }

        boolean isClosing() {
            switch (this) {
                case CLOSING:
                case CLOSING_INTERRUPTING:
                case CLOSING_CANCELLING:
                    return true;
                default:
                    return false;
            }
        }

        boolean isClosed() {
            switch (this) {
                case CLOSED:
                case CLOSED_CANCELLED:
                    return true;
                default:
                    return false;
            }
        }

        private boolean shouldCacheThreadInfo() {
            switch (this) {
                case DEFAULT:
                    return true;
                default:
                    return false;
            }
        }
    }

    volatile State state = State.DEFAULT;

    /*
     * Used only in asserts.
     */
    private boolean isTransitionAllowed(State fromState, State toState) {
        assert Thread.holdsLock(this);
        State[] successors = VALID_TRANSITIONS.get(fromState);
        for (State successor : successors) {
            if (successor == toState) {
                return isAdditionalTransitionConditionSatisfied(fromState, toState);
            }
        }
        return false;
    }

    private boolean isAdditionalTransitionConditionSatisfied(State fromState, State toState) {
        assert Thread.holdsLock(this);
        if (fromState.isClosing() && !toState.isClosing()) {
            return closingThread == Thread.currentThread();
        }
        return true;
    }

    private ExecutorService cleanupExecutorService;
    private Future<?> cleanupFuture;
    private volatile String invalidMessage;
    volatile boolean invalidResourceLimit;
    volatile Thread closingThread;
    private final ReentrantLock closingLock = new ReentrantLock();
    private final ReentrantLock interruptingLock = new ReentrantLock();

    volatile boolean disposing;
    final PolyglotEngineImpl engine;
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;

    final TruffleContext creatorTruffleContext;
    final TruffleContext currentTruffleContext;
    final PolyglotContextImpl parent;
    volatile Map<String, Value> polyglotBindings; // for direct legacy access
    volatile Value polyglotHostBindings; // for accesses from the polyglot api
    private final PolyglotBindings polyglotBindingsObject = new PolyglotBindings(this);
    final PolyglotLanguage creator; // creator for internal contexts
    final Map<String, Object> creatorArguments; // special arguments for internal contexts
    final ContextWeakReference weakReference;
    final Set<ProcessHandlers.ProcessDecorator> subProcesses;

    @CompilationFinal PolyglotContextConfig config; // effectively final

    // map from class to language index
    @CompilationFinal private volatile FinalIntMap languageIndexMap;

    private final List<PolyglotContextImpl> childContexts = new ArrayList<>();
    boolean inContextPreInitialization; // effectively final
    List<Source> sourcesToInvalidate;  // Non null only during content pre-initialization

    final AtomicLong volatileStatementCounter = new AtomicLong();
    long statementCounter;
    final long statementLimit;
    private volatile Object contextBoundLoggers;

    /*
     * Initialized once per context.
     */
    @CompilationFinal(dimensions = 1) Object[] contextLocals;

    volatile boolean localsCleared;

    private ObjectSizeCalculator objectSizeCalculator;

    final PolyglotThreadLocalActions threadLocalActions;
    private Collection<Closeable> closeables;

    private final Set<PauseThreadLocalAction> pauseThreadLocalActions = new LinkedHashSet<>();

    @CompilationFinal private Object hostContextImpl;

    /* Constructor for testing. */
    @SuppressWarnings("unused")
    private PolyglotContextImpl() {
        this.engine = null;
        this.contexts = null;
        this.creatorTruffleContext = null;
        this.currentTruffleContext = null;
        this.parent = null;
        this.polyglotHostBindings = null;
        this.polyglotBindings = null;
        this.creator = null;
        this.creatorArguments = null;
        this.weakReference = null;
        this.statementLimit = 0;
        this.threadLocalActions = null;
        this.subProcesses = new HashSet<>();
    }

    /*
     * Constructor for outer contexts.
     */
    PolyglotContextImpl(PolyglotEngineImpl engine, PolyglotContextConfig config) {
        this.parent = null;
        this.engine = engine;
        this.config = config;
        this.creator = null;
        this.creatorArguments = Collections.emptyMap();
        this.creatorTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, true);
        this.currentTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, false);
        this.weakReference = new ContextWeakReference(this);
        this.contexts = createContextArray(engine.hostLanguageInstance);
        this.subProcesses = new HashSet<>();
        this.statementLimit = config.limits != null && config.limits.statementLimit != 0 ? config.limits.statementLimit : Long.MAX_VALUE - 1;
        this.statementCounter = statementLimit;
        this.volatileStatementCounter.set(statementLimit);
        this.threadLocalActions = new PolyglotThreadLocalActions(this);

        PolyglotEngineImpl.ensureInstrumentsCreated(config.getConfiguredInstruments());

        /*
         * Instruments can add loggers, and so configuration of loggers for this context must be
         * done after instruments are created.
         */
        if (!config.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, config.logLevels, getAllLoggers());
        }

        notifyContextCreated();
    }

    /*
     * Constructor for inner contexts.
     */
    @SuppressWarnings("hiding")
    PolyglotContextImpl(PolyglotLanguageContext creator, Map<String, Object> langConfig) {
        PolyglotContextImpl parent = creator.context;
        this.parent = parent;
        this.config = parent.config;
        this.engine = parent.engine;
        this.creator = creator.language;
        this.creatorArguments = langConfig;
        this.statementLimit = 0; // inner context limit must not be used anyway
        this.weakReference = new ContextWeakReference(this);
        this.creatorTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, true);
        this.currentTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, false);
        if (parent.state.isInterrupting()) {
            this.state = State.INTERRUPTING;
        } else if (parent.state.isCancelling()) {
            this.state = State.CANCELLING;
        }
        this.invalidMessage = this.parent.invalidMessage;
        this.contextBoundLoggers = this.parent.contextBoundLoggers;
        this.threadLocalActions = new PolyglotThreadLocalActions(this);
        if (!parent.config.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, parent.config.logLevels, getAllLoggers());
        }
        this.contexts = createContextArray(engine.hostLanguageInstance);
        this.subProcesses = new HashSet<>();
        // notifyContextCreated() is called after spiContext.impl is set to this.
        this.engine.noInnerContexts.invalidate();
    }

    OptionValues getInstrumentContextOptions(PolyglotInstrument instrument) {
        return config.getInstrumentOptionValues(instrument);
    }

    public void resetLimits() {
        PolyglotLanguageContext languageContext = this.getHostContext();
        Object prev = hostEnter(languageContext);
        try {
            PolyglotLimits.reset(this);
            EngineAccessor.INSTRUMENT.notifyContextResetLimit(engine, creatorTruffleContext);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    public void safepoint() {
        PolyglotLanguageContext languageContext = this.getHostContext();
        Object prev = hostEnter(languageContext);
        try {
            TruffleSafepoint.poll(this.engine.getUncachedLocation());
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    private PolyglotLanguageContext[] createContextArray(PolyglotLanguageInstance hostLanguageInstance) {
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        PolyglotLanguageContext[] newContexts = new PolyglotLanguageContext[engine.contextLength];
        Iterator<PolyglotLanguage> languageIterator = languages.iterator();
        for (int i = (PolyglotEngineImpl.HOST_LANGUAGE_INDEX + 1); i < engine.contextLength; i++) {
            PolyglotLanguage language = languageIterator.next();
            newContexts[i] = new PolyglotLanguageContext(this, language);
        }
        PolyglotLanguage hostLanguage = hostLanguageInstance.language;
        PolyglotLanguageContext hostContext = new PolyglotLanguageContext(this, hostLanguage);
        newContexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = hostContext;
        hostContext.ensureCreated(hostLanguage, hostLanguageInstance);
        hostContext.ensureInitialized(null);
        return newContexts;
    }

    PolyglotLanguageContext getContext(PolyglotLanguage language) {
        return contexts[language.contextIndex];
    }

    Object getContextImpl(PolyglotLanguage language) {
        return contexts[language.contextIndex].getContextImpl();
    }

    PolyglotLanguageContext getContextInitialized(PolyglotLanguage language, PolyglotLanguage accessingLanguage) {
        PolyglotLanguageContext context = getContext(language);
        context.ensureInitialized(accessingLanguage);
        return context;
    }

    void notifyContextCreated() {
        EngineAccessor.INSTRUMENT.notifyContextCreated(engine, creatorTruffleContext);
    }

    synchronized void addChildContext(PolyglotContextImpl child) {
        assert !state.isClosed();
        if (state.isClosing()) {
            throw PolyglotEngineException.illegalState("Adding child context into a closing context.");
        }
        childContexts.add(child);
    }

    /**
     * May be used anywhere to lookup the context.
     *
     * @throws IllegalStateException when there is no current context available.
     */
    static PolyglotContextImpl requireContext() {
        PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
        if (context == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PolyglotEngineException.illegalState("There is no current context available.");
        }
        return context;
    }

    public synchronized void explicitEnter() {
        try {
            Object[] prev = engine.enter(this);
            PolyglotThreadInfo current = getCurrentThreadInfo();
            assert current.getThread() == Thread.currentThread();
            current.explicitContextStack.addLast(prev);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(engine, t);
        }
    }

    public synchronized void explicitLeave() {
        if (state.isClosed()) {
            /*
             * closeImpl leaves automatically for all explicit enters on the closingThread, so
             * nothing else needs to be done if context is already closed.
             */
            return;
        }
        try {
            PolyglotThreadInfo current = getCurrentThreadInfo();
            LinkedList<Object[]> stack = current.explicitContextStack;
            if (stack.isEmpty() || current.getThread() == null) {
                throw PolyglotEngineException.illegalState("The context is not entered explicity. A context can only be left if it was previously entered.");
            }
            engine.leave(stack.removeLast(), this);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(engine, t);
        }
    }

    synchronized Future<Void> pause() {
        PauseThreadLocalAction pauseAction = new PauseThreadLocalAction(this);
        Future<Void> future = threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, pauseAction, new HandshakeConfig(true, true, false, false));
        pauseThreadLocalActions.add(pauseAction);
        return new ContextPauseHandle(pauseAction, future);
    }

    void resume(Future<Void> pauseFuture) {
        if (pauseFuture instanceof ContextPauseHandle && ((ContextPauseHandle) pauseFuture).pauseThreadLocalAction.context == this) {
            ContextPauseHandle pauseHandle = (ContextPauseHandle) pauseFuture;
            pauseHandle.resume();
        } else {
            throw new IllegalArgumentException("Resume method was not passed a valid pause future!");
        }
    }

    /**
     * Use to enter context if it's guaranteed to be called rarely and configuration flexibility is
     * needed. Otherwise use {@link PolyglotEngineImpl#enter(PolyglotContextImpl)}.
     */
    @TruffleBoundary
    Object[] enterThreadChanged(boolean notifyEnter, boolean enterReverted, boolean pollSafepoint, boolean deactivateSafepoints) {
        PolyglotThreadInfo enteredThread = null;
        Object[] prev = null;
        try {
            Thread current = Thread.currentThread();
            boolean needsInitialization = false;
            synchronized (this) {
                PolyglotThreadInfo threadInfo = getCurrentThreadInfo();
                if (enterReverted && threadInfo.getEnteredCount() == 0) {
                    threadLocalActions.notifyThreadActivation(threadInfo, false);
                    if ((state.isCancelling() || state == State.CLOSED_CANCELLED) && !threadInfo.isActive()) {
                        notifyThreadClosed(threadInfo);
                    }
                    if (state.isInterrupting() && !threadInfo.isActive()) {
                        Thread.interrupted();
                        notifyAll();
                    }
                }
                if (deactivateSafepoints && threadInfo != PolyglotThreadInfo.NULL) {
                    threadLocalActions.notifyThreadActivation(threadInfo, false);
                }
                checkClosed();
                assert threadInfo != null;

                threadInfo = threads.get(current);
                if (threadInfo == null) {
                    threadInfo = createThreadInfo(current);
                    needsInitialization = true;
                }
                if (singleThreaded) {
                    /*
                     * If this is the only thread, then setting the cached thread info to NULL is no
                     * performance problem. If there is other thread that is just about to enter, we
                     * are making sure that it initializes multi-threading if this thread doesn't do
                     * it.
                     */
                    setCachedThreadInfo(PolyglotThreadInfo.NULL);
                }
                boolean transitionToMultiThreading = isSingleThreaded() && hasActiveOtherThread(true);

                if (transitionToMultiThreading) {
                    // recheck all thread accesses
                    checkAllThreadAccesses(Thread.currentThread(), false);
                }

                if (transitionToMultiThreading) {
                    /*
                     * We need to do this early (before initializeMultiThreading) as entering or
                     * local initialization depends on single thread per context.
                     */
                    engine.singleThreadPerContext.invalidate();
                    singleThreaded = false;
                }

                if (needsInitialization) {
                    threads.put(current, threadInfo);
                }

                if (needsInitialization) {
                    /*
                     * Do not enter the thread before initializing thread locals. Creation of thread
                     * locals might fail.
                     */
                    initializeThreadLocals(threadInfo);
                }

                prev = threadInfo.enterInternal();
                if (notifyEnter) {
                    try {
                        threadInfo.notifyEnter(engine, this);
                    } catch (Throwable t) {
                        threadInfo.leaveInternal(prev);
                        throw t;
                    }
                }
                enteredThread = threadInfo;

                if (needsInitialization) {
                    this.threadLocalActions.notifyEnterCreatedThread();
                }

                // new thread became active so we need to check potential active thread local
                // actions and process them.
                Set<ThreadLocalAction> activatedActions = null;
                if (enteredThread.getEnteredCount() == 1 && !deactivateSafepoints) {
                    activatedActions = threadLocalActions.notifyThreadActivation(threadInfo, true);
                }

                if (transitionToMultiThreading) {
                    // we need to verify that all languages give access
                    // to all threads in multi-threaded mode.
                    transitionToMultiThreaded();
                }

                if (needsInitialization) {
                    initializeNewThread(current);
                }

                if (enteredThread.getEnteredCount() == 1 && !pauseThreadLocalActions.isEmpty()) {
                    for (Iterator<PauseThreadLocalAction> threadLocalActionIterator = pauseThreadLocalActions.iterator(); threadLocalActionIterator.hasNext();) {
                        PauseThreadLocalAction threadLocalAction = threadLocalActionIterator.next();
                        if (!threadLocalAction.isPause()) {
                            threadLocalActionIterator.remove();
                        } else {
                            if (activatedActions == null || !activatedActions.contains(threadLocalAction)) {
                                threadLocalActions.submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, threadLocalAction, new HandshakeConfig(true, true, false, false));
                            }
                        }
                    }
                }

                // never cache last thread on close or when closingThread
                setCachedThreadInfo(threadInfo);
            }

            if (needsInitialization) {
                EngineAccessor.INSTRUMENT.notifyThreadStarted(engine, creatorTruffleContext, current);
            }
            return prev;
        } finally {
            /*
             * We need to always poll the safepoint here in case we already submitted a thread local
             * action for this thread. Not polling here would make dependencies of that event wait
             * forever.
             */
            if (pollSafepoint) {
                try {
                    TruffleSafepoint.pollHere(engine.getUncachedLocation());
                } catch (Throwable t) {
                    /*
                     * Just in case a safepoint makes the enter fail we need to leave the context
                     * again.
                     */
                    if (enteredThread != null) {
                        this.leaveThreadChanged(prev, notifyEnter, true);
                    }
                    throw t;
                }
            }
        }
    }

    PolyglotThreadInfo getCachedThread() {
        PolyglotThreadInfo info;
        if (CompilerDirectives.inCompiledCode() && CompilerDirectives.isPartialEvaluationConstant(this)) {
            info = singleThreadValue.getConstant();
            if (info == null) {
                // this branch folds away if the thread info can be resolved as a constant
                info = cachedThreadInfo;
            }
        } else {
            info = cachedThreadInfo;
        }
        return info;
    }

    PolyglotThreadInfo getCurrentThreadInfo() {
        CompilerAsserts.neverPartOfCompilation();
        assert Thread.holdsLock(this);
        PolyglotThreadInfo info = getCachedThread();
        if (info.getThread() != Thread.currentThread()) {
            info = threads.get(Thread.currentThread());
            if (info == null) {
                // closingThread from a thread we have never seen.
                info = PolyglotThreadInfo.NULL;
            }
        }
        assert info.getThread() == null || info.getThread() == Thread.currentThread();
        return info;
    }

    void setCachedThreadInfo(PolyglotThreadInfo info) {
        if (!state.shouldCacheThreadInfo() || threadLocalActions.hasActiveEvents()) {
            // never set the cached thread when closed closing or invalid
            cachedThreadInfo = PolyglotThreadInfo.NULL;
        } else {
            cachedThreadInfo = info;
        }
    }

    synchronized void checkMultiThreadedAccess(PolyglotThread newThread) {
        boolean singleThread = singleThreaded ? !isActiveNotCancelled() : false;
        checkAllThreadAccesses(newThread, singleThread);
    }

    private void checkAllThreadAccesses(Thread enteringThread, boolean singleThread) {
        assert Thread.holdsLock(this);
        List<PolyglotLanguage> deniedLanguages = null;
        for (PolyglotLanguageContext context : contexts) {
            if (!context.isInitialized()) {
                continue;
            }
            boolean accessAllowed = true;
            if (!LANGUAGE.isThreadAccessAllowed(context.env, enteringThread, singleThread)) {
                accessAllowed = false;
            }
            if (accessAllowed) {
                for (PolyglotThreadInfo seenThread : threads.values()) {
                    if (!LANGUAGE.isThreadAccessAllowed(context.env, seenThread.getThread(), singleThread)) {
                        accessAllowed = false;
                        break;
                    }
                }
            }
            if (!accessAllowed) {
                if (deniedLanguages == null) {
                    deniedLanguages = new ArrayList<>();
                }
                deniedLanguages.add(context.language);
            }
        }
        if (deniedLanguages != null) {
            throw throwDeniedThreadAccess(enteringThread, singleThread, deniedLanguages);
        }
    }

    /**
     * Use to leave a context if its guaranteed to be called rarely and configuration flexibility is
     * needed. Otherwise use
     * {@link PolyglotEngineImpl#leave(PolyglotContextImpl, PolyglotContextImpl)}.
     */
    @TruffleBoundary
    PolyglotThreadInfo leaveThreadChanged(Object[] prev, boolean notifyLeft, boolean entered) {
        PolyglotThreadInfo info;
        synchronized (this) {
            Thread current = Thread.currentThread();
            setCachedThreadInfo(PolyglotThreadInfo.NULL);

            PolyglotThreadInfo threadInfo = threads.get(current);
            assert threadInfo != null;
            info = threadInfo;

            if (entered) {
                try {
                    if (notifyLeft) {
                        info.notifyLeave(engine, this);
                    }
                } finally {
                    info.leaveInternal(prev);
                }
            }
            if (threadInfo.getEnteredCount() == 0) {
                threadLocalActions.notifyThreadActivation(threadInfo, false);
            }

            if ((state.isCancelling() || state == State.CLOSED_CANCELLED) && !info.isActive()) {
                notifyThreadClosed(info);
            }

            boolean somePauseThreadLocalActionIsActive = false;
            if (threadInfo.getEnteredCount() == 0 && !pauseThreadLocalActions.isEmpty()) {
                for (Iterator<PauseThreadLocalAction> threadLocalActionIterator = pauseThreadLocalActions.iterator(); threadLocalActionIterator.hasNext();) {
                    PauseThreadLocalAction threadLocalAction = threadLocalActionIterator.next();
                    if (!threadLocalAction.isPause()) {
                        threadLocalActionIterator.remove();
                    } else {
                        somePauseThreadLocalActionIsActive = true;
                    }
                }
            }

            if (entered && !somePauseThreadLocalActionIsActive) {
                /*
                 * Must not cache thread info when this synchronized leave was called as a slow-path
                 * fallback (entered == false). The slow-path fallback does not perform enteredCount
                 * decrement and so other threads may see this thread as already left before the
                 * synchronized block is entered. If we cached the thread info in this case, then a
                 * subsequent fast-path enter would not perform operations that might be necessary,
                 * e.g. initialize multithreading.
                 */
                setCachedThreadInfo(threadInfo);
            }

            if (state.isInterrupting() && !info.isActive()) {
                Thread.interrupted();
                notifyAll();
            }
        }
        return info;
    }

    private void initializeNewThread(Thread thread) {
        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                LANGUAGE.initializeThread(context.env, thread);
            }
        }
    }

    long getStatementsExecuted() {
        long count;
        if (engine.singleThreadPerContext.isValid()) {
            count = this.statementCounter;
        } else {
            count = this.volatileStatementCounter.get();
        }
        return statementLimit - count;
    }

    private void transitionToMultiThreaded() {
        assert Thread.holdsLock(this);

        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                context.ensureMultiThreadingInitialized();
            }
        }
        singleThreaded = false;
        singleThreadValue.invalidate();

        long statementsExecuted = statementLimit - statementCounter;
        volatileStatementCounter.getAndAdd(-statementsExecuted);
    }

    private PolyglotThreadInfo createThreadInfo(Thread current) {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo threadInfo = new PolyglotThreadInfo(this, current);

        boolean singleThread = isSingleThreaded();
        List<PolyglotLanguage> deniedLanguages = null;
        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                if (!EngineAccessor.LANGUAGE.isThreadAccessAllowed(context.env, current, singleThread)) {
                    if (deniedLanguages == null) {
                        deniedLanguages = new ArrayList<>();
                    }
                    deniedLanguages.add(context.language);
                }
            }
        }

        if (deniedLanguages != null) {
            throw throwDeniedThreadAccess(current, singleThread, deniedLanguages);
        }
        singleThreadValue.update(threadInfo);

        return threadInfo;
    }

    static RuntimeException throwDeniedThreadAccess(Thread current, boolean accessSingleThreaded, List<PolyglotLanguage> deniedLanguages) {
        String message;
        StringBuilder languagesString = new StringBuilder("");
        for (PolyglotLanguage language : deniedLanguages) {
            if (languagesString.length() != 0) {
                languagesString.append(", ");
            }
            languagesString.append(language.getId());
        }
        if (accessSingleThreaded) {
            message = String.format("Single threaded access requested by thread %s but is not allowed for language(s) %s.", current, languagesString);
        } else {
            message = String.format("Multi threaded access requested by thread %s but is not allowed for language(s) %s.", current, languagesString);
        }
        throw PolyglotEngineException.illegalState(message);
    }

    Value findLegacyExportedSymbol(String symbolName) {
        Value legacySymbol = findLegacyExportedSymbol(symbolName, true);
        if (legacySymbol != null) {
            return legacySymbol;
        }
        return findLegacyExportedSymbol(symbolName, false);
    }

    private Value findLegacyExportedSymbol(String name, boolean onlyExplicit) {
        for (PolyglotLanguageContext languageContext : contexts) {
            if (languageContext.isInitialized()) {
                Object s = LANGUAGE.findExportedSymbol(languageContext.env, name, onlyExplicit);
                if (s != null) {
                    return languageContext.asValue(s);
                }
            }
        }
        return null;
    }

    public Value getBindings(String languageId) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        assert languageContext != null;
        Object prev = hostEnter(languageContext);
        try {
            if (!languageContext.isInitialized()) {
                languageContext.ensureInitialized(null);
            }
            return languageContext.getHostBindings();
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    public Value getPolyglotBindings() {
        try {
            checkClosed();
            Value bindings = this.polyglotHostBindings;
            if (bindings == null) {
                initPolyglotBindings();
                bindings = this.polyglotHostBindings;
            }
            return bindings;
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(engine, e);
        }
    }

    public Map<String, Value> getPolyglotGuestBindings() {
        Map<String, Value> bindings = this.polyglotBindings;
        if (bindings == null) {
            initPolyglotBindings();
            bindings = this.polyglotBindings;
        }
        return bindings;
    }

    private void initPolyglotBindings() {
        synchronized (this) {
            if (this.polyglotBindings == null) {
                this.polyglotBindings = new ConcurrentHashMap<>();
                PolyglotLanguageContext hostContext = getHostContext();
                PolyglotBindings bindings = new PolyglotBindings(hostContext);
                this.polyglotHostBindings = getAPIAccess().newValue(new PolyglotBindingsValue(hostContext, bindings), hostContext, bindings);
            }
        }
    }

    public Object getPolyglotBindingsObject() {
        return polyglotBindingsObject;
    }

    void checkClosed() {
        if (state.isInvalidOrClosed() && closingThread != Thread.currentThread() && invalidMessage != null) {
            /*
             * If invalidMessage == null, then invalid flag was set by close.
             */
            throw createCancelException(null);
        }
        if (state.isClosed()) {
            throw PolyglotEngineException.illegalState("The Context is already closed.");
        }
    }

    @TruffleBoundary
    private RuntimeException failValueSharing() {
        throw new ValueMigrationException("A value was tried to be migrated from one context to a different context. " +
                        "Value migration for the current context was disabled and is therefore disallowed.", engine.getUncachedLocation());
    }

    Object migrateValue(Object value, PolyglotContextImpl valueContext) {
        if (!config.allowValueSharing) {
            throw failValueSharing();
        }
        Object result = engine.host.migrateValue(this, value, valueContext);
        if (result != null) {
            // host made sure migration is fine
            return result;
        }
        // guaranteed by migrateValue
        assert value instanceof TruffleObject;
        if (value instanceof OtherContextGuestObject) {
            OtherContextGuestObject otherValue = (OtherContextGuestObject) value;
            if (otherValue.receiverContext == this && otherValue.delegateContext == valueContext) {
                // reuse wrapper it is already wrapped
                return otherValue;
            } else if (otherValue.receiverContext == valueContext && otherValue.delegateContext == this) {
                // unpack foreign value it belongs to that context
                return otherValue.delegate;
            } else {
                return new OtherContextGuestObject(this, otherValue.delegate, valueContext);
            }
        }
        assert value instanceof TruffleObject;
        return new OtherContextGuestObject(this, value, valueContext);
    }

    Object migrateHostWrapper(PolyglotWrapper wrapper) {
        Object wrapped = wrapper.getGuestObject();
        PolyglotContextImpl valueContext = wrapper.getContext();
        if (valueContext != this) {
            // migrate wrapped value to the context
            wrapped = migrateValue(wrapped, valueContext);
        }
        return wrapped;
    }

    PolyglotLanguageContext getHostContext() {
        return contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX];
    }

    Object getHostContextImpl() {
        return hostContextImpl;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return engine;
    }

    /*
     * Special version for getLanguageContext for the fast-path.
     */
    PolyglotLanguageContext getLanguageContext(Class<? extends TruffleLanguage<?>> languageClass) {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            return getLanguageContextImpl(languageClass);
        } else {
            return getLanguageContextBoundary(languageClass);
        }
    }

    @TruffleBoundary
    private PolyglotLanguageContext getLanguageContextBoundary(Class<? extends TruffleLanguage<?>> languageClass) {
        return getLanguageContextImpl(languageClass);
    }

    @SuppressWarnings("rawtypes")
    PolyglotLanguageContext findLanguageContext(Class<? extends TruffleLanguage> languageClazz) {
        PolyglotLanguage directLanguage = engine.getLanguage(languageClazz, false);
        if (directLanguage != null) {
            return getContext(directLanguage);
        }

        // slow language lookup - for compatibility
        for (PolyglotLanguageContext lang : contexts) {
            if (lang.isInitialized()) {
                TruffleLanguage<?> language = EngineAccessor.LANGUAGE.getLanguage(lang.env);
                if (languageClazz != TruffleLanguage.class && languageClazz.isInstance(language)) {
                    return lang;
                }
            }
        }
        Set<String> languageNames = new HashSet<>();
        for (PolyglotLanguageContext lang : contexts) {
            if (lang.isInitialized()) {
                languageNames.add(lang.language.cache.getClassName());
            }
        }
        throw PolyglotEngineException.illegalState("Cannot find language " + languageClazz + " among " + languageNames);

    }

    private PolyglotLanguageContext getLanguageContextImpl(Class<? extends TruffleLanguage<?>> languageClass) {
        FinalIntMap map = this.languageIndexMap;
        int indexValue = map != null ? map.get(languageClass) : -1;
        if (indexValue == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (this.languageIndexMap == null) {
                    this.languageIndexMap = new FinalIntMap();
                }
                indexValue = languageIndexMap.get(languageClass);
                if (indexValue == -1) {
                    PolyglotLanguageContext context = findLanguageContext(languageClass);
                    indexValue = context.language.contextIndex;
                    this.languageIndexMap.put(languageClass, indexValue);
                }
            }
        }
        PolyglotLanguageContext context = contexts[indexValue];
        return context;
    }

    void initializeInnerContextLanguage(String languageId) {
        PolyglotLanguage language = engine.idToLanguage.get(languageId);
        assert language != null : "language creating the inner context not be found";
        Object prev = engine.enterIfNeeded(this, true);
        try {
            initializeLanguage(language);
        } finally {
            engine.leaveIfNeeded(prev, this);
        }
    }

    private boolean initializeLanguage(PolyglotLanguage language) {
        PolyglotLanguageContext languageContext = getContext(language);
        assert languageContext != null;
        languageContext.checkAccess(null);
        if (!languageContext.isInitialized()) {
            return languageContext.ensureInitialized(null);
        }
        return false;
    }

    public boolean initializeLanguage(String languageId) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        Object prev = hostEnter(languageContext);
        try {
            return initializeLanguage(languageContext.language);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(languageContext, t, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    public Value parse(String languageId, Object sourceImpl) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        assert languageContext != null;
        Object prev = hostEnter(languageContext);
        try {
            Source source = (Source) sourceImpl;
            languageContext.checkAccess(null);
            languageContext.ensureInitialized(null);
            CallTarget target = languageContext.parseCached(null, source, null);
            return languageContext.asValue(new PolyglotParsedEval(languageContext, source, target));
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    private PolyglotLanguageContext lookupLanguageContext(String languageId) {
        PolyglotLanguageContext languageContext;
        try {
            PolyglotLanguage language = requirePublicLanguage(languageId);
            languageContext = getContext(language);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(engine, e);
        }
        return languageContext;
    }

    public Value eval(String languageId, Object sourceImpl) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        assert languageContext != null;
        Object prev = hostEnter(languageContext);
        try {
            Source source = (Source) sourceImpl;
            languageContext.checkAccess(null);
            languageContext.ensureInitialized(null);
            CallTarget target = languageContext.parseCached(null, source, null);
            Object result = target.call(PolyglotImpl.EMPTY_ARGS);
            Value hostValue;
            try {
                hostValue = languageContext.asValue(result);
            } catch (NullPointerException | ClassCastException e) {
                throw new AssertionError(String.format("Language %s returned an invalid return value %s. Must be an interop value.", languageId, result), e);
            }
            if (source.isInteractive()) {
                printResult(languageContext, result);
            }
            return hostValue;
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    private PolyglotLanguage requirePublicLanguage(String languageId) {
        PolyglotLanguage language = engine.idToLanguage.get(languageId);
        if (language == null || language.cache.isInternal()) {
            engine.requirePublicLanguage(languageId); // will trigger the error
            assert false;
            return null;
        }
        return language;
    }

    @TruffleBoundary
    static void printResult(PolyglotLanguageContext languageContext, Object result) {
        if (!LANGUAGE.isVisible(languageContext.env, result)) {
            return;
        }
        String stringResult;
        try {
            stringResult = UNCACHED.asString(UNCACHED.toDisplayString(languageContext.getLanguageView(result), true));
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
        try {
            OutputStream out = languageContext.context.config.out;
            out.write(stringResult.getBytes(StandardCharsets.UTF_8));
            out.write(System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8));
        } catch (IOException ioex) {
            // out stream has problems.
            throw new IllegalStateException(ioex);
        }
    }

    private static boolean isCurrentEngineHostCallback(PolyglotEngineImpl engine) {
        RootNode topMostGuestToHostRootNode = Truffle.getRuntime().iterateFrames((f) -> {
            RootNode root = ((RootCallTarget) f.getCallTarget()).getRootNode();
            if (EngineAccessor.HOST.isGuestToHostRootNode(root)) {
                return root;
            }
            return null;
        });
        if (topMostGuestToHostRootNode == null) {
            return false;
        } else {
            PolyglotEngineImpl rootEngine = (PolyglotEngineImpl) EngineAccessor.NODES.getPolyglotEngine(topMostGuestToHostRootNode);
            if (rootEngine == engine) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Embedder close.
     */
    public void close(boolean cancelIfExecuting) {
        try {
            if (isActive(Thread.currentThread()) && !isCurrentEngineHostCallback(engine)) {
                clearExplicitContextStack();
            }

            if (cancelIfExecuting) {
                /*
                 * Cancel does invalidate. We always need to invalidate before force-closing a
                 * context that might be active in other threads.
                 */
                cancel(false, null);
            } else {
                closeAndMaybeWait(false, null);
            }
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(getHostContext(), t, false);
        }
    }

    void cancel(boolean resourceLimit, String message) {
        if (!state.isClosed()) {
            List<Future<Void>> futures = setCancelling(resourceLimit, message == null ? "Context execution was cancelled." : message);
            closeHereOrCancelInCleanupThread(futures);
        }
    }

    private void closeAndMaybeWait(boolean force, List<Future<Void>> futures) {
        if (force) {
            PolyglotEngineImpl.cancel(this, futures);
        } else {
            boolean closeCompleted = closeImpl(true);
            if (!closeCompleted) {
                throw PolyglotEngineException.illegalState(String.format("The context is currently executing on another thread. " +
                                "Set cancelIfExecuting to true to stop the execution on this thread."));
            }
        }
        finishCleanup();
        checkSubProcessFinished();
        if (engine.boundEngine && parent == null) {
            engine.ensureClosed(force, false);
        }
    }

    private void setState(State targetState) {
        assert Thread.holdsLock(this);
        assert isTransitionAllowed(state, targetState);
        state = targetState;
        notifyAll();
    }

    private List<Future<Void>> setInterrupting() {
        assert Thread.holdsLock(this);
        State targetState;
        List<Future<Void>> futures = new ArrayList<>();
        if (!state.isInterrupting() && !state.isInvalidOrClosed()) {
            switch (state) {
                case CLOSING:
                    targetState = State.CLOSING_INTERRUPTING;
                    break;
                default:
                    targetState = State.INTERRUPTING;
                    break;
            }
            setState(targetState);
            setCachedThreadInfo(PolyglotThreadInfo.NULL);
            futures.add(threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, new InterruptThreadLocalAction(), true));
        }
        return futures;
    }

    private void unsetInterrupting() {
        assert Thread.holdsLock(this);
        if (state.isInterrupting()) {
            State targetState;
            switch (state) {
                case CLOSING_INTERRUPTING:
                    targetState = State.CLOSING;
                    break;
                default:
                    targetState = State.DEFAULT;
                    break;
            }
            setState(targetState);
        }
    }

    private void finishInterruptForChildContexts() {
        PolyglotContextImpl[] childContextsToInterrupt;
        synchronized (this) {
            unsetInterrupting();
            childContextsToInterrupt = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
        }
        for (PolyglotContextImpl childCtx : childContextsToInterrupt) {
            childCtx.finishInterruptForChildContexts();
        }
    }

    List<Future<Void>> interruptChildContexts() {
        PolyglotContextImpl[] childContextsToInterrupt = null;
        List<Future<Void>> futures;
        synchronized (this) {
            PolyglotThreadInfo info = getCurrentThreadInfo();
            if (info != PolyglotThreadInfo.NULL && info.isActive()) {
                throw PolyglotEngineException.illegalState("Cannot interrupt context from a thread where its child context is active.");
            }
            futures = new ArrayList<>(setInterrupting());
            if (!futures.isEmpty()) {
                childContextsToInterrupt = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
            }
        }
        if (childContextsToInterrupt != null) {
            for (PolyglotContextImpl childCtx : childContextsToInterrupt) {
                futures.addAll(childCtx.interruptChildContexts());
            }
        }
        return futures;
    }

    public boolean interrupt(Duration timeout) {
        try {
            if (parent != null) {
                throw PolyglotEngineException.illegalState("Cannot interrupt inner context separately.");
            }
            long startMillis = System.currentTimeMillis();
            PolyglotContextImpl[] childContextsToInterrupt = null;
            /*
             * Two interrupt operations cannot be simultaneously in progress in the whole context
             * hierarchy. Inner contexts cannot use interrupt separately and outer context use
             * exclusive lock.
             */
            interruptingLock.lock();
            try {
                List<Future<Void>> futures;
                synchronized (this) {
                    if (state.isClosed()) {
                        // already closed
                        return true;
                    }
                    PolyglotThreadInfo info = getCurrentThreadInfo();
                    if (info != PolyglotThreadInfo.NULL && info.isActive()) {
                        throw PolyglotEngineException.illegalState("Cannot interrupt context from a thread where the context is active.");
                    }
                    futures = new ArrayList<>(setInterrupting());
                    if (!futures.isEmpty()) {
                        childContextsToInterrupt = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
                    }
                }

                if (childContextsToInterrupt != null) {
                    for (PolyglotContextImpl childCtx : childContextsToInterrupt) {
                        futures.addAll(childCtx.interruptChildContexts());
                    }
                }

                /*
                 * No matter whether we successfully transitioned into one of the interrupting
                 * states, we wait for threads to be completed (which is done as a part of the
                 * cancel method) as the states that override interrupting states also lead to
                 * threads being stopped. If that happens before the timeout, the interrupt is
                 * successful.
                 */
                return PolyglotEngineImpl.cancelOrInterrupt(this, futures, startMillis, timeout);
            } finally {
                try {
                    if (childContextsToInterrupt != null) {
                        PolyglotContextImpl[] childContextsToFinishInterrupt;
                        synchronized (this) {
                            unsetInterrupting();
                            childContextsToFinishInterrupt = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
                        }
                        for (PolyglotContextImpl childCtx : childContextsToFinishInterrupt) {
                            childCtx.finishInterruptForChildContexts();
                        }
                    }
                } finally {
                    interruptingLock.unlock();
                }
            }
        } catch (Throwable thr) {
            throw PolyglotImpl.guestToHostException(engine, thr);
        }
    }

    public Value asValue(Object hostValue) {
        PolyglotLanguageContext languageContext = this.getHostContext();
        Object prev = hostEnter(languageContext);
        try {
            checkClosed();
            PolyglotLanguageContext targetLanguageContext;
            if (hostValue instanceof Value) {
                // fast path for when no context migration is necessary
                PolyglotLanguageContext valueContext = (PolyglotLanguageContext) getAPIAccess().getContext((Value) hostValue);
                if (valueContext != null && valueContext.context == this) {
                    return (Value) hostValue;
                }
                targetLanguageContext = languageContext;
            } else if (PolyglotWrapper.isInstance(hostValue)) {
                // host wrappers can nicely reuse the associated context
                targetLanguageContext = PolyglotWrapper.asInstance(hostValue).getLanguageContext();
                if (this != targetLanguageContext.context) {
                    // this will fail later in toGuestValue when migrating
                    // or succeed in case of host languages.
                    targetLanguageContext = languageContext;
                }
            } else {
                targetLanguageContext = languageContext;
            }
            return targetLanguageContext.asValue(toGuestValue(hostValue));
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(this.getHostContext(), e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    Object toGuestValue(Object hostValue) {
        if (hostValue instanceof Value) {
            Value receiverValue = (Value) hostValue;
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) getAPIAccess().getContext(receiverValue);
            PolyglotContextImpl valueContext = languageContext != null ? languageContext.context : null;
            Object valueReceiver = getAPIAccess().getReceiver(receiverValue);
            if (valueContext != this) {
                valueReceiver = this.migrateValue(valueReceiver, valueContext);
            }
            return valueReceiver;
        } else if (PolyglotWrapper.isInstance(hostValue)) {
            return migrateHostWrapper(PolyglotWrapper.asInstance(hostValue));
        } else {
            return engine.host.toGuestValue(getHostContextImpl(), hostValue);
        }
    }

    boolean waitForThreads(long startMillis, long timeoutMillis) {
        synchronized (this) {
            long timeElapsed = System.currentTimeMillis() - startMillis;
            boolean otherThreadActive;
            while ((otherThreadActive = hasActiveOtherThread(true)) && (timeoutMillis == 0 || timeElapsed < timeoutMillis)) {
                try {
                    if (timeoutMillis == 0) {
                        wait();
                    } else {
                        wait(timeoutMillis - timeElapsed);
                    }
                } catch (InterruptedException e) {
                }
                timeElapsed = System.currentTimeMillis() - startMillis;
            }
            /*
             * hasActiveOtherThread is racy. E.g. one of the threads might be just about to enter
             * via fast path and so hasActiveOtherThread might return a different result if we
             * executed it again after the while loop. The fast-path enter might not go through in
             * the end, especially if this is waiting for cancellation of all threads, so it is not
             * a problem that hasActiveOtherThread is racy, but it is important that waitForThreads
             * does not return a wrong value. That is why we store the result in a boolean so that
             * the returned value corresponds to the reason why the while loop has ended.
             */
            return !otherThreadActive;
        }
    }

    boolean isSingleThreaded() {
        return singleThreaded;
    }

    Map<Thread, PolyglotThreadInfo> getSeenThreads() {
        assert Thread.holdsLock(this);
        return threads;
    }

    private boolean isActiveNotCancelled() {
        return isActiveNotCancelled(true);
    }

    synchronized boolean isActiveNotCancelled(boolean includePolyglotThreads) {
        for (PolyglotThreadInfo seenTinfo : threads.values()) {
            if ((includePolyglotThreads || !seenTinfo.isPolyglotThread(this)) && seenTinfo.isActiveNotCancelled()) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean isActive() {
        for (PolyglotThreadInfo seenTinfo : threads.values()) {
            if (seenTinfo.isActive()) {
                return true;
            }
        }
        return false;
    }

    synchronized boolean isActive(Thread thread) {
        PolyglotThreadInfo info = threads.get(thread);
        if (info == null || info == PolyglotThreadInfo.NULL) {
            return false;
        }
        return info.isActive();
    }

    private PolyglotThreadInfo getFirstActiveOtherThread(boolean includePolyglotThreads) {
        assert Thread.holdsLock(this);
        // send enters and leaves into a lock by setting the lastThread to null.
        for (PolyglotThreadInfo otherInfo : threads.values()) {
            if (!includePolyglotThreads && otherInfo.isPolyglotThread(this)) {
                continue;
            }
            if (!otherInfo.isCurrent() && otherInfo.isActive()) {
                return otherInfo;
            }
        }
        return null;
    }

    boolean hasActiveOtherThread(boolean includePolyglotThreads) {
        return getFirstActiveOtherThread(includePolyglotThreads) != null;
    }

    private void notifyThreadClosed(PolyglotThreadInfo info) {
        assert Thread.holdsLock(this);
        if (!info.cancelled) {
            // clear interrupted status after closingThread
            // needed because we interrupt when closingThread from another thread.
            info.cancelled = true;
            Thread.interrupted();
        }
        notifyAll();
    }

    long calculateHeapSize(long stopAtBytes, AtomicBoolean calculationCancelled) {
        try {
            ObjectSizeCalculator localObjectSizeCalculator;
            synchronized (this) {
                localObjectSizeCalculator = objectSizeCalculator;
                if (localObjectSizeCalculator == null) {
                    localObjectSizeCalculator = new ObjectSizeCalculator();
                    objectSizeCalculator = localObjectSizeCalculator;
                }
            }
            return localObjectSizeCalculator.calculateObjectSize(getContextHeapRoots(), stopAtBytes, calculationCancelled);
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("Polyglot context heap size calculation is not supported on current Truffle runtime.", e);
        }
    }

    private Object[] getContextHeapRoots() {
        List<Object> heapRoots = new ArrayList<>();
        addRootPointersForContext(heapRoots);
        addRootPointersForStackFrames(heapRoots);
        return heapRoots.toArray();
    }

    private void addRootPointersForStackFrames(List<Object> heapRoots) {
        FrameInstance[][] frameInstances = PolyglotStackFramesRetriever.getStackFrames(this);
        for (int i = 0; i < frameInstances.length; i++) {
            for (int j = 0; j < frameInstances[i].length; j++) {
                heapRoots.add(frameInstances[i][j].getFrame(FrameInstance.FrameAccess.READ_ONLY));
            }
        }
    }

    private void addRootPointersForContext(List<Object> heapRoots) {
        synchronized (this) {
            for (PolyglotLanguageContext context : contexts) {
                if (context.isCreated()) {
                    heapRoots.add(context.getContextImpl());
                }
            }
            if (polyglotBindings != null) {
                for (Map.Entry<String, Value> binding : polyglotBindings.entrySet()) {
                    heapRoots.add(binding.getKey());
                    if (binding.getValue() != null) {
                        heapRoots.add(getAPIAccess().getReceiver(binding.getValue()));
                    }
                }
            }
        }
        heapRoots.add(contextLocals);
        PolyglotContextImpl[] childContextStartPoints;
        synchronized (this) {
            for (PolyglotThreadInfo info : threads.values()) {
                heapRoots.add(info.getContextThreadLocals());
            }
            childContextStartPoints = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
        }
        for (PolyglotContextImpl childCtx : childContextStartPoints) {
            childCtx.addRootPointersForContext(heapRoots);
        }
    }

    private List<Future<Void>> setCancelling(boolean resourceLimit, String message) {
        assert message != null;
        PolyglotContextImpl[] childContextsToCancel = null;
        List<Future<Void>> futures = new ArrayList<>();
        synchronized (this) {
            if (!state.isInvalidOrClosed()) {
                State targetState;
                if (state.isClosing()) {
                    targetState = State.CLOSING_CANCELLING;
                } else {
                    targetState = State.CANCELLING;
                }
                invalidResourceLimit = resourceLimit;
                invalidMessage = message;
                setState(targetState);
                PolyglotThreadInfo info = getCurrentThreadInfo();
                futures.add(threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, new CancellationThreadLocalAction(), true));
                if (info != PolyglotThreadInfo.NULL) {
                    info.cancelled = true;
                    Thread.interrupted();
                }
                setCachedThreadInfo(PolyglotThreadInfo.NULL);
                childContextsToCancel = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
            }
        }
        if (childContextsToCancel != null) {
            for (PolyglotContextImpl childCtx : childContextsToCancel) {
                futures.addAll(childCtx.setCancelling(resourceLimit, message));
            }
        }
        return futures;
    }

    private void setClosingState() {
        assert Thread.holdsLock(this);
        closingThread = Thread.currentThread();
        closingLock.lock();
        State targetState;
        switch (state) {
            case CANCELLING:
                targetState = State.CLOSING_CANCELLING;
                break;
            case INTERRUPTING:
                targetState = State.CLOSING_INTERRUPTING;
                break;
            default:
                targetState = State.CLOSING;
                break;
        }
        setState(targetState);
    }

    private void setClosedState() {
        assert Thread.holdsLock(this);
        assert state.isClosing() : state.name();
        State targetState;
        switch (state) {
            case CLOSING_CANCELLING:
                targetState = State.CLOSED_CANCELLED;
                break;
            case CLOSING_INTERRUPTING:
            case CLOSING:
                targetState = State.CLOSED;
                break;
            default:
                throw new IllegalStateException("Cannot close polyglot context in the current state!");
        }
        setState(targetState);
        assert state.isClosed() : state.name();
    }

    private void restoreFromClosingState(boolean cancelOperation) {
        assert Thread.holdsLock(this);
        assert state.isClosing() : state.name();
        State targetState;
        assert !cancelOperation : "Close initiated for an invalid context must not fail!";
        switch (state) {
            case CLOSING_INTERRUPTING:
                targetState = State.INTERRUPTING;
                break;
            case CLOSING_CANCELLING:
                targetState = State.CANCELLING;
                break;
            default:
                targetState = State.DEFAULT;
                break;
        }
        setState(targetState);
    }

    @SuppressWarnings({"fallthrough"})
    @SuppressFBWarnings("UL_UNRELEASED_LOCK_EXCEPTION_PATH")
    boolean closeImpl(boolean notifyInstruments) {

        /*
         * Close operation initiated in the DEFAULT or INTERRUPTING state can fail in which case the
         * context will go back to the corresponsing non-closing e.g. DEFAULT -> CLOSING -> DEFAULT.
         * Please note that while the default close is in progress, i.e. the context state is in
         * CLOSING or CLOSING_INTERRUPTING state, the state can be overriden by CLOSING_CANCELLING.
         * Even in this case the default close can still fail and if that is the case the context
         * state goes back to CANCELLING. The close operation is then guaranteed to be completed by
         * the process that initiated cancel.
         *
         * This block performs the following checks:
         *
         * 1) The close was already performed on another thread -> return true
         *
         * 2) The close is currently already being performed on this thread -> return true
         *
         * 3) The close is currently being performed on another thread -> wait for the other thread
         * to finish closing and start checking again from check 1).
         *
         * 4) The close was not yet performed, cancelling or executing is not in progress, but other
         * threads are still executing -> return false
         *
         * 5) The close was not yet performed and cancelling is in progress -> wait for other
         * threads to complete and start checking again from check 1) skipping check 5) (this check)
         * as no other threads can be executing anymore.
         *
         * 6) The close was not yet performed and no thread is executing -> perform close
         */
        boolean waitForClose = false;
        boolean finishCancel = false;
        boolean cancelOperation;
        acquireClosingLock: while (true) {
            if (waitForClose) {
                closingLock.lock();
                closingLock.unlock();
                waitForClose = false;
            }
            synchronized (this) {
                switch (state) {
                    case CLOSED:
                    case CLOSED_CANCELLED:
                        return true;
                    case CLOSING:
                    case CLOSING_INTERRUPTING:
                    case CLOSING_CANCELLING:
                        assert closingThread != null;
                        if (closingThread == Thread.currentThread()) {
                            // currently closing recursively -> just complete
                            return true;
                        } else {
                            // currently closing on another thread -> wait for other thread to
                            // complete closing
                            waitForClose = true;
                            continue acquireClosingLock;
                        }
                    case CANCELLING:
                        assert cachedThreadInfo == PolyglotThreadInfo.NULL;
                        /*
                         * When cancelling, we have to wait for all other threads to complete - even
                         * for the the default close, otherwise the default close executed
                         * prematurely as the result of leaving the context on the main thread due
                         * to cancel exception could fail because of other threads still being
                         * active. The correct behavior is that the normal close finishes
                         * successfully and the cancel exception spreads further (if not caught
                         * before close is executed).
                         */
                        if (!finishCancel) {
                            waitForThreads(0, 0);
                            waitForClose = true;
                            finishCancel = true;
                            /*
                             * During wait this thread didn't hold the polyglot context lock, so
                             * some other thread might have acquired closingLock in the meantime. In
                             * that case it wouldn't be possible to acquire the closingLock by this
                             * thread in the current synchronized block, because the thread that
                             * holds it might need to acquire the context lock before releasing the
                             * closingLock, but the context lock is held by this thread, and so we
                             * have to exit the synchronized block and try again.
                             */
                            continue acquireClosingLock;
                        }
                        /*
                         * Just continue with the close if we have already waited for threads in the
                         * previous iteration of the main loop. We cannot wait for the close to be
                         * completed by the thread that executes cancelling, because it might be
                         * waiting for this thread which would lead to a deadlock. Default close is
                         * allowed to be executed when entered. Also, this might be an inner
                         * context, which, even if not entered, might block a parent's thread which
                         * could be entered on the current thread.
                         */
                        setClosingState();
                        cancelOperation = true;
                        break acquireClosingLock;
                    case INTERRUPTING:
                    case DEFAULT:
                        // triggers a thread changed event which requires slow path enter
                        setCachedThreadInfo(PolyglotThreadInfo.NULL);
                        if (hasActiveOtherThread(false)) {
                            /*
                             * We are not done executing, cannot close yet.
                             */
                            return false;
                        }
                        setClosingState();
                        cancelOperation = false;
                        break acquireClosingLock;
                    default:
                        assert false;
                }
            }
        }

        return finishClose(cancelOperation, notifyInstruments);
    }

    synchronized void clearExplicitContextStack() {
        PolyglotThreadInfo threadInfo = getCurrentThreadInfo();
        if (!threadInfo.explicitContextStack.isEmpty()) {
            PolyglotContextImpl c = this;
            while (!threadInfo.explicitContextStack.isEmpty()) {
                if (PolyglotFastThreadLocals.getContext(getEngine()) == this) {
                    Object[] prev = threadInfo.explicitContextStack.removeLast();
                    engine.leave(prev, c);
                    c = prev != null ? (PolyglotContextImpl) prev[PolyglotFastThreadLocals.CONTEXT_INDEX] : null;
                } else {
                    throw PolyglotEngineException.illegalState("Unable to automatically leave an explicitly entered context, some other context was entered in the meantime.");
                }
            }
        }
    }

    private boolean finishClose(boolean cancelOperation, boolean notifyInstruments) {
        /*
         * If we reach here then we can continue with the close. This means that no other concurrent
         * close is running and no other thread is currently executing. Note that only the context
         * and closing lock should be acquired in this area to avoid deadlocks.
         */
        Thread[] remainingThreads = null;
        List<PolyglotLanguageContext> disposedContexts = null;
        boolean success = false;
        try {
            assert closingThread == Thread.currentThread();
            assert closingLock.isHeldByCurrentThread() : "lock is acquired";
            assert !state.isClosed();
            Object[] prev;
            try {
                prev = this.enterThreadChanged(false, false, !cancelOperation, cancelOperation);
            } catch (Throwable t) {
                synchronized (this) {
                    restoreFromClosingState(cancelOperation);
                }
                throw t;
            }
            if (cancelOperation) {
                synchronized (this) {
                    /*
                     * Cancellation thread local action needs to be submitted here in case
                     * finalizeContext runs guest code.
                     */
                    threadLocalActions.submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, new CancellationThreadLocalAction(), true);
                }
            }
            try {
                closeChildContexts(notifyInstruments);

                finalizeContext(notifyInstruments, cancelOperation);

                // finalization performed commit close -> no reinitialization allowed

                disposedContexts = disposeContext();

                success = true;
            } finally {
                synchronized (this) {
                    /*
                     * The assert is synchronized because all accesses to childContexts must be
                     * synchronized.
                     */
                    assert !success || childContextsClosed() : "Polyglot context close marked as successful, but there are unclosed child contexts.";
                    this.leaveThreadChanged(prev, false, true);
                    if (success) {
                        remainingThreads = threads.keySet().toArray(new Thread[0]);
                    }
                    if (success) {
                        setClosedState();
                    } else {
                        restoreFromClosingState(cancelOperation);
                    }
                    disposing = false;
                    // triggers a thread changed event which requires slow path enter
                    setCachedThreadInfo(PolyglotThreadInfo.NULL);
                }
            }
        } finally {
            synchronized (this) {
                assert !state.isClosing();
                closingThread = null;
                closingLock.unlock();
            }
        }

        /*
         * No longer any lock is held. So we can acquire other locks to cleanup.
         */
        for (PolyglotLanguageContext context : disposedContexts) {
            context.notifyDisposed(notifyInstruments);
        }

        if (success) {
            try {
                /*
                 * We need to notify before we remove the context from engine's context list,
                 * otherwise we couldn't use context locals in the context closed notification. New
                 * instrument introducting new context locals doesn't initialize them in a context
                 * if it's not in the engine's context list.
                 */
                if (notifyInstruments) {
                    for (Thread thread : remainingThreads) {
                        EngineAccessor.INSTRUMENT.notifyThreadFinished(engine, creatorTruffleContext, thread);
                    }
                    EngineAccessor.INSTRUMENT.notifyContextClosed(engine, creatorTruffleContext);
                }
            } finally {
                if (parent != null) {
                    synchronized (parent) {
                        parent.childContexts.remove(this);
                    }
                } else if (notifyInstruments) {
                    engine.disposeContext(this);
                }
            }
            synchronized (this) {
                // sends all threads to do slow-path enter/leave
                setCachedThreadInfo(PolyglotThreadInfo.NULL);
                /*
                 * If we are closing from within an entered thread, we cannot clear locals as they
                 * might be needed in e.g. onLeaveThread events.
                 */
                if (!isActive(Thread.currentThread())) {
                    threadLocalActions.notifyContextClosed();

                    if (contexts != null) {
                        for (PolyglotLanguageContext langContext : contexts) {
                            langContext.close();
                        }
                    }
                    if (contextLocals != null) {
                        Arrays.fill(contextLocals, null);
                    }
                    for (PolyglotThreadInfo thread : threads.values()) {
                        Object[] threadLocals = thread.getContextThreadLocals();
                        if (threadLocals != null) {
                            Arrays.fill(threadLocals, null);
                        }
                        PolyglotFastThreadLocals.cleanup(thread.fastThreadLocals);
                    }
                    localsCleared = true;
                }
            }
            if (parent == null) {
                if (!this.config.logLevels.isEmpty()) {
                    EngineAccessor.LANGUAGE.configureLoggers(this, null, getAllLoggers());
                }
                if (this.config.logHandler != null && !PolyglotLoggers.isSameLogSink(this.config.logHandler, engine.logHandler)) {
                    this.config.logHandler.close();
                }
            }
        }
        return true;
    }

    /**
     * Used in assertion only. We cannot simply assert that childContexts are empty, because
     * removing the child context from its parent childContexts list can be done in another thread
     * after the assertion.
     */
    private boolean childContextsClosed() {
        assert Thread.holdsLock(this);
        for (PolyglotContextImpl childCtx : childContexts) {
            if (!childCtx.state.isClosed()) {
                return false;
            }
        }
        return true;
    }

    private void closeChildContexts(boolean notifyInstruments) {
        PolyglotContextImpl[] childrenToClose;
        synchronized (this) {
            childrenToClose = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
        }
        for (PolyglotContextImpl childContext : childrenToClose) {
            childContext.closeImpl(notifyInstruments);
        }
    }

    private void closeHereOrCancelInCleanupThread(List<Future<Void>> futures) {
        boolean cancelInSeparateThread = false;
        synchronized (this) {
            PolyglotThreadInfo info = getCurrentThreadInfo();
            if (info.isPolyglotThread(this) || (!singleThreaded && isActive(Thread.currentThread()))) {
                /*
                 * Polyglot thread must not cancel a context, because cancel waits for polyglot
                 * threads to complete. Also, it is not allowed to cancel in a thread where a
                 * multi-threaded context is entered. This would lead to deadlock if more than one
                 * thread tried to do that as cancel waits for the context not to be entered in all
                 * other threads.
                 */
                cancelInSeparateThread = true;
            }
        }
        if (cancelInSeparateThread) {
            if (!futures.isEmpty()) {
                /*
                 * Checking the futures for emptiness makes sure we don't register multiple cleanup
                 * tasks if this is called from multiple threads
                 */
                registerCleanupTask(new Runnable() {
                    @Override
                    public void run() {
                        PolyglotEngineImpl.cancel(PolyglotContextImpl.this, futures);
                    }
                });
            }
        } else {
            closeAndMaybeWait(true, futures);
        }
    }

    private void registerCleanupTask(Runnable cleanupTask) {
        synchronized (this) {
            if (cleanupExecutorService == null) {
                cleanupExecutorService = Executors.newFixedThreadPool(1, new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r);
                        t.setDaemon(true);
                        return t;
                    }
                });
            }
            assert cleanupFuture == null : "Multiple cleanup tasks are currently not supported!";
            cleanupFuture = cleanupExecutorService.submit(cleanupTask);
        }
    }

    @SuppressWarnings("deprecation")
    void finishCleanup() {
        ExecutorService localCleanupService;
        synchronized (this) {
            if (isActive(Thread.currentThread())) {
                /*
                 * The cleanup must be able to wait for the context to leave all threads which would
                 * be impossible if it is still entered in the current thread.
                 */
                return;
            }
            localCleanupService = cleanupExecutorService;
        }
        if (localCleanupService != null) {
            try {
                try {
                    cleanupFuture.get();
                } catch (InterruptedException ie) {
                    engine.getEngineLogger().log(Level.INFO, "Waiting for polyglot context cleanup was interrupted!", ie);
                } catch (ExecutionException ee) {
                    assert !(ee.getCause() instanceof com.oracle.truffle.api.TruffleException);
                    throw sneakyThrow(ee.getCause());
                }
            } finally {
                localCleanupService.shutdownNow();
                while (!localCleanupService.isTerminated()) {
                    try {
                        if (!localCleanupService.awaitTermination(1, TimeUnit.MINUTES)) {
                            throw new IllegalStateException("Context cleanup service timeout!");
                        }
                    } catch (InterruptedException ie) {
                        engine.getEngineLogger().log(Level.INFO, "Waiting for polyglot context cleanup was interrupted!", ie);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    private List<PolyglotLanguageContext> disposeContext() {
        assert !this.disposing;
        this.disposing = true;
        List<PolyglotLanguageContext> disposedContexts = new ArrayList<>(contexts.length);
        Closeable[] toClose;
        synchronized (this) {
            for (int i = contexts.length - 1; i >= 0; i--) {
                PolyglotLanguageContext context = contexts[i];
                boolean disposed = context.dispose();
                if (disposed) {
                    disposedContexts.add(context);
                }
            }
            toClose = closeables == null ? null : closeables.toArray(new Closeable[0]);
        }
        if (toClose != null) {
            for (Closeable closeable : toClose) {
                try {
                    closeable.close();
                } catch (IOException ioe) {
                    engine.getEngineLogger().log(Level.WARNING, "Failed to close " + closeable, ioe);
                }
            }
        }
        return disposedContexts;
    }

    private void finalizeContext(boolean notifyInstruments, boolean cancelOperation) {
        // we need to run finalization at least twice in case a finalization run has
        // initialized a new contexts
        boolean finalizationPerformed;
        do {
            finalizationPerformed = false;
            // inverse context order is already the right order for context
            // disposal/finalization
            for (int i = contexts.length - 1; i >= 0; i--) {
                PolyglotLanguageContext context = contexts[i];
                if (context.isInitialized()) {
                    finalizationPerformed |= context.finalizeContext(cancelOperation, notifyInstruments);
                }
            }
        } while (finalizationPerformed);
    }

    synchronized void sendInterrupt() {
        if (!state.isInterrupting() && !state.isCancelling()) {
            return;
        }
        for (PolyglotThreadInfo threadInfo : threads.values()) {
            if (!threadInfo.isCurrent() && threadInfo.isActiveNotCancelled()) {
                /*
                 * We send an interrupt to the thread to wake up and to run some guest language code
                 * in case they are waiting in some async primitive. The interrupt is then cleared
                 * when the closed is performed.
                 */
                threadInfo.getThread().interrupt();
            }
        }
    }

    Object getLocal(LocalLocation l) {
        assert l.engine == this.engine : invalidSharingError(this.engine, l.engine);
        return l.readLocal(this, this.contextLocals, false);
    }

    private Object[] getThreadLocals(Thread thread) {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo threadInfo = threads.get(thread);
        if (threadInfo == null) {
            return null;
        }
        return threadInfo.getContextThreadLocals();
    }

    /*
     * Reading from a different thread than the current thread requires synchronization. as
     * threadIdToThreadLocal and threadLocals are always updated on the current thread under the
     * context lock.
     */
    @TruffleBoundary
    synchronized Object getThreadLocal(LocalLocation l, Thread t) {
        assert l.engine == this.engine : invalidSharingError(this.engine, l.engine);
        Object[] threadLocals = getThreadLocals(t);
        if (threadLocals == null) {
            return null;
        }
        return l.readLocal(this, threadLocals, true);
    }

    void initializeThreadLocals(PolyglotThreadInfo threadInfo) {
        assert Thread.holdsLock(this);
        assert Thread.currentThread() == threadInfo.getThread() : "thread locals must only be initialized on the current thread";

        StableLocalLocations locations = engine.contextThreadLocalLocations;
        Object[] locals = new Object[locations.locations.length];

        Thread thread = threadInfo.getThread();
        for (PolyglotInstrument instrument : engine.idToInstrument.values()) {
            if (instrument.isCreated()) {
                invokeContextLocalsFactory(this.contextLocals, instrument.contextLocalLocations);
                invokeContextThreadFactory(locals, instrument.contextThreadLocalLocations, thread);
            }
        }
        for (PolyglotLanguageContext language : contexts) {
            if (language.isCreated()) {
                invokeContextLocalsFactory(this.contextLocals, language.getLanguageInstance().contextLocalLocations);
                invokeContextThreadFactory(locals, language.getLanguageInstance().contextThreadLocalLocations, thread);
            }
        }
        threadInfo.setContextThreadLocals(locals);
    }

    void initializeContextLocals() {
        assert Thread.holdsLock(this);

        if (this.contextLocals != null) {
            // Could have already been populated by resizeContextLocals.
            return;
        }

        StableLocalLocations locations = engine.contextLocalLocations;
        Object[] locals = new Object[locations.locations.length];

        for (PolyglotInstrument instrument : engine.idToInstrument.values()) {
            if (instrument.isCreated()) {
                invokeContextLocalsFactory(locals, instrument.contextLocalLocations);
            }
        }
        /*
         * Languages will be initialized in PolyglotLanguageContext#ensureCreated().
         */
        assert this.contextLocals == null;
        this.contextLocals = locals;
    }

    /**
     * Updates the current thread locals from {@link PolyglotThreadInfo#contextThreadLocals}.
     */
    synchronized Object[] updateThreadLocals() {
        assert Thread.holdsLock(this);
        Object[] newThreadLocals = getThreadLocals(Thread.currentThread());
        return newThreadLocals;
    }

    void resizeContextThreadLocals(StableLocalLocations locations) {
        assert Thread.holdsLock(this);
        for (PolyglotThreadInfo threadInfo : threads.values()) {
            Object[] threadLocals = threadInfo.getContextThreadLocals();
            if (threadLocals.length < locations.locations.length) {
                threadInfo.setContextThreadLocals(Arrays.copyOf(threadLocals, locations.locations.length));
            }
        }
    }

    void resizeContextLocals(StableLocalLocations locations) {
        Thread.holdsLock(this);
        Object[] oldLocals = this.contextLocals;
        if (oldLocals != null) {
            if (oldLocals.length > locations.locations.length) {
                throw new AssertionError("Context locals array must never shrink.");
            } else if (locations.locations.length > oldLocals.length) {
                this.contextLocals = Arrays.copyOf(oldLocals, locations.locations.length);
            }
        } else {
            this.contextLocals = new Object[locations.locations.length];
        }
    }

    void invokeContextLocalsFactory(Object[] locals, LocalLocation[] locations) {
        assert Thread.holdsLock(this);
        if (locations == null) {
            return;
        }
        try {
            for (int i = 0; i < locations.length; i++) {
                LocalLocation location = locations[i];
                if (locals[location.index] == null) {
                    locals[location.index] = location.invokeFactory(this, null);
                }
            }
        } catch (Throwable t) {
            // reset values again the language failed to initialize
            for (int i = 0; i < locations.length; i++) {
                locals[locations[i].index] = null;
            }
            throw t;
        }
    }

    void invokeContextThreadLocalFactory(LocalLocation[] locations) {
        assert Thread.holdsLock(this);
        if (locations == null) {
            return;
        }
        for (PolyglotThreadInfo threadInfo : threads.values()) {
            invokeContextThreadFactory(threadInfo.getContextThreadLocals(), locations, threadInfo.getThread());
        }
    }

    private void invokeContextThreadFactory(Object[] threadLocals, LocalLocation[] locations, Thread thread) {
        assert Thread.holdsLock(this);
        if (locations == null) {
            return;
        }
        try {
            for (int i = 0; i < locations.length; i++) {
                LocalLocation location = locations[i];
                if (threadLocals[location.index] == null) {
                    threadLocals[location.index] = location.invokeFactory(this, thread);
                }
            }
        } catch (Throwable t) {
            // reset values again the language failed to initialize
            for (int i = 0; i < locations.length; i++) {
                threadLocals[locations[i].index] = null;
            }
            throw t;
        }
    }

    static String invalidSharingError(PolyglotEngineImpl expectedEngine, PolyglotEngineImpl actualEngine) {
        return String.format("Detected invaliding sharing of context locals between polyglot engines. Expected engine %s but was %s.", expectedEngine, actualEngine);
    }

    boolean patch(PolyglotContextConfig newConfig) {
        CompilerAsserts.neverPartOfCompilation();

        this.config = newConfig;
        threadLocalActions.onContextPatch();
        if (!newConfig.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, newConfig.logLevels, getAllLoggers());
        }
        final Object[] prev = engine.enter(this);
        try {
            for (int i = 0; i < this.contexts.length; i++) {
                final PolyglotLanguageContext context = this.contexts[i];
                if (context.language.isHost()) {
                    initializeHostContext(context, newConfig);
                }
                if (!context.patch(newConfig)) {
                    return false;
                }
            }
        } finally {
            engine.leave(prev, this);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    void initializeHostContext(PolyglotLanguageContext context, PolyglotContextConfig newConfig) {
        try {
            Object contextImpl = context.getContextImpl();
            if (contextImpl == null) {
                throw new AssertionError("Host context not initialized.");
            }
            this.hostContextImpl = contextImpl;

            AbstractHostService currentHost = engine.host;
            AbstractHostService newHost = context.lookupService(AbstractHostService.class);
            if (newHost == null) {
                throw new AssertionError("The engine host language must register a service of type:" + AbstractHostService.class);
            }
            if (currentHost == null) {
                engine.host = newHost;
            } else if (currentHost != newHost) {
                throw new AssertionError("Host service must not change per engine.");
            }
            newHost.initializeHostContext(this, contextImpl, newConfig.hostAccess, newConfig.hostClassLoader, newConfig.classFilter, newConfig.hostClassLoadingAllowed,
                            newConfig.hostLookupAllowed);
        } catch (IllegalStateException e) {
            throw PolyglotEngineException.illegalState(e.getMessage());
        }
    }

    void replayInstrumentationEvents() {
        notifyContextCreated();
        for (PolyglotLanguageContext lc : contexts) {
            LanguageInfo language = lc.language.info;
            if (lc.eventsEnabled && lc.env != null) {
                EngineAccessor.INSTRUMENT.notifyLanguageContextCreate(this, creatorTruffleContext, language);
                EngineAccessor.INSTRUMENT.notifyLanguageContextCreated(this, creatorTruffleContext, language);
                if (lc.isInitialized()) {
                    EngineAccessor.INSTRUMENT.notifyLanguageContextInitialize(this, creatorTruffleContext, language);
                    EngineAccessor.INSTRUMENT.notifyLanguageContextInitialized(this, creatorTruffleContext, language);
                    if (lc.finalized) {
                        EngineAccessor.INSTRUMENT.notifyLanguageContextFinalized(this, creatorTruffleContext, language);
                    }
                }
            }
        }
    }

    synchronized void checkSubProcessFinished() {
        ProcessHandlers.ProcessDecorator[] processes = subProcesses.toArray(new ProcessHandlers.ProcessDecorator[subProcesses.size()]);
        for (ProcessHandlers.ProcessDecorator process : processes) {
            if (process.isAlive()) {
                throw PolyglotEngineException.illegalState(String.format("The context has an alive sub-process %s created by %s.",
                                process.getCommand(), process.getOwner().language.getId()));
            }
        }
    }

    static PolyglotContextImpl preInitialize(final PolyglotEngineImpl engine) {
        final FileSystems.PreInitializeContextFileSystem fs = new FileSystems.PreInitializeContextFileSystem();
        final FileSystems.PreInitializeContextFileSystem internalFs = new FileSystems.PreInitializeContextFileSystem();
        EconomicSet<String> allowedLanguages = EconomicSet.create();
        allowedLanguages.addAll(engine.getLanguages().keySet());
        final PolyglotContextConfig config = new PolyglotContextConfig(engine,
                        System.out,
                        System.err,
                        System.in,
                        false,
                        PolyglotAccess.ALL, // TODO change this to NONE with GR-14657
                        false,
                        false,
                        false,
                        false,
                        null,
                        Collections.emptyMap(),
                        allowedLanguages,
                        Collections.emptyMap(),
                        fs, internalFs, engine.logHandler, false, null,
                        EnvironmentAccess.INHERIT, null, null, null, null, null, true);

        final PolyglotContextImpl context = new PolyglotContextImpl(engine, config);
        synchronized (engine.lock) {
            engine.addContext(context);
        }
        try {
            synchronized (context) {
                context.initializeContextLocals();
            }
            context.sourcesToInvalidate = new ArrayList<>();
            final String oldOption = engine.engineOptionValues.get(PolyglotEngineOptions.PreinitializeContexts);
            final String newOption = ImageBuildTimeOptions.get(ImageBuildTimeOptions.PREINITIALIZE_CONTEXTS_NAME);
            final String optionValue;
            if (!oldOption.isEmpty() && !newOption.isEmpty()) {
                optionValue = oldOption + "," + newOption;
            } else {
                optionValue = oldOption + newOption;
            }

            final Set<String> languagesToPreinitialize = new HashSet<>();
            if (!optionValue.isEmpty()) {
                Collections.addAll(languagesToPreinitialize, optionValue.split(","));
            }
            for (PolyglotLanguage language : engine.idToLanguage.values()) {
                if (!language.isFirstInstance()) {
                    languagesToPreinitialize.add(language.getId());
                }
            }

            if (!languagesToPreinitialize.isEmpty()) {
                context.inContextPreInitialization = true;
                try {
                    Object[] prev = context.engine.enter(context);
                    try {
                        for (String languageId : engine.getLanguages().keySet()) {
                            if (languagesToPreinitialize.contains(languageId)) {
                                PolyglotLanguage language = engine.findLanguage(null, languageId, null, false, true);
                                if (language != null) {
                                    if (overridesPatchContext(languageId)) {
                                        context.getContextInitialized(language, null);
                                        LOG.log(Level.FINE, "Pre-initialized context for language: {0}", language.getId());
                                    } else {
                                        // only print warning when the context preinitialized was
                                        // configured explicitly and not through engine caching
                                        if (language.isFirstInstance()) {
                                            LOG.log(Level.WARNING, "Language {0} cannot be pre-initialized as it does not override TruffleLanguage.patchContext method.", languageId);
                                        }
                                    }
                                }
                            }
                            // Reset language options parsed during preinitialization
                            PolyglotLanguage language = engine.idToLanguage.get(languageId);
                            language.clearOptionValues();
                        }
                    } finally {
                        synchronized (context) {
                            context.leaveAndDisposeThread(prev, Thread.currentThread());
                        }
                    }
                } finally {
                    context.inContextPreInitialization = false;
                }
            }
            synchronized (context) {
                // Need to clean up Threads before storing SVM image
                context.setCachedThreadInfo(PolyglotThreadInfo.NULL);
            }
            return context;
        } finally {
            synchronized (engine.lock) {
                engine.removeContext(context);
            }
            for (Source sourceToInvalidate : context.sourcesToInvalidate) {
                EngineAccessor.SOURCE.invalidateAfterPreinitialiation(sourceToInvalidate);
            }
            context.singleThreadValue.reset();
            context.sourcesToInvalidate = null;
            context.threadLocalActions.prepareContextStore();
            fs.onPreInitializeContextEnd();
            internalFs.onPreInitializeContextEnd();
            FileSystems.resetDefaultFileSystemProvider();
            if (!config.logLevels.isEmpty()) {
                EngineAccessor.LANGUAGE.configureLoggers(context, null, context.getAllLoggers());
            }
        }
    }

    void leaveAndDisposeThread(Object[] prev, Thread thread) {
        assert Thread.holdsLock(this);
        assert Thread.currentThread() == thread;
        Map<Thread, PolyglotThreadInfo> seenThreads = getSeenThreads();
        PolyglotThreadInfo info = seenThreads.get(thread);
        if (info == null) {
            // already disposed
            return;
        }

        for (PolyglotLanguageContext languageContext : contexts) {
            if (languageContext.isInitialized()) {
                LANGUAGE.disposeThread(languageContext.env, thread);
            }
        }
        engine.leave(prev, this);
        assert !info.isActive();

        if (cachedThreadInfo.getThread() == thread) {
            setCachedThreadInfo(PolyglotThreadInfo.NULL);
        }
        info.setContextThreadLocals(DISPOSED_CONTEXT_THREAD_LOCALS);
        seenThreads.remove(thread);
    }

    Object getOrCreateContextLoggers() {
        Object res = contextBoundLoggers;
        if (res == null) {
            synchronized (this) {
                res = contextBoundLoggers;
                if (res == null) {
                    res = LANGUAGE.createEngineLoggers(PolyglotLoggers.LoggerCache.newContextLoggerCache(this));
                    if (!this.config.logLevels.isEmpty()) {
                        EngineAccessor.LANGUAGE.configureLoggers(this, this.config.logLevels, res);
                    }
                    contextBoundLoggers = res;
                }
            }
        }
        return res;
    }

    private Object[] getAllLoggers() {
        Object defaultLoggers = EngineAccessor.LANGUAGE.getDefaultLoggers();
        Object engineLoggers = engine.getEngineLoggers();
        Object contextLoggers = contextBoundLoggers;
        List<Object> allLoggers = new ArrayList<>(3);
        allLoggers.add(defaultLoggers);
        if (engineLoggers != null) {
            allLoggers.add(engineLoggers);
        }
        if (contextLoggers != null) {
            allLoggers.add(contextLoggers);
        }
        return allLoggers.toArray(new Object[allLoggers.size()]);
    }

    static class ContextWeakReference extends WeakReference<PolyglotContextImpl> {

        volatile boolean removed = false;
        final List<PolyglotLanguageInstance> freeInstances = new ArrayList<>();

        ContextWeakReference(PolyglotContextImpl referent) {
            super(referent, referent.engine.contextsReferenceQueue);
        }

    }

    private CancelExecution createCancelException(Node location) {
        return new CancelExecution(location, invalidMessage, invalidResourceLimit);
    }

    private static boolean overridesPatchContext(String languageId) {
        LanguageCache cache = LanguageCache.languages().get(languageId);
        for (Method m : cache.loadLanguage().getClass().getDeclaredMethods()) {
            if (m.getName().equals("patchContext")) {
                return true;
            }
        }
        return false;
    }

    synchronized void registerOnDispose(Closeable closeable) {
        if (disposing) {
            throw new IllegalStateException("Cannot register closeable when context is being disposed.");
        }
        if (closeables == null) {
            closeables = Collections.newSetFromMap(new WeakHashMap<>());
        }
        closeables.add(Objects.requireNonNull(closeable));
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("PolyglotContextImpl[");
        b.append("state=");
        State localState = state;
        b.append(localState.name());
        if (!localState.isClosed()) {
            if (isActive()) {
                b.append(", active");
            } else {
                b.append(", inactive");
            }
        }

        b.append(" languages=[");
        String sep = "";
        for (PolyglotLanguageContext languageContext : contexts) {
            if (languageContext.isInitialized() || languageContext.isCreated()) {
                b.append(sep);
                b.append(languageContext.language.getId());
                sep = ", ";
            }
        }
        b.append("]");
        b.append("]");
        return b.toString();
    }

    private final class CancellationThreadLocalAction extends ThreadLocalAction {
        CancellationThreadLocalAction() {
            super(false, false);
        }

        @Override
        protected void perform(Access access) {
            PolyglotContextImpl.this.threadLocalActions.submit(new Thread[]{access.getThread()}, PolyglotEngineImpl.ENGINE_ID, this, new HandshakeConfig(true, false, false, true));

            State localState = PolyglotContextImpl.this.state;
            if (localState.isInvalidOrClosed() || localState.isCancelling()) {
                throw createCancelException(access.getLocation());
            }
        }
    }

    private final class InterruptThreadLocalAction extends ThreadLocalAction {
        InterruptThreadLocalAction() {
            super(true, false);
        }

        @Override
        protected void perform(Access access) {
            PolyglotContextImpl.this.threadLocalActions.submit(new Thread[]{access.getThread()}, PolyglotEngineImpl.ENGINE_ID, this, true);

            State localState = state;
            if (access.getThread() != PolyglotContextImpl.this.closingThread) {
                if (localState.isInterrupting()) {
                    PolyglotContextImpl[] interruptingChildContexts;
                    synchronized (PolyglotContextImpl.this) {
                        interruptingChildContexts = PolyglotContextImpl.this.childContexts.toArray(new PolyglotContextImpl[0]);
                    }
                    for (PolyglotContextImpl childCtx : interruptingChildContexts) {
                        if (access.getThread() == childCtx.closingThread) {
                            return;
                        }
                    }
                    // Interrupt should never break a closing operation
                    throw new PolyglotEngineImpl.InterruptExecution(access.getLocation());
                }
            }
        }
    }
}
