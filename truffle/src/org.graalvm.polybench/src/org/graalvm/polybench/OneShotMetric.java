/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.util.Optional;

/**
 * OneShotMetric indicates the time required to run a program once.
 *
 * To reduce variance, the program should be run multiple times, each in a different fork.
 */
final class OneShotMetric extends Metric {
    long startTime;
    long endTime;

    @Override
    public String unit() {
        return "ms";
    }

    @Override
    public String name() {
        return "one-shot time";
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        startTime = System.nanoTime();
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        endTime = System.nanoTime();
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return reportAfterAll();
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return Optional.of((endTime - startTime) / 1_000_000.0);
    }

    @Override
    public void validateConfig(Config config, Map<String, String> polyglotOptions) {
        if (config.iterations != 1) {
            throw new IllegalStateException("The One-Shot Metric may only allow `-i 1`.");
        }

        if (config.warmupIterations != 0) {
            throw new IllegalStateException("The One-Shot Metric may only allow `-w 0`.");
        }
    }

    @Override
    public void reset() {
        startTime = 0L;
        endTime = 0L;
    }
}
