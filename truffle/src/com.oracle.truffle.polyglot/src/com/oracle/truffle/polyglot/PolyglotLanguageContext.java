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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

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
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.Source;

final class PolyglotLanguageContext implements PolyglotImpl.VMObject {

    private static final TruffleLogger LOG = TruffleLogger.getLogger("engine", PolyglotLanguageContext.class);

    /*
     * Lazily created when a language context is created.
     */
    final class Lazy {

        final PolyglotSourceCache sourceCache;
        final Map<Class<?>, PolyglotValue> valueCache;
        final PolyglotValue defaultValueCache;
        final Set<PolyglotThread> activePolyglotThreads;
        final Object polyglotGuestBindings;
        final Thread.UncaughtExceptionHandler uncaughtExceptionHandler;
        final PolyglotLanguageInstance languageInstance;

        Lazy(PolyglotLanguageInstance languageInstance) {
            this.valueCache = new ConcurrentHashMap<>();
            this.languageInstance = languageInstance;
            this.sourceCache = languageInstance.getSourceCache();
            this.activePolyglotThreads = new HashSet<>();
            this.defaultValueCache = new PolyglotValue.Default(PolyglotLanguageContext.this);
            this.polyglotGuestBindings = new PolyglotBindings(PolyglotLanguageContext.this, context.polyglotBindings);
            this.uncaughtExceptionHandler = new PolyglotUncaughtExceptionHandler();
        }
    }

    final PolyglotContextImpl context;
    final PolyglotLanguage language;
    final boolean eventsEnabled;

    private volatile boolean creating;
    private volatile boolean initialized;
    volatile boolean finalized;
    @CompilationFinal private volatile Value hostBindings;
    @CompilationFinal private volatile Lazy lazy;

    @CompilationFinal volatile Env env; // effectively final

    PolyglotLanguageContext(PolyglotContextImpl context, PolyglotLanguage language) {
        this.context = context;
        this.language = language;
        this.eventsEnabled = !language.isHost();
    }

    Thread.UncaughtExceptionHandler getPolyglotExceptionHandler() {
        assert env != null;
        return lazy.uncaughtExceptionHandler;
    }

    Map<Class<?>, PolyglotValue> getValueCache() {
        assert env != null;
        return lazy.valueCache;
    }

    PolyglotValue getDefaultValueCache() {
        assert env != null;
        return lazy.defaultValueCache;
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
            if (!LANGUAGE.isThreadAccessAllowed(localEnv, threadInfo.thread, singleThreaded)) {
                firstFailingThread = threadInfo.thread;
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
            return null;
        }
    }

