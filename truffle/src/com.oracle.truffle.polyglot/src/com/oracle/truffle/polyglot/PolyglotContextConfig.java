/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.LogHandler;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.ProcessHandler;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.polyglot.FileSystems.PreInitializeContextFileSystem;
import com.oracle.truffle.polyglot.PolyglotImpl.VMObject;

final class PolyglotContextConfig {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    final APIAccess api;
    final SandboxPolicy sandboxPolicy;
    final OutputStream out;
    final OutputStream err;
    final InputStream in;
    final boolean hostLookupAllowed;
    final boolean nativeAccessAllowed;
    final boolean createThreadAllowed;
    final boolean hostClassLoadingAllowed;
    final boolean innerContextOptionsAllowed;
    final boolean createProcessAllowed;
    final Predicate<String> classFilter;
    private final Map<String, String[]> applicationArguments;
    final Set<String> onlyLanguages;
    final Set<String> allowedPublicLanguages;
    final Map<String, String> originalOptions;
    private final Map<String, OptionValuesImpl> optionsById;
    @CompilationFinal FileSystemConfig fileSystemConfig;
    final Map<String, Level> logLevels;    // effectively final
    final LogHandler logHandler;
    final Object polyglotAccess;
    final ProcessHandler processHandler;
    final Object environmentAccess;
    final Map<String, String> customEnvironment;
    private volatile Map<String, String> resolvedEnvironment;
    final ZoneId timeZone;
    final PolyglotLimits limits;
    final ClassLoader hostClassLoader;
    private final List<PolyglotInstrument> configuredInstruments;
    private final Set<PolyglotLanguage> configuredLanguages;
    final Object hostAccess;
    final Boolean forceCodeSharing;
    final boolean allowValueSharing;
    final boolean useSystemExit;
    final boolean allowExperimentalOptions;
    final Map<String, Object> creatorArguments;
    final Runnable onCancelled;
    final Consumer<Integer> onExited;
    final Runnable onClosed;

    /**
     * Groups PolyglotContext's filesystem related configurations.
     */
    static class FileSystemConfig {

        final Object ioAccess;
        final FileSystem fileSystem;
        final FileSystem internalFileSystem;

        FileSystemConfig(Object ioAccess, FileSystem publicFileSystem, FileSystem internalFileSystem) {
            this.ioAccess = ioAccess;
            this.fileSystem = publicFileSystem;
            this.internalFileSystem = internalFileSystem;
        }

        static FileSystemConfig createPatched(FileSystemConfig preInitialized, FileSystemConfig patch) {
            PreInitializeContextFileSystem preInitFs = (PreInitializeContextFileSystem) preInitialized.fileSystem;
            preInitFs.onLoadPreinitializedContext(patch.fileSystem);
            PreInitializeContextFileSystem preInitInternalFs = (PreInitializeContextFileSystem) preInitialized.internalFileSystem;
            preInitInternalFs.onLoadPreinitializedContext(patch.internalFileSystem);
            return new FileSystemConfig(patch.ioAccess, preInitFs, preInitInternalFs);
        }
    }

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
        static final PreinitConfig DEFAULT_WITH_NATIVE_ACCESS = new PreinitConfig(true);

        final boolean nativeAccessAllowed;
        final boolean createThreadAllowed;
        final boolean createProcessAllowed;
        final Map<String, String> originalOptions;
        final Object polyglotAccess;
        final ZoneId timeZone;
        final boolean allowValueSharing;
        final boolean useSystemExit;

        private PreinitConfig() {
            this(false);
        }

