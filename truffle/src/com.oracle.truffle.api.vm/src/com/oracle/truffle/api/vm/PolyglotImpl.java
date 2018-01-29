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
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
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
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.vm.PolyglotLanguageContext.ToGuestValueNode;
import com.oracle.truffle.api.vm.PolyglotLanguageContext.ToGuestValuesNode;

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

    private final PolyglotSource sourceImpl = new PolyglotSource(this);
    private final PolyglotSourceSection sourceSectionImpl = new PolyglotSourceSection(this);
    private final AtomicReference<PolyglotEngineImpl> preInitializedEngineRef = new AtomicReference<>();

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
                    long maximumAllowedAllocationBytes, boolean useSystemProperties, boolean boundEngine) {
        ensureInitialized();
        OutputStream resolvedOut = out == null ? System.out : out;
        OutputStream resolvedErr = err == null ? System.err : err;
        InputStream resolvedIn = in == null ? System.in : in;

        DispatchOutputStream dispatchOut = INSTRUMENT.createDispatchOutput(resolvedOut);
        DispatchOutputStream dispatchErr = INSTRUMENT.createDispatchOutput(resolvedErr);
        ClassLoader contextClassLoader = TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader();

        PolyglotEngineImpl impl = preInitializedEngineRef.getAndSet(null);
        if (impl != null) {
            if (!impl.patch(dispatchOut, dispatchErr, resolvedIn, arguments, timeout, timeoutUnit, sandbox, useSystemProperties, contextClassLoader, boundEngine)) {
                impl.ensureClosed(false, true);
                impl = null;
            }
        }
        if (impl == null) {
            impl = new PolyglotEngineImpl(this, dispatchOut, dispatchErr, resolvedIn, arguments, timeout, timeoutUnit, sandbox, useSystemProperties,
                            contextClassLoader, boundEngine);
        }
        Engine engine = getAPIAccess().newEngine(impl);
        impl.api = engine;
        return engine;
    }

    /**
     * Pre-initializes a polyglot engine instance.
     *
     * @since 0.31
     */
    @Override
    public void preInitializeEngine() {
        ensureInitialized();
        final PolyglotEngineImpl preInitializedEngine = PolyglotEngineImpl.preInitialize(
                        this,
                        INSTRUMENT.createDispatchOutput(System.out),
                        INSTRUMENT.createDispatchOutput(System.err),
                        System.in,
                        TruffleOptions.AOT ? null : Thread.currentThread().getContextClassLoader());
        preInitializedEngineRef.set(preInitializedEngine);
    }

    /**
     * Cleans the pre-initialized polyglot engine instance.
     *
     * @since 0.31
     */
    @Override
    public void resetPreInitializedEngine() {
        preInitializedEngineRef.set(null);
        PolyglotEngineImpl.resetPreInitializedEngine();
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
        } else if (e instanceof InteropException) {
            throw ((InteropException) e).raise();
        }
        return new HostException(e);
    }

    @TruffleBoundary
    static <T extends Throwable> RuntimeException wrapGuestException(PolyglotLanguageContext context, T e) {
        if (e instanceof PolyglotException) {
            return (PolyglotException) e;
        } else if (e instanceof EngineException) {
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
        } else {
            // fallthrough
        }

        APIAccess access = context.getEngine().impl.getAPIAccess();
        PolyglotExceptionImpl exceptionImpl = new PolyglotExceptionImpl(context, e);
        return access.newLanguageException(exceptionImpl.getMessage(), exceptionImpl);
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
            return LANGUAGE.getContext(PolyglotContextImpl.requireContext().requireEnv((PolyglotLanguage) vmObject));
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
        public Env getEnvForLanguage(Object vmObject, String languageId, String mimeType) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) vmObject;
            PolyglotLanguageContext context = languageContext.context.findLanguageContext(languageId, mimeType, true);
            context.ensureInitialized(languageContext.language);
            return context.env;
        }

        @Override
        public Env getEnvForInstrument(Object vmObject, String languageId, String mimeType) {
            PolyglotLanguageContext context = PolyglotContextImpl.requireContext().findLanguageContext(languageId, mimeType, true);
            context.ensureInitialized(null);
            return context.env;
        }

        @Override
        public org.graalvm.polyglot.SourceSection createSourceSection(Object vmObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            return ((VMObject) vmObject).getAPIAccess().newSourceSection(source, sectionImpl);
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrument instrument = (PolyglotInstrument) LANGUAGE.getVMObject(info);
            return instrument.lookup(serviceClass);
        }

        @Override
        public <S> S lookup(LanguageInfo info, Class<S> serviceClass) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getEngineObject(info);
            return language.lookup(serviceClass);
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
            return (T) NODES.getLanguageSpi(context.getLanguageContext(languageClass).language.info);
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
            PolyglotLanguageContext languageContext = PolyglotContextImpl.requireContext().contexts[language.index];
            languageContext.ensureCreated(null);
            return languageContext.env;
        }

        @Override
        public Env getExistingEnvForInstrument(LanguageInfo info) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getEngineObject(info);
            PolyglotLanguageContext languageContext = PolyglotContextImpl.requireContext().contexts[language.index];
            return languageContext.env;
        }

        @Override
        public LanguageInfo getObjectLanguage(Object obj, Object vmObject) {
            PolyglotLanguageContext[] contexts = PolyglotContextImpl.requireContext().contexts;
            for (PolyglotLanguageContext context : contexts) {
                PolyglotLanguage language = context.language;
                if (!language.initialized) {
                    continue;
                }
                if (language.cache.singletonLanguage instanceof HostLanguage) {
                    // The HostLanguage might not have context created even when JavaObjects exist
                    // Check it separately:
                    if (((HostLanguage) language.cache.singletonLanguage).isObjectOfLanguage(obj)) {
                        return language.info;
                    } else {
                        continue;
                    }
                }
                Env env = context.env;
                if (env != null && LANGUAGE.isObjectOfLanguage(env, obj)) {
                    return language.info;
                }
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
        public Iterable<? extends Object> importSymbols(Object vmObject, Env env, String globalName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            context.language.engine.checkState();
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
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            context.language.engine.checkState();
            return context.context.importSymbolFromLanguage(symbolName);
        }

        @Override
        public Object lookupSymbol(Object vmObject, Env env, LanguageInfo language, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            int index = context.context.engine.idToLanguage.get(language.getId()).index;
            return context.context.contexts[index].lookupGuest(symbolName);
        }

        @Override
        public Object lookupHostSymbol(Object vmObject, Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            return context.context.getHostContext().lookupGuest(symbolName);
        }

        @Override
        public boolean isHostAccessAllowed(Object vmObject, Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            return context.context.hostAccessAllowed;
        }

        @Override
        public void exportSymbol(Object vmObject, String symbolName, Object value) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) vmObject;
            context.language.engine.checkState();
            context.context.exportSymbolFromLanguage(context, symbolName, value);
        }

        @Override
        public Map<String, ? extends Object> getExportedSymbols(Object vmObject) {
            PolyglotContextImpl currentContext = PolyglotContextImpl.current();
            if (currentContext == null) {
                return Collections.emptyMap();
            }
            Set<Map.Entry<String, Object>> entries = new LinkedHashSet<>();
            synchronized (currentContext) {
                for (Map.Entry<String, ?> symbol : currentContext.polyglotScope.entrySet()) {
                    Object value = toGuestValue(symbol.getValue(), vmObject);
                    entries.add(new AbstractMap.SimpleImmutableEntry<>(symbol.getKey(), value));
                }
            }
            Set<Map.Entry<String, Object>> mapEntries = Collections.unmodifiableSet(entries);
            return new AbstractMap<String, Object>() {

                @Override
                public Set<Map.Entry<String, Object>> entrySet() {
                    return mapEntries;
                }

                @Override
                public Object remove(Object key) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @SuppressWarnings("deprecation")
        @Override
        public <C> com.oracle.truffle.api.impl.FindContextNode<C> createFindContextNode(TruffleLanguage<C> lang) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            return new PolyglotFindContextNode<>(context.findLanguageContext(lang.getClass(), true).language);
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
        public Value toHostValue(Object obj, Object languageContext) {
            return ((PolyglotLanguageContext) languageContext).toHostValue(obj);
        }

        @Override
        public Object toGuestValue(Object obj, Object context) {
            PolyglotLanguageContext languageContext;
            if (context instanceof VMObject && obj instanceof Value) {
                PolyglotValue valueImpl = (PolyglotValue) ((VMObject) context).getAPIAccess().getImpl((Value) obj);
                languageContext = valueImpl.languageContext;
            } else {
                languageContext = (PolyglotLanguageContext) context;
            }
            return languageContext.toGuestValue(obj);
        }

        @Override
        public Iterable<Scope> createDefaultLexicalScope(Node node, Frame frame) {
            return DefaultScope.lexicalScope(node, frame);
        }

        @Override
        public Iterable<Scope> createDefaultTopScope(TruffleLanguage<?> language, Object context, Object global) {
            return DefaultScope.topScope(language, context, global);
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
            context.close(false);
        }

        @Override
        public Object createInternalContext(Object vmObject, Map<String, Object> config, TruffleContext spiContext) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) vmObject);
            PolyglotContextImpl impl;
            synchronized (creator.context) {
                impl = new PolyglotContextImpl(creator, config, spiContext);
                impl.api = impl.getAPIAccess().newContext(impl);
            }
            return impl;
        }

        @Override
        public void initializeInternalContext(Object vmObject, Object contextImpl) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) vmObject);
            PolyglotContextImpl impl = (PolyglotContextImpl) contextImpl;
            impl.notifyContextCreated();
            impl.initializeLanguage(creator.language.getId());
        }

        @Override
        public boolean isCreateThreadAllowed(Object vmObject) {
            return ((PolyglotLanguageContext) vmObject).context.createThreadAllowed;
        }

        @Override
        public Thread createThread(Object vmObject, Runnable runnable, Object innerContextImpl) {
            if (!isCreateThreadAllowed(vmObject)) {
                throw new IllegalStateException("Creating threads is not allowed.");
            }

            PolyglotLanguageContext threadContext = (PolyglotLanguageContext) vmObject;
            if (innerContextImpl != null) {
                PolyglotContextImpl innerContext = (PolyglotContextImpl) innerContextImpl;
                threadContext = innerContext.contexts[threadContext.language.index];
            }
            return new PolyglotThread(threadContext, runnable);
        }

        @Override
        public RuntimeException wrapHostException(Throwable exception) {
            return PolyglotImpl.wrapHostException(exception);
        }

        @Override
        public RootNode wrapHostBoundary(ExecutableNode executableNode, Supplier<String> name) {
            return new PolyglotBoundaryRootNode(name, executableNode);
        }

        @Override
        public BiFunction<Object, Object, Object> createToGuestValueNode() {
            return ToGuestValueNode.create();
        }

        @Override
        public BiFunction<Object, Object[], Object[]> createToGuestValuesNode() {
            return ToGuestValuesNode.create();
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
        public ClassCastException newClassCastException(String message, Throwable cause) {
            return cause == null ? new PolyglotClassCastException(message) : new PolyglotClassCastException(message, cause);
        }

        @Override
        public NullPointerException newNullPointerException(String message, Throwable cause) {
            return cause == null ? new PolyglotNullPointerException(message) : new PolyglotNullPointerException(message, cause);
        }

        @Override
        public IllegalArgumentException newIllegalArgumentException(String message, Throwable cause) {
            return cause == null ? new PolyglotIllegalArgumentException(message) : new PolyglotIllegalArgumentException(message, cause);
        }

        @Override
        public UnsupportedOperationException newUnsupportedOperationException(String message, Throwable cause) {
            return cause == null ? new PolyglotUnsupportedException(message) : new PolyglotUnsupportedException(message, cause);
        }

        @Override
        public ArrayIndexOutOfBoundsException newArrayIndexOutOfBounds(String message, Throwable cause) {
            return cause == null ? new PolyglotArrayIndexOutOfBoundsException(message) : new PolyglotArrayIndexOutOfBoundsException(message, cause);
        }

        @Override
        public Object getCurrentHostContext() {
            PolyglotContextImpl polyglotContext = PolyglotContextImpl.current();
            return polyglotContext == null ? null : polyglotContext.getHostContext();
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
                throw new IllegalArgumentException();
            }
            final PolyglotEngineImpl engine = getEngine(sourceVM);
            if (engine.runtimeData == null) {
                engine.runtimeData = constructor.get();
            }
            return (T) engine.runtimeData;
        }

    }

}
