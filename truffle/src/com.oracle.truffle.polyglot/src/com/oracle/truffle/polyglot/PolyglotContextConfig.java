/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.InputStream;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.collections.UnmodifiableEconomicSet;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;

final class PolyglotContextConfig {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    final OutputStream out;
    final OutputStream err;
    final InputStream in;
    final boolean hostLookupAllowed;
    final boolean nativeAccessAllowed;
    final boolean createThreadAllowed;
    final boolean hostClassLoadingAllowed;
    final boolean createProcessAllowed;
    final Predicate<String> classFilter;
    private final Map<String, String[]> applicationArguments;
    final Set<String> allowedPublicLanguages;
    private final Map<String, String> originalOptions;
    private final Map<String, OptionValuesImpl> optionsById;
    @CompilationFinal FileSystem fileSystem;
    @CompilationFinal FileSystem internalFileSystem;
    final Map<String, Level> logLevels;    // effectively final
    final Handler logHandler;
    final PolyglotAccess polyglotAccess;
    final ProcessHandler processHandler;
    private final EnvironmentAccess environmentAccess;
    private final Map<String, String> environment;
    private volatile Map<String, String> configuredEnvironement;
    private final ZoneId timeZone;
    final PolyglotLimits limits;
    final ClassLoader hostClassLoader;
    private final List<PolyglotInstrument> configuredInstruments;
    private final Set<PolyglotLanguage> configuredLanguages;
    final HostAccess hostAccess;
    final boolean allowValueSharing;
    final boolean useSystemExit;

    /**
     * Contains all data of a polyglot context config that can be remembered safely without causing
     * memory leaks. Any predicate from the host must not be remembered. This subset determines what
     * config is remembered for engine caching context preinitialization.
     */
    static class PreinitConfig {

        /**
         * Default configuration used for context preinitialization without code sharing.
         */
        static final PreinitConfig DEFAULT = new PreinitConfig();

        final boolean nativeAccessAllowed;
        final boolean createThreadAllowed;
        final boolean createProcessAllowed;
        final Map<String, String> originalOptions;
        final PolyglotAccess polyglotAccess;
        final ZoneId timeZone;
        final boolean allowValueSharing;
        final boolean useSystemExit;

        private PreinitConfig() {
            this.nativeAccessAllowed = false;
            this.createThreadAllowed = false;
            this.createProcessAllowed = false;
            this.originalOptions = Collections.emptyMap();
            this.polyglotAccess = PolyglotAccess.ALL; // TODO change this to NONE with GR-14657
            this.timeZone = null;
            this.allowValueSharing = true;
            this.useSystemExit = false;
        }

        /**
         * Creates the initial preinit configuration with code sharing.
         */
        PreinitConfig(PolyglotContextConfig config) {
            this.nativeAccessAllowed = config.nativeAccessAllowed;
            this.createThreadAllowed = config.createThreadAllowed;
            this.createProcessAllowed = config.createProcessAllowed;
            this.originalOptions = config.originalOptions;
            this.polyglotAccess = config.polyglotAccess;
            this.timeZone = config.timeZone;
            this.allowValueSharing = config.allowValueSharing;
            this.useSystemExit = config.useSystemExit;
        }

