/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.polyglot.EngineAccessor.INSTRUMENT;
import static com.oracle.truffle.polyglot.EngineAccessor.LANGUAGE;
import static com.oracle.truffle.polyglot.EngineAccessor.RUNTIME;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.home.HomeFinder;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostService;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.PolyglotContextImpl.ContextWeakReference;
import com.oracle.truffle.polyglot.PolyglotLimits.EngineLimits;
import com.oracle.truffle.polyglot.PolyglotLocals.AbstractContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.AbstractContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;
import com.oracle.truffle.polyglot.PolyglotLoggers.EngineLoggerProvider;
import com.oracle.truffle.polyglot.PolyglotLoggers.LoggerCache;

final class PolyglotEngineImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "host";

    static final String ENGINE_ID = "engine";
    static final String OPTION_GROUP_ENGINE = ENGINE_ID;
    static final String OPTION_GROUP_LOG = "log";
    static final String OPTION_GROUP_IMAGE_BUILD_TIME = "image-build-time";
    static final String LOG_FILE_OPTION = OPTION_GROUP_LOG + ".file";

    // also update list in LanguageRegistrationProcessor
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList(HOST_LANGUAGE_ID, "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm", "file",
                                    ENGINE_ID, OPTION_GROUP_LOG, OPTION_GROUP_IMAGE_BUILD_TIME));

    private static final Map<PolyglotEngineImpl, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean shutdownHookInitialized = false;
    private static final boolean DEBUG_MISSING_CLOSE = Boolean.getBoolean("polyglotimpl.DebugMissingClose");
    static final LocalLocation[] EMPTY_LOCATIONS = new LocalLocation[0];

    final Object lock = new Object();
    final Object instrumentationHandler;
    final String[] permittedLanguages;
    final PolyglotImpl impl;
    DispatchOutputStream out;       // effectively final
    DispatchOutputStream err;       // effectively final
    InputStream in;                 // effectively final
    Engine api;

    // languages by LanguageCache.getStaticIndex()
    @CompilationFinal(dimensions = 1) final PolyglotLanguage[] languages;
    final Map<String, PolyglotLanguage> idToLanguage;
    final Map<String, PolyglotLanguage> classToLanguage;
    final Map<String, Language> idToPublicLanguage;
    final Map<String, LanguageInfo> idToInternalLanguageInfo;

    final Map<String, PolyglotInstrument> idToInstrument;
    final Map<String, Instrument> idToPublicInstrument;
    final Map<String, InstrumentInfo> idToInternalInstrumentInfo;

    @CompilationFinal OptionValuesImpl engineOptionValues;

    // true if engine is implicitly bound to a context and therefore closed with the context
    boolean boundEngine;    // effectively final

    /*
     * True if the runtime wants to store the resulting code when the engine is closed. This means
     * that strong references for source caches should be used.
     */
    boolean storeEngine; // modified on patch
    Handler logHandler;     // effectively final
    final Exception createdLocation = DEBUG_MISSING_CLOSE ? new Exception() : null;
    private final EconomicSet<ContextWeakReference> contexts = EconomicSet.create(Equivalence.IDENTITY);
    final ReferenceQueue<PolyglotContextImpl> contextsReferenceQueue = new ReferenceQueue<>();
    private final AtomicReference<PolyglotContextImpl> preInitializedContext = new AtomicReference<>();

    @CompilationFinal Assumption singleContext = Truffle.getRuntime().createAssumption("Single context per engine.");
    final Assumption singleThreadPerContext = Truffle.getRuntime().createAssumption("Single thread per context of an engine.");
    final Assumption noInnerContexts = Truffle.getRuntime().createAssumption("No inner contexts.");
    final Assumption customHostClassLoader = Truffle.getRuntime().createAssumption("No custom host class loader needed.");

    volatile OptionDescriptors allOptions;
    volatile boolean closed;

    // field used by the TruffleRuntime implementation to persist state per Engine
    final Object runtimeData;
    Map<String, Level> logLevels;    // effectively final
    private volatile Object engineLoggers;
    private volatile Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier;

    final int contextLength;
    private volatile EngineLimits limits;
    final boolean conservativeContextReferences;
    private final MessageTransport messageInterceptor;
    private volatile int asynchronousStackDepth = 0;

    final SpecializationStatistics specializationStatistics;
    Function<String, TruffleLogger> engineLoggerSupplier;   // effectively final
    private volatile TruffleLogger engineLogger;

    final WeakAssumedValue<PolyglotContextImpl> singleContextValue = new WeakAssumedValue<>("single context");

    @CompilationFinal volatile StableLocalLocations contextLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);
    @CompilationFinal volatile StableLocalLocations contextThreadLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);

    /*
     * Node location to be used when no node is available. In the future this should no longer be
     * necessary as we should have a node for any VM operation.
     */
    @CompilationFinal HostToGuestRootNode uncachedLocation;

    final PolyglotLanguageInstance hostLanguageInstance;
    @CompilationFinal AbstractHostService host; // effectively final
    final boolean hostLanguageOnly;

    @SuppressWarnings("unchecked")
    PolyglotEngineImpl(PolyglotImpl impl, String[] permittedLanguages,
                    DispatchOutputStream out, DispatchOutputStream err, InputStream in, OptionValuesImpl engineOptions,
                    Map<String, Level> logLevels,
                    EngineLoggerProvider engineLogger, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean boundEngine, boolean preInitialization,
                    MessageTransport messageInterceptor, Handler logHandler,
                    TruffleLanguage<Object> hostImpl, boolean hostLanguageOnly) {
        this.messageInterceptor = messageInterceptor;
        this.impl = impl;
        this.permittedLanguages = permittedLanguages;
        this.out = out;
        this.err = err;
        this.in = in;
        this.logHandler = logHandler;
        this.logLevels = logLevels;
        this.boundEngine = boundEngine;
        this.storeEngine = RUNTIME.isStoreEnabled(engineOptions);
        this.hostLanguageOnly = hostLanguageOnly;
        this.hostLanguageInstance = createHostLanguageInstance(hostImpl);

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.contextLength = idToLanguage.values().size() + 1 /* +1 for host language */;

        this.languages = createLanguageStaticIndex();

        Map<String, InstrumentInfo> instrumentInfos = new LinkedHashMap<>();
        this.idToInstrument = Collections.unmodifiableMap(initializeInstruments(instrumentInfos));
        this.idToInternalInstrumentInfo = Collections.unmodifiableMap(instrumentInfos);
        this.runtimeData = RUNTIME.createRuntimeData(engineOptions, engineLogger);

        this.classToLanguage = new HashMap<>();
        for (PolyglotLanguage language : idToLanguage.values()) {
            classToLanguage.put(language.cache.getClassName(), language);
        }

        for (String id : idToLanguage.keySet()) {
            if (idToInstrument.containsKey(id)) {
                throw failDuplicateId(id,
                                idToLanguage.get(id).cache.getClassName(),
                                idToInstrument.get(id).cache.getClassName());
            }
        }

        this.engineLoggerSupplier = engineLogger;
        this.engineOptionValues = engineOptions;

        Map<String, Language> publicLanguages = new LinkedHashMap<>();
        for (String key : this.idToLanguage.keySet()) {
            PolyglotLanguage languageImpl = idToLanguage.get(key);
            if (!languageImpl.cache.isInternal()) {
                publicLanguages.put(key, languageImpl.api);
            }
        }
        this.idToPublicLanguage = Collections.unmodifiableMap(publicLanguages);

        Map<String, Instrument> publicInstruments = new LinkedHashMap<>();
        for (String key : this.idToInstrument.keySet()) {
            PolyglotInstrument instrumentImpl = idToInstrument.get(key);
            if (!instrumentImpl.cache.isInternal()) {
                publicInstruments.put(key, instrumentImpl.api);
            }
        }
        this.idToPublicInstrument = Collections.unmodifiableMap(publicInstruments);
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(this, out, err, in, messageInterceptor, storeEngine);

        if (!boundEngine) {
            initializeMultiContext();
        }
        intitializeStore(false, this.storeEngine);

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(options, languagesOptions, instrumentsOptions);

        this.conservativeContextReferences = engineOptionValues.get(PolyglotEngineOptions.UseConservativeContextReferences);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(this, languagesOptions.get(language), allowExperimentalOptions);
        }

        if (engineOptionValues.get(PolyglotEngineOptions.SpecializationStatistics)) {
            this.specializationStatistics = SpecializationStatistics.create();
        } else {
            this.specializationStatistics = null;
        }

        notifyCreated();

        if (!preInitialization) {
            createInstruments(instrumentsOptions, allowExperimentalOptions);
            registerShutDownHook();
        }
    }

    void ensureUncachedLocationLoaded() {
        assert Thread.holdsLock(lock);
        if (this.uncachedLocation == null) {
            this.uncachedLocation = createUncachedLocation(hostLanguageInstance.spi);
        }
    }

    Node getUncachedLocation() {
        assert uncachedLocation != null : "uncached location not yet initialized";
        return uncachedLocation;
    }

    /**
     * Creates an array of languages that can be used to efficiently access languages using
     * {@link LanguageCache#getStaticIndex()}.
     */
    private PolyglotLanguage[] createLanguageStaticIndex() {
        int maxLanguageStaticId = HOST_LANGUAGE_INDEX;
        for (PolyglotLanguage language : idToLanguage.values()) {
            maxLanguageStaticId = Math.max(maxLanguageStaticId, language.cache.getStaticIndex());
        }
        PolyglotLanguage[] list = new PolyglotLanguage[maxLanguageStaticId + 1];
        list[HOST_LANGUAGE_INDEX] = hostLanguageInstance.language;
        for (PolyglotLanguage language : idToLanguage.values()) {
            assert list[language.cache.getStaticIndex()] == null : "language index used twice";
            list[language.cache.getStaticIndex()] = language;
        }
        return list;
    }

    private static UncachedLocationNode createUncachedLocation(TruffleLanguage<?> hostLanguage) {
        UncachedLocationNode location = new UncachedLocationNode(hostLanguage);
        // this is intended to trigger Truffle runtime initialization in the background
        location.getCallTarget();
        return location;
    }

    private PolyglotLanguageInstance createHostLanguageInstance(TruffleLanguage<Object> hostImpl) {
        PolyglotLanguage language = createLanguage(LanguageCache.createHostLanguageCache(hostImpl), HOST_LANGUAGE_INDEX, null);
        return language.allocateInstance(new OptionValuesImpl(language.getOptionsInternal(), false));
    }

    void notifyCreated() {
        ENGINES.put(this, null);
        RUNTIME.onEngineCreate(this, this.runtimeData);
    }

    PolyglotEngineImpl(PolyglotEngineImpl prototype) {
        this.messageInterceptor = prototype.messageInterceptor;
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(
                        this,
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.out)),
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.err)),
                        prototype.in,
                        prototype.messageInterceptor, prototype.storeEngine);
        this.impl = prototype.impl;
        this.permittedLanguages = prototype.permittedLanguages;
        this.out = prototype.out;
        this.err = prototype.err;
        this.in = prototype.in;
        this.host = prototype.host;
        this.boundEngine = prototype.boundEngine;
        this.logHandler = prototype.logHandler;
        this.runtimeData = RUNTIME.createRuntimeData(prototype.engineOptionValues, prototype.engineLoggerSupplier);
        this.engineLoggerSupplier = prototype.engineLoggerSupplier;

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.hostLanguageOnly = prototype.hostLanguageOnly;
        this.hostLanguageInstance = createHostLanguageInstance(prototype.hostLanguageInstance.spi);
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.contextLength = idToLanguage.size() + 1 /* +1 for host language */;

        this.languages = createLanguageStaticIndex();

        Map<String, InstrumentInfo> instrumentInfos = new LinkedHashMap<>();
        this.idToInstrument = Collections.unmodifiableMap(initializeInstruments(instrumentInfos));
        this.idToInternalInstrumentInfo = Collections.unmodifiableMap(instrumentInfos);
        this.uncachedLocation = createUncachedLocation(hostLanguageInstance.spi);

        this.classToLanguage = new HashMap<>();
        for (PolyglotLanguage language : idToLanguage.values()) {
            classToLanguage.put(language.cache.getClassName(), language);
        }

        for (String id : idToLanguage.keySet()) {
            if (idToInstrument.containsKey(id)) {
                throw failDuplicateId(id,
                                idToLanguage.get(id).cache.getClassName(),
                                idToInstrument.get(id).cache.getClassName());
            }
        }

        Map<String, Language> publicLanguages = new LinkedHashMap<>();
        for (String key : this.idToLanguage.keySet()) {
            PolyglotLanguage languageImpl = idToLanguage.get(key);
            if (!languageImpl.cache.isInternal()) {
                publicLanguages.put(key, languageImpl.api);
            }
        }
        idToPublicLanguage = Collections.unmodifiableMap(publicLanguages);

        Map<String, Instrument> publicInstruments = new LinkedHashMap<>();
        for (String key : this.idToInstrument.keySet()) {
            PolyglotInstrument instrumentImpl = idToInstrument.get(key);
            if (!instrumentImpl.cache.isInternal()) {
                publicInstruments.put(key, instrumentImpl.api);
            }
        }
        idToPublicInstrument = Collections.unmodifiableMap(publicInstruments);
        logLevels = prototype.logLevels;

        this.engineOptionValues = prototype.engineOptionValues.copy();
        this.conservativeContextReferences = engineOptionValues.get(PolyglotEngineOptions.UseConservativeContextReferences);

        if (!boundEngine) {
            initializeMultiContext();
        }
        intitializeStore(false, prototype.storeEngine);

        for (String languageId : idToLanguage.keySet()) {
            OptionValuesImpl prototypeOptions = prototype.idToLanguage.get(languageId).getOptionValuesIfExists();
            if (prototypeOptions != null) {
                prototypeOptions.copyInto(idToLanguage.get(languageId).getOptionValues());
            }
        }

        if (this.engineOptionValues.get(PolyglotEngineOptions.SpecializationStatistics)) {
            this.specializationStatistics = SpecializationStatistics.create();
        } else {
            this.specializationStatistics = null;
        }

        Collection<PolyglotInstrument> instrumentsToCreate = new ArrayList<>();
        for (String instrumentId : idToInstrument.keySet()) {
            OptionValuesImpl prototypeOptions = prototype.idToInstrument.get(instrumentId).getOptionValuesIfExists();
            if (prototypeOptions != null) {
                PolyglotInstrument instrument = idToInstrument.get(instrumentId);
                prototypeOptions.copyInto(instrument.getEngineOptionValues());
                instrumentsToCreate.add(instrument);
            }
        }
        this.api = getAPIAccess().newEngine(impl.engineDispatch, this);

        ensureInstrumentsCreated(instrumentsToCreate);
        registerShutDownHook();
        notifyCreated();
    }

    TruffleLogger getEngineLogger() {
        TruffleLogger result = this.engineLogger;
        if (result == null) {
            synchronized (this.lock) {
                result = this.engineLogger;
                if (result == null) {
                    result = this.engineLoggerSupplier.apply(OPTION_GROUP_ENGINE);
                    Object logger = EngineAccessor.LANGUAGE.getLoggerCache(result);
                    LoggerCache loggerCache = (LoggerCache) EngineAccessor.LANGUAGE.getLoggersSPI(logger);
                    loggerCache.setOwner(this);
                    if (!logLevels.isEmpty()) {
                        EngineAccessor.LANGUAGE.configureLoggers(this, logLevels, logger);
                    }
                    this.engineLogger = result;
                }
            }
        }
        return result;
    }

    Object getOrCreateEngineLoggers() {
        Object res = engineLoggers;
        if (res == null) {
            synchronized (this.lock) {
                res = engineLoggers;
                if (res == null) {
                    LoggerCache loggerCache = PolyglotLoggers.LoggerCache.newEngineLoggerCache(this);
                    loggerCache.setOwner(this);
                    res = LANGUAGE.createEngineLoggers(loggerCache);
                    if (!logLevels.isEmpty()) {
                        EngineAccessor.LANGUAGE.configureLoggers(this, logLevels, res);
                    }
                    for (ContextWeakReference contextRef : contexts) {
                        PolyglotContextImpl context = contextRef.get();
                        if (context != null && !context.config.logLevels.isEmpty()) {
                            LANGUAGE.configureLoggers(context, context.config.logLevels, res);
                        }
                    }
                    engineLoggers = res;
                }
            }
        }
        return res;
    }

    static OptionDescriptors createEngineOptionDescriptors() {
        OptionDescriptors engineOptionDescriptors = new PolyglotEngineOptionsOptionDescriptors();
        OptionDescriptors compilerOptionDescriptors = EngineAccessor.RUNTIME.getEngineOptionDescriptors();
        return OptionDescriptors.createUnion(engineOptionDescriptors, compilerOptionDescriptors);
    }

    static Collection<Object> findActiveEngines() {
        synchronized (ENGINES) {
            List<Object> engines = new ArrayList<>(ENGINES.size());
            for (PolyglotEngineImpl engine : ENGINES.keySet()) {
                engines.add(engine.api);
            }
            return engines;
        }
    }

    void patch(DispatchOutputStream newOut,
                    DispatchOutputStream newErr,
                    InputStream newIn,
                    OptionValuesImpl engineOptions,
                    LogConfig newLogConfig,
                    EngineLoggerProvider logSupplier,
                    Map<String, String> newOptions,
                    boolean newAllowExperimentalOptions,
                    boolean newBoundEngine, Handler newLogHandler) {
        CompilerAsserts.neverPartOfCompilation();
        this.out = newOut;
        this.err = newErr;
        this.in = newIn;
        boolean wasBound = this.boundEngine;
        this.boundEngine = newBoundEngine;
        this.logHandler = newLogHandler;
        this.engineOptionValues = engineOptions;
        this.logLevels = newLogConfig.logLevels;
        boolean wasStore = this.storeEngine;
        this.storeEngine = RUNTIME.isStoreEnabled(engineOptions);
        this.engineLoggerSupplier = logSupplier;
        this.engineLogger = null;

        intitializeStore(wasStore, storeEngine);

        if (wasBound && !newBoundEngine) {
            initializeMultiContext();
        }

        INSTRUMENT.patchInstrumentationHandler(instrumentationHandler, newOut, newErr, newIn);

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(newOptions, languagesOptions, instrumentsOptions);

        RUNTIME.onEnginePatch(this.runtimeData, engineOptions, logSupplier);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(this, languagesOptions.get(language), newAllowExperimentalOptions);
        }

        // Set instruments options but do not call onCreate. OnCreate is called only in case of
        // successful context patch.
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getEngineOptionValues().putAll(this, instrumentsOptions.get(instrument), newAllowExperimentalOptions);
        }
        registerShutDownHook();
    }

    static Handler createLogHandler(LogConfig logConfig, DispatchOutputStream errDispatchOutputStream) {
        if (logConfig.logFile != null) {
            if (ALLOW_IO) {
                return PolyglotLoggers.getFileHandler(logConfig.logFile);
            } else {
                throw PolyglotEngineException.illegalState("The `log.file` option is not allowed when the allowIO() privilege is removed at image build time.");
            }
        } else {
            return PolyglotLoggers.createDefaultHandler(INSTRUMENT.getOut(errDispatchOutputStream));
        }
    }

    private void createInstruments(Map<PolyglotInstrument, Map<String, String>> instrumentsOptions, boolean allowExperimentalOptions) {
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getEngineOptionValues().putAll(this, instrumentsOptions.get(instrument), allowExperimentalOptions);
        }
        ensureInstrumentsCreated(instrumentsOptions.keySet());
    }

    static void ensureInstrumentsCreated(Collection<? extends PolyglotInstrument> instruments) {
        for (PolyglotInstrument instrument : instruments) {
            // we got options for this instrument -> create it.
            instrument.ensureCreated();
        }
    }

    private static void registerShutDownHook() {
        if (!shutdownHookInitialized) {
            synchronized (ENGINES) {
                if (!shutdownHookInitialized) {
                    shutdownHookInitialized = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(new PolyglotShutDownHook()));
                }
            }
        }
    }

    void initializeMultiContext() {
        synchronized (this.lock) {
            if (singleContext.isValid()) {
                singleContext.invalidate();
                singleContextValue.invalidate();
            }
        }
    }

    static void parseEngineOptions(Map<String, String> allOptions, Map<String, String> engineOptions, LogConfig logOptions) {
        Iterator<Entry<String, String>> iterator = allOptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            String key = entry.getKey();
            String value = entry.getValue();
            String group = parseOptionGroup(key);
            if (group.equals(OPTION_GROUP_ENGINE)) {
                engineOptions.put(entry.getKey(), entry.getValue());
                iterator.remove();
                continue;
            }
            if (group.equals(OPTION_GROUP_LOG)) {
                if (LOG_FILE_OPTION.equals(key)) {
                    logOptions.logFile = value;
                } else {
                    logOptions.logLevels.put(parseLoggerName(key), Level.parse(value));
                }
                iterator.remove();
                continue;
            }
        }
    }

    private void parseOptions(Map<String, String> options,
                    Map<PolyglotLanguage, Map<String, String>> languagesOptions,
                    Map<PolyglotInstrument, Map<String, String>> instrumentsOptions) {
        for (String key : options.keySet()) {
            String group = parseOptionGroup(key);
            String value = options.get(key);
            PolyglotLanguage language = idToLanguage.get(group);
            if (language != null && !language.cache.isInternal()) {
                Map<String, String> languageOptions = languagesOptions.get(language);
                if (languageOptions == null) {
                    languageOptions = new HashMap<>();
                    languagesOptions.put(language, languageOptions);
                }
                languageOptions.put(key, value);
                continue;
            }
            PolyglotInstrument instrument = idToInstrument.get(group);
            if (instrument != null && !instrument.cache.isInternal()) {
                Map<String, String> instrumentOptions = instrumentsOptions.get(instrument);
                if (instrumentOptions == null) {
                    instrumentOptions = new HashMap<>();
                    instrumentsOptions.put(instrument, instrumentOptions);
                }
                instrumentOptions.put(key, value);
                continue;
            }

            switch (group) {
                case OPTION_GROUP_ENGINE:
                case OPTION_GROUP_LOG:
                    throw new AssertionError("Log or engine options should already be parsed.");
                case OPTION_GROUP_IMAGE_BUILD_TIME:
                    throw PolyglotEngineException.illegalArgument("Image build-time option '" + key + "' cannot be set at runtime");
            }
            throw OptionValuesImpl.failNotFound(getAllOptions(), key);
        }
    }

    static Map<String, String> readOptionsFromSystemProperties(Map<String, String> options) {
        Properties properties = System.getProperties();
        Map<String, String> newOptions = null;
        synchronized (properties) {
            for (Object systemKey : properties.keySet()) {
                if (PolyglotImpl.PROP_ALLOW_EXPERIMENTAL_OPTIONS.equals(systemKey)) {
                    continue;
                }
                String key = (String) systemKey;
                if (key.startsWith(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX)) {
                    final String optionKey = key.substring(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX.length());
                    // Image build time options are not set in runtime options
                    if (!optionKey.startsWith(OPTION_GROUP_IMAGE_BUILD_TIME)) {
                        // system properties cannot override existing options
                        if (!options.containsKey(optionKey)) {
                            if (newOptions == null) {
                                newOptions = new HashMap<>(options);
                            }
                            newOptions.put(optionKey, System.getProperty(key));
                        }
                    }
                }
            }
        }
        if (newOptions == null) {
            return options;
        } else {
            return newOptions;
        }
    }

    static String parseOptionGroup(String key) {
        int groupIndex = key.indexOf('.');
        String group;
        if (groupIndex != -1) {
            group = key.substring(0, groupIndex);
        } else {
            group = key;
        }
        return group;
    }

    static String parseLoggerName(String optionKey) {
        final String prefix = "log.";
        final String suffix = ".level";
        if (!optionKey.startsWith(prefix) || !optionKey.endsWith(suffix)) {
            throw PolyglotEngineException.illegalArgument(optionKey);
        }
        final int start = prefix.length();
        final int end = optionKey.length() - suffix.length();
        return start < end ? optionKey.substring(start, end) : "";
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return this;
    }

    PolyglotLanguage findLanguage(PolyglotLanguageContext accessingLanguage, String languageId, String mimeType, boolean failIfNotFound, boolean allowInternalAndDependent) {
        assert languageId != null || mimeType != null : Objects.toString(languageId) + ", " + Objects.toString(mimeType);

        Map<String, LanguageInfo> languageMap;
        if (accessingLanguage != null) {
            languageMap = accessingLanguage.getAccessibleLanguages(allowInternalAndDependent);
        } else {
            assert allowInternalAndDependent : "non internal access is not yet supported for instrument lookups";
            languageMap = this.idToInternalLanguageInfo;
        }

        LanguageInfo foundLanguage = null;
        if (languageId != null) {
            foundLanguage = languageMap.get(languageId);
        }
        if (mimeType != null && foundLanguage == null) {
            // we need to interpret mime types for compatibility.
            foundLanguage = languageMap.get(mimeType);
            if (foundLanguage == null) {
                for (LanguageInfo searchLanguage : languageMap.values()) {
                    if (searchLanguage.getMimeTypes().contains(mimeType)) {
                        foundLanguage = searchLanguage;
                        break;
                    }
                }
            }
        }

        assert allowInternalAndDependent || foundLanguage == null || (!foundLanguage.isInternal() && accessingLanguage.isPolyglotEvalAllowed(languageId));

        if (foundLanguage != null) {
            return (PolyglotLanguage) EngineAccessor.NODES.getPolyglotLanguage(foundLanguage);
        }

        if (failIfNotFound) {
            if (languageId != null) {
                Set<String> ids = new LinkedHashSet<>();
                for (LanguageInfo language : languageMap.values()) {
                    ids.add(language.getId());
                }
                throw PolyglotEngineException.illegalState("No language for id " + languageId + " found. Supported languages are: " + ids);
            } else {
                Set<String> mimeTypes = new LinkedHashSet<>();
                for (LanguageInfo language : languageMap.values()) {
                    mimeTypes.addAll(language.getMimeTypes());
                }
                throw PolyglotEngineException.illegalState("No language for MIME type " + mimeType + " found. Supported languages are: " + mimeTypes);
            }
        } else {
            return null;
        }
    }

    private Map<String, PolyglotInstrument> initializeInstruments(Map<String, InstrumentInfo> infos) {
        if (hostLanguageOnly) {
            return Collections.emptyMap();
        }
        Map<String, PolyglotInstrument> instruments = new LinkedHashMap<>();
        List<InstrumentCache> cachedInstruments = InstrumentCache.load();
        for (InstrumentCache instrumentCache : cachedInstruments) {
            PolyglotInstrument instrumentImpl = new PolyglotInstrument(this, instrumentCache);
            instrumentImpl.info = LANGUAGE.createInstrument(instrumentImpl, instrumentCache.getId(), instrumentCache.getName(), instrumentCache.getVersion());
            Instrument instrument = impl.getAPIAccess().newInstrument(impl.instrumentDispatch, instrumentImpl);
            instrumentImpl.api = instrument;

            String id = instrumentImpl.cache.getId();
            verifyId(id, instrumentCache.getClassName());
            if (instruments.containsKey(id)) {
                throw failDuplicateId(id, instrumentImpl.cache.getClassName(), instruments.get(id).cache.getClassName());
            }
            instruments.put(id, instrumentImpl);
            infos.put(id, instrumentImpl.info);
        }
        return instruments;
    }

    private Map<String, PolyglotLanguage> initializeLanguages(Map<String, LanguageInfo> infos) {
        if (hostLanguageOnly) {
            return Collections.emptyMap();
        }
        Map<String, PolyglotLanguage> polyglotLanguages = new LinkedHashMap<>();
        Map<String, LanguageCache> cachedLanguages = new HashMap<>();
        List<LanguageCache> sortedLanguages = new ArrayList<>();
        for (LanguageCache lang : LanguageCache.languages().values()) {
            String id = lang.getId();
            if (!cachedLanguages.containsKey(id)) {
                sortedLanguages.add(lang);
                cachedLanguages.put(id, lang);
            }
        }
        Collections.sort(sortedLanguages);

        LinkedHashSet<LanguageCache> serializedLanguages = new LinkedHashSet<>();
        Set<String> languageReferences = new HashSet<>();
        Map<String, RuntimeException> initErrors = new HashMap<>();

        for (LanguageCache language : sortedLanguages) {
            languageReferences.addAll(language.getDependentLanguages());
        }

        // visit / initialize internal languages first to model the implicit
        // dependency of every public language to every internal language
        for (LanguageCache language : sortedLanguages) {
            if (language.isInternal() && !languageReferences.contains(language.getId())) {
                visitLanguage(initErrors, cachedLanguages, serializedLanguages, language);
            }
        }

        for (LanguageCache language : sortedLanguages) {
            if (!language.isInternal() && !languageReferences.contains(language.getId())) {
                visitLanguage(initErrors, cachedLanguages, serializedLanguages, language);
            }
        }

        int index = 1;
        for (LanguageCache cache : serializedLanguages) {
            PolyglotLanguage languageImpl = createLanguage(cache, index, initErrors.get(cache.getId()));

            String id = languageImpl.cache.getId();
            verifyId(id, cache.getClassName());
            if (polyglotLanguages.containsKey(id)) {
                throw failDuplicateId(id, languageImpl.cache.getClassName(), polyglotLanguages.get(id).cache.getClassName());
            }
            polyglotLanguages.put(id, languageImpl);
            infos.put(id, languageImpl.info);
            index++;
        }
        return polyglotLanguages;
    }

    private void visitLanguage(Map<String, RuntimeException> initErrors, Map<String, LanguageCache> cachedLanguages, LinkedHashSet<LanguageCache> serializedLanguages,
                    LanguageCache language) {
        visitLanguageImpl(new HashSet<>(), initErrors, cachedLanguages, serializedLanguages, language);
    }

    private void visitLanguageImpl(Set<String> visitedIds, Map<String, RuntimeException> initErrors, Map<String, LanguageCache> cachedLanguages, LinkedHashSet<LanguageCache> serializedLanguages,
                    LanguageCache language) {
        Set<String> dependencies = language.getDependentLanguages();
        for (String dependency : dependencies) {
            LanguageCache dependentLanguage = cachedLanguages.get(dependency);
            if (dependentLanguage == null) {
                // dependent languages are optional
                continue;
            }
            if (visitedIds.contains(dependency)) {
                initErrors.put(language.getId(), PolyglotEngineException.illegalState("Illegal cyclic language dependency found:" + language.getId() + " -> " + dependency));
                continue;
            }
            visitedIds.add(dependency);
            visitLanguageImpl(visitedIds, initErrors, cachedLanguages, serializedLanguages, dependentLanguage);
            visitedIds.remove(dependency);
        }
        serializedLanguages.add(language);
    }

    private PolyglotLanguage createLanguage(LanguageCache cache, int index, RuntimeException initError) {
        PolyglotLanguage languageImpl = new PolyglotLanguage(this, cache, index, index == HOST_LANGUAGE_INDEX, initError);
        Language language = impl.getAPIAccess().newLanguage(impl.languageDispatch, languageImpl);
        languageImpl.api = language;
        return languageImpl;
    }

    private static void verifyId(String id, String className) {
        if (RESERVED_IDS.contains(id)) {
            throw new IllegalStateException(String.format("The language or instrument with class '%s' uses a reserved id '%s'. " +
                            "Resolve this by using a not reserved id for the language or instrument. " +
                            "The following ids are reserved %s for internal use.",
                            className, id, RESERVED_IDS));
        } else if (id.contains(".")) {
            throw new IllegalStateException(String.format("The language '%s' must not contain a period in its id '%s'. " +
                            "Remove all periods from the id to resolve this issue. ",
                            className, id));
        }
    }

    private static RuntimeException failDuplicateId(String duplicateId, String className1, String className2) {
        return new IllegalStateException(
                        String.format("Duplicate id '%s' specified by language or instrument with class '%s' and '%s'. " +
                                        "Resolve this by specifying a unique id for each language or instrument.",
                                        duplicateId, className1, className2));
    }

    void checkState() {
        if (closed) {
            throw PolyglotEngineException.illegalState("Engine is already closed.");
        }
    }

    void addContext(PolyglotContextImpl context) {
        assert Thread.holdsLock(this.lock);

        ensureUncachedLocationLoaded();

        if (limits != null) {
            limits.validate(context.config.limits);
        }
        workContextReferenceQueue();
        contexts.add(context.weakReference);

        if (context.config.limits != null) {
            EngineLimits l = limits;
            if (l == null) {
                limits = l = new EngineLimits(this);
            }
            l.initialize(context.config.limits, context);
        }

        if (context.config.hostClassLoader != null) {
            context.engine.customHostClassLoader.invalidate();
        }

        singleContextValue.update(context);
    }

    void removeContext(PolyglotContextImpl context) {
        assert Thread.holdsLock(this.lock) : "Must hold PolyglotEngineImpl.lock";
        contexts.remove(context.weakReference);
        workContextReferenceQueue();
    }

    void disposeContext(PolyglotContextImpl context) {
        synchronized (this.lock) {
            // should never be remove twice
            assert !context.weakReference.removed;
            context.weakReference.removed = true;
            context.weakReference.freeInstances.clear();
            removeContext(context);
        }
    }

    private void workContextReferenceQueue() {
        Reference<?> ref;
        while ((ref = contextsReferenceQueue.poll()) != null) {
            ContextWeakReference contextRef = (ContextWeakReference) ref;
            if (!contextRef.removed) {
                for (PolyglotLanguageInstance instance : contextRef.freeInstances) {
                    instance.language.freeInstance(instance);
                }
                contextRef.freeInstances.clear();
                contexts.remove(contextRef);
                contextRef.removed = true;
            }
        }
    }

    void reportAllLanguageContexts(ContextsListener listener) {
        List<PolyglotContextImpl> allContexts;
        synchronized (this.lock) {
            if (contexts.isEmpty()) {
                return;
            }
            allContexts = collectAliveContexts();
        }
        for (PolyglotContextImpl context : allContexts) {
            listener.onContextCreated(context.creatorTruffleContext);
            for (PolyglotLanguageContext lc : context.contexts) {
                LanguageInfo language = lc.language.info;
                if (lc.eventsEnabled && lc.env != null) {
                    listener.onLanguageContextCreate(context.creatorTruffleContext, language);
                    listener.onLanguageContextCreated(context.creatorTruffleContext, language);
                    if (lc.isInitialized()) {
                        listener.onLanguageContextInitialize(context.creatorTruffleContext, language);
                        listener.onLanguageContextInitialized(context.creatorTruffleContext, language);
                        if (lc.finalized) {
                            listener.onLanguageContextFinalized(context.creatorTruffleContext, language);
                        }
                    }
                }
            }
        }
    }

    void reportAllContextThreads(ThreadsListener listener) {
        List<PolyglotContextImpl> allContexts;
        synchronized (this.lock) {
            if (contexts.isEmpty()) {
                return;
            }
            allContexts = collectAliveContexts();
        }
        for (PolyglotContextImpl context : allContexts) {
            Thread[] threads;
            synchronized (context) {
                threads = context.getSeenThreads().keySet().toArray(new Thread[0]);
            }
            for (Thread thread : threads) {
                listener.onThreadInitialized(context.creatorTruffleContext, thread);
            }
        }
    }

    PolyglotLanguage requireLanguage(String id, boolean allowInternal) {
        checkState();
        PolyglotLanguage language = idToLanguage.get(id);
        if (language == null || (!allowInternal && !idToPublicLanguage.containsKey(id))) {
            throw throwNotInstalled(id, idToLanguage.keySet());
        }
        return language;
    }

    private static RuntimeException throwNotInstalled(String id, Set<String> allLanguages) {
        String misspelledGuess = matchSpellingError(allLanguages, id);
        String didYouMean = "";
        if (misspelledGuess != null) {
            didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
        }
        throw PolyglotEngineException.illegalArgument(String.format("A language with id '%s' is not installed. %sInstalled languages are: %s.", id, didYouMean, allLanguages));
    }

    public Language requirePublicLanguage(String id) {
        checkState();
        Language language = idToPublicLanguage.get(id);
        if (language == null) {
            throw throwNotInstalled(id, idToPublicLanguage.keySet());
        }
        return language;
    }

    private static String matchSpellingError(Set<String> allIds, String enteredId) {
        String lowerCaseEnteredId = enteredId.toLowerCase();
        for (String id : allIds) {
            if (id.toLowerCase().equals(lowerCaseEnteredId)) {
                return id;
            }
        }
        return null;
    }

    public Instrument requirePublicInstrument(String id) {
        checkState();
        Instrument instrument = idToPublicInstrument.get(id);
        if (instrument == null) {
            String misspelledGuess = matchSpellingError(idToPublicInstrument.keySet(), id);
            String didYouMean = "";
            if (misspelledGuess != null) {
                didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
            }
            throw PolyglotEngineException.illegalState(String.format("An instrument with id '%s' is not installed. %sInstalled instruments are: %s.", id, didYouMean, getInstruments().keySet()));
        }
        return instrument;
    }

    @TruffleBoundary
    <T extends TruffleLanguage<?>> PolyglotLanguage getLanguage(Class<T> languageClass, boolean fail) {
        PolyglotLanguage foundLanguage = classToLanguage.get(languageClass.getName());
        if (foundLanguage == null) {
            if (languageClass == hostLanguageInstance.spi.getClass()) {
                return hostLanguageInstance.language;
            }
            if (fail) {
                Set<String> languageNames = classToLanguage.keySet();
                throw PolyglotEngineException.illegalArgument("Cannot find language " + languageClass + " among " + languageNames);
            }
        }
        return foundLanguage;
    }

    void ensureClosed(boolean force, boolean inShutdownHook) {
        synchronized (this.lock) {
            if (!closed) {
                workContextReferenceQueue();
                List<PolyglotContextImpl> localContexts = collectAliveContexts();
                /*
                 * Check ahead of time for open contexts to fail early and avoid closing only some
                 * contexts.
                 */
                if (!inShutdownHook) {
                    if (!force) {
                        for (PolyglotContextImpl context : localContexts) {
                            assert !Thread.holdsLock(context);
                            synchronized (context) {
                                if (context.hasActiveOtherThread(false) && !context.state.isClosing()) {
                                    throw PolyglotEngineException.illegalState(String.format("One of the context instances is currently executing. " +
                                                    "Set cancelIfExecuting to true to stop the execution on this thread."));
                                }
                            }
                        }
                    }
                    for (PolyglotContextImpl context : localContexts) {
                        assert !Thread.holdsLock(context);
                        if (force) {
                            context.cancel(false, null);
                        } else {
                            boolean closeCompleted = context.closeImpl(true);
                            if (!closeCompleted) {
                                throw PolyglotEngineException.illegalState(String.format("One of the context instances is currently executing. " +
                                                "Set cancelIfExecuting to true to stop the execution on this thread."));
                            }
                            context.finishCleanup();
                            context.checkSubProcessFinished();
                        }
                    }
                }

                // don't commit changes to contexts if still running
                if (!inShutdownHook) {
                    contexts.clear();

                    if (RUNTIME.onEngineClosing(this.runtimeData)) {
                        return;
                    }
                }

                // instruments should be shut-down even if they are currently still executed
                // we want to see instrument output if the process is quit while executing.
                for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                    try {
                        instrumentImpl.notifyClosing();
                    } catch (Throwable e) {
                        if (!inShutdownHook) {
                            throw e;
                        }
                    }
                }
                for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                    try {
                        instrumentImpl.ensureClosed();
                    } catch (Throwable e) {
                        if (!inShutdownHook) {
                            throw e;
                        }
                    }
                }

                if (specializationStatistics != null) {
                    StringWriter logMessage = new StringWriter();
                    try (PrintWriter writer = new PrintWriter(logMessage)) {
                        if (!specializationStatistics.hasData()) {
                            writer.printf("No specialization statistics data was collected. Either no node with @%s annotations was executed or " +
                                            "the interpreter was not compiled with -J-Dtruffle.dsl.GenerateSpecializationStatistics=true e.g as parameter to the javac tool.",
                                            Specialization.class.getSimpleName());
                        } else {
                            specializationStatistics.printHistogram(writer);
                        }
                    }
                    getEngineLogger().log(Level.INFO, String.format("Specialization histogram: %n%s", logMessage.toString()));
                }

                if (!inShutdownHook) {
                    RUNTIME.onEngineClosed(this.runtimeData);

                    Object loggers = getEngineLoggers();
                    if (loggers != null) {
                        LANGUAGE.closeEngineLoggers(loggers);
                    }
                    if (logHandler != null) {
                        logHandler.close();
                    }
                    closed = true;
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        language.close();
                    }
                    if (runtimeData != null) {
                        EngineAccessor.RUNTIME.flushCompileQueue(runtimeData);
                    }
                    ENGINES.remove(this);
                } else if (logHandler != null) {
                    // called from shutdown hook, at least flush the logging handler
                    logHandler.flush();
                }
            }
        }
    }

    List<PolyglotContextImpl> collectAliveContexts() {
        assert Thread.holdsLock(this.lock);
        List<PolyglotContextImpl> localContexts = new ArrayList<>(contexts.size());
        for (ContextWeakReference ref : contexts) {
            PolyglotContextImpl context = ref.get();
            if (context != null) {
                localContexts.add(context);
            } else {
                contexts.remove(ref);
            }
        }
        return localContexts;
    }

    public Map<String, Instrument> getInstruments() {
        checkState();
        return idToPublicInstrument;
    }

    public Map<String, Language> getLanguages() {
        checkState();
        return idToPublicLanguage;
    }

    public OptionDescriptors getOptions() {
        checkState();
        return engineOptionValues.getDescriptors();
    }

    public Set<Source> getCachedSources() {
        checkState();
        Set<Source> sources = new HashSet<>();
        List<PolyglotContextImpl> activeContexts;
        synchronized (lock) {
            activeContexts = collectAliveContexts();
        }
        for (PolyglotContextImpl context : activeContexts) {
            for (PolyglotLanguageContext language : context.contexts) {
                PolyglotLanguageInstance instance = language.getLanguageInstanceOrNull();
                if (instance != null) {
                    instance.listCachedSources(sources);
                }
            }
        }
        synchronized (lock) {
            for (PolyglotLanguage language : idToLanguage.values()) {
                for (PolyglotLanguageInstance instance : language.getInstancePool()) {
                    instance.listCachedSources(sources);
                }
            }
        }
        return sources;
    }

    Collection<CallTarget> getCallTargets() {
        return INSTRUMENT.getLoadedCallTargets(instrumentationHandler);
    }

    OptionDescriptors getAllOptions() {
        checkState();
        if (allOptions == null) {
            synchronized (this.lock) {
                if (allOptions == null) {
                    List<OptionDescriptors> allDescriptors = new ArrayList<>();
                    allDescriptors.add(engineOptionValues.getDescriptors());
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        allDescriptors.add(language.getOptionsInternal());
                    }
                    for (PolyglotInstrument instrument : idToInstrument.values()) {
                        allDescriptors.add(instrument.getAllOptionsInternal());
                    }
                    allOptions = OptionDescriptors.createUnion(allDescriptors.toArray(new OptionDescriptors[0]));
                }
            }
        }
        return allOptions;
    }

    PolyglotContextImpl getPreInitializedContext() {
        return preInitializedContext.get();
    }

    void preInitialize() {
        synchronized (this.lock) {
            this.preInitializedContext.set(PolyglotContextImpl.preInitialize(this));
        }
    }

    /**
     * Invoked when the engine will be stored. This must be executed before any guest language code
     * is executed.
     */
    void intitializeStore(boolean previousStore, boolean newStore) {
        if (newStore) {
            if (previousStore && boundEngine && singleContext.isValid()) {
                // multi-context already initialized, just the assumption was flipped by
                // finalizeStore
                singleContext.invalidate();
                singleContextValue.invalidate();
            } else {
                initializeMultiContext();
            }
        }
    }

    /**
     * Invoked when the context is closing to prepare an engine to be stored.
     */
    void finalizeStore() {
        assert Thread.holdsLock(this.lock);

        this.out = null;
        this.err = null;
        this.in = null;
        this.logHandler = null;

        INSTRUMENT.finalizeStoreInstrumentationHandler(instrumentationHandler);

        /*
         * If we store an engine we force initialize multi context to avoid language to do any
         * context related references in the AST, but after, at least for context bound engines we
         * can restore the single context assumption.
         */
        if (storeEngine && boundEngine && !singleContext.isValid()) {
            singleContext = Truffle.getRuntime().createAssumption("Single context after preinitialization.");
        }

        // much more things should be done here, like trying to use single context references
        // for stored engines and then patch them later on.
    }

    /**
     * Clears the pre-initialized engines. The TruffleBaseFeature needs to clean emitted engines
     * during Feature.cleanup.
     */
    static void resetPreInitializedEngine() {
        ENGINES.clear();
    }

    @TruffleBoundary
    int getAsynchronousStackDepth() {
        return asynchronousStackDepth;
    }

    @TruffleBoundary
    void setAsynchronousStackDepth(PolyglotInstrument polyglotInstrument, int depth) {
        assert depth >= 0 : String.format("Wrong depth: %d", depth);
        int newDepth = 0;
        synchronized (this.lock) {
            polyglotInstrument.requestedAsyncStackDepth = depth;
            for (PolyglotInstrument instrument : idToInstrument.values()) {
                if (instrument.requestedAsyncStackDepth > newDepth) {
                    newDepth = instrument.requestedAsyncStackDepth;
                }
            }
        }
        asynchronousStackDepth = newDepth;
    }

    private static final class PolyglotShutDownHook implements Runnable {

        public void run() {
            PolyglotEngineImpl[] engines;
            synchronized (ENGINES) {
                engines = ENGINES.keySet().toArray(new PolyglotEngineImpl[0]);
            }
            for (PolyglotEngineImpl engine : engines) {
                if (DEBUG_MISSING_CLOSE) {
                    PrintStream out = System.out;
                    out.println("Missing close on vm shutdown: ");
                    out.print(" InitializedLanguages:");
                    synchronized (engine.lock) {
                        for (PolyglotContextImpl context : engine.collectAliveContexts()) {
                            for (PolyglotLanguageContext langContext : context.contexts) {
                                if (langContext.env != null) {
                                    out.print(langContext.language.getId());
                                    out.print(", ");
                                }
                            }
                        }
                    }
                    out.println();
                    engine.createdLocation.printStackTrace();
                }
                if (engine != null) {
                    engine.ensureClosed(false, true);
                }
            }
        }
    }

    static void cancelOrExit(PolyglotContextImpl context, List<Future<Void>> cancelationFutures) {
        cancelOrExitOrInterrupt(context, cancelationFutures, 0, null);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    static boolean cancelOrExitOrInterrupt(PolyglotContextImpl context, List<Future<Void>> futures, long startMillis, Duration timeout) {
        try {
            synchronized (context) {
                assert context.singleThreaded || !context.isActive(Thread.currentThread()) : "Cancel while entered is only allowed for single-threaded contexts!";
                context.sendInterrupt();
            }
            if (timeout == null) {
                boolean closeCompleted = context.closeImpl(true);
                assert closeCompleted : "Close was not completed!";
            } else {
                return waitForThreads(context, startMillis, timeout);
            }
        } finally {
            for (Future<Void> future : futures) {
                boolean timedOut = false;
                try {
                    if (timeout != null) {
                        long timeElapsed = System.currentTimeMillis() - startMillis;
                        long timeoutMillis = timeout.toMillis();
                        if (timeElapsed < timeoutMillis) {
                            try {
                                future.get(timeoutMillis - timeElapsed, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException te) {
                                timedOut = true;
                            }
                        } else {
                            timedOut = true;
                        }
                    } else {
                        future.get();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                }
                if (timedOut) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean waitForThreads(PolyglotContextImpl context, long startMillis, Duration timeout) {
        long cancelTimeoutMillis = timeout != Duration.ZERO ? timeout.toMillis() : 0;
        boolean success = true;
        if (!context.waitForThreads(startMillis, cancelTimeoutMillis)) {
            success = false;
        }
        return success;
    }

    @SuppressWarnings("serial")
    static final class CancelExecution extends ThreadDeath {

        private final Node location;
        private final SourceSection sourceSection;
        private final String cancelMessage;
        private final boolean resourceLimit;

        CancelExecution(Node location, String cancelMessage, boolean resourceLimit) {
            this(location, null, cancelMessage, resourceLimit);
        }

        CancelExecution(SourceSection sourceSection, String cancelMessage, boolean resourceLimit) {
            this(null, sourceSection, cancelMessage, resourceLimit);
        }

        private CancelExecution(Node location, SourceSection sourceSection, String cancelMessage, boolean resourceLimit) {
            this.location = location;
            this.sourceSection = sourceSection;
            this.cancelMessage = cancelMessage;
            this.resourceLimit = resourceLimit;
        }

        Node getLocation() {
            return location;
        }

        SourceSection getSourceLocation() {
            if (sourceSection != null) {
                return sourceSection;
            }
            return location == null ? null : location.getEncapsulatingSourceSection();
        }

        public boolean isResourceLimit() {
            return resourceLimit;
        }

        @Override
        public String getMessage() {
            if (cancelMessage == null) {
                return "Execution got cancelled.";
            } else {
                return cancelMessage;
            }
        }

    }

    @ExportLibrary(InteropLibrary.class)
    static final class InterruptExecution extends AbstractTruffleException {

        private static final long serialVersionUID = 8652484189010224048L;

        private final SourceSection sourceSection;

        InterruptExecution(Node location) {
            this(location, null);
        }

        InterruptExecution(SourceSection sourceSection) {
            this(null, sourceSection);
        }

        private InterruptExecution(Node location, SourceSection sourceSection) {
            super("Execution got interrupted.", location);
            this.sourceSection = sourceSection;
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.INTERRUPT;
        }

        @ExportMessage
        public boolean hasSourceLocation() {
            if (sourceSection != null) {
                return true;
            }
            Node location = getLocation();
            return location != null && location.getEncapsulatingSourceSection() != null;
        }

        @ExportMessage(name = "getSourceLocation")
        @TruffleBoundary
        public SourceSection getSourceSection() throws UnsupportedMessageException {
            if (sourceSection != null) {
                return sourceSection;
            }
            Node location = getLocation();
            SourceSection section = location != null ? location.getEncapsulatingSourceSection() : null;
            if (section == null) {
                throw UnsupportedMessageException.create();
            }
            return section;
        }
    }

    private static final String DISABLE_PRIVILEGES_VALUE = ImageBuildTimeOptions.get(ImageBuildTimeOptions.DISABLE_PRIVILEGES_NAME);
    private static final String[] DISABLED_PRIVILEGES = DISABLE_PRIVILEGES_VALUE.isEmpty() ? new String[0] : DISABLE_PRIVILEGES_VALUE.split(",");

    // reflectively read from TruffleBaseFeature
    private static final boolean ALLOW_CREATE_PROCESS;
    static final boolean ALLOW_ENVIRONMENT_ACCESS;
    static final boolean ALLOW_IO;
    static {
        boolean createProcess = true;
        boolean environmentAccess = true;
        boolean io = true;

        for (String privilege : DISABLED_PRIVILEGES) {
            switch (privilege) {
                case "createProcess":
                    createProcess = false;
                    break;
                case "environmentAccess":
                    environmentAccess = false;
                    break;
                case "io":
                    io = false;
                    break;
                default:
                    throw new Error("Invalid privilege name for " + ImageBuildTimeOptions.DISABLE_PRIVILEGES_NAME + ": " + privilege);
            }
        }

        ALLOW_CREATE_PROCESS = createProcess;
        ALLOW_ENVIRONMENT_ACCESS = environmentAccess;
        ALLOW_IO = io;
    }

    @SuppressWarnings({"all"})
    public PolyglotContextImpl createContext(OutputStream configOut, OutputStream configErr, InputStream configIn, boolean allowHostLookup,
                    HostAccess hostAccess,
                    PolyglotAccess polyglotAccess, boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO,
                    boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter, Map<String, String> options,
                    Map<String, String[]> arguments, String[] permittedContextLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess, ProcessHandler processHandler,
                    EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory, ClassLoader hostClassLoader,
                    boolean allowValueSharing, boolean useSystemExit) {
        PolyglotContextImpl context;
        boolean replayEvents;
        boolean contextAddedToEngine;
        try {
            synchronized (this.lock) {
                checkState();
                if (boundEngine && !contexts.isEmpty()) {
                    throw PolyglotEngineException.illegalArgument("Automatically created engines cannot be used to create more than one context. " +
                                    "Use Engine.newBuilder().build() to construct a new engine and pass it using Context.newBuilder().engine(engine).build().");
                }
            }
            EconomicSet<String> allowedLanguages = EconomicSet.create();
            if (permittedContextLanguages.length == 0) {
                if (this.permittedLanguages.length == 0) {
                    allowedLanguages.addAll(getLanguages().keySet());
                } else {
                    allowedLanguages.addAll(Arrays.asList(this.permittedLanguages));
                }
            } else {
                if (this.permittedLanguages.length == 0) {
                    allowedLanguages.addAll(Arrays.asList(permittedContextLanguages));
                } else {
                    EconomicSet<String> engineLanguages = EconomicSet.create();
                    engineLanguages.addAll(Arrays.asList(this.permittedLanguages));

                    allowedLanguages.addAll(Arrays.asList(permittedContextLanguages));
                    for (String language : allowedLanguages) {
                        if (!engineLanguages.contains(language)) {
                            throw PolyglotEngineException.illegalArgument(String.format(
                                            "The language %s permitted for the created polyglot context was not permitted by the explicit engine. " + //
                                                            "The engine only permits the use of the following languages: %s. " + //
                                                            "Use Engine.newBuilder(\"%s\").build() to construct an engine with this language permitted " + //
                                                            "or remove the language from the set of permitted languages when constructing the context in Context.newBuilder(...) to resolve this.",
                                            language,
                                            engineLanguages.toString(), language));
                        }
                    }
                }
            }
            String error = getAPIAccess().validatePolyglotAccess(polyglotAccess, allowedLanguages);
            if (error != null) {
                throw PolyglotEngineException.illegalArgument(error);
            }
            final FileSystem fs;
            final FileSystem internalFs;
            if (!ALLOW_IO) {
                if (fileSystem == null) {
                    fileSystem = FileSystems.newNoIOFileSystem();
                }
                fs = fileSystem;
                internalFs = fileSystem;
            } else if (allowHostIO) {
                fs = fileSystem != null ? fileSystem : FileSystems.newDefaultFileSystem();
                internalFs = fs;
            } else {
                fs = FileSystems.newNoIOFileSystem();
                internalFs = FileSystems.newLanguageHomeFileSystem();
            }
            if (currentWorkingDirectory != null) {
                fs.setCurrentWorkingDirectory(fs.parsePath(currentWorkingDirectory));
                internalFs.setCurrentWorkingDirectory(internalFs.parsePath(currentWorkingDirectory));
            }
            final OutputStream useOut;
            if (configOut == null || configOut == INSTRUMENT.getOut(this.out)) {
                useOut = this.out;
            } else {
                useOut = INSTRUMENT.createDelegatingOutput(configOut, this.out);
            }
            final OutputStream useErr;
            if (configErr == null || configErr == INSTRUMENT.getOut(this.err)) {
                useErr = this.err;
            } else {
                useErr = INSTRUMENT.createDelegatingOutput(configErr, this.err);
            }
            Handler useHandler = PolyglotLoggers.asHandler(logHandlerOrStream);
            useHandler = useHandler != null ? useHandler : logHandler;
            useHandler = useHandler != null ? useHandler
                            : PolyglotLoggers.createDefaultHandler(
                                            configErr == null ? INSTRUMENT.getOut(this.err) : configErr);
            final InputStream useIn = configIn == null ? this.in : configIn;
            final ProcessHandler useProcessHandler;
            if (allowCreateProcess) {
                if (!ALLOW_CREATE_PROCESS) {
                    throw PolyglotEngineException.illegalArgument("Cannot allowCreateProcess() because the privilege is removed at image build time");
                }
                useProcessHandler = processHandler != null ? processHandler : ProcessHandlers.newDefaultProcessHandler();
            } else {
                useProcessHandler = null;
            }
            if (!ALLOW_ENVIRONMENT_ACCESS && environmentAccess != EnvironmentAccess.NONE) {
                throw PolyglotEngineException.illegalArgument("Cannot allow EnvironmentAccess because the privilege is removed at image build time");
            }
            PolyglotLimits polyglotLimits = (PolyglotLimits) limitsImpl;
            PolyglotContextConfig config = new PolyglotContextConfig(this, useOut, useErr, useIn,
                            allowHostLookup, polyglotAccess, allowNativeAccess, allowCreateThread, allowHostClassLoading,
                            allowExperimentalOptions, classFilter, arguments, allowedLanguages, options, fs, internalFs, useHandler, allowCreateProcess, useProcessHandler,
                            environmentAccess, environment, zone, polyglotLimits, hostClassLoader, hostAccess, allowValueSharing, useSystemExit);
            context = loadPreinitializedContext(config);
            replayEvents = false;
            contextAddedToEngine = false;
            if (context == null) {
                synchronized (this.lock) {
                    checkState();
                    context = new PolyglotContextImpl(this, config);
                    addContext(context);
                    contextAddedToEngine = true;
                }
            } else if (context.engine == this) {
                assert context.engine.uncachedLocation != null;
                replayEvents = true;
            }
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
        boolean hasContextBindings;
        boolean hasThreadBindings;
        try {
            if (replayEvents) { // loaded context
                /*
                 * There might be new instruments to run with a preinitialized context and these
                 * instruments might define context locals and context thread locals. The
                 * instruments were created during the context loading before the context was added
                 * to the engine's contexts set, and so the instrument creation did not update the
                 * context's locals and thread locals. Since the context loading needs to enter the
                 * context on the current thread for patching, we need to update both the context
                 * locals and context thread locals.
                 */
                synchronized (context) {
                    context.resizeContextLocals(this.contextLocalLocations);
                    context.initializeInstrumentContextLocals(context.contextLocals);
                    context.resizeContextThreadLocals(this.contextThreadLocalLocations);
                    context.initializeInstrumentContextThreadLocals();
                }
            } else { // is new context
                try {
                    synchronized (context) {
                        context.initializeContextLocals();
                        context.notifyContextCreated();
                    }
                } catch (Throwable t) {
                    if (contextAddedToEngine) {
                        synchronized (this.lock) {
                            disposeContext(context);
                            if (boundEngine) {
                                ensureClosed(false, false);
                            }
                        }
                    }
                    throw t;
                }
            }
            hasContextBindings = EngineAccessor.INSTRUMENT.hasContextBindings(this);
            hasThreadBindings = EngineAccessor.INSTRUMENT.hasThreadBindings(this);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
        }
        if (replayEvents && (hasContextBindings || hasThreadBindings)) {
            /*
             * Replay events for preinitialized contexts. Events must be replayed without engine
             * lock. The events to replay are the context events and also the thread initialization
             * event for the current thread which was initialized for context patching.
             */
            final Object[] prev;
            try {
                prev = enter(context);
            } catch (Throwable t) {
                throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
            }
            try {
                context.replayInstrumentationEvents();
            } catch (Throwable t) {
                throw PolyglotImpl.guestToHostException(context.getHostContext(), t, true);
            } finally {
                try {
                    leave(prev, context);
                } catch (Throwable t) {
                    throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
                }
            }
        }
        checkTruffleRuntime();
        return context;
    }

    private PolyglotContextImpl loadPreinitializedContext(PolyglotContextConfig config) {
        PolyglotContextImpl context = preInitializedContext.getAndSet(null);
        if (!getEngineOptionValues().get(PolyglotEngineOptions.UsePreInitializedContext)) {
            context = null;
        }
        if (context != null) {
            FileSystems.PreInitializeContextFileSystem preInitFs = (FileSystems.PreInitializeContextFileSystem) context.config.fileSystem;
            preInitFs.onLoadPreinitializedContext(config.fileSystem);
            FileSystem oldFileSystem = config.fileSystem;
            config.fileSystem = preInitFs;

            preInitFs = (FileSystems.PreInitializeContextFileSystem) context.config.internalFileSystem;
            preInitFs.onLoadPreinitializedContext(config.internalFileSystem);
            FileSystem oldInternalFileSystem = config.internalFileSystem;
            config.internalFileSystem = preInitFs;

            boolean patchResult = false;
            synchronized (this.lock) {
                addContext(context);
            }
            try {
                patchResult = context.patch(config);
            } finally {
                synchronized (this.lock) {
                    removeContext(context);
                }
                if (patchResult && arePreInitializedLanguagesCompatible(context, config)) {
                    Collection<PolyglotInstrument> toCreate = null;
                    for (PolyglotInstrument instrument : idToInstrument.values()) {
                        if (instrument.getOptionValuesIfExists() != null) {
                            if (toCreate == null) {
                                toCreate = new HashSet<>();
                            }
                            toCreate.add(instrument);
                        }
                    }
                    if (toCreate != null) {
                        ensureInstrumentsCreated(toCreate);
                    }
                    synchronized (this.lock) {
                        addContext(context);
                    }
                } else {
                    context.closeImpl(false);
                    config.fileSystem = oldFileSystem;
                    config.internalFileSystem = oldInternalFileSystem;
                    PolyglotEngineImpl engine = new PolyglotEngineImpl(this);
                    ensureClosed(true, false);
                    synchronized (engine.lock) {
                        context = new PolyglotContextImpl(engine, config);
                        engine.addContext(context);
                    }
                }
            }
        }
        return context;
    }

    private static boolean arePreInitializedLanguagesCompatible(PolyglotContextImpl context, PolyglotContextConfig config) {
        Map<String, PolyglotLanguageContext> preInitializedLanguages = new HashMap<>();
        for (PolyglotLanguageContext languageContext : context.contexts) {
            if (languageContext.isInitialized() && !languageContext.language.isHost()) {
                preInitializedLanguages.put(languageContext.language.getId(), languageContext);
            }
        }
        for (String allowedLanguage : config.allowedPublicLanguages) {
            PolyglotLanguageContext languageContext = preInitializedLanguages.remove(allowedLanguage);
            if (languageContext != null) {
                preInitializedLanguages.keySet().removeAll(languageContext.getAccessibleLanguages(true).keySet());
            }
        }
        return preInitializedLanguages.isEmpty();
    }

    private void checkTruffleRuntime() {
        if (getEngineOptionValues().get(PolyglotEngineOptions.WarnInterpreterOnly) && Truffle.getRuntime().getClass() == DefaultTruffleRuntime.class) {
            getEngineLogger().log(Level.WARNING, "" +
                            "The polyglot context is using an implementation that does not support runtime compilation.\n" +
                            "The guest application code will therefore be executed in interpreted mode only.\n" +
                            "Execution only in interpreted mode will strongly impact the guest application performance.\n" +
                            "For more information on using GraalVM see https://www.graalvm.org/java/quickstart/.\n" +
                            "To disable this warning the '--engine.WarnInterpreterOnly=false' option or use the '-Dpolyglot.engine.WarnInterpreterOnly=false' system property.");
        }
    }

    OptionValuesImpl getEngineOptionValues() {
        return engineOptionValues;
    }

    Object getEngineLoggers() {
        return engineLoggers;
    }

    Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> getFileTypeDetectorsSupplier() {
        Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> res = fileTypeDetectorsSupplier;
        if (res == null) {
            synchronized (this.lock) {
                res = fileTypeDetectorsSupplier;
                if (res == null) {
                    Collection<LanguageCache> languageCaches = new ArrayList<>(idToLanguage.size());
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        languageCaches.add(language.cache);
                    }
                    res = FileSystems.newFileTypeDetectorsSupplier(languageCaches);
                    fileTypeDetectorsSupplier = res;
                }
            }
        }
        return res;
    }

    private static final Object NO_ENTER = new Object();

    @SuppressWarnings("static-method")
    boolean needsEnter(PolyglotContextImpl context) {
        return PolyglotFastThreadLocals.needsEnter(context);
    }

    Object enterIfNeeded(PolyglotContextImpl context, boolean pollSafepoint) {
        CompilerAsserts.neverPartOfCompilation("not designed for compilation");
        if (needsEnter(context)) {
            return enterCached(context, pollSafepoint);
        }
        assert PolyglotFastThreadLocals.getContext(null) != null;
        return NO_ENTER;
    }

    void leaveIfNeeded(Object prev, PolyglotContextImpl context) {
        CompilerAsserts.neverPartOfCompilation("not designed for compilation");
        if (prev != NO_ENTER) {
            leave((Object[]) prev, context);
        }
    }

    /**
     * Use to enter contexts from paths either compiled or not. If always compiled use
     * {@link #enterCached(PolyglotContextImpl, boolean)}.
     */
    Object[] enter(PolyglotContextImpl context) {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            return enterCached(context, true);
        } else {
            return enterBoundary(context);
        }
    }

    @TruffleBoundary
    private Object[] enterBoundary(PolyglotContextImpl context) {
        return enterCached(context, true);
    }

    /**
     * Only use to enter contexts from paths that are *always* compiled otherwise use
     * {@link #enter(PolyglotContextImpl)}.
     */
    Object[] enterCached(PolyglotContextImpl context, boolean pollSafepoint) {
        Object[] prev;
        PolyglotThreadInfo info = context.getCachedThread();
        boolean enterReverted = false;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
            // Volatile increment is safe if only one thread does it.
            prev = info.enterInternal();

            // Check again whether the cached thread info is still the same as expected
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.FASTPATH_PROBABILITY, info == context.getCachedThread())) {

                /*
                 * We are deliberately not polling a safepoint here. In case a safepoint is
                 * submitted cached thread info will be null and we will enter slow path, where the
                 * safepoint is polled.
                 */
                try {
                    info.notifyEnter(this, context);
                } catch (Throwable e) {
                    info.leaveInternal(prev);
                    throw e;
                }
                return prev;
            } else {
                /*
                 * If we go this path and enteredCount drops to 0, the subsequent slowpath enter
                 * must call deactivateThread.
                 */
                info.leaveInternal(prev);
                enterReverted = true;
            }
        }
        /*
         * Slow path. This happens when the cached thread info was set to null by context
         * invalidation or submission of a safepoint or if the previous thread was not the same
         * thread. The slow path acquires context lock to ensure ordering for context operations
         * like close.
         */
        prev = context.enterThreadChanged(true, enterReverted, pollSafepoint, false);
        assert verifyContext(context);
        return prev;
    }

    private static boolean verifyContext(PolyglotContextImpl context) {
        PolyglotContextImpl.State localState = context.state;
        return context == PolyglotFastThreadLocals.getContext(null) || localState.isInvalidOrClosed();
    }

    /**
     * Use to leave contexts from paths either compiled or not. If always compiled use
     * {@link #leaveCached(PolyglotContextImpl, PolyglotContextImpl)}.
     */
    void leave(Object[] prev, PolyglotContextImpl context) {
        if (CompilerDirectives.isPartialEvaluationConstant(this)) {
            leaveCached(prev, context);
        } else {
            leaveBoundary(prev, context);
        }
    }

    @TruffleBoundary
    private void leaveBoundary(Object[] prev, PolyglotContextImpl context) {
        leaveCached(prev, context);
    }

    /**
     * Only use to leave contexts from paths that are *always* compiled otherwise use
     * {@link #leave(PolyglotContextImpl, PolyglotContextImpl)}.
     */
    void leaveCached(Object[] prev, PolyglotContextImpl context) {
        assert context.state.isClosed() ||
                        PolyglotFastThreadLocals.getContext(null) == context : "Cannot leave context that is currently not entered. Forgot to enter or leave a context?";

        boolean entered = true;
        PolyglotThreadInfo info = context.getCachedThread();
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
            try {
                info.notifyLeave(this, context);
            } finally {
                info.leaveInternal(prev);
                entered = false;
            }
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.FASTPATH_PROBABILITY, info == context.getCachedThread())) {
                // fast path leave
                return;
            }
        }
        context.leaveThreadChanged(prev, true, entered);
    }

    static final class LogConfig {

        final Map<String, Level> logLevels;
        String logFile;

        LogConfig() {
            this.logLevels = new HashMap<>();
        }
    }

    LocalLocation[] addContextLocals(List<? extends AbstractContextLocal<?>> newLocals) {
        List<PolyglotContextImpl> aliveContexts;
        LocalLocation[] newLocations;
        StableLocalLocations newStableLocations;
        synchronized (this.lock) {
            StableLocalLocations stableLocations = this.contextLocalLocations;
            int index = stableLocations.locations.length;
            LocalLocation[] locationsCopy = Arrays.copyOf(stableLocations.locations, stableLocations.locations.length + newLocals.size());
            for (AbstractContextLocal<?> newLocal : newLocals) {
                locationsCopy[index] = newLocal.createLocation(index);
                newLocal.initializeLocation(locationsCopy[index]);
                index++;
            }
            /*
             * We pick up the alive contexts before we set the new context locals. So all new
             * contexts from now on will already use the initialized locals.
             */
            aliveContexts = collectAliveContexts();
            this.contextLocalLocations = newStableLocations = new StableLocalLocations(locationsCopy);
            stableLocations.assumption.invalidate("Context local added");
            newLocations = Arrays.copyOfRange(locationsCopy, stableLocations.locations.length, index);
        }
        for (PolyglotContextImpl context : aliveContexts) {
            context.resizeLocals(newStableLocations);
        }
        return newLocations;
    }

    LocalLocation[] addContextThreadLocals(List<? extends AbstractContextThreadLocal<?>> newLocals) {
        List<PolyglotContextImpl> aliveContexts;
        LocalLocation[] newLocations;
        StableLocalLocations newStableLocations;
        synchronized (this.lock) {
            StableLocalLocations stableLocations = this.contextThreadLocalLocations;
            int index = stableLocations.locations.length;
            LocalLocation[] locationsCopy = Arrays.copyOf(stableLocations.locations, stableLocations.locations.length + newLocals.size());
            for (AbstractContextThreadLocal<?> newLocal : newLocals) {
                locationsCopy[index] = newLocal.createLocation(index);
                newLocal.initializeLocation(locationsCopy[index]);
                index++;
            }
            /*
             * We pick up the alive contexts before we set the new context locals. So all new
             * contexts from now on will already use the initialized locals.
             */
            aliveContexts = collectAliveContexts();
            this.contextThreadLocalLocations = newStableLocations = new StableLocalLocations(locationsCopy);
            stableLocations.assumption.invalidate("Context thread local added");
            newLocations = Arrays.copyOfRange(locationsCopy, stableLocations.locations.length, index);
        }
        for (PolyglotContextImpl context : aliveContexts) {
            context.resizeThreadLocals(newStableLocations);
        }
        return newLocations;
    }

    static final class StableLocalLocations {

        @CompilationFinal(dimensions = 1) final LocalLocation[] locations;
        final Assumption assumption = Truffle.getRuntime().createAssumption();

        StableLocalLocations(LocalLocation[] locations) {
            this.locations = locations;
        }
    }

    private static volatile PolyglotEngineImpl fallbackEngine;

    static PolyglotEngineImpl getFallbackEngine() {
        if (fallbackEngine == null) {
            synchronized (PolyglotImpl.class) {
                if (fallbackEngine == null) {
                    PolyglotImpl polyglot = PolyglotImpl.getInstance();
                    TruffleLanguage<Object> hostLanguage = polyglot.createHostLanguage(polyglot.createHostAccess());
                    fallbackEngine = PolyglotImpl.getInstance().createDefaultEngine(hostLanguage);
                }
            }
        }
        return fallbackEngine;
    }

    /*
     * Invoked by TruffleBaseFeature to make sure the fallback engine is not contained in the image.
     */
    static void resetFallbackEngine() {
        synchronized (PolyglotImpl.class) {
            if (fallbackEngine != null) {
                fallbackEngine.ensureClosed(false, false);
                fallbackEngine = null;
            }
        }
    }

    private static final class UncachedLocationNode extends HostToGuestRootNode {

        UncachedLocationNode(TruffleLanguage<?> hostLanguage) {
            super(hostLanguage);
        }

        @Override
        protected Class<?> getReceiverType() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        protected Object executeImpl(PolyglotLanguageContext languageContext, Object receiver, Object[] args) {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Override
        public boolean isInternal() {
            return true;
        }

    }

    @SuppressWarnings("static-method")
    String getVersion() {
        String version = HomeFinder.getInstance().getVersion();
        if (version.equals("snapshot")) {
            return "Development Build";
        } else {
            return version;
        }
    }

}
