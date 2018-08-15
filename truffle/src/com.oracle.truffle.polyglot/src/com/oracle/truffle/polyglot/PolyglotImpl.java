/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.VMAccessor.INSTRUMENT;
import static com.oracle.truffle.polyglot.VMAccessor.LANGUAGE;
import static com.oracle.truffle.polyglot.VMAccessor.NODES;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.HostLanguage.HostContext;
import java.nio.file.Path;
import com.oracle.truffle.api.impl.HomeFinder;

/*
 * This class is exported to the Graal SDK. Keep that in mind when changing its class or package name.
 */
/**
 * Internal service implementation of the polyglot API.
 */
public final class PolyglotImpl extends AbstractPolyglotImpl {

    static final Object[] EMPTY_ARGS = new Object[0];

    static final String OPTION_GROUP_COMPILER = "compiler";
    static final String OPTION_GROUP_ENGINE = "engine";

    private final PolyglotSource sourceImpl = new PolyglotSource(this);
    private final PolyglotSourceSection sourceSectionImpl = new PolyglotSourceSection(this);
    private final PolyglotExecutionListener executionListenerImpl = new PolyglotExecutionListener(this);
    private final AtomicReference<PolyglotEngineImpl> preInitializedEngineRef = new AtomicReference<>();

    /**
     * Internal method do not use.
     */
    public PolyglotImpl() {
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractSourceImpl getSourceImpl() {
        return sourceImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractSourceSectionImpl getSourceSectionImpl() {
        return sourceSectionImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public AbstractExecutionListenerImpl getExecutionListenerImpl() {
        return executionListenerImpl;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Context getCurrentContext() {
        PolyglotContextImpl context = PolyglotContextImpl.current();
        if (context == null) {
            return super.getCurrentContext();
        }
        return context.currentApi;
    }

    /**
     * Internal method do not use.
     */
    @Override
    public Engine buildEngine(OutputStream out, OutputStream err, InputStream in, Map<String, String> arguments, long timeout, TimeUnit timeoutUnit, boolean sandbox,
                    long maximumAllowedAllocationBytes, boolean useSystemProperties, boolean boundEngine, Handler logHandler) {
        if (TruffleOptions.AOT) {
            VMAccessor.SPI.initializeNativeImageTruffleLocator();
        }
        OutputStream resolvedOut = out == null ? System.out : out;
        OutputStream resolvedErr = err == null ? System.err : err;
        InputStream resolvedIn = in == null ? System.in : in;

        DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
        DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);
        ClassLoader contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();

        PolyglotEngineImpl impl = boundEngine ? preInitializedEngineRef.getAndSet(null) : null;
        if (impl != null) {
            if (!impl.patch(dispatchOut, dispatchErr, resolvedIn, arguments, useSystemProperties, contextClassLoader, boundEngine, logHandler)) {
                impl.ensureClosed(false, true);
                impl = null;
            }
        }
        if (impl == null) {
            impl = new PolyglotEngineImpl(this, dispatchOut, dispatchErr, resolvedIn, arguments, useSystemProperties, contextClassLoader, boundEngine, logHandler);
        }
        Engine engine = getAPIAccess().newEngine(impl);
        impl.creatorApi = engine;
        impl.currentApi = getAPIAccess().newEngine(impl);
        return engine;
    }

    /**
     * Pre-initializes a polyglot engine instance.
     */
    @Override
    public void preInitializeEngine() {
        final Handler logHandler = PolyglotLogHandler.createStreamHandler(System.out, false, true);
        try {
            final PolyglotEngineImpl preInitializedEngine = PolyglotEngineImpl.preInitialize(
                            this,
                            INSTRUMENT.createDispatchOutput(System.out),
                            INSTRUMENT.createDispatchOutput(System.err),
                            System.in,
                            TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader(),
                            logHandler);
            preInitializedEngineRef.set(preInitializedEngine);
        } finally {
            logHandler.flush();
        }
    }

    /**
     * Cleans the pre-initialized polyglot engine instance.
     */
    @Override
    public void resetPreInitializedEngine() {
        preInitializedEngineRef.set(null);
        PolyglotEngineImpl.resetPreInitializedEngine();
    }

    /**
     * Internal method do not use.
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

    @Override
    public Path findHome() {
        final HomeFinder homeFinder = HomeFinder.getInstance();
        return homeFinder == null ? null : homeFinder.getHomeFolder();
    }

    org.graalvm.polyglot.Source getPolyglotSource(Source source) {
        org.graalvm.polyglot.Source polyglotSource = VMAccessor.SOURCE.getPolyglotSource(source);
        if (polyglotSource == null) {
            polyglotSource = getAPIAccess().newSource(source.getLanguage(), source);
            VMAccessor.SOURCE.setPolyglotSource(source, polyglotSource);
        }
        return polyglotSource;
    }

    org.graalvm.polyglot.SourceSection getPolyglotSourceSection(SourceSection sourceSection) {
        if (sourceSection == null) {
            return null;
        }
        org.graalvm.polyglot.Source polyglotSource = getPolyglotSource(sourceSection.getSource());
        return getAPIAccess().newSourceSection(polyglotSource, sourceSection);
    }

    static RuntimeException engineError(RuntimeException e) {
        throw new EngineException(e);
    }

    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapHostException(PolyglotLanguageContext languageContext, T e) {
        throw wrapHostException(languageContext.context, e);
    }

    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapHostException(PolyglotContextImpl context, T e) {
        if (e instanceof ThreadDeath) {
            throw (ThreadDeath) e;
        } else if (e instanceof PolyglotException) {
            PolyglotException polyglot = (PolyglotException) e;
            if (context != null) {
                PolyglotExceptionImpl exceptionImpl = ((PolyglotExceptionImpl) context.getImpl().getAPIAccess().getImpl(polyglot));
                if (exceptionImpl.context == context || exceptionImpl.context == null || exceptionImpl.isHostException()) {
                    // for values of the same context the TruffleException is allowed to be unboxed
                    // for host exceptions no guest values are bound therefore it can also be
                    // unboxed
                    Throwable original = ((PolyglotExceptionImpl) context.getImpl().getAPIAccess().getImpl(polyglot)).exception;
                    if (original instanceof RuntimeException) {
                        throw (RuntimeException) original;
                    } else if (original instanceof Error) {
                        throw (Error) original;
                    }
                }
                // fall-through and treat it as any other host exception
            }
        } else if (e instanceof EngineException) {
            return ((EngineException) e).e;
        } else if (e instanceof HostException) {
            return (HostException) e;
        } else if (e instanceof InteropException) {
            throw ((InteropException) e).raise();
        }
        return new HostException(e);
    }

    @TruffleBoundary
    // Wrapping language exception
    static <T extends Throwable> PolyglotException wrapGuestException(PolyglotLanguageContext context, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else {
            doRethrowPolyglotVariants(e);
        }

        APIAccess access = context.getEngine().impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(context, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    @TruffleBoundary
    // Wrapping instrument exception
    static <T extends Throwable> PolyglotException wrapGuestException(PolyglotEngineImpl engine, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else {
            doRethrowPolyglotVariants(e);
        }

        APIAccess access = engine.impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(engine, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
    }

    private static void doRethrowPolyglotVariants(Throwable e) {
        if (e instanceof EngineException) {
            throw ((EngineException) e).e;
        } else if (e instanceof PolyglotUnsupportedException) {
            throw (PolyglotUnsupportedException) e;
        } else if (e instanceof PolyglotClassCastException) {
            throw (PolyglotClassCastException) e;
        } else if (e instanceof PolyglotIllegalStateException) {
            throw (PolyglotIllegalStateException) e;
        } else if (e instanceof PolyglotNullPointerException) {
            throw (PolyglotNullPointerException) e;
        } else if (e instanceof PolyglotIllegalArgumentException) {
            throw (PolyglotIllegalArgumentException) e;
        } else if (e instanceof PolyglotArrayIndexOutOfBoundsException) {
            throw (PolyglotArrayIndexOutOfBoundsException) e;
        }
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
        public Object getCurrentContext(Object vmObject) {
            return ((PolyglotLanguage) vmObject).profile.get();
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
        public CallTarget parseForLanguage(Object vmObject, Source source, String[] argumentNames) {
            PolyglotLanguageContext sourceContext = (PolyglotLanguageContext) vmObject;
            PolyglotLanguage targetLanguage = sourceContext.context.engine.findLanguage(source.getLanguage(), source.getMimeType(), true);
            PolyglotLanguageContext targetContext = sourceContext.context.getContextInitialized(targetLanguage, sourceContext.language);
            return targetContext.parseCached(sourceContext.language, source, argumentNames);
        }

        @Override
        public Env getEnvForInstrument(Object vmObject, String languageId, String mimeType) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            PolyglotLanguage foundLanguage = context.engine.findLanguage(languageId, mimeType, true);
            return context.getContextInitialized(foundLanguage, null).env;
        }

        @Override
        public org.graalvm.polyglot.SourceSection createSourceSection(Object vmObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            org.graalvm.polyglot.Source polyglotSource = source;
            if (polyglotSource == null) {
                com.oracle.truffle.api.source.Source sourceImpl = sectionImpl.getSource();
                polyglotSource = ((VMObject) vmObject).getAPIAccess().newSource(sourceImpl.getLanguage(), sourceImpl);
            }
            return ((VMObject) vmObject).getAPIAccess().newSourceSection(polyglotSource, sectionImpl);
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrument instrument = (PolyglotInstrument) LANGUAGE.getVMObject(info);
            return instrument.lookup(serviceClass, false);
        }

        @Override
        public <S> S lookup(LanguageInfo info, Class<S> serviceClass) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getEngineObject(info);
            PolyglotLanguageContext languageContext = PolyglotContextImpl.requireContext().getContextInitialized(language, language);
            return LANGUAGE.lookup(LANGUAGE.getLanguage(languageContext.env), serviceClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            Env env = context.getLanguageContext(languageClass).env;
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Current context is not yet initialized or already disposed.");
            }
            return (C) LANGUAGE.getContext(env);
        }

        @Override
        public TruffleContext getPolyglotContext(Object vmObject) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) vmObject;
            return languageContext.context.truffleContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            Env env = context.getLanguageContext(languageClass).env;
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Current context is not yet initialized or already disposed.");
            }
            return (T) VMAccessor.LANGUAGE.getLanguage(env);
        }

        @Override
        public Map<String, LanguageInfo> getLanguages(Object vmObject) {
            return getEngine(vmObject).idToInternalLanguageInfo;
        }

        @Override
        public Map<String, InstrumentInfo> getInstruments(Object vmObject) {
            return getEngine(vmObject).idToInternalInstrumentInfo;
        }

        private static PolyglotEngineImpl getEngine(Object vmObject) throws AssertionError {
            if (!(vmObject instanceof VMObject)) {
                throw new AssertionError();
            }
            return ((VMObject) vmObject).getEngine();
        }

        @Override
        public Env getEnvForInstrument(LanguageInfo info) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getEngineObject(info);
            return PolyglotContextImpl.requireContext().getContextInitialized(language, null).env;
        }

        @Override
        public Env getExistingEnvForInstrument(LanguageInfo info) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getEngineObject(info);
            PolyglotLanguageContext languageContext = PolyglotContextImpl.requireContext().contexts[language.index];
            return languageContext.isInitialized() ? languageContext.env : null;
        }

        static PolyglotLanguage findObjectLanguage(PolyglotContextImpl context, PolyglotLanguageContext currentlanguageContext, Object value) {
            PolyglotLanguage foundLanguage = null;
            final PolyglotLanguageContext hostLanguageContext = context.getHostContext();
            // The HostLanguage might not have context created even when JavaObjects exist
            // Check it separately:
            if (currentlanguageContext != null && isPrimitive(value)) {
                return currentlanguageContext.language;
            } else if (VMAccessor.LANGUAGE.isObjectOfLanguage(hostLanguageContext.env, value)) {
                foundLanguage = hostLanguageContext.language;
            } else if (currentlanguageContext != null && VMAccessor.LANGUAGE.isObjectOfLanguage(currentlanguageContext.env, value)) {
                foundLanguage = currentlanguageContext.language;
            } else {
                for (PolyglotLanguageContext searchContext : context.contexts) {
                    if (searchContext.isInitialized() && searchContext != currentlanguageContext) {
                        final Env searchEnv = searchContext.env;
                        if (VMAccessor.LANGUAGE.isObjectOfLanguage(searchEnv, value)) {
                            foundLanguage = searchContext.language;
                            break;
                        }
                    }
                }
            }
            return foundLanguage;
        }

        static boolean isPrimitive(final Object value) {
            final Class<?> valueClass = value.getClass();
            return valueClass == Boolean.class || valueClass == Byte.class || valueClass == Short.class || valueClass == Integer.class || valueClass == Long.class ||
                            valueClass == Float.class || valueClass == Double.class ||
                            valueClass == Character.class || valueClass == String.class;
        }

        @Override
        public LanguageInfo getObjectLanguage(Object obj, Object vmObject) {
            PolyglotLanguage language = findObjectLanguage(PolyglotContextImpl.requireContext(), null, obj);
            if (language != null) {
                return language.info;
            }
            return null;
        }

        @Override
        public Object getCurrentVM() {
            PolyglotContextImpl context = PolyglotContextImpl.current();
            if (context == null) {
                return null;
            }
            return context.engine;
        }

        @Override
        public boolean isEvalRoot(RootNode target) {
            // TODO no eval root nodes anymore on the stack for the polyglot api
            return false;
        }

        @Override
        public boolean isMimeTypeSupported(Object vmObject, String mimeType) {
            PolyglotEngineImpl engine = getEngine(vmObject);
            for (PolyglotLanguage language : engine.idToLanguage.values()) {
                if (language.cache.getMimeTypes().contains(mimeType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Env findEnv(Object vmObject, Class<? extends TruffleLanguage> languageClass, boolean failIfNotFound) {
            PolyglotLanguageContext findLanguageContext = PolyglotContextImpl.requireContext().findLanguageContext(languageClass, failIfNotFound);
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
        @TruffleBoundary
        public Object importSymbol(Object vmObject, Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            Value value = context.context.polyglotBindings.get(symbolName);
            if (value != null) {
                return context.getAPIAccess().getReceiver(value);
            } else {
                value = context.context.findLegacyExportedSymbol(symbolName);
                if (value != null) {
                    return context.getAPIAccess().getReceiver(value);
                }
            }
            return null;
        }

        @Override
        public Object lookupHostSymbol(Object vmObject, Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            HostContext hostContext = ((PolyglotLanguageContext) vmObject).context.getHostContextImpl();
            Class<?> clazz = hostContext.findClass(symbolName);
            if (clazz == null) {
                return null;
            }
            return HostObject.forStaticClass(clazz, context);
        }

        @Override
        public Object asHostSymbol(Object vmObject, Class<?> symbolClass) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            return HostObject.forStaticClass(symbolClass, context);
        }

        @Override
        public boolean isHostAccessAllowed(Object vmObject, Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            return context.context.config.hostAccessAllowed;
        }

        @Override
        public boolean isNativeAccessAllowed(Object vmObject, Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            return context.context.config.nativeAccessAllowed;
        }

        @Override
        @TruffleBoundary
        public void exportSymbol(Object vmObject, String symbolName, Object value) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            if (value == null) {
                context.context.polyglotBindings.remove(symbolName);
            } else {
                context.context.polyglotBindings.put(symbolName, context.asValue(value));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, ? extends Object> getExportedSymbols(Object vmObject) {
            PolyglotContextImpl currentContext = PolyglotContextImpl.current();
            return currentContext.polyglotHostBindings.as(Map.class);
        }

        @Override
        public void registerDebugger(Object vm, Object debugger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T installJavaInteropCodeCache(Object languageContext, Object key, T value, Class<T> expectedType) {
            if (languageContext == null) {
                return value;
            }
            T result = expectedType.cast(((PolyglotLanguageContext) languageContext).context.engine.javaInteropCodeCache.putIfAbsent(key, value));
            if (result != null) {
                return result;
            } else {
                return value;
            }
        }

        @Override
        public <T> T lookupJavaInteropCodeCache(Object languageContext, Object key, Class<T> expectedType) {
            if (languageContext == null) {
                return null;
            }

            return expectedType.cast(((PolyglotLanguageContext) languageContext).context.engine.javaInteropCodeCache.get(key));
        }

        @Override
        public Object toGuestValue(Object obj, Object context) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) context;
            if (obj instanceof Value) {
                PolyglotValue valueImpl = (PolyglotValue) languageContext.getImpl().getAPIAccess().getImpl((Value) obj);
                languageContext = valueImpl.languageContext;
            }
            return languageContext.toGuestValue(obj);
        }

        @Override
        public Object asBoxedGuestValue(Object guestObject, Object vmObject) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) vmObject;
            if (isGuestPrimitive(guestObject)) {
                return HostObject.forObject(guestObject, languageContext);
            } else if (guestObject instanceof TruffleObject) {
                return guestObject;
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException("Provided value not an interop value.");
            }
        }

        @Override
        public Iterable<Scope> createDefaultLexicalScope(Node node, Frame frame) {
            return DefaultScope.lexicalScope(node, frame);
        }

        @Override
        public Iterable<Scope> createDefaultTopScope(Object global) {
            return DefaultScope.topScope(global);
        }

        @Override
        public void reportAllLanguageContexts(Object vmObject, Object contextsListener) {
            ((PolyglotEngineImpl) vmObject).reportAllLanguageContexts((ContextsListener) contextsListener);
        }

        @Override
        public void reportAllContextThreads(Object vmObject, Object threadsListener) {
            ((PolyglotEngineImpl) vmObject).reportAllContextThreads((ThreadsListener) threadsListener);
        }

        @Override
        public TruffleContext getParentContext(Object impl) {
            PolyglotContextImpl parent = ((PolyglotContextImpl) impl).parent;
            if (parent != null) {
                return parent.truffleContext;
            } else {
                return null;
            }
        }

        @Override
        public Object enterInternalContext(Object impl) {
            return ((PolyglotContextImpl) impl).enter();
        }

        @Override
        public void leaveInternalContext(Object impl, Object prev) {
            ((PolyglotContextImpl) impl).leave(prev);
        }

        @Override
        @TruffleBoundary
        public void closeInternalContext(Object impl) {
            PolyglotContextImpl context = (PolyglotContextImpl) impl;
            if (context.isActive()) {
                throw new IllegalStateException("The context is currently entered and cannot be closed.");
            }
            context.closeImpl(false, false);
        }

        @Override
        public Object createInternalContext(Object vmObject, Map<String, Object> config, TruffleContext spiContext) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) vmObject);
            PolyglotContextImpl impl;
            synchronized (creator.context) {
                impl = new PolyglotContextImpl(creator, config, spiContext);
                impl.creatorApi = impl.getAPIAccess().newContext(impl);
                impl.currentApi = impl.getAPIAccess().newContext(impl);
            }
            return impl;
        }

        @Override
        public void initializeInternalContext(Object vmObject, Object contextImpl) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) vmObject);
            PolyglotContextImpl impl = (PolyglotContextImpl) contextImpl;
            impl.engine.initializeMultiContext(creator.context);
            impl.notifyContextCreated();
            impl.initializeLanguage(creator.language.getId());
        }

        @Override
        public boolean isCreateThreadAllowed(Object vmObject) {
            return ((PolyglotLanguageContext) vmObject).context.config.createThreadAllowed;
        }

        @Override
        public Thread createThread(Object vmObject, Runnable runnable, Object innerContextImpl) {
            if (!isCreateThreadAllowed(vmObject)) {
                throw new IllegalStateException("Creating threads is not allowed.");
            }

            PolyglotLanguageContext threadContext = (PolyglotLanguageContext) vmObject;
            if (innerContextImpl != null) {
                PolyglotContextImpl innerContext = (PolyglotContextImpl) innerContextImpl;
                threadContext = innerContext.getContext(threadContext.language);
            }
            return new PolyglotThread(threadContext, runnable);
        }

        @Override
        public RuntimeException wrapHostException(Object languageContext, Throwable exception) {
            return PolyglotImpl.wrapHostException((PolyglotLanguageContext) languageContext, exception);
        }

        @Override
        public boolean isHostException(Throwable exception) {
            return exception instanceof HostException;
        }

        @Override
        public Throwable asHostException(Throwable exception) {
            return ((HostException) exception).getOriginal();
        }

        @Override
        public Object getCurrentHostContext() {
            PolyglotContextImpl polyglotContext = PolyglotContextImpl.current();
            return polyglotContext == null ? null : polyglotContext.getHostContext();
        }

        @Override
        public Object getPolyglotBindingsForLanguage(Object languageVMObject) {
            return ((PolyglotLanguageContext) languageVMObject).getPolyglotGuestBindings();
        }

        @Override
        public Object findMetaObjectForLanguage(Object languageVMObject, Object value) {
            PolyglotLanguageContext languageContext = ((PolyglotLanguageContext) languageVMObject);
            Env currentLanguage = languageContext.env;
            assert currentLanguage != null : "current language is initialized";

            Env foundLanguage = null;
            Env hostLanguage = languageContext.context.getHostContext().env;
            if (VMAccessor.LANGUAGE.isObjectOfLanguage(hostLanguage, value)) {
                foundLanguage = hostLanguage;
            } else if (VMAccessor.LANGUAGE.isObjectOfLanguage(currentLanguage, value)) {
                foundLanguage = currentLanguage;
            } else {
                for (PolyglotLanguageContext searchContext : languageContext.context.contexts) {
                    if (searchContext.isInitialized() && searchContext != languageContext) {
                        Env searchEnv = searchContext.env;
                        if (VMAccessor.LANGUAGE.isObjectOfLanguage(searchEnv, value)) {
                            foundLanguage = searchEnv;
                            break;
                        }
                    }
                }
            }
            if (foundLanguage != null) {
                return VMAccessor.LANGUAGE.findMetaObject(foundLanguage, value);
            } else {
                return null;
            }
        }

        @SuppressWarnings("cast")
        @Override
        public PolyglotException wrapGuestException(String languageId, Throwable e) {
            PolyglotContextImpl pc = PolyglotContextImpl.current();
            if (pc == null) {
                return null;
            }
            PolyglotLanguage language = pc.engine.findLanguage(languageId, null, true);
            PolyglotLanguageContext languageContext = pc.getContextInitialized(language, null);
            return (PolyglotException) PolyglotImpl.wrapGuestException(languageContext, e);
        }

        @Override
        public Class<? extends TruffleLanguage<?>> getLanguageClass(LanguageInfo language) {
            return ((PolyglotLanguage) NODES.getEngineObject(language)).cache.getLanguageClass();
        }

        @Override
        public TruffleLanguage.Env getLanguageEnv(Object languageVMObject, LanguageInfo language) {
            PolyglotLanguage lang = (PolyglotLanguage) NODES.getEngineObject(language);
            PolyglotLanguageContext context = ((PolyglotLanguageContext) languageVMObject);
            return context.context.getContext(lang).env;
        }

        @Override
        public Object legacyTckEnter(Object vm) {
            throw new AssertionError("Should not reach here.");
        }

        @Override
        public void legacyTckLeave(Object vm, Object prev) {
            throw new AssertionError("Should not reach here.");
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOrCreateRuntimeData(Object sourceVM, Supplier<T> constructor) {
            if (!(sourceVM instanceof VMObject)) {
                return null;
            }
            final PolyglotEngineImpl engine = getEngine(sourceVM);
            if (engine.runtimeData == null) {
                engine.runtimeData = constructor.get();
            }
            return (T) engine.runtimeData;
        }

        @Override
        public boolean isDefaultFileSystem(FileSystem fs) {
            return FileSystems.getDefaultFileSystem() == fs;
        }

        @Override
        public void addToHostClassPath(Object vmObject, TruffleFile entry) {
            HostContext hostContext = ((PolyglotLanguageContext) vmObject).context.getHostContextImpl();
            hostContext.addToHostClasspath(entry);
        }

        @Override
        public String getLanguageHome(Object engineObject) {
            return ((PolyglotLanguage) engineObject).cache.getLanguageHome();
        }

        @Override
        public boolean isInstrumentExceptionsAreThrown(Object vmObject) {
            return getEngine(vmObject).engineOptionValues.get(PolyglotEngineOptions.InstrumentExceptionsAreThrown);
        }

        @Override
        public Handler getLogHandler() {
            return PolyglotLogHandler.INSTANCE;
        }

        @Override
        public LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown) {
            return PolyglotLogHandler.createLogRecord(level, loggerName, message, className, methodName, parameters, thrown);
        }

        @Override
        public Object getCurrentOuterContext() {
            return PolyglotLogHandler.getCurrentOuterContext();
        }

        @Override
        public Map<String, Level> getLogLevels(final Object context) {
            if (!(context instanceof PolyglotContextImpl)) {
                throw new AssertionError();
            }
            return ((PolyglotContextImpl) context).config.logLevels;
        }

        @Override
        public Set<String> getValidMimeTypes(String language) {
            if (language == null) {
                return LanguageCache.languageMimes().keySet();
            } else {
                LanguageCache lang = LanguageCache.languages().get(language);
                if (lang != null) {
                    return lang.getMimeTypes();
                } else {
                    return Collections.emptySet();
                }
            }
        }

        @Override
        public boolean isCharacterBasedSource(String language, String mimeType) {
            LanguageCache cache = LanguageCache.languages().get(language);
            if (cache == null) {
                return true;
            }
            String useMimeType = mimeType;
            if (useMimeType == null) {
                useMimeType = cache.getDefaultMimeType();
            }
            if (useMimeType == null || !cache.getMimeTypes().contains(useMimeType)) {
                return true;
            }
            return cache.isCharacterMimeType(useMimeType);
        }

        @Override
        public Object asHostObject(Object obj) {
            assert isHostObject(obj);
            HostObject javaObject = (HostObject) obj;
            return javaObject.obj;
        }

        @Override
        public boolean isHostFunction(Object obj) {
            if (TruffleOptions.AOT) {
                return false;
            }
            return HostFunction.isInstance(obj);
        }

        @Override
        public boolean isHostObject(Object obj) {
            return HostObject.isInstance(obj);
        }

        @Override
        public boolean isHostSymbol(Object obj) {
            return HostObject.isStaticClass(obj);
        }

    }
}
