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
import java.util.LinkedList;
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

final class PolyglotContextImpl extends AbstractContextImpl implements VMObject {

    private static final ContextThreadLocal CURRENT = new ContextThreadLocal();

    private final Assumption singleThreaded = Truffle.getRuntime().createAssumption("Single threaded");
    private final Map<Thread, PolyglotThreadInfo> threads = new HashMap<>();
    private volatile PolyglotThreadInfo lastThread = PolyglotThreadInfo.NULL;

    /*
     * While canceling the context can no longer be entered. The context goes from canceling into
     * closed state.
     */
    volatile boolean cancelling;
    /*
     * If the context is closed all operations should fail with IllegalStateException.
     */
    private volatile boolean closed;
    final PolyglotEngineImpl engine;
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;

    Context api;
    private final PolyglotContextImpl parent;
    final OutputStream out;
    final OutputStream err;
    final InputStream in;
    private final Map<String, Value> polyglotScope = new HashMap<>();
    final Predicate<String> classFilter;
    final boolean hostAccessAllowed;
    final boolean createThreadAllowed;

    // map from class to language index
    private final FinalIntMap languageIndexMap = new FinalIntMap();

    final Map<Object, CallTarget> javaInteropCache = new HashMap<>();
    final Set<String> allowedPublicLanguages;
    private final Map<String, String[]> applicationArguments;
    private final Set<PolyglotContextImpl> childContexts = new LinkedHashSet<>();

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
        this.hostAccessAllowed = hostAccessAllowed;
        this.createThreadAllowed = createThreadAllowed;
        this.applicationArguments = applicationArguments;
        this.classFilter = classFilter;

        if (out == null || out == INSTRUMENT.getOut(engine.out)) {
            this.out = engine.out;
        } else {
            this.out = INSTRUMENT.createDelegatingOutput(out, engine.out);
        }
        if (err == null || err == INSTRUMENT.getOut(engine.err)) {
            this.err = engine.err;
        } else {
            this.err = INSTRUMENT.createDelegatingOutput(err, engine.err);
        }
        this.in = in == null ? engine.in : in;
        this.allowedPublicLanguages = allowedPublicLanguages;
        this.engine = engine;
        Collection<PolyglotLanguage> languages = engine.idToLanguage.values();
        this.contexts = new PolyglotLanguageContext[languages.size() + 1];
        this.contexts[PolyglotEngineImpl.HOST_LANGUAGE_INDEX] = new PolyglotLanguageContext(this, engine.hostLanguage, null, applicationArguments.get(PolyglotEngineImpl.HOST_LANGUAGE_ID),
                        new HashMap<>());

