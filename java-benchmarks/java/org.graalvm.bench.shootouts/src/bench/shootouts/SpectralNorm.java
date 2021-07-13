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
/*
 * Copyright (c) 2004-2008 Brent Fulgham, 2005-2020 Isaac Gouy
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name "The Computer Language Benchmarks Game" nor the name "The Benchmarks Game" nor
 * the name "The Computer Language Shootout Benchmarks" nor the names of its contributors may be used
 * to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bench.shootouts;
/*
 * The Computer Language Benchmarks Game
 * http://benchmarksgame.alioth.debian.org/
 *
 * Based on C# entry by Isaac Gouy
 * contributed by Jarkko Miettinen
 * Parallel by The Anh Tran
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.CyclicBarrier;

@State(Scope.Benchmark)
public class SpectralNorm {

    @Param("1000")
    static int spectralNormN;

    @Benchmark
    public static double bench() {
        return spectralnormGame(spectralNormN);
    }

    private static final double spectralnormGame(int n) {
        // create unit vector
        double[] u = new double[n];
        double[] v = new double[n];
        double[] tmp = new double[n];

        for (int i = 0; i < n; i++)
            u[i] = 1.0;

        // get available processor, then set up syn object
        int nthread = Runtime.getRuntime().availableProcessors();
        Approximate.barrier = new CyclicBarrier(nthread);

        int chunk = n / nthread;
        Approximate[] ap = new Approximate[nthread];

        for (int i = 0; i < nthread; i++) {
            int r1 = i * chunk;
            int r2 = (i < (nthread - 1)) ? r1 + chunk : n;

            ap[i] = new Approximate(u, v, tmp, r1, r2);
        }

        double vBv = 0, vv = 0;
        for (int i = 0; i < nthread; i++) {
            try {
                ap[i].join();

                vBv += ap[i].m_vBv;
                vv += ap[i].m_vv;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Math.sqrt(vBv / vv);
    }

    private static class Approximate extends Thread {
        private static CyclicBarrier barrier;

        private double[] _u;
        private double[] _v;
        private double[] _tmp;

        private int range_begin, range_end;
        private double m_vBv = 0, m_vv = 0;

        public Approximate(double[] u, double[] v, double[] tmp, int rbegin, int rend) {
            super();

            _u = u;
            _v = v;
            _tmp = tmp;

            range_begin = rbegin;
            range_end = rend;

            start();
        }

        /* return element i,j of infinite matrix A */
        private final static double eval_A(int i, int j) {
            int div = (((i + j) * (i + j + 1) >>> 1) + i + 1);
            return 1.0 / div;
        }

        @Override
        public void run() {
            // 20 steps of the power method
            for (int i = 0; i < 10; i++) {
                MultiplyAtAv(_u, _tmp, _v);
                MultiplyAtAv(_v, _tmp, _u);
            }

            for (int i = range_begin; i < range_end; i++) {
                m_vBv += _u[i] * _v[i];
                m_vv += _v[i] * _v[i];
            }
        }

        /* multiply vector v by matrix A, each thread evaluate its range only */
        private final void MultiplyAv(final double[] v, double[] Av) {
            for (int i = range_begin; i < range_end; i++) {
                double sum = 0;
                for (int j = 0; j < v.length; j++)
                    sum += eval_A(i, j) * v[j];

                Av[i] = sum;
            }
        }

        /* multiply vector v by matrix A transposed */
        private final void MultiplyAtv(final double[] v, double[] Atv) {
            for (int i = range_begin; i < range_end; i++) {
                double sum = 0;
                for (int j = 0; j < v.length; j++)
                    sum += eval_A(j, i) * v[j];

                Atv[i] = sum;
            }
        }

        /* multiply vector v by matrix A and then by matrix A transposed */
        private final void MultiplyAtAv(final double[] v, double[] tmp, double[] AtAv) {
            try {
                MultiplyAv(v, tmp);
                // all thread must syn at completion
                barrier.await();
                MultiplyAtv(tmp, AtAv);
                // all thread must syn at completion
                barrier.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