    Value getHostBindings() {
        assert initialized;
        if (this.hostBindings == null) {
            synchronized (this) {
                if (this.hostBindings == null) {
                    Object prev = context.enterIfNeeded();
                    try {
                        Iterable<Scope> scopes = LANGUAGE.findTopScopes(env);
                        this.hostBindings = this.asValue(new PolyglotLanguageBindings(scopes));
                    } catch (Throwable t) {
                        throw PolyglotImpl.wrapGuestException(this, t);
                    } finally {
                        context.leaveIfNeeded(prev);
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
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(
                            "No language context is active on this thread.");
        }
        return localEnv;
    }

    boolean finalizeContext() {
        if (!finalized) {
            finalized = true;
            LANGUAGE.finalizeContext(env);
            if (eventsEnabled) {
                VMAccessor.INSTRUMENT.notifyLanguageContextFinalized(context.engine, context.truffleContext, language.info);
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
                throw new AssertionError("The language did not complete all polyglot threads but should have: " + lazy.activePolyglotThreads);
            }
            for (PolyglotThreadInfo threadInfo : context.getSeenThreads().values()) {
                assert threadInfo.thread != null;
                if (threadInfo.isPolyglotThread(context)) {
                    // polyglot threads need to be cleaned up by the language
                    continue;
                }
                LANGUAGE.disposeThread(localEnv, threadInfo.thread);
            }
            LANGUAGE.dispose(localEnv);
            language.freeInstance(lazy.languageInstance);
            return true;
        }
        return false;
    }

    void notifyDisposed() {
        if (eventsEnabled) {
            VMAccessor.INSTRUMENT.notifyLanguageContextDisposed(context.engine, context.truffleContext, language.info);
        }
    }

    Object enterThread(PolyglotThread thread) {
        assert isInitialized();
        assert Thread.currentThread() == thread;
        synchronized (context) {
            lazy.activePolyglotThreads.add(thread);
            return context.enter();
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
            context.leave(prev);
            seenThreads.remove(thread);
        }
        VMAccessor.INSTRUMENT.notifyThreadFinished(context.engine, context.truffleContext, thread);
    }

    private void ensureCreated(PolyglotLanguage accessingLanguage) {
        if (creating) {
            throw new PolyglotIllegalStateException(String.format("Cyclic access to language context for language %s. " +
                            "The context is currently being created.", language.getId()));
        }
        if (lazy == null) {
            checkAccess(accessingLanguage);

            Map<String, Object> creatorConfig = context.creator == language ? context.creatorArguments : Collections.emptyMap();
            PolyglotContextConfig envConfig = context.config;
            PolyglotLanguageInstance lang = language.allocateInstance(envConfig.getOptionValues(language));
            try {
                synchronized (context) {
                    if (lazy == null) {
                        Env localEnv = LANGUAGE.createEnv(PolyglotLanguageContext.this, lang.spi, envConfig.out,
                                        envConfig.err,
                                        envConfig.in,
                                        creatorConfig,
                                        envConfig.getOptionValues(language),
                                        envConfig.getApplicationArguments(language),
                                        envConfig.fileSystem);
                        Lazy localLazy = new Lazy(lang);
                        PolyglotValue.createDefaultValueCaches(PolyglotLanguageContext.this, localLazy.valueCache);
                        checkThreadAccess(localEnv);

                        // no more errors after this line
                        creating = true;
                        PolyglotLanguageContext.this.env = localEnv;
                        PolyglotLanguageContext.this.lazy = localLazy;
                        assert VMAccessor.LANGUAGE.getLanguage(env) != null;

                        try {
                            LANGUAGE.createEnvContext(localEnv);
                            lang.language.profile.notifyContextCreate(localEnv);
                            if (eventsEnabled) {
                                VMAccessor.INSTRUMENT.notifyLanguageContextCreated(context.engine, context.truffleContext, language.info);
                            }
                            lang = null; // commit language use
                        } catch (Throwable e) {
                            PolyglotLanguageContext.this.env = null;
                            PolyglotLanguageContext.this.lazy = null;
                            throw e;
                        } finally {
                            creating = false;
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
                            if (threadInfo.thread == Thread.currentThread()) {
                                continue;
                            }
                            LANGUAGE.initializeThread(env, threadInfo.thread);
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
            VMAccessor.INSTRUMENT.notifyLanguageContextInitialized(context.engine, context.truffleContext, language.info);
        }
        return wasInitialized;
    }

    void checkAccess(PolyglotLanguage accessingLanguage) {
        context.engine.checkState();
        if (context.closed) {
            throw new PolyglotIllegalStateException("The Context is already closed.");
        }
        boolean accessPermitted = language.isHost() || language.cache.isInternal() || context.config.allowedPublicLanguages.contains(language.info.getId()) ||
                        (accessingLanguage != null && accessingLanguage.dependsOn(language));

        if (!accessPermitted) {
            throw new PolyglotIllegalArgumentException(String.format("Access to language '%s' is not permitted. ", language.getId()));
        }
        RuntimeException initError = language.initError;
        if (initError != null) {
            throw new PolyglotIllegalStateException(String.format("Initialization error: %s", initError.getMessage()), initError);
        }
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return context.getEngine();
    }

    void preInitialize() {
        ensureInitialized(null);
        LOG.log(Level.FINE, "Pre-initialized context for language: {0}", language.getId());
    }

    boolean patch(PolyglotContextConfig newConfig) {
        if (isInitialized()) {
            try {
                final Env newEnv = LANGUAGE.patchEnvContext(env, newConfig.out, newConfig.err, newConfig.in,
                                Collections.emptyMap(), newConfig.getOptionValues(language), newConfig.getApplicationArguments(language),
                                newConfig.fileSystem);
                if (newEnv != null) {
                    env = newEnv;
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
                throw PolyglotImpl.wrapGuestException(this, t);
            }
        } else {
            return true;
        }
    }

    static final class ToGuestValuesNode {

        @CompilationFinal(dimensions = 1) private volatile ToGuestValueNode[] toGuestValue;
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
                    nodes[i] = createToGuestValue();
                }
                toGuestValue = nodes;
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
                        nodes[0] = createToGuestValue();
                    }
                    this.toGuestValue = nodes;
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
                Object newArg = nodes[i].apply(languageContext, arg);
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
                Object newArg = node.apply(languageContext, arg);
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
            throw new AssertionError("no instances");
        }
    }

    static final class ToGuestValueNode {

        @CompilationFinal private volatile Class<?> cachedClass;

        private ToGuestValueNode() {

        }

        public Object apply(PolyglotLanguageContext languageContext, Object receiver) {
            Class<?> cachedClassLocal = this.cachedClass;
            if (cachedClassLocal == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (receiver == null) {
                    // directly go to slow path for null
                    cachedClass = cachedClassLocal = Generic.class;
                } else {
                    cachedClass = cachedClassLocal = receiver.getClass();
                }
            }
            if (cachedClassLocal != Generic.class) {
                assert cachedClassLocal != null;
                if (receiver != null && cachedClassLocal == receiver.getClass()) {
                    return languageContext.toGuestValue(cachedClassLocal.cast(receiver));
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = cachedClassLocal = Generic.class; // switch to generic
                }
            }
            return slowPath(languageContext, receiver);
        }

        @TruffleBoundary
        private static Object slowPath(PolyglotLanguageContext languageContext, Object receiver) {
            return languageContext.toGuestValue(receiver);
        }

        public static ToGuestValueNode create() {
            return new ToGuestValueNode();
        }
    }

    static ToGuestValueNode createToGuestValue() {
        return new ToGuestValueNode();
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
            cache = lookupValueCache(guestValue);
        }
        return getAPIAccess().newValue(receiver, cache);
    }

    synchronized PolyglotValue lookupValueCache(Object guestValue) {
        assert toGuestValue(guestValue) == guestValue : "Not a valid guest value: " + guestValue + ". Only interop values are allowed to be exported.";
        PolyglotValue cache = lazy.valueCache.computeIfAbsent(guestValue.getClass(), new Function<Class<?>, PolyglotValue>() {
            public PolyglotValue apply(Class<?> t) {
                return PolyglotValue.createInteropValueCache(PolyglotLanguageContext.this, (TruffleObject) guestValue, guestValue.getClass());
            }
        });
        return cache;
    }

    final class ToHostValueNode {

        final APIAccess apiAccess = context.engine.impl.getAPIAccess();
        @CompilationFinal volatile Class<?> cachedClass;
        @CompilationFinal volatile PolyglotValue cachedValue;

        private ToHostValueNode() {
        }

        Value execute(Object value) {
            Object receiver = value;
            Class<?> cachedClassLocal = cachedClass;
            PolyglotValue cache;
            if (cachedClassLocal != Generic.class) {
                if (cachedClassLocal == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = receiver.getClass();
                    cache = lazy.valueCache.get(receiver.getClass());
                    if (cache == null) {
                        cache = lookupValueCache(receiver);
                    }
                    cachedValue = cache;
                    return apiAccess.newValue(receiver, cache);
                } else if (value.getClass() == cachedClassLocal) {
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
                    cachedValue = null;
                    // fall through to generic
                }
            }
            return asValue(value);
        }
    }

    ToHostValueNode createToHostValue() {
        return new ToHostValueNode();
    }

    Object toGuestValue(Class<?> receiver) {
        return HostObject.forClass(receiver, this);
    }

    Object toGuestValue(Object receiver) {
        if (receiver instanceof Value) {
            Value receiverValue = (Value) receiver;
            PolyglotValue valueImpl = (PolyglotValue) getAPIAccess().getImpl(receiverValue);
            if (valueImpl.languageContext.context != context) {
                CompilerDirectives.transferToInterpreter();
                throw PolyglotImpl.engineError(new IllegalArgumentException(String.format("Values cannot be passed from one context to another. " +
                                "The current value originates from context 0x%s and the argument originates from context 0x%s.",
                                Integer.toHexString(context.hashCode()), Integer.toHexString(valueImpl.languageContext.context.hashCode()))));
            }
            return getAPIAccess().getReceiver(receiverValue);
        } else if (PolyglotImpl.isGuestPrimitive(receiver)) {
            return receiver;
        } else if (receiver instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(this, (Proxy) receiver);
        } else if (receiver instanceof TruffleObject) {
            return receiver;
        } else if (receiver instanceof Class) {
            return HostObject.forClass((Class<?>) receiver, this);
        } else if (receiver == null) {
            return HostObject.NULL;
        } else if (receiver.getClass().isArray()) {
            return HostObject.forObject(receiver, this);
        } else if (receiver instanceof PolyglotList) {
            return ((PolyglotList<?>) receiver).guestObject;
        } else if (receiver instanceof PolyglotMap) {
            return ((PolyglotMap<?, ?>) receiver).guestObject;
        } else if (receiver instanceof PolyglotFunction) {
            return ((PolyglotFunction<?, ?>) receiver).guestObject;
        } else if (TruffleOptions.AOT) {
            return HostObject.forObject(receiver, this);
        } else {
            return HostInteropReflect.asTruffleViaReflection(receiver, this);
        }

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
                    // Still show the original error if printing on Env.err() fails for some reason
                    e.printStackTrace();
                }
            } else {
                e.printStackTrace();
            }
        }
    }

}
