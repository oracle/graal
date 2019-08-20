/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tools.profiler.HeapMonitor;

/**
 * The {@link TruffleInstrument} for the heap allocation monitor.
 *
 * @since 19.0
 */
@TruffleInstrument.Registration(id = HeapMonitorInstrument.ID, name = "Heap Allocation Monitor", version = HeapMonitorInstrument.VERSION, services = {HeapMonitor.class})
public class HeapMonitorInstrument extends TruffleInstrument {

    /**
     * Default constructor.
     *
     * @since 19.0
     */
    public HeapMonitorInstrument() {
    }

    /**
     * A string used to identify the heap allocation monitor.
     *
     * @since 19.0
     */
    public static final String ID = "heapmonitor";
    static final String VERSION = "0.1.0";
    private HeapMonitor monitor;
    private static ProfilerToolFactory<HeapMonitor> factory;

    /**
     * Sets the factory which instantiates the {@link HeapMonitor}.
     *
     * @param factory the factory which instantiates the {@link HeapMonitor}.
     * @since 19.0
     */
    public static void setFactory(ProfilerToolFactory<HeapMonitor> factory) {
        if (factory == null || !factory.getClass().getName().startsWith("com.oracle.truffle.tools.profiler")) {
            throw new IllegalArgumentException("Wrong factory: " + factory);
        }
        HeapMonitorInstrument.factory = factory;
    }

    static {
        // Be sure that the factory is initialized:
        try {
            Class.forName(HeapMonitor.class.getName(), true, HeapMonitor.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            // Can not happen
            throw new AssertionError();
        }
    }

    /**
     * Does a lookup in the runtime instruments of the engine and returns an instance of the
     * {@link HeapMonitor}.
     *
     * @since 19.0
     */
    public static HeapMonitor getMonitor(Engine engine) {
        Instrument instrument = engine.getInstruments().get(ID);
        if (instrument == null) {
            throw new IllegalStateException("Heap Monitor is not installed.");
        }
        return instrument.lookup(HeapMonitor.class);
    }

    /**
     * Called to create the Instrument.
     *
     * @param env environment information for the instrument
     * @since 19.0
     */
    @Override
    protected void onCreate(TruffleInstrument.Env env) {
        monitor = factory.create(env);
        if (env.getOptions().get(HeapMonitorInstrument.ENABLED)) {
            monitor.setCollecting(true);
        }
        env.registerService(monitor);
    }

    /**
     * @return A list of the options provided by the {@link HeapMonitor}.
     * @since 19.0
     */
    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new HeapMonitorInstrumentOptionDescriptors();
    }

    /**
     * Called when the Instrument is to be disposed.
     *
     * @param env environment information for the instrument
     * @since 19.0
     */
    @Override
    protected void onDispose(TruffleInstrument.Env env) {
        monitor.close();
    }

    // @formatter:off
    @Option(name = "",
            help = "Start the heap allocation monitor with the application. This produces no output but improves the precision of the data provided to third party tools.",
            category = OptionCategory.USER) static final OptionKey<Boolean> ENABLED = new OptionKey<>(false);
    // @formatter:on
}