        /**
         * Creates the common configuration with code sharing between two contexts. For access
         * privileges we turn them off for preinitialization if it was turned off for one of the
         * contexts seen. We only preinitialize using options that were set for all contexts.
         */
        PreinitConfig(PreinitConfig prev, PolyglotContextConfig config) {
            this.nativeAccessAllowed = prev.nativeAccessAllowed == config.nativeAccessAllowed ? config.nativeAccessAllowed : DEFAULT.nativeAccessAllowed;
            this.createThreadAllowed = prev.createThreadAllowed == config.createThreadAllowed ? config.createThreadAllowed : DEFAULT.createThreadAllowed;
            this.createProcessAllowed = prev.createProcessAllowed == config.createProcessAllowed ? config.createProcessAllowed : DEFAULT.createProcessAllowed;
            this.originalOptions = Objects.equals(prev.originalOptions, config.originalOptions) ? config.originalOptions : computeCommonOptions(prev.originalOptions, config.originalOptions);
            this.polyglotAccess = Objects.equals(prev.polyglotAccess, config.polyglotAccess) ? config.polyglotAccess : DEFAULT.polyglotAccess;
            this.timeZone = Objects.equals(prev.timeZone, config.timeZone) ? config.timeZone : DEFAULT.timeZone;
            this.allowValueSharing = prev.allowValueSharing == config.allowValueSharing ? config.allowValueSharing : DEFAULT.allowValueSharing;
            this.useSystemExit = prev.useSystemExit == config.useSystemExit ? config.useSystemExit : DEFAULT.useSystemExit;
        }

        private static Map<String, String> computeCommonOptions(Map<String, String> options1, Map<String, String> options2) {
            if (options1.isEmpty() || options2.isEmpty()) {
                return DEFAULT.originalOptions;
            }
            Map<String, String> commonOptions = new HashMap<>();
            for (Map.Entry<String, String> entry1 : options1.entrySet()) {
                String key1 = entry1.getKey();
                String value2 = options2.get(key1);
                if (value2 == null) {
                    continue;
                }
                if (Objects.equals(entry1.getValue(), value2)) {
                    commonOptions.put(key1, value2);
                }
            }
            return commonOptions;
        }

    }

    PolyglotContextConfig(PolyglotEngineImpl engine, FileSystem fs, FileSystem internalFs,
                    PreinitConfig sharableConfig) {
        this(engine,
                        System.out,
                        System.err,
                        System.in,
                        false, // never any host lookup should be allowed in context preinit
                        sharableConfig.polyglotAccess, // TODO change this to NONE with GR-14657
                        sharableConfig.nativeAccessAllowed,
                        sharableConfig.createThreadAllowed,
                        false,
                        false,
                        null,
                        Collections.emptyMap(),
                        Collections.emptySet(),
                        sharableConfig.originalOptions,
                        fs,
                        internalFs,
                        engine.logHandler,
                        sharableConfig.createProcessAllowed,
                        null,
                        EnvironmentAccess.INHERIT,
                        null,
                        sharableConfig.timeZone,
                        null,
                        null,
                        null,
                        sharableConfig.allowValueSharing,
                        sharableConfig.useSystemExit);
    }

