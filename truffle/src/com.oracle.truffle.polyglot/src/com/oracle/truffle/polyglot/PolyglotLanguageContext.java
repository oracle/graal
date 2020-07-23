/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.proxy.Proxy;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.polyglot.PolyglotLanguageContextFactory.ToGuestValueNodeGen;

final class PolyglotLanguageContext implements PolyglotImpl.VMObject {

    private static final TruffleLogger LOG = TruffleLogger.getLogger(PolyglotEngineImpl.OPTION_GROUP_ENGINE, PolyglotLanguageContext.class);

    /*
     * Lazily created when a language context is created.
     */
    final class Lazy {

        final PolyglotSourceCache sourceCache;
        final Set<PolyglotThread> activePolyglotThreads;
        final Object polyglotGuestBindings;
        final Map<Class<?>, PolyglotValue> valueCache;
        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        final PolyglotLanguageInstance languageInstance;
        @CompilationFinal Map<String, LanguageInfo> accessibleInternalLanguages;
        @CompilationFinal Map<String, LanguageInfo> accessiblePublicLanguages;
        final Object internalFileSystemContext;
        final Object publicFileSystemContext;

        Lazy(PolyglotLanguageInstance languageInstance, PolyglotContextConfig config) {
            /*
             * Important anything that is initialized here must be properly patched in #patch.
             */
            this.languageInstance = languageInstance;
            this.sourceCache = languageInstance.getSourceCache();
            this.activePolyglotThreads = new HashSet<>();
            this.polyglotGuestBindings = new PolyglotBindings(PolyglotLanguageContext.this);
            this.uncaughtExceptionHandler = new PolyglotUncaughtExceptionHandler();
            this.valueCache = new ConcurrentHashMap<>();
            this.computeAccessPermissions(config);
            // file systems are patched after preinitialization internally using a delegate field
            this.publicFileSystemContext = EngineAccessor.LANGUAGE.createFileSystemContext(PolyglotLanguageContext.this, config.fileSystem);
            this.internalFileSystemContext = EngineAccessor.LANGUAGE.createFileSystemContext(PolyglotLanguageContext.this, config.internalFileSystem);
        }

        void computeAccessPermissions(PolyglotContextConfig config) {
            this.accessibleInternalLanguages = computeAccessibleLanguages(config, true);
            this.accessiblePublicLanguages = computeAccessibleLanguages(config, false);
        }

        private Map<String, LanguageInfo> computeAccessibleLanguages(PolyglotContextConfig config, boolean internal) {
            PolyglotLanguage thisLanguage = languageInstance.language;
            if (thisLanguage.isHost()) {
                return languageInstance.getEngine().idToInternalLanguageInfo;
            }
            boolean embedderAllAccess = config.allowedPublicLanguages.isEmpty();
            PolyglotEngineImpl engine = languageInstance.getEngine();
            UnmodifiableEconomicSet<String> configuredAccess = engine.getAPIAccess().getEvalAccess(config.polyglotAccess, thisLanguage.getId());

            EconomicSet<String> resolveLanguages;
            if (embedderAllAccess) {
                if (configuredAccess == null) {
                    if (internal) {
                        return engine.idToInternalLanguageInfo;
                    } else {
                        resolveLanguages = EconomicSet.create(Equivalence.DEFAULT, configuredAccess);
                        resolveLanguages.addAll(engine.idToInternalLanguageInfo.keySet());
                    }
                } else {
                    resolveLanguages = EconomicSet.create(Equivalence.DEFAULT, configuredAccess);
                    resolveLanguages.add(thisLanguage.getId());
                }
            } else {
                if (configuredAccess == null) {
                    // all access configuration
                    configuredAccess = config.allowedPublicLanguages;
                }
                resolveLanguages = EconomicSet.create(Equivalence.DEFAULT, configuredAccess);
                resolveLanguages.add(thisLanguage.getId());
            }

            Map<String, LanguageInfo> resolvedLanguages = new LinkedHashMap<>();
            for (String id : resolveLanguages) {
                PolyglotLanguage resolvedLanguage = engine.idToLanguage.get(id);
                if (resolvedLanguage != null) { // resolved languages might not be on the
                                                // class-path.
                    if (!internal && resolvedLanguage.cache.isInternal()) {
                        // filter internal
                        continue;
                    }
                    resolvedLanguages.put(id, resolvedLanguage.info);
                }
            }
            if (internal) {
                addDependentLanguages(engine, resolvedLanguages, thisLanguage);
            }

            // all internal languages are accessible by default
            if (internal) {
                for (Entry<String, PolyglotLanguage> entry : languageInstance.getEngine().idToLanguage.entrySet()) {
                    if (entry.getValue().cache.isInternal()) {
                        resolvedLanguages.put(entry.getKey(), entry.getValue().info);
                    }
                }
                assert assertPermissionsConsistent(resolvedLanguages, languageInstance.language, config);
            }
            return resolvedLanguages;
        }

