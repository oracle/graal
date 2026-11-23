/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.microbenchmarks.graal;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;

import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.microbenchmarks.graal.util.ReadEliminationState;
import jdk.graal.compiler.phases.schedule.SchedulePhase;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class ReadEliminationBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class StringEquals extends ReadEliminationState.Early {
    }

    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class Intersection extends ReadEliminationState.Early {
    }

    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class ScheduleEarliestIterative extends ReadEliminationState.Early {
    }

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class LowTierStringEquals extends ReadEliminationState.LowTier {
    }

    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class LowTierIntersection extends ReadEliminationState.LowTier {
    }

    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class LowTierScheduleEarliestIterative extends ReadEliminationState.LowTier {
    }

    @Benchmark
    public void stringEquals(StringEquals s) {
        s.phase.apply(s.graph, s.context);
    }

    @Benchmark
    public void intersection(Intersection s) {
        s.phase.apply(s.graph, s.context);
    }

    @Benchmark
    public void scheduleEarliestIterative(ScheduleEarliestIterative s) {
        s.phase.apply(s.graph, s.context);
    }

    @Benchmark
    public void lowTierStringEquals(LowTierStringEquals s) {
        s.phase.apply(s.graph, s.context);
    }

    @Benchmark
    public void lowTierIntersection(LowTierIntersection s) {
        s.phase.apply(s.graph, s.context);
    }

    @Benchmark
    public void lowTierScheduleEarliestIterative(LowTierScheduleEarliestIterative s) {
        s.phase.apply(s.graph, s.context);
    }
}
