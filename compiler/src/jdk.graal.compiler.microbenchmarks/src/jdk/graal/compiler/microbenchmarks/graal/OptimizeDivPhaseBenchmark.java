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

import org.openjdk.jmh.annotations.Benchmark;

import jdk.graal.compiler.microbenchmarks.graal.util.GraalState;
import jdk.graal.compiler.microbenchmarks.graal.util.GraphState;
import jdk.graal.compiler.microbenchmarks.graal.util.MethodSpec;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.OptimizeDivPhase;

/**
 * Benchmarks the transformation of unsigned division and remainder by constant values, including
 * both branches of the magic-number algorithm.
 */
public class OptimizeDivPhaseBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = OptimizeDivPhaseBenchmark.class, name = "unsignedIntSnippet")
    public static class UnsignedInt extends GraphState {
    }

    @MethodSpec(declaringClass = OptimizeDivPhaseBenchmark.class, name = "unsignedIntAddSnippet")
    public static class UnsignedIntAdd extends GraphState {
    }

    @MethodSpec(declaringClass = OptimizeDivPhaseBenchmark.class, name = "unsignedLongSnippet")
    public static class UnsignedLong extends GraphState {
    }

    @MethodSpec(declaringClass = OptimizeDivPhaseBenchmark.class, name = "unsignedLongAddSnippet")
    public static class UnsignedLongAdd extends GraphState {
    }

    @SuppressWarnings("unused")
    public static int unsignedIntSnippet(int value) {
        return Integer.divideUnsigned(value, 10) + Integer.remainderUnsigned(value, 13);
    }

    // Divisor 7 exercises the magic-number algorithm's add-indicator path.
    @SuppressWarnings("unused")
    public static int unsignedIntAddSnippet(int value) {
        return Integer.divideUnsigned(value, 7) + Integer.remainderUnsigned(value, 7);
    }

    @SuppressWarnings("unused")
    public static long unsignedLongSnippet(long value) {
        return Long.divideUnsigned(value, 10) + Long.remainderUnsigned(value, 13);
    }

    // Divisor 7 exercises the add-indicator path with a 64-bit dividend.
    @SuppressWarnings("unused")
    public static long unsignedLongAddSnippet(long value) {
        return Long.divideUnsigned(value, 7) + Long.remainderUnsigned(value, 7);
    }

    @Benchmark
    public void unsignedInt(UnsignedInt s, GraalState g) {
        new OptimizeDivPhase(CanonicalizerPhase.create()).apply(s.graph, g.providers);
    }

    @Benchmark
    public void unsignedIntAdd(UnsignedIntAdd s, GraalState g) {
        new OptimizeDivPhase(CanonicalizerPhase.create()).apply(s.graph, g.providers);
    }

    @Benchmark
    public void unsignedLong(UnsignedLong s, GraalState g) {
        new OptimizeDivPhase(CanonicalizerPhase.create()).apply(s.graph, g.providers);
    }

    @Benchmark
    public void unsignedLongAdd(UnsignedLongAdd s, GraalState g) {
        new OptimizeDivPhase(CanonicalizerPhase.create()).apply(s.graph, g.providers);
    }
}
