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
package micro.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class SignalCorrelationBenchmark {
    /** Approximate {@code sin(i / 1000)}. */
    public static double sin(int i) {
        double x = i / 1000.0;
        double term3 = (x * x * x) / (3 * 2 * 1);
        double term5 = (x * x * x * x * x) / (5 * 4 * 3 * 2 * 1);
        return x - term3 + term5;
    }

    public static double[] sinTable(int n) {
        double[] table = new double[n];
        for (int i = 0; i < n; i++) {
            table[i] = sin(i);
        }
        return table;
    }

    /** Approximate {@code cos(i / 1000)}. */
    public static double cos(int i) {
        double x = i / 1000.0;
        double term2 = (x * x) / (2 * 1);
        double term4 = (x * x * x * x) / (4 * 3 * 2 * 1);
        return 1 - term2 + term4;
    }

    public static double[] cosTable(int n) {
        double[] table = new double[n];
        for (int i = 0; i < n; i++) {
            table[i] = cos(i);
        }
        return table;
    }

    public static double correlate(double[] a, double[] b) {
        double correlation = 0.0;
        for (int i = 0; i < a.length; i++) {
            correlation += a[i] * b[i];
        }
        return correlation;
    }

    public static double correlateUnrolled(double[] a, double[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException();
        }
        double[] partial = new double[4];
        for (int i = 0; i + 3 < a.length; i += 4) {
            partial[0] += a[i + 0] * b[i + 0];
            partial[1] += a[i + 1] * b[i + 1];
            partial[2] += a[i + 2] * b[i + 2];
            partial[3] += a[i + 3] * b[i + 3];
        }
        for (int i = a.length & ~0b11; i < a.length; ++i) {
            partial[0] += a[i] * b[i];
        }
        double correlation = partial[0] + partial[1] + partial[2] + partial[3];
        return correlation;
    }

    @Benchmark
    public void correlateBench(Blackhole bh) {
        int n = 10_000_000;
        for (int j = 0; j < 10; j++) {
            double[] sin = sinTable(n);
            double[] cos = cosTable(n);

            double sinSin = correlate(sin, sin);
            double sinCos = correlate(sin, cos);
            double cosSin = correlate(cos, sin);
            double cosCos = correlate(cos, cos);

            double sinSinNormalized = sinSin / sinSin;
            double sinCosNormalized = sinCos / Math.sqrt(sinSin * cosCos);
            double cosSinNormalized = cosSin / Math.sqrt(cosCos * sinSin);
            double cosCosNormalized = cosCos / cosCos;

            bh.consume(sinSinNormalized);
            bh.consume(sinCosNormalized);
            bh.consume(cosSinNormalized);
            bh.consume(cosCosNormalized);
        }
    }

    @Benchmark
    public void unrolledCorrelateBench(Blackhole bh) {
        int n = 10_000_000;
        for (int j = 0; j < 10; j++) {
            double[] sin = sinTable(n);
            double[] cos = cosTable(n);

            double sinSin = correlateUnrolled(sin, sin);
            double sinCos = correlateUnrolled(sin, cos);
            double cosSin = correlateUnrolled(cos, sin);
            double cosCos = correlateUnrolled(cos, cos);

            double sinSinNormalized = sinSin / sinSin;
            double sinCosNormalized = sinCos / Math.sqrt(sinSin * cosCos);
            double cosSinNormalized = cosSin / Math.sqrt(cosCos * sinSin);
            double cosCosNormalized = cosCos / cosCos;

            bh.consume(sinSinNormalized);
            bh.consume(sinCosNormalized);
            bh.consume(cosSinNormalized);
            bh.consume(cosCosNormalized);
        }
    }
}
