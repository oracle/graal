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
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class CompilationTimeMetric implements Metric {

    enum Mode {
        COMPILATION(null),
        PARTIAL_EVALUATION("peTime");

        private final String fieldName;

        Mode(String fieldName) {
            this.fieldName = fieldName;
        }

        String getFieldName() {
            return fieldName;
        }
    }

    private static final Logger LOG = Logger.getLogger(CompilationTimeMetric.class.getName());
    private static final String TRUFFLE_COMPILATION_EVENT = "org.graalvm.compiler.truffle.Compilation";

    private final Mode mode;
    private Object recording;
    private Object snapshot;

    CompilationTimeMetric(Mode mode) {
        if (!JFRSupport.isAvailable()) {
            throw new IllegalStateException("The VM does not support Java Flight Recorder.");
        }
        this.mode = mode;
    }

    @Override
    public String unit() {
        return "ms";
    }

    @Override
    public String name() {
        return "compilation time";
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        if (recording == null) {
            // First iteration, create a new Recording used for all iterations until reset.
            recording = JFRSupport.startRecording(TRUFFLE_COMPILATION_EVENT);
        }
        JFRSupport.disposeRecording(snapshot, false);
        snapshot = null;
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        if (recording == null) {
            throw new IllegalStateException("Missing JFR recording.");
        }
        if (snapshot != null) {
            throw new IllegalStateException("Existing JFR snapshot.");
        }
        snapshot = JFRSupport.snapshotRecording(recording);
    }

    @Override
    public void reset() {
        // Stop and dispose JFR recording.
        JFRSupport.disposeRecording(recording, true);
        recording = null;
        JFRSupport.disposeRecording(snapshot, false);
        snapshot = null;
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return computeCumulativeTime();
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return computeCumulativeTime();
    }

    private Optional<Double> computeCumulativeTime() {
        if (snapshot == null) {
            throw new IllegalStateException("No snapshot.");
        }
        try {
            return Optional.of(1.0 * JFRSupport.computeCumulativeTime(snapshot, TRUFFLE_COMPILATION_EVENT, mode.getFieldName()));
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "Cannot write recording.", ioe);
            return Optional.empty();
        }
    }
}