    PolyglotContextConfig(PolyglotEngineImpl engine, OutputStream out, OutputStream err, InputStream in,
                    boolean hostLookupAllowed, PolyglotAccess polyglotAccess, boolean nativeAccessAllowed, boolean createThreadAllowed,
                    boolean hostClassLoadingAllowed, boolean allowExperimentalOptions,
                    Predicate<String> classFilter, Map<String, String[]> applicationArguments,
                    Set<String> onlyLanguages, Map<String, String> options, FileSystem publicFileSystem, FileSystem internalFileSystem, Handler logHandler,
                    boolean createProcessAllowed, ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment,
                    ZoneId timeZone, PolyglotLimits limits, ClassLoader hostClassLoader, HostAccess hostAccess, boolean allowValueSharing, boolean useSystemExit) {
        assert out != null;
        assert err != null;
        assert in != null;
        assert environmentAccess != null;
        this.out = out;
        this.err = err;
        this.in = in;
        this.hostLookupAllowed = hostLookupAllowed;
        this.polyglotAccess = polyglotAccess;
        this.nativeAccessAllowed = nativeAccessAllowed;
        this.createThreadAllowed = createThreadAllowed;
        this.hostClassLoadingAllowed = hostClassLoadingAllowed;
        this.createProcessAllowed = createProcessAllowed;
        this.classFilter = classFilter;
        this.applicationArguments = applicationArguments;
        this.allowedPublicLanguages = onlyLanguages.isEmpty() ? engine.getLanguages().keySet() : onlyLanguages;
        this.fileSystem = publicFileSystem;
        this.internalFileSystem = internalFileSystem;
        this.optionsById = new HashMap<>();
        this.logHandler = logHandler;
        this.timeZone = timeZone;
        this.limits = limits;
        this.logLevels = new HashMap<>(engine.logLevels);
        this.allowValueSharing = allowValueSharing;
        this.originalOptions = options;
        List<PolyglotInstrument> instruments = null;
        final Set<PolyglotLanguage> languages = new LinkedHashSet<>();

        for (String id : onlyLanguages) {
            addConfiguredLanguage(engine, languages, engine.idToLanguage.get(id));
        }
        for (String optionKey : options.keySet()) {
            final String group = PolyglotEngineImpl.parseOptionGroup(optionKey);
            if (group.equals(PolyglotEngineImpl.OPTION_GROUP_LOG)) {
                logLevels.put(PolyglotEngineImpl.parseLoggerName(optionKey), Level.parse(options.get(optionKey)));
                continue;
            }
            VMObject object = findObjectForContextOption(engine, optionKey, group);
            String id;
            OptionValuesImpl engineOptionValues;
            if (object instanceof PolyglotLanguage) {
                PolyglotLanguage language = (PolyglotLanguage) object;
                id = language.getId();
                engineOptionValues = language.getOptionValues();
                addConfiguredLanguage(engine, languages, language);
            } else if (object instanceof PolyglotInstrument) {
                PolyglotInstrument instrument = (PolyglotInstrument) object;
                id = instrument.getId();
                engineOptionValues = instrument.getEngineOptionValues();
                if (instruments == null) {
                    instruments = new ArrayList<>();
                }
                instruments.add(instrument);
            } else {
                throw new AssertionError("invalid vm object");
            }

            OptionValuesImpl targetOptions = optionsById.get(id);
            if (targetOptions == null) {
                targetOptions = engineOptionValues.copy();
                optionsById.put(id, targetOptions);
            }
            targetOptions.put(engine, optionKey, options.get(optionKey), allowExperimentalOptions);
        }
        this.configuredInstruments = instruments == null ? Collections.emptyList() : instruments;
        this.configuredLanguages = languages == null ? Collections.emptySet() : languages;
        this.processHandler = processHandler;
        this.environmentAccess = environmentAccess;
        this.environment = environment == null ? Collections.emptyMap() : environment;
        this.hostAccess = hostAccess;
        this.hostClassLoader = hostClassLoader;
        this.useSystemExit = useSystemExit;
    }

    void addConfiguredLanguage(PolyglotEngineImpl engine, Set<PolyglotLanguage> languages, PolyglotLanguage language) {
        if (language != null && languages.add(language)) {
            collectDependentLanguages(engine, language.cache.getDependentLanguages(), languages);
        }
    }

    private void collectDependentLanguages(PolyglotEngineImpl engine, final Collection<String> languageIds, Collection<PolyglotLanguage> foundLanguages) {
        for (String id : languageIds) {
            PolyglotLanguage language = engine.idToLanguage.get(id);
            if (language != null && foundLanguages.add(language)) {
                collectDependentLanguages(engine, language.cache.getDependentLanguages(), foundLanguages);
            }
        }
    }

    ZoneId getTimeZone() {
        ZoneId zone = this.timeZone;
        if (zone == null) {
            zone = ZoneId.systemDefault();
        }
        return zone;
    }

    boolean isAccessPermitted(PolyglotLanguage from, PolyglotLanguage to) {
        if (to.isHost() || to.cache.isInternal()) {
            // everyone has access to host or internal languages
            return true;
        }
        if (from == to) {
            return true;
        }
        if (from == null) {
            // embedder access
            if (allowedPublicLanguages.contains(to.info.getId())) {
                return true;
            }
        } else {
            // language access
            if (polyglotAccess == PolyglotAccess.ALL) {
                if (allowedPublicLanguages.contains(to.info.getId())) {
                    return true;
                }
            } else {
                if (from == to) {
                    return true;
                }
                UnmodifiableEconomicSet<String> configuredAccess = from.engine.getAPIAccess().getEvalAccess(polyglotAccess, from.getId());
                if (configuredAccess != null && configuredAccess.contains(to.getId())) {
                    return true;
                }
            }
            if (from.dependsOn(to)) {
                return true;
            }
        }
        return false;
    }