        testNoEngineOptions(options);
        for (PolyglotLanguage language : languages) {
            OptionValuesImpl values = language.getOptionValues().copy();
            values.putAll(options);

            PolyglotLanguageContext languageContext = new PolyglotLanguageContext(this, language, values, applicationArguments.get(language.getId()), new HashMap<>());
            this.contexts[language.index] = languageContext;
        }
    }

    // Test that "engine options" are not present among the options designated for this context
    private void testNoEngineOptions(Map<String, String> options) {
        String engineOption = engine.findPublicEngineOption(options);
        if (engineOption != null) {
            throw new IllegalArgumentException("Option " + engineOption + " is supported, but cannot be configured for contexts with a shared engine set." +
                            " To resolve this, configure the option when creating the Engine.");
        }
    }

    /*
     * Constructor for inner contexts.
     */
    @SuppressWarnings("hiding")
    PolyglotContextImpl(PolyglotLanguageContext creator, Map<String, Object> config) {
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
                        new HashMap<>());
        for (PolyglotLanguage language : languages) {
            OptionValuesImpl values = parent.contexts[language.index].getOptionValues().copy();

            Map<String, Object> languageConfig;
            if (creator.language == language) {
                languageConfig = config;
            } else {
                languageConfig = new HashMap<>();
            }

            PolyglotLanguageContext languageContext = new PolyglotLanguageContext(this, language, values, applicationArguments.get(language.getId()), languageConfig);
            this.contexts[language.index] = languageContext;
        }
        this.parent.childContexts.add(this);
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
    synchronized PolyglotContextImpl enterThreadChanged() {
        engine.checkState();
        if (closed) {
            throw new PolyglotIllegalStateException("The Context is already closed.");
        }

        Thread current = Thread.currentThread();
        PolyglotThreadInfo threadInfo = this.lastThread;
        assert threadInfo != null;

        if (threadInfo.thread != current) {
            boolean needsInitialization = false;
            threadInfo = threads.get(current);
            if (threadInfo == null) {
                threadInfo = createThreadInfo(current);
                needsInitialization = true;
            }

            boolean transitionToMultiThreading = singleThreaded.isValid() && hasActiveOtherThread(true);
            if (transitionToMultiThreading) {
                // recheck all thread accesses
                checkAllThreadAccesses();
            }

            if (needsInitialization) {
                threads.put(current, threadInfo);
            }

            // enter the thread info already
            PolyglotContextImpl prev = (PolyglotContextImpl) CURRENT.setReturnParent(this);
            threadInfo.enter();

            if (transitionToMultiThreading) {
                // we need to verify that all languages give access
                // to all threads in multi-threaded mode.
                transitionToMultiThreaded();
            }

            if (needsInitialization) {
                initializeNewThread(current);
            }

            // never cache last thread on close or when cancelling
            if (!closed && !cancelling) {
                lastThread = threadInfo;
            }

            return prev;
        } else {
            // enter the thread info already
            PolyglotContextImpl prev = (PolyglotContextImpl) CURRENT.setReturnParent(this);
            threadInfo.enter();
            return prev;
        }
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
            if (!closed && !cancelling) {
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
                hostContext.ensureInitialized();
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
                TruffleLanguage<?> spi = NODES.getLanguageSpi(lang.language.info);
                if (languageClazz != TruffleLanguage.class && languageClazz.isInstance(spi)) {
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
        languageContext.checkAccess();
        Object prev = languageContext.enter();
        try {
            return languageContext.ensureInitialized();
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
            languageContext.checkAccess();
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
            // clear interrupted status after closing
            // needed because we interrupt when closing from another thread.
            Thread.interrupted();
            notifyAll();
        }
    }

    synchronized boolean closeImpl(boolean cancelIfExecuting, boolean waitForPolyglotThreads) {
        if (!closed) {

            // triggers a thread changed event which requires synchronization on the next
            // enter/leave.
            lastThread = PolyglotThreadInfo.NULL;

            if (cancelIfExecuting) {
                cancelling = true;
                PolyglotThreadInfo currentTInfo = getCurrentThreadInfo();
                if (currentTInfo != PolyglotThreadInfo.NULL) {
                    currentTInfo.cancelled = true;
                    // clear interrupted status after closing
                    // needed because we interrupt when closing from another thread.
                    Thread.interrupted();
                }
            }

            if (hasActiveOtherThread(waitForPolyglotThreads)) {
                /*
                 * We are not done executing, cannot close yet.
                 */
                return false;
            }

            Object prev = enter();
            try {
                for (PolyglotContextImpl childContext : childContexts.toArray(new PolyglotContextImpl[0])) {
                    childContext.closeImpl(cancelIfExecuting, waitForPolyglotThreads);
                }

                LinkedList<PolyglotLanguageContext> contextsToDispose = new LinkedList<>();
                for (PolyglotLanguageContext context : contexts) {
                    if (!context.isInitialized()) {
                        continue;
                    }
                    // Dispose non-internal language contexts first,
                    // they may depend on internal ones
                    if (context.language.cache.isInternal()) {
                        contextsToDispose.addLast(context);
                    } else {
                        contextsToDispose.addFirst(context);
                    }
                }
                for (PolyglotLanguageContext context : contextsToDispose) {
                    try {
                        context.dispose();
                    } catch (Exception | Error ex) {
                        throw wrapGuestException(context, ex);
                    }
                }
                assert childContexts.isEmpty();
            } finally {
                leave(prev);
                if (parent != null) {
                    synchronized (parent) {
                        parent.childContexts.remove(this);
                    }
                } else {
                    engine.removeContext(this);
                }
                lastThread = PolyglotThreadInfo.NULL;
                closed = true;
                cancelling = false;
            }
            return true;
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
        PolyglotThreadInfo currentTInfo = lastThread;

        if (currentTInfo.thread != Thread.currentThread()) {
            currentTInfo = threads.get(Thread.currentThread());
            if (currentTInfo == null) {
                // closing from a thread we have never seen.
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

}
