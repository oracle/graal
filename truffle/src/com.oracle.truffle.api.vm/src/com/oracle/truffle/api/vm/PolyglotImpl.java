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

import static com.oracle.truffle.api.vm.VMAccessor.INSTRUMENT;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;
import static com.oracle.truffle.api.vm.VMAccessor.NODES;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.RootNode;

/*
 * This class is exported to the Graal SDK. Keep that in mind when changing its class or package name.
 */
/**
 * Internal service implementation of the polyglot API.
 *
 * @since 0.27
 */
public final class PolyglotImpl extends AbstractPolyglotImpl {

    static final Object[] EMPTY_ARGS = new Object[0];

    static final String OPTION_GROUP_COMPILER = "compiler";
    static final String OPTION_GROUP_ENGINE = "engine";

    private static final PolyglotContextProfile CURRENT_CONTEXT = new PolyglotContextProfile();

    private final PolyglotSourceImpl sourceImpl = new PolyglotSourceImpl(this);
    private final PolyglotSourceSectionImpl sourceSectionImpl = new PolyglotSourceSectionImpl(this);

    private static void ensureInitialized() {
        if (VMAccessor.SPI == null || !(VMAccessor.SPI.engineSupport() instanceof EngineImpl)) {
            VMAccessor.initialize(new EngineImpl());
        }
    }

    /**
     * Internal method do not use.
     *
     * @since 0.27
     */
    public PolyglotImpl() {
        ensureInitialized();
    }

    /**
     * Internal method do not use.
     *
     * @since 0.27
     */
    @Override
    public AbstractSourceImpl getSourceImpl() {
        return sourceImpl;
    }

    /**
     * Internal method do not use.
     *
     * @since 0.27
     */
    @Override
    public AbstractSourceSectionImpl getSourceSectionImpl() {
        return sourceSectionImpl;
    }

