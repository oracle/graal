/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Path;
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
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.home.HomeFinder;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostLanguageService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractPolyglotHostService;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageEndpoint;
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
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.SpecializationStatistics;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.polyglot.PolyglotContextConfig.FileSystemConfig;
import com.oracle.truffle.polyglot.PolyglotContextConfig.PreinitConfig;
import com.oracle.truffle.polyglot.PolyglotLimits.EngineLimits;
import com.oracle.truffle.polyglot.PolyglotLocals.AbstractContextLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.AbstractContextThreadLocal;
import com.oracle.truffle.polyglot.PolyglotLocals.LocalLocation;
import com.oracle.truffle.polyglot.PolyglotLoggers.EngineLoggerProvider;
import com.oracle.truffle.polyglot.PolyglotLoggers.LoggerCache;
import com.oracle.truffle.polyglot.SystemThread.InstrumentSystemThread;

/** The implementation of {@link org.graalvm.polyglot.Engine}, stored in the receiver field. */
final class PolyglotEngineImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "host";

    private static final AtomicLong ENGINE_COUNTER = new AtomicLong();
    static final String ENGINE_ID = "engine";
    static final String OPTION_GROUP_ENGINE = ENGINE_ID;
    static final String OPTION_GROUP_COMPILER = "compiler";
    static final String OPTION_GROUP_LOG = "log";
    static final String OPTION_GROUP_IMAGE_BUILD_TIME = "image-build-time";
    static final String LOG_FILE_OPTION = OPTION_GROUP_LOG + ".file";

    // also update list in LanguageRegistrationProcessor
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList(HOST_LANGUAGE_ID, "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm", "file",
                                    ENGINE_ID, OPTION_GROUP_LOG, OPTION_GROUP_IMAGE_BUILD_TIME));

    private static final boolean DEBUG_MISSING_CLOSE = Boolean.getBoolean("polyglotimpl.DebugMissingClose");
    static final LocalLocation[] EMPTY_LOCATIONS = new LocalLocation[0];

    final Object lock = new Object();

    private Thread closingThread;

    final Object instrumentationHandler;
    final String[] permittedLanguages;
    final PolyglotImpl impl;
    SandboxPolicy sandboxPolicy;    // effectively final
    DispatchOutputStream out;       // effectively final
    DispatchOutputStream err;       // effectively final
    InputStream in;                 // effectively final
    private Reference<Engine> weakAPI;

    // languages by LanguageCache.getStaticIndex()
    @CompilationFinal(dimensions = 1) final PolyglotLanguage[] languages;
    final Map<String, PolyglotLanguage> idToLanguage;
    final Map<String, PolyglotLanguage> classToLanguage;
    final Map<String, PolyglotLanguage> idToPublicLanguage;
    final Map<String, LanguageInfo> idToInternalLanguageInfo;

    final Map<String, PolyglotInstrument> idToInstrument;
    final Map<String, PolyglotInstrument> idToPublicInstrument;
    final Map<String, InstrumentInfo> idToInternalInstrumentInfo;

    @CompilationFinal OptionValuesImpl engineOptionValues;

    // true if engine is implicitly bound to a context and therefore closed with the context
    boolean boundEngine;    // effectively final

    /*
     * True if the runtime wants to store the resulting code when the engine is closed. This means
     * that strong references for source caches should be used.
     */
    boolean storeEngine; // modified on patch
    LogHandler logHandler;     // effectively final
    final Exception createdLocation = DEBUG_MISSING_CLOSE ? new Exception() : null;
    private final EconomicSet<PolyglotContextImpl> contexts = EconomicSet.create(Equivalence.IDENTITY);
    final ReferenceQueue<PolyglotContextImpl> contextsReferenceQueue = new ReferenceQueue<>();

    private final AtomicReference<PolyglotContextImpl> preInitializedContext = new AtomicReference<>();

    final Assumption singleThreadPerContext = Truffle.getRuntime().createAssumption("Single thread per context of an engine.");
    final Assumption noInnerContexts = Truffle.getRuntime().createAssumption("No inner contexts.");
    final Assumption customHostClassLoader = Truffle.getRuntime().createAssumption("No custom host class loader needed.");

    volatile OptionDescriptors allOptions;
    volatile OptionDescriptors allSourceOptions;
    volatile boolean closed;

    // field used by the TruffleRuntime implementation to persist state per Engine
    final Object runtimeData;
    Map<String, Level> logLevels;    // effectively final
    private volatile Object engineLoggers;
    private volatile Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier;

    final int languageCount;
    private volatile EngineLimits limits;
    final MessageTransport messageTransport;
    private volatile int asynchronousStackDepth = 0;

    final SpecializationStatistics specializationStatistics;
    Function<String, TruffleLogger> engineLoggerSupplier;   // effectively final
    @CompilationFinal private TruffleLogger engineLogger;   // effectively final

    final WeakAssumedValue<PolyglotContextImpl> singleContextValue = new WeakAssumedValue<>("single context");

    @CompilationFinal volatile StableLocalLocations contextLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);
    @CompilationFinal volatile StableLocalLocations contextThreadLocalLocations = new StableLocalLocations(EMPTY_LOCATIONS);

    @CompilationFinal PolyglotLanguage hostLanguage;    // effectively final after engine patching
    @CompilationFinal AbstractHostLanguageService host; // effectively final after engine patching

    boolean inEnginePreInitialization; // effectively final after engine pre-initialization

    final boolean hostLanguageOnly;

    final List<PolyglotSharingLayer> sharedLayers = new ArrayList<>();

    private final ReferenceQueue<Source> deadSourcesQueue = new ReferenceQueue<>();

    private boolean runtimeInitialized;

    AbstractPolyglotHostService polyglotHostService; // effectively final after engine patching

    private final Set<InstrumentSystemThread> activeSystemThreads = Collections.newSetFromMap(new HashMap<>());

    final APIAccess apiAccess;

    final boolean probeAssertionsEnabled;

    final InternalResourceRoots internalResourceRoots;

    final long engineId;
    final boolean allowExperimentalOptions;

    SourceCacheStatisticsListener sourceCacheStatisticsListener; // effectively final

    @SuppressWarnings("unchecked")
    PolyglotEngineImpl(PolyglotImpl impl, SandboxPolicy sandboxPolicy, String[] permittedLanguages,
                    DispatchOutputStream out, DispatchOutputStream err, InputStream in, OptionValuesImpl engineOptions,
                    Map<String, Level> logLevels,
                    EngineLoggerProvider engineLoggerSupplier, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean boundEngine, boolean preInitialization,
                    MessageTransport messageTransport, LogHandler logHandler,
                    TruffleLanguage<Object> hostImpl, boolean hostLanguageOnly, AbstractPolyglotHostService polyglotHostService) {
        this.engineId = ENGINE_COUNTER.incrementAndGet();
        this.apiAccess = impl.getAPIAccess();
        this.sandboxPolicy = sandboxPolicy;
        this.messageTransport = messageTransport != null ? new MessageTransportProxy(messageTransport) : null;
        this.impl = impl;
        this.permittedLanguages = permittedLanguages;
        this.allowExperimentalOptions = allowExperimentalOptions;
        this.out = out;
        this.err = err;
        this.in = in;
        this.logHandler = logHandler;
        this.logLevels = logLevels;
        this.probeAssertionsEnabled = initAssertProbes(engineOptions);
        this.hostLanguage = createLanguage(LanguageCache.createHostLanguageCache(hostImpl), PolyglotEngineImpl.HOST_LANGUAGE_INDEX, null);
        this.boundEngine = boundEngine;
        this.storeEngine = RUNTIME.isStoreEnabled(engineOptions);
        this.hostLanguageOnly = hostLanguageOnly;

        this.polyglotHostService = polyglotHostService;

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.languageCount = idToLanguage.values().size() + 1 /* +1 for host language */;

        this.languages = createLanguageStaticIndex();

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
        this.engineLoggerSupplier = engineLoggerSupplier;
        this.engineLogger = initializeEngineLogger(engineLoggerSupplier, logLevels);
        this.engineOptionValues = engineOptions;

        this.sourceCacheStatisticsListener = SourceCacheStatisticsListener.createOrNull(this);

        Map<String, PolyglotLanguage> publicLanguages = new LinkedHashMap<>();
        for (String key : this.idToLanguage.keySet()) {
            PolyglotLanguage languageImpl = idToLanguage.get(key);
            if (!languageImpl.cache.isInternal()) {
                publicLanguages.put(key, languageImpl);
            }
        }
        this.idToPublicLanguage = Collections.unmodifiableMap(publicLanguages);
        this.internalResourceRoots = InternalResourceRoots.getInstance();

        Map<String, PolyglotInstrument> publicInstruments = new LinkedHashMap<>();
        for (String key : this.idToInstrument.keySet()) {
            PolyglotInstrument instrumentImpl = idToInstrument.get(key);
            if (!instrumentImpl.cache.isInternal()) {
                publicInstruments.put(key, instrumentImpl);
            }
        }
        this.idToPublicInstrument = Collections.unmodifiableMap(publicInstruments);
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(this, storeEngine);

        if (isSharingEnabled(null)) {
            initializeMultiContext();
        }

        List<OptionDescriptor> deprecatedDescriptors = new ArrayList<>();
        deprecatedDescriptors.addAll(this.engineOptionValues.getUsedDeprecatedDescriptors());

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(options, languagesOptions, instrumentsOptions);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            OptionValuesImpl languageOptions = language.getOptionValues();
            Map<String, String> unparsedOptions = languagesOptions.get(language);
            parseAllOptions(languageOptions, unparsedOptions, deprecatedDescriptors);
        }

        if (engineOptionValues.get(PolyglotEngineOptions.SpecializationStatistics)) {
            this.specializationStatistics = SpecializationStatistics.create();
        } else {
            this.specializationStatistics = null;
        }

        this.runtimeData = RUNTIME.createRuntimeData(this, engineOptions, engineLoggerSupplier, sandboxPolicy);
        notifyCreated();

        if (!preInitialization) {
            createInstruments(instrumentsOptions, deprecatedDescriptors);
        }

        validateSandbox();

        printDeprecatedOptionsWarning(deprecatedDescriptors);
    }

    Engine getEngineAPI() {
        Engine result = this.weakAPI.get();
        if (result == null) {
            throw CompilerDirectives.shouldNotReachHere("API object must not be garbage collected when engine implementation is in use.");
        }
        return result;
    }

    Engine getEngineAPIOrNull() {
        /*
         * apiReference == null when PolyglotEngineImpl is not yet fully initialized
         */
        return weakAPI == null ? null : weakAPI.get();
    }

    void setEngineAPIReference(Reference<Engine> engineAPI) {
        assert engineAPI != null;
        this.weakAPI = engineAPI;
    }

    private void parseAllOptions(OptionValuesImpl targetOptions, Map<String, String> unparsedOptions, List<OptionDescriptor> deprecatedDescriptors) {
        for (var entry : unparsedOptions.entrySet()) {
            OptionDescriptor d = targetOptions.put(entry.getKey(), entry.getValue(), allowExperimentalOptions, this::getAllOptions);
            if (d != null && d.isDeprecated()) {
                deprecatedDescriptors.add(d);
            }
        }
    }

    private static boolean initAssertProbes(OptionValuesImpl engineOptions) {
        boolean assertsOn = false;
        assert !!(assertsOn = true);
        boolean assertProbes = engineOptions.get(PolyglotEngineOptions.AssertProbes);
        if (assertProbes && !assertsOn) {
            throw PolyglotEngineException.illegalState("Option engine.AssertProbes is set to true, but assertions are disabled. " +
                            "Assertions need to be enabled for this option to be functional. Pass -ea as VM option to resolve this problem.\n");
        }
        return assertProbes;
    }

    /**
     * Returns true if this engine should try to share code between language contexts.
     *
     */
    boolean isSharingEnabled(PolyglotContextConfig config) {
        boolean forced = engineOptionValues.get(PolyglotEngineOptions.ForceCodeSharing);
        boolean disabled = engineOptionValues.get(PolyglotEngineOptions.DisableCodeSharing);
        if (forced && disabled) {
            throw PolyglotEngineException.illegalState("Option engine.ForceCodeSharing can not be true at the same time as engine.DisableCodeSahring.");
        }
        forced |= config != null && config.isCodeSharingForced();
        disabled |= config != null && config.isCodeSharingDisabled();

        return !disabled && (forced || !boundEngine || storeEngine);
    }

    boolean isStoreEngine() {
        return storeEngine;
    }

    TruffleLanguage<?> getHostLanguageSPI() {
        assert hostLanguage.cache.loadLanguage() == hostLanguage.cache.loadLanguage() : "host language caches must always return the same instance";
        return hostLanguage.cache.loadLanguage();
    }

    /*
     * Sharing layers are allocated when the first non-host language context is created.
     */
    void claimSharingLayer(PolyglotSharingLayer layer, PolyglotContextImpl context, PolyglotLanguage requestingLanguage) {
        assert Thread.holdsLock(lock);
        assert !layer.isClaimed();

        for (PolyglotSharingLayer sharableLayer : sharedLayers) {
            if (layer.claimLayerForContext(sharableLayer, context, Collections.singleton(requestingLanguage))) {
                assert layer.isClaimed();
                return;
            }
        }
        boolean result = layer.claimLayerForContext(null, context, Collections.singleton(requestingLanguage));
        assert result : "new layer must be compatible";
        switch (layer.getContextPolicy()) {
            case EXCLUSIVE:
            case REUSE:
                // nothing to do, cannot be reused directly.
                break;
            case SHARED:
                assert layer.isClaimed();
                sharedLayers.add(layer);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    void freeSharingLayer(PolyglotSharingLayer layer, PolyglotContextImpl context) {
        assert Thread.holdsLock(lock);
        assert layer.isClaimed();
        layer.freeSharingLayer(context);

        switch (layer.getContextPolicy()) {
            case EXCLUSIVE:
                break;
            case REUSE:
                sharedLayers.add(layer);
                break;
            case SHARED:
                // already in sharingLayers
                assert sharedLayers.contains(layer);
                break;
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    ReferenceQueue<Source> getDeadSourcesQueue() {
        return deadSourcesQueue;
    }

    void ensureRuntimeInitialized(PolyglotContextImpl context) {
        assert Thread.holdsLock(lock);

        if (runtimeInitialized) {
            return;
        }
        runtimeInitialized = true;
        if (TruffleOptions.AOT) {
            // we do not need to trigger runtime in native image
            return;
        }

        // we crate a dummy node
        RootNode node = RootNode.createConstantNode(42);
        // since we are not entered we need to set the sharing layer here
        EngineAccessor.NODES.setSharingLayer(node, context.layer);

        // this is intended to trigger Truffle runtime initialization in the background
        node.getCallTarget();
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
        list[HOST_LANGUAGE_INDEX] = hostLanguage;
        for (PolyglotLanguage language : idToLanguage.values()) {
            assert list[language.cache.getStaticIndex()] == null : "language index used twice";
            list[language.cache.getStaticIndex()] = language;
        }
        return list;
    }

    void notifyCreated() {
        RUNTIME.onEngineCreate(this, this.runtimeData);
        impl.getRootImpl().onEngineCreated(this);
    }

    @SuppressWarnings("unchecked")
    PolyglotEngineImpl(PolyglotEngineImpl prototype) {
        this.engineId = ENGINE_COUNTER.incrementAndGet();
        this.apiAccess = prototype.apiAccess;
        this.sandboxPolicy = prototype.sandboxPolicy;
        this.messageTransport = prototype.messageTransport;
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(
                        this,
                        prototype.storeEngine);
        this.impl = prototype.impl;
        this.permittedLanguages = prototype.permittedLanguages;
        this.out = prototype.out;
        this.err = prototype.err;
        this.allowExperimentalOptions = prototype.allowExperimentalOptions;
        this.in = prototype.in;
        this.host = prototype.host;
        this.boundEngine = prototype.boundEngine;
        this.logHandler = prototype.logHandler;
        this.probeAssertionsEnabled = prototype.probeAssertionsEnabled;
        this.hostLanguage = createLanguage(LanguageCache.createHostLanguageCache(prototype.getHostLanguageSPI()), HOST_LANGUAGE_INDEX, null);
        this.engineLoggerSupplier = prototype.engineLoggerSupplier;
        this.engineLogger = prototype.engineLogger;
        this.sourceCacheStatisticsListener = prototype.sourceCacheStatisticsListener;

        this.polyglotHostService = prototype.polyglotHostService;
        this.internalResourceRoots = prototype.internalResourceRoots;

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.hostLanguageOnly = prototype.hostLanguageOnly;
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);
        this.languageCount = idToLanguage.size() + 1 /* +1 for host language */;

        this.languages = createLanguageStaticIndex();

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

        Map<String, PolyglotLanguage> publicLanguages = new LinkedHashMap<>();
        for (String key : this.idToLanguage.keySet()) {
            PolyglotLanguage languageImpl = idToLanguage.get(key);
            if (!languageImpl.cache.isInternal()) {
                publicLanguages.put(key, languageImpl);
            }
        }
        idToPublicLanguage = Collections.unmodifiableMap(publicLanguages);

        Map<String, PolyglotInstrument> publicInstruments = new LinkedHashMap<>();
        for (String key : this.idToInstrument.keySet()) {
            PolyglotInstrument instrumentImpl = idToInstrument.get(key);
            if (!instrumentImpl.cache.isInternal()) {
                publicInstruments.put(key, instrumentImpl);
            }
        }
        idToPublicInstrument = Collections.unmodifiableMap(publicInstruments);
        logLevels = prototype.logLevels;

        this.engineOptionValues = prototype.engineOptionValues.copy();

        if (isSharingEnabled(null)) {
            initializeMultiContext();
        }

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
        this.runtimeData = RUNTIME.createRuntimeData(this, prototype.engineOptionValues, prototype.engineLoggerSupplier, sandboxPolicy);
        ensureInstrumentsCreated(instrumentsToCreate);
        notifyCreated();
    }

    void printDeprecatedOptionsWarning(List<OptionDescriptor> descriptors) {
        if (descriptors == null) {
            return;
        }
        for (OptionDescriptor key : descriptors) {
            assert key.isDeprecated();
            if (engineOptionValues.get(PolyglotEngineOptions.WarnOptionDeprecation)) {
                getEngineLogger().log(Level.WARNING,
                                String.format("Option '%s' is deprecated: %s. Please update the option or suppress this warning using the option 'engine.WarnOptionDeprecation=false'.",
                                                key.getName(), key.getDeprecationMessage()));
            }
        }
    }

    TruffleLogger getEngineLogger() {
        return engineLogger;
    }

    private TruffleLogger initializeEngineLogger(Function<String, TruffleLogger> supplier, Map<String, Level> levels) {
        TruffleLogger result = supplier.apply(OPTION_GROUP_ENGINE);
        Object logger = EngineAccessor.LANGUAGE.getLoggerCache(result);
        LoggerCache loggerCache = (LoggerCache) EngineAccessor.LANGUAGE.getLoggersSPI(logger);
        loggerCache.setOwner(this);
        EngineAccessor.LANGUAGE.configureLoggers(this, levels, logger);
        return result;
    }

    Object getOrCreateEngineLoggers() {
        Object res = engineLoggers;
        if (res == null) {
            synchronized (this.lock) {
                res = engineLoggers;
                if (res == null) {
                    LoggerCache loggerCache = PolyglotLoggers.LoggerCache.newEngineLoggerCache(this);
                    res = LANGUAGE.createEngineLoggers(loggerCache);
                    EngineAccessor.LANGUAGE.configureLoggers(this, logLevels, res);
                    for (PolyglotContextImpl context : contexts) {
                        LANGUAGE.configureLoggers(context, context.config.logLevels, res);
                    }
                    engineLoggers = res;
                }
            }
        }
        return res;
    }

    static OptionDescriptors createEngineOptionDescriptors() {
        OptionDescriptors engineOptionDescriptors = new PolyglotEngineOptionsOptionDescriptors();
        OptionDescriptors compilerOptionDescriptors = EngineAccessor.RUNTIME.getRuntimeOptionDescriptors();
        return LANGUAGE.createOptionDescriptorsUnion(engineOptionDescriptors, compilerOptionDescriptors);
    }

    boolean patch(SandboxPolicy newSandboxPolicy,
                    DispatchOutputStream newOut,
                    DispatchOutputStream newErr,
                    InputStream newIn,
                    OptionValuesImpl engineOptions,
                    LogConfig newLogConfig,
                    EngineLoggerProvider logSupplier,
                    Map<String, String> newOptions,
                    boolean newAllowExperimentalOptions,
                    boolean newBoundEngine, LogHandler newLogHandler,
                    TruffleLanguage<?> newHostLanguage,
                    AbstractPolyglotHostService newPolyglotHostService) {
        CompilerAsserts.neverPartOfCompilation();
        this.sandboxPolicy = newSandboxPolicy;
        this.out = newOut;
        this.err = newErr;
        this.in = newIn;
        this.boundEngine = newBoundEngine;
        this.logHandler = newLogHandler;
        this.engineOptionValues = engineOptions;
        this.logLevels = newLogConfig.logLevels;
        if (!this.internalResourceRoots.patch(this)) {
            return false;
        }

        if (PreInitContextHostLanguage.isInstance(hostLanguage)) {
            this.hostLanguage = createLanguage(LanguageCache.createHostLanguageCache(newHostLanguage), HOST_LANGUAGE_INDEX, null);
            this.languages[HOST_LANGUAGE_INDEX] = this.hostLanguage;
        }

        polyglotHostService = newPolyglotHostService;

        /*
         * Store must only go from false to true, and never back. As it is used for
         * isSharingEnabled().
         */
        this.storeEngine = this.storeEngine || RUNTIME.isStoreEnabled(engineOptions);
        this.engineLoggerSupplier = logSupplier;
        this.engineLogger = initializeEngineLogger(logSupplier, newLogConfig.logLevels);
        /*
         * The engineLoggers must be created before calling PolyglotContextImpl#patch, otherwise the
         * engineLoggers is not in an array returned by the PolyglotContextImpl#getAllLoggers and
         * the context log levels are not set.
         */
        getOrCreateEngineLoggers();

        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();
        parseOptions(newOptions, languagesOptions, instrumentsOptions);

        sourceCacheStatisticsListener = SourceCacheStatisticsListener.createOrNull(this);

        RUNTIME.onEnginePatch(this.runtimeData, engineOptions, logSupplier, sandboxPolicy);

        List<OptionDescriptor> deprecatedDescriptors = new ArrayList<>();
        for (PolyglotLanguage language : languagesOptions.keySet()) {
            for (Map.Entry<String, String> languageOption : languagesOptions.get(language).entrySet()) {
                OptionDescriptor descriptor = language.getOptionValues().put(languageOption.getKey(), languageOption.getValue(), newAllowExperimentalOptions, this::getAllOptions);
                if (descriptor.isDeprecated()) {
                    deprecatedDescriptors.add(descriptor);
                }
            }
        }

        // Set instruments options but do not call onCreate. OnCreate is called only in case of
        // successful context patch.
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            for (Map.Entry<String, String> instrumentOption : instrumentsOptions.get(instrument).entrySet()) {
                OptionDescriptor descriptor = instrument.getEngineOptionValues().put(instrumentOption.getKey(), instrumentOption.getValue(), newAllowExperimentalOptions, this::getAllOptions);
                if (descriptor.isDeprecated()) {
                    deprecatedDescriptors.add(descriptor);
                }
            }
        }
        validateSandbox();
        printDeprecatedOptionsWarning(deprecatedDescriptors);
        return true;
    }

    static LogHandler createLogHandler(LogConfig logConfig, DispatchOutputStream errDispatchOutputStream, SandboxPolicy sandboxPolicy) {
        if (logConfig.logFile != null) {
            return createFileHandler(logConfig.logFile);
        } else {
            return PolyglotLoggers.createDefaultHandler(INSTRUMENT.getOut(errDispatchOutputStream), sandboxPolicy);
        }
    }

    private static LogHandler createFileHandler(String logFile) {
        if (ALLOW_IO) {
            return PolyglotLoggers.getFileHandler(logFile);
        } else {
            throw PolyglotEngineException.illegalState("The `log.file` option is not allowed when the allowIO() privilege is removed at image build time.");
        }
    }

    private void createInstruments(Map<PolyglotInstrument, Map<String, String>> instrumentsOptions, List<OptionDescriptor> deprecatedDescriptors) {
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            parseAllOptions(instrument.getEngineOptionValues(), instrumentsOptions.get(instrument), deprecatedDescriptors);
        }
        ensureInstrumentsCreated(instrumentsOptions.keySet());
    }

    static void ensureInstrumentsCreated(Collection<? extends PolyglotInstrument> instruments) {
        for (PolyglotInstrument instrument : instruments) {
            // we got options for this instrument -> create it.
            instrument.ensureCreated();
        }
    }

    void initializeMultiContext() {
        singleContextValue.invalidate();
    }

    static void parseEngineOptions(Map<String, String> allOptions, Map<String, String> engineOptions, LogConfig logOptions) {
        Iterator<Entry<String, String>> iterator = allOptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<String, String> entry = iterator.next();
            String key = entry.getKey();
            String value = entry.getValue();
            String group = parseOptionGroup(key);
            if (group.equals(OPTION_GROUP_ENGINE) || group.equals(OPTION_GROUP_COMPILER)) {
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
                case OPTION_GROUP_COMPILER:
                case OPTION_GROUP_LOG:
                    throw new AssertionError("Log or engine options should already be parsed.");
                case OPTION_GROUP_IMAGE_BUILD_TIME:
                    throw PolyglotEngineException.illegalArgument("Image build-time option '" + key + "' cannot be set at runtime");
            }
            throw OptionValuesImpl.failNotFound(getAllOptions(), key);
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

    @Override
    public APIAccess getAPIAccess() {
        return apiAccess;
    }

    @Override
    public PolyglotImpl getImpl() {
        return impl;
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

        if (foundLanguage != null) {
            return idToLanguage.get(foundLanguage.getId());
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

    /**
     * Finds a {@link PolyglotLanguage} for a guest or host language.
     */
    PolyglotLanguage findLanguage(LanguageInfo info) {
        LanguageCache cache = (LanguageCache) EngineAccessor.NODES.getLanguageCache(info);
        return languages[cache.getStaticIndex()];
    }

    private Map<String, PolyglotInstrument> initializeInstruments(Map<String, InstrumentInfo> infos) {
        if (hostLanguageOnly) {
            return Collections.emptyMap();
        }
        Map<String, PolyglotInstrument> instruments = new LinkedHashMap<>();
        Collection<InstrumentCache> cachedInstruments = InstrumentCache.load().values();
        for (InstrumentCache instrumentCache : cachedInstruments) {
            PolyglotInstrument instrumentImpl = new PolyglotInstrument(this, instrumentCache);
            instrumentImpl.info = LANGUAGE.createInstrument(instrumentImpl, instrumentCache.getId(), instrumentCache.getName(), instrumentCache.getVersion());

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
        PolyglotLanguage languageImpl = new PolyglotLanguage(this, cache, index, initError);
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

        ensureRuntimeInitialized(context);

        if (limits != null) {
            limits.validate(context.config.limits);
        }
        contexts.add(context);

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
        contexts.remove(context);
    }

    void disposeContext(PolyglotContextImpl context) {
        synchronized (this.lock) {
            context.freeSharing();
            removeContext(context);
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
            TruffleContext creatorTruffleContext = context.getCreatorTruffleContext();
            listener.onContextCreated(creatorTruffleContext);
            for (PolyglotLanguageContext lc : context.contexts) {
                LanguageInfo language = lc.language.info;
                if (lc.eventsEnabled && lc.env != null) {
                    listener.onLanguageContextCreate(creatorTruffleContext, language);
                    listener.onLanguageContextCreated(creatorTruffleContext, language);
                    if (lc.isInitialized()) {
                        listener.onLanguageContextInitialize(creatorTruffleContext, language);
                        listener.onLanguageContextInitialized(creatorTruffleContext, language);
                        if (lc.finalized) {
                            listener.onLanguageContextFinalized(creatorTruffleContext, language);
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
                listener.onThreadInitialized(context.getCreatorTruffleContext(), thread);
            }
        }
    }

    PolyglotLanguage requireLanguage(String id, boolean allowInternal) {
        checkState();
        Map<String, PolyglotLanguage> useLanguages;
        if (allowInternal) {
            useLanguages = idToLanguage;
        } else {
            useLanguages = idToPublicLanguage;
        }
        PolyglotLanguage language = useLanguages.get(id);
        if (language == null) {
            throw throwNotInstalled(id, useLanguages.keySet());
        }
        return language;
    }

    private RuntimeException throwNotInstalled(String id, Set<String> allLanguages) {
        String misspelledGuess = matchSpellingError(allLanguages, id);
        String didYouMean = "";
        if (misspelledGuess != null) {
            didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
        }
        String internalLanguageHint = "";
        if (idToLanguage.containsKey(id)) {
            // no access to internal, but internal language available
            internalLanguageHint = "A language with this id is installed, but only available internally. ";
        }
        throw PolyglotEngineException.illegalArgument(String.format("A language with id '%s' is not available. %s%sAvailable languages are: %s.", id, didYouMean, internalLanguageHint, allLanguages));
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

    public PolyglotInstrument requirePublicInstrument(String id) {
        checkState();
        PolyglotInstrument instrument = idToPublicInstrument.get(id);
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
            if (languageClass == getHostLanguageSPI().getClass()) {
                return hostLanguage;
            }
            if (fail) {
                Set<String> languageNames = classToLanguage.keySet();
                throw PolyglotEngineException.illegalArgument("Cannot find language " + languageClass + " among " + languageNames);
            }
        }
        return foundLanguage;
    }

    boolean storeCache(Path targetPath, long cancelledWord) {
        if (!TruffleOptions.AOT) {
            throw new UnsupportedOperationException("Storing the engine cache is only supported on native-image hosts.");
        }

        synchronized (this.lock) {
            if (closingThread != null || closed) {
                throw new IllegalStateException("The engine is already closed and cannot be cancelled or persisted.");
            }
            if (!storeEngine) {
                throw new IllegalStateException(
                                "In order to store the cache the option 'engine.CacheStoreEnabled' must be set to 'true'.");
            }
            List<PolyglotContextImpl> localContexts = collectAliveContexts();
            if (!localContexts.isEmpty()) {
                throw new IllegalStateException("There are still alive contexts that need to be closed or cancelled before the engine can be persisted.");
            }

            return RUNTIME.onStoreCache(this.runtimeData, targetPath, cancelledWord);
        }
    }

    void ensureClosed(boolean force, boolean initiatedByContext) {
        synchronized (this.lock) {
            Thread currentThread = Thread.currentThread();
            boolean interrupted = false;
            if (closingThread == currentThread) {
                return;
            }
            while (closingThread != null) {
                try {
                    this.lock.wait();
                } catch (InterruptedException ie) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                currentThread.interrupt();
            }
            if (closed) {
                return;
            }
            List<PolyglotContextImpl> localContexts = collectAliveContexts();
            /*
             * Check ahead of time for open contexts to fail early and avoid closing only some
             * contexts.
             */
            if (!force) {
                for (PolyglotContextImpl context : localContexts) {
                    assert !Thread.holdsLock(context);
                    synchronized (context) {
                        if (context.hasActiveOtherThread(false, false) && !context.state.isClosing()) {
                            throw PolyglotEngineException.illegalState(String.format("One of the context instances is currently executing. " +
                                            "Set cancelIfExecuting to true to stop the execution on this thread."));
                        }
                    }
                }
            }

            closingThread = currentThread;
            try {
                if (!initiatedByContext) {
                    /*
                     * context.cancel and context.closeAndMaybeWait close the engine if it is bound
                     * to the context, so if we called these methods here, it might lead to
                     * StackOverflowError.
                     */
                    for (PolyglotContextImpl context : localContexts) {
                        assert !Thread.holdsLock(context);
                        assert context.parent == null;
                        if (force) {
                            context.cancel(false, null);
                        } else {
                            context.closeAndMaybeWait(false, null);
                        }
                    }
                }

                contexts.clear();
            } finally {
                /*
                 * RuntimeSupport#onEngineClosing must be called without the closingThread set.
                 * Otherwise, it will store a running thread into an auxiliary image.
                 */
                closingThread = null;
            }

            if (RUNTIME.onEngineClosing(this.runtimeData)) {
                getAPIAccess().engineClosed(weakAPI);
                return;
            }
            closingThread = currentThread;
        }

        try {
            // instruments should be shut-down even if they are currently still executed
            // we want to see instrument output if the process is quit while executing.
            for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                instrumentImpl.ensureFinalized();
            }
            for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                instrumentImpl.ensureClosed();
            }
        } catch (Throwable t) {
            synchronized (this.lock) {
                closingThread = null;
                this.lock.notifyAll();
            }
            throw t;
        }

        synchronized (this.lock) {
            closed = true;
            closingThread = null;
            this.lock.notifyAll();
            if (!activeSystemThreads.isEmpty()) {
                InstrumentSystemThread thread = activeSystemThreads.iterator().next();
                StringBuilder stack = new StringBuilder("Alive system thread '");
                stack.append(thread.getName());
                stack.append('\'');
                stack.append(System.lineSeparator());
                for (StackTraceElement e : thread.getStackTrace()) {
                    stack.append("\tat ");
                    stack.append(e);
                    stack.append(System.lineSeparator());
                }
                throw new IllegalStateException(String.format("%sThe engine has an alive system thread '%s' created by instrument %s.", stack, thread.getName(), thread.instrumentId));
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

            RUNTIME.onEngineClosed(this.runtimeData);
            if (sourceCacheStatisticsListener != null) {
                sourceCacheStatisticsListener.onEngineClose(this);
            }

            Object loggers = getEngineLoggers();
            if (loggers != null) {
                LANGUAGE.closeEngineLoggers(loggers);
            }
            if (logHandler != null) {
                logHandler.close();
            }

            polyglotHostService.notifyEngineClosed(this, force);

            if (runtimeData != null) {
                EngineAccessor.RUNTIME.flushCompileQueue(runtimeData);
            }
            getAPIAccess().engineClosed(weakAPI);
        }
    }

    List<PolyglotContextImpl> collectAliveContexts() {
        assert Thread.holdsLock(this.lock);
        List<PolyglotContextImpl> localContexts = new ArrayList<>(contexts.size());
        for (PolyglotContextImpl context : contexts) {
            localContexts.add(context);
        }
        return localContexts;
    }

    public Map<String, PolyglotInstrument> getInstruments() {
        checkState();
        return idToPublicInstrument;
    }

    public Map<String, PolyglotLanguage> getPublicLanguages() {
        checkState();
        return idToPublicLanguage;
    }

    public OptionDescriptors getOptions() {
        checkState();
        return engineOptionValues.getDescriptors();
    }

    public Set<Object> getCachedSources() {
        checkState();
        Set<Object> sources = new HashSet<>();
        List<PolyglotContextImpl> activeContexts;
        synchronized (lock) {
            activeContexts = collectAliveContexts();
        }
        for (PolyglotContextImpl context : activeContexts) {
            context.layer.listCachedSources(sources);
        }
        synchronized (lock) {
            for (PolyglotSharingLayer layer : sharedLayers) {
                layer.listCachedSources(sources);
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
                    allOptions = LANGUAGE.createOptionDescriptorsUnion(allDescriptors.toArray(OptionDescriptors[]::new));
                }
            }
        }
        return allOptions;
    }

    OptionDescriptors getAllSourceOptions() {
        checkState();
        OptionDescriptors d = this.allSourceOptions;
        if (d == null) {
            synchronized (this.lock) {
                d = this.allSourceOptions;
                if (d == null) {
                    List<OptionDescriptors> allDescriptors = new ArrayList<>();
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        allDescriptors.add(language.getSourceOptionsInternal());
                    }
                    for (PolyglotInstrument instrument : idToInstrument.values()) {
                        allDescriptors.add(instrument.getSourceOptionsInternal());
                    }
                    d = allSourceOptions = LANGUAGE.createOptionDescriptorsUnion(allDescriptors.toArray(OptionDescriptors[]::new));
                }
            }
        }
        return d;
    }

    PolyglotContextImpl getPreInitializedContext() {
        return preInitializedContext.get();
    }

    void preInitialize() {
        synchronized (this.lock) {
            inEnginePreInitialization = true;
            try {
                if (isSharingEnabled(null)) {
                    for (PolyglotSharingLayer layer : sharedLayers) {
                        if (!layer.isClaimed()) {
                            continue;
                        }
                        layer.preInitialize();
                    }
                } else {
                    final String oldOption = engineOptionValues.get(PolyglotEngineOptions.PreinitializeContexts);
                    final String newOption = ImageBuildTimeOptions.get(ImageBuildTimeOptions.PREINITIALIZE_CONTEXTS_NAME);
                    final String optionValue;
                    if (!oldOption.isEmpty() && !newOption.isEmpty()) {
                        optionValue = oldOption + "," + newOption;
                    } else {
                        optionValue = oldOption + newOption;
                    }

                    final Set<String> languageIds = new HashSet<>();
                    if (!optionValue.isEmpty()) {
                        Collections.addAll(languageIds, optionValue.split(","));
                    }
                    final Set<PolyglotLanguage> preinitLanguages = new HashSet<>();
                    for (String id : languageIds) {
                        PolyglotLanguage language = this.idToLanguage.get(id);
                        if (language != null && !language.cache.isInternal()) {
                            preinitLanguages.add(language);
                        }
                    }

                    boolean allowNativeAccess = ImageBuildTimeOptions.getBoolean(ImageBuildTimeOptions.PREINITIALIZE_CONTEXTS_WITH_NATIVE_NAME);
                    PreinitConfig preinitConfig = allowNativeAccess ? PreinitConfig.DEFAULT_WITH_NATIVE_ACCESS : PreinitConfig.DEFAULT;
                    this.preInitializedContext.set(PolyglotContextImpl.preinitialize(this, preinitConfig, null, preinitLanguages, true));
                }
            } finally {
                inEnginePreInitialization = false;
            }
        }
    }

    record FinalizationResult(DispatchOutputStream out, DispatchOutputStream err, InputStream in) {
    }

    /**
     * Invoked when the context is closing to prepare an engine to be stored.
     */
    FinalizationResult finalizeStore() {
        assert Thread.holdsLock(this.lock);
        FinalizationResult result = new FinalizationResult(out, err, in);
        this.out = null;
        this.err = null;
        this.in = null;
        /*
         * Note: The 'logHandler' field must not be cleaned until the image persists to facilitate
         * the detection and cleanup of all references to the 'logHandler' instance in the image
         * heap. The cleanup process is handled by the 'AuxiliaryImageObjectReplacer' registered in
         * the 'AuxiliaryEngineCacheSupport'.
         */
        AbstractHostLanguageService hostLanguageService = this.host;
        if (hostLanguageService != null) {
            hostLanguageService.release();
        }
        return result;
    }

    void restoreStore(FinalizationResult result) {
        this.out = result.out;
        this.err = result.err;
        this.in = result.in;
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

    static void cancelOrExit(PolyglotContextImpl context, List<Future<Void>> cancelationFutures) {
        cancelOrExitOrInterrupt(context, cancelationFutures, 0, null);
    }

    static boolean cancelOrExitOrInterrupt(PolyglotContextImpl context, List<Future<Void>> futures, long startMillis, Duration timeout) {
        try {
            assert singleThreadedOrNotActive(context) : "Cancel while active is only allowed for single-threaded contexts!";
            if (timeout == null) {
                boolean closeCompleted = context.closeImpl(true);
                assert closeCompleted : "Close was not completed!";
            } else {
                return waitForThreads(context, startMillis, timeout);
            }
        } finally {
            boolean interrupted = false;
            for (Future<Void> future : futures) {
                boolean timedOut = false;
                try {
                    if (timeout != null && timeout != Duration.ZERO) {
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
                } catch (ExecutionException e) {
                    throw CompilerDirectives.shouldNotReachHere(e);
                } catch (InterruptedException e) {
                    interrupted = true;
                } catch (CancellationException e) {
                    // Expected
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                if (timedOut) {
                    return false;
                }
            }
        }
        return true;
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    private static boolean singleThreadedOrNotActive(PolyglotContextImpl context) {
        synchronized (context) {
            return context.singleThreaded || !context.isActive(Thread.currentThread());
        }
    }

    private static boolean waitForThreads(PolyglotContextImpl context, long startMillis, Duration timeout) {
        long cancelTimeoutMillis = timeout != Duration.ZERO ? timeout.toMillis() : 0;
        boolean success = true;
        if (!context.waitForAllThreads(startMillis, cancelTimeoutMillis)) {
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
    @SuppressWarnings("serial")
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
            SourceSection section = getEncapsulatingSourceSection();
            if (section == null) {
                throw UnsupportedMessageException.create();
            }
            return section;
        }
    }

    private static final String DISABLE_PRIVILEGES_VALUE = ImageBuildTimeOptions.get(ImageBuildTimeOptions.DISABLE_PRIVILEGES_NAME);
    private static final String[] DISABLED_PRIVILEGES = DISABLE_PRIVILEGES_VALUE.isEmpty() ? new String[0] : DISABLE_PRIVILEGES_VALUE.split(",");

    // reflectively read from TruffleBaseFeature
    static final boolean ALLOW_CREATE_PROCESS;
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
    public Context createContext(Engine engineAPI, SandboxPolicy contextSandboxPolicy, OutputStream configOut, OutputStream configErr, InputStream configIn, boolean allowHostLookup,
                    Object hostAccess, Object polyglotAccess,
                    boolean allowNativeAccess, boolean allowCreateThread, Consumer<String> threadAccessDeniedHandler, boolean allowHostClassLoading, boolean allowContextOptions,
                    boolean allowExperimentalOptions,
                    Predicate<String> classFilter, Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguagesArray, Object ioAccess, Object handler,
                    boolean allowCreateProcess, ProcessHandler processHandler, Object environmentAccess, Map<String, String> environment, ZoneId zone, Object limitsImpl,
                    String currentWorkingDirectory, String tmpDir, ClassLoader hostClassLoader, boolean allowValueSharing, boolean useSystemExit, boolean registerInActiveContexts) {
        PolyglotContextImpl context;
        Context contextAPI;
        boolean replayEvents;
        try {
            assert sandboxPolicy == contextSandboxPolicy : "Engine and context must have the same SandboxPolicy.";
            synchronized (this.lock) {
                checkState();
                if (boundEngine && !contexts.isEmpty()) {
                    throw PolyglotEngineException.illegalArgument("Automatically created engines cannot be used to create more than one context. " +
                                    "Use Engine.newBuilder().build() to construct a new engine and pass it using Context.newBuilder().engine(engine).build().");
                }
            }

            Set<String> allowedLanguages = Collections.emptySet();
            if (onlyLanguagesArray.length == 0) {
                if (this.permittedLanguages.length != 0) {
                    // no restrictions set on context but restrictions set on engine
                    allowedLanguages = new HashSet<>();
                    allowedLanguages.addAll(Arrays.asList(this.permittedLanguages));
                }
            } else {
                if (this.permittedLanguages.length == 0) {
                    allowedLanguages = new HashSet<>();
                    allowedLanguages.addAll(Arrays.asList(onlyLanguagesArray));
                } else {
                    // restrictions set on both engine and context
                    EconomicSet<String> engineLanguages = EconomicSet.create();
                    engineLanguages.addAll(Arrays.asList(this.permittedLanguages));

                    allowedLanguages = new HashSet<>();
                    allowedLanguages.addAll(Arrays.asList(onlyLanguagesArray));

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

            String error = getAPIAccess().validatePolyglotAccess(polyglotAccess, allowedLanguages.isEmpty() ? getPublicLanguages().keySet() : allowedLanguages);
            if (error != null) {
                throw PolyglotEngineException.illegalArgument(error);
            }
            final FileSystem customFileSystem = getImpl().getIO().getFileSystem(ioAccess);
            final boolean allowHostFileAccess = getImpl().getIO().hasHostFileAccess(ioAccess);
            final FileSystemConfig fileSystemConfig;
            if (!ALLOW_IO) {
                if (allowHostFileAccess) {
                    throw PolyglotEngineException.illegalArgument("Cannot allowHostFileAccess() because the privilege is removed at image build time");
                }
                FileSystem fs = customFileSystem != null ? customFileSystem : FileSystems.newDenyIOFileSystem();
                fileSystemConfig = new FileSystemConfig(ioAccess, fs, fs);
            } else if (allowHostFileAccess) {
                FileSystem fs = FileSystems.newDefaultFileSystem(tmpDir);
                fileSystemConfig = new FileSystemConfig(ioAccess, fs, fs);
            } else if (customFileSystem != null) {
                fileSystemConfig = new FileSystemConfig(ioAccess, customFileSystem, customFileSystem);
            } else {
                fileSystemConfig = new FileSystemConfig(ioAccess, FileSystems.newDenyIOFileSystem(), FileSystems.newResourcesFileSystem(this));
            }
            if (currentWorkingDirectory != null) {
                Path publicFsCwd;
                Path internalFsCwd;
                try {
                    publicFsCwd = fileSystemConfig.fileSystem.parsePath(currentWorkingDirectory);
                    internalFsCwd = fileSystemConfig.internalFileSystem.parsePath(currentWorkingDirectory);
                } catch (IllegalArgumentException e) {
                    throw PolyglotEngineException.illegalArgument(e);
                } catch (UnsupportedOperationException e) {
                    throw PolyglotEngineException.illegalArgument(new IllegalArgumentException(e));
                }
                fileSystemConfig.fileSystem.setCurrentWorkingDirectory(publicFsCwd);
                fileSystemConfig.internalFileSystem.setCurrentWorkingDirectory(internalFsCwd);
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
            String logFile = options.remove(LOG_FILE_OPTION);
            LogHandler useHandler;
            if (handler != null) {
                useHandler = (LogHandler) handler;
            } else if (logFile != null) {
                useHandler = createFileHandler(logFile);
            } else if (logHandler != null && !PolyglotLoggers.isDefault(logHandler)) {
                useHandler = logHandler;
            } else {
                useHandler = PolyglotLoggers.createDefaultHandler(configErr == null ? INSTRUMENT.getOut(this.err) : configErr, contextSandboxPolicy);
            }
            final InputStream useIn = configIn == null ? this.in : configIn;
            final ProcessHandler useProcessHandler;
            if (allowCreateProcess) {
                if (!ALLOW_CREATE_PROCESS) {
                    throw PolyglotEngineException.illegalArgument("Cannot allowCreateProcess() because the privilege is removed at image build time");
                }
                useProcessHandler = processHandler != null ? processHandler : getImpl().newDefaultProcessHandler();
            } else {
                useProcessHandler = null;
            }
            if (!ALLOW_ENVIRONMENT_ACCESS && environmentAccess != EnvironmentAccess.NONE) {
                throw PolyglotEngineException.illegalArgument("Cannot allow EnvironmentAccess because the privilege is removed at image build time");
            }
            PolyglotLimits polyglotLimits = (PolyglotLimits) limitsImpl;
            PolyglotContextConfig config = new PolyglotContextConfig(this, contextSandboxPolicy, null, useOut, useErr, useIn,
                            allowHostLookup, polyglotAccess, allowNativeAccess, allowCreateThread, threadAccessDeniedHandler, allowHostClassLoading, allowContextOptions,
                            allowExperimentalOptions, classFilter, arguments, allowedLanguages, options, fileSystemConfig, useHandler, allowCreateProcess, useProcessHandler,
                            environmentAccess, environment, zone, polyglotLimits, hostClassLoader, hostAccess, allowValueSharing, useSystemExit, null, null, null, null);
            contextAPI = loadPreinitializedContext(config, engineAPI, registerInActiveContexts);
            replayEvents = false;
            if (contextAPI == null) {
                synchronized (this.lock) {
                    checkState();
                    context = new PolyglotContextImpl(this, config);
                    contextAPI = getAPIAccess().newContext(impl.contextDispatch, context, engineAPI, registerInActiveContexts);
                    addContext(context);
                }
            } else {
                context = (PolyglotContextImpl) apiAccess.getContextReceiver(contextAPI);
                if (context.engine == this) {
                    replayEvents = true;
                }
            }
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
        boolean hasContextBindings;
        boolean hasThreadBindings;
        try {
            try {
                if (replayEvents) { // loaded context
                    /*
                     * There might be new instruments to run with a preinitialized context and these
                     * instruments might define context locals and context thread locals. The
                     * instruments were created during the context loading before the context was
                     * added to the engine's contexts set, and so the instrument creation did not
                     * update the context's locals and thread locals. Since the context loading
                     * needs to enter the context on the current thread for patching, we need to
                     * update both the context locals and context thread locals.
                     */
                    synchronized (context) {
                        context.resizeContextLocals(this.contextLocalLocations);
                        context.initializeInstrumentContextLocals(context.contextLocals);
                        context.resizeContextThreadLocals(this.contextThreadLocalLocations);
                        context.initializeInstrumentContextThreadLocals();
                    }
                } else { // is new context
                    synchronized (context) {
                        context.initializeContextLocals();
                        context.notifyContextCreated();
                    }
                }
            } catch (Throwable t) {
                Reference<Context> contextReference = context.getContextAPIReference();
                apiAccess.contextClosed(contextReference);
                context.engine.disposeContext(context);
                contextReference.clear();
                if (boundEngine) {
                    context.engine.ensureClosed(false, false);
                }
                throw t;
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
        /*
         * When registerInActiveContext is false, the context is a local context decorated by its
         * owning context. In this case, there's no need to process the reference queue, as it will
         * be processed by the owner.
         */
        if (registerInActiveContexts) {
            getAPIAccess().processReferenceQueue();
        }
        return contextAPI;
    }

    private Context loadPreinitializedContext(PolyglotContextConfig config, Engine engineAPI, boolean registerInActiveContexts) {
        PolyglotContextImpl context = null;
        final boolean sharing = isSharingEnabled(config);
        if (sharing) {
            assert preInitializedContext.get() == null : "sharing enabled with preinitialized regular context. sharing requires context preinit per layer.";
            synchronized (this.lock) {
                for (PolyglotSharingLayer sharedLayer : sharedLayers) {
                    context = sharedLayer.loadPreinitializedContext(config);
                    if (context != null) {
                        break;
                    }
                }
            }
        } else {
            context = preInitializedContext.getAndSet(null);
        }

        if (context == null) {
            return null;
        }

        if (!getEngineOptionValues().get(PolyglotEngineOptions.UsePreInitializedContext)) {
            return null;
        }

        if (getEngineOptionValues().get(PolyglotEngineOptions.StaticObjectStorageStrategy) != PolyglotEngineOptions.StaticObjectStorageStrategy.getDefaultValue()) {
            return null;
        }

        FileSystemConfig oldFileSystemConfig = config.fileSystemConfig;
        config.fileSystemConfig = FileSystemConfig.createPatched(context.config.fileSystemConfig, config.fileSystemConfig);

        boolean patchResult = false;
        Context contextAPI = apiAccess.newContext(impl.contextDispatch, context, engineAPI, registerInActiveContexts);
        synchronized (this.lock) {
            addContext(context);
        }
        try {
            patchResult = context.patch(config);
        } finally {
            if (patchResult && arePreInitializedLanguagesCompatible(context, config)) {
                synchronized (this.lock) {
                    removeContext(context);
                }
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
                config.fileSystemConfig = oldFileSystemConfig;
                if (sharing) {
                    contextAPI = null;
                } else {
                    PolyglotEngineImpl newEngine = new PolyglotEngineImpl(this);
                    Engine newEngineAPI = apiAccess.newEngine(impl.engineDispatch, newEngine, registerInActiveContexts);
                    // If the patching fails we have to perform a silent engine close without
                    // notifying the new polyglotHostService.
                    polyglotHostService = new DefaultPolyglotHostService(impl);
                    /*
                     * The engine close must not close the log handler, the same log handler
                     * instance is used for newly created engine.
                     */
                    logHandler = null;
                    ensureClosed(true, false);
                    synchronized (newEngine.lock) {
                        context = new PolyglotContextImpl(newEngine, config);
                        contextAPI = apiAccess.newContext(impl.contextDispatch, context, newEngineAPI, registerInActiveContexts);
                        newEngine.addContext(context);
                    }
                }
            }
        }
        return contextAPI;
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
        prev = context.enterThreadChanged(enterReverted, pollSafepoint, false, null, false);
        assert verifyContext(context);
        return prev;
    }

    private static boolean verifyContext(PolyglotContextImpl context) {
        PolyglotContextImpl.State localState = context.state;
        return context == PolyglotFastThreadLocals.getContext(null) || localState.isInvalidOrClosed();
    }

    /**
     * Use to leave contexts from paths either compiled or not. If always compiled use
     * {@link #leaveCached(Object[], PolyglotContextImpl)}.
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
     * {@link #leave(Object[], PolyglotContextImpl)}.
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
        context.leaveThreadChanged(prev, entered, false);
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

    void onVMShutdown() {
        logMissingClose();
        /*
         * In the event of VM shutdown, the engine is not closed and active instruments on it are
         * not disposed. Without closing the contexts on the engine, which is not possible during
         * shutdown, because it could delay or block the shutdown, closing the engine could lead to
         * unexpected errors. Therefore, we let the engine die with the VM and just notify the
         * instruments by calling TruffleInstrument#onFinalize.
         */
        for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
            try {
                instrumentImpl.ensureFinalized();
            } catch (Throwable e) {
                getEngineLogger().log(Level.WARNING, "Instrument " + instrumentImpl.getName() + " threw an exception during onFinalize.", e);
            }
        }
        synchronized (this.lock) {
            if (logHandler != null) {
                logHandler.flush();
            }
        }
    }

    void logMissingClose() {
        if (DEBUG_MISSING_CLOSE) {
            PrintStream log = System.out;
            log.println("Missing close on vm shutdown: ");
            log.print(" InitializedLanguages:");
            synchronized (lock) {
                for (PolyglotContextImpl context : collectAliveContexts()) {
                    for (PolyglotLanguageContext langContext : context.contexts) {
                        if (langContext.env != null) {
                            log.print(langContext.language.getId());
                            log.print(", ");
                        }
                    }
                }
            }
            log.println();
            createdLocation.printStackTrace();
        }
    }

    void addSystemThread(InstrumentSystemThread thread) {
        synchronized (lock) {
            if (!closed) {
                activeSystemThreads.add(thread);
            }
        }
    }

    void removeSystemThread(InstrumentSystemThread thread) {
        synchronized (lock) {
            activeSystemThreads.remove(thread);
        }
    }

    private void validateSandbox() {
        if (sandboxPolicy == SandboxPolicy.TRUSTED) {
            return;
        }
        for (String permittedLanguage : permittedLanguages) {
            idToLanguage.get(permittedLanguage).validateSandbox(sandboxPolicy);
        }
    }

    void onEngineCollected() {
        try {
            logMissingClose();
            ensureClosed(false, false);
        } catch (PolyglotException pe) {
            // Don't log cancel exception, it's expected
            if (!pe.isExit() && !pe.isCancelled()) {
                logCloseOnCollectedError(pe);
            }
        } catch (PolyglotContextImpl.ExitException | CancelExecution ee) {
            // Don't log exit exception, it's expected
        } catch (Throwable t) {
            logCloseOnCollectedError(t);
        }
    }

    private void logCloseOnCollectedError(Throwable exception) {
        logCloseOnCollectedError(this, "Exception encountered while closing a garbage collected engine.", exception);
    }

    static void logCloseOnCollectedError(PolyglotEngineImpl engine, String reason, Throwable exception) {
        switch (engine.getEngineOptionValues().get(PolyglotEngineOptions.CloseOnGCFailureAction)) {
            case Ignore -> {
            }
            case Print -> {
                StringWriter message = new StringWriter();
                try (PrintWriter errWriter = new PrintWriter(message)) {
                    errWriter.printf("""
                                    [engine] WARNING: %s
                                    To customize the behavior of this warning, use 'engine.CloseOnGCFailureAction' option or the 'polyglot.engine.CloseOnGCFailureAction' system property.
                                    The accepted values are:
                                      - Ignore:    Do not print this warning.
                                      - Print:     Print this warning (default value).
                                      - Throw:     Throw an exception instead of printing this warning.
                                    """, reason);
                    exception.printStackTrace(errWriter);
                }
                logFallback(message.toString());
            }
            case Throw -> throw new RuntimeException(reason, exception);
        }
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
                    PolyglotImpl polyglot = PolyglotImpl.findInstance();
                    TruffleLanguage<Object> hostLanguage = polyglot.createHostLanguage(polyglot.createHostAccess());
                    fallbackEngine = PolyglotImpl.findInstance().createDefaultEngine(hostLanguage);
                }
            }
        }
        return fallbackEngine;
    }

    /*
     * Invoked by TruffleBaseFeature to make sure the fallback engine is not contained in the image.
     */
    static void resetFallbackEngine() {
        PolyglotEngineImpl engineToClose = null;
        synchronized (PolyglotImpl.class) {
            if (fallbackEngine != null) {
                engineToClose = fallbackEngine;
                fallbackEngine = null;
            }
        }
        if (engineToClose != null) {
            engineToClose.ensureClosed(false, false);
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

    Map<String, Path> languageHomes() {
        Map<String, Path> languageHomes = new HashMap<>();
        for (PolyglotLanguage language : languages) {
            if (language != null) {
                LanguageCache cache = language.cache;
                String languageHome = cache.getLanguageHome();
                if (languageHome != null) {
                    languageHomes.put(cache.getId(), Path.of(languageHome));
                }
            }
        }
        return languageHomes;
    }

    private final AtomicBoolean warnedVirtualThreadSupport = new AtomicBoolean(false);

    @SuppressWarnings("try")
    void validateVirtualThreadCreation() {
        if (!warnedVirtualThreadSupport.get() && warnedVirtualThreadSupport.compareAndSet(false, true)) {
            try (AbstractPolyglotImpl.ThreadScope scope = impl.getRootImpl().createThreadScope()) {
                var options = getEngineOptionValues();
                boolean warnVirtualThreadSupport = options.get(PolyglotEngineOptions.WarnVirtualThreadSupport);

                if (warnVirtualThreadSupport && !(Truffle.getRuntime() instanceof DefaultTruffleRuntime)) {
                    if (!TruffleOptions.AOT) {
                        getEngineLogger().warning("""
                                        Using polyglot contexts on Java virtual threads on HotSpot is experimental in this release,
                                        because access to caller frames in write or materialize mode is not yet supported on virtual threads (some tools and languages depend on that).
                                        To disable this warning use the '--engine.WarnVirtualThreadSupport=false' option or the '-Dpolyglot.engine.WarnVirtualThreadSupport=false' system property.
                                        """);
                    } else {
                        getEngineLogger().warning(
                                        """
                                                        Using polyglot contexts on Java virtual threads on Native Image currently uses one platform thread per VirtualThread.
                                                        This will prevent creating many virtual threads and have different performance characteristics.
                                                        You can either suppress this warning with the '--engine.WarnVirtualThreadSupport=false' option or the '-Dpolyglot.engine.WarnVirtualThreadSupport=false' system property,
                                                        or use the default runtime (no JIT compilation of polyglot code) by passing -Dtruffle.UseFallbackRuntime=true when building the native image.
                                                        Full VirtualThread support for Native Image together with polyglot contexts will be added in a future release.
                                                        VirtualThread is fully supported with polyglot contexts in JVM mode.
                                                        """);
                    }
                }
            }
        }

        impl.getRootImpl().validateVirtualThreadCreation(getEngineOptionValues());
    }

    /**
     * Logs a message when other logging mechanisms, such as {@link TruffleLogger} or the context's
     * error stream, are unavailable. This can occur, for instance, in the event of a log handler
     * failure.
     * <p>
     * On HotSpot, this method writes the message to {@code System.err}. When running on a native
     * image, this method is substituted to delegate logging to the native image's
     * {@link org.graalvm.nativeimage.LogHandler}.
     *
     * @param message the message to log
     */
    static void logFallback(String message) {
        PrintStream err = System.err;
        err.print(message);
    }

    private static class MessageTransportProxy implements MessageTransport {

        private final MessageTransport transport;

        MessageTransportProxy(MessageTransport transport) {
            this.transport = transport;
        }

        @Override
        public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) throws IOException, VetoException {
            Objects.requireNonNull(peerEndpoint, "The peer endpoint must be non null.");
            MessageEndpoint openedEndpoint = transport.open(uri, new MessageEndpointProxy(peerEndpoint));
            if (openedEndpoint == null) {
                return null;
            }
            return new MessageEndpointProxy(openedEndpoint);
        }

        private static class MessageEndpointProxy implements MessageEndpoint {

            private final MessageEndpoint endpoint;

            MessageEndpointProxy(MessageEndpoint endpoint) {
                this.endpoint = endpoint;
            }

            @Override
            public void sendText(String text) throws IOException {
                endpoint.sendText(text);
            }

            @Override
            public void sendBinary(ByteBuffer data) throws IOException {
                endpoint.sendBinary(data);
            }

            @Override
            public void sendPing(ByteBuffer data) throws IOException {
                endpoint.sendPing(data);
            }

            @Override
            public void sendPong(ByteBuffer data) throws IOException {
                endpoint.sendPong(data);
            }

            @Override
            public void sendClose() throws IOException {
                endpoint.sendClose();
            }
        }
    }

}
