/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.vm;

import static com.oracle.truffle.api.vm.PolyglotImpl.isGuestInteropValue;
import static com.oracle.truffle.api.vm.PolyglotImpl.wrapGuestException;
import static com.oracle.truffle.api.vm.VMAccessor.INSTRUMENT;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;
import static com.oracle.truffle.api.vm.VMAccessor.NODES;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
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
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;
import java.util.Collections;

final class PolyglotContextImpl extends AbstractContextImpl implements VMObject {

    @CompilationFinal private static ContextThreadLocal CURRENT = new ContextThreadLocal();

    private final Assumption singleThreaded = Truffle.getRuntime().createAssumption("Single threaded");
    private final Map<Thread, PolyglotThreadInfo> threads = new HashMap<>();
    private volatile PolyglotThreadInfo lastThread = PolyglotThreadInfo.NULL;

    /*
     * While canceling the context can no longer be entered. The context goes from canceling into
     * closed state.
     */
    volatile boolean cancelling;
    private volatile Thread closingThread;
    /*
     * If the context is closed all operations should fail with IllegalStateException.
     */
    volatile boolean closed;
    final PolyglotEngineImpl engine;
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;

    Context api;
    final TruffleContext truffleContext;
    final PolyglotContextImpl parent;
    OutputStream out;   // effectively final
    OutputStream err;   // effectively final
    InputStream in;     // effectively final
    final Map<String, Value> polyglotScope = new HashMap<>();
    Predicate<String> classFilter;  // effectively final
    boolean hostAccessAllowed;      // effectively final
    @CompilationFinal boolean createThreadAllowed;

    // map from class to language index
    private final FinalIntMap languageIndexMap = new FinalIntMap();

    final Map<Object, CallTarget> javaInteropCache = new HashMap<>();
    Set<String> allowedPublicLanguages;     // effectively final
    Map<String, String[]> applicationArguments;  // effectively final
    private final Set<PolyglotContextImpl> childContexts = new LinkedHashSet<>();
    boolean inContextPreInitialization; // effectively final

    /*
     * Constructor for outer contexts.
     */
    PolyglotContextImpl(PolyglotEngineImpl engine, final OutputStream out,
                    OutputStream err,
                    InputStream in,
                    boolean hostAccessAllowed,
                    boolean createThreadAllowed,
                    Predicate<String> classFilter,
                    Map<String, String> options,
                    Map<String, String[]> applicationArguments,
                    Set<String> allowedPublicLanguages) {
        super(engine.impl);
        this.parent = null;
        this.engine = engine;
        patchInstance(out, err, in, hostAccessAllowed, createThreadAllowed, classFilter, applicationArguments, allowedPublicLanguages);
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        this.contexts = new PolyglotLanguageContext[languages.size() + 1];
        this.contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = new PolyglotLanguageContext(this, engine.hostLanguage, null, applicationArguments.get(PolyglotEngineImpl.HOST_LANGUAGE_ID),
                        Collections.emptyMap());

        for (PolyglotLanguage language : languages) {
            PolyglotLanguageContext languageContext = new PolyglotLanguageContext(this, language, null, applicationArguments.get(language.getId()), Collections.emptyMap());
            this.contexts[language.index] = languageContext;
        }

        // process language specific options
        for (String optionKey : options.keySet()) {
            String group = PolyglotEngineImpl.parseOptionGroup(optionKey);
            PolyglotLanguage language = engine.idToLanguage.get(group);
            if (language == null) {
                if (engine.isEngineGroup(group)) {
                    // Test that "engine options" are not present among the options designated for
                    // this context
                    if (engine.getAllOptions().get(optionKey) != null) {
                        throw new IllegalArgumentException("Option " + optionKey + " is an engine option. Engine level options can only be configured for contexts without a shared engine set." +
                                        " To resolve this, configure the option when creating the Engine or create a context without a shared engine.");
                    }
                }
                throw OptionValuesImpl.failNotFound(engine.getAllOptions(), optionKey);
            } else {
                // there should not be any overlaps -> engine creation should already fail
                assert !engine.isEngineGroup(group);
            }

            this.contexts[language.index].getOptionValues().put(optionKey, options.get(optionKey));
        }
        this.truffleContext = VMAccessor.LANGUAGE.createTruffleContext(this);
        VMAccessor.INSTRUMENT.notifyContextCreated(engine, truffleContext);
    }

