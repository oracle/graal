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

import static com.oracle.truffle.api.vm.PolyglotImpl.checkEngine;
import static com.oracle.truffle.api.vm.VMAccessor.INSTRUMENT;
import static com.oracle.truffle.api.vm.VMAccessor.LANGUAGE;
import static com.oracle.truffle.api.vm.VMAccessor.NODES;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.spi.FileTypeDetector;
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
import java.util.ServiceLoader;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotContext;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DispatchOutputStream;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.impl.SourceAccessor;
import com.oracle.truffle.api.vm.PolyglotImpl.VMObject;

class PolyglotEngineImpl extends org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractEngineImpl implements VMObject {

    /**
     * Context index for the host language.
     */
    static final int HOST_LANGUAGE_INDEX = 0;
    static final String HOST_LANGUAGE_ID = "java";
    private static final Set<String> RESERVED_IDS = new HashSet<>(Arrays.asList("graal", "truffle", "engine", "language", "instrument", "graalvm", "context", "polyglot", "compiler", "vm"));

    private static final Map<PolyglotEngineImpl, Void> ENGINES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean shutdownHookInitialized = false;

    Engine api; // effectively final
    final Object instrumentationHandler;
    final PolyglotImpl impl;
    final DispatchOutputStream out;
    final DispatchOutputStream err;
    final InputStream in;
    final long timeout;
    final TimeUnit timeoutUnit;
    final boolean sandbox;

    final Map<String, PolyglotLanguageImpl> idToLanguage;
    final Map<String, Instrument> idToInstrument;
    final Map<String, InstrumentInfo> idToInstrumentInfo;
    final Map<String, LanguageInfo> idToLanguageInfo;
    final Map<String, Language> idToPublicLanguage;

    final OptionDescriptors engineOptions;
    final OptionDescriptors compilerOptions;
    final OptionDescriptors allEngineOptions;

    final OptionValuesImpl engineOptionValues;
    final OptionValuesImpl compilerOptionValues;

    volatile OptionDescriptors allOptions;

    volatile boolean closed;

