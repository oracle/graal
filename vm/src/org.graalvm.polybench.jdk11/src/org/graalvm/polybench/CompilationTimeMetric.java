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
package org.graalvm.polybench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;

final class CompilationTimeMetric implements Metric {

    private static final Logger LOG = Logger.getLogger(CompilationTimeMetric.class.getName());
    private static final String TRUFFLE_COMPILATION_EVENT = "org.graalvm.compiler.truffle.Compilation";

    private Recording recording;
    private long cumulativeTime = 0L;

    CompilationTimeMetric() {
        if (!FlightRecorder.isAvailable()) {
            throw new IllegalStateException("The VM does not support Java Flight Recorder.");
        }
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        // Reset the cumulative time. It's recomputed in the afterIteration from the JFR snapshot.
        cumulativeTime = 0L;
        if (recording == null) {
            // First iteration, create a new Recording used for all iterations until reset.
            recording = new Recording();
            recording.enable(TRUFFLE_COMPILATION_EVENT);
            recording.setDumpOnExit(false);
            recording.start();
        }
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        if (recording == null) {
            throw new IllegalStateException("Missing JFR recording.");
        }
        if (cumulativeTime != 0L) {
            throw new IllegalStateException("Missing a call to beforeIteration().");
        }
        try {
            Path file = Files.createTempFile("recording", ".jfr");
            try {
                // Copy a JFR events snapshot into a temp file.
                recording.dump(file);
                // Calculate a cumulative Truffle compilation time from all events in the snapshot.
                cumulativeTime = processRecordings(file);
            } finally {
                Files.delete(file);
            }
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "Cannot write recording", ioe);
        }
    }

    @Override
    public void reset() {
        cumulativeTime = 0L;
        // Stop and dispose JFR recording.
        recording.stop();
        recording.close();
        recording = null;
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return Optional.of(cumulativeTime * 1.0);
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return Optional.of(cumulativeTime * 1.0);
    }

    @Override
    public String unit() {
        return "ms";
    }

    @Override
    public String name() {
        return "compilation time";
    }

    private static long processRecordings(Path jfrFile) throws IOException {
        return RecordingFile.readAllEvents(jfrFile).stream()
                        .filter((event) -> {
                            return TRUFFLE_COMPILATION_EVENT.equals(event.getEventType().getName());
                        })
                        .map((event) -> event.getDuration())
                        .collect(Collectors.reducing(Duration.ofNanos(0), (a, b) -> a.plus(b)))
                        .toMillis();
    }
}
