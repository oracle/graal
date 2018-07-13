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

import static com.oracle.truffle.api.vm.VMAccessor.JAVAINTEROP;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.Source;
import java.util.logging.Level;

@SuppressWarnings("deprecation")
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
            this.sourceCache = languageInstance.getSourceCache(context.config);
            this.activePolyglotThreads = new HashSet<>();
            this.defaultValueCache = new PolyglotValue.Default(PolyglotLanguageContext.this);
            this.polyglotGuestBindings = new PolyglotBindings(PolyglotLanguageContext.this, context.polyglotBindings);
            this.uncaughtExceptionHandler = new PolyglotUncaughtExceptionHandler();
        }
    }

    final PolyglotContextImpl context;
    final PolyglotLanguage language;
    final boolean eventsEnabled;

    volatile boolean creating;
    volatile boolean initialized;
    volatile boolean finalized;
    @CompilationFinal private volatile Value hostBindings;
    @CompilationFinal private volatile Lazy lazy;

    @CompilationFinal volatile Env env; // effectively final

    final Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new PolyglotUncaughtExceptionHandler();

    PolyglotLanguageContext(PolyglotContextImpl context, PolyglotLanguage language) {
        this.context = context;
        this.language = language;
        this.eventsEnabled = !language.isHost();
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
                        this.hostBindings = this.toHostValue(new PolyglotLanguageBindings(scopes));
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
            PolyglotLanguageInstance lang = language.allocateInstance();
            try {
                synchronized (context) {
                    if (lazy == null) {
                        Lazy localLazy = new Lazy(lang);
                        PolyglotValue.createDefaultValueCaches(PolyglotLanguageContext.this, localLazy.valueCache);
                        Map<String, Object> creatorConfig = context.creator == language ? context.creatorArguments : Collections.emptyMap();
                        PolyglotContextConfig envConfig = context.config;
                        Env localEnv = LANGUAGE.createEnv(PolyglotLanguageContext.this, lang.spi,
                                        envConfig.out,
                                        envConfig.err,
                                        envConfig.in,
                                        creatorConfig,
                                        envConfig.getOptionValues(language),
                                        envConfig.getApplicationArguments(language),
                                        envConfig.fileSystem);
                        checkThreadAccess(localEnv);

                        // no more errors after this line
                        creating = true;
                        PolyglotLanguageContext.this.env = localEnv;
                        PolyglotLanguageContext.this.lazy = localLazy;

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
                // free uncommited language instance
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
            throw new PolyglotIllegalStateException(String.format("Access to language '%s' is not permitted. ", language.getId()));
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

    @TruffleBoundary
    static Object[] toGuestValues(Object languageContext, Object[] args) {
        Object[] newArgs = args;
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            Object newArg = toGuestValue(languageContext, arg);
            if (newArg != arg) {
                if (newArgs == args) {
                    newArgs = Arrays.copyOf(args, args.length);
                }
                newArgs[i] = newArg;
            }
        }
        return newArgs;
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

    static final class ToGuestValuesNode implements BiFunction<Object, Object[], Object[]> {

        @CompilationFinal private int cachedLength = -1;
        @CompilationFinal(dimensions = 1) private ToGuestValueNode[] toGuestValue;
        @CompilationFinal private boolean needsCopy = false;

        private ToGuestValuesNode() {
        }

        @Override
        public Object[] apply(Object languageContext, Object[] args) {
            if (cachedLength == -1) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedLength = args.length;
                toGuestValue = new ToGuestValueNode[cachedLength];
                for (int i = 0; i < cachedLength; i++) {
                    toGuestValue[i] = createToGuestValue();
                }
            }
            if (args.length == 0) {
                return args;
            } else if (cachedLength == args.length) {
                // fast path
                Object[] newArgs = fastToGuestValuesUnroll(languageContext, args);
                return newArgs;
            } else {
                if (cachedLength != -2) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedLength = -2;
                    toGuestValue = Arrays.copyOf(toGuestValue, 1);
                    if (toGuestValue[0] == null) {
                        toGuestValue[0] = createToGuestValue();
                    }
                }
                return fastToGuestValues(languageContext, args);
            }
        }

        /*
         * Specialization for constant number of arguments. Uses a profile for each argument.
         */
        @ExplodeLoop
        private Object[] fastToGuestValuesUnroll(Object languageContext, Object[] args) {
            Object[] newArgs = needsCopy ? new Object[toGuestValue.length] : args;
            for (int i = 0; i < toGuestValue.length; i++) {
                Object arg = args[i];
                Object newArg = toGuestValue[i].apply(languageContext, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = Arrays.copyOf(args, args.length);
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
        private Object[] fastToGuestValues(Object languageContext, Object[] args) {
            assert toGuestValue[0] != null;
            Object[] newArgs = needsCopy ? new Object[args.length] : args;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                Object newArg = toGuestValue[0].apply(languageContext, arg);
                if (needsCopy) {
                    newArgs[i] = newArg;
                } else if (arg != newArg) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    newArgs = Arrays.copyOf(args, args.length);
                    newArgs[i] = newArg;
                    needsCopy = true;
                }
            }
            return newArgs;
        }

        static ToGuestValuesNode create() {
            return new ToGuestValuesNode();
        }

    }

    static final class Generic {
        private Generic() {
            throw new AssertionError("no instances");
        }
    }

    static final class ToGuestValueNode implements BiFunction<Object, Object, Object> {

        @CompilationFinal private Class<?> cachedClass;

        private ToGuestValueNode() {

        }

        @Override
        public Object apply(Object languageContext, Object receiver) {
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
                if (cachedClassLocal.isInstance(receiver)) {
                    return toGuestValue(languageContext, cachedClassLocal.cast(receiver));
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = cachedClassLocal = Generic.class; // switch to generic
                }
            }
            return slowPath(languageContext, receiver);
        }

        @TruffleBoundary
        private static Object slowPath(Object languageContext, Object receiver) {
            return toGuestValue(languageContext, receiver);
        }

        static ToGuestValueNode create() {
            return new ToGuestValueNode();
        }

    }

    static Object toGuestValue(Object originalLanguageContext, Object receiver) {
        PolyglotLanguageContext languageContext = ((PolyglotLanguageContext) originalLanguageContext);
        if (receiver instanceof Value) {
            Value receiverValue = (Value) receiver;
            PolyglotValue valueImpl = (PolyglotValue) languageContext.getAPIAccess().getImpl(receiverValue);
            if (valueImpl.languageContext.context != languageContext.context) {
                CompilerDirectives.transferToInterpreter();
                throw PolyglotImpl.engineError(new IllegalArgumentException(String.format("Values cannot be passed from one context to another. " +
                                "The current value originates from context 0x%s and the argument originates from context 0x%s.",
                                Integer.toHexString(languageContext.context.hashCode()), Integer.toHexString(valueImpl.languageContext.context.hashCode()))));
            }
            return languageContext.getAPIAccess().getReceiver(receiverValue);
        } else if (PolyglotImpl.isGuestPrimitive(receiver)) {
            return receiver;
        } else if (receiver instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(languageContext, (Proxy) receiver);
        } else {
            return JAVAINTEROP.toGuestObject(receiver, languageContext);
        }
    }

    static ToGuestValueNode createToGuestValue() {
        return new ToGuestValueNode();
    }

    PolyglotSourceCache getSourceCache() {
        assert isInitialized();
        return lazy.sourceCache;
    }

    @TruffleBoundary
    Value toHostValue(Object value) {
        assert lazy != null;
        assert value != null;
        assert !(value instanceof Value);
        Object receiver = value;
        PolyglotValue cache = lazy.valueCache.get(receiver.getClass());
        if (cache == null) {
            receiver = convertToInterop(receiver);
            cache = lookupValueCache(receiver);
        }
        return getAPIAccess().newValue(receiver, cache);
    }

    TruffleObject convertToInterop(Object receiver) {
        if (receiver instanceof Proxy) {
            return PolyglotProxy.toProxyGuestObject(PolyglotLanguageContext.this, (Proxy) receiver);
        } else {
            return (TruffleObject) JAVAINTEROP.toGuestObject(receiver, PolyglotLanguageContext.this);
        }
    }

    synchronized PolyglotValue lookupValueCache(Object value) {
        assert value instanceof TruffleObject;
        PolyglotValue cache = lazy.valueCache.get(value.getClass());
        if (cache == null) {
            cache = PolyglotValue.createInteropValueCache(PolyglotLanguageContext.this, (TruffleObject) value, value.getClass());
            lazy.valueCache.put(value.getClass(), cache);
        }
        return cache;
    }

    final class ToHostValueNode {

        final APIAccess apiAccess = context.engine.impl.getAPIAccess();
        @CompilationFinal Class<?> cachedClass;
        @CompilationFinal PolyglotValue cachedValue;
        @CompilationFinal Class<?> cachedFallbackClass;
        @CompilationFinal PolyglotValue cachedFallbackValue;

        private ToHostValueNode() {
        }

        Value execute(Object value) {
            Object receiver = value;
            Class<?> cachedClassLocal = cachedClass;
            if (cachedClassLocal == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cachedClass = receiver.getClass();
                cachedValue = lazy.valueCache.get(cachedClass);
                PolyglotValue cache = cachedValue;
                if (cachedValue == null) {
                    receiver = convertToInterop(receiver);
                    cachedFallbackClass = receiver.getClass();
                    cachedFallbackValue = lookupValueCache(receiver);
                    cache = cachedFallbackValue;
                }
                return apiAccess.newValue(receiver, cache);
            } else if (cachedClassLocal != Generic.class) {
                if (cachedClassLocal.isInstance(value)) {
                    receiver = cachedClassLocal.cast(receiver);
                    PolyglotValue cache = cachedValue;
                    if (cache == null) {
                        receiver = convertToInterop(receiver);
                        if (cachedFallbackClass.isInstance(receiver)) {
                            cache = cachedFallbackValue;
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            cachedClass = Generic.class;
                            cache = lookupValueCache(receiver.getClass());
                        }
                    }
                    return apiAccess.newValue(receiver, cache);
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    cachedClass = Generic.class; // switch to generic
                    // fall through to generic
                }
            }
            return toHostValue(value);
        }
    }

    ToHostValueNode createToHostValue() {
        return new ToHostValueNode();
    }

    Object toGuestValue(Object receiver) {
        return toGuestValue(this, receiver);
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values, int startIndex) {
        Value[] args = new Value[values.length - startIndex];
        for (int i = startIndex; i < values.length; i++) {
            args[i - startIndex] = toHostValue(values[i]);
        }
        return args;
    }

    @TruffleBoundary
    Value[] toHostValues(Object[] values) {
        Value[] args = new Value[values.length];
        for (int i = 0; i < args.length; i++) {
            args[i] = toHostValue(values[i]);
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
