/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.launcher;

import static java.lang.Integer.max;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;

/**
 * Base implementation for polyglot-aware languages and tools. Prints additional language-related
 * help items, prints installed engine's options.
 */
public abstract class LanguageLauncherBase extends Launcher {
    private static Engine tempEngine;
    private boolean seenPolyglot;
    private VersionAction versionAction = VersionAction.None;

    static Engine getTempEngine() {
        if (tempEngine == null) {
            tempEngine = Engine.newBuilder().useSystemProperties(false).build();
        }
        return tempEngine;
    }

    private static String website(Instrument instrument) {
        String website = instrument.getWebsite();
        return website.isEmpty() ? "" : " (" + website + ")";
    }

    private static String title(Language language) {
        final StringBuilder title = new StringBuilder("  " + language.getName());
        final String website = language.getWebsite();
        if (!"".equals(website)) {
            title.append(" (");
            title.append(website);
            title.append(")");
        }
        title.append(":");
        return title.toString();
    }

    private static List<PrintableOption> filterOptions(OptionDescriptors descriptors, OptionCategory optionCategory) {
        List<PrintableOption> options = new ArrayList<>();
        for (OptionDescriptor descriptor : descriptors) {
            if (!descriptor.isDeprecated() && sameCategory(descriptor, optionCategory)) {
                options.add(asPrintableOption(descriptor));
            }
        }
        options.sort(PrintableOption::compareTo);
        return options;
    }

    private static boolean sameCategory(OptionDescriptor descriptor, OptionCategory optionCategory) {
        return descriptor.getCategory().ordinal() == optionCategory.ordinal();
    }

    private static Launcher.PrintableOption asPrintableOption(OptionDescriptor descriptor) {
        StringBuilder key = new StringBuilder("--");
        final String name = descriptor.getName();
        key.append(name);
        if (descriptor.isOptionMap()) {
            key.append(".<key>");
        }
        if (!Boolean.FALSE.equals(descriptor.getKey().getDefaultValue())) {
            key.append("=");
            if (descriptor.isOptionMap()) {
                key.append("<value>");
            } else {
                key.append(descriptor.getUsageSyntax());
            }
        }
        return new PrintableOption(name, key.toString(), descriptor.getHelp());
    }

    private static void addOptions(OptionDescriptors descriptors, Set<String> target) {
        for (OptionDescriptor descriptor : descriptors) {
            target.add("--" + descriptor.getName());
        }
    }

    static List<Language> sortedLanguages(Engine engine) {
        List<Language> languages = new ArrayList<>(engine.getLanguages().values());
        languages.sort(Comparator.comparing(Language::getId));
        return languages;
    }