    /*
     * Constructor for inner contexts.
     */
    @SuppressWarnings("hiding")
    PolyglotContextImpl(PolyglotLanguageContext creator, Map<String, Object> config, TruffleContext spiContext) {
        super(creator.getEngine().impl);
        PolyglotContextImpl parent = creator.context;
        this.parent = creator.context;
        this.hostAccessAllowed = parent.hostAccessAllowed;
        this.createThreadAllowed = parent.createThreadAllowed;
        this.applicationArguments = parent.applicationArguments;
        this.classFilter = parent.classFilter;
        this.out = parent.out;
        this.err = parent.err;
        this.in = parent.in;
        this.allowedPublicLanguages = parent.allowedPublicLanguages;
        this.engine = parent.engine;
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        this.contexts = new PolyglotLanguageContext[languages.size() + 1];
        this.contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = new PolyglotLanguageContext(this, engine.hostLanguage, null, applicationArguments.get(PolyglotEngineImpl.HOST_LANGUAGE_ID),
                        Collections.emptyMap());

        for (PolyglotLanguage language : languages) {
            OptionValuesImpl values = parent.contexts[language.index].optionValues;
            if (values != null) {
                values = values.copy();
            }
            Map<String, Object> languageConfig;
            if (creator.language == language) {
                languageConfig = config;
            } else {
                languageConfig = new HashMap<>();
            }

            PolyglotLanguageContext languageContext = new PolyglotLanguageContext(this, language, values, applicationArguments.get(language.getId()), languageConfig);
            this.contexts[language.index] = languageContext;
        }
        this.parent.addChildContext(this);
        this.truffleContext = spiContext;
        // notifyContextCreated() is called after spiContext.impl is set to this.
    }

    void notifyContextCreated() {
        VMAccessor.INSTRUMENT.notifyContextCreated(engine, truffleContext);
    }

    private synchronized void addChildContext(PolyglotContextImpl child) {
        if (closingThread != null) {
            throw new IllegalStateException("Adding child context into a closing context.");
        }
        childContexts.add(child);
    }

    Env requireEnv(PolyglotLanguage language) {
        return contexts[language.index].requireEnv();
    }

    Predicate<String> getClassFilter() {
        return classFilter;
    }

    static PolyglotContextImpl current() {
        return (PolyglotContextImpl) CURRENT.get();
    }

