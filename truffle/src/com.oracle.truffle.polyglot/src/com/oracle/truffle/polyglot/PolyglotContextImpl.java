/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
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
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.impl.JDKAccessor;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.FileSystems.PreInitializeContextFileSystem;
import com.oracle.truffle.polyglot.PolyglotContextConfig.FileSystemConfig;
import com.oracle.truffle.polyglot.PolyglotContextConfig.PreinitConfig;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.CancelExecution;
import com.oracle.truffle.polyglot.PolyglotEngineImpl.StableLocalLocations;
import com.oracle.truffle.polyglot.PolyglotLanguageContext.ValueMigrationException;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;
import com.oracle.truffle.polyglot.PolyglotThreadLocalActions.HandshakeConfig;
import com.oracle.truffle.polyglot.SystemThread.LanguageSystemThread;

final class PolyglotContextImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    private static final TruffleLogger LOG = TruffleLogger.getLogger(PolyglotEngineImpl.OPTION_GROUP_ENGINE, PolyglotContextImpl.class);
    private static final InteropLibrary UNCACHED = InteropLibrary.getFactory().getUncached();
    private static final Object[] DISPOSED_CONTEXT_THREAD_LOCALS = new Object[0];
    private static final Map<State, State[]> VALID_TRANSITIONS = new EnumMap<>(State.class);
    private static final TruffleSafepoint.Interrupter DO_NOTHING_INTERRUPTER = new TruffleSafepoint.Interrupter() {
        @Override
        public void interrupt(Thread thread) {

        }

        @Override
        public void resetInterrupted() {

        }
    };

    static {
        VALID_TRANSITIONS.put(State.DEFAULT, new State[]{
                        State.CLOSING,
                        State.INTERRUPTING,
                        State.PENDING_EXIT,
                        State.CANCELLING,
                        State.EXITING, // only for child contexts and local contexts for isolated
                                       // contexts
        });
        VALID_TRANSITIONS.put(State.CLOSING, new State[]{
                        State.CLOSING_FINALIZING,
                        State.CLOSING_INTERRUPTING,
                        State.CLOSING_CANCELLING,
                        State.CLOSING_PENDING_EXIT,
                        State.CLOSING_EXITING,  // only for child contexts and local contexts for
                                                // isolated contexts
                        State.DEFAULT
        });
        VALID_TRANSITIONS.put(State.CLOSING_FINALIZING, new State[]{
                        State.CLOSED,
                        State.CLOSING_INTERRUPTING_FINALIZING,
                        State.CLOSING_CANCELLING,
                        State.CLOSING_EXITING,  // only for child contexts and local contexts for
                                                // isolated contexts
                        State.DEFAULT
        });
        VALID_TRANSITIONS.put(State.INTERRUPTING, new State[]{
                        State.DEFAULT,
                        State.CLOSING_INTERRUPTING,
                        State.CANCELLING,
                        State.PENDING_EXIT,
                        State.EXITING,  // only for child contexts and local contexts for isolated
                                        // contexts
        });
        VALID_TRANSITIONS.put(State.PENDING_EXIT, new State[]{
                        State.EXITING,
                        State.CANCELLING
        });
        VALID_TRANSITIONS.put(State.CANCELLING, new State[]{
                        State.CLOSING_CANCELLING
        });
        VALID_TRANSITIONS.put(State.CLOSING_INTERRUPTING, new State[]{
                        State.CLOSING_INTERRUPTING_FINALIZING,
                        State.CLOSING,
                        State.CLOSING_PENDING_EXIT,
                        State.CLOSING_CANCELLING,
                        State.CLOSING_EXITING,  // only for child contexts and local contexts for
                                                // isolate contexts
                        State.INTERRUPTING
        });
        VALID_TRANSITIONS.put(State.CLOSING_INTERRUPTING_FINALIZING, new State[]{
                        State.CLOSED_INTERRUPTED,
                        State.CLOSING_FINALIZING,
                        State.CLOSING_CANCELLING,
                        State.CLOSING_EXITING,  // only for child contexts and local contexts for
                                                // isolated contexts
                        State.INTERRUPTING
        });
        VALID_TRANSITIONS.put(State.CLOSING_CANCELLING, new State[]{
                        State.CLOSED_CANCELLED,
                        State.CANCELLING
        });
        VALID_TRANSITIONS.put(State.CLOSING_PENDING_EXIT, new State[]{
                        State.CLOSING_EXITING,
                        State.CLOSING_CANCELLING,
                        State.PENDING_EXIT
        });
        VALID_TRANSITIONS.put(State.CLOSING_EXITING, new State[]{
                        State.CLOSED_EXITED,
                        State.EXITING
        });
        VALID_TRANSITIONS.put(State.EXITING, new State[]{
                        State.CLOSING_EXITING
        });
        VALID_TRANSITIONS.put(State.CLOSED,
                        new State[0]);
        VALID_TRANSITIONS.put(State.CLOSED_CANCELLED,
                        new State[0]);
        VALID_TRANSITIONS.put(State.CLOSED_EXITED,
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
         * Hard exit was called in the DEFAULT or the INTERRUPTING state and exit notifications (see
         * TruffleLanguage#exitContext) are about to be executed or are already executing. the
         * PENDING_EXIT state overrides the INTERRUPTING state.
         */
        PENDING_EXIT,
        /*
         * Exit operation has been initiated after exit notifications were executed in the
         * PENDING_EXIT state. Threads are being stopped.
         */
        EXITING,
        /*
         * Cancel operation has been initiated. Threads are being stopped. The CANCELLING state
         * overrides the INTERRUPTING and the PENDING_EXIT state.
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
         * Hard exit was called in the CLOSING or the CLOSING_INTERRUPTING state and exit
         * notifications (see TruffleLanguage#exitContext) are about to be executed or are already
         * executing. the CLOSING_PENDING_EXIT state overrides the CLOSING_INTERRUPTING state.
         */
        CLOSING_PENDING_EXIT,
        /*
         * The close operation progressed to the "finalizing" stage where creation of inner contexts
         * and caching of thread info is no longer allowed. Also, hard exit is no longer allowed in
         * this state. The close operation either finishes successfully and the context goes into
         * one of the closed states, or the close operation fails and the context goes back to the
         * DEFAULT state.
         */
        CLOSING_FINALIZING,
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
         * The close operation while interrupting progressed to the "finalizing" stage where
         * creation of inner contexts is no longer allowed. Also, hard exit is no longer allowed in
         * this state. The close operation either finishes successfully and the context goes into
         * one of the closed states, or the close operation fails and the context goes back to the
         * INTERRUPTING state.
         */
        CLOSING_INTERRUPTING_FINALIZING,
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
         * Close operation has been initiated and at the same time exit operation is in progress.
         * Transition to this state can be only from the EXITING state. Close operation that started
         * in the CLOSING_EXITING state must finish successfully, otherwise it is an internal error.
         */
        CLOSING_EXITING,
        /*
         * Closing operation in the CLOSING state has finished successfully via the
         * CLOSING_FINALIZING state.
         */
        CLOSED,
        /*
         * Closing operation in the CLOSING_INTERRUPTING state has finished successfully via the
         * CLOSING_INTERRUPTING_FINALIZING state. Essentially the same as the CLOSED state, the only
         * difference is that in the CLOSED_INTERRUPTED state, the context leave operations on
         * threads that are still entered notify the thread that is waiting for the interrupting
         * operation to complete.
         */
        CLOSED_INTERRUPTED,
        /*
         * Closing operation in the CLOSING_CANCELLING state has finished successfully.
         */
        CLOSED_CANCELLED,
        /*
         * Closing operation in the CLOSING_EXITING state has finished successfully.
         */
        CLOSED_EXITED;

        /*
         * If false then code can run in this context. If true then code can no longer run - due to
         * cancelling, exiting or closing.
         */
        boolean isInvalidOrClosed() {
            switch (this) {
                case CANCELLING:
                case EXITING:
                case CLOSING_CANCELLING:
                case CLOSING_EXITING:
                case CLOSED:
                case CLOSED_INTERRUPTED:
                case CLOSED_CANCELLED:
                case CLOSED_EXITED:
                    return true;
                default:
                    return false;
            }
        }

        /*
         * If true the context is not usable and may be in an inconsistent state. This is due to
         * cancelling or exiting.
         */
        boolean isCancelled() {
            switch (this) {
                case CANCELLING:
                case EXITING:
                case CLOSING_CANCELLING:
                case CLOSING_EXITING:
                case CLOSED_CANCELLED:
                case CLOSED_EXITED:
                    return true;
                default:
                    return false;
            }
        }

        boolean isInterrupting() {
            switch (this) {
                case INTERRUPTING:
                case CLOSING_INTERRUPTING:
                case CLOSING_INTERRUPTING_FINALIZING:
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

        boolean isExiting() {
            switch (this) {
                case EXITING:
                case CLOSING_EXITING:
                    return true;
                default:
                    return false;
            }
        }

        boolean isClosing() {
            switch (this) {
                case CLOSING:
                case CLOSING_FINALIZING:
                case CLOSING_INTERRUPTING:
                case CLOSING_INTERRUPTING_FINALIZING:
                case CLOSING_CANCELLING:
                case CLOSING_PENDING_EXIT:
                case CLOSING_EXITING:
                    return true;
                default:
                    return false;
            }
        }

        boolean isClosed() {
            switch (this) {
                case CLOSED:
                case CLOSED_INTERRUPTED:
                case CLOSED_CANCELLED:
                case CLOSED_EXITED:
                    return true;
                default:
                    return false;
            }
        }

        private boolean shouldCacheThreadInfo() {
            switch (this) {
                case DEFAULT:
                case PENDING_EXIT:
                case CLOSING:
                case CLOSING_PENDING_EXIT:
                    return true;
                default:
                    return false;
            }
        }
    }

    volatile State state = State.DEFAULT;
    final WeakAssumedValue<PolyglotThreadInfo> singleThreadValue = new WeakAssumedValue<>("Single thread");
    volatile boolean singleThreaded = true;

    private final Map<Thread, PolyglotThreadInfo> threads = new WeakHashMap<>();

    /*
     * Do not modify only read. Use setCachedThreadInfo to modify.
     */
    private volatile PolyglotThreadInfo cachedThreadInfo = PolyglotThreadInfo.NULL;
    volatile Object api;

    private ExecutorService cleanupExecutorService;
    private Future<?> cleanupFuture;
    boolean skipPendingExit;
    volatile int exitCode;
    private volatile String exitMessage;
    volatile Thread closeExitedTriggerThread;
    private volatile String invalidMessage;
    volatile boolean invalidResourceLimit;
    volatile Thread closingThread;
    private final ReentrantLock closingLock = new ReentrantLock();
    private final ReentrantLock interruptingLock = new ReentrantLock();
    private final ReentrantLock initiateCancelOrExitLock = new ReentrantLock();
    private List<Future<Void>> cancellationOrExitingFutures;

    volatile boolean disposing;
    volatile boolean finalizingEmbedderThreads;
    final PolyglotEngineImpl engine;
    final PolyglotSharingLayer layer;
    // contexts by PolyglotLanguage.engineIndex
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;

    final TruffleContext creatorTruffleContext;
    final TruffleContext currentTruffleContext;
    final PolyglotContextImpl parent;
    volatile Map<String, Object> polyglotBindings; // for direct legacy access
    volatile Object polyglotHostBindings; // for accesses from the polyglot api
    private final PolyglotBindings polyglotBindingsObject = new PolyglotBindings(this);
    final PolyglotLanguage creator; // creator for internal contexts
    final ContextWeakReference weakReference;
    final Set<ProcessHandlers.ProcessDecorator> subProcesses;

    @CompilationFinal PolyglotContextConfig config; // effectively final

    // map from class to language index
    @CompilationFinal private volatile FinalIntMap languageIndexMap;

    private final List<PolyglotContextImpl> childContexts = new ArrayList<>();
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

    final Node uncachedLocation;

    private final Set<LanguageSystemThread> activeSystemThreads = Collections.newSetFromMap(new HashMap<>());

    /* Constructor for testing. */
    @SuppressWarnings("unused")
    private PolyglotContextImpl() {
        this.engine = null;
        this.contexts = null;
        this.creatorTruffleContext = null;
        this.currentTruffleContext = null;
        this.layer = null;
        this.parent = null;
        this.polyglotHostBindings = null;
        this.polyglotBindings = null;
        this.creator = null;
        this.weakReference = null;
        this.statementLimit = 0;
        this.threadLocalActions = null;
        this.subProcesses = new HashSet<>();
        this.uncachedLocation = null;
    }

    /*
     * Constructor for outer contexts.
     */
    PolyglotContextImpl(PolyglotEngineImpl engine, PolyglotContextConfig config) {
        this.parent = null;
        this.engine = engine;
        this.layer = new PolyglotSharingLayer(engine);
        this.config = config;
        this.creator = null;
        this.uncachedLocation = new UncachedLocationNode(layer);
        this.creatorTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, true);
        this.currentTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, false);
        this.weakReference = new ContextWeakReference(this);
        this.contexts = createContextArray();
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
    }

    /*
     * Constructor for inner contexts.
     */
    @SuppressWarnings("hiding")
    PolyglotContextImpl(PolyglotLanguageContext creator, PolyglotContextConfig config) {
        PolyglotContextImpl parent = creator.context;
        this.parent = parent;
        this.layer = new PolyglotSharingLayer(parent.engine);
        this.config = config;
        this.engine = parent.engine;
        this.creator = creator.language;
        this.uncachedLocation = new UncachedLocationNode(layer);
        this.statementLimit = 0; // inner context limit must not be used anyway
        this.weakReference = new ContextWeakReference(this);
        this.creatorTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, true);
        this.currentTruffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this, false);
        if (parent.state.isInterrupting()) {
            this.state = State.INTERRUPTING;
        } else if (parent.state.isCancelling()) {
            this.state = State.CANCELLING;
        } else if (parent.state.isExiting()) {
            this.state = State.EXITING;
        }
        this.invalidMessage = this.parent.invalidMessage;
        this.exitCode = this.parent.exitCode;
        this.exitMessage = this.parent.exitMessage;
        this.contextBoundLoggers = this.parent.contextBoundLoggers;
        this.threadLocalActions = new PolyglotThreadLocalActions(this);
        if (!parent.config.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, parent.config.logLevels, getAllLoggers());
        }
        this.contexts = createContextArray();
        this.subProcesses = new HashSet<>();
        // notifyContextCreated() is called after spiContext.impl is set to this.
        this.engine.noInnerContexts.invalidate();
    }

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
        if (fromState.isClosing() != toState.isClosing()) {
            if (closingThread != Thread.currentThread()) {
                return false;
            }
        }
        if (!fromState.isExiting() && toState.isExiting() && fromState != State.PENDING_EXIT && fromState != State.CLOSING_PENDING_EXIT) {
            if (parent == null && !skipPendingExit) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldCacheThreadInfo() {
        assert Thread.holdsLock(this);
        return state.shouldCacheThreadInfo() && !disposing;
    }

    /**
     * Claims a sharing layer for a context. This typically happens at when the first non-host
     * language is initialized in a context.
     */
    void claimSharingLayer(PolyglotLanguage language) {
        PolyglotSharingLayer s = this.layer;
        if (!s.isClaimed()) {
            synchronized (engine.lock) {
                if (!s.isClaimed()) {
                    assert !language.isHost() : "cannot claim context for a host language";
                    engine.claimSharingLayer(s, this, language);
                    assert s.isClaimed();
                    this.weakReference.layer = s;
                }
            }
        }
    }

    boolean claimSharingLayer(PolyglotSharingLayer sharableLayer, Set<PolyglotLanguage> languages) {
        PolyglotSharingLayer s = this.layer;
        synchronized (engine.lock) {
            assert !s.isClaimed() : "sharing layer already claimed";
            if (!s.isClaimed()) {
                if (!s.claimLayerForContext(sharableLayer, this, languages)) {
                    return false;
                }
                assert s.isClaimed();
                assert this.layer.equals(sharableLayer);
                this.weakReference.layer = s;
            }
        }
        return true;
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
            TruffleSafepoint.poll(this.uncachedLocation);
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    private PolyglotLanguageContext[] createContextArray() {
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        PolyglotLanguageContext[] newContexts = new PolyglotLanguageContext[engine.languageCount];
        Iterator<PolyglotLanguage> languageIterator = languages.iterator();
        for (int i = (PolyglotEngineImpl.HOST_LANGUAGE_INDEX + 1); i < engine.languageCount; i++) {
            PolyglotLanguage language = languageIterator.next();
            newContexts[i] = new PolyglotLanguageContext(this, language);
        }
        maybeInitializeHostLanguage(newContexts);
        return newContexts;
    }

    private void maybeInitializeHostLanguage(PolyglotLanguageContext[] contextsArray) {
        PolyglotLanguage hostLanguage = engine.hostLanguage;
        PolyglotLanguageContext hostContext = new PolyglotLanguageContext(this, hostLanguage);
        contextsArray[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = hostContext;
        if (PreInitContextHostLanguage.isInstance(hostLanguage)) {
            // The host language in the image execution time may differ from host language in the
            // image build time. We have to postpone the creation and initialization of the host
            // language context until the patching.
            assert engine.inEnginePreInitialization : "PreInitContextHostLanguage can be used only during context pre-initialization";
        } else {
            hostContext.ensureCreated(hostLanguage);
            hostContext.ensureInitialized(null);
        }
    }

    PolyglotLanguageContext getContext(PolyglotLanguage language) {
        return contexts[language.engineIndex];
    }

    Object getContextImpl(PolyglotLanguage language) {
        return contexts[language.engineIndex].getContextImpl();
    }

    PolyglotLanguageContext getContextInitialized(PolyglotLanguage language, PolyglotLanguage accessingLanguage) {
        PolyglotLanguageContext context = getContext(language);
        context.ensureInitialized(accessingLanguage);
        return context;
    }

    void notifyContextCreated() {
        EngineAccessor.INSTRUMENT.notifyContextCreated(engine, creatorTruffleContext);
    }

    void addChildContext(PolyglotContextImpl child) {
        assert Thread.holdsLock(this);
        assert !state.isClosed();
        if (state.isClosing() && !state.shouldCacheThreadInfo()) {
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
    Object[] enterThreadChanged(boolean enterReverted, boolean pollSafepoint, boolean mustSucceed, boolean polyglotThreadFirstEnter,
                    boolean leaveAndEnter) {
        PolyglotThreadInfo enteredThread = null;
        Object[] prev = null;
        Thread current = Thread.currentThread();
        if (JDKAccessor.isVirtualThread(current) && !(Truffle.getRuntime() instanceof DefaultTruffleRuntime)) {
            throw PolyglotEngineException.illegalState(
                            "Using polyglot contexts on Java virtual threads is currently not supported with an optimizing Truffle runtime. " +
                                            "As a workaround you may add the -Dtruffle.TruffleRuntime=com.oracle.truffle.api.impl.DefaultTruffleRuntime JVM argument to switch to a non-optimizing runtime when using virtual threads. " +
                                            "Please note that performance is severly reduced in this mode. Loom support for optimizing runtimes will be added in a future release.");
        }
        try {
            boolean deactivateSafepoints = mustSucceed;
            boolean localPollSafepoint = pollSafepoint && !mustSucceed;
            try {
                if (current instanceof SystemThread) {
                    assert !mustSucceed;
                    throw PolyglotEngineException.illegalState("Context cannot be entered on system threads.");
                }
                if (current instanceof PolyglotThread && !((PolyglotThread) current).isEnterAllowed()) {
                    assert !mustSucceed;
                    throw PolyglotEngineException.illegalState("Context cannot be entered in polyglot thread's beforeEnter or afterLeave notifications.");
                }
                boolean needsInitialization = false;
                synchronized (this) {
                    PolyglotThreadInfo threadInfo = getCurrentThreadInfo();

                    if (enterReverted && threadInfo.getEnteredCount() == 0) {
                        threadLocalActions.notifyThreadActivation(threadInfo, false);
                        if ((state.isCancelling() || state.isExiting() || state == State.CLOSED_CANCELLED || state == State.CLOSED_EXITED) && !threadInfo.isActive()) {
                            notifyThreadClosed(threadInfo);
                        }
                        if ((state.isInterrupting() || state == State.CLOSED_INTERRUPTED) && !threadInfo.isActive()) {
                            if (threadInfo.interruptSent) {
                                Thread.interrupted();
                                threadInfo.interruptSent = false;
                            }
                            notifyAll();
                        }
                    }
                    if (deactivateSafepoints && threadInfo != PolyglotThreadInfo.NULL) {
                        threadLocalActions.notifyThreadActivation(threadInfo, false);
                    }

                    assert threadInfo != null;
                    if (!leaveAndEnter) {
                        checkClosedOrDisposing(mustSucceed);
                        if (threadInfo.isInLeaveAndEnter()) {
                            throw PolyglotEngineException.illegalState("Context cannot be entered inside leaveAndEnter.");
                        }
                    }

                    threadInfo = threads.get(current);
                    if (threadInfo == null) {
                        threadInfo = createThreadInfo(current, polyglotThreadFirstEnter);
                        needsInitialization = true;
                    }
                    if (singleThreaded) {
                        /*
                         * If this is the only thread, then setting the cached thread info to NULL
                         * is no performance problem. If there is other thread that is just about to
                         * enter, we are making sure that it initializes multi-threading if this
                         * thread doesn't do it.
                         */
                        setCachedThreadInfo(PolyglotThreadInfo.NULL);
                    }
                    boolean transitionToMultiThreading = isSingleThreaded() && hasActiveOtherThread(true, false);

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
                         * Do not enter the thread before initializing thread locals. Creation of
                         * thread locals might fail.
                         */
                        initializeThreadLocals(threadInfo);
                    }

                    prev = threadInfo.enterInternal();
                    if (leaveAndEnter) {
                        threadInfo.setLeaveAndEnterInterrupter(null);
                        notifyAll();
                    }
                    if (needsInitialization) {
                        this.threadLocalActions.notifyEnterCreatedThread();
                    }
                    if (closingThread != Thread.currentThread()) {
                        try {
                            threadInfo.notifyEnter(engine, this);
                        } catch (Throwable t) {
                            threadInfo.leaveInternal(prev);
                            throw t;
                        }
                    }
                    enteredThread = threadInfo;

                    // new thread became active so we need to check potential active thread local
                    // actions and process them.
                    Set<ThreadLocalAction> activatedActions = null;
                    if (enteredThread.getEnteredCount() == 1 && !deactivateSafepoints) {
                        activatedActions = threadLocalActions.notifyThreadActivation(threadInfo, true);
                    }

                    if (transitionToMultiThreading) {
                        // we need to verify that all languages give access
                        // to all threads in multi-threaded mode.
                        transitionToMultiThreaded(mustSucceed);
                    }

                    if (needsInitialization) {
                        initializeNewThread(enteredThread, mustSucceed);
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
                 * We need to always poll the safepoint here in case we already submitted a thread
                 * local action for this thread. Not polling here would make dependencies of that
                 * event wait forever.
                 */
                if (localPollSafepoint) {
                    TruffleSafepoint.pollHere(this.uncachedLocation);
                }
            }
        } catch (Throwable t) {
            /*
             * Just in case the enter fails when already entered, we need to leave the context again
             * unless we are inside leaveAndEnter which should be followed by leave in a finally
             * block.
             */
            if (enteredThread != null && !leaveAndEnter) {
                this.leaveThreadChanged(prev, true, polyglotThreadFirstEnter);
            }
            throw t;
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
        if (!shouldCacheThreadInfo() || threadLocalActions.hasActiveEvents()) {
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

    @SuppressWarnings("CatchMayIgnoreException")
    public <T, R> R leaveAndEnter(TruffleSafepoint.Interrupter interrupter, TruffleSafepoint.InterruptibleFunction<T, R> interruptible, T object, boolean mustSucceed) {
        Objects.requireNonNull(interrupter);
        Objects.requireNonNull(interruptible);

        if (!mustSucceed) {
            TruffleSafepoint.pollHere(uncachedLocation);
        }

        PolyglotThreadInfo currentThreadInfo;
        synchronized (this) {
            currentThreadInfo = getCurrentThreadInfo();
            if (currentThreadInfo.getEnteredCount() != 1) {
                throw PolyglotEngineException.illegalState("Context is entered " + currentThreadInfo.getEnteredCount() + " times. It must be entered exactly once for leaveAndEnter.");
            }
            leaveThreadChanged(null, true, false);
            currentThreadInfo.setLeaveAndEnterInterrupter(interrupter);
            setCachedThreadInfo(PolyglotThreadInfo.NULL);
        }
        boolean interrupted = false;
        try {
            return interruptible.apply(object);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (currentThreadInfo.leaveAndEnterInterrupted) {
                interrupter.resetInterrupted();
                currentThreadInfo.leaveAndEnterInterrupted = false;
            }
            enterThreadChanged(false, true, mustSucceed, false, true);
            synchronized (this) {
                if (state.isCancelled()) {
                    assert invalidMessage != null;
                    /*
                     * Cancellation thread local action needs to be submitted here in case the
                     * context is cancelling, exiting, cancelled or exited, because we just entered
                     * the context in a thread which may have been excluded from cancellation while
                     * the context was not entered.
                     */
                    threadLocalActions.submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, new CancellationThreadLocalAction(), true);
                } else if (state.isInterrupting()) {
                    threadLocalActions.submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, new InterruptThreadLocalAction(), true);
                }
            }
            if (!mustSucceed) {
                TruffleSafepoint.pollHere(uncachedLocation);
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * Use to leave a context if its guaranteed to be called rarely and configuration flexibility is
     * needed. Otherwise use {@link PolyglotEngineImpl#leave(Object[], PolyglotContextImpl)}.
     */
    @TruffleBoundary
    void leaveThreadChanged(Object[] prev, boolean entered, boolean finalizeAndDispose) {
        PolyglotThreadInfo threadInfo;
        Throwable ex = null;
        Thread current = Thread.currentThread();
        if (current instanceof PolyglotThread && !((PolyglotThread) current).isEnterAllowed()) {
            throw PolyglotEngineException.illegalState("Context cannot be left in polyglot thread's beforeEnter or afterLeave notifications.");
        }
        synchronized (this) {
            threadInfo = threads.get(current);
            assert threadInfo != null : "thread must not be disposed";
        }
        if (finalizeAndDispose) {
            /*
             * Thread finalization notification is invoked outside of the context lock so that the
             * guest languages can operate freely without the risk of a deadlock.
             */
            ex = notifyThreadFinalizing(threadInfo, null);
        }
        synchronized (this) {
            if (finalizeAndDispose) {
                ex = notifyThreadDisposing(threadInfo, ex);
            }

            setCachedThreadInfo(PolyglotThreadInfo.NULL);

            if (entered) {
                try {
                    if (closingThread != Thread.currentThread()) {
                        threadInfo.notifyLeave(engine, this);
                    }
                } finally {
                    threadInfo.leaveInternal(prev);
                }
            }
            if (threadInfo.getEnteredCount() == 0) {
                threadLocalActions.notifyThreadActivation(threadInfo, false);
            }

            if ((state.isCancelling() || state.isExiting() || state == State.CLOSED_CANCELLED || state == State.CLOSED_EXITED) && !threadInfo.isActive()) {
                notifyThreadClosed(threadInfo);
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

            if ((state.isInterrupting() || state == State.CLOSED_INTERRUPTED) && !threadInfo.isActive()) {
                if (threadInfo.interruptSent) {
                    Thread.interrupted();
                    threadInfo.interruptSent = false;
                }
                notifyAll();
            }

            if (finalizeAndDispose) {
                finishThreadDispose(current, threadInfo, ex);
            }
        }
    }

    private void finishThreadDispose(Thread current, PolyglotThreadInfo info, Throwable ex) {
        assert !info.isActive();

        if (cachedThreadInfo.getThread() == current) {
            setCachedThreadInfo(PolyglotThreadInfo.NULL);
        }
        info.setContextThreadLocals(DISPOSED_CONTEXT_THREAD_LOCALS);
        threads.remove(current);

        if (ex != null) {
            throw sneakyThrow(ex);
        }
    }

    private Throwable notifyThreadFinalizing(PolyglotThreadInfo threadInfo, Throwable previousEx) {
        Throwable ex = previousEx;
        Thread thread = threadInfo.getThread();
        if (thread == null) {
            // thread was already collected
            return ex;
        }

        BitSet finalizedContexts = new BitSet(contexts.length);
        while (true) {
            for (PolyglotLanguageContext languageContext : contexts) {
                /*
                 * New contexts might be initialized while we are finalizing threads. The
                 * initialization of a new context can happen both on this thread and some other
                 * thread. The initialization of a new context on other thread also executes
                 * initializeThread for this thread as long as this thread is in
                 * PolyglotContextImpl's seen threads. For embedder threads, the thread finalization
                 * happens after the context is finalized. For polyglot threads it can be before the
                 * context finalization or during. We must keep the initializeThread,
                 * finalizeThread, disposeThread order for each thread. For polyglot threads all
                 * those three are executed on the appropriate thread, except for lazily initialized
                 * contexts which execute initializeThread for all seen threads from the point of
                 * context initialization. Therefore:
                 *
                 * 1) We cannot initialize this thread for new lazily initialized context when this
                 * finalization loop is completed => we set threadInfo#finalizationComplete to true
                 * and never call initializeThread for this thread anymore.
                 *
                 * 2) We must not call finalizeThread and disposeThread for a context for which
                 * initializeThread was not called => we call finalizeThread only for those contexts
                 * which have their bit in threadInfo#initializedLanguageContexts set.
                 */
                if (!finalizedContexts.get(languageContext.language.engineIndex)) {
                    boolean contextInitialized;
                    synchronized (this) {
                        contextInitialized = languageContext.isInitialized() && threadInfo.isLanguageContextInitialized(languageContext.language);
                    }
                    if (contextInitialized) {
                        try {
                            finalizedContexts.set(languageContext.language.engineIndex);
                            LANGUAGE.finalizeThread(languageContext.env, thread);
                        } catch (Throwable t) {
                            if (ex == null) {
                                ex = t;
                            } else {
                                ex.addSuppressed(t);
                            }
                        }
                    }
                }
            }
            synchronized (this) {
                if (finalizedContexts.cardinality() == threadInfo.initializedLanguageContextsCount()) {
                    threadInfo.setFinalizationComplete();
                    break;
                }
            }
        }

        return ex;
    }

    private Throwable notifyThreadDisposing(PolyglotThreadInfo threadInfo, Throwable previousEx) {
        Throwable ex = previousEx;
        Thread thread = threadInfo.getThread();
        if (thread == null) {
            // thread was already collected
            return ex;
        }

        for (PolyglotLanguageContext languageContext : contexts) {
            if (languageContext.isInitialized() && threadInfo.isLanguageContextInitialized(languageContext.language)) {
                try {
                    LANGUAGE.disposeThread(languageContext.env, thread);
                } catch (Throwable t) {
                    if (ex == null) {
                        ex = t;
                    } else {
                        ex.addSuppressed(t);
                    }
                }
            }
        }

        try {
            EngineAccessor.INSTRUMENT.notifyThreadFinished(engine, creatorTruffleContext, thread);
        } catch (Throwable t) {
            if (ex == null) {
                ex = t;
            } else {
                ex.addSuppressed(t);
            }
        }

        return ex;
    }

    /*
     * When a context is being cancelled or hard-exited, certain exceptions are suppressed and just
     * logged in certain situations in order not to interfere with the cancelling process, but those
     * for which this method returns true are always thrown.
     */
    static boolean isInternalError(Throwable t) {
        return !(t instanceof AbstractTruffleException) && !(t instanceof PolyglotEngineImpl.CancelExecution) && !(t instanceof PolyglotContextImpl.ExitException);
    }

    private void initializeNewThread(PolyglotThreadInfo threadInfo, boolean mustSucceed) {
        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                try {
                    threadInfo.initializeLanguageContext(context);
                } catch (Throwable t) {
                    if (!mustSucceed || isInternalError(t)) {
                        throw t;
                    } else {
                        /*
                         * initializeThread may execute thread local actions, and so truffle and
                         * cancel exceptions are expected. However, they must not fail the cancel
                         * operation, and so we just log them.
                         */
                        assert state.isClosing();
                        assert state.isInvalidOrClosed();
                        engine.getEngineLogger().log(Level.FINE,
                                        "Exception was thrown while initializing new thread for a polyglot context that is being cancelled or exited. Such exceptions are expected during cancelling or exiting.",
                                        t);
                    }
                }
            }
        }
    }

    private void transitionToMultiThreaded(boolean mustSucceed) {
        assert Thread.holdsLock(this);

        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                context.ensureMultiThreadingInitialized(mustSucceed);
            }
        }
        singleThreaded = false;
        singleThreadValue.invalidate();

        long statementsExecuted = statementLimit - statementCounter;
        volatileStatementCounter.getAndAdd(-statementsExecuted);
    }

    private PolyglotThreadInfo createThreadInfo(Thread current, boolean polyglotThreadFirstEnter) {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo threadInfo = new PolyglotThreadInfo(this, current, polyglotThreadFirstEnter);

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

    public Object getBindings(String languageId) {
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

    public Object getPolyglotBindings() {
        try {
            checkClosed();
            Object bindings = this.polyglotHostBindings;
            if (bindings == null) {
                initPolyglotBindings();
                bindings = this.polyglotHostBindings;
            }
            return bindings;
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(engine, e);
        }
    }

    public Map<String, Object> getPolyglotGuestBindings() {
        Map<String, Object> bindings = this.polyglotBindings;
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

    void checkClosedOrDisposing(boolean mustSucceed) {
        assert !mustSucceed || (closingThread == Thread.currentThread() && !state.isClosed() && !disposing);
        checkCancelledNotClosing();
        if (state.isClosed() || disposing) {
            throw PolyglotEngineException.closedException("The Context is already closed.");
        }
    }

    void checkClosed() {
        checkCancelledNotClosing();
        if (state.isClosed()) {
            throw PolyglotEngineException.closedException("The Context is already closed.");
        }
    }

    private void checkCancelledNotClosing() {
        if (closingThread != Thread.currentThread()) {
            checkCancelled();
        }
    }

    private void checkCancelled() {
        if (state.isCancelled()) {
            assert invalidMessage != null;
            /*
             * If invalidMessage == null, then invalid flag was set by close.
             */
            if (exitMessage == null) {
                throw createCancelException(null);
            } else {
                throw createExitException(null);
            }
        }
    }

    @TruffleBoundary
    private RuntimeException failValueSharing() {
        throw new ValueMigrationException("A value was tried to be migrated from one context to a different context. " +
                        "Value migration for the current context was disabled and is therefore disallowed.", this.uncachedLocation);
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

    @Override
    public APIAccess getAPIAccess() {
        return engine.apiAccess;
    }

    @Override
    public PolyglotImpl getImpl() {
        return engine.impl;
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
                    indexValue = context.language.engineIndex;
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

    public Object parse(String languageId, Object source) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        assert languageContext != null;
        Object prev = hostEnter(languageContext);
        try {
            Source truffleSource = (Source) getAPIAccess().getSourceReceiver(source);
            languageContext.checkAccess(null);
            languageContext.ensureInitialized(null);
            CallTarget target = languageContext.parseCached(null, truffleSource, null);
            return languageContext.asValue(new PolyglotParsedEval(languageContext, truffleSource, target));
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

    public Object eval(String languageId, Object source) {
        PolyglotLanguageContext languageContext = lookupLanguageContext(languageId);
        assert languageContext != null;
        Object prev = hostEnter(languageContext);
        try {
            Source truffleSource = (Source) getAPIAccess().getSourceReceiver(source);
            languageContext.checkAccess(null);
            languageContext.ensureInitialized(null);
            CallTarget target = languageContext.parseCached(null, truffleSource, null);
            Object result = target.call(PolyglotImpl.EMPTY_ARGS);
            Object hostValue;
            try {
                hostValue = languageContext.asValue(result);
            } catch (NullPointerException | ClassCastException e) {
                throw new AssertionError(String.format("Language %s returned an invalid return value %s. Must be an interop value.", languageId, result), e);
            }
            if (truffleSource.isInteractive()) {
                printResult(languageContext, result);
            }
            return hostValue;
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(languageContext, e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    public PolyglotLanguage requirePublicLanguage(String languageId) {
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

    /**
     * Embedder close.
     */
    public void close(boolean cancelIfExecuting) {
        try {
            clearExplicitContextStack();

            if (cancelIfExecuting) {
                /*
                 * Cancel does invalidate. We always need to invalidate before force-closing a
                 * context that might be active in other threads.
                 */
                cancel(false, null);
            } else {
                closeAndMaybeWait(false, null);
                checkCancelledNotClosing();
            }
        } catch (Throwable t) {
            RuntimeException polyglotException = PolyglotImpl.guestToHostException(getHostContext(), t, false);
            PolyglotExceptionImpl polyglotExceptionImpl = (PolyglotExceptionImpl) getAPIAccess().getPolyglotExceptionReceiver(polyglotException);

            if (!cancelIfExecuting && state.isInvalidOrClosed() && (polyglotExceptionImpl.isCancelled() || polyglotExceptionImpl.isExit())) {
                try {
                    /*
                     * The close operation was interrupted by cancelling or exiting, we are now in
                     * an invalid state. By executing the close operation again, we make sure that
                     * the close operation is fully completed when we return.
                     */
                    closeAndMaybeWait(false, null);
                } catch (Throwable closeFinishError) {
                    /*
                     * Close operation started when the context is already invalid should complete
                     * without an error. This exception indicates a bug, most probably in the
                     * language implementation.
                     */
                    RuntimeException closeFinishPolyglotException = PolyglotImpl.guestToHostException(getHostContext(), t, false);
                    polyglotException.addSuppressed(closeFinishPolyglotException);
                }
            }
            throw polyglotException;
        }
    }

    void cancel(boolean resourceLimit, String message) {
        String cancelMessage = message == null ? "Context execution was cancelled." : message;
        if (parent == null) {
            engine.polyglotHostService.notifyContextCancellingOrExiting(this, false, 0, resourceLimit, cancelMessage);
        }
        List<Future<Void>> futures = setCancelling(resourceLimit, cancelMessage);
        closeHereOrCancelInCleanupThread(futures);
    }

    void initiateCancelOrExit(boolean exit, int code, boolean resourceLimit, String message) {
        assert parent == null;
        initiateCancelOrExitLock.lock();
        try {
            List<Future<Void>> futures;
            if (exit) {
                futures = setExiting(null, code, message, true);
            } else {
                futures = setCancelling(resourceLimit, message);
            }
            if (!futures.isEmpty()) {
                /*
                 * initiateCancelOrExit keeps assigning cancellationOrExitingFutures until one of
                 * the other setExiting or setCancelling calls takes it and from that point
                 * cancellationOrExitingFutures == null. If the futures are empty, it means that
                 * cancelling was not initiated by this method, because it was already initiated
                 * before, or it is no longer possible.
                 */
                cancellationOrExitingFutures = futures;
            }
        } finally {
            initiateCancelOrExitLock.unlock();
        }
    }

    void closeAndMaybeWait(boolean force, List<Future<Void>> futures) {
        if (force) {
            PolyglotEngineImpl.cancelOrExit(this, futures);
        } else {
            boolean closeCompleted = closeImpl(true);
            if (!closeCompleted) {
                throw PolyglotEngineException.illegalState(String.format("The context is currently executing on another thread. " +
                                "Set cancelIfExecuting to true to stop the execution on this thread."));
            }
        }
        finishCleanup();
        checkSubProcessFinished();
        checkSystemThreadsFinished();
        if (parent == null) {
            engine.polyglotHostService.notifyContextClosed(this, force, invalidResourceLimit, invalidMessage);
        }
        if (engine.boundEngine && parent == null) {
            engine.ensureClosed(force, true);
        }
    }

    private void setState(State targetState) {
        assert Thread.holdsLock(this);
        assert isTransitionAllowed(state, targetState) : "Transition from " + state.name() + " to " + targetState.name() + " not allowed!";
        state = targetState;
        notifyAll();
    }

    private List<Future<Void>> setInterrupting() {
        assert Thread.holdsLock(this);
        State targetState;
        List<Future<Void>> futures = new ArrayList<>();
        if (!state.isInterrupting() && !state.isInvalidOrClosed() && state != State.PENDING_EXIT && state != State.CLOSING_PENDING_EXIT) {
            switch (state) {
                case CLOSING:
                    targetState = State.CLOSING_INTERRUPTING;
                    break;
                case CLOSING_FINALIZING:
                    targetState = State.CLOSING_INTERRUPTING_FINALIZING;
                    break;
                default:
                    targetState = State.INTERRUPTING;
                    break;
            }
            setState(targetState);
            setCachedThreadInfo(PolyglotThreadInfo.NULL);
            futures.add(threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, new InterruptThreadLocalAction(), true));
            maybeSendInterrupt();
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
                case CLOSING_INTERRUPTING_FINALIZING:
                    targetState = State.CLOSING_FINALIZING;
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

    private List<Future<Void>> interruptChildContexts() {
        PolyglotContextImpl[] childContextsToInterrupt = null;
        List<Future<Void>> futures;
        synchronized (this) {
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

    private void validateInterruptPrecondition(PolyglotContextImpl operationSource) {
        PolyglotContextImpl[] childContextsToInterrupt;
        synchronized (this) {
            PolyglotThreadInfo info = getCurrentThreadInfo();
            if (info != PolyglotThreadInfo.NULL && info.isActive()) {
                throw PolyglotEngineException.illegalState(String.format("Cannot interrupt context from a thread where %s context is active.", this == operationSource ? "the" : "its child"));
            }
            childContextsToInterrupt = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
        }
        for (PolyglotContextImpl childCtx : childContextsToInterrupt) {
            childCtx.validateInterruptPrecondition(operationSource);
        }
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
                validateInterruptPrecondition(this);
                List<Future<Void>> futures;
                synchronized (this) {
                    if (state.isClosed()) {
                        // already closed
                        return true;
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
                return PolyglotEngineImpl.cancelOrExitOrInterrupt(this, futures, startMillis, timeout);
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

    public Object asValue(Object hostValue) {
        PolyglotLanguageContext languageContext = this.getHostContext();
        Object prev = hostEnter(languageContext);
        try {
            checkClosed();
            PolyglotLanguageContext targetLanguageContext;
            if (getAPIAccess().isValue(hostValue)) {
                // fast path for when no context migration is necessary
                PolyglotLanguageContext valueContext = (PolyglotLanguageContext) getAPIAccess().getValueContext(hostValue);
                if (valueContext != null && valueContext.context == this) {
                    return hostValue;
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
            return targetLanguageContext.asValue(toGuestValue(null, hostValue, true));
        } catch (Throwable e) {
            throw PolyglotImpl.guestToHostException(this.getHostContext(), e, true);
        } finally {
            hostLeave(languageContext, prev);
        }
    }

    static PolyglotEngineImpl getConstantEngine(Node node) {
        if (!CompilerDirectives.inCompiledCode() ||
                        !CompilerDirectives.isPartialEvaluationConstant(node)) {
            return null;
        }
        if (node == null) {
            return null;
        }
        RootNode root = node.getRootNode();
        if (root == null) {
            return null;
        }
        PolyglotSharingLayer layer = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);
        return layer != null ? layer.engine : null;
    }

    Object toGuestValue(Node node, Object hostValue, boolean asValue) {
        PolyglotEngineImpl localEngine = getConstantEngine(node);
        PolyglotContextImpl localContext;
        if (localEngine == null) {
            localEngine = this.engine;
            localContext = this;
        } else {
            // lookup context as a constant
            localContext = localEngine.singleContextValue.getConstant();
            if (localContext == null) {
                // not a constant use this
                localContext = this;
            }
        }
        Object value = PolyglotHostAccess.toGuestValue(localContext, hostValue);
        return localEngine.host.toGuestValue(localContext.getHostContextImpl(), value, asValue);
    }

    /**
     * Wait until the condition is false.
     */
    void waitUntilFalse(Supplier<Boolean> condition) {
        assert Thread.holdsLock(this);
        boolean interrupted = false;
        while (condition.get()) {
            try {
                wait();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wait until the condition is false and return true, or wait until timeout while the condition
     * is true and return false.
     */
    boolean waitUntilFalseWithTimeout(Supplier<Boolean> condition, long startMillis, long timeoutMillis) {
        assert Thread.holdsLock(this);
        long timeElapsed = System.currentTimeMillis() - startMillis;
        boolean value;
        boolean interrupted = false;
        while ((value = condition.get()) && timeElapsed < timeoutMillis) {
            try {
                wait(timeoutMillis - timeElapsed);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            timeElapsed = System.currentTimeMillis() - startMillis;
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        /*
         * The condition supplier might be racy. E.g., for hasActiveOtherThread, one of the threads
         * might be just about to enter via fast path and so hasActiveOtherThread might return a
         * different result if we executed it again after the while loop. The fast-path enter might
         * not go through in the end, especially if this is waiting for cancellation of all threads,
         * so it is not a problem that hasActiveOtherThread is racy, but it is important that the
         * waiting method does not return a wrong value. That is why we store the result in a
         * boolean so that the returned value corresponds to the reason why the while loop has
         * ended.
         */
        return !value;
    }

    /**
     * Wait until the condition is false and return true, or if timeoutMillis != 0, wait until
     * timeout while the condition is true and return false.
     */
    boolean waitUntilFalse(Supplier<Boolean> condition, long startMillis, long timeoutMillis) {
        if (timeoutMillis == 0) {
            waitUntilFalse(condition);
            return true;
        } else {
            return waitUntilFalseWithTimeout(condition, startMillis, timeoutMillis);
        }
    }

    @SuppressWarnings("ConstantConditions")
    boolean waitForAllThreads(long startMillis, long timeoutMillis) {
        synchronized (this) {
            if (!waitUntilFalse(() -> hasActiveOtherThread(true, false), startMillis, timeoutMillis)) {
                return false;
            } else {
                PolyglotThreadInfo currentThreadInfo = getCurrentThreadInfo();
                boolean shouldLeaveAndEnter = false;
                /*
                 * If enteredCount == 0, then leaveAndEnter is not needed and if enteredCount > 1,
                 * then we can't do it, so we have to live with the fact that we might trigger
                 * multi-threading.
                 */
                if (currentThreadInfo != PolyglotThreadInfo.NULL && currentThreadInfo.getEnteredCount() == 1) {
                    for (PolyglotThreadInfo threadInfo : threads.values()) {
                        if (!threadInfo.isCurrent() && threadInfo.isInLeaveAndEnter()) {
                            shouldLeaveAndEnter = true;
                            break;
                        }
                    }
                }

                TruffleSafepoint.InterruptibleFunction<Void, Boolean> leaveAndEnterThreadInterrupter = (x) -> {
                    /*
                     * Threads might be deleted from the threads map while we iterate, so we have to
                     * store them separately.
                     */
                    PolyglotThreadInfo[] threadInfos = threads.values().toArray(new PolyglotThreadInfo[0]);
                    for (PolyglotThreadInfo threadInfo : threadInfos) {
                        if (!threadInfo.isCurrent() && threadInfo.isInLeaveAndEnter()) {
                            threadInfo.leaveAndEnterInterrupted = true;
                            threadInfo.getLeaveAndEnterInterrupter().interrupt(threadInfo.getThread());
                            if (!waitUntilFalse(() -> threadInfo.isInLeaveAndEnter() || threadInfo.isActive(), startMillis, timeoutMillis)) {
                                return false;
                            }
                            assert !threadInfo.isInLeaveAndEnter();
                        }
                    }

                    return true;
                };

                if (shouldLeaveAndEnter) {
                    if (!leaveAndEnter(DO_NOTHING_INTERRUPTER, leaveAndEnterThreadInterrupter, null, true)) {
                        return false;
                    }
                } else {
                    try {
                        if (!leaveAndEnterThreadInterrupter.apply(null)) {
                            return false;
                        }
                    } catch (InterruptedException ie) {
                        assert false;
                    }
                }

                return waitUntilFalse(() -> hasActiveOtherThread(false, true) || hasAliveOtherPolyglotThread(), startMillis, timeoutMillis);
            }
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

    boolean hasActiveOtherThread(boolean includePolyglotThreads, boolean includeLeaveAndEnterThreads) {
        assert Thread.holdsLock(this);
        // send enters and leaves into a lock by setting the lastThread to null.
        for (PolyglotThreadInfo otherInfo : threads.values()) {
            if (!includePolyglotThreads && otherInfo.isPolyglotThread(this)) {
                continue;
            }
            if (!otherInfo.isCurrent() && (otherInfo.isActive() || (includeLeaveAndEnterThreads && otherInfo.isInLeaveAndEnter()))) {
                return true;
            }
        }
        return false;
    }

    boolean hasAliveOtherPolyglotThread() {
        assert Thread.holdsLock(this);
        for (PolyglotLanguageContext context : contexts) {
            Set<PolyglotThread> contextOwnedAlivePolyglotThreads = context.getOwnedAlivePolyglotThreads();
            if (contextOwnedAlivePolyglotThreads != null) {
                for (Thread polyglotThread : contextOwnedAlivePolyglotThreads) {
                    if (Thread.currentThread() != polyglotThread && polyglotThread.isAlive()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void notifyThreadClosed(PolyglotThreadInfo info) {
        assert Thread.holdsLock(this);
        if (!info.cancelled) {
            // clear interrupted status after closingThread
            // needed because we interrupt when closingThread from another thread.
            info.cancelled = true;
            if (info.interruptSent) {
                Thread.interrupted();
            }
        }
        notifyAll();
    }

    long calculateHeapSize(long stopAtBytes, AtomicBoolean calculationCancelled) {
        ObjectSizeCalculator localObjectSizeCalculator;
        synchronized (this) {
            localObjectSizeCalculator = objectSizeCalculator;
            if (localObjectSizeCalculator == null) {
                localObjectSizeCalculator = new ObjectSizeCalculator();
                objectSizeCalculator = localObjectSizeCalculator;
            }
        }
        return localObjectSizeCalculator.calculateObjectSize(getAPIAccess(), getContextHeapRoots(), stopAtBytes, calculationCancelled);
    }

    private Object[] getContextHeapRoots() {
        List<Object> heapRoots = new ArrayList<>();
        addRootPointersForContext(heapRoots);
        addRootPointersForStackFrames(heapRoots);
        return heapRoots.toArray();
    }

    private void addRootPointersForStackFrames(List<Object> heapRoots) {
        PolyglotStackFramesRetriever.populateHeapRoots(this, heapRoots);
    }

    private void addRootPointersForContext(List<Object> heapRoots) {
        synchronized (this) {
            for (PolyglotLanguageContext context : contexts) {
                if (context.isCreated()) {
                    heapRoots.add(context.getContextImpl());
                }
            }
            if (polyglotBindings != null) {
                for (Map.Entry<String, Object> binding : polyglotBindings.entrySet()) {
                    heapRoots.add(binding.getKey());
                    if (binding.getValue() != null) {
                        heapRoots.add(getAPIAccess().getValueReceiver(binding.getValue()));
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

    /**
     * @return non-empty list of thread local action futures if this method sets the cancelling
     *         state or obtains the futures from cancellationOrExitingFutures, empty list otherwise.
     */
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
                exitMessage = null;
                setState(targetState);
                submitCancellationThreadLocalAction(futures);
                maybeSendInterrupt();
                childContextsToCancel = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
            }
        }
        if (childContextsToCancel != null) {
            assert !futures.isEmpty();
            for (PolyglotContextImpl childCtx : childContextsToCancel) {
                futures.addAll(childCtx.setCancelling(resourceLimit, message));
            }
        }
        return getCancellingOrExitingFutures(futures);
    }

    private void submitCancellationThreadLocalAction(List<Future<Void>> futures) {
        PolyglotThreadInfo info = getCurrentThreadInfo();
        futures.add(threadLocalActions.submit(null, PolyglotEngineImpl.ENGINE_ID, new CancellationThreadLocalAction(), true));
        if (info != PolyglotThreadInfo.NULL) {
            info.cancelled = true;
        }
        setCachedThreadInfo(PolyglotThreadInfo.NULL);
    }

    /**
     * @return non-empty list of thread local action futures if this method sets the exiting state
     *         or obtains the futures from cancellationOrExitingFutures, empty list otherwise. One
     *         exception to this rule is when config.useSystemExit == true, in that case the
     *         returned futures are also empty as the context won't be closed in the standard way.
     *         Instead, System.exit will be used to exit the whole VM.
     */
    private List<Future<Void>> setExiting(PolyglotContextImpl triggeringParent, int code, String message, boolean skipPendingExit) {
        PolyglotContextImpl[] childContextsToCancel = null;
        List<Future<Void>> futures = new ArrayList<>();
        synchronized (this) {
            if (!state.isInvalidOrClosed()) {
                assert message != null;
                State targetState;
                if (state.isClosing()) {
                    targetState = State.CLOSING_EXITING;
                } else {
                    targetState = State.EXITING;
                }
                this.skipPendingExit = skipPendingExit;
                invalidMessage = message;
                if (skipPendingExit) {
                    /*
                     * Setting the exiting state is supposed to match some other context we have no
                     * direct reference to (e.g. isolated context) that this context is driven by.
                     * This context is not being exited in the standard way and so it does not go
                     * through the PENDING_EXIT state.
                     */
                    exitMessage = message;
                    exitCode = code;
                }
                if (triggeringParent != null) {
                    /*
                     * triggeringParent is not null (and equal to parent) if the exit was initiated
                     * by some ancestor of this context (not necessarily the parent) and not this
                     * context directly. This means that the triggeringParent can be null even if
                     * parent is not null.
                     */
                    exitMessage = triggeringParent.exitMessage;
                    exitCode = triggeringParent.exitCode;
                }
                setState(targetState);
                if (!config.useSystemExit) {
                    submitCancellationThreadLocalAction(futures);
                    maybeSendInterrupt();
                }
                childContextsToCancel = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
            }
        }
        if (childContextsToCancel != null) {
            for (PolyglotContextImpl childCtx : childContextsToCancel) {
                futures.addAll(childCtx.setExiting(this, code, message, skipPendingExit));
            }
        }
        return getCancellingOrExitingFutures(futures);
    }

    private List<Future<Void>> getCancellingOrExitingFutures(List<Future<Void>> futures) {
        List<Future<Void>> toRet = futures;
        if (parent == null && toRet.isEmpty()) {
            initiateCancelOrExitLock.lock();
            try {
                if (cancellationOrExitingFutures != null) {
                    toRet = cancellationOrExitingFutures;
                    cancellationOrExitingFutures = null;
                }
            } finally {
                initiateCancelOrExitLock.unlock();
            }
        }
        return toRet;
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
            case EXITING:
                targetState = State.CLOSING_EXITING;
                break;
            default:
                targetState = State.CLOSING;
                break;
        }
        setState(targetState);
    }

    private void setFinalizingState() {
        assert Thread.holdsLock(this);
        assert closingThread == Thread.currentThread();
        assert closingLock.isHeldByCurrentThread();
        State targetState;
        switch (state) {
            case CLOSING:
                targetState = State.CLOSING_FINALIZING;
                break;
            case CLOSING_INTERRUPTING:
                targetState = State.CLOSING_INTERRUPTING_FINALIZING;
                break;
            default:
                return;
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
            case CLOSING_EXITING:
                targetState = State.CLOSED_EXITED;
                break;
            case CLOSING_INTERRUPTING_FINALIZING:
                targetState = State.CLOSED_INTERRUPTED;
                break;
            case CLOSING_FINALIZING:
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
            case CLOSING_INTERRUPTING_FINALIZING:
                targetState = State.INTERRUPTING;
                break;
            case CLOSING_CANCELLING:
                targetState = State.CANCELLING;
                break;
            case CLOSING_PENDING_EXIT:
                targetState = State.PENDING_EXIT;
                break;
            case CLOSING_EXITING:
                targetState = State.EXITING;
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
         * context will go back to the corresponsing non-closing state e.g. DEFAULT ->
         * CLOSING/CLOSING_FINALIZING -> DEFAULT. Please note that while the default/interrupting
         * close is in progress, i.e. the context is in the CLOSING/CLOSING_FINALIZING or the
         * CLOSING_INTERRUPTING/CLOSING_INTERRUPTING_FINALIZING state, the state can be overriden by
         * the CLOSING_CANCELLING state. The CLOSING and CLOSING_INTERRUPTING states can also be
         * overriden by the CLOSING_PENDING_EXIT and then the CLOSING_EXITING state. Even in these
         * cases the default close can still fail and if that is the case the context state goes
         * back to the CANCELLING or the EXITING state. The close operation is then guaranteed to be
         * completed by the process that initiated cancel or exit.
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
         * 4) The close was not yet performed, cancelling or exiting is not in progress, the context
         * is not in the PENDING_EXIT state, but other threads are still executing -> return false
         *
         * 5) The close was not yet performed and the context is in the PENDING_EXIT state -> wait
         * for the context to go to an invalid state (CANCELLING, EXITING, or their closing or
         * closed variants) and start checking from check 1).
         *
         * 6) The close was not yet performed and cancelling or exiting is in progress -> wait for
         * other threads to complete and start checking again from check 1) skipping check 6) (this
         * check) as no other threads can be executing anymore.
         *
         * 7) The close was not yet performed and no thread is executing -> perform close
         */
        boolean waitForClose = false;
        boolean finishCancelOrExit = false;
        boolean cancelOrExitOperation;
        acquireClosingLock: while (true) {
            if (waitForClose) {
                closingLock.lock();
                closingLock.unlock();
                waitForClose = false;
            }
            synchronized (this) {
                switch (state) {
                    case CLOSED:
                    case CLOSED_INTERRUPTED:
                    case CLOSED_CANCELLED:
                    case CLOSED_EXITED:
                        return true;
                    case CLOSING:
                    case CLOSING_FINALIZING:
                    case CLOSING_INTERRUPTING:
                    case CLOSING_INTERRUPTING_FINALIZING:
                    case CLOSING_CANCELLING:
                    case CLOSING_PENDING_EXIT:
                    case CLOSING_EXITING:
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
                    case PENDING_EXIT:
                        waitUntilInvalid();
                        continue acquireClosingLock;
                    case CANCELLING:
                    case EXITING:
                        assert cachedThreadInfo == PolyglotThreadInfo.NULL;
                        /*
                         * When cancelling or exiting, we have to wait for all other threads to
                         * complete - even for the the default close, otherwise the default close
                         * executed prematurely as the result of leaving the context on the main
                         * thread due to cancel exception could fail because of other threads still
                         * being active. The correct behavior is that the normal close finishes
                         * successfully and the cancel exception spreads further (if not caught
                         * before close is executed).
                         */
                        if (!finishCancelOrExit) {
                            waitForAllThreads(0, 0);
                            waitForClose = true;
                            finishCancelOrExit = true;
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
                         * completed by the thread that executes cancelling or exiting, because it
                         * might be waiting for this thread which would lead to a deadlock. Default
                         * close is allowed to be executed when entered. Also, this might be an
                         * inner context, which, even if not entered, might block a parent's thread
                         * which could be entered on the current thread.
                         */
                        setClosingState();
                        cancelOrExitOperation = true;
                        break acquireClosingLock;
                    case INTERRUPTING:
                    case DEFAULT:
                        Thread current = Thread.currentThread();
                        if (current instanceof SystemThread) {
                            throw PolyglotEngineException.illegalState("Context cannot be closed normally on a system thread. The context must be cancelled or exited.");
                        }
                        if (current instanceof PolyglotThread && !((PolyglotThread) current).isEnterAllowed()) {
                            throw PolyglotEngineException.illegalState(
                                            "Context cannot be closed normally in polyglot thread's beforeEnter or afterLeave notifications. The context must be cancelled or exited.");
                        }

                        if (hasActiveOtherThread(false, false)) {
                            /*
                             * We are not done executing, cannot close yet.
                             */
                            return false;
                        }
                        setClosingState();
                        cancelOrExitOperation = false;
                        break acquireClosingLock;
                    default:
                        assert false : state.name();
                }
            }
        }

        return finishClose(cancelOrExitOperation, notifyInstruments);
    }

    private void waitUntilInvalid() {
        boolean interrupted = false;
        while (!state.isInvalidOrClosed()) {
            try {
                wait();
            } catch (InterruptedException ie) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    synchronized void clearExplicitContextStack() {
        if (parent == null) {
            engine.polyglotHostService.notifyClearExplicitContextStack(this);
        }
        if (isActive(Thread.currentThread()) && !engine.getImpl().getRootImpl().isInCurrentEngineHostCallback(engine)) {
            PolyglotThreadInfo threadInfo = getCurrentThreadInfo();
            if (!threadInfo.explicitContextStack.isEmpty()) {
                PolyglotContextImpl c = this;
                while (!threadInfo.explicitContextStack.isEmpty()) {
                    if (PolyglotFastThreadLocals.getContext(null) == this) {
                        Object[] prev = threadInfo.explicitContextStack.removeLast();
                        engine.leave(prev, c);
                        c = prev != null ? (PolyglotContextImpl) prev[PolyglotFastThreadLocals.CONTEXT_INDEX] : null;
                    } else {
                        throw PolyglotEngineException.illegalState("Unable to automatically leave an explicitly entered context, some other context was entered in the meantime.");
                    }
                }
            }
        }
    }

    private boolean finishClose(boolean cancelOrExitOperation, boolean notifyInstruments) {
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
                boolean enterMustSuceed = cancelOrExitOperation;
                prev = this.enterThreadChanged(false, true, enterMustSuceed, false, false);
            } catch (Throwable t) {
                synchronized (this) {
                    restoreFromClosingState(cancelOrExitOperation);
                }
                throw t;
            }
            if (cancelOrExitOperation) {
                synchronized (this) {
                    /*
                     * Cancellation thread local action needs to be submitted here in case
                     * finalizeContext runs guest code.
                     */
                    threadLocalActions.submit(new Thread[]{Thread.currentThread()}, PolyglotEngineImpl.ENGINE_ID, new CancellationThreadLocalAction(), true);
                }
            }
            try {
                if (cancelOrExitOperation) {
                    closeChildContexts(notifyInstruments);
                } else {
                    exitContextNotification(TruffleLanguage.ExitMode.NATURAL, 0);
                }

                synchronized (this) {
                    assert state != State.CLOSING_FINALIZING && state != State.CLOSING_INTERRUPTING_FINALIZING;
                    setCachedThreadInfo(PolyglotThreadInfo.NULL);
                    setFinalizingState();
                    if (state == State.CLOSING_PENDING_EXIT) {
                        /*
                         * In case hard exit was triggered during the closing operation, we need to
                         * wait until the hard exit notifications are finished or cancelled by
                         * cancelling the whole context. Otherwise, we would execute the finalize
                         * notifications prematurely.
                         */
                        waitUntilInvalid();
                    }
                }

                finalizeContext(notifyInstruments, cancelOrExitOperation);

                // finalization performed commit close -> no reinitialization allowed

                disposedContexts = disposeContext();
                success = true;
            } finally {
                synchronized (this) {
                    /*
                     * The assert is synchronized because all accesses to childContexts must be
                     * synchronized. We cannot simply assert that childContexts are empty, because
                     * removing the child context from its parent childContexts list can be done in
                     * another thread after the assertion.
                     */
                    assert !success || getUnclosedChildContexts().isEmpty() : "Polyglot context close marked as successful, but there are unclosed child contexts.";
                    this.leaveThreadChanged(prev, true, false);
                    if (success) {
                        remainingThreads = threads.keySet().toArray(new Thread[0]);
                    }
                    if (success) {
                        setClosedState();
                    } else {
                        restoreFromClosingState(cancelOrExitOperation);
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
                 * might be needed in e.g. onLeaveThread events. Moreover, closing a non-invalid
                 * context does not prevent other threads from entering and, additionally, if the
                 * context becomes invalid after some other thread has entered, then
                 * PolyglotLanguageContext#dispose does not check for other entered main threads,
                 * and so the close operation (started when the context was still non-invalid) can
                 * proceed and reach this point. Therefore, we have to check for entered threads
                 * here, and in case there is any, we cannot close PolyglotLanguageContexts and
                 * clear locals.
                 */
                if (!isActive()) {
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
                if (this.config.logHandler != null && !PolyglotLoggers.haveSameTarget(this.config.logHandler, engine.logHandler)) {
                    this.config.logHandler.close();
                }
            }
        }
        return true;
    }

    private List<PolyglotContextImpl> getUnclosedChildContexts() {
        assert Thread.holdsLock(this);
        List<PolyglotContextImpl> unclosedChildContexts = new ArrayList<>();
        for (PolyglotContextImpl childCtx : childContexts) {
            if (!childCtx.state.isClosed()) {
                unclosedChildContexts.add(childCtx);
            }
        }
        return unclosedChildContexts;
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

    @SuppressWarnings("serial")
    static final class ExitException extends ThreadDeath {
        private static final long serialVersionUID = -4838571769179260137L;

        private final Node location;
        private final SourceSection sourceSection;
        private final String exitMessage;
        private final int exitCode;

        ExitException(Node location, int exitCode, String exitMessage) {
            this(location, null, exitCode, exitMessage);
        }

        ExitException(SourceSection sourceSection, int exitCode, String exitMessage) {
            this(null, sourceSection, exitCode, exitMessage);
        }

        private ExitException(Node location, SourceSection sourceSection, int exitCode, String exitMessage) {
            this.location = location;
            this.sourceSection = sourceSection;
            this.exitCode = exitCode;
            this.exitMessage = exitMessage;
        }

        Node getLocation() {
            return location;
        }

        SourceSection getSourceLocation() {
            if (sourceSection != null) {
                return sourceSection;
            }
            return location == null ? null : location.getEncapsulatingSourceSection();
        }

        @Override
        public String getMessage() {
            return exitMessage;
        }

        int getExitCode() {
            return exitCode;
        }
    }

    private boolean setPendingExit(int code) {
        synchronized (this) {
            State targetState;
            switch (state) {
                case DEFAULT:
                case INTERRUPTING:
                    targetState = State.PENDING_EXIT;
                    break;
                case CLOSING:
                case CLOSING_INTERRUPTING:
                    targetState = State.CLOSING_PENDING_EXIT;
                    break;
                default:
                    return false;
            }
            exitCode = code;
            exitMessage = "Exit was called with exit code " + code + ".";
            closeExitedTriggerThread = Thread.currentThread();
            setState(targetState);
            return true;
        }
    }

    void closeExited(Node exitLocation, int code) {
        if (setPendingExit(code)) {
            /*
             * If this thread set PENDING_EXIT state and ran exit notifications, it will also be the
             * one to execute the transition to EXITING state, unless the exit notifications were
             * cancelled by cancelling the whole context.
             */
            exitContextNotification(TruffleLanguage.ExitMode.HARD, code);
            if (parent == null) {
                engine.polyglotHostService.notifyContextCancellingOrExiting(this, true, code, false, exitMessage);
            }
            List<Future<Void>> futures = setExiting(null, code, exitMessage, false);
            if (!futures.isEmpty()) {
                closeHereOrCancelInCleanupThread(futures);
            }
        } else {
            synchronized (this) {
                if (!state.isInvalidOrClosed()) {
                    /*
                     * Normally, if closeExited is called more than once, the subsequent calls wait
                     * until the context is invalid, which means that either the first call to
                     * closeExited finished running the exit notifications and set the context state
                     * to the (invalid) state EXITING, or the context was cancelled during the exit
                     * notifications and it is in the (invalid) state CANCELLING. However, we cannot
                     * wait for the invalid state when closeExited is called from an exit
                     * notification because the invalid state is only set when exit notifications
                     * are finished or cancelled, and so in these cases, we throw the exit exception
                     * immediately.
                     */
                    PolyglotThreadInfo info = getCurrentThreadInfo();
                    if (closeExitedTriggerThread == info.getThread() || (info.isPolyglotThread(this) && ((PolyglotThread) info.getThread()).hardExitNotificationThread)) {
                        throw createExitException(exitLocation);
                    }
                }
            }
        }

        /*
         * It is possible that the context is not invalid, but the exit operation was not allowed,
         * because the context is being closed in which case the state is neither invalid nor
         * PENDING_EXIT. In this case the closeExited operation is a no-op.
         */
        State localState = state;
        Node location = exitLocation != null ? exitLocation : uncachedLocation;
        if (localState == State.PENDING_EXIT || localState == State.CLOSING_PENDING_EXIT || localState.isInvalidOrClosed()) {
            /*
             * Wait for the context to become invalid. If this is the first call to closeExited that
             * ran the exit notifications and set the exiting state, then the context is already
             * invalid, otherwise we wait here until the first call to closeExited has done its job.
             */
            TruffleSafepoint.setBlockedThreadInterruptible(location, new TruffleSafepoint.Interruptible<PolyglotContextImpl>() {
                @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
                @Override
                public void apply(PolyglotContextImpl ctx) throws InterruptedException {
                    synchronized (ctx) {
                        while (!ctx.state.isInvalidOrClosed()) {
                            ctx.wait();
                        }
                    }
                }
            }, this);

            localState = state;
            if (config.useSystemExit && (localState.isExiting() || localState == State.CLOSED_EXITED)) {
                engine.host.hostExit(exitCode);
            }
            /*
             * Poll will throw the correct exception. Either the ThreadDeath exit or the ThreadDeath
             * cancel exception based on whether the exit notifications were finished and the hard
             * exit can be completed, or the context was cancelled during exit notifications.
             */
            TruffleSafepoint.pollHere(location);
        }
    }

    private void closeHereOrCancelInCleanupThread(List<Future<Void>> futures) {
        boolean cancelInSeparateThread = false;
        synchronized (this) {
            PolyglotThreadInfo info = getCurrentThreadInfo();
            Thread currentThread = Thread.currentThread();
            if (info.isPolyglotThread(this) || (!singleThreaded && isActive(currentThread)) || closingThread == currentThread || currentThread instanceof SystemThread) {
                /*
                 * Polyglot thread or system thread must not cancel a context, because cancel waits
                 * for polyglot threads and system threads to complete. Also, it is not allowed to
                 * cancel in a thread where a multi-threaded context is entered. This would lead to
                 * deadlock if more than one thread tried to do that as cancel waits for the context
                 * not to be entered in all other threads.
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
                        PolyglotEngineImpl.cancelOrExit(PolyglotContextImpl.this, futures);
                    }
                });
            }
        } else {
            closeAndMaybeWait(true, futures);
        }
    }

    private void registerCleanupTask(Runnable cleanupTask) {
        synchronized (this) {
            if (!state.isClosed()) {
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
    }

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
            boolean interrupted = false;
            try {
                try {
                    cleanupFuture.get();
                } catch (InterruptedException ie) {
                    interrupted = true;
                    engine.getEngineLogger().log(Level.INFO, "Waiting for polyglot context cleanup was interrupted!", ie);
                } catch (ExecutionException ee) {
                    assert !(ee.getCause() instanceof AbstractTruffleException);
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
                        interrupted = true;
                        engine.getEngineLogger().log(Level.INFO, "Waiting for polyglot context cleanup was interrupted!", ie);
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
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
        for (int i = contexts.length - 1; i >= 0; i--) {
            PolyglotLanguageContext context = contexts[i];
            boolean disposed = context.dispose();
            if (disposed) {
                disposedContexts.add(context);
            }
        }
        Closeable[] toClose;
        synchronized (this) {
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

    private void exitContextNotification(TruffleLanguage.ExitMode exitMode, int code) {
        // we need to run exit notifications at least twice in case an exit notification run has
        // initialized new contexts
        boolean exitNotificationPerformed;
        try {
            do {
                exitNotificationPerformed = false;
                for (int i = contexts.length - 1; i >= 0; i--) {
                    PolyglotLanguageContext context = contexts[i];
                    if (context.isInitialized()) {
                        exitNotificationPerformed |= context.exitContext(exitMode, code);
                    }
                }
            } while (exitNotificationPerformed);
        } catch (Throwable t) {
            if (exitMode == TruffleLanguage.ExitMode.NATURAL || !(t instanceof CancelExecution)) {
                throw t;
            } else {
                engine.getEngineLogger().log(Level.FINE, "Execution was cancelled during exit notifications!", t);
            }
        }

    }

    private void finalizeContext(boolean notifyInstruments, boolean mustSucceed) {
        // we need to run finalization at least twice in case a finalization run has
        // initialized new contexts
        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        boolean prevChangeAllowActions = PolyglotThreadLocalActions.TL_HANDSHAKE.setChangeAllowActions(safepoint, true);
        try {
            boolean finalizationPerformed;
            do {
                finalizationPerformed = false;
                // inverse context order is already the right order for context
                // disposal/finalization
                for (int i = contexts.length - 1; i >= 0; i--) {
                    PolyglotLanguageContext context = contexts[i];
                    if (context.isInitialized()) {
                        try {
                            finalizationPerformed |= context.finalizeContext(mustSucceed, notifyInstruments);
                        } finally {
                            if (!PolyglotThreadLocalActions.TL_HANDSHAKE.isAllowActions(safepoint)) {
                                safepoint.setAllowActions(true);
                                throw new IllegalStateException(
                                                "TruffleSafepoint.setAllowActions is still disabled even though finalization completed. Make sure allow actions are reset in a finally block.");
                            }
                        }
                    }
                }
            } while (finalizationPerformed);
        } finally {
            PolyglotThreadLocalActions.TL_HANDSHAKE.setChangeAllowActions(safepoint, prevChangeAllowActions);
        }

        List<PolyglotContextImpl> unclosedChildContexts;
        synchronized (this) {
            unclosedChildContexts = getUnclosedChildContexts();
        }
        for (PolyglotContextImpl childCtx : unclosedChildContexts) {
            if (childCtx.isActive()) {
                throw new IllegalStateException("There is an active child contexts after finalizeContext!");
            }
        }
        if (!unclosedChildContexts.isEmpty()) {
            closeChildContexts(notifyInstruments);
        }

        assert !finalizingEmbedderThreads;
        finalizingEmbedderThreads = true;
        try {
            /*
             * finalizing embedder and non-owned polyglot threads, all language contexts are
             * finalized but still usable. Creation of new threads and initializing new language
             * contexts is no longer allowed.
             */
            PolyglotThreadInfo[] embedderThreads;
            Throwable ex = null;
            synchronized (this) {
                embedderThreads = getSeenThreads().values().stream().filter(threadInfo -> !threadInfo.isPolyglotThread(this)).toList().toArray(new PolyglotThreadInfo[0]);
            }
            for (PolyglotThreadInfo threadInfo : embedderThreads) {
                ex = notifyThreadFinalizing(threadInfo, ex);
            }
            if (ex != null) {
                if (!mustSucceed || isInternalError(ex)) {
                    sneakyThrow(ex);
                } else {
                    engine.getEngineLogger().log(Level.FINE,
                                    "Exception was thrown while finalizing a non-polyglot thread for a context that is being cancelled or exited. Such exceptions are expected during cancelling or exiting.",
                                    ex);
                }

            }
        } finally {
            finalizingEmbedderThreads = false;
        }
    }

    synchronized void maybeSendInterrupt() {
        if (!state.isInterrupting() && !state.isCancelling() && !state.isExiting()) {
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
                threadInfo.interruptSent = true;
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
        initializeInstrumentContextLocals(locals);
        /*
         * Languages will be initialized in PolyglotLanguageContext#ensureCreated().
         */
        assert this.contextLocals == null;
        this.contextLocals = locals;
    }

    void initializeInstrumentContextLocals(Object[] locals) {
        for (PolyglotInstrument instrument : engine.idToInstrument.values()) {
            if (instrument.isCreated()) {
                invokeContextLocalsFactory(locals, instrument.contextLocalLocations);
            }
        }
    }

    void initializeInstrumentContextThreadLocals() {
        for (PolyglotInstrument instrument : engine.idToInstrument.values()) {
            if (instrument.isCreated()) {
                invokeContextThreadLocalFactory(instrument.contextThreadLocalLocations);
            }
        }
    }

    void invokeLocalsFactories(LocalLocation[] contextLocalLocations, LocalLocation[] contextThreadLocalLocations) {
        PolyglotContextImpl[] localChildContexts;
        synchronized (this) {
            if (localsCleared) {
                return;
            }
            /*
             * contextLocals might not be initialized yet, in which case the context local factory
             * for this instrument will be invoked during contextLocals initialization.
             */
            if (contextLocals != null) {
                invokeContextLocalsFactory(contextLocals, contextLocalLocations);
                invokeContextThreadLocalFactory(contextThreadLocalLocations);
            }
            localChildContexts = PolyglotContextImpl.this.childContexts.toArray(new PolyglotContextImpl[0]);
        }
        for (PolyglotContextImpl childCtx : localChildContexts) {
            childCtx.invokeLocalsFactories(contextLocalLocations, contextThreadLocalLocations);
        }
    }

    void resizeThreadLocals(StableLocalLocations locations) {
        PolyglotContextImpl[] localChildContexts;
        synchronized (this) {
            if (localsCleared) {
                return;
            }
            resizeContextThreadLocals(locations);
            localChildContexts = PolyglotContextImpl.this.childContexts.toArray(new PolyglotContextImpl[0]);
        }
        for (PolyglotContextImpl childCtx : localChildContexts) {
            childCtx.resizeThreadLocals(locations);
        }
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

    void resizeLocals(StableLocalLocations locations) {
        PolyglotContextImpl[] localChildContexts;
        synchronized (this) {
            if (localsCleared) {
                return;
            }
            resizeContextLocals(locations);
            localChildContexts = PolyglotContextImpl.this.childContexts.toArray(new PolyglotContextImpl[0]);
        }
        for (PolyglotContextImpl childCtx : localChildContexts) {
            childCtx.resizeLocals(locations);
        }
    }

    void resizeContextLocals(StableLocalLocations locations) {
        assert Thread.holdsLock(this);
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
        if (PreInitContextHostLanguage.isInstance(contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX].language)) {
            maybeInitializeHostLanguage(contexts);
        }
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
        PolyglotSharingLayer.Shared s = layer.shared;
        if (s != null) {
            s.sourceCache.patch(TracingSourceCacheListener.createOrNull(engine));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    void initializeHostContext(PolyglotLanguageContext context, PolyglotContextConfig newConfig) {
        Object contextImpl = context.getContextImpl();
        if (contextImpl == null) {
            throw new AssertionError("Host context not initialized.");
        }
        this.hostContextImpl = contextImpl;

        AbstractHostLanguageService currentHost = engine.host;
        AbstractHostLanguageService newHost = context.lookupService(AbstractHostLanguageService.class);
        if (newHost == null) {
            throw new AssertionError("The engine host language must register a service of type:" + AbstractHostLanguageService.class);
        }
        if (currentHost == null) {
            engine.host = newHost;
        } else if (currentHost != newHost) {
            throw new AssertionError("Host service must not change per engine.");
        }
        newHost.initializeHostContext(this, contextImpl, newConfig.hostAccess, newConfig.hostClassLoader, newConfig.classFilter, newConfig.hostClassLoadingAllowed,
                        newConfig.hostLookupAllowed);
    }

    void replayInstrumentationEvents() {
        notifyContextCreated();
        EngineAccessor.INSTRUMENT.notifyThreadStarted(engine, creatorTruffleContext, Thread.currentThread());
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

    private synchronized void checkSubProcessFinished() {
        ProcessHandlers.ProcessDecorator[] processes = subProcesses.toArray(new ProcessHandlers.ProcessDecorator[subProcesses.size()]);
        for (ProcessHandlers.ProcessDecorator process : processes) {
            if (process.isAlive()) {
                throw new IllegalStateException(String.format("The context has an alive sub-process %s created by %s.",
                                process.getCommand(), process.getOwner().language.getId()));
            }
        }
    }

    private synchronized void checkSystemThreadsFinished() {
        if (!activeSystemThreads.isEmpty()) {
            LanguageSystemThread thread = activeSystemThreads.iterator().next();
            throw new IllegalStateException(String.format("The context has an alive system thread %s created by language %s.", thread.getName(), thread.languageId));
        }
    }

    static PolyglotContextImpl preinitialize(final PolyglotEngineImpl engine, final PreinitConfig preinitConfig, PolyglotSharingLayer sharableLayer, Set<PolyglotLanguage> languagesToPreinitialize,
                    boolean emitWarning) {
        String tmpDir = System.getProperty("java.io.tmpdir");
        final FileSystemConfig fileSystemConfig = new FileSystemConfig(engine.getAPIAccess().getIOAccessAll(), new PreInitializeContextFileSystem(tmpDir), new PreInitializeContextFileSystem(tmpDir));
        final PolyglotContextConfig config = new PolyglotContextConfig(engine, fileSystemConfig, preinitConfig);
        final PolyglotContextImpl context = new PolyglotContextImpl(engine, config);
        synchronized (engine.lock) {
            engine.addContext(context);
        }

        context.sourcesToInvalidate = new ArrayList<>();

        try {

            if (sharableLayer != null) {
                if (!context.claimSharingLayer(sharableLayer, languagesToPreinitialize)) {
                    // could not claim layer. cannot preinitialize context.
                    return null;
                }
            }

            synchronized (context) {
                context.initializeContextLocals();
            }

            if (!languagesToPreinitialize.isEmpty()) {
                Object[] prev = context.engine.enter(context);
                try {
                    for (PolyglotLanguage language : languagesToPreinitialize) {
                        assert language.engine == engine : "invalid language";

                        if (overridesPatchContext(language.getId())) {
                            context.getContextInitialized(language, null);
                            LOG.log(Level.FINE, "Pre-initialized context for language: {0}", language.getId());
                        } else {
                            if (emitWarning) {
                                LOG.log(Level.WARNING, "Language {0} cannot be pre-initialized as it does not override TruffleLanguage.patchContext method.", language.getId());
                            }
                        }
                    }

                } finally {
                    context.leaveThreadChanged(prev, true, true);
                }
            }
            return context;
        } finally {

            for (PolyglotLanguage language : engine.languages) {
                if (language != null) {
                    language.clearOptionValues();
                }
            }
            synchronized (engine.lock) {
                engine.removeContext(context);
            }
            for (Source sourceToInvalidate : context.sourcesToInvalidate) {
                EngineAccessor.SOURCE.invalidateAfterPreinitialiation(sourceToInvalidate);
            }
            context.singleThreadValue.reset();
            context.sourcesToInvalidate = null;
            context.threadLocalActions.prepareContextStore();
            Map<String, Path> languageHomes = engine.languageHomes();
            ((PreInitializeContextFileSystem) fileSystemConfig.fileSystem).onPreInitializeContextEnd(engine.internalResourceRoots, languageHomes);
            ((PreInitializeContextFileSystem) fileSystemConfig.internalFileSystem).onPreInitializeContextEnd(engine.internalResourceRoots, languageHomes);
            if (!config.logLevels.isEmpty()) {
                EngineAccessor.LANGUAGE.configureLoggers(context, null, context.getAllLoggers());
            }
        }
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
        volatile PolyglotSharingLayer layer;

        ContextWeakReference(PolyglotContextImpl referent) {
            super(referent, referent.engine.contextsReferenceQueue);
        }

        void freeSharing(PolyglotContextImpl context) {
            if (context != null) {
                assert layer == null || layer.equals(context.layer);
            }
            if (layer != null && layer.isClaimed()) {
                layer.engine.freeSharingLayer(layer, context);
            }
        }

    }

    private CancelExecution createCancelException(Node location) {
        return new CancelExecution(location, invalidMessage, invalidResourceLimit);
    }

    private ExitException createExitException(Node location) {
        return new ExitException(location, exitCode, exitMessage);
    }

    private static boolean overridesPatchContext(String languageId) {
        if (TruffleOptions.AOT) {
            return LanguageCache.overridesPathContext(languageId);
        } else {
            // Used by context pre-initialization tests on HotSpot
            LanguageCache cache = LanguageCache.languages().get(languageId);
            for (Method m : cache.loadLanguage().getClass().getDeclaredMethods()) {
                if (m.getName().equals("patchContext")) {
                    return true;
                }
            }
            return false;
        }
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
        b.append(",disposing=");
        b.append(disposing);
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

    private static final class UncachedLocationNode extends HostToGuestRootNode {

        UncachedLocationNode(PolyglotSharingLayer layer) {
            super(layer);
        }

        @Override
        protected Class<?> getReceiverType() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public boolean isInternal() {
            return true;
        }

    }

    private final class CancellationThreadLocalAction extends ThreadLocalAction {
        CancellationThreadLocalAction() {
            super(false, false);
        }

        @Override
        protected void perform(Access access) {
            PolyglotContextImpl.this.threadLocalActions.submit(new Thread[]{access.getThread()}, PolyglotEngineImpl.ENGINE_ID, this, new HandshakeConfig(true, false, false, true));

            State localState = PolyglotContextImpl.this.state;
            if (localState.isCancelling() || localState.isExiting() || localState == State.CLOSED_CANCELLED || localState == State.CLOSED_EXITED) {
                if (localState.isExiting() || localState == State.CLOSED_EXITED) {
                    throw createExitException(access.getLocation());
                } else {
                    throw createCancelException(access.getLocation());
                }
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
                if (localState.isInterrupting() || localState == State.CLOSED_INTERRUPTED) {
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

    @TruffleBoundary
    void runOnCancelled() {
        Runnable onCancelledRunnable = config.onCancelled;
        if (onCancelledRunnable != null) {
            onCancelledRunnable.run();
        }
    }

    @TruffleBoundary
    void runOnExited(int code) {
        Consumer<Integer> onExitedRunnable = config.onExited;
        if (onExitedRunnable != null) {
            onExitedRunnable.accept(code);
        }
    }

    @TruffleBoundary
    void runOnClosed() {
        Runnable onClosedRunnable = config.onClosed;
        if (onClosedRunnable != null) {
            onClosedRunnable.run();
        }
    }

    synchronized void addSystemThread(LanguageSystemThread thread) {
        if (!state.isClosed()) {
            activeSystemThreads.add(thread);
        }
    }

    synchronized void removeSystemThread(LanguageSystemThread thread) {
        activeSystemThreads.remove(thread);
    }

}
