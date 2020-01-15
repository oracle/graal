/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.graalvm.options.OptionKey;

final class EngineAccessor extends Accessor {

    static final EngineAccessor ACCESSOR = new EngineAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final JDKSupport JDKSERVICES = ACCESSOR.jdkSupport();

    private static List<AbstractClassLoaderSupplier> locatorLoaders() {
        if (TruffleOptions.AOT) {
            return Collections.emptyList();
        }
        List<ClassLoader> loaders = TruffleLocator.loaders();
        if (loaders == null) {
            return null;
        }
        List<AbstractClassLoaderSupplier> suppliers = new ArrayList<>(loaders.size());
        for (ClassLoader loader : loaders) {
            suppliers.add(new StrongClassLoaderSupplier(loader));
        }
        return suppliers;
    }

    private static List<AbstractClassLoaderSupplier> defaultLoaders() {
        return Arrays.<AbstractClassLoaderSupplier> asList(
                        new StrongClassLoaderSupplier(EngineAccessor.class.getClassLoader()),
                        new StrongClassLoaderSupplier(ClassLoader.getSystemClassLoader()),
                        new WeakClassLoaderSupplier(Thread.currentThread().getContextClassLoader()));
    }

    static List<AbstractClassLoaderSupplier> locatorOrDefaultLoaders() {
        List<AbstractClassLoaderSupplier> loaders = locatorLoaders();
        if (loaders == null) {
            loaders = defaultLoaders();
        }
        return loaders;
    }

    private EngineAccessor() {
    }

    @Override
    protected void initializeNativeImageTruffleLocator() {
        super.initializeNativeImageTruffleLocator();
    }

    @Override
    protected OptionDescriptors getCompilerOptions() {
        return super.getCompilerOptions();
    }

    @Override
    protected void initializeProfile(CallTarget target, Class<?>[] argumentTypes) {
        super.initializeProfile(target, argumentTypes);
    }

    @Override
    protected CallInlined getCallInlined() {
        return super.getCallInlined();
    }

    @Override
    protected void reloadEngineOptions(Object runtimeData, OptionValues optionValues) {
        super.reloadEngineOptions(runtimeData, optionValues);
    }

    @Override
    protected void onEngineClosed(Object runtimeData) {
        super.onEngineClosed(runtimeData);
    }

    @Override
    protected CastUnsafe getCastUnsafe() {
        return super.getCastUnsafe();
    }

    @Override
    protected CallProfiled getCallProfiled() {
        return super.getCallProfiled();
    }

    @Override
    protected boolean isGuestCallStackElement(StackTraceElement element) {
        return super.isGuestCallStackElement(element);
    }

    static final class EngineImpl extends EngineSupport {

        private EngineImpl() {
        }

        @Override
        public boolean isDisposed(Object polyglotLanguageContext) {
            return getEngine(polyglotLanguageContext).closed;
        }

        @Override
        public TruffleLanguage.ContextReference<Object> getCurrentContextReference(Object polyglotLanguage) {
            return ((PolyglotLanguage) polyglotLanguage).getContextReference();
        }

        @Override
        public boolean isPolyglotEvalAllowed(Object polyglotLanguageContext) {
            PolyglotLanguageContext languageContext = ((PolyglotLanguageContext) polyglotLanguageContext);
            return languageContext.isPolyglotEvalAllowed(null);
        }

        @Override
        public boolean isPolyglotBindingsAccessAllowed(Object polyglotLanguageContext) {
            PolyglotLanguageContext languageContext = ((PolyglotLanguageContext) polyglotLanguageContext);
            return languageContext.isPolyglotBindingsAccessAllowed();
        }

        @Override
        public ZoneId getTimeZone(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.getTimeZone();
        }

        @Override
        public Object getPolyglotEngine(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).language.engine;
        }

        @Override
        public CallTarget parseForLanguage(Object sourceLanguageContext, Source source, String[] argumentNames, boolean allowInternal) {
            PolyglotLanguageContext sourceContext = (PolyglotLanguageContext) sourceLanguageContext;
            PolyglotLanguage targetLanguage = sourceContext.context.engine.findLanguage(sourceContext, source.getLanguage(), source.getMimeType(), true, allowInternal);
            PolyglotLanguageContext targetContext = sourceContext.context.getContextInitialized(targetLanguage, sourceContext.language);
            targetContext.checkAccess(sourceContext.getLanguageInstance().language);
            return targetContext.parseCached(sourceContext.language, source, argumentNames);
        }