    static PolyglotContextImpl requireContext() {
        PolyglotContextImpl context = current();
        if (context == null) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("No current context available.");
        }
        return context;
    }

    PolyglotContextImpl enter() {
        PolyglotThreadInfo tinfo = this.lastThread;
        assert tinfo != null;

        // double checked locking
        PolyglotContextImpl context;
        if (tinfo.thread == Thread.currentThread()) {
            // fast-path -> same thread
            context = (PolyglotContextImpl) CURRENT.setReturnParent(this);
            tinfo.enter();
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

    @TruffleBoundary
    PolyglotContextImpl enterThreadChanged() {
        Thread current = Thread.currentThread();
        PolyglotContextImpl prev;
        boolean needsInitialization = false;
        synchronized (this) {
            engine.checkState();
            if (closed) {
                throw new PolyglotIllegalStateException("The Context is already closed.");
            }
            PolyglotThreadInfo threadInfo = this.lastThread;
            assert threadInfo != null;

            if (threadInfo.thread != current) {
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

                if (needsInitialization) {
                    if (closingThread != null && closingThread != current) {
                        throw new PolyglotIllegalStateException("Can not create new threads in closing context.");
                    }
                    threads.put(current, threadInfo);
                }

                // enter the thread info already
                prev = (PolyglotContextImpl) CURRENT.setReturnParent(this);
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
                if (!closed && closingThread == null) {
                    lastThread = threadInfo;
                }
            } else {
                // enter the thread info already
                prev = (PolyglotContextImpl) CURRENT.setReturnParent(this);
                threadInfo.enter();
            }
        }
        if (needsInitialization) {
            VMAccessor.INSTRUMENT.notifyThreadStarted(engine, truffleContext, current);
        }
        return prev;
    }

    private void checkAllThreadAccesses() {
        Thread current = Thread.currentThread();
        List<PolyglotLanguage> deniedLanguages = null;
        for (PolyglotLanguageContext context : contexts) {
            if (!context.isInitialized()) {
                continue;
            }
            boolean accessAllowed = true;
            if (!LANGUAGE.isThreadAccessAllowed(context.language.info, current, false)) {
                accessAllowed = false;
            }
            if (accessAllowed) {
                for (PolyglotThreadInfo seenThread : threads.values()) {
                    if (!LANGUAGE.isThreadAccessAllowed(context.language.info, seenThread.thread, false)) {
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

    void leave(Object prev) {
        assert current() == this : "Cannot leave context that is currently not entered. Forgot to leave a context?";

        Thread current = Thread.currentThread();
        PolyglotThreadInfo tinfo = this.lastThread;
        if (tinfo.thread != current) {
            if (singleThreaded.isValid()) {
                CompilerDirectives.transferToInterpreter();
            }
            tinfo = leaveThreadChanged(current);
        }
        tinfo.leave();
        CURRENT.set(prev);
    }

    @TruffleBoundary
    synchronized PolyglotThreadInfo leaveThreadChanged(Thread current) {
        PolyglotThreadInfo threadInfo = this.lastThread;
        assert threadInfo != null;
        if (threadInfo.thread != current) {
            threadInfo = threads.get(current);
            // never cache last thread on close or when cancelling
            if (!closed && closingThread == null) {
                lastThread = threadInfo;
            }
        }
        PolyglotThreadInfo info = threadInfo;
        if (cancelling && info.isLastActive()) {
            notifyThreadClosed();
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
            if (!context.isInitialized()) {
                continue;
            }
            LANGUAGE.initializeMultiThreading(context.env);
        }
        singleThreaded.invalidate();
    }

    private PolyglotThreadInfo createThreadInfo(Thread current) {
        assert Thread.holdsLock(this);
        PolyglotThreadInfo threadInfo = new PolyglotThreadInfo(current);

        boolean singleThread = isSingleThreaded();
        List<PolyglotLanguage> deniedLanguages = null;
        for (PolyglotLanguageContext context : contexts) {
            if (context.isInitialized()) {
                if (!VMAccessor.LANGUAGE.isThreadAccessAllowed(context.language.info, current, singleThread)) {
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

    synchronized Object importSymbolFromLanguage(String symbolName) {
        Value symbol = polyglotScope.get(symbolName);
        if (symbol == null) {
            return findLegacyExportedSymbol(symbolName);
        } else {
            return getAPIAccess().getReceiver(symbol);
        }
    }

    private Object findLegacyExportedSymbol(String symbolName) {
        Object legacySymbol = findLegacyExportedSymbol(symbolName, true);
        if (legacySymbol != null) {
            return legacySymbol;
        }
        return findLegacyExportedSymbol(symbolName, false);
    }

    private Value findLegacyExportedSymbolValue(String symbolName) {
        Value legacySymbol = findLegacyExportedSymbolValue(symbolName, true);
        if (legacySymbol != null) {
            return legacySymbol;
        }
        return findLegacyExportedSymbolValue(symbolName, false);
    }

    private Object findLegacyExportedSymbol(String name, boolean onlyExplicit) {
        for (PolyglotLanguageContext languageContext : contexts) {
            Env env = languageContext.env;
            if (env != null) {
                Object s = LANGUAGE.findExportedSymbol(env, name, onlyExplicit);
                if (s != null) {
                    return s;
                }
            }
        }
        return null;
    }

    private Value findLegacyExportedSymbolValue(String name, boolean onlyExplicit) {
        for (PolyglotLanguageContext languageContext : contexts) {
            Env env = languageContext.env;
            if (env != null) {
                Object s = LANGUAGE.findExportedSymbol(env, name, onlyExplicit);
                if (s != null) {
                    return languageContext.toHostValue(s);
                }
            }
        }
        return null;
    }

    synchronized void exportSymbolFromLanguage(PolyglotLanguageContext languageConext, String symbolName, Object value) {
        if (value == null) {
            polyglotScope.remove(symbolName);
        } else if (!isGuestInteropValue(value)) {
            throw new IllegalArgumentException(String.format("Invalid exported symbol value %s. Only interop and primitive values can be exported.", value.getClass().getName()));
        } else {
            polyglotScope.put(symbolName, languageConext.toHostValue(value));
        }
    }

    @Override
    public synchronized void exportSymbol(String symbolName, Object value) {
        Object prev = enter();
        try {
            Value resolvedValue;
            if (value instanceof Value) {
                resolvedValue = (Value) value;
            } else {
                PolyglotLanguageContext hostContext = getHostContext();
                hostContext.ensureInitialized(null);
                resolvedValue = hostContext.toHostValue(hostContext.toGuestValue(value));
            }
            polyglotScope.put(symbolName, resolvedValue);
        } finally {
            leave(prev);
        }
    }

    @Override
    public synchronized Value importSymbol(String symbolName) {
        Object prev = enter();
        try {
            Value value = polyglotScope.get(symbolName);
            if (value == null) {
                value = findLegacyExportedSymbolValue(symbolName);
            }
            return value;
        } catch (Throwable e) {
            throw wrapGuestException(getHostContext(), e);
        } finally {
            leave(prev);
        }
    }

    PolyglotLanguageContext getHostContext() {
        return contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX];
    }

    PolyglotLanguageContext findLanguageContext(String languageId, String mimeType, boolean failIfNotFound) {
        assert languageId != null || mimeType != null : Objects.toString(languageId) + ", " + Objects.toString(mimeType);
        if (languageId != null) {
            PolyglotLanguage language = engine.idToLanguage.get(languageId);
            if (language != null) {
                return contexts[language.index];
            }

        }
        if (mimeType != null) {
            // we need to interpret mime types for compatibility.
            PolyglotLanguage language = engine.idToLanguage.get(mimeType);
            if (language != null) {
                return contexts[language.index];
            }
            for (PolyglotLanguageContext context : contexts) {
                if (context.language.cache.getMimeTypes().contains(mimeType)) {
                    return context;
                }
            }
        }
        if (failIfNotFound) {
            if (languageId != null) {
                Set<String> ids = new LinkedHashSet<>();
                for (PolyglotLanguage language : engine.idToLanguage.values()) {
                    ids.add(language.cache.getId());
                }
                throw new IllegalStateException("No language for id " + languageId + " found. Supported languages are: " + ids);
            } else {
                Set<String> mimeTypes = new LinkedHashSet<>();
                for (PolyglotLanguageContext language : contexts) {
                    mimeTypes.addAll(language.language.cache.getMimeTypes());
                }
                throw new IllegalStateException("No language for MIME type " + mimeType + " found. Supported languages are: " + mimeTypes);
            }
        } else {
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    PolyglotLanguageContext findLanguageContext(Class<? extends TruffleLanguage> languageClazz, boolean failIfNotFound) {
        for (PolyglotLanguageContext lang : contexts) {
            Env env = lang.env;
            if (env != null) {
                TruffleLanguage<?> language = NODES.getLanguageSpi(lang.language.info);
                if (languageClazz != TruffleLanguage.class && languageClazz.isInstance(language)) {
                    return lang;
                }
            }
        }
        if (failIfNotFound) {
            Set<String> languageNames = new HashSet<>();
            for (PolyglotLanguageContext lang : contexts) {
                if (lang.env == null) {
                    continue;
                }
                languageNames.add(lang.language.cache.getClassName());
            }
            throw new IllegalStateException("Cannot find language " + languageClazz + " among " + languageNames);
        } else {
            return null;
        }
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

    private PolyglotLanguageContext getLanguageContextImpl(Class<? extends TruffleLanguage<?>> languageClass) {
        int indexValue = languageIndexMap.get(languageClass);
        if (indexValue == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                indexValue = languageIndexMap.get(languageClass);
                if (indexValue == -1) {
                    PolyglotLanguageContext context = findLanguageContext(languageClass, true);
                    indexValue = context.language.index;
                    languageIndexMap.put(languageClass, indexValue);
                }
            }
        }
        PolyglotLanguageContext context = contexts[indexValue];
        return context;
    }

    @Override
    public boolean initializeLanguage(String languageId) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        PolyglotLanguageContext languageContext = this.contexts[language.index];
        languageContext.checkAccess(null);
        Object prev = languageContext.enter();
        try {
            return languageContext.ensureInitialized(null);
        } catch (Throwable t) {
            throw wrapGuestException(languageContext, t);
        } finally {
            languageContext.leave(prev);
        }
    }

    @Override
    public Value eval(String languageId, Object sourceImpl) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        Object prev = enter();
        PolyglotLanguageContext languageContext = contexts[language.index];
        try {
            languageContext.checkAccess(null);
            com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) sourceImpl;
            CallTarget target = languageContext.parseCached(source);
            Object result = target.call(PolyglotImpl.EMPTY_ARGS);

            if (source.isInteractive()) {
                printResult(languageContext, result);
            }

            return languageContext.toHostValue(result);
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(prev);
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
                OutputStream out = languageContext.context.out;
                out.write(stringResult.getBytes(StandardCharsets.UTF_8));
                out.write(System.getProperty("line.separator").getBytes(StandardCharsets.UTF_8));
            } catch (IOException ioex) {
                // out stream has problems.
                throw new IllegalStateException(ioex);
            }
        }
    }

    @Override
    public Engine getEngineImpl() {
        return engine.api;
    }

    @Override
    public void close(boolean cancelIfExecuting) {
        boolean closeCompleted = closeImpl(cancelIfExecuting, cancelIfExecuting);
        if (cancelIfExecuting) {
            engine.getCancelHandler().waitForClosing(this);
        } else if (!closeCompleted) {
            throw new PolyglotIllegalStateException(String.format("The context is currently executing on another thread. " +
                            "Set cancelIfExecuting to true to stop the execution on this thread."));
        }
        if (engine.boundEngine && parent == null) {
            engine.ensureClosed(cancelIfExecuting, false);
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
        this.lastThread = PolyglotThreadInfo.NULL;
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
        this.lastThread = PolyglotThreadInfo.NULL;
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
        boolean success = false;
        Thread[] remainingThreads = null;
        PolyglotContextImpl[] childrenToClose = null;
        try {
            synchronized (this) {
                if (!closed) {
                    closingThread = Thread.currentThread();

                    // triggers a thread changed event which requires synchronization on the next
                    // enter/leave.
                    lastThread = PolyglotThreadInfo.NULL;

                    if (cancelIfExecuting) {
                        cancelling = true;
                        PolyglotThreadInfo currentTInfo = getCurrentThreadInfo();
                        if (currentTInfo != PolyglotThreadInfo.NULL) {
                            currentTInfo.cancelled = true;
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

                    childrenToClose = childContexts.toArray(new PolyglotContextImpl[childContexts.size()]);
                }
            }
            if (childrenToClose != null) {
                Object prev = enter();
                try {
                    for (PolyglotContextImpl childContext : childrenToClose) {
                        childContext.closeImpl(cancelIfExecuting, waitForPolyglotThreads);
                    }

                    // we need to run finalization at least twice in case a finalization run has
                    // initialized a new contexts
                    boolean finalizationPerformed;
                    do {
                        finalizationPerformed = false;
                        // inverse context order is already the right order for context
                        // disposal/finalization
                        for (int i = contexts.length - 1; i >= 0; i--) {
                            PolyglotLanguageContext context = contexts[i];
                            try {
                                finalizationPerformed |= context.finalizeContext();
                            } catch (Exception | Error ex) {
                                throw wrapGuestException(context, ex);
                            }
                        }
                    } while (finalizationPerformed);

                    // finalization performed commit close -> no actions allowed on dispose
                    closed = true;

                    List<PolyglotLanguageContext> disposedContexts = new ArrayList<>(contexts.length);
                    try {
                        synchronized (this) {
                            for (int i = contexts.length - 1; i >= 0; i--) {
                                PolyglotLanguageContext context = contexts[i];
                                try {
                                    boolean disposed = context.dispose();
                                    if (disposed) {
                                        disposedContexts.add(context);
                                    }
                                } catch (Exception | Error ex) {
                                    throw wrapGuestException(context, ex);
                                }
                            }
                        }
                    } finally {
                        for (PolyglotLanguageContext context : disposedContexts) {
                            context.notifyDisposed();
                        }
                    }
                    assert childContexts.isEmpty();
                    success = true;
                } finally {
                    leave(prev);
                    if (success) {
                        if (parent != null) {
                            synchronized (parent) {
                                parent.childContexts.remove(this);
                            }
                        } else {
                            engine.removeContext(this);
                        }
                        synchronized (this) {
                            remainingThreads = threads.keySet().toArray(new Thread[0]);
                        }
                    }
                    closed = success;
                    lastThread = PolyglotThreadInfo.NULL;
                    cancelling = false;
                }
            }
        } finally {
            closingThread = null;
        }
        if (success) {
            for (Thread thread : remainingThreads) {
                VMAccessor.INSTRUMENT.notifyThreadFinished(engine, truffleContext, thread);
            }
            VMAccessor.INSTRUMENT.notifyContextClosed(engine, truffleContext);
        }
        return true;
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
        PolyglotThreadInfo currentTInfo = lastThread;

        if (currentTInfo.thread != Thread.currentThread()) {
            currentTInfo = threads.get(Thread.currentThread());
            if (currentTInfo == null) {
                // closingThread from a thread we have never seen.
                currentTInfo = PolyglotThreadInfo.NULL;
            }
        }
        return currentTInfo;
    }

    @Override
    public Value lookup(String languageId, String symbolName) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        Object prev = enter();
        PolyglotLanguageContext languageContext = this.contexts[language.index];
        try {
            return languageContext.lookupHost(symbolName);
        } catch (Throwable e) {
            throw PolyglotImpl.wrapGuestException(languageContext, e);
        } finally {
            leave(prev);
        }
    }

    boolean patch(OutputStream newOut, OutputStream newErr, InputStream newIn, boolean newHostAccessAllowed,
                    boolean newCreateThreadAllowed, Predicate<String> newClassFilter,
                    Map<String, String> newOptions, Map<String, String[]> newApplicationArguments, Set<String> newAllowedPublicLanguages) {
        CompilerAsserts.neverPartOfCompilation();
        patchInstance(newOut, newErr, newIn, newHostAccessAllowed, newCreateThreadAllowed, newClassFilter, newApplicationArguments, newAllowedPublicLanguages);
        for (int i = 1; i < this.contexts.length; i++) {
            final PolyglotLanguageContext context = this.contexts[i];
            if (context.isInitialized()) {
                OptionValuesImpl values = context.language.getOptionValues().copy();
                values.putAll(newOptions);
                if (!context.patch(values, newApplicationArguments.get(context.language.getId()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void patchInstance(OutputStream newOut, OutputStream newErr, InputStream newIn, boolean newHostAccessAllowed,
                    boolean newCreateThreadAllowed, Predicate<String> newClassFilter,
                    Map<String, String[]> newApplicationArguments, Set<String> newAllowedPublicLanguages) {
        this.hostAccessAllowed = newHostAccessAllowed;
        this.createThreadAllowed = newCreateThreadAllowed;
        this.applicationArguments = newApplicationArguments;
        this.classFilter = newClassFilter;

        if (newOut == null || newOut == INSTRUMENT.getOut(engine.out)) {
            this.out = engine.out;
        } else {
            this.out = INSTRUMENT.createDelegatingOutput(newOut, engine.out);
        }
        if (newErr == null || newErr == INSTRUMENT.getOut(engine.err)) {
            this.err = engine.err;
        } else {
            this.err = INSTRUMENT.createDelegatingOutput(newErr, engine.err);
        }
        this.in = newIn == null ? engine.in : newIn;
        this.allowedPublicLanguages = newAllowedPublicLanguages;
    }

    static PolyglotContextImpl preInitialize(final PolyglotEngineImpl engine) {
        PolyglotContextImpl context = new PolyglotContextImpl(
                        engine,
                        null,
                        null,
                        null,
                        false,
                        false,
                        null,
                        Collections.emptyMap(),
                        Collections.emptyMap(),
                        engine.getLanguages().keySet());
        final String optionValue = engine.engineOptionValues.get(PolyglotEngineImpl.PREINITIALIZE_CONTEXTS);
        if (optionValue != null && !optionValue.isEmpty()) {
            final Set<String> languagesToPreinitialize = new HashSet<>();
            Collections.addAll(languagesToPreinitialize, optionValue.split(","));
            context.inContextPreInitialization = true;
            try {
                Object prev = context.enter();
                try {
                    for (String languageId : engine.getLanguages().keySet()) {
                        if (languagesToPreinitialize.contains(languageId)) {
                            final PolyglotLanguageContext languageContext = context.findLanguageContext(languageId, null, false);
                            if (languageContext != null) {
                                languageContext.preInitialize();
                            }
                        }
                    }
                } finally {
                    context.leave(prev);
                }
            } finally {
                context.inContextPreInitialization = false;
            }
        }
        // Need to clean up Threads before storing SVM image
        context.lastThread = PolyglotThreadInfo.NULL;
        CURRENT = new ContextThreadLocal();
        return context;
    }

}
