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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.collections.EconomicSet;
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
    final EconomicSet<String> allowedPublicLanguages;
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
    private volatile ZoneId timeZone;
    final PolyglotLimits limits;
    final ClassLoader hostClassLoader;
    private final List<PolyglotInstrument> configuredInstruments;
    final HostAccess hostAccess;
    final boolean allowValueSharing;

    PolyglotContextConfig(PolyglotEngineImpl engine, OutputStream out, OutputStream err, InputStream in,
                    boolean hostLookupAllowed, PolyglotAccess polyglotAccess, boolean nativeAccessAllowed, boolean createThreadAllowed,
                    boolean hostClassLoadingAllowed, boolean allowExperimentalOptions,
                    Predicate<String> classFilter, Map<String, String[]> applicationArguments,
                    EconomicSet<String> allowedPublicLanguages, Map<String, String> options, FileSystem publicFileSystem, FileSystem internalFileSystem, Handler logHandler,
                    boolean createProcessAllowed, ProcessHandler processHandler, EnvironmentAccess environmentAccess, Map<String, String> environment,
                    ZoneId timeZone, PolyglotLimits limits, ClassLoader hostClassLoader, HostAccess hostAccess, boolean allowValueSharing) {
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
        this.allowedPublicLanguages = allowedPublicLanguages;
        this.fileSystem = publicFileSystem;
        this.internalFileSystem = internalFileSystem;
        this.optionsById = new HashMap<>();
        this.logHandler = logHandler;
        this.timeZone = timeZone;
        this.limits = limits;
        this.logLevels = new HashMap<>(engine.logLevels);
        this.allowValueSharing = allowValueSharing;
        List<PolyglotInstrument> instruments = null;
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
            targetOptions.put(optionKey, options.get(optionKey), allowExperimentalOptions);
        }
        this.configuredInstruments = instruments == null ? Collections.emptyList() : instruments;
        this.processHandler = processHandler;
        this.environmentAccess = environmentAccess;
        this.environment = environment == null ? Collections.emptyMap() : environment;
        this.hostAccess = hostAccess;
        this.hostClassLoader = hostClassLoader;
    }

    public ZoneId getTimeZone() {
        ZoneId zone = this.timeZone;
        if (zone == null) {
            zone = timeZone = ZoneId.systemDefault();
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
        return values.copy();
    }

    OptionValuesImpl getInstrumentOptionValues(PolyglotInstrument instrument) {
        OptionValuesImpl values = optionsById.get(instrument.getId());
        if (values == null) {
            values = instrument.getEngineOptionValues();
        }
        return values.copy();
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