    PolyglotEngineImpl(PolyglotImpl impl, DispatchOutputStream out, DispatchOutputStream err, InputStream in, Map<String, String> options, long timeout, TimeUnit timeoutUnit,
                    boolean sandbox, boolean useSystemProperties) {
        super(impl);
        this.instrumentationHandler = INSTRUMENT.createInstrumentationHandler(this, out, err, in);
        this.impl = impl;
        this.out = out;
        this.err = err;
        this.in = in;
        this.timeout = timeout;
        this.timeoutUnit = timeoutUnit;
        this.sandbox = sandbox;
        Map<String, LanguageInfo> languageInfos = new LinkedHashMap<>();
        this.idToLanguage = Collections.unmodifiableMap(initializeLanguages(languageInfos));
        this.idToLanguageInfo = Collections.unmodifiableMap(languageInfos);
        Map<String, InstrumentInfo> instrumentInfos = new LinkedHashMap<>();
        this.idToInstrument = Collections.unmodifiableMap(initializeInstruments(instrumentInfos));
        this.idToInstrumentInfo = Collections.unmodifiableMap(instrumentInfos);

        for (String id : idToLanguage.keySet()) {
            if (idToInstrument.containsKey(id)) {
                throw failDuplicateId(id,
                                idToLanguage.get(id).cache.getClassName(),
                                getData(idToInstrument.get(id)).cache.getClassName());
            }
        }

        this.engineOptions = new OptionDescriptorsImpl(describeEngineOptions());
        this.compilerOptions = VMAccessor.SPI.getCompilerOptions();
        this.allEngineOptions = OptionDescriptors.createUnion(engineOptions, compilerOptions);
        this.engineOptionValues = new OptionValuesImpl(this, this.engineOptions);
        this.compilerOptionValues = new OptionValuesImpl(this, this.compilerOptions);

        Map<String, String> originalEngineOptions = new HashMap<>();
        Map<String, String> originalCompilerOptions = new HashMap<>();
        Map<PolyglotLanguageImpl, Map<String, String>> languagesOptions = new HashMap<>();
        Map<Instrument, Map<String, String>> instrumentsOptions = new HashMap<>();

        parseOptions(options, useSystemProperties, originalEngineOptions, originalCompilerOptions, languagesOptions, instrumentsOptions);

        this.engineOptionValues.putAll(originalEngineOptions);
        this.compilerOptionValues.putAll(originalCompilerOptions);

        for (PolyglotLanguageImpl language : languagesOptions.keySet()) {
            language.getOptionValues().putAll(languagesOptions.get(language));
        }

        Map<String, Language> publicLanguages = new HashMap<>();
        for (String key : this.idToLanguage.keySet()) {
            PolyglotLanguageImpl languageImpl = idToLanguage.get(key);
            if (!languageImpl.cache.isInternal()) {
                publicLanguages.put(key, languageImpl.api);
            }
        }
        idToPublicLanguage = Collections.unmodifiableMap(publicLanguages);

        for (Instrument instrument : instrumentsOptions.keySet()) {
            PolyglotInstrumentImpl instrumentData = getData(instrument);
            instrumentData.getOptionValues().putAll(instrumentsOptions.get(instrument));
        }

        for (Instrument instrument : instrumentsOptions.keySet()) {
            // we got options for this instrument -> create it.
            getData(instrument).ensureCreated();
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

    private void parseOptions(Map<String, String> options, boolean useSystemProperties,
                    Map<String, String> originalEngineOptions, Map<String, String> originalCompilerOptions,
                    Map<PolyglotLanguageImpl, Map<String, String>> languagesOptions, Map<Instrument, Map<String, String>> instrumentsOptions) {
        if (useSystemProperties) {
            for (Object systemKey : System.getProperties().keySet()) {
                String key = (String) systemKey;
                if (key.startsWith(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX)) {
                    String engineKey = key.substring(OptionValuesImpl.SYSTEM_PROPERTY_PREFIX.length(), key.length());
                    if (!options.containsKey(engineKey)) {
                        options.put(key, System.getProperty(key));
                    }
                }
            }
        }

        for (String key : options.keySet()) {
            int groupIndex = key.indexOf('.');
            if (groupIndex != -1) {
                String group = key.substring(0, groupIndex);
                String value = options.get(key);
                PolyglotLanguageImpl language = idToLanguage.get(group);
                if (language != null) {
                    Map<String, String> languageOptions = languagesOptions.get(language);
                    if (languageOptions == null) {
                        languageOptions = new HashMap<>();
                        languagesOptions.put(language, languageOptions);
                    }
                    languageOptions.put(key, value);
                    continue;
                }
                Instrument instrument = idToInstrument.get(group);
                if (instrument != null) {
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
            }
            throw OptionValuesImpl.failNotFound(getAllOptions(), key);
        }
    }

    List<OptionDescriptor> describeEngineOptions() {
        List<OptionDescriptor> descriptors = new ArrayList<>();

        return descriptors;
    }

    @Override
    public PolyglotEngineImpl getEngine() {
        return this;
    }

    private Map<String, Instrument> initializeInstruments(Map<String, InstrumentInfo> infos) {
        Map<String, Instrument> instruments = new LinkedHashMap<>();
        List<InstrumentCache> cachedInstruments = InstrumentCache.load();
        for (InstrumentCache instrumentCache : cachedInstruments) {
            PolyglotInstrumentImpl instrumentImpl = new PolyglotInstrumentImpl(this, instrumentCache);
            instrumentImpl.info = LANGUAGE.createInstrument(instrumentImpl, instrumentCache.getId(), instrumentCache.getName(), instrumentCache.getVersion());
            Instrument instrument = impl.getAPIAccess().newInstrument(instrumentImpl);
            instrumentImpl.api = instrument;

            String id = instrumentImpl.cache.getId();
            verifyId(id, instrumentCache.getClassName());
            if (instruments.containsKey(id)) {
                throw failDuplicateId(id, instrumentImpl.cache.getClassName(), getData(instruments.get(id)).cache.getClassName());
            }
            instruments.put(id, instrument);
            infos.put(id, instrumentImpl.info);
        }
        return instruments;
    }

    private Map<String, PolyglotLanguageImpl> initializeLanguages(Map<String, LanguageInfo> infos) {
        Map<String, PolyglotLanguageImpl> langs = new LinkedHashMap<>();
        Map<String, LanguageCache> cachedLanguages = LanguageCache.languages();
        Set<LanguageCache> uniqueLanguages = new LinkedHashSet<>();
        uniqueLanguages.add(createHostLanguageCache());
        uniqueLanguages.addAll(cachedLanguages.values());

        int index = 0;
        for (LanguageCache cache : uniqueLanguages) {
            PolyglotLanguageImpl languageImpl = createLanguage(cache, index);

            String id = languageImpl.cache.getId();
            verifyId(id, cache.getClassName());
            if (langs.containsKey(id)) {
                throw failDuplicateId(id, languageImpl.cache.getClassName(), langs.get(id).cache.getClassName());
            }
            langs.put(id, languageImpl);
            infos.put(id, languageImpl.info);
            index++;
        }

        langs.get(HOST_LANGUAGE_ID).ensureInitialized();

        return langs;
    }

    private PolyglotLanguageImpl createLanguage(LanguageCache cache, int index) {
        PolyglotLanguageImpl languageImpl = new PolyglotLanguageImpl(this, cache, index, index == HOST_LANGUAGE_INDEX);
        languageImpl.info = NODES.createLanguage(languageImpl, cache.getId(), cache.getName(), cache.getVersion(), cache.getMimeTypes());
        Language language = impl.getAPIAccess().newLanguage(languageImpl);
        languageImpl.api = language;
        return languageImpl;
    }

    private static LanguageCache createHostLanguageCache() {
        return new LanguageCache(HOST_LANGUAGE_ID, Collections.emptySet(),
                        "Java", System.getProperty("java.version"), false, false, new HostLanguage());
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

    private PolyglotInstrumentImpl getData(Instrument language) {
        return (PolyglotInstrumentImpl) impl.getAPIAccess().getImpl(language);
    }

    OptionDescriptors getEngineOptions() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Language getLanguage(String id) {
        checkEngine(this);
        return idToPublicLanguage.get(id);
    }

    @Override
    public Instrument getInstrument(String id) {
        checkEngine(this);
        return idToInstrument.get(id);
    }

    @Override
    public PolyglotContext createPolyglotContext(OutputStream providedOut, OutputStream providedErr, InputStream providedIn, Map<String, String[]> arguments, Map<String, String> options) {
        checkEngine(this);
        OutputStream useOut = providedOut == null ? out : providedOut;
        OutputStream useErr = providedErr == null ? err : providedErr;
        InputStream useIn = providedIn == null ? in : providedIn;
        PolyglotContextImpl contextImpl = new PolyglotContextImpl(this, useOut, useErr, useIn, options, arguments, null);
        return impl.getAPIAccess().newPolyglotContext(api, contextImpl);
    }

    @Override
    public void ensureClosed() {
        if (!closed) {
            synchronized (this) {
                if (!closed) {
                    closed = true;
                    for (Instrument instrument : idToInstrument.values()) {
                        PolyglotInstrumentImpl instrumentImpl = (PolyglotInstrumentImpl) getAPIAccess().getImpl(instrument);
                        instrumentImpl.ensureClosed();
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Instrument> getInstruments() {
        checkEngine(this);
        return idToInstrument;
    }

    @Override
    public Map<String, Language> getLanguages() {
        checkEngine(this);
        return idToPublicLanguage;
    }

    @Override
    public OptionDescriptors getOptions() {
        checkEngine(this);
        return allEngineOptions;
    }

    @Override
    public String getVersion() {
        String version = System.getProperty("graalvm.version");
        if (version == null) {
            return "Development Build";
        } else {
            return version;
        }
    }

    @Override
    public Language detectLanguage(Object sourceImpl) {
        checkEngine(this);
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) sourceImpl;
        String filePath = source.getPath();
        if (filePath == null) {
            return null;
        }
        Path path = Paths.get(filePath);
        String mimeType = null;
        try {
            mimeType = getMimeType(path);
        } catch (IOException e) {
        }
        Language foundLanguage = null;
        if (mimeType != null) {
            for (Language language : idToPublicLanguage.values()) {
                PolyglotLanguageImpl languageData = (PolyglotLanguageImpl) impl.getAPIAccess().getImpl(language);
                if (languageData.cache.getMimeTypes().contains(mimeType)) {
                    foundLanguage = language;
                    break;
                }
            }
        }
        return foundLanguage;
    }

    static String getMimeType(Path filePath) throws IOException {
        if (!TruffleOptions.AOT) {
            Collection<ClassLoader> loaders = SourceAccessor.allLoaders();
            for (ClassLoader l : loaders) {
                for (FileTypeDetector detector : ServiceLoader.load(FileTypeDetector.class, l)) {
                    String mimeType = detector.probeContentType(filePath);
                    if (mimeType != null) {
                        return mimeType;
                    }
                }
            }
        }

        String found = Files.probeContentType(filePath);
        return found;
    }

    OptionDescriptors getAllOptions() {
        checkEngine(this);
        if (allOptions == null) {
            synchronized (this) {
                if (allOptions == null) {
                    List<OptionDescriptors> allDescriptors = new ArrayList<>();
                    allDescriptors.add(engineOptions);
                    allDescriptors.add(compilerOptions);
                    for (PolyglotLanguageImpl language : idToLanguage.values()) {
                        allDescriptors.add(language.getOptions());
                    }
                    for (Instrument instrument : idToInstrument.values()) {
                        allDescriptors.add(getData(instrument).getOptions());
                    }
                    allOptions = OptionDescriptors.createUnion(allDescriptors.toArray(new OptionDescriptors[0]));
                }
            }
        }
        return allOptions;
    }

    private static final class PolyglotShutDownHook implements Runnable {

        public void run() {
            for (PolyglotEngineImpl engine : ENGINES.keySet()) {
                if (engine != null) {
                    engine.ensureClosed();
                }
            }
        }

    }

}
