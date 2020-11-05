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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
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

final class PolyglotEngineImpl extends AbstractPolyglotImpl.AbstractEngineImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "host";

    static final String OPTION_GROUP_ENGINE = "engine";
    static final String OPTION_GROUP_LOG = "log";
    static final String OPTION_GROUP_IMAGE_BUILD_TIME = "image-build-time";
    static final String LOG_FILE_OPTION = OPTION_GROUP_LOG + ".file";

    // also update list in LanguageRegistrationProcessor
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList(HOST_LANGUAGE_ID, "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm", "file",
                                    OPTION_GROUP_ENGINE, OPTION_GROUP_LOG, OPTION_GROUP_IMAGE_BUILD_TIME));

    private static final Map<PolyglotEngineImpl, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean shutdownHookInitialized = false;
    private static final boolean DEBUG_MISSING_CLOSE = Boolean.getBoolean("polyglotimpl.DebugMissingClose");
    static final LocalLocation[] EMPTY_LOCATIONS = new LocalLocation[0];

    final Object lock = new Object();
    Engine creatorApi; // effectively final
    Engine currentApi;
    final Object instrumentationHandler;
    final PolyglotImpl impl;
    DispatchOutputStream out;       // effectively final
    DispatchOutputStream err;       // effectively final
    InputStream in;                 // effectively final

    final Map<String, PolyglotLanguage> idToLanguage;
    final Map<String, PolyglotLanguage> classToLanguage;
    final Map<String, Language> idToPublicLanguage;
    final Map<String, LanguageInfo> idToInternalLanguageInfo;

    final Map<String, PolyglotInstrument> idToInstrument;
    final Map<String, Instrument> idToPublicInstrument;
    final Map<String, InstrumentInfo> idToInternalInstrumentInfo;

    @CompilationFinal OptionValuesImpl engineOptionValues;

    ClassLoader contextClassLoader;     // effectively final
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

    PolyglotLanguage hostLanguage;
    @CompilationFinal Assumption singleContext = Truffle.getRuntime().createAssumption("Single context per engine.");
    final Assumption singleThreadPerContext = Truffle.getRuntime().createAssumption("Single thread per context of an engine.");
    final Assumption noInnerContexts = Truffle.getRuntime().createAssumption("No inner contexts.");
    final Assumption customHostClassLoader = Truffle.getRuntime().createAssumption("No custom host class loader needed.");
    final Assumption neverInterrupted = Truffle.getRuntime().createAssumption("No context interrupted.");

    volatile OptionDescriptors allOptions;
    volatile boolean closed;

    private volatile CancelHandler cancelHandler;
    // field used by the TruffleRuntime implementation to persist state per Engine
    final Object runtimeData;
    Map<String, Level> logLevels;    // effectively final
    HostClassCache hostClassCache; // effectively final
    private volatile Object engineLoggers;
    private volatile Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier;

    final int contextLength;
    private volatile EngineLimits limits;
    final boolean conservativeContextReferences;
    private final MessageTransport messageInterceptor;
    private volatile int asynchronousStackDepth = 0;
    @CompilationFinal private HostToGuestCodeCache hostToGuestCodeCache;

    final SpecializationStatistics specializationStatistics;
    final Function<String, TruffleLogger> engineLoggerSupplier;
    private volatile TruffleLogger engineLogger;

    @CompilationFinal volatile StableLocalLocations contextLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);
    @CompilationFinal volatile StableLocalLocations contextThreadLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);

    PolyglotEngineImpl(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, OptionValuesImpl engineOptions,
                    Map<String, Level> logLevels,
                    EngineLoggerProvider engineLogger, Map<String, String> options,
                    boolean allowExperimentalOptions, ClassLoader contextClassLoader, boolean boundEngine, boolean preInitialization,
                    MessageTransport messageInterceptor, Handler logHandler) {
        super(impl);
        this.messageInterceptor = messageInterceptor;
        this.impl = impl;
        this.out = out;
        this.err = err;
        this.in = in;
        this.contextClassLoader = contextClassLoader;
        this.logHandler = logHandler;
        this.logLevels = logLevels;
        this.boundEngine = boundEngine;
        this.storeEngine = RUNTIME.isStoreEnabled(engineOptions);

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.contextLength = idToLanguage.size() + 1 /* +1 for host language */;

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
            initializeMultiContext(null);
        }
        intitializeStore(false, this.storeEngine);

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(options, languagesOptions, instrumentsOptions);

        this.conservativeContextReferences = engineOptionValues.get(PolyglotEngineOptions.UseConservativeContextReferences);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language), allowExperimentalOptions);
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

    HostToGuestCodeCache getHostToGuestCodeCache() {
        HostToGuestCodeCache cache = this.hostToGuestCodeCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hostToGuestCodeCache = cache = new HostToGuestCodeCache();
        }
        return cache;
    }

    void notifyCreated() {
        ENGINES.put(this, null);
        RUNTIME.onEngineCreate(this, this.runtimeData);
    }

    PolyglotEngineImpl(PolyglotEngineImpl prototype) {
        super(prototype.impl);
        this.messageInterceptor = prototype.messageInterceptor;
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(
                        this,
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.out)),
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.err)),
                        prototype.in,
                        prototype.messageInterceptor, prototype.storeEngine);
        this.impl = prototype.impl;
        this.out = prototype.out;
        this.err = prototype.err;
        this.in = prototype.in;
        this.contextClassLoader = prototype.contextClassLoader;
        this.boundEngine = prototype.boundEngine;
        this.logHandler = prototype.logHandler;
        this.runtimeData = RUNTIME.createRuntimeData(prototype.engineOptionValues, prototype.engineLoggerSupplier);
        this.engineLoggerSupplier = prototype.engineLoggerSupplier;

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.contextLength = idToLanguage.size() + 1 /* +1 for host language */;

        Map<String, InstrumentInfo> instrumentInfos = new LinkedHashMap<>();
        this.idToInstrument = Collections.unmodifiableMap(initializeInstruments(instrumentInfos));
        this.idToInternalInstrumentInfo = Collections.unmodifiableMap(instrumentInfos);

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
            initializeMultiContext(null);
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
                    this.engineLogger = result = this.engineLoggerSupplier.apply(OPTION_GROUP_ENGINE);
                }
            }
        }
        return result;
    }

    static OptionDescriptors createEngineOptionDescriptors() {
        OptionDescriptors engineOptionDescriptors = new PolyglotEngineOptionsOptionDescriptors();
        OptionDescriptors compilerOptionDescriptors = EngineAccessor.RUNTIME.getEngineOptionDescriptors();
        return OptionDescriptors.createUnion(engineOptionDescriptors, compilerOptionDescriptors);
    }

    static Collection<Engine> findActiveEngines() {
        synchronized (ENGINES) {
            List<Engine> engines = new ArrayList<>(ENGINES.size());
            for (PolyglotEngineImpl engine : ENGINES.keySet()) {
                engines.add(engine.creatorApi);
            }
            return engines;
        }
    }

    boolean patch(DispatchOutputStream newOut,
                    DispatchOutputStream newErr,
                    InputStream newIn,
                    OptionValuesImpl engineOptions,
                    LogConfig newLogConfig,
                    Function<String, TruffleLogger> logSupplier,
                    Map<String, String> newOptions,
                    boolean newAllowExperimentalOptions,
                    ClassLoader newContextClassLoader, boolean newBoundEngine, Handler newLogHandler) {
        CompilerAsserts.neverPartOfCompilation();
        if (this.boundEngine != newBoundEngine) {
            return false;
        }
        this.out = newOut;
        this.err = newErr;
        this.in = newIn;
        this.contextClassLoader = newContextClassLoader;
        this.boundEngine = newBoundEngine;
        this.logHandler = newLogHandler;
        this.engineOptionValues = engineOptions;
        this.logLevels = newLogConfig.logLevels;
        boolean wasStore = this.storeEngine;
        this.storeEngine = RUNTIME.isStoreEnabled(engineOptions);

        intitializeStore(wasStore, storeEngine);

        INSTRUMENT.patchInstrumentationHandler(instrumentationHandler, newOut, newErr, newIn);

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(newOptions, languagesOptions, instrumentsOptions);

        RUNTIME.onEnginePatch(this.runtimeData, engineOptions, logSupplier);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language), newAllowExperimentalOptions);
        }

        // Set instruments options but do not call onCreate. OnCreate is called only in case of
        // successful context patch.
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getEngineOptionValues().putAll(instrumentsOptions.get(instrument), newAllowExperimentalOptions);
        }
        registerShutDownHook();
        return true;
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

    private static void createInstruments(Map<PolyglotInstrument, Map<String, String>> instrumentsOptions, boolean allowExperimentalOptions) {
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getEngineOptionValues().putAll(instrumentsOptions.get(instrument), allowExperimentalOptions);
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

    void initializeMultiContext(PolyglotContextImpl existingContext) {
        synchronized (this.lock) {
            if (singleContext.isValid()) {
                singleContext.invalidate("More than one context introduced.");
                if (existingContext != null) {
                    for (PolyglotLanguageContext context : existingContext.contexts) {
                        if (context.isInitialized()) {
                            context.getLanguageInstance().ensureMultiContextInitialized();
                        }
                    }
                }
                for (PolyglotLanguage lang : idToLanguage.values()) {
                    lang.profile.prepareForMultiContext();
                }
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
                            options.put(optionKey, System.getProperty(key));
                        }
                    }
                }
            }
        }
        return options;
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

        Map<String, LanguageInfo> languages;
        if (accessingLanguage != null) {
            languages = accessingLanguage.getAccessibleLanguages(allowInternalAndDependent);
        } else {
            assert allowInternalAndDependent : "non internal access is not yet supported for instrument lookups";
            languages = this.idToInternalLanguageInfo;
        }

        LanguageInfo foundLanguage = null;
        if (languageId != null) {
            foundLanguage = languages.get(languageId);
        }
        if (mimeType != null && foundLanguage == null) {
            // we need to interpret mime types for compatibility.
            foundLanguage = languages.get(mimeType);
            if (foundLanguage == null) {
                for (LanguageInfo searchLanguage : languages.values()) {
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
                for (LanguageInfo language : languages.values()) {
                    ids.add(language.getId());
                }
                throw PolyglotEngineException.illegalState("No language for id " + languageId + " found. Supported languages are: " + ids);
            } else {
                Set<String> mimeTypes = new LinkedHashSet<>();
                for (LanguageInfo language : languages.values()) {
                    mimeTypes.addAll(language.getMimeTypes());
                }
                throw PolyglotEngineException.illegalState("No language for MIME type " + mimeType + " found. Supported languages are: " + mimeTypes);
            }
        } else {
            return null;
        }
    }

    private Map<String, PolyglotInstrument> initializeInstruments(Map<String, InstrumentInfo> infos) {
        Map<String, PolyglotInstrument> instruments = new LinkedHashMap<>();
        List<InstrumentCache> cachedInstruments = InstrumentCache.load();
        for (InstrumentCache instrumentCache : cachedInstruments) {
            PolyglotInstrument instrumentImpl = new PolyglotInstrument(this, instrumentCache);
            instrumentImpl.info = LANGUAGE.createInstrument(instrumentImpl, instrumentCache.getId(), instrumentCache.getName(), instrumentCache.getVersion());
            Instrument instrument = impl.getAPIAccess().newInstrument(instrumentImpl);
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

        this.hostLanguage = createLanguage(LanguageCache.createHostLanguageCache(), HOST_LANGUAGE_INDEX, null);

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
        Language language = impl.getAPIAccess().newLanguage(languageImpl);
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

    private void addContext(PolyglotContextImpl context) {
        assert Thread.holdsLock(this.lock);
        assert context.creatorApi == null;
        assert context.currentApi == null;

        Context api = impl.getAPIAccess().newContext(context);
        context.creatorApi = api;
        context.currentApi = impl.getAPIAccess().newContext(context);

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

        if (!singleContext.isValid()) {
            PolyglotContextImpl.invalidateStaticContextAssumption();
        }
    }

    void removeContext(PolyglotContextImpl context) {
        synchronized (this.lock) {
            // should never be remove twice
            assert !context.weakReference.removed;
            context.weakReference.removed = true;
            context.weakReference.freeInstances.clear();
            contexts.remove(context.weakReference);
            workContextReferenceQueue();
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
                    listener.onLanguageContextCreated(context.creatorTruffleContext, language);
                    if (lc.isInitialized()) {
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

    @Override
    public Language requirePublicLanguage(String id) {
        try {
            checkState();
            Language language = idToPublicLanguage.get(id);
            if (language == null) {
                String misspelledGuess = matchSpellingError(idToPublicLanguage.keySet(), id);
                String didYouMean = "";
                if (misspelledGuess != null) {
                    didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
                }
                throw PolyglotEngineException.illegalArgument(String.format("A language with id '%s' is not installed. %sInstalled languages are: %s.", id, didYouMean, getLanguages().keySet()));
            }
            return language;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
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

    @Override
    public Instrument requirePublicInstrument(String id) {
        try {
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
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public void close(Engine sourceEngine, boolean cancelIfExecuting) {
        try {
            if (sourceEngine != creatorApi) {
                throw PolyglotEngineException.illegalState("Engine instances that were indirectly received using Context.get() cannot be closed.");
            }
            ensureClosed(cancelIfExecuting, false);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @TruffleBoundary
    <T extends TruffleLanguage<?>> PolyglotLanguage getLanguage(Class<T> languageClass, boolean fail) {
        PolyglotLanguage foundLanguage = classToLanguage.get(languageClass.getName());
        if (foundLanguage == null) {
            if (languageClass == HostLanguage.class) {
                return hostLanguage;
            }
            if (fail) {
                Set<String> languageNames = classToLanguage.keySet();
                throw PolyglotEngineException.illegalArgument("Cannot find language " + languageClass + " among " + languageNames);
            }
        }
        return foundLanguage;
    }

    <T extends TruffleLanguage<?>> PolyglotLanguageInstance getCurrentLanguageInstance(Class<T> languageClass) {
        PolyglotLanguage foundLanguage = getLanguage(languageClass, true);
        PolyglotLanguageContext context = foundLanguage.getCurrentLanguageContext();
        if (!context.isCreated()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw PolyglotEngineException.illegalState(String.format("A context for language %s was not yet created.", languageClass.getName()));
        }
        return context.getLanguageInstance();
    }

    void ensureClosed(boolean cancelIfExecuting, boolean inShutdownHook) {
        synchronized (this.lock) {
            if (!closed) {
                workContextReferenceQueue();
                List<PolyglotContextImpl> localContexts = collectAliveContexts();
                /*
                 * Check ahead of time for open contexts to fail early and avoid closing only some
                 * contexts.
                 */
                if (!inShutdownHook) {
                    if (!cancelIfExecuting) {
                        for (PolyglotContextImpl context : localContexts) {
                            assert !Thread.holdsLock(context);
                            synchronized (context) {
                                if (context.hasActiveOtherThread(false) && context.closingThread == null) {
                                    throw PolyglotEngineException.illegalState(String.format("One of the context instances is currently executing. " +
                                                    "Set cancelIfExecuting to true to stop the execution on this thread."));
                                }
                            }
                        }
                    }
                    for (PolyglotContextImpl context : localContexts) {
                        assert !Thread.holdsLock(context);
                        boolean closeCompleted = context.closeImpl(cancelIfExecuting, cancelIfExecuting, true);
                        if (!closeCompleted && !cancelIfExecuting) {
                            throw PolyglotEngineException.illegalState(String.format("One of the context instances is currently executing. " +
                                            "Set cancelIfExecuting to true to stop the execution on this thread."));
                        }
                        context.checkSubProcessFinished();
                    }
                    if (cancelIfExecuting) {
                        getCancelHandler().cancel(localContexts);
                    }
                }

                // don't commit changes to contexts if still running
                if (!inShutdownHook) {
                    if (!boundEngine) {
                        for (PolyglotContextImpl context : localContexts) {
                            PolyglotContextImpl.disposeStaticContext(context);
                        }
                    }

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
        Thread.holdsLock(this.lock);
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

    @Override
    public Map<String, Instrument> getInstruments() {
        try {
            checkState();
            return idToPublicInstrument;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public Map<String, Language> getLanguages() {
        try {
            checkState();
            return idToPublicLanguage;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public OptionDescriptors getOptions() {
        try {
            checkState();
            return engineOptionValues.getDescriptors();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    @Override
    public Set<Source> getCachedSources() {
        checkState();
        Set<Source> sources = new HashSet<>();
        List<PolyglotContextImpl> activeContexts = collectAliveContexts();
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
            } else {
                initializeMultiContext(null);
            }

            PolyglotContextImpl.singleContextState.getContextThreadLocal().enableStore();
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
     * Clears the pre-initialized engines. The TruffleFeature needs to clean emitted engines during
     * Feature.cleanup.
     */
    static void resetPreInitializedEngine() {
        ENGINES.clear();
    }

    void initializeHostAccess(HostAccess policy) {
        assert Thread.holdsLock(this.lock);
        assert policy != null;
        HostClassCache cache = HostClassCache.findOrInitialize(getAPIAccess(), policy, contextClassLoader);
        if (this.hostClassCache != null) {
            if (this.hostClassCache.hostAccess.equals(cache.hostAccess)) {
                /*
                 * The cache can be effectively be reused if the same host access configuration
                 * applies.
                 */
            } else {
                throw PolyglotEngineException.illegalState("Found different host access configuration for a context with a shared engine. " +
                                "The host access configuration must be the same for all contexts of an engine. " +
                                "Provide the same host access configuration using the Context.Builder.allowHostAccess method when constructing the context.");
            }
        } else {
            this.hostClassCache = cache;
        }
    }

    HostClassCache getHostClassCache() {
        return hostClassCache;
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

    CancelHandler getCancelHandler() {
        if (cancelHandler == null) {
            synchronized (this.lock) {
                if (cancelHandler == null) {
                    cancelHandler = new CancelHandler();
                }
            }
        }
        return cancelHandler;

    }

    final class CancelHandler {
        private final Instrumenter instrumenter;
        private volatile EventBinding<?> cancellationBinding;
        private int cancellationUsers;

        CancelHandler() {
            this.instrumenter = (Instrumenter) INSTRUMENT.getEngineInstrumenter(instrumentationHandler);
        }

        void cancel(List<PolyglotContextImpl> localContexts) {
            cancel(localContexts, 0, null);
        }

        boolean cancel(List<PolyglotContextImpl> localContexts, long startMillis, Duration timeout) {
            boolean cancelling = false;
            for (PolyglotContextImpl context : localContexts) {
                if (context.cancelling || context.interrupting) {
                    cancelling = true;
                    break;
                }
            }
            if (cancelling) {
                enableCancel();
                try {
                    for (PolyglotContextImpl context : localContexts) {
                        context.sendInterrupt();
                    }
                    if (timeout == null) {
                        for (PolyglotContextImpl context : localContexts) {
                            context.waitForClose();
                        }
                    } else {
                        long cancelTimeoutMillis = timeout != Duration.ZERO ? timeout.toMillis() : 0;
                        boolean success = true;
                        for (PolyglotContextImpl context : localContexts) {
                            if (!context.waitForThreads(startMillis, cancelTimeoutMillis)) {
                                success = false;
                            }
                        }
                        return success;
                    }
                } finally {
                    disableCancel();
                }
            }
            return true;
        }

        void enableCancel() {
            synchronized (PolyglotEngineImpl.this.lock) {
                if (cancellationBinding == null) {
                    cancellationBinding = instrumenter.attachExecutionEventListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                        public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                            cancelExecution(context);
                        }

                        public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                            if (!(exception instanceof CancelExecution)) {
                                cancelExecution(context);
                            }
                        }

                        public void onEnter(EventContext context, VirtualFrame frame) {
                            cancelExecution(context);
                        }

                        @TruffleBoundary
                        private void cancelExecution(EventContext eventContext) {
                            PolyglotContextImpl context = PolyglotContextImpl.requireContext();
                            if (context.invalid || context.cancelling) {
                                throw context.createCancelException(eventContext.getInstrumentedNode());
                            } else if (context.interrupting) {
                                throw new InterruptExecution(eventContext.getInstrumentedNode());
                            }
                        }
                    });
                }
                cancellationUsers++;
            }
        }

        private void disableCancel() {
            synchronized (PolyglotEngineImpl.this.lock) {
                int usersLeft = --cancellationUsers;
                if (usersLeft <= 0) {
                    EventBinding<?> b = cancellationBinding;
                    if (b != null) {
                        b.dispose();
                    }
                    cancellationBinding = null;
                }
            }
        }
    }

    @SuppressWarnings("serial")
    static final class CancelExecution extends ThreadDeath {

        private final Node location;
        private final String cancelMessage;
        private final boolean resourceLimit;

        CancelExecution(Node location, String cancelMessage, boolean resourceLimit) {
            this.location = location;
            this.cancelMessage = cancelMessage;
            this.resourceLimit = resourceLimit;
        }

        Node getLocation() {
            return location;
        }

        SourceSection getSourceLocation() {
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

        InterruptExecution(Node location) {
            super("Execution got interrupted.", location);
        }

        @ExportMessage
        @SuppressWarnings("static-method")
        ExceptionType getExceptionType() {
            return ExceptionType.INTERRUPT;
        }
    }

    @Override
    public String getImplementationName() {
        try {
            return Truffle.getRuntime().getName();
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    private static final String DISABLE_PRIVILEGES_VALUE = ImageBuildTimeOptions.get(ImageBuildTimeOptions.DISABLE_PRIVILEGES_NAME);
    private static final String[] DISABLED_PRIVILEGES = DISABLE_PRIVILEGES_VALUE.isEmpty() ? new String[0] : DISABLE_PRIVILEGES_VALUE.split(",");

    // reflectively read from TruffleFeature
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

    @Override
    @SuppressWarnings({"all"})
    public Context createContext(OutputStream configOut, OutputStream configErr, InputStream configIn, boolean allowHostLookup,
                    HostAccess hostAccess,
                    PolyglotAccess polyglotAccess, boolean allowNativeAccess, boolean allowCreateThread, boolean allowHostIO,
                    boolean allowHostClassLoading, boolean allowExperimentalOptions, Predicate<String> classFilter, Map<String, String> options,
                    Map<String, String[]> arguments, String[] onlyLanguages, FileSystem fileSystem, Object logHandlerOrStream, boolean allowCreateProcess, ProcessHandler processHandler,
                    EnvironmentAccess environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl, String currentWorkingDirectory, ClassLoader hostClassLoader) {
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
                initializeHostAccess(hostAccess);
            }
            EconomicSet<String> allowedLanguages = EconomicSet.create();
            if (onlyLanguages.length == 0) {
                allowedLanguages.addAll(getLanguages().keySet());
            } else {
                allowedLanguages.addAll(Arrays.asList(onlyLanguages));
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
                            environmentAccess, environment, zone, polyglotLimits, hostClassLoader);
            context = loadPreinitializedContext(config, hostAccess);
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
                replayEvents = true;
            }
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
        boolean hasContextBindings;
        try {
            if (!replayEvents) { // is new context
                try {
                    synchronized (context) {
                        context.initializeContextLocals();
                    }
                } catch (Throwable t) {
                    if (contextAddedToEngine) {
                        synchronized (this.lock) {
                            removeContext(context);
                        }
                    }
                    throw t;
                }
            }
            hasContextBindings = EngineAccessor.INSTRUMENT.hasContextBindings(this);
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(context.getHostContext(), t, false);
        }
        if (replayEvents && hasContextBindings) {
            // replace events for preinitialized contexts
            // events must be replayed without engine lock.
            final PolyglotContextImpl prev;
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
        return context.creatorApi;
    }

    private PolyglotContextImpl loadPreinitializedContext(PolyglotContextConfig config, HostAccess hostAccess) {
        PolyglotContextImpl context = preInitializedContext.getAndSet(null);
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
            try {
                patchResult = context.patch(config);
            } finally {
                if (patchResult) {
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
                    context.closeImpl(false, false, false);
                    PolyglotContextImpl.disposeStaticContext(null);
                    config.fileSystem = oldFileSystem;
                    config.internalFileSystem = oldInternalFileSystem;
                    PolyglotEngineImpl engine = new PolyglotEngineImpl(this);
                    ensureClosed(true, false);
                    synchronized (engine.lock) {
                        engine.creatorApi = getAPIAccess().newEngine(engine);
                        engine.currentApi = getAPIAccess().newEngine(engine);
                        engine.initializeHostAccess(hostAccess);
                        context = new PolyglotContextImpl(engine, config);
                        engine.addContext(context);
                    }
                }
            }
        }
        return context;
    }

    OptionValuesImpl getEngineOptionValues() {
        return engineOptionValues;
    }

    Object getOrCreateEngineLoggers() {
        Object res = engineLoggers;
        if (res == null) {
            synchronized (this.lock) {
                res = engineLoggers;
                if (res == null) {
                    res = LANGUAGE.createEngineLoggers(PolyglotLoggers.createEngineSPI(this), logLevels);
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
        if (PolyglotContextImpl.getSingleContextState().getSingleContextAssumption().isValid()) {
            // if its a single context we know which one to enter
            return !PolyglotContextImpl.singleContextState.getContextThreadLocal().isSet();
        } else {
            return PolyglotContextImpl.currentNotEntered() != context;
        }
    }

    Object enterIfNeeded(PolyglotContextImpl context) {
        if (needsEnter(context)) {
            return enter(context);
        }
        assert PolyglotContextImpl.currentNotEntered() != null;
        return NO_ENTER;
    }

    void leaveIfNeeded(Object prev, PolyglotContextImpl context) {
        if (prev != NO_ENTER) {
            leave((PolyglotContextImpl) prev, context);
        }
    }

    PolyglotContextImpl enter(PolyglotContextImpl context) {
        PolyglotContextImpl prev;
        PolyglotThreadInfo info = getCachedThreadInfo(context);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
            // fast-path -> same thread
            prev = PolyglotContextImpl.getSingleContextState().getContextThreadLocal().setReturnParent(context);
            try {
                info.enter(this, context);
            } catch (Throwable t) {
                PolyglotContextImpl.getSingleContextState().getContextThreadLocal().set(prev);
                throw t;
            }
        } else {
            // slow path -> changed thread
            if (singleThreadPerContext.isValid()) {
                CompilerDirectives.transferToInterpreter();
            }
            prev = context.enterThreadChanged();
        }
        assert verifyContext(context);
        return prev;
    }

    private static boolean verifyContext(PolyglotContextImpl context) {
        return context == PolyglotContextImpl.currentNotEntered() || context.closed || context.invalid;
    }

    void leave(PolyglotContextImpl prev, PolyglotContextImpl polyglotContext) {
        assert polyglotContext.closed || polyglotContext.closingThread == Thread.currentThread() ||
                        PolyglotContextImpl.currentNotEntered() == polyglotContext : "Cannot leave context that is currently not entered. Forgot to enter or leave a context?";
        PolyglotThreadInfo info = getCachedThreadInfo(polyglotContext);
        try {
            if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
                info.leave(this, polyglotContext);
            } else {
                if (singleThreadPerContext.isValid() && singleContext.isValid()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
                polyglotContext.leaveThreadChanged();
            }
        } finally {
            PolyglotContextImpl.getSingleContextState().getContextThreadLocal().set(prev);
        }
    }

    PolyglotThreadInfo getCachedThreadInfo(PolyglotContextImpl context) {
        if (singleThreadPerContext.isValid() && singleContext.isValid() && neverInterrupted.isValid()) {
            return context.constantCurrentThreadInfo;
        } else {
            return context.currentThreadInfo;
        }
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
            synchronized (context) {
                if (context.closed || context.invalid) {
                    continue;
                }
                context.resizeContextLocals(newStableLocations);
            }
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
            synchronized (context) {
                if (context.closed || context.invalid) {
                    continue;
                }
                context.resizeContextThreadLocals(newStableLocations);
            }
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
                    fallbackEngine = PolyglotImpl.getInstance().createDefaultEngine();
                }
            }
        }
        return fallbackEngine;
    }

    /*
     * Invoked by TruffleFeature to make sure the fallback engine is not contained in the image.
     */
    static void resetFallbackEngine() {
        synchronized (PolyglotImpl.class) {
            fallbackEngine = null;
        }
    }

}
