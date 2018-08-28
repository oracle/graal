/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

final class PolyglotContextConfig {
    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    final OutputStream out;
    final OutputStream err;
    final InputStream in;
    final boolean hostAccessAllowed;
    final boolean nativeAccessAllowed;
    final boolean createThreadAllowed;
    final boolean hostClassLoadingAllowed;
    final Predicate<String> classFilter;
    private final Map<String, String[]> applicationArguments;
    final Set<String> allowedPublicLanguages;
    private final Map<String, OptionValuesImpl> optionsByLanguage;
    @CompilationFinal FileSystem fileSystem;
    final Map<String, Level> logLevels;    // effectively final
    final Handler logHandler;

    PolyglotContextConfig(PolyglotEngineImpl engine, OutputStream out, OutputStream err, InputStream in,
                    boolean hostAccessAllowed, boolean nativeAccessAllowed, boolean createThreadAllowed,
                    boolean hostClassLoadingAllowed, Predicate<String> classFilter,
                    Map<String, String[]> applicationArguments, Set<String> allowedPublicLanguages,
                    Map<String, String> options, FileSystem fileSystem, Handler logHandler) {
        assert out != null;
        assert err != null;
        assert in != null;
        this.out = out;
        this.err = err;
        this.in = in;
        this.hostAccessAllowed = hostAccessAllowed;
        this.nativeAccessAllowed = nativeAccessAllowed;
        this.createThreadAllowed = createThreadAllowed;
        this.hostClassLoadingAllowed = hostClassLoadingAllowed;
        this.classFilter = classFilter;
        this.applicationArguments = applicationArguments;
        this.allowedPublicLanguages = allowedPublicLanguages;
        this.fileSystem = fileSystem;
        this.optionsByLanguage = new HashMap<>();
        this.logHandler = logHandler;
        this.logLevels = new HashMap<>(engine.logLevels);
        for (String optionKey : options.keySet()) {
            String group = PolyglotEngineImpl.parseOptionGroup(optionKey);
            if (group.equals(PolyglotEngineOptions.OPTION_GROUP_LOG)) {
                logLevels.put(PolyglotEngineImpl.parseLoggerName(optionKey), Level.parse(options.get(optionKey)));
                continue;
            }
            final PolyglotLanguage language = findLanguageForOption(engine, optionKey, group);
            OptionValuesImpl languageOptions = optionsByLanguage.get(language.getId());
            if (languageOptions == null) {
                languageOptions = language.getOptionValues().copy();
                optionsByLanguage.put(language.getId(), languageOptions);
            }
            languageOptions.put(optionKey, options.get(optionKey));
        }
    }

    String[] getApplicationArguments(PolyglotLanguage lang) {
        String[] args = applicationArguments.get(lang.getId());
        if (args == null) {
            args = EMPTY_STRING_ARRAY;
        }
        return args;
    }

    OptionValuesImpl getOptionValues(PolyglotLanguage lang) {
        OptionValuesImpl values = optionsByLanguage.get(lang.getId());
        if (values == null) {
            values = lang.getOptionValues();
        }
        return values.copy();
    }

    private static PolyglotLanguage findLanguageForOption(PolyglotEngineImpl engine, final String optionKey, String group) {
        PolyglotLanguage language = engine.idToLanguage.get(group);
        if (language == null) {
            if (engine.isEngineGroup(group)) {
                // Test that "engine options" are not present among the options designated for
                // this context
                if (engine.getAllOptions().get(optionKey) != null) {
                    throw new IllegalArgumentException("Option " + optionKey + " is an engine option. Engine level options can only be configured for contexts without a shared engine set." +
                                    " To resolve this, configure the option when creating the Engine or create a context without a shared engine.");
                }
            }
            throw OptionValuesImpl.failNotFound(engine.getAllOptions(), optionKey);
        } else {
            // there should not be any overlaps -> engine creation should already fail
            assert !engine.isEngineGroup(group);
        }
        return language;
    }

}
