/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.provider.DefaultExportProvider;
import com.oracle.truffle.api.library.provider.EagerExportProvider;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.FileSystems.ResetablePath;
import com.oracle.truffle.polyglot.PolyglotContextConfig.FileSystemConfig;
import com.oracle.truffle.polyglot.PolyglotImpl.EmbedderFileSystemContext;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;
import com.oracle.truffle.polyglot.PolyglotLocals.InstrumentContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.InstrumentContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LanguageContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LanguageContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotThread.ThreadSpawnRootNode;
import com.oracle.truffle.polyglot.SystemThread.InstrumentSystemThread;
import com.oracle.truffle.polyglot.SystemThread.LanguageSystemThread;

final class EngineAccessor extends Accessor {

    static final EngineAccessor ACCESSOR = new EngineAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final ExceptionSupport EXCEPTION = ACCESSOR.exceptionSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();
    static final HostSupport HOST = ACCESSOR.hostSupport();
    static final LanguageProviderSupport LANGUAGE_PROVIDER = ACCESSOR.languageProviderSupport();
    static final InstrumentProviderSupport INSTRUMENT_PROVIDER = ACCESSOR.instrumentProviderSupport();
    static final DynamicObjectSupport DYNAMIC_OBJECT = ACCESSOR.dynamicObjectSupport();

    private static List<AbstractClassLoaderSupplier> locatorLoaders() {
        if (ImageInfo.inImageRuntimeCode()) {
            return Collections.emptyList();
        }
        List<ClassLoader> loaders = TruffleLocator.loaders();
        if (loaders == null) {
            return null;
        }
        List<AbstractClassLoaderSupplier> suppliers = new ArrayList<>(2 + loaders.size());
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (isValidLoader(systemClassLoader)) {
            suppliers.add(new ModulePathLoaderSupplier(systemClassLoader));
        }
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (isValidLoader(contextClassLoader)) {
            suppliers.add(new WeakModulePathLoaderSupplier(contextClassLoader));
        }
        for (ClassLoader loader : loaders) {
            if (isValidLoader(loader)) {
                suppliers.add(new StrongClassLoaderSupplier(loader));
            }
        }
        return suppliers;
    }

