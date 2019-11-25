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

import com.oracle.truffle.api.instrumentation.CompilationState;

public class ThermometerState {

    private final long elapsedTime;
    private final double sampleReading;
    private final double iterationsPerSecond;
    private final long loadedSource;
    private final CompilationState compilationState;

    public ThermometerState(long elapsedTime, double sampleReading, double iterationsPerSecond, long loadedSource, CompilationState compilationState) {
        this.elapsedTime = elapsedTime;
        this.sampleReading = sampleReading;
        this.iterationsPerSecond = iterationsPerSecond;
        this.loadedSource = loadedSource;
        this.compilationState = compilationState;
    }

    public String format(ThermometerState reference, boolean reportIPS) {
        final String ipsComponent;

        if (!reportIPS) {
            ipsComponent = "";
        } else {
            final String suffix;

            final double reportedIterationsPerSecond;

            if (iterationsPerSecond >= 1e5) {
                reportedIterationsPerSecond = iterationsPerSecond / 1e6;
                suffix = "M";
            } else if (iterationsPerSecond >= 1e2) {
                reportedIterationsPerSecond = iterationsPerSecond / 1e3;
                suffix = "K";
            } else {
                reportedIterationsPerSecond = iterationsPerSecond;
                suffix = " ";
            }

            ipsComponent = String.format("   %6.3f %s i/s", reportedIterationsPerSecond, suffix);
        }

        return String.format("%6.2fs  %s  %3.0f°%s   %5.2f MB  %2d ▶ %2d ▶ %2d  ( %2d, %2d )  %2d ▼",
                elapsedTime / 1e9,
                indicator(reference),
                sampleReading * 100,
                ipsComponent,
                loadedSource / 1024.0 / 1024.0,
                compilationState.getQueued(),
                compilationState.getRunning(),
                compilationState.getFinished(),
                compilationState.getFailed(),
                compilationState.getDequeued(),
                compilationState.getDeoptimizations());
    }

    private String indicator(ThermometerState reference) {
        if (reference != null && compilationState.getFailed() != reference.compilationState.getFailed()) {
            return "😡";
        } else if (reference != null && compilationState.getDeoptimizations() != reference.compilationState.getDeoptimizations()) {
            return "🤮";
        } else if (sampleReading < 0.5) {
            return "🥶";
        } else if (sampleReading < 0.9 || (reference != null && reference.loadedSource < loadedSource)) {
            return "🤔";
        } else {
            return "😊";
        }
    }

}
