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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

@TruffleInstrument.Registration(id = WarmupEstimatorInstrument.ID, name = "Warmup Estimator", version = WarmupEstimatorInstrument.VERSION)
public class WarmupEstimatorInstrument extends TruffleInstrument {

    public static final String ID = "warmup";
    public static final String VERSION = "0.0.1";

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
    @Option(name = "", help = "Enable the Warmup Estimator (default: false).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    @Option(name = "RootNames", help = "A comma separated list of names of roots being benchmarked, ie. that should be instrumented. NOTE: Only first occurrence of a name will be instrumented.", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<String> ROOT_NAMES = new OptionKey<>("");
    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to stdout by default.", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");
    @Option(name = "Output", help = "Can be: 'raw' for json array of raw samples; 'json' for included post processing of samples; 'simple' for just the human-readable post-processed result (default: simple)", category = OptionCategory.USER) static final OptionKey<Output> OUTPUT = new OptionKey<>(
                    Output.SIMPLE, CLI_OUTPUT_TYPE);
    @Option(name = "Epsilon", help = "Epsilon value. It's inferred if the value is 0. (default: 1.05)", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<Double> EPSILON = new OptionKey<>(1.05);

    private boolean enabled;
    private final Map<String, WarmupEstimatorNode> nodes = new HashMap<>();

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WarmupEstimatorInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        enabled = env.getOptions().get(WarmupEstimatorInstrument.ENABLED);
        if (enabled) {
            final String[] rootNames = rootNames(env);
            if (rootNames.length == 0) {
                throw new IllegalArgumentException("RootNames must be set");
            }
            env.getInstrumenter().attachExecutionEventFactory(filter(rootNames), context -> createNode(env, context));
        }
    }

    private synchronized ExecutionEventNode createNode(Env env, EventContext context) {
        final TruffleLogger logger = env.getLogger(this.getClass());
        final String rootName = context.getInstrumentedNode().getRootNode().getName();
        WarmupEstimatorNode node = nodes.get(rootName);
        if (node == null) {
            logger.log(Level.INFO, "Instrumenting root named " + rootName + " on " + context.getInstrumentedSourceSection());
            node = new WarmupEstimatorNode();
            nodes.put(rootName, node);
            return node;
        }
        logger.log(Level.WARNING, "Ignoring multiple roots named " + rootName + " on " + context.getInstrumentedSourceSection());
        return null;
    }

    private static SourceSectionFilter filter(String[] rootNames) {
        return SourceSectionFilter.newBuilder().//
                        includeInternal(false).//
                        tagIs(StandardTags.RootTag.class).//
                        rootNameIs(name -> contains(rootNames, name)).//
                        build();
    }

    private static String[] rootNames(Env env) {
        final String rootNamesOpt = env.getOptions().get(ROOT_NAMES);
        return rootNamesOpt.split(",");
    }

    private static boolean contains(String[] rootNames, String name) {
        for (String rootName : rootNames) {
            if (rootName.equals(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onDispose(Env env) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("No roots with names " + ROOT_NAMES.getValue(env.getOptions()) + " found during execution.");
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
        for (String rootName : nodes.keySet()) {
            final WarmupEstimatorNode node = nodes.get(rootName);
            final List<Long> times = node.getTimes();
            results.add(new Results(rootName, times, epsilon));
        }
        return results;
    }

    private PrintStream outputStream(Env env, OptionValues options) {
        final String outputPath = OUTPUT_FILE.getValue(options);
        if ("".equals(outputPath)) {
            return new PrintStream(env.out());
        } else {
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
    }

    enum Output {
        SIMPLE,
        JSON,
        RAW,
    }
}
