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
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

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

    private static final Assumption constantStoreAssumption = Truffle.getRuntime().createAssumption("dynamic context store");
    private static final Assumption dynamicStoreAssumption = Truffle.getRuntime().createAssumption("constant context store");
    @CompilationFinal private static WeakReference<PolyglotContextImpl> contextConstant = new WeakReference<>(null);
    private static volatile PolyglotContextImpl contextDynamic;
    @CompilationFinal private static volatile Thread contextSingleThread;
    private static volatile ThreadLocal<PolyglotContextImpl> contextThreadStore;

    private final Assumption notClosingAssumption = Truffle.getRuntime().createAssumption("not closed");
    final AtomicReference<Thread> boundThread = new AtomicReference<>(null);

    volatile boolean closed;
    volatile CountDownLatch closingLatch;
    int enteredCount = 0;
    final PolyglotEngineImpl engine;
    @CompilationFinal(dimensions = 1) final PolyglotLanguageContext[] contexts;

    final PolyglotContextImpl parent;
    final OutputStream out;
    final OutputStream err;
    final InputStream in;
    final Map<String, Value> polyglotScope = new HashMap<>();
    final Predicate<String> classFilter;
    final boolean hostAccessAllowed;

    // map from class to language index
    private final FinalIntMap languageIndexMap = new FinalIntMap();

    final Map<Object, CallTarget> javaInteropCache = new HashMap<>();
    final Set<String> allowedPublicLanguages;
    final Map<String, String[]> applicationArguments;
    final Set<PolyglotContextImpl> childContexts = new LinkedHashSet<>();

    /*
     * Constructor for outer contexts.
     */
    PolyglotContextImpl(PolyglotEngineImpl engine, final OutputStream out,
                    OutputStream err,
                    InputStream in,
                    boolean hostAccessAllowed,
                    Predicate<String> classFilter,
                    Map<String, String> options,
                    Map<String, String[]> applicationArguments,
                    Set<String> allowedPublicLanguages) {
        super(engine.impl);
        this.parent = null;
        this.hostAccessAllowed = hostAccessAllowed;
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

        for (PolyglotLanguage language : languages) {
            OptionValuesImpl values = language.getOptionValues().copy();
            values.putAll(options);

            PolyglotLanguageContext languageContext = new PolyglotLanguageContext(this, language, values, applicationArguments.get(language.getId()), new HashMap<>());
            this.contexts[language.index] = languageContext;
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

    Predicate<String> getClassFilter() {
        return classFilter;
    }

    static PolyglotContextImpl current() {
        // can be used on the fast path
        PolyglotContextImpl store;
        if (constantStoreAssumption.isValid()) {
            // we can skip the constantEntered check in compiled code, because we are assume we are
            // always entered in such cases.
            if (CompilerDirectives.inCompiledCode()) {
                store = contextConstant.get();
            } else {
                PolyglotContextImpl context = contextConstant.get();
                if (context != null && context.enteredCount > 0) {
                    store = context;
                } else {
                    store = null;
                }
            }
        } else if (dynamicStoreAssumption.isValid()) {
            // multiple context single thread
            store = contextDynamic;
        } else {
            // multiple context multiple threads
            store = getThreadLocalStore(contextThreadStore);
        }
        if (store != null) {
            assert validThread(store);
        }
        return store;
    }

    private static boolean validThread(PolyglotContextImpl store) {
        Thread boundThread = store.boundThread.get();
        assert boundThread == null || boundThread == Thread.currentThread() || store.enteredCount == 0 : "Attempt to access context from an unbound thread.";
        return true;
    }

    @TruffleBoundary
    private static PolyglotContextImpl getThreadLocalStore(ThreadLocal<PolyglotContextImpl> tls) {
        return tls.get();
    }

    static PolyglotContextImpl requireContext() {
        PolyglotContextImpl context = current();
        if (context == null) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("No current context found.");
        }
        return context;
    }

    PolyglotContextImpl enter() {
        Thread current = Thread.currentThread();
        Thread thread = this.boundThread.get();
        if (thread != current) {
            CompilerDirectives.transferToInterpreter();
            int enterCount = this.enteredCount;
            if (enterCount > 0 || !boundThread.compareAndSet(thread, current)) {
                throw new IllegalStateException(
                                String.format("The context was accessed from thread %s but is currently accessed form thread %s. " +
                                                "The context cannot be accessed from multiple threads at the same time. ",
                                                boundThread.get(), Thread.currentThread()));
            }
        }
        if (!notClosingAssumption.isValid()) {
            engine.checkState();
            if (closed) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Language context is already closed.");
            }
        }
        enteredCount++;
        if (constantStoreAssumption.isValid()) {
            if (contextConstant.get() == this) {
                return null;
            }
        } else if (dynamicStoreAssumption.isValid()) {
            PolyglotContextImpl prevStore = contextDynamic;
            if (Thread.currentThread() == contextSingleThread) {
                contextDynamic = this;
                return prevStore;
            }
        } else {
            // fast path multiple threads
            ThreadLocal<PolyglotContextImpl> tlstore = contextThreadStore;
            assert tlstore != null;
            PolyglotContextImpl currentstore = getThreadLocalStore(tlstore);
            if (currentstore != this) {
                setThreadLocalStore(tlstore, this);
            }
            return currentstore;
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return enterSlowPath();
    }

    void leave(Object prev) {
        assert boundThread.get() == Thread.currentThread() : "invalid thread when leaving";
        int result = --enteredCount;
        if (!notClosingAssumption.isValid()) {
            if (result <= 0) {
                if (closingLatch != null) {
                    CompilerDirectives.transferToInterpreter();
                    close(false);
                }
            }
        }
        // only constant stores should not be cleared as they use a compilation final weak
        // reference.
        if (constantStoreAssumption.isValid()) {
            // nothing to do on leave.
        } else if (dynamicStoreAssumption.isValid()) {
            contextDynamic = (PolyglotContextImpl) prev;
        } else {
            ThreadLocal<PolyglotContextImpl> tlstore = contextThreadStore;
            assert tlstore != null;
            setThreadLocalStore(tlstore, (PolyglotContextImpl) prev);
        }
    }

    @TruffleBoundary
    private static void setThreadLocalStore(ThreadLocal<PolyglotContextImpl> tlstore, PolyglotContextImpl store) {
        tlstore.set(store);
    }

    @TruffleBoundary
    private synchronized PolyglotContextImpl enterSlowPath() {
        PolyglotContextImpl prev = null;
        if (constantStoreAssumption.isValid()) {
            if (contextConstant.get() == null) {
                contextConstant = new WeakReference<>(this);
                contextSingleThread = Thread.currentThread();
                return null;
            } else {
                constantStoreAssumption.invalidate();
                prev = contextConstant.get();
                contextConstant.clear();
            }
        }
        if (dynamicStoreAssumption.isValid()) {
            Thread currentThread = Thread.currentThread();
            if (contextDynamic == null && contextSingleThread == currentThread) {
                contextDynamic = this;
                return prev;
            } else {
                final PolyglotContextImpl initialEngine = contextDynamic == null ? prev : contextDynamic;
                contextThreadStore = new ThreadLocal<PolyglotContextImpl>() {
                    @Override
                    protected PolyglotContextImpl initialValue() {
                        return initialEngine;
                    }
                };
                contextThreadStore.set(this);
                dynamicStoreAssumption.invalidate();
                prev = initialEngine;
            }
        }
        contextDynamic = null;

        assert !constantStoreAssumption.isValid();
        assert !dynamicStoreAssumption.isValid();

        // ensure cleaned up speculation
        assert contextThreadStore != null;
        return prev;
    }

    Object importSymbolFromLanguage(String symbolName) {
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

    private Object findLegacyExportedSymbol(String name, boolean onlyExplicit) {
        for (PolyglotLanguageContext languageContext : contexts) {
            Env env = languageContext.env;
            if (env != null) {
                return LANGUAGE.findExportedSymbol(env, name, onlyExplicit);
            }
        }
        return null;
    }

    void exportSymbolFromLanguage(PolyglotLanguageContext languageConext, String symbolName, Object value) {
        if (value == null) {
            polyglotScope.remove(symbolName);
        } else if (!isGuestInteropValue(value)) {
            throw new IllegalArgumentException(String.format("Invalid exported symbol value %s. Only interop and primitive values can be exported.", value.getClass().getName()));
        } else {
            polyglotScope.put(symbolName, languageConext.toHostValue(value));
        }
    }

    @Override
    public void exportSymbol(String symbolName, Object value) {
        Object prev = enter();
        try {
            Value resolvedValue;
            if (value instanceof Value) {
                resolvedValue = (Value) value;
            } else {
                PolyglotLanguageContext hostContext = getHostContext();
                resolvedValue = hostContext.toHostValue(hostContext.toGuestValue(value));
            }
            polyglotScope.put(symbolName, resolvedValue);
        } finally {
            leave(prev);
        }
    }

    @Override
    public Value importSymbol(String symbolName) {
        Object prev = enter();
        try {
            Value value = polyglotScope.get(symbolName);
            if (value == null) {
                Object legacySymbol = findLegacyExportedSymbol(symbolName);
                if (legacySymbol == null) {
                    value = null;
                } else {
                    value = getHostContext().toHostValue(legacySymbol);
                }
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

    PolyglotLanguageContext findLanguageContext(String mimeTypeOrId, boolean failIfNotFound) {
        for (PolyglotLanguageContext language : contexts) {
            LanguageCache cache = language.language.cache;
            if (cache.getId().equals(mimeTypeOrId) || language.language.cache.getMimeTypes().contains(mimeTypeOrId)) {
                return language;
            }
        }
        if (failIfNotFound) {
            Set<String> mimeTypes = new LinkedHashSet<>();
            for (PolyglotLanguageContext language : contexts) {
                mimeTypes.add(language.language.cache.getId());
            }
            throw new IllegalStateException("No language for id " + mimeTypeOrId + " found. Supported languages are: " + mimeTypes);
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
        assert boundThread.get() == null || boundThread.get() == Thread.currentThread() : "not designed for thread-safety";
        int indexValue = languageIndexMap.get(languageClass);
        if (indexValue == -1) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            PolyglotLanguageContext context = findLanguageContext(languageClass, false);
            if (context == null) {
                throw new IllegalArgumentException(String.format("Illegal or unregistered language class provided %s.", languageClass.getName()));
            }
            indexValue = context.language.index;
            languageIndexMap.put(languageClass, indexValue);
        }
        return contexts[indexValue];
    }

    @Override
    public boolean initializeLanguage(String languageId) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        PolyglotLanguageContext languageContext = this.contexts[language.index];
        languageContext.checkAccess();
        Object prev = enter();
        try {
            return languageContext.ensureInitialized();
        } catch (Throwable t) {
            throw wrapGuestException(languageContext, t);
        } finally {
            leave(prev);
        }
    }

    @Override
    public Value eval(String languageId, Object sourceImpl) {
        PolyglotLanguage language = requirePublicLanguage(languageId);
        Object prev = enter();
        PolyglotLanguageContext languageContext = contexts[language.index];
        try {
            com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) sourceImpl;
            CallTarget target = languageContext.sourceCache.get(source);
            if (target == null) {
                languageContext.ensureInitialized();
                target = LANGUAGE.parse(languageContext.env, source, null);
                if (target == null) {
                    throw new IllegalStateException(String.format("Parsing resulted in a null CallTarget for %s.", source));
                }
                languageContext.sourceCache.put(source, target);
            }
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
        if (language == null) {
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
        closeImpl(cancelIfExecuting);
        if (cancelIfExecuting) {
            engine.getCancelHandler().waitForClosing(this);
        }
        if (engine.boundEngine) {
            engine.ensureClosed(cancelIfExecuting, false);
        }
    }

    void waitForClose() {
        assert boundThread.get() == null || boundThread.get() != Thread.currentThread() : "cannot wait on current thread";
        while (!closed) {
            CountDownLatch closing = closingLatch;
            if (closing == null) {
                return;
            }
            try {
                if (closing.await(100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            } catch (InterruptedException e) {
            }
        }
    }

    synchronized void closeImpl(boolean cancelIfExecuting) {
        if (!closed) {
            Thread thread = boundThread.get();
            if (cancelIfExecuting) {
                if (thread != null && Thread.currentThread() != thread) {
                    if (closingLatch == null) {
                        notClosingAssumption.invalidate();
                        closingLatch = new CountDownLatch(1);
                    }
                    return;
                }
            }

            Object prev = enter();
            try {

                for (PolyglotContextImpl childContext : childContexts) {
                    childContext.closeImpl(cancelIfExecuting);
                }

                for (PolyglotLanguageContext context : contexts) {
                    try {
                        context.dispose();
                    } catch (Exception | Error ex) {
                        try {
                            err.write(String.format("Error closing language %s: %n", context.language.cache.getId()).getBytes());
                        } catch (IOException e) {
                            ex.addSuppressed(e);
                        }
                        ex.printStackTrace(new PrintStream(err));
                    }
                }
                childContexts.clear();
                engine.removeContext(this);
                notClosingAssumption.invalidate();
                closed = true;

                if (closingLatch != null) {
                    closingLatch.countDown();
                    closingLatch = null;
                }

            } finally {
                leave(prev);
            }
        }
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
