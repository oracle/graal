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
 * Contributed by Oleg Mazurov, June 2010
 */


import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class FannkuchRedux {

    private static final int NCHUNKS = 150;
    @Param("9")
    private static int fannkuchReduxN;
    private static int CHUNKSZ;
    private static int NTASKS;
    private static int[] Fact;
    private static int[] maxFlips;
    private static int[] chkSums;
    private static AtomicInteger taskId;

    @Benchmark
    public static void bench(Blackhole blackhole) {
        if (fannkuchReduxN <= 1 || fannkuchReduxN > 12) {         // 13! won't fit into int
            return;
        }

        Fact = new int[fannkuchReduxN + 1];
        Fact[0] = 1;
        for (int i = 1; i < Fact.length; ++i) {
            Fact[i] = Fact[i - 1] * i;
        }

        CHUNKSZ = (Fact[fannkuchReduxN] + NCHUNKS - 1) / NCHUNKS;
        NTASKS = (Fact[fannkuchReduxN] + CHUNKSZ - 1) / CHUNKSZ;
        maxFlips = new int[NTASKS];
        chkSums = new int[NTASKS];
        taskId = new AtomicInteger(0);

        int nthreads = Runtime.getRuntime().availableProcessors();
        Thread[] threads = new Thread[nthreads];
        for (int i = 0; i < nthreads; ++i) {
            threads[i] = new Thread(new FannkuchReduxRunnable());
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }

        int res = 0;
        for (int v : maxFlips) {
            res = Math.max(res, v);
        }
        int chk = 0;
        for (int v : chkSums) {
            chk += v;
        }

        blackhole.consume(chk + "\nPfannkuchen(" + fannkuchReduxN + ") = " + res);
    }

    public static class FannkuchReduxRunnable implements Runnable {
        int[] p;
        int[] pp;
        int[] count;

        void firstPermutation(int idx) {
            for (int i = 0; i < p.length; ++i) {
                p[i] = i;
            }

            for (int i = count.length - 1; i > 0; --i) {
                int d = idx / Fact[i];
                count[i] = d;
                idx = idx % Fact[i];

                System.arraycopy(p, 0, pp, 0, i + 1);
                for (int j = 0; j <= i; ++j) {
                    p[j] = j + d <= i ? pp[j + d] : pp[j + d - i - 1];
                }
            }
        }

        boolean nextPermutation() {
            int first = p[1];
            p[1] = p[0];
            p[0] = first;

            int i = 1;
            while (++count[i] > i) {
                count[i++] = 0;
                int next = p[0] = p[1];
                for (int j = 1; j < i; ++j) {
                    p[j] = p[j + 1];
                }
                p[i] = first;
                first = next;
            }
            return true;
        }

        int countFlips() {
            int flips = 1;
            int first = p[0];
            if (p[first] != 0) {
                System.arraycopy(p, 0, pp, 0, pp.length);
                do {
                    ++flips;
                    for (int lo = 1, hi = first - 1; lo < hi; ++lo, --hi) {
                        int t = pp[lo];
                        pp[lo] = pp[hi];
                        pp[hi] = t;
                    }
                    int t = pp[first];
                    pp[first] = first;
                    first = t;
                } while (pp[first] != 0);
            }
            return flips;
        }

        void runTask(int task) {
            int idxMin = task * CHUNKSZ;
            int idxMax = Math.min(Fact[fannkuchReduxN], idxMin + CHUNKSZ);

            firstPermutation(idxMin);

            int maxflips = 1;
            int chksum = 0;
            for (int i = idxMin; ; ) {

                if (p[0] != 0) {
                    int flips = countFlips();
                    maxflips = Math.max(maxflips, flips);
                    chksum += i % 2 == 0 ? flips : -flips;
                }

                if (++i == idxMax) {
                    break;
                }

                nextPermutation();
            }
            maxFlips[task] = maxflips;
            chkSums[task] = chksum;
        }

        public void run() {
            p = new int[fannkuchReduxN];
            pp = new int[fannkuchReduxN];
            count = new int[fannkuchReduxN];

            int task;
            while ((task = taskId.getAndIncrement()) < NTASKS) {
                runTask(task);
            }
        }
    }
}
