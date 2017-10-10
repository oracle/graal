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
package com.oracle.truffle.tools.profiler.impl;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUSampler;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@linkplain TruffleInstrument instrument} for the CPU sampler.
 *
 * @since 0.29
 */
@TruffleInstrument.Registration(id = CPUSamplerInstrument.ID, name = "CPU Sampler", version = "0.1", services = {CPUSampler.class})
public class CPUSamplerInstrument extends TruffleInstrument {

    /**
     * Default constructor.
     *
     * @since 0.29
     */
    public CPUSamplerInstrument() {
    }

    /**
     * A string used to identify the sampler, i.e. as the name of the tool.
     *
     * @since 0.29
     */
    public static final String ID = "cpusampler";
    private static CPUSampler sampler;
    OptionDescriptors descriptors = null;
    private static ProfilerToolFactory<CPUSampler> factory;

    public static void setFactory(ProfilerToolFactory<CPUSampler> factory) {
        CPUSamplerInstrument.factory = factory;
    }

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(CPUSampler.class.getName(), true, CPUSampler.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // Can not happen
        }
    }

    /**
     * Called to create the Instrument.
     *
     * @param env environment information for the instrument
     * @since 0.29
     */
    @Override
    protected void onCreate(Env env) {
        sampler = factory.create(env);
        if (env.getOptions().get(CPUSamplerCLI.ENABLED)) {
            sampler.setPeriod(env.getOptions().get(CPUSamplerCLI.SAMPLE_PERIOD));
            sampler.setDelay(env.getOptions().get(CPUSamplerCLI.DELAY_PERIOD));
            sampler.setStackLimit(env.getOptions().get(CPUSamplerCLI.STACK_LIMIT));
            sampler.setFilter(getSourceSectionFilter(env));
            sampler.setMode(env.getOptions().get(CPUSamplerCLI.MODE));
            sampler.setCollecting(true);
        }
        env.registerService(sampler);
    }

    private static SourceSectionFilter getSourceSectionFilter(Env env) {
        final CPUSampler.Mode mode = env.getOptions().get(CPUSamplerCLI.MODE);
        final boolean statements = mode == CPUSampler.Mode.STATEMENTS;
        final boolean internals = env.getOptions().get(CPUSamplerCLI.SAMPLE_INTERNAL);
        final Object[] filterRootName = env.getOptions().get(CPUSamplerCLI.FILTER_ROOT);
        final Object[] filterFile = env.getOptions().get(CPUSamplerCLI.FILTER_FILE);
        final String filterLanguage = env.getOptions().get(CPUSamplerCLI.FILTER_LANGUAGE);
        return CPUSamplerCLI.buildFilter(true, statements, false, internals, filterRootName, filterFile, filterLanguage);
    }

    /**
     * @return All the {@link OptionDescriptors options} provided by the {@link CPUSampler}.
     * @since 0.29
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        List<OptionDescriptor> descriptorList = new ArrayList<>();
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.ENABLED, ID).category(OptionCategory.USER).help("Enable the CPU sampler.").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.SAMPLE_PERIOD, ID + ".Period").category(OptionCategory.USER).help("Period in milliseconds to sample the stack.").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.DELAY_PERIOD, ID + ".Delay").category(OptionCategory.USER).help(
                        "Delay the sampling for this many milliseconds (default: 0).").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.STACK_LIMIT, ID + ".StackLimit").category(OptionCategory.USER).help("Maximum number of maximum stack elements.").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.OUTPUT, ID + ".Output").category(OptionCategory.USER).help(
                        "Print a 'histogram' or 'calltree' as output (default:HISTOGRAM).").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.MODE, ID + ".Mode").category(OptionCategory.USER).help(
                        "Describes level of sampling detail. NOTE: Increased detail can lead to reduced accuracy. Modes:" + System.lineSeparator() +
                                        "'compiled' - samples roots excluding inlined functions (default)" + System.lineSeparator() + "'roots' - samples roots including inlined functions" +
                                        System.lineSeparator() + "'statements' - samples all statements.").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.SAMPLE_INTERNAL, ID + ".SampleInternal").category(OptionCategory.USER).help("Capture internal elements (default:false).").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.FILTER_ROOT, ID + ".FilterRootName").category(OptionCategory.USER).help(
                        "Wildcard filter for program roots. (eg. Math.*, default:*).").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.FILTER_FILE, ID + ".FilterFile").category(OptionCategory.USER).help(
                        "Wildcard filter for source file paths. (eg. *program*.sl, default:*).").build());
        descriptorList.add(OptionDescriptor.newBuilder(CPUSamplerCLI.FILTER_LANGUAGE, ID + ".FilterLanguage").category(OptionCategory.USER).help(
                        "Only profile languages with mime-type. (eg. +, default:no filter).").build());
        descriptors = OptionDescriptors.create(descriptorList);
        return descriptors;
    }

    /**
     * Called when the Instrument is to be disposed.
     *
     * @param env environment information for the instrument
     * @since 0.29
     */
    @Override
    protected void onDispose(Env env) {
        if (env.getOptions().get(CPUSamplerCLI.ENABLED)) {
            CPUSamplerCLI.handleOutput(env, sampler, descriptors);
        }
        sampler.close();
    }
}
