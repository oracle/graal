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

import org.graalvm.options.OptionDescriptors;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.CPUTracer;
import com.oracle.truffle.tools.profiler.MemoryTracer;

/**
 * The {@linkplain TruffleInstrument instrument} for the memory tracer.
 *
 * @since 0.30
 */
@TruffleInstrument.Registration(id = MemoryTracerInstrument.ID, name = "Memory Tracer", version = "0.2", services = {MemoryTracer.class})
public class MemoryTracerInstrument extends TruffleInstrument {

    /**
     * Default constructor.
     *
     * @since 0.30
     */
    public MemoryTracerInstrument() {
    }

    /**
     * A string used to identify the tracer, i.e. as the name of the tool.
     *
     * @since 0.30
     */
    public static final String ID = "memtracer";
    private MemoryTracer tracer;
    private static ProfilerToolFactory<MemoryTracer> factory;

    /**
     * Sets the factory which instantiates the {@link MemoryTracer}.
     *
     * @param factory the factory which instantiates the {@link MemoryTracer}.
     * @since 0.30
     */
    public static void setFactory(ProfilerToolFactory<MemoryTracer> factory) {
        if (factory == null || !factory.getClass().getName().startsWith("com.oracle.truffle.tools.profiler")) {
            throw new IllegalArgumentException("Wrong factory: " + factory);
        }
        MemoryTracerInstrument.factory = factory;
    }

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(MemoryTracer.class.getName(), true, MemoryTracer.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // Can not happen
            throw new AssertionError();
        }
    }

    /**
     * Does a lookup in the runtime instruments of the engine and returns an instance of the
     * {@link CPUTracer}.
     *
     * @since 0.33
     */
    public static MemoryTracer getTracer(Engine engine) {
        Instrument instrument = engine.getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Memory Tracer is not installed.");
        }
        return instrument.lookup(MemoryTracer.class);
    }

    /**
     * Called to create the Instrument.
     *
     * @param env environment information for the instrument
     * @since 0.30
     */
    @Override
    protected void onCreate(Env env) {
        tracer = factory.create(env);
        if (env.getOptions().get(MemoryTracerCLI.ENABLED)) {
            tracer.setFilter(getSourceSectionFilter(env));
            tracer.setStackLimit(env.getOptions().get(MemoryTracerCLI.STACK_LIMIT));
            tracer.setCollecting(true);
        }
        env.registerService(tracer);
    }

    private static SourceSectionFilter getSourceSectionFilter(Env env) {
        final boolean roots = env.getOptions().get(MemoryTracerCLI.TRACE_ROOTS);
        final boolean statements = env.getOptions().get(MemoryTracerCLI.TRACE_STATEMENTS);
        final boolean calls = env.getOptions().get(MemoryTracerCLI.TRACE_CALLS);
        final boolean internals = env.getOptions().get(MemoryTracerCLI.TRACE_INTERNAL);
        final Object[] filterRootName = env.getOptions().get(MemoryTracerCLI.FILTER_ROOT);
        final Object[] filterFile = env.getOptions().get(MemoryTracerCLI.FILTER_FILE);
        final String filterMimeType = env.getOptions().get(MemoryTracerCLI.FILTER_MIME_TYPE);
        final String filterLanguage = env.getOptions().get(MemoryTracerCLI.FILTER_LANGUAGE);
        return MemoryTracerCLI.buildFilter(roots, statements, calls, internals, filterRootName, filterFile, filterMimeType, filterLanguage);
    }

    /**
     * @return A list of the options provided by the {@link MemoryTracer}.
     * @since 0.30
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new MemoryTracerCLIOptionDescriptors();
    }

    /**
     * Called when the Instrument is to be disposed.
     *
     * @param env environment information for the instrument
     * @since 0.30
     */
    @Override
    protected void onDispose(Env env) {
        if (env.getOptions().get(MemoryTracerCLI.ENABLED)) {
            MemoryTracerCLI.handleOutput(env, tracer);
        }
        tracer.close();
    }
}