    String[] getApplicationArguments(PolyglotLanguage lang) {
        String[] args = applicationArguments.get(lang.getId());
        if (args == null) {
            args = EMPTY_STRING_ARRAY;
        }
        return args;
    }

    OptionValuesImpl getLanguageOptionValues(PolyglotLanguage lang) {
        OptionValuesImpl values = optionsById.get(lang.getId());
        if (values == null) {
            values = lang.getOptionValues();
        }
        return values;
    }

    OptionValuesImpl getInstrumentOptionValues(PolyglotInstrument instrument) {
        OptionValuesImpl values = optionsById.get(instrument.getId());
        if (values == null) {
            values = instrument.getEngineOptionValues();
        }
        return values.copy();
    }

    Set<PolyglotLanguage> getConfiguredLanguages() {
        return configuredLanguages;
    }

    /**
     * Returns a list of instruments with options for this context. Does not include instruments
     * only configured for the engine.
     */
    Collection<? extends PolyglotInstrument> getConfiguredInstruments() {
        return configuredInstruments;
    }

    Map<String, String> getEnvironment() {
        Map<String, String> result = configuredEnvironement;
        if (result == null) {
            synchronized (this) {
                result = configuredEnvironement;
                if (result == null) {
                    if (environmentAccess == EnvironmentAccess.NONE) {
                        result = Collections.unmodifiableMap(environment);
                    } else if (PolyglotEngineImpl.ALLOW_ENVIRONMENT_ACCESS && environmentAccess == EnvironmentAccess.INHERIT) {
                        result = System.getenv();  // System.getenv returns unmodifiable map.
                        if (!environment.isEmpty()) {
                            result = new HashMap<>(result);
                            result.putAll(environment);
                            result = Collections.unmodifiableMap(result);
                        }
                    } else {
                        throw PolyglotEngineException.unsupported(String.format("Unsupported EnvironmentAccess: %s", environmentAccess));
                    }
                    configuredEnvironement = result;
                }
            }
        }
        return result;
    }

    private static VMObject findObjectForContextOption(PolyglotEngineImpl engine, final String optionKey, String group) {
        PolyglotLanguage language = engine.idToLanguage.get(group);
        if (language == null) {
            PolyglotInstrument instrument = engine.idToInstrument.get(group);
            if (instrument != null) {
                if (instrument.getEngineOptionsInternal().get(optionKey) != null) {
                    throw PolyglotEngineException.illegalArgument(
                                    "Option " + optionKey +
                                                    " is an engine level instrument option. Engine level instrument options can only be configured for contexts without an explicit engine set." +
                                                    " To resolve this, configure the option when creating the Engine or create a context without a shared engine.");
                }
                return instrument;
            }
            if (group.equals(PolyglotEngineImpl.OPTION_GROUP_ENGINE)) {
                // Test that "engine options" are not present among the options designated for
                // this context
                if (engine.getAllOptions().get(optionKey) != null) {
                    throw PolyglotEngineException.illegalArgument(
                                    "Option " + optionKey + " is an engine option. Engine level options can only be configured for contexts without a shared engine set." +
                                                    " To resolve this, configure the option when creating the Engine or create a context without a shared engine.");
                }
            }
            throw OptionValuesImpl.failNotFound(engine.getAllOptions(), optionKey);
        } else {
            // there should not be any overlaps -> engine creation should already fail
            assert !group.equals(PolyglotEngineImpl.OPTION_GROUP_ENGINE);
        }
        return language;
    }

}