        private PreinitConfig(boolean nativeAccessAllowed) {
            this.nativeAccessAllowed = nativeAccessAllowed;
            this.createThreadAllowed = false;
            this.createProcessAllowed = false;
            this.originalOptions = Collections.emptyMap();
            this.polyglotAccess = null; // TODO GR-14657 change this to NONE
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
            this.polyglotAccess = Objects.equals(prev.polyglotAccess, config.polyglotAccess) ? config.polyglotAccess : config.api.getPolyglotAccessAll();
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

    PolyglotContextConfig(PolyglotEngineImpl engine, FileSystemConfig fileSystemConfig, PreinitConfig sharableConfig) {
        this(engine, SandboxPolicy.TRUSTED, null,
                        System.out,
                        System.err,
                        System.in,
                        false,
                        // TODO GR-14657 change this to NONE
                        sharableConfig.polyglotAccess == null ? engine.getAPIAccess().getPolyglotAccessAll() : sharableConfig.polyglotAccess,
                        sharableConfig.nativeAccessAllowed,
                        sharableConfig.createThreadAllowed,
                        false,
                        false,
                        false,
                        null,
                        Collections.emptyMap(),
                        Collections.emptySet(),
                        sharableConfig.originalOptions,
                        fileSystemConfig,
                        engine.logHandler,
                        sharableConfig.createProcessAllowed,
                        null,
                        engine.getAPIAccess().getEnvironmentAccessInherit(),
                        null,
                        sharableConfig.timeZone,
                        null,
                        null,
                        null,
                        sharableConfig.allowValueSharing,
                        sharableConfig.useSystemExit,
                        null, null, null, null);
    }

    PolyglotContextConfig(PolyglotEngineImpl engine, SandboxPolicy sandboxPolicy, Boolean forceSharing,
                    OutputStream out, OutputStream err, InputStream in,
                    boolean hostLookupAllowed, Object polyglotAccess, boolean nativeAccessAllowed,
                    boolean createThreadAllowed, boolean hostClassLoadingAllowed,
                    boolean contextOptionsAllowed, boolean allowExperimentalOptions,
                    Predicate<String> classFilter, Map<String, String[]> applicationArguments,
                    Set<String> onlyLanguages, Map<String, String> options, FileSystemConfig fileSystemConfig, LogHandler logHandler,
                    boolean createProcessAllowed, ProcessHandler processHandler, Object environmentAccess, Map<String, String> environment,
                    ZoneId timeZone, PolyglotLimits limits, ClassLoader hostClassLoader, Object hostAccess, boolean allowValueSharing, boolean useSystemExit,
                    Map<String, Object> creatorArguments, Runnable onCancelled, Consumer<Integer> onExited, Runnable onClosed) {
        assert out != null;
        assert err != null;
        assert in != null;
        assert environmentAccess != null;
        assert sandboxPolicy != null;
        this.api = engine.getAPIAccess();
        this.sandboxPolicy = sandboxPolicy;
        this.forceCodeSharing = forceSharing;
        this.out = out;
        this.err = err;
        this.in = in;
        this.hostLookupAllowed = hostLookupAllowed;
        this.polyglotAccess = polyglotAccess;
        this.nativeAccessAllowed = nativeAccessAllowed;
        this.createThreadAllowed = createThreadAllowed;
        this.hostClassLoadingAllowed = hostClassLoadingAllowed;
        this.innerContextOptionsAllowed = contextOptionsAllowed;
        this.allowExperimentalOptions = allowExperimentalOptions;
        this.createProcessAllowed = createProcessAllowed;
        this.classFilter = classFilter;
        this.applicationArguments = applicationArguments;
        this.onlyLanguages = onlyLanguages;
        this.allowedPublicLanguages = onlyLanguages.isEmpty() ? engine.getLanguages().keySet() : onlyLanguages;
        this.fileSystemConfig = fileSystemConfig;
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
        List<OptionDescriptor> deprecatedOptions = null;

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
            OptionDescriptor d = targetOptions.put(engine, optionKey, options.get(optionKey), allowExperimentalOptions);
            if (d != null && d.isDeprecated()) {
                if (deprecatedOptions == null) {
                    deprecatedOptions = new ArrayList<>();
                }
                deprecatedOptions.add(d);
            }
        }

        if (sandboxPolicy != SandboxPolicy.TRUSTED) {
            for (String language : allowedPublicLanguages) {
                engine.idToLanguage.get(language).validateSandbox(sandboxPolicy);
            }
        }
        this.configuredInstruments = instruments == null ? Collections.emptyList() : instruments;
        this.configuredLanguages = languages == null ? Collections.emptySet() : languages;
        this.processHandler = processHandler;
        this.environmentAccess = environmentAccess;
        this.customEnvironment = environment == null || environment.isEmpty() ? Collections.emptyMap() : new HashMap<>(environment);
        this.hostAccess = hostAccess;
        this.hostClassLoader = hostClassLoader;
        this.useSystemExit = useSystemExit;
        this.creatorArguments = creatorArguments;
        this.onCancelled = onCancelled;
        this.onExited = onExited;
        this.onClosed = onClosed;

        engine.printDeprecatedOptionsWarning(deprecatedOptions);
    }

    boolean isCodeSharingForced() {
        return forceCodeSharing != null && forceCodeSharing;
    }

    boolean isCodeSharingDisabled() {
        return forceCodeSharing != null && !forceCodeSharing;
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

    boolean isAllowIO() {
        return !FileSystems.hasNoAccess(fileSystemConfig.fileSystem);
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
            if (polyglotAccess == from.getAPIAccess().getPolyglotAccessAll()) {
                if (allowedPublicLanguages.contains(to.info.getId())) {
                    return true;
                }
            } else {
                if (from == to) {
                    return true;
                }
                Set<String> configuredAccess = from.engine.getAPIAccess().getEvalAccess(polyglotAccess, from.getId());
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
        Map<String, String> result = resolvedEnvironment;
        if (result == null) {
            synchronized (this) {
                result = resolvedEnvironment;
                if (result == null) {
                    if (environmentAccess == api.getEnvironmentAccessNone()) {
                        result = Collections.unmodifiableMap(customEnvironment);
                    } else if (PolyglotEngineImpl.ALLOW_ENVIRONMENT_ACCESS && environmentAccess == api.getEnvironmentAccessInherit()) {
                        result = System.getenv();  // System.getenv returns unmodifiable map.
                        if (!customEnvironment.isEmpty()) {
                            result = new HashMap<>(result);
                            result.putAll(customEnvironment);
                            result = Collections.unmodifiableMap(result);
                        }
                    } else {
                        throw PolyglotEngineException.unsupported(String.format("Unsupported EnvironmentAccess: %s", environmentAccess));
                    }
                    resolvedEnvironment = result;
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
