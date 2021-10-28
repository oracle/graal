/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.polyglot.Value;

import java.util.Optional;

class PeakTimeMetric implements Metric {
    long startTime;
    long endTime;
    long totalTime;
    int totalIterations;

    int batchSize;
    Unit unit;

    private enum Unit {
        ns(1.0),
        us(1_000.0),
        ms(1_000_000.0),
        s(1_000_000_000.0);

        private final double factor;

        Unit(double factor) {
            this.factor = factor;
        }
    }

    PeakTimeMetric() {
        this.totalTime = 0L;
        this.batchSize = 1;
        this.unit = Unit.ms;
    }

    @Override
    public void parseBenchSpecificOptions(Value runner) {
        if (runner.hasMember("batchSize")) {
            Value batchSizeMember = runner.getMember("batchSize");
            this.batchSize = batchSizeMember.canExecute() ? batchSizeMember.execute().asInt() : batchSizeMember.asInt();
        }
        if (runner.hasMember("unit")) {
            Value unitMember = runner.getMember("unit");
            String u = unitMember.canExecute() ? unitMember.execute().asString() : unitMember.asString();
            this.unit = Unit.valueOf(u);
        }
    }

    @Override
    public void beforeIteration(boolean warmup, int iteration, Config config) {
        startTime = System.nanoTime();
    }

    @Override
    public void afterIteration(boolean warmup, int iteration, Config config) {
        endTime = System.nanoTime();

        totalTime += endTime - startTime;
        totalIterations++;
    }

    @Override
    public void reset() {
        startTime = 0L;
        endTime = 0L;
        totalTime = 0L;
        totalIterations = 0;
    }

    @Override
    public Optional<Double> reportAfterIteration(Config config) {
        return Optional.of((endTime - startTime) / (unit.factor * batchSize));
    }

    @Override
    public Optional<Double> reportAfterAll() {
        return Optional.of(totalTime / (totalIterations * unit.factor * batchSize));
    }

    @Override
    public String unit() {
        return unit.name();
    }

    @Override
    public String name() {
        return "peak time";
    }
}