        private boolean assertPermissionsConsistent(Map<String, LanguageInfo> resolvedLanguages, PolyglotLanguage thisLanguage, PolyglotContextConfig config) {
            for (Entry<String, PolyglotLanguage> entry : languageInstance.getEngine().idToLanguage.entrySet()) {
                boolean permitted = config.isAccessPermitted(thisLanguage, entry.getValue());
                assert permitted == resolvedLanguages.containsKey(entry.getKey()) : "inconsistent access permissions";
            }
            return true;
        }

        private void addDependentLanguages(PolyglotEngineImpl engine, Map<String, LanguageInfo> resolvedLanguages, PolyglotLanguage currentLanguage) {
            for (String dependentLanguage : currentLanguage.cache.getDependentLanguages()) {
                PolyglotLanguage dependent = engine.idToLanguage.get(dependentLanguage);
                if (dependent == null) { // dependent languages might not exist.
                    continue;
                }
                if (resolvedLanguages.containsKey(dependentLanguage)) {
                    continue; // cycle or duplicate detection
                }
                resolvedLanguages.put(dependentLanguage, dependent.info);
                addDependentLanguages(engine, resolvedLanguages, dependent);
            }
        }
    }

    final PolyglotContextImpl context;
    final PolyglotLanguage language;
    final boolean eventsEnabled;

    private volatile Thread creatingThread;
    private volatile boolean initialized;
    volatile boolean finalized;
    @CompilationFinal private volatile Value hostBindings;
    @CompilationFinal private volatile Lazy lazy;
    @CompilationFinal volatile Env env; // effectively final
    @CompilationFinal private volatile List<Object> languageServices = Collections.emptyList();

    PolyglotLanguageContext(PolyglotContextImpl context, PolyglotLanguage language) {
        this.context = context;
        this.language = language;
        this.eventsEnabled = !language.isHost();
    }

    boolean isPolyglotBindingsAccessAllowed() {
        if (context.config.polyglotAccess == PolyglotAccess.ALL) {
            return true;
        }

        UnmodifiableEconomicSet<String> accessibleLanguages = getAPIAccess().getBindingsAccess(context.config.polyglotAccess);
        if (accessibleLanguages == null) {
            return true;
        }
        return accessibleLanguages.contains(language.getId());
    }

    boolean isPolyglotEvalAllowed(String targetLanguage) {
        if (context.config.polyglotAccess == PolyglotAccess.ALL) {
            return true;
        } else if (targetLanguage != null && language.getId().equals(targetLanguage)) {
            return true;
        }
        UnmodifiableEconomicSet<String> accessibleLanguages = getAPIAccess().getEvalAccess(context.config.polyglotAccess,
                        language.getId());
        if (accessibleLanguages == null || accessibleLanguages.isEmpty()) {
            return false;
        } else if (accessibleLanguages.size() > 1 || !accessibleLanguages.iterator().next().equals(language.getId())) {
            return targetLanguage == null || accessibleLanguages.contains(targetLanguage);
        }
        return false;
    }

    Thread.UncaughtExceptionHandler getPolyglotExceptionHandler() {
        assert env != null;
        return lazy.uncaughtExceptionHandler;
    }

    Map<Class<?>, PolyglotValue> getValueCache() {
        assert env != null;
        return lazy.valueCache;
    }

    Map<String, LanguageInfo> getAccessibleLanguages(boolean allowInternalAndDependent) {
        if (allowInternalAndDependent) {
            return lazy.accessibleInternalLanguages;
        } else {
            return lazy.accessiblePublicLanguages;
        }
    }

    PolyglotLanguageInstance getLanguageInstance() {
        assert env != null;
        return lazy.languageInstance;
    }

