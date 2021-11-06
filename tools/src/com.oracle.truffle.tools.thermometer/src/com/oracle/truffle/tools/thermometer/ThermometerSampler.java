/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ThermometerSampler {

    private final AtomicBoolean compilationFlag = new AtomicBoolean();
    private final AtomicLong iterationsCounter = new AtomicLong();

    private long samples;
    private long compiled;

    private long iterationsLastReadTime = 0;

    // Thread-safe

    public void setCompilationFlag(boolean inCompiledCode) {
        compilationFlag.set(inCompiledCode);
    }

    // These two methods are effectively synchronized, by being run on a shared ScheduledThreadPoolExecutor with a single thread

    public void sampleCompilation() {
        samples++;
        compiled += compilationFlag.get() ? 1 : 0;
    }

    public double readCompilation() {
        final double reading = (float) compiled / (float) samples;
        samples = 0;
        compiled = 0;
        return reading;
    }

    // Thread-safe

    public void iterationPoint() {
        iterationsCounter.getAndIncrement();
    }

    // Only called from the reporting thread

    public double readIterationsPerSecond(long elapsedTime) {
        final long iterations = iterationsCounter.getAndSet(0);

        if (iterations > 0) {
            final double iterationsPerSecond = iterations / ((elapsedTime - iterationsLastReadTime) / 1e9);
            iterationsLastReadTime = elapsedTime;
            return iterationsPerSecond;
        } else {
            return 0;
        }
    }

}
