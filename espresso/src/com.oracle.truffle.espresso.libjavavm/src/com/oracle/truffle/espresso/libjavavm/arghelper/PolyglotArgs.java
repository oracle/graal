/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.libjavavm.arghelper;

import static com.oracle.truffle.espresso.libjavavm.Arguments.abort;
import static com.oracle.truffle.espresso.libjavavm.Arguments.abortExperimental;
import static com.oracle.truffle.espresso.libjavavm.arghelper.ArgumentsHandler.isBooleanOption;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.graalvm.launcher.Launcher;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;

/**
 * Handles communicating options to polyglot.
 */
class PolyglotArgs {
    private final Context.Builder builder;
    private final ArgumentsHandler handler;

    private Engine tempEngine;

    private Path logFile;

    PolyglotArgs(Context.Builder builder, ArgumentsHandler handler) {
        this.builder = builder;
        this.handler = handler;
    }

    private Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.newBuilder().useSystemProperties(false).build();
        }
        return tempEngine;
    }

    void argumentProcessingDone() {
        if (tempEngine != null) {
            tempEngine.close();
            tempEngine = null;
        }
        if (logFile != null) {
            try {
                builder.logHandler(Launcher.newLogStream(logFile));
            } catch (IOException ioe) {
                throw abort(ioe.toString());
            }
        }
    }

    void parsePolyglotOption(String arg, boolean experimentalOptions) {
        if (arg.length() <= 2 || !arg.startsWith("--")) {
            throw abort(String.format("Unrecognized option: %s%n", arg));
        }
        int eqIdx = arg.indexOf('=');
        String key;
        String value;
        if (eqIdx < 0) {
            key = arg.substring(2);
            value = "";
        } else {
            key = arg.substring(2, eqIdx);
            value = arg.substring(eqIdx + 1);
        }

        int index = key.indexOf('.');
        String group = key;
        if (index >= 0) {
            group = group.substring(0, index);
        }
        parsePolyglotOption(group, key, value, arg, experimentalOptions);
    }

    void parsePolyglotOption(String group, String key, String value, String arg, boolean experimentalOptions) {
        if ("log".equals(group)) {
            if (key.endsWith(".level")) {
                try {
                    Level.parse(value);
                    builder.option(key, value);
                } catch (IllegalArgumentException e) {
                    throw abort(String.format("Invalid log level %s specified. %s'", arg, e.getMessage()));
                }
                return;
            } else if (key.equals("log.file")) {
                logFile = Paths.get(value);
                return;
            }
        }
        OptionDescriptor descriptor = findOptionDescriptor(group, key);
        if (descriptor == null) {
            descriptor = findOptionDescriptor("java", "java" + "." + key);
            if (descriptor == null) {
                throw abort(String.format("Unrecognized option: %s%n", arg));
            }
        }
        String actualValue = value;
        if (isBooleanOption(descriptor) && actualValue.isEmpty()) {
            actualValue = "true";
        }
        try {
            descriptor.getKey().getType().convert(actualValue);
        } catch (IllegalArgumentException e) {
            throw abort(String.format("Invalid argument %s specified. %s'", arg, e.getMessage()));
        }
        if (!experimentalOptions && descriptor.getStability() == OptionStability.EXPERIMENTAL) {
            throw abortExperimental(String.format("Option '%s' is experimental and must be enabled via '--experimental-options'%n" +
                            "Do not use experimental options in production environments.", arg));
        }
        // use the full name of the found descriptor
        builder.option(descriptor.getName(), actualValue);
    }

    private OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        if ("engine".equals(group) || "compiler".equals(group)) {
            descriptors = getTempEngine().getOptions();
        } else {
            Engine engine = getTempEngine();
            if (engine.getLanguages().containsKey(group)) {
                descriptors = engine.getLanguages().get(group).getOptions();
            } else if (engine.getInstruments().containsKey(group)) {
                descriptors = engine.getInstruments().get(group).getOptions();
            }
        }
        if (descriptors == null) {
            return null;
        }
        return descriptors.get(key);
    }

    void printToolsHelp(OptionCategory optionCategory) {
        Map<Instrument, List<PrintableOption>> instrumentsOptions = new HashMap<>();
        List<Instrument> instruments = sortedInstruments(getTempEngine());
        for (Instrument instrument : instruments) {
            List<PrintableOption> options = filterOptions(instrument.getOptions(), optionCategory);
            if (!options.isEmpty()) {
                instrumentsOptions.put(instrument, options);
            }
        }
        if (!instrumentsOptions.isEmpty()) {
            handler.printRaw(optionsTitle("tool", optionCategory));
            for (Instrument instrument : instruments) {
                List<PrintableOption> options = instrumentsOptions.get(instrument);
                if (options != null) {
                    printOptions(options, "  " + instrument.getName() + ":", 4);
                }
            }
        }
    }

    void printEngineHelp(OptionCategory optionCategory) {
        List<PrintableOption> engineOptions = filterOptions(getTempEngine().getOptions(), optionCategory);
        if (!engineOptions.isEmpty()) {
            printOptions(engineOptions, optionsTitle("engine", optionCategory), 2);
        }
    }

    void printLanguageHelp(OptionCategory optionCategory) {
        Map<Language, List<PrintableOption>> languagesOptions = new HashMap<>();
        List<Language> languages = sortedLanguages(getTempEngine());
        for (Language language : languages) {
            List<PrintableOption> options = filterOptions(language.getOptions(), optionCategory);
            if (!options.isEmpty()) {
                languagesOptions.put(language, options);
            }
        }
        if (!languagesOptions.isEmpty()) {
            handler.printRaw(optionsTitle("language", optionCategory));
            for (Language language : languages) {
                List<PrintableOption> options = languagesOptions.get(language);
                if (options != null) {
                    printOptions(options, "  " + language.getName() + ":", 4);
                }
            }
        }
    }

    static final class PrintableOption implements Comparable<PrintableOption> {
        final String option;
        final String description;

        private PrintableOption(String option, String description) {
            this.option = option;
            this.description = description;
        }

        @Override
        public int compareTo(PrintableOption o) {
            return this.option.compareTo(o.option);
        }
    }

    private static String optionsTitle(String kind, OptionCategory optionCategory) {
        String category = switch (optionCategory) {
            case USER -> "User ";
            case EXPERT -> "Expert ";
            case INTERNAL -> "Internal ";
            default -> "";
        };
        return category + kind + " options:";
    }

    private void printOptions(List<PrintableOption> options, String title, int indentation) {
        Collections.sort(options);
        handler.printRaw(title);
        for (PrintableOption option : options) {
            printOption(option, indentation);
        }
    }

    private void printOption(PrintableOption option, int indentation) {
        handler.printLauncherOption(option.option, option.description, indentation);
    }

    private static List<PrintableOption> filterOptions(OptionDescriptors descriptors, OptionCategory optionCategory) {
        List<PrintableOption> options = new ArrayList<>();
        for (OptionDescriptor descriptor : descriptors) {
            if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
                options.add(asPrintableOption(descriptor));
            }
        }
        return options;
    }

    private static boolean sameCategory(OptionDescriptor descriptor, OptionCategory optionCategory) {
        return descriptor.getCategory().ordinal() == optionCategory.ordinal();
    }

    private static PrintableOption asPrintableOption(OptionDescriptor descriptor) {
        StringBuilder key = new StringBuilder("--");
        key.append(descriptor.getName());
        Object defaultValue = descriptor.getKey().getDefaultValue();
        if (defaultValue != Boolean.FALSE) {
            key.append("=<");
            key.append(descriptor.getKey().getType().getName());
            key.append(">");
        }
        return new PrintableOption(key.toString(), descriptor.getHelp());
    }

    private static List<Language> sortedLanguages(Engine engine) {
        List<Language> languages = new ArrayList<>(engine.getLanguages().values());
        languages.sort(Comparator.comparing(Language::getId));
        return languages;
    }

    private static List<Instrument> sortedInstruments(Engine engine) {
        List<Instrument> instruments = new ArrayList<>();
        for (Instrument instrument : engine.getInstruments().values()) {
            // no options not accessible to the user.
            if (!instrument.getOptions().iterator().hasNext()) {
                continue;
            }
            instruments.add(instrument);
        }
        instruments.sort(Comparator.comparing(Instrument::getId));
        return instruments;
    }

}