        @Override
        public Env getEnvForInstrument(String languageId, String mimeType) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            PolyglotLanguage foundLanguage = context.engine.findLanguage(null, languageId, mimeType, true, true);
            return context.getContextInitialized(foundLanguage, null).env;
        }

        @Override
        public org.graalvm.polyglot.SourceSection createSourceSection(Object polyglotObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            return createSourceSectionStatic(polyglotObject, source, sectionImpl);
        }

        static org.graalvm.polyglot.SourceSection createSourceSectionStatic(Object polyglotObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            org.graalvm.polyglot.Source polyglotSource = source;
            if (polyglotSource == null) {
                Source sourceImpl = sectionImpl.getSource();
                polyglotSource = ((PolyglotImpl.VMObject) polyglotObject).getAPIAccess().newSource(sourceImpl.getLanguage(), sourceImpl);
            }
            return ((PolyglotImpl.VMObject) polyglotObject).getAPIAccess().newSourceSection(polyglotSource, sectionImpl);
        }

        @Override
        public TruffleFile getTruffleFile(String path) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            FileSystem fileSystem = context.config.fileSystem;
            Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier = context.engine.getFileTypeDetectorsSupplier();
            return EngineAccessor.LANGUAGE.getTruffleFile(path, fileSystem, fileTypeDetectorsSupplier);
        }

        @Override
        public TruffleFile getTruffleFile(URI uri) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            FileSystem fileSystem = context.config.fileSystem;
            Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier = context.engine.getFileTypeDetectorsSupplier();
            return EngineAccessor.LANGUAGE.getTruffleFile(uri, fileSystem, fileTypeDetectorsSupplier);
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrument instrument = (PolyglotInstrument) LANGUAGE.getPolyglotInstrument(info);
            return instrument.lookup(serviceClass, false);
        }

        @Override
        public <S> S lookup(LanguageInfo info, Class<S> serviceClass) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getPolyglotLanguage(info);
            if (!language.cache.supportsService(serviceClass)) {
                return null;
            }
            PolyglotLanguageContext languageContext = PolyglotContextImpl.requireContext().getContext(language);
            languageContext.ensureCreated(language);
            return languageContext.lookupService(serviceClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotLanguageContext context = PolyglotContextImpl.requireContext().getLanguageContext(languageClass);
            TruffleLanguage.Env env = context.env;
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Current context is not yet initialized or already disposed.");
            }
            return (C) LANGUAGE.getContext(env);
        }

        @Override
        public TruffleContext getTruffleContext(Object polyglotLanguageContext) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) polyglotLanguageContext;
            return languageContext.context.truffleContext;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            TruffleLanguage.Env env = context.getLanguageContext(languageClass).env;
            if (env == null) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("Current context is not yet initialized or already disposed.");
            }
            return (T) EngineAccessor.LANGUAGE.getLanguage(env);
        }

        @Override
        public Map<String, LanguageInfo> getInternalLanguages(Object polyglotObject) {
            if (polyglotObject instanceof PolyglotLanguageContext) {
                return ((PolyglotLanguageContext) polyglotObject).getAccessibleLanguages(true);
            } else {
                return getEngine(polyglotObject).idToInternalLanguageInfo;
            }
        }

        @Override
        public Map<String, LanguageInfo> getPublicLanguages(Object polyglotObject) {
            return ((PolyglotLanguageContext) polyglotObject).getAccessibleLanguages(false);
        }

        @Override
        public Map<String, InstrumentInfo> getInstruments(Object polyglotObject) {
            return getEngine(polyglotObject).idToInternalInstrumentInfo;
        }

        private static PolyglotEngineImpl getEngine(Object polyglotObject) throws AssertionError {
            if (!(polyglotObject instanceof PolyglotImpl.VMObject)) {
                throw new AssertionError();
            }
            return ((PolyglotImpl.VMObject) polyglotObject).getEngine();
        }

        @Override
        public TruffleLanguage.Env getEnvForInstrument(LanguageInfo info) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getPolyglotLanguage(info);
            return PolyglotContextImpl.requireContext().getContextInitialized(language, null).env;
        }

        static PolyglotLanguage findObjectLanguage(PolyglotContextImpl context, PolyglotLanguageContext currentlanguageContext, Object value) {
            PolyglotLanguage foundLanguage = null;
            final PolyglotLanguageContext hostLanguageContext = context.getHostContext();
            // The HostLanguage might not have context created even when JavaObjects exist
            // Check it separately:
            if (currentlanguageContext != null && isPrimitive(value)) {
                return currentlanguageContext.language;
            } else if (EngineAccessor.LANGUAGE.isObjectOfLanguage(hostLanguageContext.env, value)) {
                foundLanguage = hostLanguageContext.language;
            } else if (currentlanguageContext != null && EngineAccessor.LANGUAGE.isObjectOfLanguage(currentlanguageContext.env, value)) {
                foundLanguage = currentlanguageContext.language;
            } else {
                for (PolyglotLanguageContext searchContext : context.contexts) {
                    if (searchContext.isInitialized() && searchContext != currentlanguageContext) {
                        final TruffleLanguage.Env searchEnv = searchContext.env;
                        if (EngineAccessor.LANGUAGE.isObjectOfLanguage(searchEnv, value)) {
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
        public LanguageInfo getObjectLanguage(Object obj) {
            PolyglotLanguage language = findObjectLanguage(PolyglotContextImpl.requireContext(), null, obj);
            if (language != null) {
                return language.info;
            }
            return null;
        }

        @Override
        public Object getCurrentPolyglotEngine() {
            PolyglotContextImpl context = PolyglotContextImpl.currentNotEntered();
            if (context == null) {
                return null;
            }
            return context.engine;
        }

        @Override
        public boolean isMultiThreaded(Object guestObject) {
            PolyglotContextImpl context = PolyglotContextImpl.currentNotEntered();
            if (context == null) {
                return true;
            }
            if (isPrimitive(guestObject)) {
                return false;
            } else if (guestObject instanceof HostObject || guestObject instanceof PolyglotBindings) {
                return true;
            }
            PolyglotLanguage language = findObjectLanguage(context, null, guestObject);
            if (language == null) {
                // be conservative
                return true;
            }
            return context.singleThreaded.isValid();
        }

        @Override
        public boolean isEvalRoot(RootNode target) {
            // TODO no eval root nodes anymore on the stack for the polyglot api
            return false;
        }

        @Override
        public boolean isMimeTypeSupported(Object polyglotLanguageContext, String mimeType) {
            PolyglotEngineImpl engine = getEngine(polyglotLanguageContext);
            for (PolyglotLanguage language : engine.idToLanguage.values()) {
                if (language.cache.getMimeTypes().contains(mimeType)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Object getInstrumentationHandler(Object polyglotObject) {
            return getEngine(polyglotObject).instrumentationHandler;
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public Object importSymbol(Object polyglotLanguageContext, TruffleLanguage.Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
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
        public Object lookupHostSymbol(Object polyglotLanguageContext, TruffleLanguage.Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            HostLanguage.HostContext hostContext = ((PolyglotLanguageContext) polyglotLanguageContext).context.getHostContextImpl();
            Class<?> clazz = hostContext.findClass(symbolName);
            if (clazz == null) {
                return null;
            }
            return HostObject.forStaticClass(clazz, context);
        }

        @Override
        public Object asHostSymbol(Object polyglotLanguageContext, Class<?> symbolClass) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return HostObject.forStaticClass(symbolClass, context);
        }

        @Override
        public boolean isHostAccessAllowed(Object polyglotLanguageContext, TruffleLanguage.Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.context.config.hostLookupAllowed;
        }

        @Override
        public boolean isNativeAccessAllowed(Object polyglotLanguageContext, TruffleLanguage.Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.context.config.nativeAccessAllowed;
        }

        @Override
        public boolean inContextPreInitialization(Object polyglotLanguageContext) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.context.inContextPreInitialization;
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void exportSymbol(Object polyglotLanguageContext, String symbolName, Object value) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            if (value == null) {
                context.context.getPolyglotGuestBindings().remove(symbolName);
                return;
            }
            if (!PolyglotImpl.isGuestPrimitive(value) && !(value instanceof TruffleObject)) {
                throw new IllegalArgumentException("Invalid exported value. Must be an interop value.");
            }
            context.context.getPolyglotGuestBindings().put(symbolName, context.asValue(value));
        }

        @SuppressWarnings("unchecked")
        @Override
        public Map<String, ? extends Object> getExportedSymbols() {
            PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
            return currentContext.getPolyglotBindings().as(Map.class);
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
        public Object asBoxedGuestValue(Object guestObject, Object polyglotLanguageContext) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) polyglotLanguageContext;
            if (PolyglotImpl.isGuestPrimitive(guestObject)) {
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
        public void reportAllLanguageContexts(Object polyglotEngine, Object contextsListener) {
            ((PolyglotEngineImpl) polyglotEngine).reportAllLanguageContexts((ContextsListener) contextsListener);
        }

        @Override
        public void reportAllContextThreads(Object polyglotEngine, Object threadsListener) {
            ((PolyglotEngineImpl) polyglotEngine).reportAllContextThreads((ThreadsListener) threadsListener);
        }

        @Override
        public TruffleContext getParentContext(Object polyglotContext) {
            PolyglotContextImpl parent = ((PolyglotContextImpl) polyglotContext).parent;
            if (parent != null) {
                return parent.truffleContext;
            } else {
                return null;
            }
        }

        @Override
        public Object enterInternalContext(Object polyglotLanguageContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotLanguageContext);
            return context.engine.enter(context);
        }

        @Override
        public void leaveInternalContext(Object impl, Object prev) {
            PolyglotContextImpl context = ((PolyglotContextImpl) impl);
            context.engine.leave(prev, context);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void closeInternalContext(Object impl) {
            PolyglotContextImpl context = (PolyglotContextImpl) impl;
            if (context.isActive()) {
                throw new IllegalStateException("The context is currently entered and cannot be closed.");
            }
            context.closeImpl(false, false, true);
        }

        @Override
        public boolean isInternalContextEntered(Object impl) {
            return PolyglotContextImpl.currentNotEntered() == impl;
        }

        @Override
        public Object createInternalContext(Object sourcePolyglotLanguageContext, Map<String, Object> config, TruffleContext spiContext) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) sourcePolyglotLanguageContext);
            PolyglotContextImpl impl;
            synchronized (creator.context) {
                impl = new PolyglotContextImpl(creator, config, spiContext);
                impl.creatorApi = impl.getAPIAccess().newContext(impl);
                impl.currentApi = impl.getAPIAccess().newContext(impl);
            }
            return impl;
        }

        @Override
        public void initializeInternalContext(Object sourcePolyglotLanguageContext, Object polyglotContext) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) sourcePolyglotLanguageContext);
            PolyglotContextImpl impl = (PolyglotContextImpl) polyglotContext;
            impl.engine.initializeMultiContext(creator.context);
            impl.notifyContextCreated();
            impl.initializeLanguage(creator.language.getId());
        }

        @Override
        public boolean isCreateThreadAllowed(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.createThreadAllowed;
        }

        @Override
        public Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group, long stackSize) {
            if (!isCreateThreadAllowed(polyglotLanguageContext)) {
                throw new IllegalStateException("Creating threads is not allowed.");
            }

            PolyglotLanguageContext threadContext = (PolyglotLanguageContext) polyglotLanguageContext;
            if (innerContextImpl != null) {
                PolyglotContextImpl innerContext = (PolyglotContextImpl) innerContextImpl;
                threadContext = innerContext.getContext(threadContext.language);
            }
            return new PolyglotThread(threadContext, runnable, group, stackSize);
        }

        @Override
        public RuntimeException wrapHostException(Node location, Object languageContext, Throwable exception) {
            return PolyglotImpl.wrapHostException((PolyglotLanguageContext) languageContext, exception);
        }

        @Override
        public boolean isHostException(Throwable exception) {
            return exception instanceof HostException;
        }

        @Override
        public Throwable asHostException(Throwable exception) {
            if (!(exception instanceof HostException)) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalArgumentException("Provided value not a host exception.");
            }
            return ((HostException) exception).getOriginal();
        }

        @Override
        public Object getCurrentHostContext() {
            PolyglotContextImpl polyglotContext = PolyglotContextImpl.currentNotEntered();
            return polyglotContext == null ? null : polyglotContext.getHostContext();
        }

        @Override
        public Object getPolyglotBindingsForLanguage(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).getPolyglotGuestBindings();
        }

        @Override
        public Object findMetaObjectForLanguage(Object polyglotLanguageContext, Object value) {
            PolyglotLanguageContext languageContext = ((PolyglotLanguageContext) polyglotLanguageContext);
            TruffleLanguage.Env currentLanguage = languageContext.env;
            assert currentLanguage != null : "current language is initialized";

            TruffleLanguage.Env foundLanguage = null;
            TruffleLanguage.Env hostLanguage = languageContext.context.getHostContext().env;
            if (EngineAccessor.LANGUAGE.isObjectOfLanguage(hostLanguage, value)) {
                foundLanguage = hostLanguage;
            } else if (EngineAccessor.LANGUAGE.isObjectOfLanguage(currentLanguage, value)) {
                foundLanguage = currentLanguage;
            } else {
                for (PolyglotLanguageContext searchContext : languageContext.context.contexts) {
                    if (searchContext.isInitialized() && searchContext != languageContext) {
                        TruffleLanguage.Env searchEnv = searchContext.env;
                        if (EngineAccessor.LANGUAGE.isObjectOfLanguage(searchEnv, value)) {
                            foundLanguage = searchEnv;
                            break;
                        }
                    }
                }
            }
            if (foundLanguage != null) {
                return EngineAccessor.LANGUAGE.findMetaObject(foundLanguage, value);
            } else {
                return null;
            }
        }

        @SuppressWarnings("cast")
        @Override
        public PolyglotException wrapGuestException(String languageId, Throwable e) {
            PolyglotContextImpl pc = PolyglotContextImpl.currentNotEntered();
            if (pc == null) {
                return null;
            }
            PolyglotLanguage language = pc.engine.findLanguage(null, languageId, null, true, true);
            PolyglotLanguageContext languageContext = pc.getContextInitialized(language, null);
            return (PolyglotException) PolyglotImpl.wrapGuestException(languageContext, e);
        }

        @Override
        public Set<? extends Class<?>> getProvidedTags(LanguageInfo language) {
            return ((PolyglotLanguage) NODES.getPolyglotLanguage(language)).cache.getProvidedTags();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOrCreateRuntimeData(Object polyglotEngine, Function<OptionValues, T> constructor) {
            if (polyglotEngine == null) {
                OptionValues engineOptionValues = PolyglotEngineImpl.getEngineOptionsWithNoEngine();
                return constructor.apply(engineOptionValues);
            }

            final PolyglotEngineImpl engine = (PolyglotEngineImpl) polyglotEngine;
            if (engine.runtimeData == null) {
                engine.runtimeData = constructor.apply(engine.engineOptionValues);
            }
            return (T) engine.runtimeData;
        }

        @Override
        public boolean isDefaultFileSystem(FileSystem fs) {
            return FileSystems.isDefaultFileSystem(fs);
        }

        @Override
        public void addToHostClassPath(Object polyglotLanguageContext, TruffleFile entry) {
            HostLanguage.HostContext hostContext = ((PolyglotLanguageContext) polyglotLanguageContext).context.getHostContextImpl();
            hostContext.addToHostClasspath(entry);
        }

        @Override
        public String getLanguageHome(Object engineObject) {
            return ((PolyglotLanguage) engineObject).cache.getLanguageHome();
        }

        @Override
        public boolean isInstrumentExceptionsAreThrown(Object polyglotEngine) {
            // We want to enable this option for testing in general, to ensure tests fail if
            // instruments throw.
            return areAssertionsEnabled() || getEngine(polyglotEngine).engineOptionValues.get(PolyglotEngineOptions.InstrumentExceptionsAreThrown);
        }

        @SuppressWarnings("all")
        private static boolean areAssertionsEnabled() {
            boolean assertsEnabled = false;
            // Next assignment will be executed when asserts are enabled.
            assert assertsEnabled = true;
            return assertsEnabled;
        }

        @Override
        public Handler getLogHandler(Object polyglotEngine) {
            return polyglotEngine == null ? PolyglotLogHandler.INSTANCE : new PolyglotLogHandler((PolyglotEngineImpl) polyglotEngine);
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
        public Map<String, Level> getLogLevels(final Object polyglotObject) {
            if (polyglotObject instanceof PolyglotContextImpl) {
                return ((PolyglotContextImpl) polyglotObject).config.logLevels;
            } else if (polyglotObject instanceof PolyglotEngineImpl) {
                return ((PolyglotEngineImpl) polyglotObject).logLevels;
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public Set<String> getLanguageIds() {
            return LanguageCache.languages().keySet();
        }

        @Override
        public Set<String> getInstrumentIds() {
            Set<String> ids = new HashSet<>();
            for (InstrumentCache cache : InstrumentCache.load()) {
                ids.add(cache.getId());
            }
            return ids;
        }

        @Override
        public Set<String> getInternalIds() {
            return Collections.singleton(PolyglotEngineImpl.OPTION_GROUP_ENGINE);
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
            return HostFunction.isInstance(obj);
        }

        @Override
        public boolean isHostObject(Object obj) {
            return HostObject.isInstance(obj);
        }

        @Override
        public boolean isHostSymbol(Object obj) {
            if (HostObject.isInstance(obj)) {
                return ((HostObject) obj).isStaticClass();
            }
            return false;
        }

        @Override
        public <S> S lookupService(Object polyglotLanguageContext, LanguageInfo language, LanguageInfo accessingLanguage, Class<S> type) {
            PolyglotLanguage lang = (PolyglotLanguage) NODES.getPolyglotLanguage(language);
            if (!lang.cache.supportsService(type)) {
                return null;
            }
            PolyglotLanguageContext context = ((PolyglotLanguageContext) polyglotLanguageContext).context.getContext(lang);
            context.ensureCreated((PolyglotLanguage) NODES.getPolyglotLanguage(accessingLanguage));
            return context.lookupService(type);
        }

        @Override
        public TruffleLogger getLogger(Object polyglotInstrument, String loggerName) {
            PolyglotInstrument instrument = (PolyglotInstrument) polyglotInstrument;
            String id = instrument.getId();
            PolyglotEngineImpl engine = getEngine(polyglotInstrument);
            Object loggerCache = engine.getOrCreateEngineLoggers();
            return LANGUAGE.getLogger(id, loggerName, loggerCache);
        }

        @Override
        public Object convertPrimitive(Object value, Class<?> requestedType) {
            return ToHostNode.convertLossLess(value, requestedType, InteropLibrary.getFactory().getUncached());
        }

        @SuppressWarnings("unchecked")
        @Override
        @CompilerDirectives.TruffleBoundary
        public <T extends TruffleLanguage<C>, C> TruffleLanguage.ContextReference<C> lookupContextReference(Object polyglotEngine, TruffleLanguage<?> sourceLanguageSPI,
                        Class<T> targetLanguageClass) {
            assert sourceLanguageSPI == null || sourceLanguageSPI.getClass() != targetLanguageClass;
            PolyglotLanguageInstance instance = ((PolyglotEngineImpl) polyglotEngine).getCurrentLanguageInstance(targetLanguageClass);
            return (TruffleLanguage.ContextReference<C>) instance.lookupContextSupplier(resolveLanguage(sourceLanguageSPI));
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<C>, C> TruffleLanguage.ContextReference<C> getDirectContextReference(Object polyglotEngine, TruffleLanguage<?> sourceLanguageSPI,
                        Class<T> targetLanguageClass) {
            assert sourceLanguageSPI == null || sourceLanguageSPI.getClass() == targetLanguageClass;
            return (TruffleLanguage.ContextReference<C>) resolveLanguageInstance(sourceLanguageSPI).getDirectContextSupplier();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> TruffleLanguage.LanguageReference<T> getDirectLanguageReference(Object polyglotEngine, TruffleLanguage<?> sourceLanguageSPI,
                        Class<T> targetLanguageClass) {
            assert sourceLanguageSPI == null || sourceLanguageSPI.getClass() == targetLanguageClass;
            return (TruffleLanguage.LanguageReference<T>) resolveLanguageInstance(sourceLanguageSPI).getDirectLanguageReference();
        }

        @SuppressWarnings("unchecked")
        @Override
        @CompilerDirectives.TruffleBoundary
        public <T extends TruffleLanguage<?>> TruffleLanguage.LanguageReference<T> lookupLanguageReference(Object polyglotEngineImpl, TruffleLanguage<?> sourceLanguageSPI,
                        Class<T> targetLanguageClass) {
            assert sourceLanguageSPI == null || sourceLanguageSPI.getClass() != targetLanguageClass;
            PolyglotLanguageInstance instance = ((PolyglotEngineImpl) polyglotEngineImpl).getCurrentLanguageInstance(targetLanguageClass);
            return (TruffleLanguage.LanguageReference<T>) instance.lookupLanguageSupplier(resolveLanguage(sourceLanguageSPI));
        }

        private static PolyglotLanguageInstance resolveLanguageInstance(TruffleLanguage<?> sourceLanguageSPI) {
            if (sourceLanguageSPI == null) {
                return null;
            }
            return ((PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(sourceLanguageSPI));
        }

        private static PolyglotLanguage resolveLanguage(TruffleLanguage<?> sourceLanguageSPI) {
            if (sourceLanguageSPI == null) {
                return null;
            }
            return ((PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(sourceLanguageSPI)).language;
        }

        @Override
        public FileSystem getFileSystem(Object polyglotContext) {
            return ((PolyglotContextImpl) polyglotContext).config.fileSystem;
        }

        @Override
        public Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> getFileTypeDetectorsSupplier(Object polyglotContext) {
            return ((PolyglotContextImpl) polyglotContext).engine.getFileTypeDetectorsSupplier();
        }

        @Override
        public boolean isCreateProcessAllowed(Object polylgotLanguageContext) {
            return ((PolyglotLanguageContext) polylgotLanguageContext).context.config.createProcessAllowed;
        }

        @Override
        public Map<String, String> getProcessEnvironment(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.getEnvironment();
        }

        @Override
        public Process createSubProcess(Object polyglotLanguageContext, List<String> cmd, String cwd, Map<String, String> environment, boolean redirectErrorStream,
                        ProcessHandler.Redirect inputRedirect, ProcessHandler.Redirect outputRedirect, ProcessHandler.Redirect errorRedirect) throws IOException {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) polyglotLanguageContext;
            OutputStream stdOut = languageContext.getImpl().getIO().getOutputStream(outputRedirect);
            OutputStream stdErr = languageContext.getImpl().getIO().getOutputStream(errorRedirect);
            ProcessHandler.Redirect useOutputRedirect = stdOut == null ? outputRedirect : ProcessHandler.Redirect.PIPE;
            ProcessHandler.Redirect useErrorRedirect = stdErr == null ? errorRedirect : ProcessHandler.Redirect.PIPE;
            ProcessHandler.ProcessCommand command = languageContext.getImpl().getIO().newProcessCommand(cmd, cwd, environment, redirectErrorStream, inputRedirect, useOutputRedirect, useErrorRedirect);
            Process process = languageContext.context.config.processHandler.start(command);
            return ProcessHandlers.decorate(
                            languageContext,
                            cmd,
                            process,
                            stdOut,
                            stdErr);
        }

        @Override
        public boolean hasDefaultProcessHandler(Object polyglotLanguageContext) {
            return ProcessHandlers.isDefault(((PolyglotLanguageContext) polyglotLanguageContext).context.config.processHandler);
        }

        @Override
        public ProcessHandler.Redirect createRedirectToOutputStream(Object polyglotLanguageContext, OutputStream stream) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).getImpl().getIO().createRedirectToStream(stream);
        }

        @Override
        public boolean isIOAllowed() {
            return PolyglotEngineImpl.ALLOW_IO;
        }

        @Override
        public String getUnparsedOptionValue(OptionValues optionValues, OptionKey<?> optionKey) {
            if (!(optionValues instanceof OptionValuesImpl)) {
                throw new IllegalArgumentException(String.format("Only %s is supported.", OptionValuesImpl.class.getName()));
            }
            return ((OptionValuesImpl) optionValues).getUnparsedOptionValue(optionKey);
        }
    }

    abstract static class AbstractClassLoaderSupplier implements Supplier<ClassLoader> {

        private final int hashCode;

        AbstractClassLoaderSupplier(ClassLoader loader) {
            this.hashCode = loader == null ? 0 : loader.hashCode();
        }

        @Override
        public final int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof AbstractClassLoaderSupplier)) {
                return false;
            }
            AbstractClassLoaderSupplier supplier = (AbstractClassLoaderSupplier) obj;
            return Objects.equals(get(), supplier.get());
        }
    }

    static final class StrongClassLoaderSupplier extends AbstractClassLoaderSupplier {

        private final ClassLoader classLoader;

        StrongClassLoaderSupplier(ClassLoader classLoader) {
            super(classLoader);
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader get() {
            return classLoader;
        }
    }

    private static final class WeakClassLoaderSupplier extends AbstractClassLoaderSupplier {

        private final Reference<ClassLoader> classLoaderRef;

        WeakClassLoaderSupplier(ClassLoader classLoader) {
            super(classLoader);
            this.classLoaderRef = new WeakReference<>(classLoader);
        }

        @Override
        public ClassLoader get() {
            return classLoaderRef.get();
        }
    }
}
