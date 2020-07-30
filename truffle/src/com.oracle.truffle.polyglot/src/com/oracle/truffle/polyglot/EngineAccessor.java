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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleFile.FileTypeDetector;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLogger;
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
import com.oracle.truffle.polyglot.PolyglotSource.EmbedderFileSystemContext;

final class EngineAccessor extends Accessor {

    static final EngineAccessor ACCESSOR = new EngineAccessor();

    static final NodeSupport NODES = ACCESSOR.nodeSupport();
    static final SourceSupport SOURCE = ACCESSOR.sourceSupport();
    static final InstrumentSupport INSTRUMENT = ACCESSOR.instrumentSupport();
    static final LanguageSupport LANGUAGE = ACCESSOR.languageSupport();
    static final JDKSupport JDKSERVICES = ACCESSOR.jdkSupport();
    static final InteropSupport INTEROP = ACCESSOR.interopSupport();
    static final RuntimeSupport RUNTIME = ACCESSOR.runtimeSupport();

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
        public <C> Object getDefaultLanguageView(TruffleLanguage<C> truffleLanguage, C context, Object value) {
            return new DefaultLanguageView<>(truffleLanguage, context, value);
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
            if (PolyglotContextImpl.currentNotEntered() != sourceContext.context) {
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
            return createSourceSectionStatic(source, sectionImpl);
        }

        static org.graalvm.polyglot.SourceSection createSourceSectionStatic(org.graalvm.polyglot.Source source, SourceSection sectionImpl) {
            org.graalvm.polyglot.Source polyglotSource = source;
            APIAccess apiAccess = PolyglotImpl.getInstance().getAPIAccess();
            if (polyglotSource == null) {
                Source sourceImpl = sectionImpl.getSource();
                polyglotSource = apiAccess.newSource(sourceImpl.getLanguage(), sourceImpl);
            }
            return apiAccess.newSourceSection(polyglotSource, sectionImpl);
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
            PolyglotLanguageContext context = PolyglotContextImpl.requireContext().getLanguageContext(languageClass);
            TruffleLanguage.Env env = context.env;
            if (env == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PolyglotEngineException.illegalState("Current context is not yet initialized or already disposed.");
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw PolyglotEngineException.illegalState("Current context is not yet initialized or already disposed.");
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
        public Env getLegacyLanguageEnv(Object obj, boolean nullForHost) {
            PolyglotContextImpl context = PolyglotContextImpl.currentNotEntered();
            if (context == null) {
                return null;
            }
            PolyglotLanguage language = findLegacyLanguage(context, obj);
            if (language == null) {
                return null;
            }
            return context.getContext(language).env;
        }

        private static PolyglotLanguage findLegacyLanguage(PolyglotContextImpl context, Object value) {
            PolyglotLanguage foundLanguage = null;
            for (PolyglotLanguageContext searchContext : context.contexts) {
                if (searchContext.isCreated()) {
                    final TruffleLanguage.Env searchEnv = searchContext.env;
                    if (EngineAccessor.LANGUAGE.isObjectOfLanguage(searchEnv, value)) {
                        foundLanguage = searchContext.language;
                        break;
                    }
                }
            }
            return foundLanguage;
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
            PolyglotLanguage language = findObjectLanguage(context.engine, guestObject);
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
            PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
            return currentContext.getPolyglotBindings().as(Map.class);
        }

        @Override
        public Object getPolyglotBindingsObject() {
            PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
            return currentContext.getPolyglotBindingsObject();
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
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
            return PolyglotImpl.hostToGuestException((PolyglotLanguageContext) languageContext, exception);
        }

        @Override
        public boolean isHostException(Throwable exception) {
            return exception instanceof HostException;
        }

        @Override
        public Throwable asHostException(Throwable exception) {
            if (!(exception instanceof HostException)) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
            PolyglotContextImpl pc = PolyglotContextImpl.currentNotEntered();
            if (pc == null) {
                return null;
            }
            PolyglotLanguage language = pc.engine.findLanguage(null, languageId, null, true, true);
            PolyglotLanguageContext languageContext = pc.getContextInitialized(language, null);
            return (PolyglotException) PolyglotImpl.guestToHostException(languageContext, e);
        }

        @Override
        public Set<? extends Class<?>> getProvidedTags(LanguageInfo language) {
            return ((PolyglotLanguage) NODES.getPolyglotLanguage(language)).cache.getProvidedTags();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getOrCreateRuntimeData(Object polyglotEngine, BiFunction<OptionValues, Supplier<TruffleLogger>, T> constructor) {
            if (polyglotEngine == null) {
                OptionValues engineOptionValues = PolyglotEngineImpl.getEngineOptionsWithNoEngine();
                return constructor.apply(engineOptionValues, PolyglotLoggers.createCompilerLoggerProvider(null));
            }

            final PolyglotEngineImpl engine = (PolyglotEngineImpl) polyglotEngine;
            if (engine.runtimeData == null) {
                engine.runtimeData = constructor.apply(engine.engineOptionValues, PolyglotLoggers.createCompilerLoggerProvider(engine));
            }
            return (T) engine.runtimeData;
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
            OptionValuesImpl engineOptionValues = getEngine(polyglotEngine).engineOptionValues;
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
            return PolyglotLoggers.defaultSPI();
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
        public Object getCurrentOuterContext() {
            return PolyglotLoggers.getCurrentOuterContext();
        }

        @Override
        public Map<String, Level> getLogLevels(final Object loggerCache) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).getLogLevels();
        }

        @Override
        public Object getLoggerOwner(Object loggerCache) {
            return ((PolyglotLoggers.LoggerCache) loggerCache).getEngine();
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
            PolyglotContextImpl currentContext = PolyglotContextImpl.currentNotEntered();
            if (currentContext != null && currentContext.sourcesToInvalidate != null) {
                currentContext.sourcesToInvalidate.add(source);
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
        public boolean isHostToGuestRootNode(RootNode rootNode) {
            return rootNode instanceof HostToGuestRootNode;
        }

        @Override
        public AssertionError invalidSharingError(Object polyglotEngine) throws AssertionError {
            return PolyglotReferences.invalidSharingError((PolyglotEngineImpl) polyglotEngine);
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
