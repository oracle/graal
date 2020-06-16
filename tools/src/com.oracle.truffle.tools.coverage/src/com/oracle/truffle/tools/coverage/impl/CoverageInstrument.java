/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.impl;

import static com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.function.Function;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.coverage.CoverageTracker;
import com.oracle.truffle.tools.coverage.SourceCoverage;

@Registration(id = CoverageInstrument.ID, name = "Code Coverage", version = CoverageInstrument.VERSION, services = CoverageTracker.class)
public class CoverageInstrument extends TruffleInstrument {

    public static final String ID = "coverage";
    static final String VERSION = "0.1.0";
    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
                    new Function<String, Output>() {
                        @Override
                        public Output apply(String s) {
                            try {
                                return Output.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                StringBuilder message = new StringBuilder("Output can be one of: ");
                                for (Output output : Output.values()) {
                                    message.append(output.toString().toLowerCase());
                                    message.append(" ");
                                }
                                throw new IllegalArgumentException(message.toString());
                            }
                        }
                    });
    // @formatter:off
    @Option(name = "", help = "Enable Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    @Option(help = "Keep count of each element's coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> Count = new OptionKey<>(false);
    @Option(name = "Output", help = "Can be: human readable 'histogram' (per file coverage summary) or 'detailed' (per line coverage summary), machine readable 'json', tool compliant 'lcov'. (default: histogram)",
            category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);
    @Option(name = "FilterRootName", help = "Wildcard filter for program roots. (eg. Math.*, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Object[]> FILTER_ROOT = new OptionKey<>(new Object[0], WildcardHandler.WILDCARD_FILTER_TYPE);
    @Option(name = "FilterFile", help = "Wildcard filter for source file paths. (eg. *program*.sl, default:*).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Object[]> FILTER_FILE = new OptionKey<>(new Object[0], WildcardHandler.WILDCARD_FILTER_TYPE);
    @Option(name = "FilterMimeType", help = "Only track languages with mime-type. (eg. +, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<String> FILTER_MIME_TYPE = new OptionKey<>("");
    @Option(name = "FilterLanguage", help = "Only track languages with given ID. (eg. js, default:no filter).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<String> FILTER_LANGUAGE = new OptionKey<>("");
    @Option(name = "TrackInternal", help = "Track internal elements (default:false).", category = OptionCategory.INTERNAL)
    static final OptionKey<Boolean> TRACK_INTERNAL = new OptionKey<>(false);
    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to standard output stream by default.", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");
    @Option(help = "Consider a source code line covered only if covered in it's entirety. (default: true)", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL)
    static final OptionKey<Boolean> StrictLines = new OptionKey<>(true);
    // @formatter:on

    private static Function<Env, CoverageTracker> factory;

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(CoverageTracker.class.getName(), true, CoverageTracker.class.getClassLoader());
        } catch (ClassNotFoundException cannotHappen) {
            // Can not happen
            throw new AssertionError();
        }
    }

    private CoverageTracker tracker;
    private Boolean enabled;

    public static CoverageTracker getTracker(Engine engine) {
        Instrument instrument = engine.getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Tracker is not installed.");
        }
        return instrument.lookup(CoverageTracker.class);
    }

    public static void setFactory(Function<Env, CoverageTracker> factory) {
        if (factory == null || !factory.getClass().getName().startsWith("com.oracle.truffle.tools.coverage")) {
            throw new IllegalArgumentException("Wrong factory: " + factory);
        }
        CoverageInstrument.factory = factory;
    }

    private static PrintStream chooseOutputStream(TruffleInstrument.Env env, OptionKey<String> option) {
        try {
            if (option.hasBeenSet(env.getOptions())) {
                final String outputPath = option.getValue(env.getOptions());
                final File file = new File(outputPath);
                if (file.exists()) {
                    throw new IllegalArgumentException("Cannot redirect output to an existing file!");
                }
                return new PrintStream(new FileOutputStream(file));
            } else {
                return new PrintStream(env.out());
            }
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Cannot redirect output to a directory");
        }
    }

    private static SourceSectionFilter getSourceSectionFilter(OptionValues options) {
        final Object[] filterFile = FILTER_FILE.getValue(options);
        final String filterMimeType = FILTER_MIME_TYPE.getValue(options);
        final String filterLanguage = FILTER_LANGUAGE.getValue(options);
        final Boolean internals = TRACK_INTERNAL.getValue(options);
        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.sourceIs(source -> {
            boolean internal = (internals || !source.isInternal());
            boolean file = WildcardHandler.testWildcardExpressions(source.getPath(), filterFile);
            boolean mimeType = filterMimeType.equals("") || filterMimeType.equals(source.getMimeType());
            final boolean languageId = filterLanguage.equals("") || filterMimeType.equals(source.getLanguage());
            return internal && file && mimeType && languageId;
        });
        final Object[] filterRootName = FILTER_ROOT.getValue(options);
        builder.rootNameIs(s -> WildcardHandler.testWildcardExpressions(s, filterRootName));
        return builder.build();
    }

    @Override
    protected void onCreate(Env env) {
        tracker = factory.apply(env);
        env.registerService(tracker);
        final OptionValues options = env.getOptions();
        enabled = ENABLED.getValue(options);
        if (enabled) {
            tracker.start(new CoverageTracker.Config(getSourceSectionFilter(options), Count.getValue(options)));
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (enabled) {
            SourceCoverage[] coverage = tracker.getCoverage();
            final OptionValues options = env.getOptions();
            final boolean strictLines = StrictLines.getValue(options);
            try (PrintStream out = chooseOutputStream(env, OUTPUT_FILE)) {
                switch (OUTPUT.getValue(options)) {
                    case HISTOGRAM:
                        new CoverageCLI(out, coverage, strictLines).printHistogramOutput();
                        break;
                    case DETAILED:
                        new CoverageCLI(out, coverage, strictLines).printLinesOutput();
                        break;
                    case JSON:
                        new JSONPrinter(out, coverage).print();
                        break;
                    case LCOV:
                        new LCOVPrinter(out, coverage, strictLines).print();
                        break;
                }
            }
            tracker.close();
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CoverageInstrumentOptionDescriptors();
    }

    enum Output {
        HISTOGRAM,
        DETAILED,
        JSON,
        LCOV,
    }

}
