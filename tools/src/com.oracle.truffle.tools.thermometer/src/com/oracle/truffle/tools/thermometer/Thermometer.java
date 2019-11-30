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
package com.oracle.truffle.tools.thermometer;

import static com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

import com.oracle.truffle.api.instrumentation.CompilationState;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class Thermometer implements AutoCloseable {

    private final Env env;

    private final AtomicLong loadedSource = new AtomicLong();
    private final ThermometerSampler sampler = new ThermometerSampler();
    private final AtomicReference<ThermometerState> previousState = new AtomicReference<>();

    private boolean running;
    private long startTime;
    private PrintStream logStream;
    private ScheduledThreadPoolExecutor executor;

    private boolean hasIterationLocation;

    public Thermometer(Env env) {
        this.env = env;
    }

    public synchronized void start(ThermometerConfig config) {
        if (running) {
            throw new IllegalStateException("the thermometer is already running");
        }

        running = true;
        startTime = System.nanoTime();

        // Install a node at the root of each compilation unit to set the compilation flag

        final SourceSectionFilter rootNodeFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.CompilationRootTag.class).build();
        env.getInstrumenter().attachExecutionEventFactory(rootNodeFilter, context -> new ThermometerCompilationSampleNode(sampler));

        // Install a node at the IPS sample location to count iterations

        hasIterationLocation = config.getIterationLocation() != null;

        if (hasIterationLocation) {
            final int index = config.getIterationLocation().lastIndexOf(':');

            if (index == -1) {
                env.getLogger("").log(Level.WARNING, "thermometer iteration location could not be parsed - should be file:line");
                hasIterationLocation = false;
            } else {
                final String file = config.getIterationLocation().substring(0, index);
                final int line = Integer.parseInt(config.getIterationLocation().substring(index + 1));

                final SourceSectionFilter iterationNodeFilter = SourceSectionFilter.newBuilder()
                        .tagIs(StandardTags.StatementTag.class)
                        .sourceIs((source) -> source.getName().endsWith(file))
                        .lineIs(line)
                        .build();

                env.getInstrumenter().attachExecutionEventFactory(iterationNodeFilter, context -> new ThermometerSampleIterationNode(sampler));
            }
        }

        // Create the log stream if requested

        if (config.getLogFile() != null) {
            try {
                logStream = new PrintStream(new FileOutputStream(config.getLogFile()), true);
            } catch (IOException e) {
                env.getLogger("").log(Level.WARNING, "thermometer log file could not be opened", e);
            }
        }

        // Track loaded sources

        env.getInstrumenter().attachLoadSourceListener(SourceFilter.ANY, (loadSourceEvent) -> {
            final Source source = loadSourceEvent.getSource();
            if (source.hasCharacters() || source.hasBytes()) {
                loadedSource.addAndGet(loadSourceEvent.getSource().getLength());
            }
        }, true);

        // Create a scheduler with a daemon worker thread with high priority

        executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            final Thread thread = new Thread(runnable, ThermometerInstrument.NAME);
            thread.setDaemon(true);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
        });

        // One task is to sample

        executor.scheduleAtFixedRate(sampler::sampleCompilation, 0, config.getSamplingPeriod(), TimeUnit.MILLISECONDS);

        // The other task is to report in the log

        executor.scheduleAtFixedRate(this::report, 0, config.getReportingPeriod(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void close() {
        if (running) {
            end();
        }
    }

    private void report() {
        final long time = System.nanoTime();
        final long elapsedTime = time - startTime;
        final CompilationState compilationState = env.getInstrumenter().sampleCompilationState();
        if (compilationState != null) {
            final ThermometerState state = new ThermometerState(elapsedTime, sampler.readCompilation(), sampler.readIterationsPerSecond(elapsedTime), loadedSource.get(), compilationState);
            env.getLogger("").info(state.format(previousState.get(), hasIterationLocation));
            if (logStream != null) {
                state.writeLog(logStream, hasIterationLocation);
            }
            previousState.set(state);
        }
    }

    private synchronized void end() {
        if (!running) {
            throw new IllegalStateException("the thermometer is not running");
        }

        running = false;
        executor.shutdown();

        // Report at the end to see the final deoptimisations

        report();

        if (logStream != null) {
            logStream.close();
        }
    }

}