    private void checkThreadAccess(Env localEnv) {
        assert Thread.holdsLock(context);
        boolean singleThreaded = context.isSingleThreaded();
        Thread firstFailingThread = null;
        for (PolyglotThreadInfo threadInfo : context.getSeenThreads().values()) {
            if (!LANGUAGE.isThreadAccessAllowed(localEnv, threadInfo.getThread(), singleThreaded)) {
                firstFailingThread = threadInfo.getThread();
                break;
            }
        }
        if (firstFailingThread != null) {
            throw PolyglotContextImpl.throwDeniedThreadAccess(firstFailingThread, singleThreaded, Arrays.asList(language));
        }
    }

    Object getContextImpl() {
        if (env != null) {
            return LANGUAGE.getContext(env);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return null;
        }
    }

    Object getPublicFileSystemContext() {
        Lazy l = lazy;
        if (l != null) {
            return l.publicFileSystemContext;
        } else {
            return null;
        }
    }

    Object getInternalFileSystemContext() {
        Lazy l = lazy;
        if (l != null) {
            return l.internalFileSystemContext;
        } else {
            return null;
        }
    }

    Value getHostBindings() {
        assert initialized;
        if (this.hostBindings == null) {
            synchronized (this) {
                if (this.hostBindings == null) {
                    Object prev = context.engine.enterIfNeeded(context);
                    try {
                        Iterable<Scope> scopes = LANGUAGE.findTopScopes(env);
                        this.hostBindings = this.asValue(PolyglotLanguageBindings.create(scopes));
                    } finally {
                        context.engine.leaveIfNeeded(prev, context);
                    }
                }
            }
        }
        return this.hostBindings;
    }

    Object getPolyglotGuestBindings() {
        assert isInitialized();
        return this.lazy.polyglotGuestBindings;
    }

    boolean isInitialized() {
        return initialized;
    }

    CallTarget parseCached(PolyglotLanguage accessingLanguage, Source source, String[] argumentNames) throws AssertionError {
        ensureInitialized(accessingLanguage);
        PolyglotSourceCache cache = lazy.sourceCache;
        assert cache != null;
        return cache.parseCached(this, source, argumentNames);
    }

    Env requireEnv() {
        Env localEnv = this.env;
        if (localEnv == null) {
            throw shouldNotReachHere("No language context is active on this thread.");
        }
        return localEnv;
    }

    boolean finalizeContext(boolean notifyInstruments) {
        if (!finalized) {
            finalized = true;
            LANGUAGE.finalizeContext(env);
            if (eventsEnabled && notifyInstruments) {
                EngineAccessor.INSTRUMENT.notifyLanguageContextFinalized(context.engine, context.truffleContext, language.info);
            }
            return true;
        }
        return false;
    }

    boolean dispose() {
        assert Thread.holdsLock(context);
        Env localEnv = this.env;
        if (localEnv != null) {
            if (!lazy.activePolyglotThreads.isEmpty()) {
                // this should show up as internal error so it does not use PolyglotEngineException
                throw new IllegalStateException("The language did not complete all polyglot threads but should have: " + lazy.activePolyglotThreads);
            }
            for (PolyglotThreadInfo threadInfo : context.getSeenThreads().values()) {
                assert threadInfo != PolyglotThreadInfo.NULL;
                final Thread thread = threadInfo.getThread();
                if (thread == null || threadInfo.isPolyglotThread(context)) {
                    // polyglot threads need to be cleaned up by the language
                    continue;
                }
                LANGUAGE.disposeThread(localEnv, thread);
            }
            LANGUAGE.dispose(localEnv);
            return true;
        }
        return false;
    }

    void notifyDisposed(boolean notifyInstruments) {
        if (eventsEnabled && notifyInstruments) {
            EngineAccessor.INSTRUMENT.notifyLanguageContextDisposed(context.engine, context.truffleContext, language.info);
        }
        language.freeInstance(lazy.languageInstance);
    }

    Object enterThread(PolyglotThread thread) {
        assert isInitialized();
        assert Thread.currentThread() == thread;
        synchronized (context) {
            Object prev = context.engine.enter(context);
            lazy.activePolyglotThreads.add(thread);
            return prev;
        }
    }

