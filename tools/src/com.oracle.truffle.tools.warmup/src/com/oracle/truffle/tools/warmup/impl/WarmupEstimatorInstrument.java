/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.instrumentation.StandardTags;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

@TruffleInstrument.Registration(id = WarmupEstimatorInstrument.ID, name = "Warmup Estimator", version = WarmupEstimatorInstrument.VERSION)
public class WarmupEstimatorInstrument extends TruffleInstrument {

    public static final String ID = "warmup";
    public static final String VERSION = "0.0.1";

    @Option(name = "", help = "Enable the Warmup Estimator (default: false).", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    @Option(name = "RootName", help = "", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<String> ROOT_NAME = new OptionKey<>("");
    @Option(name = "OutputFile", help = "Save output to the given file. Output is printed to output stream by default.", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<String> OUTPUT_FILE = new OptionKey<>("");
    @Option(name = "Epsilon", help = "Save output to the given file. Output is printed to output stream by default.", category = OptionCategory.USER, stability = OptionStability.EXPERIMENTAL) //
    static final OptionKey<Double> EPSILON = new OptionKey<>(0.05);

    private boolean enabled;
    private WarmupEstimatorNode node;

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new WarmupEstimatorInstrumentOptionDescriptors();
    }

    @Override
    protected void onCreate(Env env) {
        enabled = env.getOptions().get(WarmupEstimatorInstrument.ENABLED);
        if (enabled) {
            final String rootName = env.getOptions().get(ROOT_NAME);
            if (rootName.equals("")) {
                throw new IllegalArgumentException("RootName must be set");
            }
            final SourceSectionFilter filter = SourceSectionFilter.newBuilder().includeInternal(false).tagIs(StandardTags.RootTag.class).rootNameIs(rootName::equals).build();
            env.getInstrumenter().attachExecutionEventFactory(filter, context -> {
                if (node == null) {
                    node = new WarmupEstimatorNode(env.getOptions().get(EPSILON));
                    return node;
                }
                 throw new IllegalStateException("Cannot estimate warmup for multiple roots.");
            });
        }
    }

    @Override
    protected void onDispose(Env env) {
        final String outputPath = OUTPUT_FILE.getValue(env.getOptions());
        if ("".equals(outputPath)) {
            node.printSimpleResults(new PrintStream(env.out()));
        } else {
            final File file = new File(outputPath);
            if (file.exists()) {
                throw new IllegalArgumentException("Cannot redirect output to an existing file!");
            }
            try {
                node.printJsonResults(new PrintStream(new FileOutputStream(file)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        super.onDispose(env);
    }

}
