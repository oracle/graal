/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.warmup.impl;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

@TruffleInstrument.Registration(id = WarmupEstimatorInstrument.ID, name = "Warmup Estimator", version = WarmupEstimatorInstrument.VERSION)
public class WarmupEstimatorInstrument extends TruffleInstrument {

    public static final String ID = "warmup";
    public static final String VERSION = "0.0.1";

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output", optionString -> {
        try {
            return Output.valueOf(optionString.toUpperCase());
        } catch (IllegalArgumentException e) {
            StringBuilder message = new StringBuilder("Wrong output specified. Output can be one of:");
            for (Output output : Output.values()) {
                message.append(" ");
                message.append(output.toString().toLowerCase());
            }
            message.append(". For example: --warmup.Output=" + Output.SIMPLE.toString());
            throw new IllegalArgumentException(message.toString());
        }
    });

    static final OptionType<List<Location>> LOCATION_OPTION_TYPE = new OptionType<>("Location", optionString -> {
        if (optionString.isEmpty()) {
            throw new IllegalArgumentException("Root option must be set. " +
                            "If no root is set nothing can be instrumented. " +
                            "Root must be specified as 'rootName:fileName:lineNumber' e.g. --" + ID + "Root=foo:foo.js:14");
        }
        final String[] split = optionString.split(",");
        final List<Location> locations = new ArrayList<>();
        for (String locationString : split) {
            locations.add(Location.parseLocation(locationString));
        }
        return Collections.unmodifiableList(locations);
    });

    @Option(name = "", help = "Enable the Warmup Estimator (default: false).", category = OptionCategory.USER) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    @Option(help = "Specifies the root representing a benchmark iteration as 'rootName:fileName:lineNumber' where any of the parts are optional. " +
                    "Multiple entries can be specified separated by commas. (e.g. 'main::,foo:foo.js:,fact:factorial.js:14').", category = OptionCategory.USER) //
    static final OptionKey<List<Location>> Root = new OptionKey<>(Collections.emptyList(), LOCATION_OPTION_TYPE);
    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to stdout by default.", category = OptionCategory.USER) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");
    @Option(name = "Output", help = "Can be: 'raw' for json array of raw samples; 'json' for included post processing of samples; 'simple' for just the human-readable post-processed result (default: simple)", //
                    category = OptionCategory.USER) //
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.SIMPLE, CLI_OUTPUT_TYPE);
    @Option(name = "Epsilon", help = "Sets the epsilon value which specifies the tolerance for peak performance detection. It's inferred if the value is 0. (default: 1.05)", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<Double> EPSILON = new OptionKey<>(1.05);
    private final Map<Location, List<Long>> locationsToTimes = new HashMap<>();
    private boolean enabled;

    private static int parseInt(String string) {
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Unable to parse line number from given input: " + string);
        }
    }

    private static SourceSectionFilter filter(Location location) {
        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder().//
                        includeInternal(false).//
                        tagIs(StandardTags.RootTag.class);
        if (!Objects.equals(location.rootName, "")) {
            builder.rootNameIs(location.rootName::equals);
        }
        if (!Objects.equals(location.fileName, "")) {
            builder.sourceIs(s -> location.fileName.equals(s.getName()));
        }
        if (location.line != null) {
            builder.lineStartsIn(SourceSectionFilter.IndexRange.byLength(location.line, 1));
        }
        return builder.build();
    }

    private static PrintStream outputStream(Env env, OptionValues options) {
        final String outputPath = OUTPUT_FILE.getValue(options);
        if ("".equals(outputPath)) {
            return new PrintStream(env.out());
        }
        final File file = new File(outputPath);
        if (file.exists()) {
            throw new IllegalArgumentException("Cannot redirect output to an existing file!");
        }
        try {
            return new PrintStream(new FileOutputStream(file));
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("File not found for argument " + outputPath, e);
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WarmupEstimatorInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        final OptionValues options = env.getOptions();
        enabled = options.get(WarmupEstimatorInstrument.ENABLED);
        if (enabled) {
            final List<Location> locations = options.get(Root);
            if (locations.size() == 0) {
                throw new IllegalArgumentException("Locations must be set");
            }
            final Instrumenter instrumenter = env.getInstrumenter();
            for (Location location : locations) {
                instrumenter.attachExecutionEventFactory(filter(location), context -> createNode(env, context, location));
            }
        }
    }

    private synchronized ExecutionEventNode createNode(Env env, EventContext context, Location location) {
        final TruffleLogger logger = env.getLogger(this.getClass());
        List<Long> times = locationsToTimes.get(location);
        if (times == null) {
            logger.log(Level.INFO, "Instrumenting root like " + location + " on " + context.getInstrumentedSourceSection());
            times = new ArrayList<>();
            locationsToTimes.put(location, times);
            return new WarmupEstimatorNode(times);
        }
        logger.log(Level.WARNING, "Ignoring multiple roots like " + location + " on " + context.getInstrumentedSourceSection());
        return null;
    }

    @Override
    protected void onDispose(Env env) {
        if (locationsToTimes.isEmpty()) {
            env.getLogger(this.getClass()).log(Level.WARNING, "No roots like " + Root.getValue(env.getOptions()) + " found during execution.");
        }
        final OptionValues options = env.getOptions();
        final List<Results> results = results(EPSILON.getValue(options));
        try (PrintStream stream = outputStream(env, options)) {
            final ResultsPrinter printer = new ResultsPrinter(results, stream);
            switch (OUTPUT.getValue(options)) {
                case SIMPLE:
                    printer.printSimpleResults();
                    break;
                case JSON:
                    printer.printJsonResults();
                    break;
                case RAW:
                    printer.printRawResults();
                    break;
            }
        }
        super.onDispose(env);
    }

    private List<Results> results(Double epsilon) {
        final List<Results> results = new ArrayList<>();
        for (Location location : locationsToTimes.keySet()) {
            final List<Long> times = locationsToTimes.get(location);
            results.add(new Results(location.toString(), times, epsilon));
        }
        return results;
    }

    enum Output {
        SIMPLE,
        JSON,
        RAW,
    }

    static final class Location {
        final String rootName;
        final String fileName;
        final Integer line;

        Location(String rootName, String fileName, Integer line) {
            this.rootName = rootName;
            this.fileName = fileName;
            this.line = line;
        }

        private static Location parseLocation(String locationString) {
            final String[] strings = locationString.split(":");
            final String rootName = strings[0];
            final String fileName = strings.length > 1 ? strings[1] : "";
            final Integer line = strings.length == 3 && !Objects.equals(strings[2], "") ? parseInt(strings[2]) : null;
            return new Location(rootName, fileName, line);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || o instanceof Location) {
                return false;
            }
            Location location = (Location) o;
            return Objects.equals(rootName, location.rootName) &&
                            Objects.equals(fileName, location.fileName) &&
                            Objects.equals(line, location.line);
        }

        @Override
        public int hashCode() {
            return Objects.hash(rootName, fileName, line);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(rootName);
            if (!Objects.equals(fileName, "")) {
                builder.append(':');
                builder.append(fileName);
            } else if (line != null) {
                builder.append(':');
            }
            if (line != null) {
                builder.append(':');
                builder.append(line);
            }
            return builder.toString();
        }
    }
}