    void leaveThread(Object prev, PolyglotThread thread) {
        assert isInitialized();
        assert Thread.currentThread() == thread;
        synchronized (context) {
            Map<Thread, PolyglotThreadInfo> seenThreads = context.getSeenThreads();
            PolyglotThreadInfo info = seenThreads.get(thread);
            if (info == null) {
                // already disposed
                return;
            }
            for (PolyglotLanguageContext languageContext : context.contexts) {
                if (languageContext.isInitialized()) {
                    LANGUAGE.disposeThread(languageContext.env, thread);
                }
            }
            lazy.activePolyglotThreads.remove(thread);
            context.engine.leave(prev, context);
            seenThreads.remove(thread);
        }
        EngineAccessor.INSTRUMENT.notifyThreadFinished(context.engine, context.truffleContext, thread);
    }

    boolean isCreated() {
        return lazy != null;
    }

    void ensureCreated(PolyglotLanguage accessingLanguage) {
        if (creatingThread == Thread.currentThread()) {
            throw PolyglotEngineException.illegalState(String.format("Cyclic access to language context for language %s. " +
                            "The context is currently being created.", language.getId()));
        } else if (creatingThread != null) {
            // Wait for creation
            boolean interrupted = false;
            synchronized (context) {
                while (creatingThread != null) {
                    try {
                        context.wait();
                    } catch (InterruptedException e) {
                        // Keep waiting
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        if (lazy == null) {
            checkAccess(accessingLanguage);

            Map<String, Object> creatorConfig = context.creator == language ? context.creatorArguments : Collections.emptyMap();
            PolyglotContextConfig envConfig = context.config;
            PolyglotLanguageInstance lang = language.allocateInstance(envConfig.getOptionValues(language));
            try {
                synchronized (context) {
                    if (lazy == null) {
                        Env localEnv = LANGUAGE.createEnv(this, lang.spi, envConfig.out,
                                        envConfig.err,
                                        envConfig.in,
                                        creatorConfig,
                                        envConfig.getOptionValues(language),
                                        envConfig.getApplicationArguments(language));
                        Lazy localLazy = new Lazy(lang, envConfig);
                        PolyglotValue.createDefaultValues(getImpl(), this, localLazy.valueCache);
                        checkThreadAccess(localEnv);

                        // no more errors after this line
                        creatingThread = Thread.currentThread();
                        env = localEnv;
                        lazy = localLazy;
                        assert EngineAccessor.LANGUAGE.getLanguage(env) != null;

                        try {
                            List<Object> languageServicesCollector = new ArrayList<>();
                            Object contextImpl = LANGUAGE.createEnvContext(localEnv, languageServicesCollector);
                            language.initializeContextClass(contextImpl);
                            context.contextImpls[lang.language.index] = contextImpl;

                            String errorMessage = verifyServices(language.info, languageServicesCollector, language.cache.getServices());
                            if (errorMessage != null) {
                                throw PolyglotEngineException.illegalState(errorMessage);
                            }
                            this.languageServices = languageServicesCollector;
                            lang.language.profile.notifyContextCreate(this, localEnv);
                            if (eventsEnabled) {
                                EngineAccessor.INSTRUMENT.notifyLanguageContextCreated(context.engine, context.truffleContext, language.info);
                            }
                            context.weakReference.freeInstances.add(lang);
                            lang = null; // commit language use
                        } catch (Throwable e) {
                            env = null;
                            lazy = null;
                            throw e;
                        } finally {
                            creatingThread = null;
                            context.notifyAll();
                        }
                    }
                }
            } finally {
                // free not commited language instance
                if (lang != null) {
                    language.freeInstance(lang);
                }
            }
        }
    }

    void close() {
        assert Thread.holdsLock(context);
        lazy = null;
        env = null;
    }

    private static String verifyServices(LanguageInfo info, List<Object> registeredServices, Collection<String> expectedServices) {
        for (String expectedService : expectedServices) {
            boolean found = false;
            for (Object registeredService : registeredServices) {
                if (isSubType(registeredService.getClass(), expectedService)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return String.format("Language %s declares service %s but doesn't register it", info.getName(), expectedService);
            }
        }
        return null;
    }

    private static boolean isSubType(Class<?> clazz, String serviceClass) {
        if (clazz == null) {
            return false;
        }
        if (serviceClass.equals(clazz.getName()) || serviceClass.equals(clazz.getCanonicalName())) {
            return true;
        }
        if (isSubType(clazz.getSuperclass(), serviceClass)) {
            return true;
        }
        for (Class<?> implementedInterface : clazz.getInterfaces()) {
            if (isSubType(implementedInterface, serviceClass)) {
                return true;
            }
        }
        return false;
    }

    boolean ensureInitialized(PolyglotLanguage accessingLanguage) {
        ensureCreated(accessingLanguage);
        boolean wasInitialized = false;
        if (!initialized) {
            synchronized (context) {
                if (!initialized) {
                    initialized = true; // Allow language use during initialization
                    try {
                        if (!context.inContextPreInitialization) {
                            LANGUAGE.initializeThread(env, Thread.currentThread());
                        }
                        LANGUAGE.postInitEnv(env);

                        if (!context.isSingleThreaded()) {
                            LANGUAGE.initializeMultiThreading(env);
                        }

                        for (PolyglotThreadInfo threadInfo : context.getSeenThreads().values()) {
                            final Thread thread = threadInfo.getThread();
                            if (thread == Thread.currentThread()) {
                                continue;
                            }
                            LANGUAGE.initializeThread(env, thread);
                        }

                        wasInitialized = true;
                    } catch (Throwable e) {
                        // language not successfully initialized, reset to avoid inconsistent
                        // language contexts
                        initialized = false;
                        throw e;
                    }
                }
            }
        }
        if (wasInitialized && eventsEnabled) {
            EngineAccessor.INSTRUMENT.notifyLanguageContextInitialized(context.engine, context.truffleContext, language.info);
        }
        return wasInitialized;
    }

    void checkAccess(PolyglotLanguage accessingLanguage) {
        // Always check context first, as it might be invalidated.
        context.checkClosed();
        if (context.disposing) {
            throw PolyglotEngineException.illegalState("The Context is already closed.");
        }
        if (!context.config.isAccessPermitted(accessingLanguage, language)) {
            throw PolyglotEngineException.illegalArgument(String.format("Access to language '%s' is not permitted. ", language.getId()));
        }
        RuntimeException initError = language.initError;
        if (initError != null) {
            throw PolyglotEngineException.illegalState(String.format("Initialization error: %s", initError.getMessage(), initError));
        }
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return context.getEngine();
    }

    boolean patch(PolyglotContextConfig newConfig) {
        if (isCreated()) {
            try {
                OptionValuesImpl newOptionValues = newConfig.getOptionValues(language);
                lazy.computeAccessPermissions(newConfig);
                Env newEnv = LANGUAGE.patchEnvContext(env, newConfig.out, newConfig.err, newConfig.in,
                                Collections.emptyMap(), newOptionValues, newConfig.getApplicationArguments(language));
                if (newEnv != null) {
                    env = newEnv;
                    lazy.languageInstance.patchFirstOptions(newOptionValues);
                    LOG.log(Level.FINE, "Successfully patched context of language: {0}", this.language.getId());
                    return true;
                }
                LOG.log(Level.FINE, "Failed to patch context of language: {0}", this.language.getId());
                return false;
            } catch (Throwable t) {
                if (t instanceof ThreadDeath) {
                    throw t;
                }
                LOG.log(Level.FINE, "Exception during patching context of language: {0}", this.language.getId());
                // The conversion to the host exception happens in the
                // PolyglotEngineImpl.createContext
                throw GuestToHostRootNode.silenceException(RuntimeException.class, t);
            }
        } else {
            return true;
        }
    }

    <S> S lookupService(Class<S> type) {
        for (Object languageService : languageServices) {
            if (type.isInstance(languageService)) {
                return type.cast(languageService);
            }
        }
        return null;
    }

    static final class ToGuestValuesNode extends Node {

        @Children private volatile ToGuestValueNode[] toGuestValue;
        @CompilationFinal private volatile boolean needsCopy = false;
        @CompilationFinal private volatile boolean generic = false;

        private ToGuestValuesNode() {
        }

        public Object[] apply(PolyglotLanguageContext languageContext, Object[] args) {
            ToGuestValueNode[] nodes = this.toGuestValue;
            if (nodes == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                nodes = new ToGuestValueNode[args.length];
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i] = ToGuestValueNodeGen.create();
                }
                toGuestValue = insert(nodes);
            }
            if (args.length == nodes.length) {
                // fast path
                if (nodes.length == 0) {
                    return args;
                } else {
                    Object[] newArgs = fastToGuestValuesUnroll(nodes, languageContext, args);
                    return newArgs;
                }
            } else {
                if (!generic || nodes.length != 1) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    nodes = Arrays.copyOf(nodes, 1);
                    if (nodes[0] == null) {
                        nodes[0] = ToGuestValueNodeGen.create();
                    }
                    this.toGuestValue = insert(nodes);
                    this.generic = true;
                }
                if (args.length == 0) {
                    return args;
                }
                return fastToGuestValues(nodes[0], languageContext, args);
            }
        }

        /*
         * Specialization for constant number of arguments. Uses a profile for each argument.
         */
        @ExplodeLoop
        private Object[] fastToGuestValuesUnroll(ToGuestValueNode[] nodes, PolyglotLanguageContext languageContext, Object[] args) {
            Object[] newArgs = needsCopy ? new Object[nodes.length] : args;
            for (int i = 0; i < nodes.length; i++) {
                Object arg = args[i];
                Object newArg = nodes[i].execute(languageContext, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[nodes.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        /*
         * Specialization that supports multiple argument lengths but uses a single profile for all
         * arguments.
         */
        private Object[] fastToGuestValues(ToGuestValueNode node, PolyglotLanguageContext languageContext, Object[] args) {
            assert toGuestValue[0] != null;
            Object[] newArgs = needsCopy ? new Object[args.length] : args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = node.execute(languageContext, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = new Object[args.length];
                    System.arraycopy(args, 0, newArgs, 0, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        public static ToGuestValuesNode create() {
            return new ToGuestValuesNode();
        }

    }

    static final class Generic {
        private Generic() {
            throw shouldNotReachHere("no instances");
        }
    }

    @GenerateUncached
    abstract static class ToGuestValueNode extends Node {

        abstract Object execute(PolyglotLanguageContext context, Object receiver);

        @Specialization(guards = "receiver == null")
        static Object doNull(PolyglotLanguageContext context, @SuppressWarnings("unused") Object receiver) {
            return context.toGuestValue(receiver);
        }

        @Specialization(guards = {"receiver != null", "receiver.getClass() == cachedReceiver"}, limit = "3")
        static Object doCached(PolyglotLanguageContext context, Object receiver, @Cached("receiver.getClass()") Class<?> cachedReceiver) {
            return context.toGuestValue(cachedReceiver.cast(receiver));
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static Object doUncached(PolyglotLanguageContext context, Object receiver) {
            return context.toGuestValue(receiver);
        }
    }

    @TruffleBoundary
    Value asValue(Object guestValue) {
        assert lazy != null;
        assert guestValue != null;
        assert !(guestValue instanceof Value);
        assert !(guestValue instanceof Proxy);
        Object receiver = guestValue;
        PolyglotValue cache = lazy.valueCache.get(receiver.getClass());
        if (cache == null) {
            Object prev = language.engine.enterIfNeeded(this.context);
            try {
                cache = lookupValueCache(guestValue);
            } finally {
                language.engine.leaveIfNeeded(prev, this.context);
            }
        }
        return getAPIAccess().newValue(receiver, cache);
    }

    synchronized PolyglotValue lookupValueCache(Object guestValue) {
        assert toGuestValue(guestValue) == guestValue : "Not a valid guest value: " + guestValue + ". Only interop values are allowed to be exported.";
        Object prev = context.engine.enterIfNeeded(context);
        try {
            PolyglotValue cache = lazy.valueCache.computeIfAbsent(guestValue.getClass(), new Function<Class<?>, PolyglotValue>() {
                public PolyglotValue apply(Class<?> t) {
                    return PolyglotValue.createInteropValue(PolyglotLanguageContext.this, (TruffleObject) guestValue, guestValue.getClass());
                }
            });
            return cache;
        } finally {
            context.engine.leaveIfNeeded(prev, context);
        }
    }

    static final class ToHostValueNode {

        final APIAccess apiAccess;
        @CompilationFinal volatile Class<?> cachedClass;
        @CompilationFinal volatile PolyglotValue cachedValue;
        @CompilationFinal volatile PolyglotLanguageContext cachedContext;

        private ToHostValueNode(PolyglotImpl polyglot) {
            this.apiAccess = polyglot.getAPIAccess();
        }

        Value execute(PolyglotLanguageContext languageContext, Object value) {
            Object receiver = value;
            Class<?> cachedClassLocal = cachedClass;
            PolyglotLanguageContext cachedContextLocal = cachedContext;
            PolyglotValue cache;
            if (cachedClassLocal != Generic.class) {
                if (cachedClassLocal == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    if (languageContext.context.engine.boundEngine) {
                        cachedClass = receiver.getClass();
                        cache = languageContext.lazy.valueCache.get(receiver.getClass());
                        if (cache == null) {
                            cache = languageContext.lookupValueCache(receiver);
                        }
                        cachedContext = languageContext;
                        cachedValue = cache;
                        return apiAccess.newValue(receiver, cache);
                    } else {
                        // TODO this needs to be rewritten to cache that uses
                        // InteropCodeCache and does not store the context in a node directly
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        cachedClass = Generic.class; // switch to generic
                        cachedContext = null;
                        cachedValue = null;
                    }
                } else if (value.getClass() == cachedClassLocal && cachedContextLocal == languageContext) {
                    receiver = CompilerDirectives.inInterpreter() ? receiver : CompilerDirectives.castExact(receiver, cachedClassLocal);
                    cache = cachedValue;
                    if (cache == null) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        // invalid state retry next time for now do generic
                    } else {
                        return apiAccess.newValue(receiver, cache);
                    }
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = Generic.class; // switch to generic
                    cachedContext = null;
                    cachedValue = null;
                    // fall through to generic
                }
            }
            return languageContext.asValue(value);
        }

        public static ToHostValueNode create(PolyglotImpl polyglot) {
            return new ToHostValueNode(polyglot);
        }
    }

    Object toGuestValue(Class<?> receiver) {
        return HostObject.forClass(receiver, this);
    }

    Object toGuestValue(Object hostValue) {
        if (hostValue instanceof Value) {
            Value receiverValue = (Value) hostValue;
            PolyglotValue valueImpl = (PolyglotValue) getAPIAccess().getImpl(receiverValue);
            PolyglotContextImpl valueContext = valueImpl.languageContext != null ? valueImpl.languageContext.context : null;
            Object valueReceiver = getAPIAccess().getReceiver(receiverValue);
            if (valueContext != this.context) {

                valueReceiver = migrateValue(valueReceiver, valueContext);
            }
            return valueReceiver;
        } else if (PolyglotImpl.isGuestPrimitive(hostValue)) {
            return hostValue;
        } else if (hostValue instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject((Proxy) hostValue);
        } else if (hostValue instanceof TruffleObject) {
            return hostValue;
        } else if (hostValue instanceof Class) {
            return HostObject.forClass((Class<?>) hostValue, this);
        } else if (hostValue == null) {
            return HostObject.NULL;
        } else if (hostValue.getClass().isArray()) {
            return HostObject.forObject(hostValue, this);
        } else if (HostWrapper.isInstance(hostValue)) {
            return migrateHostWrapper(HostWrapper.asInstance(hostValue));
        } else {
            return HostInteropReflect.asTruffleViaReflection(hostValue, this);
        }
    }

    private Object migrateValue(Object value, PolyglotContextImpl valueContext) {
        // migration of guest primitives is already handled.
        if (PolyglotImpl.isGuestPrimitive(value)) {
            // allowed to be passed freely
            return value;
        } else if (HostObject.isInstance(value)) {
            return ((HostObject) value).withContext(this);
        } else if (PolyglotProxy.isProxyGuestObject(value)) {
            return value;
        } else if (valueContext == null) {
            /*
             * The only way this can happen is with Value.asValue(TruffleObject). If it happens
             * otherwise, its wrong.
             */
            assert value instanceof TruffleObject;
            return value;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PolyglotEngineException.illegalArgument(String.format("The value '%s' cannot be passed from one context to another. " +
                            "The current context is 0x%x and the argument value originates from context 0x%x.",
                            PolyglotValue.getValueInfo(null, value), context.hashCode(), valueContext.hashCode()));
        }
    }

    private Object migrateHostWrapper(HostWrapper wrapper) {
        Object wrapped = wrapper.getGuestObject();
        PolyglotContextImpl valueContext = wrapper.getContext();
        if (valueContext != this.context) {
            // migrate wrapped value to the context
            wrapped = migrateValue(wrapped, valueContext);
        }
        return wrapped;
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values, int startIndex) {
        Value[] args = new Value[values.length - startIndex];
        for (int i = startIndex; i < values.length; i++) {
            args[i - startIndex] = asValue(values[i]);
        }
        return args;
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values) {
        Value[] args = new Value[values.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = asValue(values[i]);
        }
        return args;
    }

    @Override
    public String toString() {
        return "PolyglotLanguageContext [language=" + language + ", initialized=" + (env != null) + "]";
    }

    private class PolyglotUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(Thread t, Throwable e) {
            Env currentEnv = env;
            if (currentEnv != null) {
                try {
                    e.printStackTrace(new PrintStream(currentEnv.err()));
                } catch (Throwable exc) {
                    // Still show the original error if printing on Env.err() fails for some
                    // reason
                    e.printStackTrace();
                }
            } else {
                e.printStackTrace();
            }
        }
    }

    public Object getLanguageView(Object receiver) {
        EngineAccessor.INTEROP.checkInteropType(receiver);
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(receiver);
        if (lib.hasLanguage(receiver)) {
            try {
                if (!this.isCreated()) {
                    throw PolyglotEngineException.illegalState("Language not yet created. Initialize the language first to request a language view.");
                }
                if (lib.getLanguage(receiver) == this.lazy.languageInstance.spi.getClass()) {
                    return receiver;
                }
            } catch (UnsupportedMessageException e) {
                throw shouldNotReachHere(e);
            }
        }
        return getLanguageViewNoCheck(receiver);
    }

    private boolean validLanguageView(Object result) {
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(result);
        Class<?> languageClass = EngineAccessor.LANGUAGE.getLanguage(env).getClass();
        try {
            assert lib.hasLanguage(result) &&
                            lib.getLanguage(result) == languageClass : String.format("The returned language view of language '%s' must return the class '%s' for InteropLibrary.getLanguage." +
                                            "Fix the implementation of %s.getLanguageView to resolve this.", languageClass.getTypeName(), languageClass.getTypeName(), languageClass.getTypeName());
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
        return true;
    }

    private boolean validScopedView(Object result) {
        InteropLibrary lib = InteropLibrary.getFactory().getUncached(result);
        Class<?> languageClass = EngineAccessor.LANGUAGE.getLanguage(env).getClass();
        try {
            assert lib.hasLanguage(result) &&
                            lib.getLanguage(result) == languageClass : String.format("The returned scoped view of language '%s' must return the class '%s' for InteropLibrary.getLanguage." +
                                            "Fix the implementation of %s.getScopedView to resolve this.", languageClass.getTypeName(), languageClass.getTypeName(), languageClass.getTypeName());
        } catch (UnsupportedMessageException e) {
            throw shouldNotReachHere(e);
        }
        return true;
    }

    public Object getLanguageViewNoCheck(Object receiver) {
        Object result = EngineAccessor.LANGUAGE.getLanguageView(env, receiver);
        assert validLanguageView(result);
        return result;
    }

    public Object getScopedView(Node location, Frame frame, Object value) {
        validateLocationAndFrame(language.info, location, frame);
        Object languageView = getLanguageView(value);
        Object result = EngineAccessor.LANGUAGE.getScopedView(env, location, frame, languageView);
        assert validScopedView(result);
        return result;
    }

    private static void validateLocationAndFrame(LanguageInfo viewLanguage, Node location, Frame frame) {
        RootNode rootNode = location.getRootNode();
        if (rootNode == null) {
            throw PolyglotEngineException.illegalArgument(String.format("The location '%s' does not have a RootNode.", location));
        }
        LanguageInfo nodeLocation = rootNode.getLanguageInfo();
        if (nodeLocation == null) {
            throw PolyglotEngineException.illegalArgument(String.format("The location '%s' does not have a language associated.", location));
        }
        if (nodeLocation != viewLanguage) {
            throw PolyglotEngineException.illegalArgument(String.format("The view language '%s' must match the language of the location %s.", viewLanguage, nodeLocation));
        }
        if (!EngineAccessor.INSTRUMENT.isInstrumentable(location)) {
            throw PolyglotEngineException.illegalArgument(String.format("The location '%s' is not instrumentable but must be to request scoped views.", location));
        }
        if (!rootNode.getFrameDescriptor().equals(frame.getFrameDescriptor())) {
            throw PolyglotEngineException.illegalArgument(String.format("The frame provided does not originate from the location. " +
                            "Expected frame descriptor '%s' but was '%s'.", rootNode.getFrameDescriptor(), frame.getFrameDescriptor()));
        }
    }

}
