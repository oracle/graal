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
import static com.oracle.truffle.api.vm.VMAccessor.SPI;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

class PolyglotEngineImpl extends org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineImpl implements VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "host";
    private static final Set<String> RESERVED_IDS = new HashSet<>(
                    Arrays.asList(HOST_LANGUAGE_ID, "graal", "truffle", "engine", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm"));

    private static final Map<PolyglotEngineImpl, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean shutdownHookInitialized = false;
    private static final boolean DEBUG_MISSING_CLOSE = Boolean.getBoolean("polyglotimpl.DebugMissingClose");

    Engine api; // effectively final
    final Object instrumentationHandler;
    final PolyglotImpl impl;
    final DispatchOutputStream out;
    final DispatchOutputStream err;
    final InputStream in;
    final long timeout;
    final TimeUnit timeoutUnit;
    final boolean sandbox;

    final Map<String, PolyglotLanguage> idToLanguage;
    final Map<String, Language> idToPublicLanguage;
    final Map<String, LanguageInfo> idToInternalLanguageInfo;

    final Map<String, PolyglotInstrument> idToInstrument;
    final Map<String, Instrument> idToPublicInstrument;
    final Map<String, InstrumentInfo> idToInternalInstrumentInfo;

    final OptionDescriptors engineOptions;
    final OptionDescriptors compilerOptions;
    final OptionDescriptors allEngineOptions;

    final OptionValuesImpl engineOptionValues;
    final OptionValuesImpl compilerOptionValues;
    final ClassLoader contextClassLoader;
    final boolean boundEngine;
    final Exception createdLocation = DEBUG_MISSING_CLOSE ? new Exception() : null;
    private final Set<PolyglotContextImpl> contexts = new LinkedHashSet<>();

    PolyglotLanguage hostLanguage;

    volatile OptionDescriptors allOptions;
    volatile boolean closed;

    private volatile CancelHandler cancelHandler;

    PolyglotEngineImpl(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, Map<String, String> options, long timeout, TimeUnit timeoutUnit,
                    boolean sandbox, boolean useSystemProperties, ClassLoader contextClassLoader, boolean boundEngine) {
        super(impl);
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(this, out, err, in);
        this.impl = impl;
        this.out = out;
        this.err = err;
        this.in = in;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
        this.contextClassLoader = contextClassLoader;
        this.sandbox = sandbox;
        this.boundEngine = boundEngine;

        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToInternalLanguageInfo = Collections.unmodifiableMap(languageInfos);

        Map<String, InstrumentInfo> instrumentInfos = new LinkedHashMap<>();
        this.idToInstrument = Collections.unmodifiableMap(initializeInstruments(instrumentInfos));
        this.idToInternalInstrumentInfo = Collections.unmodifiableMap(instrumentInfos);

        for (String id : idToLanguage.keySet()) {
            if (idToInstrument.containsKey(id)) {
                throw failDuplicateId(id,
                                idToLanguage.get(id).cache.getClassName(),
                                idToInstrument.get(id).cache.getClassName());
            }
        }

        this.engineOptions = OptionDescriptors.create(describeEngineOptions());
        this.compilerOptions = VMAccessor.SPI.getCompilerOptions();
        this.allEngineOptions = OptionDescriptors.createUnion(engineOptions, compilerOptions);
        this.engineOptionValues = new OptionValuesImpl(this, this.engineOptions);
        this.compilerOptionValues = new OptionValuesImpl(this, this.compilerOptions);

        Map<String, String> originalEngineOptions = new HashMap<>();
        Map<String, String> originalCompilerOptions = new HashMap<>();
        Map<PolyglotLanguage, Map<String, String>> languagesOptions = new HashMap<>();
        Map<PolyglotInstrument, Map<String, String>> instrumentsOptions = new HashMap<>();

        parseOptions(options, useSystemProperties, originalEngineOptions, originalCompilerOptions, languagesOptions, instrumentsOptions);

        this.engineOptionValues.putAll(originalEngineOptions);
        this.compilerOptionValues.putAll(originalCompilerOptions);

        for (PolyglotLanguage language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language));
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

        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            instrument.getOptionValues().putAll(instrumentsOptions.get(instrument));
        }

        for (PolyglotInstrument instrument : instrumentsOptions.keySet()) {
            // we got options for this instrument -> create it.
            instrument.ensureCreated();
        }

        ENGINES.put(this, null);
        if (!shutdownHookInitialized) {
            synchronized (ENGINES) {
                if (!shutdownHookInitialized) {
                    shutdownHookInitialized = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(new PolyglotShutDownHook()));
                }
            }
        }
    }

    List<OptionDescriptor> describeEngineOptions() {
        List<OptionDescriptor> descriptors = new ArrayList<>();

        return descriptors;
    }

    private void parseOptions(Map<String, String> options, boolean useSystemProperties,
                    Map<String, String> originalEngineOptions, Map<String, String> originalCompilerOptions,
                    Map<PolyglotLanguage, Map<String, String>> languagesOptions, Map<PolyglotInstrument, Map<String, String>> instrumentsOptions) {
        if (useSystemProperties) {
            Properties properties = System.getProperties();
            synchronized (properties) {
                for (Object systemKey : properties.keySet()) {
                    String key = (String) systemKey;
                    if (key.startsWith(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX)) {
                        String engineKey = key.substring(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX.length(), key.length());
                        if (!options.containsKey(engineKey)) {
                            options.put(engineKey, System.getProperty(key));
                        }
                    }
                }
            }
        }

        for (String key : options.keySet()) {
            int groupIndex = key.indexOf('.');
            String group;
            if (groupIndex != -1) {
                group = key.substring(0, groupIndex);
            } else {
                group = key;
            }
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

            if (group.equals(PolyglotImpl.OPTION_GROUP_ENGINE)) {
                originalEngineOptions.put(key, value);
                continue;
            }

            if (group.equals(PolyglotImpl.OPTION_GROUP_COMPILER)) {
                originalCompilerOptions.put(key, value);
                continue;
            }
            throw OptionValuesImpl.failNotFound(getAllOptions(), key);
        }
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return this;
    }

    private Map<String, PolyglotInstrument> initializeInstruments(Map<String, InstrumentInfo> infos) {
        Map<String, PolyglotInstrument> instruments = new LinkedHashMap<>();
        List<InstrumentCache> cachedInstruments = InstrumentCache.load(SPI.allLoaders());
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
        Map<String, PolyglotLanguage> langs = new LinkedHashMap<>();
        Map<String, LanguageCache> cachedLanguages = LanguageCache.languages();
        Set<LanguageCache> uniqueLanguages = new LinkedHashSet<>();
        uniqueLanguages.addAll(cachedLanguages.values());
        this.hostLanguage = createLanguage(createHostLanguageCache(), HOST_LANGUAGE_INDEX);

        int index = 1;
        for (LanguageCache cache : uniqueLanguages) {
            PolyglotLanguage languageImpl = createLanguage(cache, index);

            String id = languageImpl.cache.getId();
            verifyId(id, cache.getClassName());
            if (langs.containsKey(id)) {
                throw failDuplicateId(id, languageImpl.cache.getClassName(), langs.get(id).cache.getClassName());
            }
            langs.put(id, languageImpl);
            infos.put(id, languageImpl.info);
            index++;
        }

        this.hostLanguage.ensureInitialized();

        return langs;
    }

    private PolyglotLanguage createLanguage(LanguageCache cache, int index) {
        PolyglotLanguage languageImpl = new PolyglotLanguage(this, cache, index, index == HOST_LANGUAGE_INDEX);
        languageImpl.info = NODES.createLanguage(languageImpl, cache.getId(), cache.getName(), cache.getVersion(), cache.getMimeTypes());
        Language language = impl.getAPIAccess().newLanguage(languageImpl);
        languageImpl.api = language;
        return languageImpl;
    }

    private static LanguageCache createHostLanguageCache() {
        return new LanguageCache(HOST_LANGUAGE_ID, Collections.emptySet(),
                        "Host", "Host", System.getProperty("java.version"), false, false, new HostLanguage());
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

    final void checkState() {
        if (closed) {
            throw new IllegalStateException("Engine is already closed.");
        }
    }

    OptionDescriptors getEngineOptions() {
        throw new UnsupportedOperationException();
    }

    void addContext(PolyglotContextImpl context) {
        assert Thread.holdsLock(this);
        contexts.add(context);
    }

    synchronized void removeContext(PolyglotContextImpl context) {
        contexts.remove(context);
    }

    @Override
    public Language requirePublicLanguage(String id) {
        checkState();
        Language language = idToPublicLanguage.get(id);
        if (language == null) {
            String misspelledGuess = matchSpellingError(idToPublicLanguage.keySet(), id);
            String didYouMean = "";
            if (misspelledGuess != null) {
                didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
            }
            throw new PolyglotIllegalStateException(String.format("A language with id '%s' is not installed. %sInstalled languages are: %s.", id, didYouMean, getLanguages().keySet()));
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

    @Override
    public Instrument requirePublicInstrument(String id) {
        checkState();
        Instrument instrument = idToPublicInstrument.get(id);
        if (instrument == null) {
            String misspelledGuess = matchSpellingError(idToPublicInstrument.keySet(), id);
            String didYouMean = "";
            if (misspelledGuess != null) {
                didYouMean = String.format("Did you mean '%s'? ", misspelledGuess);
            }
            throw new PolyglotIllegalStateException(String.format("An instrument with id '%s' is not installed. %sInstalled instruments are: %s.", id, didYouMean, getInstruments().keySet()));
        }
        return instrument;
    }

    @Override
    public synchronized void ensureClosed(boolean cancelIfExecuting, boolean ignoreCloseFailure) {
        if (!closed) {
            PolyglotContextImpl[] localContexts = contexts.toArray(new PolyglotContextImpl[0]);
            /*
             * Check ahead of time for open contexts to fail early and avoid closing only some
             * contexts.
             */
            if (!cancelIfExecuting && !ignoreCloseFailure) {
                for (PolyglotContextImpl context : localContexts) {
                    synchronized (context) {
                        if (context.hasActiveOtherThread(false)) {
                            throw new IllegalStateException(String.format("One of the context instances is currently executing. " +
                                            "Set cancelIfExecuting to true to stop the execution on this thread."));
                        }
                    }
                }
            }
            for (PolyglotContextImpl context : localContexts) {
                try {
                    boolean closeCompleted = context.closeImpl(cancelIfExecuting, cancelIfExecuting);
                    if (!closeCompleted && !cancelIfExecuting && !ignoreCloseFailure) {
                        throw new IllegalStateException(String.format("One of the context instances is currently executing. " +
                                        "Set cancelIfExecuting to true to stop the execution on this thread."));
                    }
                } catch (Throwable e) {
                    if (!ignoreCloseFailure) {
                        throw e;
                    }
                }
            }
            if (cancelIfExecuting) {
                getCancelHandler().waitForClosing(localContexts);
            }

            contexts.clear();
            for (Instrument instrument : idToPublicInstrument.values()) {
                PolyglotInstrument instrumentImpl = (PolyglotInstrument) getAPIAccess().getImpl(instrument);
                try {
                    instrumentImpl.ensureClosed();
                } catch (Throwable e) {
                    if (!ignoreCloseFailure) {
                        throw e;
                    }
                }
            }
            ENGINES.remove(this);
            closed = true;
        }
    }

    @Override
    public Map<String, Instrument> getInstruments() {
        checkState();
        return idToPublicInstrument;
    }

    @Override
    public Map<String, Language> getLanguages() {
        checkState();
        return idToPublicLanguage;
    }

    @Override
    public OptionDescriptors getOptions() {
        checkState();
        return allEngineOptions;
    }

    @Override
    public String getVersion() {
        String version = System.getProperty("org.graalvm.version");
        if (version == null) {
            version = System.getProperty("graalvm.version");
        }
        if (version == null) {
            return "Development Build";
        } else {
            return version;
        }
    }

    OptionDescriptors getAllOptions() {
        checkState();
        if (allOptions == null) {
            synchronized (this) {
                if (allOptions == null) {
                    List<OptionDescriptors> allDescriptors = new ArrayList<>();
                    allDescriptors.add(engineOptions);
                    allDescriptors.add(compilerOptions);
                    for (PolyglotLanguage language : idToLanguage.values()) {
                        allDescriptors.add(language.getOptions());
                    }
                    for (PolyglotInstrument instrument : idToInstrument.values()) {
                        allDescriptors.add(instrument.getOptions());
                    }
                    allOptions = OptionDescriptors.createUnion(allDescriptors.toArray(new OptionDescriptors[0]));
                }
            }
        }
        return allOptions;
    }

    private static final class PolyglotShutDownHook implements Runnable {

        public void run() {
            PolyglotEngineImpl[] engines = ENGINES.keySet().toArray(new PolyglotEngineImpl[0]);
            for (PolyglotEngineImpl engine : engines) {
                if (DEBUG_MISSING_CLOSE) {
                    PrintStream out = System.out;
                    out.println("Missing close on vm shutdown: ");
                    out.print(" InitializedLanguages:");
                    for (PolyglotContextImpl context : engine.contexts) {
                        for (PolyglotLanguageContext langContext : context.contexts) {
                            if (langContext.env != null) {
                                out.print(langContext.language.getId());
                                out.print(", ");
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
            ENGINES.keySet().removeAll(Arrays.asList(engines));
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

        void waitForClosing(PolyglotContextImpl... localContexts) {
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

        private synchronized void enableCancel() {
            if (cancellationBinding == null) {
                cancellationBinding = instrumenter.attachListener(SourceSectionFilter.ANY, new ExecutionEventListener() {
                    public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                        cancelExecution(context);
                    }

                    public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                        cancelExecution(context);
                    }

                    public void onEnter(EventContext context, VirtualFrame frame) {
                        cancelExecution(context);
                    }

                    @TruffleBoundary
                    private void cancelExecution(EventContext eventContext) {
                        PolyglotContextImpl context = PolyglotContextImpl.requireContext();
                        if (context.cancelling) {
                            throw new CancelExecution(eventContext);
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
    private static final class CancelExecution extends ThreadDeath implements TruffleException {

        private final Node node;

        CancelExecution(EventContext context) {
            this.node = context.getInstrumentedNode();
        }

        public Node getLocation() {
            return node;
        }

        @Override
        public String getMessage() {
            return "Execution got cancelled.";
        }

        public boolean isCancelled() {
            return true;
        }

    }

    @Override
    @SuppressWarnings({"hiding"})
    public synchronized Context createContext(OutputStream out, OutputStream err, InputStream in, boolean allowHostAccess,
                    boolean allowCreateThread, Predicate<String> classFilter,
                    Map<String, String> options, Map<String, String[]> arguments, String[] onlyLanguages) {
        checkState();
        if (boundEngine && !contexts.isEmpty()) {
            throw new IllegalArgumentException("Automatically created engines cannot be used to create more than one context. " +
                            "Use Engine.newBuilder().build() to construct a new engine and pass it using Context.newBuilder().engine(engine).build().");
        }

        Set<String> allowedLanguages;
        if (onlyLanguages.length == 0) {
            allowedLanguages = getLanguages().keySet();
        } else {
            allowedLanguages = new HashSet<>(Arrays.asList(onlyLanguages));
        }

        PolyglotContextImpl contextImpl = new PolyglotContextImpl(this, out, err, in, allowHostAccess, allowCreateThread, classFilter, options, arguments, allowedLanguages);
        addContext(contextImpl);
        Context api = impl.getAPIAccess().newContext(contextImpl);
        contextImpl.api = api;
        return api;
    }

}
