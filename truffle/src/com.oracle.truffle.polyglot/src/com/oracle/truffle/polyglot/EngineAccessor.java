/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostService;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ContextLocal;
import com.oracle.truffle.api.ContextThreadLocal;
import com.oracle.truffle.api.InstrumentInfo;
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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.impl.TruffleLocator;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.PolyglotImpl.EmbedderFileSystemContext;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;
import com.oracle.truffle.polyglot.PolyglotLocals.InstrumentContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.InstrumentContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LanguageContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LanguageContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotThread.ThreadSpawnRootNode;

final class EngineAccessor extends Accessor {

    static final EngineAccessor ACCESSOR = new EngineAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final JDKSupport JDKSERVICES = ACCESSOR.jdkSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final ExceptionSupport EXCEPTION = ACCESSOR.exceptionSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();
    static final HostSupport HOST = ACCESSOR.hostSupport();

    private static List<AbstractClassLoaderSupplier> locatorLoaders() {
        if (ImageInfo.inImageRuntimeCode()) {
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

        @Override
        public Object getLanguageView(LanguageInfo viewLanguage, Object value) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getPolyglotLanguage(viewLanguage);
            PolyglotLanguageContext context = PolyglotContextImpl.requireContext().getContextInitialized(language, null);
            return context.getLanguageView(value);
        }

        @Override
        public Object getScopedView(LanguageInfo viewLanguage, Node location, Frame frame, Object value) {
            PolyglotLanguage language = (PolyglotLanguage) NODES.getPolyglotLanguage(viewLanguage);
            PolyglotLanguageContext context = PolyglotContextImpl.requireContext().getContextInitialized(language, null);
            return context.getScopedView(location, frame, value);
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
        public org.graalvm.polyglot.SourceSection createSourceSection(Object polyglotObject, org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            return PolyglotImpl.getPolyglotSourceSection(((VMObject) polyglotObject).getImpl(), sectionImpl);
        }

        @Override
        public TruffleFile getTruffleFile(String path) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            return EngineAccessor.LANGUAGE.getTruffleFile(context.getHostContext().getPublicFileSystemContext(), path);
        }

        @Override
        public TruffleFile getTruffleFile(URI uri) {
            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
            return EngineAccessor.LANGUAGE.getTruffleFile(context.getHostContext().getPublicFileSystemContext(), uri);
        }

        @Override
        public <T> Iterable<T> loadServices(Class<T> type) {
            Map<Class<?>, T> found = new LinkedHashMap<>();
            // Library providers exported by Truffle are not on the GuestLangToolsLoader path.
            if (type.getClassLoader() == Truffle.class.getClassLoader()) {
                for (T service : ServiceLoader.load(type, type.getClassLoader())) {
                    found.putIfAbsent(service.getClass(), service);
                }
            }
            // Search guest languages and tools.
            for (AbstractClassLoaderSupplier loaderSupplier : EngineAccessor.locatorOrDefaultLoaders()) {
                ClassLoader loader = loaderSupplier.get();
                if (seesTheSameClass(loader, type)) {
                    EngineAccessor.JDKSERVICES.exportTo(loader, null);
                    for (T service : ServiceLoader.load(type, loader)) {
                        found.putIfAbsent(service.getClass(), service);
                    }
                }
            }
            return found.values();
        }

        private static boolean seesTheSameClass(ClassLoader loader, Class<?> type) {
            try {
                return loader != null && loader.loadClass(type.getName()) == type;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }

        @Override
        public <T> T lookup(InstrumentInfo info, Class<T> serviceClass) {
            PolyglotInstrument instrument = (PolyglotInstrument) LANGUAGE.getPolyglotInstrument(info);
            return instrument.lookupInternal(serviceClass);
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
            PolyglotLanguage language = (PolyglotLanguage) NODES.getPolyglotLanguage(info);
            return PolyglotContextImpl.requireContext().getContextInitialized(language, null).env;
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
            // TODO no eval root nodes anymore on the stack for the polyglot api
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
            if (engineFileSystemObject instanceof PolyglotLanguageContext) {
                return ((PolyglotLanguageContext) engineFileSystemObject).context.engine.getFileTypeDetectorsSupplier().get();
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
            if (engineObject instanceof PolyglotLanguageContext) {
                PolyglotLanguage polyglotLanguage = ((PolyglotLanguageContext) engineObject).context.engine.idToLanguage.get(language);
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
            Value value = context.context.polyglotBindings.get(symbolName);
            if (value != null) {
                return context.getAPIAccess().getReceiver(value);
            }
            return null;
        }

        @Override
        public Object lookupHostSymbol(Object polyglotLanguageContext, TruffleLanguage.Env env, String symbolName) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) polyglotLanguageContext).context;
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
            PolyglotContextImpl polyglotContext;
            if (polyglotObject instanceof PolyglotContextImpl) {
                polyglotContext = (PolyglotContextImpl) polyglotObject;
            } else if (polyglotObject instanceof PolyglotLanguageContext) {
                polyglotContext = ((PolyglotLanguageContext) polyglotObject).context;
            } else if (polyglotObject instanceof EmbedderFileSystemContext) {
                return false;
            } else {
                throw shouldNotReachHere();
            }
            return polyglotContext.inContextPreInitialization;
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
            PolyglotContextImpl currentContext = PolyglotFastThreadLocals.getContext(null);
            return currentContext.getPolyglotBindings().as(Map.class);
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
        public Object evalInternalContext(Node location, Object polyglotContext, Source source, boolean allowInternal) {
            PolyglotContextImpl context = ((PolyglotContextImpl) polyglotContext);
            if (context.parent == null) {
                throw PolyglotEngineException.illegalState("Only created inner contexts can be used to evaluate sources. " +
                                "Use TruffleLanguage.Env.parseInternal(Source) or TruffleInstrument.Env.parse(Source) instead.");
            }
            PolyglotEngineImpl engine = resolveEngine(location, context);
            Object[] prev = engine.enter(context);
            try {
                return evalBoundary(source, prev, context, allowInternal);
            } finally {
                engine.leave(prev, context);
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
            } catch (RuntimeException e) {
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
        public TruffleContext createInternalContext(Object sourcePolyglotLanguageContext, Map<String, Object> config, boolean initializeCreatorContext) {
            PolyglotLanguageContext creator = ((PolyglotLanguageContext) sourcePolyglotLanguageContext);
            PolyglotContextImpl impl;
            synchronized (creator.context) {
                impl = new PolyglotContextImpl(creator, config);
                creator.context.engine.noInnerContexts.invalidate();
                creator.context.addChildContext(impl);
                impl.api = creator.getImpl().getAPIAccess().newContext(creator.getImpl().contextDispatch, impl, creator.context.engine.api);
            }
            synchronized (impl) {
                impl.initializeContextLocals();
                impl.notifyContextCreated();
                if (initializeCreatorContext) {
                    impl.initializeInnerContextLanguage(creator.language.getId());
                }
            }
            return impl.creatorTruffleContext;
        }

        @Override
        public boolean isCreateThreadAllowed(Object polyglotLanguageContext) {
            return ((PolyglotLanguageContext) polyglotLanguageContext).context.config.createThreadAllowed;
        }

        @Override
        public Thread createThread(Object polyglotLanguageContext, Runnable runnable, Object innerContextImpl, ThreadGroup group, long stackSize) {
            if (!isCreateThreadAllowed(polyglotLanguageContext)) {
                throw PolyglotEngineException.illegalState("Creating threads is not allowed.");
            }
            PolyglotLanguageContext threadContext = (PolyglotLanguageContext) polyglotLanguageContext;
            if (innerContextImpl != null) {
                PolyglotContextImpl innerContext = (PolyglotContextImpl) innerContextImpl;
                threadContext = innerContext.getContext(threadContext.language);
            }
            PolyglotThread newThread = new PolyglotThread(threadContext, runnable, group, stackSize);
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
            return context.engine.host.isHostException(exception);
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
        public PolyglotException wrapGuestException(String languageId, Throwable e) {
            PolyglotContextImpl pc = PolyglotFastThreadLocals.getContext(null);
            if (pc == null) {
                return null;
            }
            PolyglotLanguage language = pc.engine.findLanguage(null, languageId, null, true, true);
            PolyglotLanguageContext languageContext = pc.getContextInitialized(language, null);
            return (PolyglotException) PolyglotImpl.guestToHostException(languageContext, e, true);
        }

        @Override
        public PolyglotException wrapGuestException(Object polyglotObject, Throwable e) {
            if (polyglotObject instanceof PolyglotContextImpl) {
                PolyglotContextImpl polyglotContext = (PolyglotContextImpl) polyglotObject;
                PolyglotLanguage language = polyglotContext.getHostContext().language;
                PolyglotLanguageContext languageContext = polyglotContext.getContextInitialized(language, null);
                return PolyglotImpl.guestToHostException(languageContext, e, true);
            } else if (polyglotObject instanceof PolyglotEngineImpl) {
                return PolyglotImpl.guestToHostException((PolyglotEngineImpl) polyglotObject, e);
            } else {
                return PolyglotImpl.guestToHostException((PolyglotImpl) polyglotObject, e);
            }
        }

        @Override
        public Set<? extends Class<?>> getProvidedTags(LanguageInfo language) {
            return ((PolyglotLanguage) NODES.getPolyglotLanguage(language)).cache.getProvidedTags();
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
        public boolean isInternal(FileSystem fs) {
            return FileSystems.isInternal(fs);
        }

        @Override
        public boolean hasAllAccess(FileSystem fs) {
            return FileSystems.hasAllAccess(fs);
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
            context.engine.host.addToHostClassPath(context.getHostContextImpl(), entry);
        }

        @Override
        public String getLanguageHome(Object engineObject) {
            return ((PolyglotLanguage) engineObject).cache.getLanguageHome();
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
        public Handler getLogHandler(Object loggerCache) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).getLogHandler();
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
            return context.engine.host.isHostFunction(obj);
        }

        @Override
        public boolean isHostObject(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            return context.engine.host.isHostObject(obj);
        }

        @Override
        public boolean isHostSymbol(Object languageContext, Object obj) {
            PolyglotContextImpl context = ((PolyglotLanguageContext) languageContext).context;
            return context.engine.host.isHostSymbol(obj);
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
        public <T extends TruffleLanguage<C>, C> ContextReference<C> createContextReference(Node node, Class<T> languageClass) {
            return PolyglotFastThreadLocals.createContextReference(node, languageClass);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T extends TruffleLanguage<?>> LanguageReference<T> createLanguageReference(Node node, Class<T> targetLanguageClass) {
            return (TruffleLanguage.LanguageReference<T>) PolyglotFastThreadLocals.createLanguageReference(node, targetLanguageClass);
        }

        @Override
        public FileSystem getFileSystem(Object polyglotContext) {
            return ((PolyglotContextImpl) polyglotContext).config.fileSystem;
        }

        @Override
        public int getAsynchronousStackDepth(Object polylgotLanguage) {
            return ((PolyglotLanguage) polylgotLanguage).engine.getAsynchronousStackDepth();
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

        @Override
        public String getRelativePathInLanguageHome(TruffleFile truffleFile) {
            return FileSystems.getRelativePathInLanguageHome(truffleFile);
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
            FileSystem fs = EngineAccessor.LANGUAGE.getFileSystem(truffleFile);
            Path path = EngineAccessor.LANGUAGE.getPath(truffleFile);
            return ((FileSystems.PreInitializeContextFileSystem) fs).pathToString(path);
        }

        @Override
        public URI getReinitializedURI(TruffleFile truffleFile) {
            FileSystem fs = EngineAccessor.LANGUAGE.getFileSystem(truffleFile);
            Path path = EngineAccessor.LANGUAGE.getPath(truffleFile);
            return ((FileSystems.PreInitializeContextFileSystem) fs).absolutePathtoURI(path);
        }

        @Override
        public boolean initializeLanguage(Object polyglotLanguageContext, LanguageInfo targetLanguage) {
            PolyglotLanguage targetPolyglotLanguage = (PolyglotLanguage) NODES.getPolyglotLanguage(targetLanguage);
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
        public boolean isPolyglotObject(Object polyglotObject) {
            return PolyglotImpl.getInstance() == polyglotObject;
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
        public <T, G> Iterator<T> mergeHostGuestFrames(Object instrumentEnv, StackTraceElement[] hostStack, Iterator<G> guestFrames, boolean inHostLanguage,
                        Function<StackTraceElement, T> hostFrameConvertor,
                        Function<G, T> guestFrameConvertor) {
            PolyglotInstrument instrument = (PolyglotInstrument) INSTRUMENT.getPolyglotInstrument(instrumentEnv);
            return new PolyglotExceptionImpl.MergedHostGuestIterator<>(instrument.engine, hostStack, guestFrames, inHostLanguage, hostFrameConvertor, guestFrameConvertor);
        }

        @Override
        public Object createHostAdapterClass(Object languageContext, Class<?>[] types, Object classOverrides) {
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
        public ClassLoader getStaticObjectClassLoader(Object polyglotLanguageInstance, Class<?> referenceClass) {
            return ((PolyglotLanguageInstance) polyglotLanguageInstance).staticObjectClassLoaders.get(referenceClass);
        }

        @Override
        public void setStaticObjectClassLoader(Object polyglotLanguageInstance, Class<?> referenceClass, ClassLoader cl) {
            ((PolyglotLanguageInstance) polyglotLanguageInstance).staticObjectClassLoaders.put(referenceClass, cl);
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
        public Map<String, String> readOptionsFromSystemProperties(Map<String, String> options) {
            return PolyglotEngineImpl.readOptionsFromSystemProperties(options);
        }

        @Override
        public AbstractHostService getHostService(Object polyglotEngineImpl) {
            assert polyglotEngineImpl instanceof PolyglotEngineImpl;
            return ((PolyglotEngineImpl) polyglotEngineImpl).host;
        }

        @Override
        public Handler getEngineLogHandler(Object polyglotEngineImpl) {
            return ((PolyglotEngineImpl) polyglotEngineImpl).logHandler;
        }

        @Override
        public Handler getContextLogHandler(Object polyglotContextImpl) {
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
