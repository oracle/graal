/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.tools.profiler.impl.CPUSamplerCLI.GATHER_HIT_TIMES;
import static com.oracle.truffle.tools.profiler.impl.CPUSamplerCLI.SAMPLE_CONTEXT_INITIALIZATION;

import java.lang.reflect.Method;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUSampler;

/**
 * The {@linkplain TruffleInstrument instrument} for the CPU sampler.
 *
 * @since 0.30
 */
@TruffleInstrument.Registration(id = CPUSamplerInstrument.ID, name = "CPU Sampler", version = CPUSamplerInstrument.VERSION, services = {
                CPUSampler.class}, website = "https://www.graalvm.org/tools/profiling/")
public class CPUSamplerInstrument extends TruffleInstrument {

    /**
     * Default constructor.
     *
     * @since 0.30
     */
    public CPUSamplerInstrument() {
    }

    /**
     * A string used to identify the sampler, i.e. as the name of the tool.
     *
     * @since 0.30
     */
    public static final String ID = "cpusampler";
    static final String VERSION = "0.5.0";
    private CPUSampler sampler;
    private static final ProfilerToolFactory<CPUSampler> factory = getDefaultFactory();

    @SuppressWarnings("unchecked")
    private static ProfilerToolFactory<CPUSampler> getDefaultFactory() {
        try {
            Method createFactory = CPUSampler.class.getDeclaredMethod("createFactory");
            createFactory.setAccessible(true);
            return (ProfilerToolFactory<CPUSampler>) createFactory.invoke(null);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    /**
     * Does a lookup in the runtime instruments of the engine and returns an instance of the
     * {@link CPUSampler}.
     *
     * @since 0.33
     */
    public static CPUSampler getSampler(Engine engine) {
        Instrument instrument = engine.getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Sampler is not installed.");
        }
        return instrument.lookup(CPUSampler.class);
    }

    /**
     * Called to create the Instrument.
     *
     * @param env environment information for the instrument
     * @since 0.30
     */
    @Override
    protected void onCreate(Env env) {
        sampler = factory.create(env);
        OptionValues options = env.getOptions();
        CPUSamplerCLI.EnableOptionData enableOptionData = options.get(CPUSamplerCLI.ENABLED);
        if (enableOptionData.enabled) {
            sampler.setPeriod(options.get(CPUSamplerCLI.SAMPLE_PERIOD));
            sampler.setDelay(options.get(CPUSamplerCLI.DELAY_PERIOD));
            sampler.setStackLimit(options.get(CPUSamplerCLI.STACK_LIMIT));
            sampler.setFilter(getSourceSectionFilter(env));
            sampler.setGatherSelfHitTimes(options.get(GATHER_HIT_TIMES));
            sampler.setSampleContextInitialization(options.get(SAMPLE_CONTEXT_INITIALIZATION));
            sampler.setCollecting(true);
        }
        env.registerService(sampler);
    }

    private static SourceSectionFilter getSourceSectionFilter(Env env) {
        final boolean internals = env.getOptions().get(CPUSamplerCLI.SAMPLE_INTERNAL);
        final WildcardFilter filterRootName = env.getOptions().get(CPUSamplerCLI.FILTER_ROOT);
        final WildcardFilter filterFile = env.getOptions().get(CPUSamplerCLI.FILTER_FILE);
        final String filterMimeType = env.getOptions().get(CPUSamplerCLI.FILTER_MIME_TYPE);
        final String filterLanguage = env.getOptions().get(CPUSamplerCLI.FILTER_LANGUAGE);
        return CPUSamplerCLI.buildFilter(true, false, false, internals, filterRootName, filterFile, filterMimeType, filterLanguage);
    }

    /**
     * @return All the {@link OptionDescriptors options} provided by the {@link CPUSampler}.
     * @since 0.30
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new CPUSamplerCLIOptionDescriptors();
    }

    /**
     * Called when the Instrument is to be disposed.
     *
     * @param env environment information for the instrument
     * @since 0.30
     */
    @Override
    protected void onDispose(Env env) {
        OptionValues options = env.getOptions();
        CPUSamplerCLI.EnableOptionData enableOptionData = options.get(CPUSamplerCLI.ENABLED);
        if (enableOptionData.enabled) {
            CPUSamplerCLI.handleOutput(env, sampler);
        }
        sampler.close();
    }
}
