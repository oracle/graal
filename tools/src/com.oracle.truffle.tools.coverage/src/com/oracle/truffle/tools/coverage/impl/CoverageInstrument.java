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
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.coverage.CoverageTracker;

@Registration(id = CoverageInstrument.ID, name = "Code Coverage", version = CoverageInstrument.VERSION, services = CoverageTracker.class)
public class CoverageInstrument extends TruffleInstrument {

    public static final String ID = "codecoverage";
    static final String VERSION = "0.1.0";
    private CoverageTracker tracker;
    private static Function<Env, CoverageTracker> factory;
    private Boolean enabled;

    enum Output {
        HISTOGRAM,
        LINES,
        JSON,
    }

    static final OptionType<Output> CLI_OUTPUT_TYPE = new OptionType<>("Output",
                    new Function<String, Output>() {
                        @Override
                        public Output apply(String s) {
                            try {
                                return Output.valueOf(s.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException("Output can be: histogram or json");
                            }
                        }
                    });

    // @formatter:off
    @Option(name = "", help = "Enable Coverage (default: false).", category = OptionCategory.USER, stability = OptionStability.STABLE)
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);

    @Option(name = "Output", help = "", category = OptionCategory.USER, stability = OptionStability.STABLE) //
    static final OptionKey<Output> OUTPUT = new OptionKey<>(Output.HISTOGRAM, CLI_OUTPUT_TYPE);
    // @formatter:on

    @Override
    protected void onCreate(Env env) {
        tracker = factory.apply(env);
        final OptionValues options = env.getOptions();
        enabled = ENABLED.getValue(options);
        if (enabled) {
            tracker.setFilter(getSourceFilter(options));
            tracker.setTracking(true);
            env.registerService(tracker);
        }
    }

    @Override
    protected void onDispose(Env env) {
        if (enabled) {
            CoverageCLI.handleOutput(new PrintStream(env.out()), tracker.getCoverage(), OUTPUT.getValue(env.getOptions()));
            tracker.close();
        }
    }

    private SourceFilter getSourceFilter(OptionValues options) {
        return SourceFilter.newBuilder().includeInternal(false).build();
    }

    public static void setFactory(Function<Env, CoverageTracker> factory) {
        if (factory == null || !factory.getClass().getName().startsWith("com.oracle.truffle.tools.coverage")) {
            throw new IllegalArgumentException("Wrong factory: " + factory);
        }
        CoverageInstrument.factory = factory;
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CoverageInstrumentOptionDescriptors();
    }

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(CoverageTracker.class.getName(), true, CoverageTracker.class.getClassLoader());
        } catch (ClassNotFoundException cannotHappen) {
            // Can not happen
            throw new AssertionError();
        }
    }

    public static CoverageTracker getTracker(Engine engine) {
        Instrument instrument = engine.getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Sampler is not installed.");
        }
        return instrument.lookup(CoverageTracker.class);
    }

}
