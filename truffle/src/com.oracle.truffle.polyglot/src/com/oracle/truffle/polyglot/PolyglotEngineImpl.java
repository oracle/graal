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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
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
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.MessageTransport;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.ThreadsListener;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.polyglot.PolyglotContextImpl.ContextWeakReference;
import com.oracle.truffle.polyglot.PolyglotLimits.EngineLimits;

final class PolyglotEngineImpl extends AbstractPolyglotImpl.AbstractEngineImpl implements com.oracle.truffle.polyglot.PolyglotImpl.VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "host";

    static final String OPTION_GROUP_ENGINE = "engine";
    static final String OPTION_GROUP_LOG = "log";
    static final String OPTION_GROUP_IMAGE_BUILD_TIME = "image-build-time";
    private static final String PROP_ALLOW_EXPERIMENTAL_OPTIONS = OptionValuesImpl.SYSTEM_PROPERTY_PREFIX + OPTION_GROUP_ENGINE + ".AllowExperimentalOptions";

    // also update list in LanguageRegistrationProcessor
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList(HOST_LANGUAGE_ID, "graal", "truffle", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm",
                                    OPTION_GROUP_ENGINE, OPTION_GROUP_LOG, OPTION_GROUP_IMAGE_BUILD_TIME));

    private static final Map<PolyglotEngineImpl, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean shutdownHookInitialized = false;
    private static final boolean DEBUG_MISSING_CLOSE = Boolean.getBoolean("polyglotimpl.DebugMissingClose");

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

    final OptionDescriptors engineOptions;
    final OptionValuesImpl engineOptionValues;

    ClassLoader contextClassLoader;     // effectively final
    boolean boundEngine;    // effectively final
    Handler logHandler;     // effectively final
    final Exception createdLocation = DEBUG_MISSING_CLOSE ? new Exception() : null;
    private final EconomicSet<ContextWeakReference> contexts = EconomicSet.create(Equivalence.IDENTITY);
    final ReferenceQueue<PolyglotContextImpl> contextsReferenceQueue = new ReferenceQueue<>();
    private final AtomicReference<PolyglotContextImpl> preInitializedContext = new AtomicReference<>();

    PolyglotLanguage hostLanguage;
    final Assumption singleContext = Truffle.getRuntime().createAssumption("Single context per engine.");
    final Assumption singleThreadPerContext = Truffle.getRuntime().createAssumption("Single thread per context of an engine.");
    final Assumption noInnerContexts = Truffle.getRuntime().createAssumption("No inner contexts.");
    final Assumption noThreadTimingNeeded = Truffle.getRuntime().createAssumption("No enter timing needed.");
    final Assumption noPriorityChangeNeeded = Truffle.getRuntime().createAssumption("No priority change needed.");
    final Assumption customHostClassLoader = Truffle.getRuntime().createAssumption("No custom host class loader needed.");

    volatile OptionDescriptors allOptions;
    volatile boolean closed;

    private volatile CancelHandler cancelHandler;
    // Data used by the runtime to enable "global" state per Engine
    volatile Object runtimeData;
    Map<String, Level> logLevels;    // effectively final
    private HostClassCache hostClassCache; // effectively final
    private volatile Object engineLoggers;
    private volatile Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectorsSupplier;

    final int contextLength;
    private volatile EngineLimits limits;
    final boolean conservativeContextReferences;
    private final MessageTransport messageInterceptor;
    private volatile int asynchronousStackDepth = 0;
    @CompilationFinal private HostToGuestCodeCache hostToGuestCodeCache;

    PolyglotEngineImpl(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean useSystemProperties, ClassLoader contextClassLoader, boolean boundEngine,
                    MessageTransport messageInterceptor, Handler logHandler) {
        this(impl, out, err, in, options, allowExperimentalOptions, useSystemProperties, contextClassLoader, boundEngine, false, messageInterceptor, logHandler);
    }

    private PolyglotEngineImpl(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, Map<String, String> options,
                    boolean allowExperimentalOptions, boolean useSystemProperties, ClassLoader contextClassLoader, boolean boundEngine, boolean preInitialization,
                    MessageTransport messageInterceptor, Handler logHandler) {
        super(impl);
        this.messageInterceptor = messageInterceptor;
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(this, out, err, in, messageInterceptor);
        this.impl = impl;
        this.out = out;
        this.err = err;
        this.in = in;
        this.contextClassLoader = contextClassLoader;
        this.boundEngine = boundEngine;
        this.logHandler = logHandler;

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

        this.engineOptions = createEngineOptionDescriptors();
        this.engineOptionValues = new OptionValuesImpl(this, engineOptions, true);

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

        Map<String, String> originalEngineOptions = new HashMap<>();
        logLevels = new HashMap<>();
        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();

        parseOptions(options, useSystemProperties, originalEngineOptions, languagesOptions, instrumentsOptions, logLevels);

        boolean useAllowExperimentalOptions = allowExperimentalOptions || Boolean.parseBoolean(EngineAccessor.RUNTIME.getSavedProperty(PROP_ALLOW_EXPERIMENTAL_OPTIONS));
        this.engineOptionValues.putAll(originalEngineOptions, useAllowExperimentalOptions);
        this.conservativeContextReferences = engineOptionValues.get(PolyglotEngineOptions.UseConservativeContextReferences);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language), useAllowExperimentalOptions);
        }

        ENGINES.put(this, null);
        if (!preInitialization) {
            createInstruments(instrumentsOptions, useAllowExperimentalOptions);
            registerShutDownHook();
        }
    }

    private PolyglotEngineImpl(PolyglotEngineImpl prototype) {
        super(prototype.impl);
        this.messageInterceptor = prototype.messageInterceptor;
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(
                        this,
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.out)),
                        INSTRUMENT.createDispatchOutput(INSTRUMENT.getOut(prototype.err)),
                        prototype.in,
                        prototype.messageInterceptor);
        this.impl = prototype.impl;
        this.out = prototype.out;
        this.err = prototype.err;
        this.in = prototype.in;
        this.contextClassLoader = prototype.contextClassLoader;
        this.boundEngine = prototype.boundEngine;
        this.logHandler = prototype.logHandler;

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

        this.engineOptions = createEngineOptionDescriptors();
        this.engineOptionValues = new OptionValuesImpl(this, engineOptions, true);

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

        prototype.engineOptionValues.copyInto(this.engineOptionValues);
        this.conservativeContextReferences = engineOptionValues.get(PolyglotEngineOptions.UseConservativeContextReferences);

        for (String languageId : idToLanguage.keySet()) {
            OptionValuesImpl prototypeOptions = prototype.idToLanguage.get(languageId).getOptionValuesIfExists();
            if (prototypeOptions != null) {
                prototypeOptions.copyInto(idToLanguage.get(languageId).getOptionValues());
            }
        }

        ENGINES.put(this, null);
        Collection<PolyglotInstrument> instrumentsToCreate = new ArrayList<>();
        for (String instrumentId : idToInstrument.keySet()) {
            OptionValuesImpl prototypeOptions = prototype.idToInstrument.get(instrumentId).getOptionValuesIfExists();
            if (prototypeOptions != null) {
                PolyglotInstrument instrument = idToInstrument.get(instrumentId);
                prototypeOptions.copyInto(instrument.getOptionValues());
                instrumentsToCreate.add(instrument);
            }
        }
        ensureInstrumentsCreated(instrumentsToCreate);
        registerShutDownHook();
    }

    HostToGuestCodeCache getHostToGuestCodeCache() {
        HostToGuestCodeCache cache = this.hostToGuestCodeCache;
        if (cache == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            hostToGuestCodeCache = cache = new HostToGuestCodeCache();
        }
        return cache;
    }

    private static OptionDescriptors createEngineOptionDescriptors() {
        OptionDescriptors engineOptionDescriptors = new PolyglotEngineOptionsOptionDescriptors();
        OptionDescriptors compilerOptionDescriptors = EngineAccessor.RUNTIME.getCompilerOptionDescriptors();
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

    boolean patch(DispatchOutputStream newOut, DispatchOutputStream newErr, InputStream newIn, Map<String, String> newOptions,
                    boolean newUseSystemProperties, boolean newAllowExperimentalOptions,
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
        INSTRUMENT.patchInstrumentationHandler(instrumentationHandler, newOut, newErr, newIn);

        Map<String, String> originalEngineOptions = new HashMap<>();
        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();

        assert this.logLevels.isEmpty();
        parseOptions(newOptions, newUseSystemProperties, originalEngineOptions, languagesOptions, instrumentsOptions, logLevels);
        boolean useAllowExperimentalOptions = newAllowExperimentalOptions || Boolean.parseBoolean(EngineAccessor.RUNTIME.getSavedProperty(PROP_ALLOW_EXPERIMENTAL_OPTIONS));
        this.engineOptionValues.putAll(originalEngineOptions, useAllowExperimentalOptions);

        if (this.runtimeData != null) {
            EngineAccessor.RUNTIME.reloadEngineOptions(this.runtimeData, this.engineOptionValues);
        }

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language), useAllowExperimentalOptions);
        }

        // Set instruments options but do not call onCreate. OnCreate is called only in case of
        // successful context patch.
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getOptionValues().putAll(instrumentsOptions.get(instrument), useAllowExperimentalOptions);
        }
        registerShutDownHook();
        return true;
    }

    private static void createInstruments(Map<PolyglotInstrument, Map<String, String>> instrumentsOptions, boolean allowExperimentalOptions) {
        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getOptionValues().putAll(instrumentsOptions.get(instrument), allowExperimentalOptions);
        }
        ensureInstrumentsCreated(instrumentsOptions.keySet());
    }

    private static void ensureInstrumentsCreated(Collection<? extends PolyglotInstrument> instruments) {
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

    synchronized void initializeMultiContext(PolyglotContextImpl existingContext) {
        if (singleContext.isValid()) {
            singleContext.invalidate("More than one context introduced.");
            PolyglotContextImpl.invalidateStaticContextAssumption();
            if (existingContext != null) {
                for (PolyglotLanguageContext context : existingContext.contexts) {
                    if (context.isInitialized()) {
                        context.getLanguageInstance().initializeMultiContext();
                    }
                }
            }
        }
    }

    private void parseOptions(Map<String, String> options, boolean useSystemProperties,
                    Map<String, String> originalEngineOptions,
                    Map<PolyglotLanguage, Map<String, String>> languagesOptions, Map<PolyglotInstrument, Map<String, String>> instrumentsOptions,
                    Map<String, Level> logOptions) {
        final Map<String, String> optionsWithSystemProperties;
        if (useSystemProperties) {
            optionsWithSystemProperties = readOptionsFromSystemProperties();
            // Context options override system properties options
            optionsWithSystemProperties.putAll(options);
        } else {
            optionsWithSystemProperties = options;
        }

        for (String key : optionsWithSystemProperties.keySet()) {
            String group = parseOptionGroup(key);
            String value = optionsWithSystemProperties.get(key);
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

            if (group.equals(OPTION_GROUP_ENGINE)) {
                originalEngineOptions.put(key, value);
                continue;
            }

            if (group.equals(OPTION_GROUP_IMAGE_BUILD_TIME)) {
                throw PolyglotEngineException.illegalArgument("Image build-time option '" + key + "' cannot be set at runtime");
            }

            if (group.equals(OPTION_GROUP_LOG)) {
                logOptions.put(parseLoggerName(key), Level.parse(value));
                continue;
            }
            throw OptionValuesImpl.failNotFound(getAllOptions(), key);
        }
    }

    static OptionValuesImpl getEngineOptionsWithNoEngine() {
        OptionValuesImpl optionValues = new OptionValuesImpl(null, createEngineOptionDescriptors(), true);
        Map<String, String> options = readOptionsFromSystemProperties();

        for (String key : options.keySet()) {
            String group = parseOptionGroup(key);
            String value = options.get(key);

            if (group.equals(OPTION_GROUP_ENGINE)) {
                optionValues.put(key, value, true);
            }
        }

        return optionValues;
    }

    private static Map<String, String> readOptionsFromSystemProperties() {
        Map<String, String> options = new HashMap<>();
        Properties properties = System.getProperties();
        synchronized (properties) {
            for (Object systemKey : properties.keySet()) {
                if (PROP_ALLOW_EXPERIMENTAL_OPTIONS.equals(systemKey)) {
                    continue;
                }
                String key = (String) systemKey;
                if (key.startsWith(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX)) {
                    final String optionKey = key.substring(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX.length());
                    // Image build time options are not set in runtime options
                    if (!optionKey.startsWith(OPTION_GROUP_IMAGE_BUILD_TIME)) {
                        options.put(optionKey, System.getProperty(key));
                    }
                }
            }
        }
        return options;
    }

    /**
     * Find if there is an "engine option" (covers engine and instruments options) present among the
     * given options.
     */
    boolean isEngineGroup(String group) {
        return idToPublicInstrument.containsKey(group) || group.equals(OPTION_GROUP_ENGINE);
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
        assert Thread.holdsLock(this);
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
    }

    synchronized void removeContext(PolyglotContextImpl context) {
        // should never be remove twice
        assert !context.weakReference.removed;
        context.weakReference.removed = true;
        context.weakReference.freeInstances.clear();
        contexts.remove(context.weakReference);
        workContextReferenceQueue();
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
        synchronized (this) {
            if (contexts.isEmpty()) {
                return;
            }
            allContexts = collectAliveContexts();
        }
        for (PolyglotContextImpl context : allContexts) {
            listener.onContextCreated(context.truffleContext);
            for (PolyglotLanguageContext lc : context.contexts) {
                LanguageInfo language = lc.language.info;
                if (lc.eventsEnabled && lc.env != null) {
                    listener.onLanguageContextCreated(context.truffleContext, language);
                    if (lc.isInitialized()) {
                        listener.onLanguageContextInitialized(context.truffleContext, language);
                        if (lc.finalized) {
                            listener.onLanguageContextFinalized(context.truffleContext, language);
                        }
                    }
                }
            }
        }
    }

    void reportAllContextThreads(ThreadsListener listener) {
        List<PolyglotContextImpl> allContexts;
        synchronized (this) {
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
                listener.onThreadInitialized(context.truffleContext, thread);
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
            ensureClosed(cancelIfExecuting, true);
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

    void ensureClosed(boolean cancelIfExecuting, boolean closeContexts) {
        ensureClosed(cancelIfExecuting, closeContexts, true);
    }

    private synchronized void ensureClosed(boolean cancelIfExecuting, boolean closeContexts, boolean closeLogHandler) {
        if (!closed) {
            workContextReferenceQueue();
            List<PolyglotContextImpl> localContexts = collectAliveContexts();
            /*
             * Check ahead of time for open contexts to fail early and avoid closing only some
             * contexts.
             */
            if (closeContexts) {
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

            if (!boundEngine && closeContexts) {
                for (PolyglotContextImpl context : localContexts) {
                    PolyglotContextImpl.disposeStaticContext(context);
                }
            }

            // don't commit changes to contexts if still running
            if (closeContexts) {
                contexts.clear();
            }

            // instruments should be shut-down even if they are currently still executed
            // we want to see instrument output if the process is quit while executing.
            for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                try {
                    instrumentImpl.notifyClosing();
                } catch (Throwable e) {
                    if (closeContexts) {
                        throw e;
                    }
                }
            }
            for (PolyglotInstrument instrumentImpl : idToInstrument.values()) {
                try {
                    instrumentImpl.ensureClosed();
                } catch (Throwable e) {
                    if (closeContexts) {
                        throw e;
                    }
                }
            }
            // don't commit to the close if still running as this might cause races in the executing
            // context.
            if (this.runtimeData != null) {
                EngineAccessor.RUNTIME.onEngineClosed(this.runtimeData);
            }
            if (closeContexts) {
                Object loggers = getEngineLoggers();
                if (loggers != null) {
                    LANGUAGE.closeEngineLoggers(loggers);
                }
                if (logHandler != null && closeLogHandler) {
                    logHandler.close();
                }
                ENGINES.remove(this);
                closed = true;
                for (PolyglotLanguage language : idToLanguage.values()) {
                    language.close();
                }
            } else if (logHandler != null) {
                // called from shutdown hook, at least flush the logging handler
                logHandler.flush();
            }
        }
    }

    List<PolyglotContextImpl> collectAliveContexts() {
        Thread.holdsLock(this);
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
            return engineOptions;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
    }

    OptionDescriptors getAllOptions() {
        checkState();
        if (allOptions == null) {
            synchronized (this) {
                if (allOptions == null) {
                    List<OptionDescriptors> allDescriptors = new ArrayList<>();
                    allDescriptors.add(engineOptions);
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        allDescriptors.add(language.getOptionsInternal());
                    }
                    for (PolyglotInstrument instrument : idToInstrument.values()) {
                        allDescriptors.add(instrument.getOptionsInternal());
                    }
                    allOptions = OptionDescriptors.createUnion(allDescriptors.toArray(new OptionDescriptors[0]));
                }
            }
        }
        return allOptions;
    }

    static PolyglotEngineImpl preInitialize(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, ClassLoader contextClassLoader, Handler logHandler) {
        final PolyglotEngineImpl engine = new PolyglotEngineImpl(impl, out, err, in, new HashMap<>(), true, true, contextClassLoader, true, true, null, logHandler);
        synchronized (engine) {
            try {
                engine.preInitializedContext.set(PolyglotContextImpl.preInitialize(engine));
            } finally {
                // Reset language homes from native-image compilatio time, will be recomputed in
                // image execution time
                LanguageCache.resetNativeImageCacheLanguageHomes();
                // Clear logger settings
                engine.logLevels.clear();
                engine.logHandler = null;
            }
        }
        return engine;
    }

    /**
     * Clears the pre-initialized engines. The TruffleFeature needs to clean emitted engines during
     * Feature.cleanup.
     */
    static void resetPreInitializedEngine() {
        ENGINES.clear();
    }

    void initializeHostAccess(HostAccess policy) {
        assert Thread.holdsLock(this);
        assert policy != null;
        HostClassCache cache = HostClassCache.findOrInitialize(getAPIAccess(), policy, contextClassLoader);
        if (this.hostClassCache != null && this.hostClassCache != cache) {
            throw PolyglotEngineException.illegalState("Found different host access configuration for a context with a shared engine. " +
                            "The host access configuration must be the same for all contexts of an engine. " +
                            "Provide the same host access configuration using the Context.Builder.allowHostAccess method when constructing the context.");
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
        synchronized (this) {
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
                    synchronized (engine) {
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
                    engine.ensureClosed(false, false);
                }
            }
        }
    }

    CancelHandler getCancelHandler() {
        if (cancelHandler == null) {
            synchronized (this) {
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
            boolean cancelling = false;
            for (PolyglotContextImpl context : localContexts) {
                if (context.cancelling) {
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
                    for (PolyglotContextImpl context : localContexts) {
                        context.waitForClose();
                    }
                } finally {
                    disableCancel();
                }
            }
        }

        synchronized void enableCancel() {
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
                            throw new CancelExecution(eventContext, context.invalidMessage);
                        }
                    }
                });
            }
            cancellationUsers++;
        }

        private synchronized void disableCancel() {
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

    @SuppressWarnings("serial")
    static final class CancelExecution extends ThreadDeath implements TruffleException {

        private final Node node;
        private final String cancelMessage;

        CancelExecution(EventContext context, String cancelMessage) {
            this.node = context != null ? context.getInstrumentedNode() : null;
            this.cancelMessage = cancelMessage;
        }

        public Node getLocation() {
            return node;
        }

        @Override
        public String getMessage() {
            if (cancelMessage == null) {
                return "Execution got cancelled.";
            } else {
                return cancelMessage;
            }
        }

        public boolean isCancelled() {
            return true;
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
        try {
            PolyglotContextImpl context;
            synchronized (this) {
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
                            : PolyglotLoggers.createStreamHandler(
                                            configErr == null ? INSTRUMENT.getOut(this.err) : configErr,
                                            false, true);

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
            boolean replayEvents = false;
            if (context == null) {
                synchronized (this) {
                    checkState();
                    context = new PolyglotContextImpl(this, config);
                    addContext(context);
                }
            } else if (context.engine == this) {
                replayEvents = true;
            }

            if (replayEvents && EngineAccessor.INSTRUMENT.hasContextBindings(this)) {
                // replace events for preinitialized contexts
                // events must be replayed without engine lock.
                final Object prev = enter(context);
                try {
                    context.replayInstrumentationEvents();
                } finally {
                    leave(prev, context);
                }
            }
            return context.creatorApi;
        } catch (Throwable t) {
            throw PolyglotImpl.guestToHostException(this, t);
        }
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
                    synchronized (this) {
                        addContext(context);
                    }
                } else {
                    context.closeImpl(false, false, false);
                    PolyglotContextImpl.disposeStaticContext(null);
                    config.fileSystem = oldFileSystem;
                    config.internalFileSystem = oldInternalFileSystem;
                    PolyglotEngineImpl engine = new PolyglotEngineImpl(this);
                    ensureClosed(true, true, false);
                    synchronized (engine) {
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

    Object getOrCreateEngineLoggers() {
        Object res = engineLoggers;
        if (res == null) {
            synchronized (this) {
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
            synchronized (this) {
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
            leave(prev, context);
        }
    }

    Object enter(PolyglotContextImpl context) {
        Object prev;
        PolyglotThreadInfo info = getCachedThreadInfo(context);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
            // fast-path -> same thread
            prev = PolyglotContextImpl.getSingleContextState().getContextThreadLocal().setReturnParent(context);
            info.enter(this);
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

    void leave(Object prev, PolyglotContextImpl polyglotContext) {
        assert polyglotContext.closed || polyglotContext.closingThread == Thread.currentThread() ||
                        PolyglotContextImpl.currentNotEntered() == polyglotContext : "Cannot leave context that is currently not entered. Forgot to enter or leave a context?";
        PolyglotThreadInfo info = getCachedThreadInfo(polyglotContext);
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, info.getThread() == Thread.currentThread())) {
            info.leave(this);
        } else {
            if (singleThreadPerContext.isValid() && singleContext.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            polyglotContext.leaveThreadChanged();
        }
        PolyglotContextImpl.getSingleContextState().getContextThreadLocal().set(prev);
    }

    PolyglotThreadInfo getCachedThreadInfo(PolyglotContextImpl context) {
        if (singleThreadPerContext.isValid() && singleContext.isValid()) {
            return context.getCachedThreadInfo(true);
        } else {
            return context.getCachedThreadInfo(false);
        }
    }
}
