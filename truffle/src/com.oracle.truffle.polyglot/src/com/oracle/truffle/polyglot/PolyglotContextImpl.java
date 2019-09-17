/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.collections.EconomicSet;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractContextImpl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor.CastUnsafe;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;

final class PolyglotContextImpl extends AbstractContextImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    /**
     * This class isolates static state to optimize when only a single context is used. This
     * simplifies resetting state in AOT mode during native image generation.
     */
    static final class SingleContextState {
        private final ContextThreadLocal contextThreadLocal = new ContextThreadLocal();
        private final Assumption singleContextAssumption = Truffle.getRuntime().createAssumption("Single Context");
        @CompilationFinal private volatile PolyglotContextImpl singleContext;
    }

    @CompilationFinal private static SingleContextState singleContextState = new SingleContextState();

    /*
     * Used from testing using reflection. Its invalid to call it anywhere else than testing. Used
     * in ContextLookupCompilationTest and EngineAPITest.
     */
    static Object resetSingleContextState() {
        SingleContextState prev = singleContextState;
        singleContextState = new SingleContextState();
        return prev;
    }

    /*
     * Used from testing using reflection. Its invalid to call it anywhere else than testing. Used
     * in EngineAPITest.
     */
    static void restoreSingleContextState(Object state) {
        singleContextState = (SingleContextState) state;
    }

    /*
     * Used from testing using reflection. Its invalid to call it anywhere else than testing. Used
     * in EngineAPITest.
     */
    static boolean isSingleContextAssumptionValid() {
        return singleContextState.singleContextAssumption.isValid();
    }

    private static final Object NO_ENTER = new Object();

    final Assumption singleThreaded = Truffle.getRuntime().createAssumption("Single threaded");
    private final Assumption singleThreadedConstant = Truffle.getRuntime().createAssumption("Single threaded constant thread");
    private final Map<Thread, PolyglotThreadInfo> threads = new HashMap<>();

    private volatile PolyglotThreadInfo currentThreadInfo = PolyglotThreadInfo.NULL;
    @CompilationFinal private volatile PolyglotThreadInfo constantCurrentThreadInfo = PolyglotThreadInfo.NULL;

    /*
     * While canceling the context can no longer be entered. The context goes from canceling into
     * closed state.
     */
    volatile boolean cancelling;
    volatile Thread closingThread;
    private final ReentrantLock closingLock = new ReentrantLock();
    /*
     * If the context is closed all operations should fail with IllegalStateException.
     */
    volatile boolean closed;
    volatile boolean disposing;
    final PolyglotEngineImpl engine;
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;
    /* Duplicated context impl array for efficient context lookup. */
    @CompilationFinal(dimensions = 1) final Object[] contextImpls;

    Context creatorApi; // effectively final
    Context currentApi; // effectively final

    final TruffleContext truffleContext;
    final PolyglotContextImpl parent;
    final Map<String, Value> polyglotBindings; // for direct legacy access
    final Value polyglotHostBindings; // for accesses from the polyglot api
    final PolyglotLanguage creator; // creator for internal contexts
    final Map<String, Object> creatorArguments; // special arguments for internal contexts
    final ContextWeakReference weakReference;
    final Set<ProcessHandlers.ProcessDecorator> subProcesses;

    @CompilationFinal PolyglotContextConfig config; // effectively final

    // map from class to language index
    @CompilationFinal private volatile FinalIntMap languageIndexMap;

    private final List<PolyglotContextImpl> childContexts = new ArrayList<>();
    boolean inContextPreInitialization; // effectively final

    /* Constructor for testing. */
    private PolyglotContextImpl() {
        super(null);
        this.engine = null;
        this.contexts = null;
        this.contextImpls = null;
        this.truffleContext = null;
        this.parent = null;
        this.polyglotHostBindings = null;
        this.polyglotBindings = null;
        this.creator = null;
        this.creatorArguments = null;
        this.weakReference = null;
        this.subProcesses = new HashSet<>();
    }

    /*
     * Constructor for outer contexts.
     */
    PolyglotContextImpl(PolyglotEngineImpl engine, PolyglotContextConfig config) {
        super(engine.impl);
        this.parent = null;
        this.engine = engine;
        this.config = config;
        this.creator = null;
        this.creatorArguments = Collections.emptyMap();
        this.truffleContext = EngineAccessor.LANGUAGE.createTruffleContext(this);
        this.polyglotBindings = new ConcurrentHashMap<>();
        this.weakReference = new ContextWeakReference(this);
        this.contextImpls = new Object[engine.contextLength];
        this.contexts = createContextArray();
        if (!config.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, config.logLevels, getAllLoggers(engine));
        }
        PolyglotLanguageContext hostContext = getContextInitialized(engine.hostLanguage, null);
        this.polyglotHostBindings = getAPIAccess().newValue(polyglotBindings, new PolyglotBindingsValue(hostContext));
        this.subProcesses = new HashSet<>();
        notifyContextCreated();
        PolyglotContextImpl.initializeStaticContext(this);
    }

    /*
     * Constructor for inner contexts.
     */
    @SuppressWarnings("hiding")
    PolyglotContextImpl(PolyglotLanguageContext creator, Map<String, Object> langConfig, TruffleContext spiContext) {
        super(creator.getEngine().impl);
        PolyglotContextImpl parent = creator.context;
        this.parent = parent;
        this.config = parent.config;
        this.engine = parent.engine;
        this.creator = creator.language;
        this.creatorArguments = langConfig;
        this.weakReference = new ContextWeakReference(this);
        this.parent.addChildContext(this);
        this.truffleContext = spiContext;
        this.polyglotBindings = new ConcurrentHashMap<>();
        if (!parent.config.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, parent.config.logLevels, getAllLoggers(engine));
        }
        this.contextImpls = new Object[engine.contextLength];
        this.contexts = createContextArray();

        this.polyglotHostBindings = getAPIAccess().newValue(polyglotBindings, new PolyglotBindingsValue(getHostContext()));
        this.subProcesses = new HashSet<>();
        // notifyContextCreated() is called after spiContext.impl is set to this.
        this.engine.noInnerContexts.invalidate();
        initializeStaticContext(this);
    }

    private PolyglotLanguageContext[] createContextArray() {
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        PolyglotLanguageContext[] newContexts = new PolyglotLanguageContext[engine.contextLength];
        Iterator<PolyglotLanguage> languageIterator = languages.iterator();
        PolyglotLanguageContext hostContext = new PolyglotLanguageContext(this, engine.hostLanguage);
        newContexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = hostContext;
        for (int i = (PolyglotEngineImpl.HOST_LANGUAGE_INDEX + 1); i < engine.contextLength; i++) {
            PolyglotLanguage language = languageIterator.next();
            newContexts[i] = new PolyglotLanguageContext(this, language);
        }
        hostContext.ensureInitialized(null);
        ((HostContext) hostContext.getContextImpl()).internalContext = hostContext;
        return newContexts;
    }

    /**
     * Marks a context used globally. Potentially invalidating the global single context assumption.
     */
    static void initializeStaticContext(PolyglotContextImpl context) {
        SingleContextState state = singleContextState;
        if (state.singleContextAssumption.isValid()) {
            synchronized (state) {
                if (state.singleContextAssumption.isValid()) {
                    if (state.singleContext != null) {
                        state.singleContextAssumption.invalidate();
                        state.singleContext = null;
                    } else {
                        state.singleContext = context;
                    }
                }
            }
        }
    }

    /**
     * Marks all code from this context as unusable. It's important that a context is only disposed
     * when there is no code that could rely on the singleContextAssumption.
     */
    static void disposeStaticContext(PolyglotContextImpl context) {
        SingleContextState state = singleContextState;
        if (state.singleContextAssumption.isValid()) {
            synchronized (state) {
                if (state.singleContextAssumption.isValid()) {
                    assert state.singleContext == context;
                    state.singleContext = null;
                }
            }
        }
    }

    /**
     * Invalidates the global single context assumption when creating an unbound Engine.
     */
    static void invalidateStaticContextAssumption() {
        SingleContextState state = singleContextState;
        if (state.singleContextAssumption.isValid()) {
            synchronized (state) {
                if (state.singleContextAssumption.isValid()) {
                    state.singleContextAssumption.invalidate();
                    state.singleContext = null;
                }
            }
        }
    }

    PolyglotLanguageContext getContext(PolyglotLanguage language) {
        return contexts[language.index];
    }

    Object getContextImpl(PolyglotLanguage language) {
        assert contextImpls.length == engine.contextLength;
        Object contextImpl;
        if (CompilerDirectives.inInterpreter()) {
            contextImpl = contextImpls[language.index];
        } else {
            CompilerAsserts.partialEvaluationConstant(language);
            CastUnsafe unsafe = language.engine.castUnsafe;
            contextImpl = unsafe.castArrayFixedLength(contextImpls, language.engine.contextLength)[language.index];
            Class<?> castClass = language.contextClass;
            contextImpl = unsafe.unsafeCast(contextImpl, castClass, true, castClass != Void.class, true);
        }
        assert language.contextClass == (contextImpl == null ? Void.class : contextImpl.getClass()) : "Instable context class";
        return contextImpl;
    }

    PolyglotLanguageContext getContextInitialized(PolyglotLanguage language, PolyglotLanguage accessingLanguage) {
        PolyglotLanguageContext context = getContext(language);
        context.ensureInitialized(accessingLanguage);
        return context;
    }

    void notifyContextCreated() {
        EngineAccessor.INSTRUMENT.notifyContextCreated(engine, truffleContext);
    }

    private synchronized void addChildContext(PolyglotContextImpl child) {
        if (closingThread != null) {
            throw new IllegalStateException("Adding child context into a closing context.");
        }
        childContexts.add(child);
    }

    static PolyglotContextImpl current() {
        return currentEntered(null);
    }

    private static PolyglotContextImpl currentEntered(PolyglotEngineImpl engine) {
        SingleContextState singleContext = singleContextState;
        if (singleContext.singleContextAssumption.isValid()) {
            if (singleContext.contextThreadLocal.isSet()) {
                return singleContext.singleContext;
            } else {
                CompilerDirectives.transferToInterpreter();
                return null;
            }
        } else {
            ContextThreadLocal local = singleContext.contextThreadLocal;
            if (engine != null && engine.singleThread.isValid()) {
                return (PolyglotContextImpl) local.getNoThreadCheck();
            } else {
                return (PolyglotContextImpl) local.get();
            }
        }
    }

    /**
     * Must only be used to lookup the context if entered in an engine.
     */
    static PolyglotContextImpl requireContextEntered(PolyglotEngineImpl engine) {
        CompilerAsserts.partialEvaluationConstant(engine);
        PolyglotContextImpl context = currentEntered(engine);
        assert context != null : "No current context available.";
        return context;
    }

    /**
     * May be used anywhere to lookup the context.
     */
    static PolyglotContextImpl requireContext() {
        PolyglotContextImpl context = currentEntered(null);
        assert context != null : "No current context available.";
        return context;
    }

    @Override
    public synchronized void explicitEnter(Context sourceContext) {
        checkCreatorAccess(sourceContext, "entered");
        Object prev = enter();
        PolyglotThreadInfo current = getCurrentThreadInfo();
        assert current.thread == Thread.currentThread();
        current.explicitContextStack.addLast(prev);
    }

    @Override
    public synchronized void explicitLeave(Context sourceContext) {
        checkCreatorAccess(sourceContext, "left");
        PolyglotThreadInfo current = getCurrentThreadInfo();
        LinkedList<Object> stack = current.explicitContextStack;
        if (stack.isEmpty() || current.thread == null) {
            throw new IllegalStateException("The context is not entered explicity. A context can only be left if it was previously entered.");
        }
        leave(stack.removeLast());
    }

    private void checkCreatorAccess(Context context, String operation) {
        if (context != creatorApi) {
            throw new IllegalStateException(String.format("Context instances that were received using Context.get() cannot be %s.", operation));
        }
    }

    boolean needsEnter() {
        if (singleContextState.singleContextAssumption.isValid()) {
            // if its a single context we know which one to enter
            return !singleContextState.contextThreadLocal.isSet();
        } else {
            return current() != this;
        }
    }

    PolyglotThreadInfo getCachedThreadInfo() {
        return singleThreadedConstant.isValid() ? constantCurrentThreadInfo : currentThreadInfo;
    }

    Object enterIfNeeded() {
        if (needsEnter()) {
            return enter();
        }
        return NO_ENTER;
    }

    void leaveIfNeeded(Object prev) {
        if (prev != NO_ENTER) {
            leave(prev);
        }
    }

    Object enter() {
        Object context;
        PolyglotThreadInfo info = getCachedThreadInfo();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.thread == Thread.currentThread())) {
            // fast-path -> same thread
            context = singleContextState.contextThreadLocal.setReturnParent(this);
            info.enter();
        } else {
            // slow path -> changed thread
            if (singleThreaded.isValid()) {
                CompilerDirectives.transferToInterpreter();
            }
            context = enterThreadChanged();
        }
        assert this == current();
        return context;
    }

    void leave(Object prev) {
        assert current() == this : "Cannot leave context that is currently not entered. Forgot to enter or leave a context?";
        PolyglotThreadInfo info = getCachedThreadInfo();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.thread == Thread.currentThread())) {
            info.leave();
        } else {
            if (singleThreaded.isValid()) {
                CompilerDirectives.transferToInterpreter();
            }
            leaveThreadChanged();
        }
        singleContextState.contextThreadLocal.set(prev);
    }

    @TruffleBoundary
    PolyglotContextImpl enterThreadChanged() {
        Thread current = Thread.currentThread();
        PolyglotContextImpl prev;
        boolean needsInitialization = false;
        synchronized (this) {
            engine.checkState();
            checkClosed();
            PolyglotThreadInfo threadInfo = getCurrentThreadInfo();
            assert threadInfo != null;

            threadInfo = threads.get(current);
            if (threadInfo == null) {
                threadInfo = createThreadInfo(current);
                needsInitialization = !inContextPreInitialization;
            }
            boolean transitionToMultiThreading = singleThreaded.isValid() && hasActiveOtherThread(true);
            if (transitionToMultiThreading) {
                // recheck all thread accesses
                checkAllThreadAccesses();
            }

            Thread closing = this.closingThread;
            if (needsInitialization) {
                if (closing != null && closing != current) {
                    throw new PolyglotIllegalStateException("Can not create new threads in closing context.");
                }
                threads.put(current, threadInfo);
            }

            // enter the thread info already
            prev = (PolyglotContextImpl) singleContextState.contextThreadLocal.setReturnParent(this);
            threadInfo.enter();

            if (transitionToMultiThreading) {
                // we need to verify that all languages give access
                // to all threads in multi-threaded mode.
                transitionToMultiThreaded();
            }

            if (needsInitialization) {
                initializeNewThread(current);
            }

            // never cache last thread on close or when closingThread
            if (!closed && closing == null) {
                setCachedThreadInfo(threadInfo);
            }

        }
        if (needsInitialization) {
            EngineAccessor.INSTRUMENT.notifyThreadStarted(engine, truffleContext, current);
        }
        return prev;
    }

    private void setCachedThreadInfo(PolyglotThreadInfo info) {
        assert Thread.holdsLock(this);
        // persist enteredCount from the current cached thread
        if (constantCurrentThreadInfo != info) {
            if (constantCurrentThreadInfo.thread == null) {
                constantCurrentThreadInfo = info;
            } else {
                constantCurrentThreadInfo = PolyglotThreadInfo.NULL;
                if (info != PolyglotThreadInfo.NULL) {
                    singleThreadedConstant.invalidate();
                }
            }
        }
        currentThreadInfo = info;
    }

    private void checkAllThreadAccesses() {
        assert Thread.holdsLock(this);
        Thread current = Thread.currentThread();
        List<PolyglotLanguage> deniedLanguages = null;
        for (PolyglotLanguageContext context : contexts) {
            if (!context.isInitialized()) {
                continue;
            }
            boolean accessAllowed = true;
            if (!LANGUAGE.isThreadAccessAllowed(context.env, current, false)) {
                accessAllowed = false;
            }
            if (accessAllowed) {
                for (PolyglotThreadInfo seenThread : threads.values()) {
                    if (!LANGUAGE.isThreadAccessAllowed(context.env, seenThread.thread, false)) {
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
            throw throwDeniedThreadAccess(current, false, deniedLanguages);
        }
    }

    @TruffleBoundary
    synchronized PolyglotThreadInfo leaveThreadChanged() {
        Thread current = Thread.currentThread();
        setCachedThreadInfo(PolyglotThreadInfo.NULL);

        PolyglotThreadInfo threadInfo = threads.get(current);
        assert threadInfo != null;
        PolyglotThreadInfo info = threadInfo;
        if (cancelling && info.isLastActive()) {
            notifyThreadClosed();
        }
        info.leave();
        if (!closed && !cancelling) {
            setCachedThreadInfo(threadInfo);
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

    private void transitionToMultiThreaded() {
        assert singleThreaded.isValid();
        assert Thread.holdsLock(this);

        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                LANGUAGE.initializeMultiThreading(context.env);
            }
        }
        engine.singleThread.invalidate();
        singleThreaded.invalidate();
        singleThreadedConstant.invalidate();
    }

    private PolyglotThreadInfo createThreadInfo(Thread current) {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo threadInfo = new PolyglotThreadInfo(current);

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
        throw new PolyglotIllegalStateException(message);
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

    @Override
    public Value getBindings(String languageId) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        PolyglotLanguageContext languageContext = getContext(language);
        if (!languageContext.isInitialized()) {
            Object prev = enterIfNeeded();
            try {
                languageContext.ensureInitialized(null);
            } catch (Throwable e) {
                throw PolyglotImpl.wrapGuestException(languageContext, e);
            } finally {
                leaveIfNeeded(prev);
            }
        }
        return languageContext.getHostBindings();
    }

    @Override
    public Value getPolyglotBindings() {
        checkClosed();
        return this.polyglotHostBindings;
    }

    private void checkClosed() {
        if (closed) {
            throw new PolyglotIllegalStateException("The Context is already closed.");
        }
    }

    PolyglotLanguageContext getHostContext() {
        return contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX];
    }

    HostContext getHostContextImpl() {
        return (HostContext) getHostContext().getContextImpl();
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
        throw new IllegalStateException("Cannot find language " + languageClazz + " among " + languageNames);

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
                    indexValue = context.language.index;
                    this.languageIndexMap.put(languageClass, indexValue);
                }
            }
        }
        PolyglotLanguageContext context = contexts[indexValue];
        return context;
    }

    @Override
    public boolean initializeLanguage(String languageId) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        PolyglotLanguageContext languageContext = getContext(language);
        languageContext.checkAccess(null);
        if (!languageContext.isInitialized()) {
            Object prev = enterIfNeeded();
            try {
                return languageContext.ensureInitialized(null);
            } catch (Throwable t) {
                throw PolyglotImpl.wrapGuestException(languageContext, t);
            } finally {
                leaveIfNeeded(prev);
            }
        }
        return false;
    }

    @Override
    public Value eval(String languageId, Object sourceImpl) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        Object prev = enterIfNeeded();
        PolyglotLanguageContext languageContext = getContext(language);
        try {
            languageContext.checkAccess(null);
            languageContext.ensureInitialized(null);
            com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) sourceImpl;
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
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leaveIfNeeded(prev);
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
    private static void printResult(PolyglotLanguageContext languageContext, Object result) {
        String stringResult = LANGUAGE.toStringIfVisible(languageContext.env, result, true);
        if (stringResult != null) {
            try {
                OutputStream out = languageContext.context.config.out;
                out.write(stringResult.getBytes(StandardCharsets.UTF_8));
                out.write(System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8));
            } catch (IOException ioex) {
                // out stream has problems.
                throw new IllegalStateException(ioex);
            }
        }
    }

    @Override
    public Engine getEngineImpl(Context sourceContext) {
        return sourceContext == creatorApi ? engine.creatorApi : engine.currentApi;
    }

    @Override
    public void close(Context sourceContext, boolean cancelIfExecuting) {
        checkCreatorAccess(sourceContext, "closed");
        boolean closeCompleted = closeImpl(cancelIfExecuting, cancelIfExecuting);
        if (cancelIfExecuting) {
            engine.getCancelHandler().waitForClosing(Arrays.asList(this));
        } else if (!closeCompleted) {
            throw new PolyglotIllegalStateException(String.format("The context is currently executing on another thread. " +
                            "Set cancelIfExecuting to true to stop the execution on this thread."));
        }
        checkSubProcessFinished();
        if (engine.boundEngine && parent == null) {
            try {
                engine.ensureClosed(cancelIfExecuting, false);
            } catch (Throwable t) {
                throw PolyglotImpl.wrapGuestException(engine, t);
            }
        }
    }

    @Override
    public Value asValue(Object hostValue) {
        try {
            PolyglotLanguageContext targetLanguageContext;
            if (hostValue instanceof Value) {
                // fast path for when no context migration is necessary
                PolyglotValue value = (PolyglotValue) getAPIAccess().getImpl((Value) hostValue);
                if (value.languageContext != null && value.languageContext.context == this) {
                    return (Value) hostValue;
                }
                targetLanguageContext = getHostContext();
            } else if (HostWrapper.isInstance(hostValue)) {
                // host wrappers can nicely reuse the associated context
                targetLanguageContext = HostWrapper.asInstance(hostValue).getLanguageContext();
                if (this != targetLanguageContext.context) {
                    // this will fail later in toGuestValue when migrating
                    // or succeed in case of host languages.
                    targetLanguageContext = getHostContext();
                }
            } else {
                targetLanguageContext = getHostContext();
            }
            return targetLanguageContext.asValue(targetLanguageContext.toGuestValue(hostValue));
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(this.getHostContext(), e);
        }
    }

    void waitForClose() {
        while (!closeImpl(false, true)) {
            try {
                synchronized (this) {
                    wait(1000);
                }
            } catch (InterruptedException e) {
            }
        }
    }

    boolean isSingleThreaded() {
        return singleThreaded.isValid();
    }

    Map<Thread, PolyglotThreadInfo> getSeenThreads() {
        assert Thread.holdsLock(this);
        return threads;
    }

    synchronized boolean isActive() {
        for (PolyglotThreadInfo seenTinfo : threads.values()) {
            if (seenTinfo.isActive()) {
                return true;
            }
        }
        return false;
    }

    PolyglotThreadInfo getFirstActiveOtherThread(boolean includePolyglotThread) {
        assert Thread.holdsLock(this);
        // send enters and leaves into a lock by setting the lastThread to null.
        for (PolyglotThreadInfo otherInfo : threads.values()) {
            if (!includePolyglotThread && otherInfo.isPolyglotThread(this)) {
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

    synchronized void notifyThreadClosed() {
        PolyglotThreadInfo currentTInfo = getCurrentThreadInfo();
        if (currentTInfo != PolyglotThreadInfo.NULL) {
            currentTInfo.cancelled = true;
            // clear interrupted status after closingThread
            // needed because we interrupt when closingThread from another thread.
            Thread.interrupted();
            notifyAll();
        }
    }

    boolean closeImpl(boolean cancelIfExecuting, boolean waitForPolyglotThreads) {
        /*
         * As a first step we prepare for close by waiting for other threads to finish closing and
         * checking whether other threads are still executing. This block performs the following
         * checks:
         *
         * 1) The close was already performed on another thread -> return true
         *
         * 2) The close is currently already being performed on this thread -> return true
         *
         * 3) The close was not yet performed but other threads are still executing -> mark current
         * thread as cancelled and return false
         *
         * 4) The close was not yet performed and no thread is executing -> perform close
         */
        boolean waitForClose = false;
        while (true) {
            if (waitForClose) {
                closingLock.lock();
                closingLock.unlock();
                waitForClose = false;
            }
            synchronized (this) {
                if (closed) {
                    // already cancelled
                    return true;
                }
                Thread localClosingThread = closingThread;
                if (localClosingThread != null) {
                    if (localClosingThread == Thread.currentThread()) {
                        // currently canceling recursively -> just complete
                        return true;
                    } else {
                        // currently canceling on another thread -> wait for other thread to
                        // complete closing
                        waitForClose = true;
                        continue;
                    }
                }
                PolyglotThreadInfo threadInfo = getCurrentThreadInfo();

                // triggers a thread changed event which requires slow path enter
                setCachedThreadInfo(PolyglotThreadInfo.NULL);

                if (!threadInfo.explicitContextStack.isEmpty()) {
                    throw new IllegalStateException("The context is explicitely entered on the current thread. Call leave() before closing the context to resolve this.");
                }

                if (cancelIfExecuting) {
                    cancelling = true;
                    if (threadInfo != PolyglotThreadInfo.NULL) {
                        threadInfo.cancelled = true;
                        // clear interrupted status after closingThread
                        // needed because we interrupt when closingThread from another thread.
                        Thread.interrupted();
                    }
                }

                if (hasActiveOtherThread(waitForPolyglotThreads)) {
                    /*
                     * We are not done executing, cannot close yet.
                     */
                    return false;
                }
                closingThread = Thread.currentThread();
                closingLock.lock();
                break;
            }
        }

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
            assert !closed;
            Object prev = enter();
            try {
                closeChildContexts(cancelIfExecuting, waitForPolyglotThreads);

                finalizeContext();

                // finalization performed commit close -> no reinitialization allowed

                disposedContexts = disposeContext();

                assert childContexts.isEmpty();
                success = true;
            } finally {
                synchronized (this) {
                    leave(prev);
                    if (success) {
                        remainingThreads = threads.keySet().toArray(new Thread[0]);
                    }
                    cancelling = false;
                    if (success) {
                        closed = true;
                    }
                    // triggers a thread changed event which requires slow path enter
                    setCachedThreadInfo(PolyglotThreadInfo.NULL);
                }
                if (success && engine.boundEngine) {
                    disposeStaticContext(this);
                }
            }
        } finally {
            closingThread = null;
            closingLock.unlock();
        }

        /*
         * No longer any lock is held. So we can acquire other locks to cleanup.
         */
        if (disposedContexts != null) {
            for (PolyglotLanguageContext context : disposedContexts) {
                context.notifyDisposed();
            }
        }

        if (success) {
            if (parent != null) {
                synchronized (parent) {
                    parent.childContexts.remove(this);
                }
            } else {
                engine.removeContext(this);
            }

            for (Thread thread : remainingThreads) {
                EngineAccessor.INSTRUMENT.notifyThreadFinished(engine, truffleContext, thread);
            }
            EngineAccessor.INSTRUMENT.notifyContextClosed(engine, truffleContext);
            if (parent == null) {
                if (!this.config.logLevels.isEmpty()) {
                    EngineAccessor.LANGUAGE.configureLoggers(this, null, getAllLoggers(engine));
                }
                if (this.config.logHandler != null && !PolyglotLogHandler.isSameLogSink(this.config.logHandler, engine.logHandler)) {
                    this.config.logHandler.close();
                }
            }
        }
        return true;
    }

    private void closeChildContexts(boolean cancelIfExecuting, boolean waitForPolyglotThreads) {
        PolyglotContextImpl[] childrenToClose;
        synchronized (this) {
            childrenToClose = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
        }
        for (PolyglotContextImpl childContext : childrenToClose) {
            childContext.closeImpl(cancelIfExecuting, waitForPolyglotThreads);
        }
    }

    private List<PolyglotLanguageContext> disposeContext() {
        assert !this.disposing;
        this.disposing = true;
        try {
            List<PolyglotLanguageContext> disposedContexts = new ArrayList<>(contexts.length);
            synchronized (this) {
                for (int i = contexts.length - 1; i >= 0; i--) {
                    PolyglotLanguageContext context = contexts[i];
                    try {
                        boolean disposed = context.dispose();
                        if (disposed) {
                            disposedContexts.add(context);
                        }
                    } catch (Exception | Error ex) {
                        throw PolyglotImpl.wrapGuestException(context, ex);
                    }
                }
            }
            return disposedContexts;
        } finally {
            this.disposing = false;
        }
    }

    private void finalizeContext() {
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
                    try {
                        finalizationPerformed |= context.finalizeContext();
                    } catch (Exception | Error ex) {
                        throw PolyglotImpl.wrapGuestException(context, ex);
                    }
                }
            }
        } while (finalizationPerformed);
    }

    synchronized void sendInterrupt() {
        if (!cancelling) {
            return;
        }
        for (PolyglotThreadInfo threadInfo : threads.values()) {
            if (!threadInfo.isCurrent() && threadInfo.isActive()) {
                /*
                 * We send an interrupt to the thread to wake up and to run some guest language code
                 * in case they are waiting in some async primitive. The interrupt is then cleared
                 * when the closed is performed.
                 */
                threadInfo.thread.interrupt();
            }
        }
    }

    PolyglotThreadInfo getCurrentThreadInfo() {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo currentTInfo = currentThreadInfo;

        if (currentTInfo.thread != Thread.currentThread()) {
            currentTInfo = threads.get(Thread.currentThread());
            if (currentTInfo == null) {
                // closingThread from a thread we have never seen.
                currentTInfo = PolyglotThreadInfo.NULL;
            }
        }
        assert currentTInfo.thread == null || currentTInfo.thread == Thread.currentThread();

        return currentTInfo;
    }

    boolean patch(PolyglotContextConfig newConfig) {
        CompilerAsserts.neverPartOfCompilation();

        this.config = newConfig;
        initializeStaticContext(this);
        if (!newConfig.logLevels.isEmpty()) {
            EngineAccessor.LANGUAGE.configureLoggers(this, newConfig.logLevels, getAllLoggers(engine));
        }
        final Object prev = enter();
        try {
            for (int i = 1; i < this.contexts.length; i++) {
                final PolyglotLanguageContext context = this.contexts[i];
                if (!context.patch(newConfig)) {
                    return false;
                }
            }
        } finally {
            leave(prev);
        }
        return true;
    }

    synchronized void checkSubProcessFinished() {
        ProcessHandlers.ProcessDecorator[] processes = subProcesses.toArray(new ProcessHandlers.ProcessDecorator[subProcesses.size()]);
        for (ProcessHandlers.ProcessDecorator process : processes) {
            if (process.isAlive()) {
                throw new PolyglotIllegalStateException(String.format("The context has an alive sub-process %s created by %s.",
                                process.getCommand(), process.getOwner().language.getId()));
            }
        }
    }

    static PolyglotContextImpl preInitialize(final PolyglotEngineImpl engine) {
        final FileSystems.PreInitializeContextFileSystem fs = new FileSystems.PreInitializeContextFileSystem();
        EconomicSet<String> allowedLanguages = EconomicSet.create();
        allowedLanguages.addAll(engine.getLanguages().keySet());
        final PolyglotContextConfig config = new PolyglotContextConfig(engine,
                        engine.out,
                        engine.err,
                        engine.in,
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
                        fs, engine.logHandler, false, null,
                        EnvironmentAccess.INHERIT, null, null);
        final PolyglotContextImpl context = new PolyglotContextImpl(engine, config);
        try {
            final String oldOption = engine.engineOptionValues.get(PolyglotEngineOptions.PreinitializeContexts);
            final String newOption = ImageBuildTimeOptions.get(ImageBuildTimeOptions.PREINITIALIZE_CONTEXTS_NAME);
            final String optionValue;
            if (!oldOption.isEmpty() && !newOption.isEmpty()) {
                optionValue = oldOption + "," + newOption;
            } else {
                optionValue = oldOption + newOption;
            }

            if (!optionValue.isEmpty()) {
                final Set<String> languagesToPreinitialize = new HashSet<>();
                Collections.addAll(languagesToPreinitialize, optionValue.split(","));
                context.inContextPreInitialization = true;
                try {
                    Object prev = context.enter();
                    try {
                        for (String languageId : engine.getLanguages().keySet()) {
                            if (languagesToPreinitialize.contains(languageId)) {
                                PolyglotLanguage language = engine.findLanguage(null, languageId, null, false, true);
                                if (language != null) {
                                    final PolyglotLanguageContext languageContext = context.getContextInitialized(language, null);
                                    languageContext.preInitialize();
                                }
                            }
                            // Reset language options parsed during preinitialization
                            PolyglotLanguage language = engine.idToLanguage.get(languageId);
                            language.clearOptionValues();
                        }
                    } finally {
                        context.leave(prev);
                    }
                } finally {
                    context.inContextPreInitialization = false;
                }
            }
            // Need to clean up Threads before storing SVM image
            context.currentThreadInfo = PolyglotThreadInfo.NULL;
            context.constantCurrentThreadInfo = PolyglotThreadInfo.NULL;
            disposeStaticContext(context);
            return context;
        } finally {
            fs.onPreInitializeContextEnd();
            FileSystems.resetDefaultFileSystemProvider();
            if (!config.logLevels.isEmpty()) {
                EngineAccessor.LANGUAGE.configureLoggers(context, null, getAllLoggers(engine));
            }
        }
    }

    private static Object[] getAllLoggers(PolyglotEngineImpl engine) {
        Object defaultLoggers = EngineAccessor.LANGUAGE.getDefaultLoggers();
        Object engineLoggers = engine.getEngineLoggers();
        return engineLoggers == null ? new Object[]{defaultLoggers} : new Object[]{defaultLoggers, engineLoggers};
    }

    static class ContextWeakReference extends WeakReference<PolyglotContextImpl> {

        volatile boolean removed = false;
        final List<PolyglotLanguageInstance> freeInstances = new ArrayList<>();

        ContextWeakReference(PolyglotContextImpl referent) {
            super(referent, referent.engine.contextsReferenceQueue);
        }

    }

}