    /**
     * Internal method do not use.
     *
     * @since 0.27
     */
    @Override
    public Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties) {
        ensureInitialized();
        OutputStream resolvedOut = out == null ? System.out : out;
        OutputStream resolvedErr = err == null ? System.err : err;
        InputStream resolvedIn = in == null ? System.in : in;

        DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
        DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);
        ClassLoader contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();
        PolyglotEngineImpl impl = new PolyglotEngineImpl(this, dispatchOut, dispatchErr, resolvedIn, arguments, timeout, timeoutUnit, sandbox, useSystemProperties,
                        contextClassLoader);
        Engine engine = getAPIAccess().newEngine(impl);
        impl.api = engine;
        return engine;
    }

    /**
     * Internal method do not use.
     *
     * @since 0.27
     */
    @Override
    public Class<?> loadLanguageClass(String className) {
        for (ClassLoader loader : TruffleLocator.loaders()) {
            try {
                return loader.loadClass(className);
            } catch (ClassNotFoundException e) {
            }
        }
        return null;
    }

    static PolyglotContextImpl requireContext() {
        PolyglotContextImpl context = CURRENT_CONTEXT.get();
        if (context == null) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("No current context found.");
        }
        return context;
    }

    static PolyglotContextImpl currentContext() {
        return CURRENT_CONTEXT.get();
    }

    static Object enterGuest(PolyglotLanguageContextImpl languageContext) {
        checkStateForGuest(languageContext);
        PolyglotContextImpl prevEngine = CURRENT_CONTEXT.get();
        CURRENT_CONTEXT.enter(languageContext.context);
        return prevEngine;
    }

    static void checkEngine(PolyglotEngineImpl engine) {
        if (engine.closed) {
            throw new IllegalStateException("Engine already closed.");
        }
    }

    static void checkStateForGuest(PolyglotLanguageContextImpl languageContext) {
        checkStateForGuest(languageContext.context);
    }

    static void checkStateForGuest(PolyglotContextImpl context) {
        checkEngine(context.engine);
        Thread boundThread = context.boundThread;
        if (boundThread == null) {
            context.boundThread = Thread.currentThread();
        } else if (boundThread != Thread.currentThread()) {
            throw engineError(new IllegalStateException(
                            String.format("The context was accessed from thread %s but is bound to thread %s. " +
                                            "The context is not thread-safe and can therefore not be accessed from multiple threads. ",
                                            boundThread, Thread.currentThread())));
        }
    }

    static RuntimeException engineError(RuntimeException e) {
        throw new EngineException(e);
    }

    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapHostException(T e) {
        if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
        } else if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else if (e instanceof EngineException) {
            return ((EngineException) e).e;
        } else if (e instanceof HostException) {
            return (HostException) e;
        }
        return new HostException(e);
    }

    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapGuestException(PolyglotLanguageContextImpl context, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else if (e instanceof EngineException) {
            throw ((EngineException) e).e;
        } else {
            // fallthrough
        }

        APIAccess access = context.getEngine().impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(context, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    static void leaveGuest(Object prevContext) {
        CURRENT_CONTEXT.leave((PolyglotContextImpl) prevContext);
    }

    static boolean isGuestInteropValue(Object receiver) {
        return isGuestPrimitive(receiver) || receiver instanceof TruffleObject;
    }

    static boolean isGuestPrimitive(Object receiver) {
        return receiver instanceof Integer || receiver instanceof Double //
                        || receiver instanceof Long || receiver instanceof Float //
                        || receiver instanceof Boolean || receiver instanceof Character //
                        || receiver instanceof Byte || receiver instanceof Short //
                        || receiver instanceof String;
    }

    interface VMObject {

        PolyglotEngineImpl getEngine();

        default PolyglotImpl getImpl() {
            return getEngine().impl;
        }

        default APIAccess getAPIAccess() {
            return getEngine().impl.getAPIAccess();
        }

    }

    @SuppressWarnings("serial")
    private static class EngineException extends RuntimeException {

        final RuntimeException e;

        EngineException(RuntimeException e) {
            this.e = e;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

    }

    static final class EngineImpl extends EngineSupport {

        @Override
        public boolean isDisposed(Object vmObject) {
            return getEngine(vmObject).closed;
        }

        @Override
        public Object contextReferenceGet(Object vmObject) {
            PolyglotLanguageImpl language = (PolyglotLanguageImpl) vmObject;
            PolyglotContextImpl context = currentContext();
            TruffleLanguage.Env env = null;
            if (context != null) {
                env = context.contexts[language.index].env;
            }
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException(
                                "The language context is not yet initialized or already disposed. ");
            }
            return LANGUAGE.getContext(env);
        }

        @Override
        public OptionValues getCompilerOptionValues(RootNode rootNode) {
            Object vm = NODES.getSourceVM(rootNode);
            if (vm instanceof PolyglotEngineImpl) {
                return ((PolyglotEngineImpl) vm).compilerOptionValues;
            }
            return null;
        }

        @Override
        public Object getVMFromLanguageObject(Object engineObject) {
            return getEngine(engineObject);
        }

        @Override
        public Env getEnvForLanguage(Object vmObject, String mimeType) {
            PolyglotLanguageContextImpl languageContext = (PolyglotLanguageContextImpl) vmObject;
            PolyglotLanguageContextImpl context = languageContext.context.findLanguageContext(mimeType, true);
            context.ensureInitialized();
            return context.env;
        }

        @Override
        public Env getEnvForInstrument(Object vmObject, String mimeType) {
            PolyglotLanguageContextImpl context = requireContext().findLanguageContext(mimeType, true);
            context.ensureInitialized();
            return context.env;
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrumentImpl instrument = (PolyglotInstrumentImpl) LANGUAGE.getVMObject(info);
            return instrument.lookup(serviceClass);
        }

        @Override
        public <S> S lookup(LanguageInfo info, Class<S> serviceClass) {
            PolyglotLanguageImpl language = (PolyglotLanguageImpl) NODES.getEngineObject(info);
            return language.lookup(serviceClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotContextImpl context = currentContext();
            if (context == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("No current context available.");
            }
            return (C) LANGUAGE.getContext(context.getLanguageContext(languageClass).env);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotContextImpl context = currentContext();
            if (context == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("No current language available.");
            }
            return (T) LANGUAGE.getSPI(context.getLanguageContext(languageClass).env);
        }

        @Override
        public Map<String, LanguageInfo> getLanguages(Object vmObject) {
            return getEngine(vmObject).idToLanguageInfo;
        }

        @Override
        public Map<String, InstrumentInfo> getInstruments(Object vmObject) {
            return getEngine(vmObject).idToInstrumentInfo;
        }

        private static PolyglotEngineImpl getEngine(Object vmObject) throws AssertionError {
            if (!(vmObject instanceof VMObject)) {
                throw new AssertionError();
            }
            return ((VMObject) vmObject).getEngine();
        }

        @Override
        public Env getEnvForInstrument(LanguageInfo info) {
            PolyglotLanguageImpl language = (PolyglotLanguageImpl) NODES.getEngineObject(info);
            PolyglotLanguageContextImpl languageContext = requireContext().contexts[language.index];
            languageContext.ensureInitialized();
            return languageContext.env;
        }

        @Override
        public Object getCurrentVM() {
            PolyglotContextImpl current = CURRENT_CONTEXT.get();
            return current != null ? current.getEngine() : null;
        }

        @Override
        public boolean isEvalRoot(RootNode target) {
            // TODO no eval root nodes anymore on the stack for the polyglot api
            return false;
        }

        @Override
        public boolean isMimeTypeSupported(Object vmObject, String mimeType) {
            PolyglotEngineImpl engine = getEngine(vmObject);
            for (PolyglotLanguageImpl language : engine.idToLanguage.values()) {
                if (language.cache.getMimeTypes().contains(mimeType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Env findEnv(Object vmObject, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound) {
            PolyglotLanguageContextImpl findLanguageContext = requireContext().findLanguageContext(languageClass, failIfNotFound);
            if (findLanguageContext != null) {
                return findLanguageContext.env;
            }
            return null;
        }

        @Override
        public Object getInstrumentationHandler(Object vmObject) {
            return getEngine(vmObject).instrumentationHandler;
        }

        @Override
        public Iterable<? extends Object> importSymbols(Object vmObject, Env env, String globalName) {
            PolyglotLanguageContextImpl context = (PolyglotLanguageContextImpl) vmObject;
            checkEngine(context.language.engine);
            Object result = context.context.importSymbolFromLanguage(globalName);
            List<Object> resultValues;
            if (result == null) {
                resultValues = Collections.emptyList();
            } else {
                resultValues = Arrays.asList(result);
            }
            return resultValues;
        }

        @Override
        public Object importSymbol(Object vmObject, Env env, String symbolName) {
            PolyglotLanguageContextImpl context = (PolyglotLanguageContextImpl) vmObject;
            checkEngine(context.language.engine);
            return context.context.importSymbolFromLanguage(symbolName);
        }

        @Override
        public void exportSymbol(Object vmObject, String symbolName, Object value) {
            PolyglotLanguageContextImpl context = (PolyglotLanguageContextImpl) vmObject;
            checkEngine(context.language.engine);
            context.context.exportSymbolFromLanguage(context, symbolName, value);
        }

        @SuppressWarnings("deprecation")
        @Override
        public <C> com.oracle.truffle.api.impl.FindContextNode<C> createFindContextNode(TruffleLanguage<C> lang) {
            PolyglotContextImpl context = requireContext();
            return new PolyglotFindContextNodeImpl<>(context.findLanguageContext(lang.getClass(), true).env);
        }

        @Override
        public void registerDebugger(Object vm, Object debugger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object findOriginalObject(Object truffleObject) {
            if (truffleObject instanceof EngineTruffleObject) {
                return ((EngineTruffleObject) truffleObject).getDelegate();
            }
            return truffleObject;
        }

        private static boolean assertKeyType(Object key) {
            assert key instanceof Class || key instanceof Method || key instanceof Message : "Unexpected key: " + key;
            return true;
        }

        @Override
        public CallTarget lookupOrRegisterComputation(Object truffleObject, RootNode computation, Object... keys) {
            CompilerAsserts.neverPartOfCompilation();
            assert keys.length > 0;
            Object key;
            if (keys.length == 1) {
                key = keys[0];
                assert TruffleOptions.AOT || assertKeyType(key);
            } else {
                Pair p = null;
                for (Object k : keys) {
                    assert TruffleOptions.AOT || assertKeyType(k);
                    p = new Pair(k, p);
                }
                key = p;
            }
            PolyglotContextImpl context = currentContext();
            if (context == null) {
                throw new IllegalStateException("No valid context found. Cannot use Java interop.");
            }
            CallTarget cachedTarget = context.javaInteropCache.get(key);
            if (cachedTarget == null && computation != null) {
                cachedTarget = Truffle.getRuntime().createCallTarget(computation);
                context.javaInteropCache.put(key, cachedTarget);
            }
            return cachedTarget;
        }

        private static final class Pair {
            final Object key;
            final Pair next;

            Pair(Object key, Pair next) {
                this.key = key;
                this.next = next;
            }

            @Override
            public int hashCode() {
                return this.key.hashCode() + (next == null ? 3754 : next.hashCode());
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (obj == null) {
                    return false;
                }
                if (getClass() != obj.getClass()) {
                    return false;
                }
                final Pair other = (Pair) obj;
                if (!Objects.equals(this.key, other.key)) {
                    return false;
                }
                if (!Objects.equals(this.next, other.next)) {
                    return false;
                }
                return true;
            }

        }

        @Override
        public Value toHostValue(Object obj, Object languageContext) {
            return ((PolyglotLanguageContextImpl) languageContext).toHostValue(obj);
        }

        @Override
        public Object toGuestValue(Object obj, Object languageContext) {
            return ((PolyglotLanguageContextImpl) languageContext).toGuestValue(obj);
        }
    }

}