    static List<Instrument> sortedInstruments(Engine engine) {
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

    final boolean isPolyglot() {
        return seenPolyglot;
    }

    final void setPolyglot(boolean polyglot) {
        seenPolyglot = polyglot;
    }

    final void setupContextBuilder(Context.Builder builder) {
        Path logFile = getLogFile();
        if (logFile != null) {
            try {
                builder.logHandler(newLogStream(logFile));
            } catch (IOException ioe) {
                throw abort(ioe);
            }
        }
        if (System.err != getError()) {
            builder.err(getError());
        }
        if (System.out != getOutput()) {
            builder.out(getOutput());
        }
    }

    protected void argumentsProcessingDone() {
        if (tempEngine != null) {
            tempEngine.close();
            tempEngine = null;
        }
    }

    @Override
    protected boolean runLauncherAction() {
        switch (versionAction) {
            case PrintAndExit:
                printPolyglotVersions();
                return true;
            case PrintAndContinue:
                printPolyglotVersions();
                break;
            case None:
                break;
        }
        return super.runLauncherAction();
    }

    @Override
    protected boolean parseCommonOption(String defaultOptionPrefix, Map<String, String> polyglotOptions, boolean experimentalOptions, String arg) {
        switch (arg) {
            case "--polyglot":
                seenPolyglot = true;
                break;
            case "--version:graalvm":
                versionAction = VersionAction.PrintAndExit;
                break;
            case "--show-version:graalvm":
                versionAction = VersionAction.PrintAndContinue;
                break;
            default:
                return super.parseCommonOption(defaultOptionPrefix, polyglotOptions, experimentalOptions, arg);
        }
        return true;
    }

    void handlePolyglotException(PolyglotException e) {
        String message = null;
        if (e.getMessage() != null) {
            message = e.getMessage();
        } else if (e.isInternalError()) {
            message = "Unknown error";
        }
        if (message != null) {
            System.err.println("ERROR: " + message);
        }
        if (e.isInternalError()) {
            e.printStackTrace();
        }
        if (e.isExit()) {
            System.exit(e.getExitStatus());
        } else {
            System.exit(1);
        }
    }

    @Override
    protected void printDefaultHelp(OptionCategory helpCategory) {
        super.printDefaultHelp(helpCategory);
        launcherOption("--help:languages", "Print options for all installed languages.");
        launcherOption("--help:tools", "Print options for all installed tools.");
        launcherOption("--help:engine", "Print engine options.");
        launcherOption("--version:graalvm", "Print GraalVM version information and exit.");
        launcherOption("--show-version:graalvm", "Print GraalVM version information and continue execution.");
    }

    @Override
    protected void maybePrintAdditionalHelp(OptionCategory helpCategory) {
        if (helpArg == null) {
            return;
        }
        final boolean all = helpArgIs("all");
        boolean printed = false;
        if (all || helpArgIs("languages")) {
            printed = printLanguageOptions(getTempEngine(), helpInternal, null);
        }
        if (all || helpArgIs("tools")) {
            printed = printInstrumentOptions(getTempEngine(), helpInternal, null);
        }
        if (all || helpArgIs("engine")) {
            printed = printEngineOptions(getTempEngine(), helpInternal);
        }
        if (printed) {
            return;
        }
        printed = printLanguageOptions(getTempEngine(), helpInternal, helpArg);
        printed |= printInstrumentOptions(getTempEngine(), helpInternal, helpArg);
        if (!printed) {
            printDefaultHelp(OptionCategory.USER);
        }
    }

    /**
     * Prints version information about all known {@linkplain Language languages} and
     * {@linkplain Instrument instruments} on {@linkplain System#out stdout}.
     */
    protected void printPolyglotVersions() {
        Engine engine = getTempEngine();
        String mode = isAOT() ? "Native" : "JVM";
        println(engine.getImplementationName() + " " + mode + " Polyglot Engine Version " + engine.getVersion());
        println("Java Version " + System.getProperty("java.version"));
        println("Java VM Version " + System.getProperty("java.vm.version"));
        Path graalVMHome = Engine.findHome();
        if (graalVMHome != null) {
            println("GraalVM Home " + graalVMHome);
        }
        printLanguages(engine, true);
        printInstruments(engine, true);
    }

    @Override
    protected OptionDescriptor findOptionDescriptor(String group, String key) {
        OptionDescriptors descriptors = null;
        switch (group) {
            case "engine":
                descriptors = getTempEngine().getOptions();
                break;
            default:
                Engine engine = getTempEngine();
                if (engine.getLanguages().containsKey(group)) {
                    descriptors = engine.getLanguages().get(group).getOptions();
                } else if (engine.getInstruments().containsKey(group)) {
                    descriptors = engine.getInstruments().get(group).getOptions();
                }
                break;
        }
        if (descriptors == null) {
            return null;
        }
        return descriptors.get(key);

    }

    private boolean printEngineOptions(Engine engine, boolean includeInternal) {
        List<PrintableOption> engineOptions = filterOptions(engine.getOptions(), includeInternal);
        if (!engineOptions.isEmpty()) {
            println();
            // TODO not always user
            printOptions(engineOptions, optionsTitle("engine", OptionCategory.USER), 2);
            return true;
        }
        return false;
    }

    private boolean printInstrumentOptions(Engine engine, boolean includeInternal, String id) {
        Map<Instrument, List<PrintableOption>> instrumentsOptions = new HashMap<>();
        List<Instrument> instruments = sortedInstruments(engine);
        for (Instrument instrument : instruments) {
            if (id == null || instrument.getId().equals(id)) {
                List<PrintableOption> options = filterOptions(instrument.getOptions(), includeInternal);
                if (!options.isEmpty()) {
                    instrumentsOptions.put(instrument, options);
                }
            }
        }
        if (!instrumentsOptions.isEmpty()) {
            println();
            // TODO not always user
            println(optionsTitle("tool", OptionCategory.USER));
            for (Instrument instrument : instruments) {
                List<PrintableOption> options = instrumentsOptions.get(instrument);
                if (options != null) {
                    printOptions(options, "  " + instrument.getName() + website(instrument) + ":", 4);
                }
            }
            return true;
        }
        return false;
    }

    private boolean printLanguageOptions(Engine engine, boolean includeInternal, String id) {
        Map<Language, List<PrintableOption>> languagesOptions = new HashMap<>();
        List<Language> languages = sortedLanguages(engine);
        for (Language language : languages) {
            if (id == null || language.getId().equals(id)) {
                List<PrintableOption> options = filterOptions(language.getOptions(), includeInternal);
                options.sort(PrintableOption::compareTo);
                if (!options.isEmpty()) {
                    languagesOptions.put(language, options);
                }
            }
        }
        if (!languagesOptions.isEmpty()) {
            println();
            // TODO Should not always be user
            println(optionsTitle("language", OptionCategory.USER));
            for (Language language : languages) {
                List<PrintableOption> options = languagesOptions.get(language);
                if (options != null) {
                    printOptions(options, title(language), 4);
                }
            }
            return true;
        }
        return false;
    }

    private List<PrintableOption> filterOptions(OptionDescriptors descriptors, boolean includeInternal) {
        List<PrintableOption> options = new ArrayList<>();
        for (OptionDescriptor descriptor : descriptors) {
            if (includeInternal || descriptor.getCategory() != OptionCategory.INTERNAL) {
                options.add(asPrintableOption(descriptor));
            }
        }
        return options;
    }

    private void printLanguages(Engine engine, boolean printWhenEmpty) {
        if (engine.getLanguages().isEmpty()) {
            if (printWhenEmpty) {
                println("  Installed Languages: none");
            }
        } else {
            println("  Installed Languages:");
            List<Language> languages = new ArrayList<>(engine.getLanguages().size());
            int nameLength = 0;
            for (Language language : engine.getLanguages().values()) {
                languages.add(language);
                nameLength = max(nameLength, language.getName().length());
            }
            languages.sort(Comparator.comparing(Language::getId));
            String langFormat = "    %-" + nameLength + "s%s version %s%n";
            for (Language language : languages) {
                String host;
                host = "";
                String version = language.getVersion();
                if (version == null || version.length() == 0) {
                    version = "";
                }
                System.out.printf(langFormat, language.getName().isEmpty() ? "Unnamed" : language.getName(), host, version);
            }
        }
    }

    private void printInstruments(Engine engine, boolean printWhenEmpty) {
        if (engine.getInstruments().isEmpty()) {
            if (printWhenEmpty) {
                println("  Installed Tools: none");
            }
        } else {
            println("  Installed Tools:");
            List<Instrument> instruments = sortedInstruments(engine);
            int nameLength = 0;
            for (Instrument instrument : instruments) {
                nameLength = max(nameLength, instrument.getName().length());
            }
            String instrumentFormat = "    %-" + nameLength + "s version %s%n";
            for (Instrument instrument : instruments) {
                String version = instrument.getVersion();
                if (version == null || version.length() == 0) {
                    version = "";
                }
                System.out.printf(instrumentFormat, instrument.getName().isEmpty() ? instrument.getId() : instrument.getName(), version);
            }
        }
    }

    @Override
    protected void collectArguments(Set<String> options) {
        options.add("--help:languages");
        options.add("--help:tools");
        options.add("--version:graalvm");
        options.add("--show-version:graalvm");

        Engine engine = getTempEngine();
        addOptions(engine.getOptions(), options);
        for (Instrument instrument : engine.getInstruments().values()) {
            addOptions(instrument.getOptions(), options);
        }

        String languageId = null;
        if (this instanceof AbstractLanguageLauncher) {
            languageId = ((AbstractLanguageLauncher) this).getLanguageId();
        }
        for (Language language : engine.getLanguages().values()) {
            if (language.getId().equals(languageId)) {
                for (OptionDescriptor descriptor : language.getOptions()) {
                    options.add("--" + descriptor.getName().substring(languageId.length() + 1));
                }
            }
            addOptions(language.getOptions(), options);
        }
    }
}