    private static AbstractClassLoaderSupplier defaultLoader() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (contextClassLoader != null && isValidLoader(contextClassLoader)) {
            return new WeakClassLoaderSupplier(contextClassLoader);
        } else if (isValidLoader(systemClassLoader)) {
            return new StrongClassLoaderSupplier(ClassLoader.getSystemClassLoader());
        } else {
            /*
             * This class loader is necessary for classpath isolation, enabled by the
             * `-Dpolyglotimpl.DisableClassPathIsolation=false` option. It's needed because the
             * system classloader does not load Truffle from a new module layer but from an unnamed
             * module.
             */
            return new StrongClassLoaderSupplier(EngineAccessor.class.getClassLoader());
        }
    }

    /**
     * Check that Truffle classes loaded by {@code loader} are the same as active Truffle runtime
     * classes.
     */
    private static boolean isValidLoader(ClassLoader loader) {
        try {
            Class<?> truffleClassAsSeenByLoader = Class.forName(Truffle.class.getName(), true, loader);
            return truffleClassAsSeenByLoader == Truffle.class;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    static List<AbstractClassLoaderSupplier> locatorOrDefaultLoaders() {
        List<AbstractClassLoaderSupplier> loaders = locatorLoaders();
        if (loaders == null) {
            loaders = List.of(defaultLoader());
        }
        return loaders;
    }

    private EngineAccessor() {
    }

    @Override
    protected void initializeNativeImageTruffleLocator() {
        super.initializeNativeImageTruffleLocator();
    }

    static final class EngineImpl extends EngineSupport {

        private EngineImpl() {
        }

        @Override
        public boolean isDisposed(Object polyglotLanguageContext) {
            return getEngine(polyglotLanguageContext).closed;
        }

        @Override
        public boolean hasCurrentContext() {
            return PolyglotFastThreadLocals.getContext(null) != null;
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
        public Object getDefaultLanguageView(TruffleLanguage<?> truffleLanguage, Object value) {
            return new DefaultLanguageView<>(truffleLanguage, value);
        }

        @TruffleBoundary
        @Override
        public Object getLanguageView(LanguageInfo viewLanguage, Object value) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            PolyglotLanguage language = context.engine.findLanguage(viewLanguage);
            PolyglotLanguageContext languageContext = context.getContextInitialized(language, null);
            return languageContext.getLanguageView(value);
        }

        @Override
        public LanguageInfo getLanguageInfo(Object polyglotInstrument, Class<? extends TruffleLanguage<?>> languageClass) {
            return ((PolyglotInstrument) polyglotInstrument).engine.getLanguage(languageClass, true).info;
        }

        @Override
        public CallTarget parseForLanguage(Object sourceLanguageContext, Source source, String[] argumentNames, boolean allowInternal) {
            PolyglotLanguageContext sourceContext = (PolyglotLanguageContext) sourceLanguageContext;
            if (PolyglotFastThreadLocals.getContext(null) != sourceContext.context) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PolyglotEngineException.illegalState("The context is not entered.");
            }
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
        public boolean isInstrumentReadyForContextEvents(Object polyglotInstrument) {
            return ((PolyglotInstrument) polyglotInstrument).isReadyForContextEvents();
        }

        @Override
        public Object createPolyglotSourceSection(Object polyglotObject, Object source, SourceSection sectionImpl) {
            return PolyglotImpl.getPolyglotSourceSection(((VMObject) polyglotObject).getImpl(), sectionImpl);
        }

        @Override
        public TruffleFile getTruffleFile(TruffleContext truffleContext, String path) {
            PolyglotContextImpl context = getPolyglotContext(truffleContext);
            return EngineAccessor.LANGUAGE.getTruffleFile(path, context.getHostContext().getPublicFileSystemContext());
        }

        @Override
        public TruffleFile getTruffleFile(TruffleContext truffleContext, URI uri) {
            PolyglotContextImpl context = getPolyglotContext(truffleContext);
            return EngineAccessor.LANGUAGE.getTruffleFile(uri, context.getHostContext().getPublicFileSystemContext());
        }

        private static PolyglotContextImpl getPolyglotContext(TruffleContext truffleContext) {
            if (truffleContext == null) {
                return PolyglotContextImpl.requireContext();
            } else {
                return (PolyglotContextImpl) LANGUAGE.getPolyglotContext(truffleContext);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public <T> Iterable<T> loadServices(Class<T> type) {
            Map<Class<?>, T> found = new LinkedHashMap<>();
            // 1) Add known Truffle DynamicObjectLibraryProvider service.
            DYNAMIC_OBJECT.lookupTruffleService(type).forEach((s) -> found.putIfAbsent(s.getClass(), s));
            Class<? extends T> legacyInterface = null;
            if (type == EagerExportProvider.class) {
                legacyInterface = com.oracle.truffle.api.library.EagerExportProvider.class.asSubclass(type);
            } else if (type == DefaultExportProvider.class) {
                legacyInterface = com.oracle.truffle.api.library.DefaultExportProvider.class.asSubclass(type);
            }
            for (AbstractClassLoaderSupplier loaderSupplier : EngineAccessor.locatorOrDefaultLoaders()) {
                ClassLoader loader = loaderSupplier.get();
                if (loader != null) {
                    // 2) Lookup implementations of a module aware interface
                    for (T service : ServiceLoader.load(type, loader)) {
                        if (loaderSupplier.accepts(service.getClass())) {
                            ModuleUtils.exportTransitivelyTo(service.getClass().getModule());
                            found.putIfAbsent(service.getClass(), service);
                        }
                    }
                    // 3) Lookup implementations of a legacy interface
                    // GR-46293 Remove the deprecated service interface lookup.
                    if (legacyInterface != null && loaderSupplier.supportsLegacyProviders()) {
                        ModuleUtils.exportToUnnamedModuleOf(loader);
                        for (T service : ServiceLoader.load(legacyInterface, loader)) {
                            if (loaderSupplier.accepts(service.getClass())) {
                                found.putIfAbsent(service.getClass(), service);
                            }
                        }
                    }
                }
            }
            return found.values();
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrument instrument = (PolyglotInstrument) LANGUAGE.getPolyglotInstrument(info);
            return instrument.lookupInternal(serviceClass);
        }

        @Override
        public <S> S lookup(LanguageInfo info, Class<S> serviceClass) {
            if (!((LanguageCache) NODES.getLanguageCache(info)).supportsService(serviceClass)) {
                return null;
            }
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            PolyglotLanguage language = context.engine.findLanguage(info);
            PolyglotLanguageContext languageContext = context.getContext(language);
            languageContext.ensureCreated(language);
            return languageContext.lookupService(serviceClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <C, T extends TruffleLanguage<C>> C getCurrentContext(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            int index = PolyglotFastThreadLocals.computePELanguageIndex(languageClass, PolyglotFastThreadLocals.LANGUAGE_CONTEXT_OFFSET);
            CompilerAsserts.partialEvaluationConstant(index);
            Object contextImpl = PolyglotFastThreadLocals.getLanguageContext(null, index);
            if (contextImpl == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PolyglotEngineException.illegalState("There is no current context available.");
            }
            return (C) contextImpl;
        }

        @Override
        public TruffleContext getTruffleContext(Object polyglotLanguageContext) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) polyglotLanguageContext;
            return languageContext.context.currentTruffleContext;
        }

        @Override
        public Object getHostContext(Object polyglotContext) {
            return ((PolyglotContextImpl) polyglotContext).getHostContextImpl();
        }

        @Override
        public Object asValue(Object polyglotContextImpl, Object guestValue) {
            return ((PolyglotContextImpl) polyglotContextImpl).getHostContext().asValue(guestValue);
        }

        @Override
        public Object enterLanguageFromRuntime(TruffleLanguage<?> language) {
            PolyglotLanguageInstance instance = (PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(language);
            return PolyglotFastThreadLocals.enterLanguage(instance);
        }

        @Override
        public void leaveLanguageFromRuntime(TruffleLanguage<?> language, Object prev) {
            PolyglotLanguageInstance instance = (PolyglotLanguageInstance) EngineAccessor.LANGUAGE.getPolyglotLanguageInstance(language);
            PolyglotFastThreadLocals.leaveLanguage(instance, prev);
        }

        @Override
        public Object enterRootNodeVisit(RootNode root) {
            return PolyglotFastThreadLocals.enterLayer(root);
        }

        @Override
        public void leaveRootNodeVisit(RootNode root, Object prev) {
            PolyglotFastThreadLocals.leaveLayer(prev);
        }

        @Override
        public TruffleContext getCurrentCreatorTruffleContext() {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            return context != null ? context.creatorTruffleContext : null;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> T getCurrentLanguage(Class<T> languageClass) {
            CompilerAsserts.partialEvaluationConstant(languageClass);
            int index = PolyglotFastThreadLocals.computePELanguageIndex(languageClass, PolyglotFastThreadLocals.LANGUAGE_SPI_OFFSET);
            CompilerAsserts.partialEvaluationConstant(index);
            T language = (T) PolyglotFastThreadLocals.getLanguage(null, index, languageClass);
            if (language == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PolyglotEngineException.illegalState("There is no current context available.");
            }
            return language;
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
                throw shouldNotReachHere();
            }
            return ((PolyglotImpl.VMObject) polyglotObject).getEngine();
        }

        @Override
        public TruffleLanguage.Env getEnvForInstrument(LanguageInfo info) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            PolyglotLanguage language = context.engine.findLanguage(info);
            return context.getContextInitialized(language, null).env;
        }

        static PolyglotLanguage findObjectLanguage(PolyglotEngineImpl engine, Object value) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            if (lib.hasLanguage(value)) {
                try {
                    return engine.getLanguage(lib.getLanguage(value), false);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                return null;
            }
        }

        static PolyglotLanguage getLanguageView(PolyglotEngineImpl engine, Object value) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            if (lib.hasLanguage(value)) {
                try {
                    return engine.getLanguage(lib.getLanguage(value), false);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere(e);
                }
            } else {
                return null;
            }
        }

        static boolean isPrimitive(final Object value) {
            final Class<?> valueClass = value.getClass();
            return valueClass == Boolean.class || valueClass == Byte.class || valueClass == Short.class || valueClass == Integer.class || valueClass == Long.class ||
                            valueClass == Float.class || valueClass == Double.class ||
                            valueClass == Character.class || valueClass == String.class;
        }

        @Override
        public Object getCurrentSharingLayer() {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            if (context == null) {
                return null;
            }
            return context.layer;
        }

        @Override
        public Object getCurrentPolyglotEngine() {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            if (context == null) {
                return null;
            }
            return context.engine;
        }

        @Override
        public boolean isMultiThreaded(Object guestObject) {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            if (context == null) {
                return true;
            }
            if (isPrimitive(guestObject)) {
                return false;
            } else if (context.engine.host.isHostValue(guestObject) || guestObject instanceof PolyglotBindings) {
                return true;
            }
            PolyglotLanguage language = findObjectLanguage(context.engine, guestObject);
            if (language == null) {
                // be conservative
                return true;
            }
            return !context.singleThreaded;
        }

        @Override
        public boolean isEvalRoot(RootNode target) {
            // TODO GR-38632 no eval root nodes anymore on the stack for the polyglot api
            return false;
        }

        @Override
        public RuntimeException engineToLanguageException(Throwable t) {
            return PolyglotImpl.engineToLanguageException(t);
        }

        @Override
        public RuntimeException engineToInstrumentException(Throwable t) {
            return PolyglotImpl.engineToInstrumentException(t);
        }

        @Override
        public Object getCurrentFileSystemContext() {
            return PolyglotContextImpl.requireContext().getHostContext().getPublicFileSystemContext();
        }

        @Override
        public Object getPublicFileSystemContext(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).getPublicFileSystemContext();
        }

        @Override
        public Object getInternalFileSystemContext(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).getInternalFileSystemContext();
        }

        @Override
        public Map<String, Collection<? extends FileTypeDetector>> getEngineFileTypeDetectors(Object engineFileSystemObject) {
            if (engineFileSystemObject instanceof VMObject vmObject) {
                return vmObject.getEngine().getFileTypeDetectorsSupplier().get();
            } else if (engineFileSystemObject instanceof EmbedderFileSystemContext) {
                return ((EmbedderFileSystemContext) engineFileSystemObject).fileTypeDetectors.get();
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public Set<String> getValidMimeTypes(Object engineObject, String language) {
            LanguageCache lang = getLanguageCache(engineObject, language);
            if (lang != null) {
                return lang.getMimeTypes();
            } else {
                return Collections.emptySet();
            }
        }

        private static LanguageCache getLanguageCache(Object engineObject, String language) throws AssertionError {
            if (engineObject instanceof VMObject vmObject) {
                PolyglotLanguage polyglotLanguage = vmObject.getEngine().idToLanguage.get(language);
                if (polyglotLanguage != null) {
                    return polyglotLanguage.cache;
                } else {
                    return null;
                }
            } else if (engineObject instanceof EmbedderFileSystemContext) {
                return ((EmbedderFileSystemContext) engineObject).cachedLanguages.get(language);
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public boolean isCharacterBasedSource(Object fsEngineObject, String language, String mimeType) {
            LanguageCache cache = getLanguageCache(fsEngineObject, language);
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
        public Object getInstrumentationHandler(RootNode rootNode) {
            PolyglotSharingLayer sharing = (PolyglotSharingLayer) NODES.getSharingLayer(rootNode);
            if (sharing == null) {
                return null;
            }
            return getInstrumentationHandler(sharing.engine);
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public Object importSymbol(Object polyglotLanguageContext, TruffleLanguage.Env env, String symbolName) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            Object value = context.context.polyglotBindings.get(symbolName);
            if (value != null) {
                return context.getAPIAccess().getValueReceiver(value);
            }
            return null;
        }

        @Override
        public Object lookupHostSymbol(Object polyglotLanguageContext, TruffleLanguage.Env env, String symbolName) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) polyglotLanguageContext).context;
            if (!context.config.hostLookupAllowed) {
                context.engine.host.throwHostLanguageException("Host class access is not allowed.");
            }
            return context.engine.host.findStaticClass(context.getHostContextImpl(), symbolName);
        }

        @Override
        public Object asHostSymbol(Object polyglotLanguageContext, Class<?> symbolClass) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) polyglotLanguageContext).context;
            return context.engine.host.asHostStaticClass(context.getHostContextImpl(), symbolClass);
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
        public boolean isIOAllowed(Object polyglotLanguageContext, Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.context.config.isAllowIO();
        }

        @Override
        public boolean isInnerContextOptionsAllowed(Object polyglotLanguageContext, TruffleLanguage.Env env) {
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.context.config.innerContextOptionsAllowed;
        }

        @Override
        public boolean isCurrentNativeAccessAllowed(Node node) {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(PolyglotFastThreadLocals.resolveLayer(node));
            if (context == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalStateException("No current context entered.");
            }
            return context.config.nativeAccessAllowed;
        }

        @Override
        public boolean inContextPreInitialization(Object polyglotObject) {
            if (polyglotObject instanceof PolyglotLanguageContext languageContext) {
                PolyglotContextImpl polyglotContext = languageContext.context;
                return polyglotContext.getEngine().inEnginePreInitialization && polyglotContext.parent == null;
            } else if (polyglotObject instanceof EmbedderFileSystemContext) {
                return false;
            } else {
                throw shouldNotReachHere();
            }
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

        @Override
        public Object getPolyglotBindingsObject() {
            PolyglotContextImpl currentContext = PolyglotFastThreadLocals.getContext(null);
            return currentContext.getPolyglotBindingsObject();
        }

        @Override
        public Object toGuestValue(Node node, Object obj, Object languageContext) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            return context.toGuestValue(node, obj, false);
        }

        @Override
        public Object asBoxedGuestValue(Object guestObject, Object polyglotLanguageContext) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) polyglotLanguageContext).context;
            if (PolyglotImpl.isGuestPrimitive(guestObject)) {
                return context.engine.host.toHostObject(context.getHostContextImpl(), guestObject);
            } else if (guestObject instanceof TruffleObject) {
                return guestObject;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Provided value not an interop value.");
            }
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
                return parent.currentTruffleContext;
            } else {
                return null;
            }
        }

        @Override
        public Object enterInternalContext(Node location, Object polyglotLanguageContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotLanguageContext);
            PolyglotEngineImpl engine = resolveEngine(location, context);
            return engine.enter(context);
        }

        @Override
        public Object[] enterContextAsPolyglotThread(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            return context.enterThreadChanged(false, true, false, true, false);
        }

        @Override
        public void leaveContextAsPolyglotThread(Object polyglotContext, Object[] prev) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            context.leaveThreadChanged(prev, true, true);
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        @Override
        public <T, R> R leaveAndEnter(Object polyglotContext, TruffleSafepoint.Interrupter interrupter, TruffleSafepoint.InterruptibleFunction<T, R> interruptible, T object) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            return context.leaveAndEnter(interrupter, interruptible, object, false);
        }

        @Override
        public Object enterIfNeeded(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            try {
                return context.engine.enterIfNeeded(context, true);
            } catch (Throwable t) {
                throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
            }
        }

        @Override
        public void leaveIfNeeded(Object polyglotContext, Object prev) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            try {
                context.engine.leaveIfNeeded(prev, context);
            } catch (Throwable t) {
                throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
            }
        }

        @Override
        public boolean initializeInnerContext(Node location, Object polyglotContext, String languageId, boolean allowInternal) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            if (context.parent == null) {
                throw PolyglotEngineException.illegalState("Only created inner contexts can be used to initialize language contexts. " +
                                "Use TruffleLanguage.Env.initializeLanguage(LanguageInfo) instead.");
            }
            PolyglotEngineImpl engine = resolveEngine(location, context);
            try {
                Object[] prev = engine.enter(context);
                try {
                    PolyglotLanguage language = engine.requireLanguage(languageId, allowInternal);
                    PolyglotLanguageContext targetLanguageContext = context.getContext(language);
                    return targetLanguageContext.ensureInitialized(null);
                } finally {
                    engine.leave(prev, context);
                }
            } catch (Throwable t) {
                throw OtherContextGuestObject.toHostOrInnerContextBoundaryException(context.parent, t, context);
            }
        }

        @Override
        public Object evalInternalContext(Node location, Object polyglotContext, Source source, boolean allowInternal) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            if (context.parent == null) {
                throw PolyglotEngineException.illegalState("Only created inner contexts can be used to evaluate sources. " +
                                "Use TruffleLanguage.Env.parseInternal(Source) or TruffleInstrument.Env.parse(Source) instead.");
            }
            PolyglotEngineImpl engine = resolveEngine(location, context);
            try {
                Object[] prev = engine.enter(context);
                try {
                    return evalBoundary(source, prev, context, allowInternal);
                } finally {
                    engine.leave(prev, context);
                }
            } catch (Throwable t) {
                throw OtherContextGuestObject.toHostOrInnerContextBoundaryException(context.parent, t, context);
            }
        }

        @TruffleBoundary
        private static Object evalBoundary(Source source, Object[] prev, PolyglotContextImpl context, boolean allowInternal) {
            PolyglotContextImpl parentEnteredContext = (PolyglotContextImpl) prev[PolyglotFastThreadLocals.CONTEXT_INDEX];
            if (parentEnteredContext != null && parentEnteredContext != context.parent && parentEnteredContext.engine == context.engine) {
                throw PolyglotEngineException.illegalState("Invalid parent context entered. " +
                                "The parent creator context or no context must be entered to evaluate code in an inner context.");
            }
            PolyglotLanguageContext targetContext = context.getContext(context.engine.requireLanguage(source.getLanguage(), allowInternal));
            PolyglotLanguage accessingLanguage = context.creator;
            targetContext.checkAccess(accessingLanguage);
            Object result;
            try {
                CallTarget target = targetContext.parseCached(accessingLanguage, source, null);
                result = target.call(PolyglotImpl.EMPTY_ARGS);
            } catch (Throwable e) {
                throw OtherContextGuestObject.migrateException(context.parent, e, context);
            }
            assert InteropLibrary.isValidValue(result) : "invalid call target return value";
            return context.parent.migrateValue(result, context);
        }

        @Override
        public void leaveInternalContext(Node node, Object impl, Object prev) {
            CompilerAsserts.partialEvaluationConstant(node);
            PolyglotContextImpl context = ((PolyglotContextImpl) impl);
            PolyglotEngineImpl engine = resolveEngine(node, context);
            if (CompilerDirectives.isPartialEvaluationConstant(engine)) {
                engine.leave((Object[]) prev, context);
            } else {
                leaveInternalContextBoundary(prev, context, engine);
            }
        }

        @TruffleBoundary
        private static void leaveInternalContextBoundary(Object prev, PolyglotContextImpl context, PolyglotEngineImpl engine) {
            engine.leave((Object[]) prev, context);
        }

        private static PolyglotEngineImpl resolveEngine(Node node, PolyglotContextImpl context) {
            PolyglotEngineImpl engine;
            if (CompilerDirectives.inCompiledCode() && node != null) {
                RootNode root = node.getRootNode();
                if (root == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("Passed node is not yet adopted. Adopt it first.");
                }
                CompilerAsserts.partialEvaluationConstant(root);
                PolyglotSharingLayer sharing = (PolyglotSharingLayer) EngineAccessor.NODES.getSharingLayer(root);
                engine = sharing.engine;
                CompilerAsserts.partialEvaluationConstant(engine);
                assert engine != null : "root node engine must not be null";
            } else {
                engine = context.engine;
            }
            return engine;
        }

        @Override
        public boolean isContextEntered(Object impl) {
            return PolyglotFastThreadLocals.getContext(null) == impl;
        }

        @Override
        public TruffleContext createInternalContext(Object sourcePolyglotLanguageContext,
                        OutputStream out, OutputStream err, InputStream in, ZoneId timeZone,
                        String[] onlyLanguagesArray, Map<String, Object> config, Map<String, String> options, Map<String, String[]> arguments,
                        Boolean sharingEnabled, boolean initializeCreatorContext, Runnable onCancelledRunnable,
                        Consumer<Integer> onExitedRunnable, Runnable onClosedRunnable, boolean inheritAccess, Boolean allowCreateThreads,
                        Boolean allowNativeAccess, Boolean allowIO, Boolean allowHostLookup, Boolean allowHostClassLoading,
                        Boolean allowCreateProcess, Boolean allowPolyglotAccess, Boolean allowEnvironmentAccess,
                        Map<String, String> customEnvironment, Boolean allowInnerContextOptions) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) sourcePolyglotLanguageContext);
            PolyglotEngineImpl engine = creator.context.engine;
            PolyglotContextConfig creatorConfig = creator.context.config;
            PolyglotContextImpl impl;
            Set<String> allowedLanguages;
            if (onlyLanguagesArray.length == 0) {
                allowedLanguages = creatorConfig.onlyLanguages;
            } else {
                allowedLanguages = new HashSet<>();
                allowedLanguages.addAll(Arrays.asList(onlyLanguagesArray));
                if (initializeCreatorContext) {
                    allowedLanguages.add(creator.language.getId());
                }
                for (String language : allowedLanguages) {
                    if (!creatorConfig.allowedPublicLanguages.contains(language)) {
                        throw PolyglotEngineException.illegalArgument(String.format(
                                        "The language %s permitted for the created inner context is not installed or was not permitted by the parent context. " + //
                                                        "The parent context only permits the use of the following languages: %s. " + //
                                                        "Ensure the context has access to the permitted language or remove the language from the permitted language list.",
                                        language, creatorConfig.allowedPublicLanguages.toString(), language));
                    }
                }
            }

            Map<String, String> useOptions;
            if (options == null) {
                useOptions = creatorConfig.originalOptions;
            } else {
                useOptions = new HashMap<>(creatorConfig.originalOptions);
                useOptions.putAll(options);
            }

            if (options != null && !options.isEmpty() && !creatorConfig.innerContextOptionsAllowed) {
                throw PolyglotEngineException.illegalArgument(String.format(
                                "Language options were specified for the inner context but the outer context does not have the required context options privilege for this operation. " +
                                                "Use TruffleLanguage.Env.isInnerContextOptionsAllowed() to check whether the inner context has this privilege. " +
                                                "Use Context.Builder.allowInnerContextOptions(true) to grant inner context option privilege for inner contexts."));
            }

            APIAccess api = engine.getAPIAccess();

            OutputStream useOut = out;
            if (useOut == null) {
                useOut = creatorConfig.out;
            } else {
                useOut = INSTRUMENT.createDelegatingOutput(out, engine.out);
            }

            OutputStream useErr = err;
            if (useErr == null) {
                useErr = creatorConfig.err;
            } else {
                useErr = INSTRUMENT.createDelegatingOutput(useErr, engine.out);
            }
            final InputStream useIn = in == null ? creatorConfig.in : in;

            boolean useAllowCreateThread = inheritAccess(inheritAccess, allowCreateThreads, creatorConfig.createThreadAllowed);
            boolean useAllowNativeAccess = inheritAccess(inheritAccess, allowNativeAccess, creatorConfig.nativeAccessAllowed);

            FileSystemConfig fileSystemConfig;
            if (inheritAccess(inheritAccess, allowIO, true)) {
                fileSystemConfig = creatorConfig.fileSystemConfig;
            } else {
                FileSystem publicFileSystem = FileSystems.newNoIOFileSystem();
                FileSystem internalFileSystem = PolyglotEngineImpl.ALLOW_IO ? FileSystems.newResourcesFileSystem(engine) : publicFileSystem;
                fileSystemConfig = new FileSystemConfig(api.getIOAccessNone(), publicFileSystem, internalFileSystem);
            }

            /*
             * We currently only support one host access configuration per engine. So we need to
             * inherit the host access configuration from the creator. Exposing host values is
             * explicit anyway, by exposing a value to the inner context.
             */
            Object useAllowHostAccess = creatorConfig.hostAccess;

            boolean useAllowHostClassLoading = inheritAccess(inheritAccess, allowHostClassLoading, creatorConfig.hostClassLoadingAllowed);
            boolean useAllowHostLookup = inheritAccess(inheritAccess, allowHostLookup, creatorConfig.hostLookupAllowed);
            Predicate<String> useClassFilter;
            if (useAllowHostLookup) {
                useClassFilter = creatorConfig.classFilter;
            } else {
                useClassFilter = null;
            }

            boolean useAllowCreateProcess = inheritAccess(inheritAccess, allowCreateProcess, creatorConfig.createProcessAllowed);
            ProcessHandler useProcessHandler;
            if (useAllowCreateProcess) {
                useProcessHandler = creatorConfig.processHandler;
            } else {
                useProcessHandler = null;
            }

            Object usePolyglotAccess;
            if (inheritAccess(inheritAccess, allowPolyglotAccess, true)) {
                usePolyglotAccess = creatorConfig.polyglotAccess;
            } else {
                usePolyglotAccess = api.getPolyglotAccessNone();
            }

            Object useEnvironmentAccess;
            if (inheritAccess(inheritAccess, allowEnvironmentAccess, true)) {
                useEnvironmentAccess = creatorConfig.environmentAccess;
            } else {
                useEnvironmentAccess = api.getEnvironmentAccessNone();
            }

            Map<String, String> useCustomEnvironment;
            if (useEnvironmentAccess == api.getEnvironmentAccessInherit() && !creatorConfig.customEnvironment.isEmpty()) {
                useCustomEnvironment = new HashMap<>(creatorConfig.customEnvironment);
                if (customEnvironment != null) {
                    useCustomEnvironment.putAll(customEnvironment);
                }
            } else {
                useCustomEnvironment = customEnvironment;
            }

            boolean useAllowInnerContextOptions = inheritAccess(inheritAccess, allowInnerContextOptions, creatorConfig.innerContextOptionsAllowed);

            ZoneId useTimeZone = timeZone == null ? creatorConfig.timeZone : timeZone;

            Map<String, String[]> useArguments;
            if (arguments == null) {
                // change: application arguments are not inherited by default
                useArguments = Collections.emptyMap();
            } else {
                useArguments = arguments;
            }

            PolyglotContextConfig innerConfig = new PolyglotContextConfig(engine, creatorConfig.sandboxPolicy, sharingEnabled, useOut, useErr, useIn,
                            useAllowHostLookup, usePolyglotAccess, useAllowNativeAccess, useAllowCreateThread, useAllowHostClassLoading,
                            useAllowInnerContextOptions, creatorConfig.allowExperimentalOptions,
                            useClassFilter, useArguments, allowedLanguages, useOptions, fileSystemConfig, creatorConfig.logHandler,
                            useAllowCreateProcess, useProcessHandler, useEnvironmentAccess, useCustomEnvironment,
                            useTimeZone, creatorConfig.limits, creatorConfig.hostClassLoader, useAllowHostAccess,
                            creatorConfig.allowValueSharing, false,
                            config, onCancelledRunnable, onExitedRunnable, onClosedRunnable);

            synchronized (creator.context) {
                impl = new PolyglotContextImpl(creator, innerConfig);
                impl.api = creator.getImpl().getAPIAccess().newContext(creator.getImpl().contextDispatch, impl, creator.context.engine.api);
                creator.context.addChildContext(impl);
            }

            synchronized (impl) {
                impl.initializeContextLocals();
                impl.notifyContextCreated();
            }
            if (initializeCreatorContext) {
                impl.initializeInnerContextLanguage(creator.language.getId());
            }
            return impl.creatorTruffleContext;
        }

        private static boolean inheritAccess(boolean inheritAccess, Boolean newPrivilege, boolean creatorPrivilege) {
            if (newPrivilege != null) {
                /*
                 * Explicitly set privilege in the context builder. Still we need to respect the
                 * creator privilege.
                 */
                return newPrivilege && creatorPrivilege;
            } else {
                /*
                 * Not set privilege we can inherit the creator privilege if that is enabled.
                 */
                return inheritAccess && creatorPrivilege;
            }
        }

        @Override
        public boolean isCreateThreadAllowed(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.createThreadAllowed;
        }

        @Override
        public Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group, long stackSize, Runnable beforeEnter, Runnable afterLeave) {
            if (!isCreateThreadAllowed(polyglotLanguageContext)) {
                throw PolyglotEngineException.illegalState("Creating threads is not allowed.");
            }
            PolyglotLanguageContext threadContext = (PolyglotLanguageContext) polyglotLanguageContext;
            if (innerContextImpl != null) {
                PolyglotContextImpl innerContext = (PolyglotContextImpl) innerContextImpl;
                threadContext = innerContext.getContext(threadContext.language);
            }
            PolyglotThread newThread = new PolyglotThread(threadContext, runnable, group, stackSize, beforeEnter, afterLeave);
            threadContext.context.checkMultiThreadedAccess(newThread);
            return newThread;
        }

        @Override
        public RuntimeException wrapHostException(Node location, Object languageContext, Throwable exception) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            return context.engine.host.toHostException(context.getHostContextImpl(), exception);
        }

        @Override
        public boolean isHostException(Object languageContext, Throwable exception) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            PolyglotEngineImpl engine = context.engine;
            // During context pre-initialization, engine.host is null, languages are not allowed to
            // use host interop. But the call to isHostException is supported and returns false
            // because languages cannot create a HostObject.
            return !engine.inEnginePreInitialization && engine.host.isHostException(exception);
        }

        @Override
        public Throwable asHostException(Object languageContext, Throwable exception) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            Throwable host = context.engine.host.unboxHostException(exception);
            if (host == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new IllegalArgumentException("Provided value not a host exception.");
            }
            return host;
        }

        @Override
        public Object getCurrentHostContext() {
            PolyglotContextImpl polyglotContext = PolyglotFastThreadLocals.getContext(null);
            return polyglotContext == null ? null : polyglotContext.getHostContext();
        }

        @Override
        public Object getPolyglotBindingsForLanguage(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).getPolyglotGuestBindings();
        }

        @Override
        public Object findMetaObjectForLanguage(Object polyglotLanguageContext, Object value) {
            InteropLibrary lib = InteropLibrary.getFactory().getUncached(value);
            if (lib.hasMetaObject(value)) {
                try {
                    return lib.getMetaObject(value);
                } catch (UnsupportedMessageException e) {
                    throw shouldNotReachHere("Unexpected unsupported message.", e);
                }
            }
            return null;
        }

        @SuppressWarnings("cast")
        @Override
        public RuntimeException wrapGuestException(String languageId, Throwable e) {
            PolyglotContextImpl pc = PolyglotFastThreadLocals.getContext(null);
            if (pc == null) {
                return null;
            }
            PolyglotLanguage language = pc.engine.findLanguage(null, languageId, null, true, true);
            PolyglotLanguageContext languageContext = pc.getContextInitialized(language, null);
            return PolyglotImpl.guestToHostException(languageContext, e, true);
        }

        @Override
        public RuntimeException wrapGuestException(Object polyglotObject, Throwable e) {
            if (polyglotObject instanceof PolyglotContextImpl) {
                PolyglotContextImpl polyglotContext = (PolyglotContextImpl) polyglotObject;
                PolyglotLanguageContext hostLanguageContext = polyglotContext.getHostContext();
                if (polyglotContext.state.isInvalidOrClosed()) {
                    return PolyglotImpl.guestToHostException(hostLanguageContext, e, false);
                } else {
                    PolyglotLanguage language = polyglotContext.getHostContext().language;
                    PolyglotLanguageContext languageContext = polyglotContext.getContextInitialized(language, null);
                    return PolyglotImpl.guestToHostException(languageContext, e, true);
                }
            } else if (polyglotObject instanceof PolyglotEngineImpl) {
                return PolyglotImpl.guestToHostException((PolyglotEngineImpl) polyglotObject, e);
            } else {
                return PolyglotImpl.guestToHostException((PolyglotImpl) polyglotObject, e);
            }
        }

        @Override
        public Set<? extends Class<?>> getProvidedTags(LanguageInfo language) {
            return ((LanguageCache) NODES.getLanguageCache(language)).getProvidedTags();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOrCreateRuntimeData(Object layer) {
            PolyglotSharingLayer useLayer = ((PolyglotSharingLayer) layer);
            PolyglotEngineImpl engine = useLayer != null ? useLayer.engine : null;
            if (engine == null) {
                engine = PolyglotEngineImpl.getFallbackEngine();
            }
            return (T) engine.runtimeData;
        }

        @Override
        public OptionValues getEngineOptionValues(Object polyglotEngine) {
            return ((PolyglotEngineImpl) polyglotEngine).engineOptionValues;
        }

        @Override
        public Collection<CallTarget> findCallTargets(Object polyglotEngine) {
            return INSTRUMENT.getLoadedCallTargets(((PolyglotEngineImpl) polyglotEngine).instrumentationHandler);
        }

        @Override
        public void preinitializeContext(Object polyglotEngine) {
            ((PolyglotEngineImpl) polyglotEngine).preInitialize();
        }

        @Override
        public void finalizeStore(Object polyglotEngine) {
            ((PolyglotEngineImpl) polyglotEngine).finalizeStore();
        }

        @Override
        public Object getEngineLock(Object polyglotEngine) {
            return ((PolyglotEngineImpl) polyglotEngine).lock;
        }

        @Override
        public boolean isInternal(Object engineObject, FileSystem fs) {
            AbstractPolyglotImpl polyglot;
            if (engineObject instanceof VMObject vmObject) {
                polyglot = vmObject.getImpl();
            } else if (engineObject instanceof EmbedderFileSystemContext embedderContext) {
                polyglot = embedderContext.getImpl();
            } else {
                throw new AssertionError("Unsupported engine object " + engineObject);
            }
            return polyglot.getRootImpl().isInternalFileSystem(fs);
        }

        @Override
        public boolean isSocketIOAllowed(Object engineFileSystemContext) {
            if (engineFileSystemContext instanceof PolyglotLanguageContext languageContext) {
                return languageContext.getImpl().getIO().hasHostSocketAccess(languageContext.context.config.fileSystemConfig.ioAccess);
            } else if (engineFileSystemContext instanceof EmbedderFileSystemContext) {
                return true;
            } else {
                throw new AssertionError();
            }
        }

        @Override
        public boolean hasNoAccess(FileSystem fs) {
            return FileSystems.hasNoAccess(fs);
        }

        @Override
        public boolean isInternal(TruffleFile file) {
            Object fsContext = EngineAccessor.LANGUAGE.getFileSystemContext(file);
            Object engineObject = EngineAccessor.LANGUAGE.getFileSystemEngineObject(fsContext);
            if (engineObject instanceof PolyglotLanguageContext) {
                return fsContext == ((PolyglotLanguageContext) engineObject).getInternalFileSystemContext();
            } else {
                // embedder sources are never internal
                return false;
            }
        }

        @Override
        public void addToHostClassPath(Object polyglotLanguageContext, TruffleFile entry) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) polyglotLanguageContext).context;
            if (!context.config.hostLookupAllowed) {
                context.engine.host.throwHostLanguageException("Host class access is not allowed.");
            }
            if (!context.config.hostClassLoadingAllowed) {
                context.engine.host.throwHostLanguageException("Host class loading is not allowed.");
            }
            context.engine.host.addToHostClassPath(context.getHostContextImpl(), entry);
        }

        @Override
        public String getLanguageHome(LanguageInfo languageInfo) {
            return ((LanguageCache) NODES.getLanguageCache(languageInfo)).getLanguageHome();
        }

        @Override
        public boolean isInstrumentExceptionsAreThrown(Object polyglotInstrument) {
            // We want to enable this option for testing in general, to ensure tests fail if
            // instruments throw.

            OptionValuesImpl engineOptionValues = getEngine(polyglotInstrument).engineOptionValues;
            return areAssertionsEnabled() && !engineOptionValues.hasBeenSet(PolyglotEngineOptions.InstrumentExceptionsAreThrown) ||
                            engineOptionValues.get(PolyglotEngineOptions.InstrumentExceptionsAreThrown);
        }

        @SuppressWarnings("all")
        private static boolean areAssertionsEnabled() {
            boolean assertsEnabled = false;
            // Next assignment will be executed when asserts are enabled.
            assert assertsEnabled = true;
            return assertsEnabled;
        }

        @Override
        public Object createDefaultLoggerCache() {
            return PolyglotLoggers.LoggerCache.DEFAULT;
        }

        @Override
        public Object getContextLoggerCache(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.getOrCreateContextLoggers();
        }

        @Override
        public void publish(Object loggerCache, LogRecord logRecord) {
            ((PolyglotLoggers.LoggerCache) loggerCache).getLogHandler().publish(logRecord);
        }

        @Override
        public LogRecord createLogRecord(Object loggerCache, Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).createLogRecord(level, loggerName, message, className, methodName, parameters, thrown);
        }

        @Override
        public Object getOuterContext(Object polyglotContext) {
            return getOuterContext((PolyglotContextImpl) polyglotContext);
        }

        static PolyglotContextImpl getOuterContext(PolyglotContextImpl context) {
            PolyglotContextImpl res = context;
            if (res != null) {
                while (res.parent != null) {
                    res = res.parent;
                }
            }
            return res;
        }

        @Override
        public Map<String, Level> getLogLevels(final Object loggerCache) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).getLogLevels();
        }

        @Override
        public Object getLoggerOwner(Object loggerCache) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).getOwner();
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
            return PolyglotLoggers.getInternalIds();
        }

        @Override
        public Object asHostObject(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            assert isHostObject(languageContext, obj);
            return context.engine.host.unboxHostObject(obj);
        }

        @Override
        public boolean isHostFunction(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            PolyglotEngineImpl engine = context.engine;
            // During context pre-initialization, engine.host is null, languages are not allowed to
            // use host interop. But the call to isHostFunction is supported and returns false
            // because languages cannot create a HostObject.
            return !engine.inEnginePreInitialization && engine.host.isHostFunction(obj);
        }

        @Override
        public boolean isHostObject(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            PolyglotEngineImpl engine = context.engine;
            // During context pre-initialization, engine.host is null, languages are not allowed to
            // use host interop. But the call to isHostObject is supported and returns false because
            // languages cannot create a HostObject.
            return !engine.inEnginePreInitialization && engine.host.isHostObject(obj);
        }

        @Override
        public boolean isHostSymbol(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            PolyglotEngineImpl engine = context.engine;
            // During context pre-initialization, engine.host is null, languages are not allowed to
            // use host interop. But the call to isHostSymbol is supported and returns false because
            // languages cannot create a HostObject.
            return !engine.inEnginePreInitialization && engine.host.isHostSymbol(obj);
        }

        @Override
        public <S> S lookupService(Object polyglotLanguageContext, LanguageInfo language, LanguageInfo accessingLanguage, Class<S> type) {
            PolyglotLanguage lang = ((PolyglotLanguageContext) polyglotLanguageContext).context.engine.findLanguage(language);
            if (!lang.cache.supportsService(type)) {
                return null;
            }
            PolyglotLanguageContext context = ((PolyglotLanguageContext) polyglotLanguageContext).context.getContext(lang);
            PolyglotLanguage accessingLang = ((PolyglotLanguageContext) polyglotLanguageContext).context.engine.findLanguage(accessingLanguage);
            context.ensureCreated(accessingLang);
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
        public <T extends TruffleLanguage<C>, C> ContextReference<C> createContextReference(Class<T> languageClass) {
            return PolyglotFastThreadLocals.createContextReference(languageClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> LanguageReference<T> createLanguageReference(Class<T> targetLanguageClass) {
            return (TruffleLanguage.LanguageReference<T>) PolyglotFastThreadLocals.createLanguageReference(targetLanguageClass);
        }

        @Override
        public FileSystem getFileSystem(Object polyglotContext) {
            return ((PolyglotContextImpl) polyglotContext).config.fileSystemConfig.fileSystem;
        }

        @Override
        public int getAsynchronousStackDepth(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).getEngine().getAsynchronousStackDepth();
        }

        @Override
        public void setAsynchronousStackDepth(Object polyglotInstrument, int depth) {
            getEngine(polyglotInstrument).setAsynchronousStackDepth((PolyglotInstrument) polyglotInstrument, depth);
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
            OutputStream stdOut = outputRedirect.getOutputStream();
            OutputStream stdErr = errorRedirect.getOutputStream();
            ProcessHandler.Redirect useOutputRedirect = stdOut == null ? outputRedirect : ProcessHandler.Redirect.PIPE;
            ProcessHandler.Redirect useErrorRedirect = stdErr == null ? errorRedirect : ProcessHandler.Redirect.PIPE;
            ProcessHandler.ProcessCommand command = ProcessHandler.ProcessCommand.create(cmd, cwd, environment, redirectErrorStream, inputRedirect, useOutputRedirect, useErrorRedirect);
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
            PolyglotLanguageContext context = (PolyglotLanguageContext) polyglotLanguageContext;
            return context.getImpl().getRootImpl().isDefaultProcessHandler(context.context.config.processHandler);
        }

        @Override
        public boolean isIOSupported() {
            return PolyglotEngineImpl.ALLOW_IO;
        }

        @Override
        public boolean isCreateProcessSupported() {
            return PolyglotEngineImpl.ALLOW_CREATE_PROCESS;
        }

        @Override
        public String getUnparsedOptionValue(OptionValues optionValues, OptionKey<?> optionKey) {
            if (!(optionValues instanceof OptionValuesImpl)) {
                throw new IllegalArgumentException(String.format("Only %s is supported.", OptionValuesImpl.class.getName()));
            }
            return ((OptionValuesImpl) optionValues).getUnparsedOptionValue(optionKey);
        }

        @Override
        public String getRelativePathInResourceRoot(TruffleFile truffleFile) {
            return FileSystems.getRelativePathInResourceRoot(truffleFile);
        }

        @Override
        public void onSourceCreated(Source source) {
            PolyglotContextImpl currentContext = PolyglotFastThreadLocals.getContext(null);
            if (currentContext != null && currentContext.sourcesToInvalidate != null) {
                currentContext.sourcesToInvalidate.add(source);
            }
        }

        @Override
        public void registerOnDispose(Object engineObject, Closeable closeable) {
            if (engineObject instanceof PolyglotLanguageContext) {
                ((PolyglotLanguageContext) engineObject).context.registerOnDispose(closeable);
            } else {
                throw CompilerDirectives.shouldNotReachHere("EngineObject must be PolyglotLanguageContext.");
            }
        }

        @Override
        public String getReinitializedPath(TruffleFile truffleFile) {
            Path path = EngineAccessor.LANGUAGE.getPath(truffleFile);
            return ((ResetablePath) path).getReinitializedPath();
        }

        @Override
        public URI getReinitializedURI(TruffleFile truffleFile) {
            Path path = EngineAccessor.LANGUAGE.getPath(truffleFile);
            return ((ResetablePath) path).getReinitializedURI();
        }

        @Override
        public boolean initializeLanguage(Object polyglotLanguageContext, LanguageInfo targetLanguage) {
            PolyglotLanguage targetPolyglotLanguage = ((PolyglotLanguageContext) polyglotLanguageContext).context.engine.findLanguage(targetLanguage);
            PolyglotLanguageContext targetLanguageContext = ((PolyglotLanguageContext) polyglotLanguageContext).context.getContext(targetPolyglotLanguage);
            PolyglotLanguage accessingPolyglotLanguage = ((PolyglotLanguageContext) polyglotLanguageContext).language;
            try {
                targetLanguageContext.checkAccess(accessingPolyglotLanguage);
            } catch (PolyglotEngineException notAccessible) {
                if (notAccessible.e instanceof IllegalArgumentException) {
                    throw new SecurityException(notAccessible.e.getMessage());
                }
                throw notAccessible;
            }
            return targetLanguageContext.ensureInitialized(accessingPolyglotLanguage);
        }

        @Override
        public boolean skipEngineValidation(RootNode rootNode) {
            return rootNode instanceof HostToGuestRootNode || rootNode instanceof ThreadSpawnRootNode;
        }

        @Override
        public AssertionError invalidSharingError(Node node, Object previousSharingLayer, Object newSharingLayer) throws AssertionError {
            return PolyglotSharingLayer.invalidSharingError(node, (PolyglotSharingLayer) previousSharingLayer, (PolyglotSharingLayer) newSharingLayer);
        }

        @Override
        public <T> ContextLocal<T> createInstrumentContextLocal(Object factory) {
            return PolyglotLocals.createInstrumentContextLocal(factory);
        }

        @Override
        public <T> ContextThreadLocal<T> createInstrumentContextThreadLocal(Object factory) {
            return PolyglotLocals.createInstrumentContextThreadLocal(factory);
        }

        @Override
        public <T> ContextLocal<T> createLanguageContextLocal(Object factory) {
            return PolyglotLocals.createLanguageContextLocal(factory);
        }

        @Override
        public <T> ContextThreadLocal<T> createLanguageContextThreadLocal(Object factory) {
            return PolyglotLocals.createLanguageContextThreadLocal(factory);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void initializeInstrumentContextLocal(List<? extends ContextLocal<?>> locals, Object polyglotInstrument) {
            PolyglotLocals.initializeInstrumentContextLocals((List<InstrumentContextLocal<?>>) locals, (PolyglotInstrument) polyglotInstrument);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void initializeInstrumentContextThreadLocal(List<? extends ContextThreadLocal<?>> local, Object polyglotInstrument) {
            PolyglotLocals.initializeInstrumentContextThreadLocals((List<InstrumentContextThreadLocal<?>>) local, (PolyglotInstrument) polyglotInstrument);
        }

        @Override
        public boolean isPolyglotSecret(Object polyglotObject) {
            return PolyglotImpl.SECRET == polyglotObject;
        }

        @SuppressWarnings("unchecked")
        @Override
        public void initializeLanguageContextLocal(List<? extends ContextLocal<?>> locals, Object polyglotLanguageInstance) {
            PolyglotLocals.initializeLanguageContextLocals((List<LanguageContextLocal<?>>) locals, (PolyglotLanguageInstance) polyglotLanguageInstance);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void initializeLanguageContextThreadLocal(List<? extends ContextThreadLocal<?>> local, Object polyglotLanguageInstance) {
            PolyglotLocals.initializeLanguageContextThreadLocals((List<LanguageContextThreadLocal<?>>) local, (PolyglotLanguageInstance) polyglotLanguageInstance);
        }

        @Override
        public OptionValues getInstrumentContextOptions(Object polyglotInstrument, Object polyglotContext) {
            PolyglotInstrument instrument = (PolyglotInstrument) polyglotInstrument;
            PolyglotContextImpl context = (PolyglotContextImpl) polyglotContext;
            return context.getInstrumentContextOptions(instrument);
        }

        @Override
        public boolean isContextClosed(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            PolyglotContextImpl.State localContextState = context.state;
            return localContextState.isInvalidOrClosed();
        }

        @Override
        public boolean isContextCancelling(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            PolyglotContextImpl.State localContextState = context.state;
            return localContextState.isCancelling();
        }

        @Override
        public boolean isContextExiting(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            PolyglotContextImpl.State contextState = context.state;
            return contextState.isExiting();
        }

        @Override
        public Future<Void> pause(Object polyglotContext) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            return context.pause();
        }

        @Override
        public void resume(Object polyglotContext, Future<Void> pauseFuture) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            context.resume(pauseFuture);
        }

        @Override
        public boolean isContextActive(Object polyglotContext) {
            PolyglotContextImpl context = (PolyglotContextImpl) polyglotContext;
            return context.isActive(Thread.currentThread());
        }

        @Override
        public void clearExplicitContextStack(Object polyglotContext) {
            PolyglotContextImpl context = (PolyglotContextImpl) polyglotContext;
            context.clearExplicitContextStack();
        }

        @Override
        public void initiateCancelOrExit(Object polyglotContext, boolean exit, int exitCode, boolean resourceLimit, String message) {
            PolyglotContextImpl context = (PolyglotContextImpl) polyglotContext;
            context.initiateCancelOrExit(exit, exitCode, resourceLimit, message);
        }

        @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
        @Override
        @CompilerDirectives.TruffleBoundary
        public void closeContext(Object impl, boolean force, Node closeLocation, boolean resourceExhaused, String resourceExhausedReason) {
            PolyglotContextImpl context = (PolyglotContextImpl) impl;
            if (force) {
                boolean isActive = isContextActive(context);
                boolean entered = isContextEntered(context);
                if (isActive && !entered) {
                    throw PolyglotEngineException.illegalState(String.format("The context is currently active on the current thread but another different context is entered as top-most context. " +
                                    "Leave or close the top-most context first or close the context on a separate thread to resolve this problem."));
                }
                context.cancel(resourceExhaused, resourceExhausedReason);
                if (entered) {
                    TruffleSafepoint.pollHere(closeLocation != null ? closeLocation : context.uncachedLocation);
                }
            } else {
                synchronized (context) {
                    /*
                     * Closing the context on another thread done as a part of cancelling or exiting
                     * enters the context which could lead to the following IllegalStateException if
                     * the cancelling flag was not checked.
                     */
                    PolyglotContextImpl.State localContextState = context.state;
                    if (context.isActiveNotCancelled(false) && !localContextState.isCancelling() && !localContextState.isExiting()) {
                        /*
                         * Polyglot threads are still allowed to run at this point. They are
                         * required to be finished after finalizeContext.
                         */
                        throw new IllegalStateException("The context is currently active and cannot be closed. Make sure no thread is running or call closeCancelled on the context to resolve this.");
                    }
                }
                context.closeImpl(true);
                context.finishCleanup();
            }
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        public void closeContext(Object impl, boolean force, boolean resourceExhaused, String resourceExhausedReason) {
            PolyglotContextImpl context = (PolyglotContextImpl) impl;
            if (force) {
                context.cancel(resourceExhaused, resourceExhausedReason);
            } else {
                context.closeAndMaybeWait(false, null);
            }
        }

        @Override
        public void closeEngine(Object polyglotEngine, boolean force) {
            PolyglotEngineImpl engine = (PolyglotEngineImpl) polyglotEngine;
            engine.ensureClosed(force, false);
        }

        @Override
        public <T, G> Iterator<T> mergeHostGuestFrames(Object polyglotEngine, StackTraceElement[] hostStack, Iterator<G> guestFrames, boolean inHostLanguage,
                        boolean includeHostFrames, Function<StackTraceElement, T> hostFrameConvertor, Function<G, T> guestFrameConvertor) {
            PolyglotEngineImpl engine = (PolyglotEngineImpl) polyglotEngine;
            return new PolyglotExceptionImpl.MergedHostGuestIterator<>(engine, hostStack, guestFrames, inHostLanguage, includeHostFrames,
                            hostFrameConvertor, guestFrameConvertor);
        }

        @Override
        public boolean isHostToGuestRootNode(RootNode root) {
            return root instanceof HostToGuestRootNode;
        }

        @Override
        public Object createHostAdapterClass(Object languageContext, Object[] types, Object classOverrides) {
            CompilerAsserts.neverPartOfCompilation();
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            return context.engine.host.createHostAdapter(context.getHostContextImpl(), types, classOverrides);
        }

        @Override
        public long calculateContextHeapSize(Object polyglotContext, long stopAtBytes, AtomicBoolean cancelled) {
            return ((PolyglotContextImpl) polyglotContext).calculateHeapSize(stopAtBytes, cancelled);
        }

        @Override
        public Future<Void> submitThreadLocal(Object polyglotContext, Object sourcePolyglotObject, Thread[] threads, ThreadLocalAction action, boolean needsEnter) {
            String componentId;
            if (sourcePolyglotObject instanceof PolyglotInstrument) {
                componentId = ((PolyglotInstrument) sourcePolyglotObject).getId();
            } else if (sourcePolyglotObject instanceof PolyglotLanguageContext) {
                componentId = ((PolyglotLanguageContext) sourcePolyglotObject).language.getId();
            } else {
                throw CompilerDirectives.shouldNotReachHere("Invalid source component");
            }
            return ((PolyglotContextImpl) polyglotContext).threadLocalActions.submit(threads, componentId, action, needsEnter);
        }

        @Override
        public Object getContext(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context;
        }

        @Override
        public Object getStaticObjectClassLoaders(Object polyglotLanguageInstance, Class<?> referenceClass) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).staticObjectClassLoaders.get(referenceClass);
        }

        @Override
        public void setStaticObjectClassLoaders(Object polyglotLanguageInstance, Class<?> referenceClass, Object value) {
            ((PolyglotLanguageInstance) polyglotLanguageInstance).staticObjectClassLoaders.put(referenceClass, value);
        }

        @Override
        public ConcurrentHashMap<Pair<Class<?>, Class<?>>, Object> getGeneratorCache(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).generatorCache;
        }

        @Override
        public boolean areStaticObjectSafetyChecksRelaxed(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).getEngine().getEngineOptionValues().get(PolyglotEngineOptions.RelaxStaticObjectSafetyChecks);
        }

        @Override
        public String getStaticObjectStorageStrategy(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).getEngine().getEngineOptionValues().get(PolyglotEngineOptions.StaticObjectStorageStrategy).name();
        }

        @Override
        public void exitContext(Object impl, Node exitLocation, int exitCode) {
            PolyglotContextImpl context = ((PolyglotContextImpl) impl);
            context.closeExited(exitLocation, exitCode);
        }

        @Override
        public Throwable getPolyglotExceptionCause(Object polyglotExceptionImpl) {
            return ((PolyglotExceptionImpl) polyglotExceptionImpl).exception;
        }

        @Override
        public Object getPolyglotExceptionContext(Object polyglotExceptionImpl) {
            return ((PolyglotExceptionImpl) polyglotExceptionImpl).context;
        }

        @Override
        public Object getPolyglotExceptionEngine(Object polyglotExceptionImpl) {
            return ((PolyglotExceptionImpl) polyglotExceptionImpl).engine;
        }

        @Override
        public boolean isCancelExecution(Throwable throwable) {
            return throwable instanceof PolyglotEngineImpl.CancelExecution;
        }

        @Override
        public boolean isExitException(Throwable throwable) {
            return throwable instanceof PolyglotContextImpl.ExitException;
        }

        @Override
        public boolean isInterruptExecution(Throwable throwable) {
            return throwable instanceof PolyglotEngineImpl.InterruptExecution;
        }

        @Override
        public boolean isResourceLimitCancelExecution(Throwable cancelExecution) {
            return ((PolyglotEngineImpl.CancelExecution) cancelExecution).isResourceLimit();
        }

        @Override
        public boolean isPolyglotEngineException(Throwable throwable) {
            return throwable instanceof PolyglotEngineException;
        }

        @Override
        public RuntimeException getPolyglotEngineExceptionCause(Throwable polyglotEngineException) {
            return ((PolyglotEngineException) polyglotEngineException).e;
        }

        @Override
        public RuntimeException createPolyglotEngineException(RuntimeException cause) {
            return new PolyglotEngineException(cause);
        }

        @Override
        public int getExitExceptionExitCode(Throwable exitException) {
            return ((PolyglotContextImpl.ExitException) exitException).getExitCode();
        }

        @Override
        public SourceSection getCancelExecutionSourceLocation(Throwable cancelExecution) {
            return ((PolyglotEngineImpl.CancelExecution) cancelExecution).getSourceLocation();
        }

        @Override
        public ThreadDeath createCancelExecution(SourceSection sourceSection, String message, boolean resourceLimit) {
            return new PolyglotEngineImpl.CancelExecution(sourceSection, message, resourceLimit);
        }

        @Override
        public SourceSection getExitExceptionSourceLocation(Throwable exitException) {
            return ((PolyglotContextImpl.ExitException) exitException).getSourceLocation();
        }

        @Override
        public ThreadDeath createExitException(SourceSection sourceSection, String message, int exitCode) {
            return new PolyglotContextImpl.ExitException(sourceSection, exitCode, message);
        }

        @Override
        public Throwable createInterruptExecution(SourceSection sourceSection) {
            return new PolyglotEngineImpl.InterruptExecution(sourceSection);
        }

        @Override
        public AbstractPolyglotImpl.AbstractHostLanguageService getHostService(Object polyglotEngineImpl) {
            assert polyglotEngineImpl instanceof PolyglotEngineImpl;
            return ((PolyglotEngineImpl) polyglotEngineImpl).host;
        }

        @Override
        public LogHandler getEngineLogHandler(Object polyglotEngineImpl) {
            return ((PolyglotEngineImpl) polyglotEngineImpl).logHandler;
        }

        @Override
        public LogHandler getContextLogHandler(Object polyglotContextImpl) {
            return ((PolyglotContextImpl) polyglotContextImpl).config.logHandler;
        }

        @Override
        public LogRecord createLogRecord(Level level, String loggerName, String message, String className, String methodName, Object[] parameters, Throwable thrown, String formatKind) {
            return PolyglotLoggers.createLogRecord(level, loggerName, message, className, methodName, parameters, thrown, formatKind);
        }

        @Override
        public String getFormatKind(LogRecord logRecord) {
            return PolyglotLoggers.getFormatKind(logRecord);
        }

        @Override
        public boolean isPolyglotThread(Thread thread) {
            return thread instanceof PolyglotThread;
        }

        @Override
        public Object getHostNull() {
            return EngineAccessor.HOST.getHostNull();
        }

        @Override
        public Object getPolyglotSharingLayer(Object polyglotLanguageInstance) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).sharing;
        }

        @Override
        public boolean getNeedsAllEncodings() {
            return LanguageCache.getNeedsAllEncodings();
        }

        @Override
        public boolean requireLanguageWithAllEncodings(Object encoding) {
            PolyglotContextImpl context = PolyglotFastThreadLocals.getContext(null);
            if (context == null) {
                // we cannot really check this without an entered context.
                return true;
            }

            boolean needsAllEncodingsFound = false;
            for (PolyglotLanguage language : context.engine.languages) {
                if (language != null && !language.isFirstInstance() && language.cache.isNeedsAllEncodings()) {
                    needsAllEncodingsFound = true;
                    break;
                }
            }
            if (!needsAllEncodingsFound) {
                throw new AssertionError(String.format(
                                "The encoding %s for a new TruffleString was requested but was not configured by any language. " +
                                                "In order to use this encoding configure your language using %s.%s(...needsAllEncodings=true).",
                                encoding, TruffleLanguage.class.getSimpleName(), Registration.class.getSimpleName()));
            }
            return true;
        }

        @Override
        public Object getGuestToHostCodeCache(Object polyglotContextImpl) {
            return ((PolyglotContextImpl) polyglotContextImpl).getHostContext().getLanguageInstance().getGuestToHostCodeCache();
        }

        @Override
        public Object installGuestToHostCodeCache(Object polyglotContextImpl, Object cache) {
            return ((PolyglotContextImpl) polyglotContextImpl).getHostContext().getLanguageInstance().installGuestToHostCodeCache(cache);
        }

        @Override
        public AutoCloseable createPolyglotThreadScope() {
            AbstractPolyglotImpl impl = PolyglotImpl.findIsolatePolyglot();
            if (impl != null) {
                return impl.createThreadScope();
            } else {
                return null;
            }
        }

        @Override
        public Object getPolyglotEngineAPI(Object polyglotEngineImpl) {
            return ((PolyglotEngineImpl) polyglotEngineImpl).api;
        }

        @Override
        public Object getPolyglotContextAPI(Object polyglotContextImpl) {
            return ((PolyglotContextImpl) polyglotContextImpl).api;
        }

        @Override
        public EncapsulatingNodeReference getEncapsulatingNodeReference(boolean invalidateOnNull) {
            return PolyglotFastThreadLocals.getEncapsulatingNodeReference(invalidateOnNull);
        }

        @Override
        public Thread createInstrumentSystemThread(Object polyglotInstrument, Runnable runnable, ThreadGroup threadGroup) {
            return new InstrumentSystemThread((PolyglotInstrument) polyglotInstrument, runnable, threadGroup);
        }

        @Override
        public Thread createLanguageSystemThread(Object polyglotLanguageContext, Runnable runnable, ThreadGroup threadGroup) {
            PolyglotLanguageContext languageContext = (PolyglotLanguageContext) polyglotLanguageContext;
            // Ensure that thread is entered in correct context
            if (PolyglotContextImpl.requireContext() != languageContext.context) {
                throw new IllegalStateException("Not entered in an Env's context.");
            }
            return new LanguageSystemThread(languageContext, runnable, threadGroup);
        }

        @Override
        public Object getEngineFromPolyglotObject(Object polyglotObject) {
            return getEngine(polyglotObject);
        }

        @Override
        public SandboxPolicy getContextSandboxPolicy(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.sandboxPolicy;
        }

        @Override
        public SandboxPolicy getEngineSandboxPolicy(Object polyglotInstrument) {
            return ((PolyglotInstrument) polyglotInstrument).engine.sandboxPolicy;
        }

        @Override
        public void ensureInstrumentCreated(Object polyglotContextImpl, String instrumentId) {
            PolyglotInstrument polyglotInstrument = ((PolyglotContextImpl) polyglotContextImpl).engine.idToInstrument.get(instrumentId);
            polyglotInstrument.ensureCreated();
        }

        @Override
        public TruffleFile getInternalResource(Object owner, Class<? extends InternalResource> resourceType) throws IOException {
            InternalResource.Id id = resourceType.getAnnotation(InternalResource.Id.class);
            assert id != null : resourceType + " must be annotated by @InternalResource.Id";
            return getInternalResource(owner, id.value(), true);
        }

        @Override
        public TruffleFile getInternalResource(Object owner, String resourceId) throws IOException {
            return getInternalResource(owner, resourceId, false);
        }

        private static TruffleFile getInternalResource(Object owner, String resourceId, boolean failIfMissing) throws IOException {
            InternalResourceCache resourceCache;
            String componentId;
            Supplier<Collection<String>> supportedResourceIds;
            PolyglotLanguageContext languageContext;
            if (owner instanceof PolyglotLanguageContext) {
                languageContext = (PolyglotLanguageContext) owner;
                LanguageCache cache = languageContext.language.cache;
                resourceCache = cache.getResourceCache(resourceId);
                componentId = cache.getId();
                supportedResourceIds = cache::getResourceIds;
            } else if (owner instanceof PolyglotInstrument polyglotInstrument) {
                InstrumentCache cache = polyglotInstrument.cache;
                resourceCache = cache.getResourceCache(resourceId);
                componentId = cache.getId();
                supportedResourceIds = cache::getResourceIds;
                languageContext = getPolyglotContext(null).getHostContext();
            } else {
                throw CompilerDirectives.shouldNotReachHere("Unsupported owner " + owner);
            }
            if (resourceCache == null) {
                if (failIfMissing) {
                    throw new IllegalArgumentException(String.format("Resource with id %s is not provided by component %s, provided resource types are %s",
                                    resourceId, componentId, String.join(", ", supportedResourceIds.get())));
                } else {
                    return null;
                }
            }
            Path rootPath = resourceCache.getPath(languageContext.getEngine());
            return EngineAccessor.LANGUAGE.getTruffleFile(rootPath.toString(), languageContext.getInternalFileSystemContext());
        }

        @Override
        public Path getEngineResource(Object polyglotEngine, String resourceId) throws IOException {
            InternalResourceCache resourceCache = InternalResourceCache.getEngineResource(resourceId);
            if (resourceCache != null) {
                return resourceCache.getPath((PolyglotEngineImpl) polyglotEngine);
            } else {
                return null;
            }
        }

        @Override
        public Collection<String> getResourceIds(String componentId) {
            if (PolyglotEngineImpl.ENGINE_ID.equals(componentId)) {
                return InternalResourceCache.getEngineResourceIds();
            }
            LanguageCache languageCache = LanguageCache.languages().get(componentId);
            if (languageCache != null) {
                return languageCache.getResourceIds();
            }
            for (InstrumentCache instrumentCache : InstrumentCache.load()) {
                if (instrumentCache.getId().equals(componentId)) {
                    return instrumentCache.getResourceIds();
                }
            }
            throw new IllegalArgumentException(componentId);
        }

        @Override
        public void setIsolatePolyglot(AbstractPolyglotImpl instance) {
            PolyglotImpl.setIsolatePolyglot(instance);
        }
    }

    abstract static class AbstractClassLoaderSupplier implements Supplier<ClassLoader> {
        private final int hashCode;

        AbstractClassLoaderSupplier(ClassLoader loader) {
            this.hashCode = loader == null ? 0 : loader.hashCode();
        }

        boolean supportsLegacyProviders() {
            return true;
        }

        boolean accepts(@SuppressWarnings("unused") Class<?> clazz) {
            return true;
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

    static class StrongClassLoaderSupplier extends AbstractClassLoaderSupplier {

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

    private static final class ModulePathLoaderSupplier extends StrongClassLoaderSupplier {

        ModulePathLoaderSupplier(ClassLoader classLoader) {
            super(classLoader);
        }

        @Override
        boolean supportsLegacyProviders() {
            return false;
        }

        @Override
        boolean accepts(Class<?> clazz) {
            return clazz.getModule().isNamed();
        }
    }

    private static class WeakClassLoaderSupplier extends AbstractClassLoaderSupplier {

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

    private static final class WeakModulePathLoaderSupplier extends WeakClassLoaderSupplier {

        WeakModulePathLoaderSupplier(ClassLoader loader) {
            super(loader);
        }

        @Override
        boolean supportsLegacyProviders() {
            return false;
        }

        @Override
        boolean accepts(Class<?> clazz) {
            return clazz.getModule().isNamed();
        }
    }

}
