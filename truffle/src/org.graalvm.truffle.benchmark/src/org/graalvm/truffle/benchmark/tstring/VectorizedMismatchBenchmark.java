/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.truffle.benchmark.tstring;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class VectorizedMismatchBenchmark extends TStringBenchmarkBase {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"64"}) int length;
        // Checkstyle: stop
        String str = "NoahLiamJacobMasonWilliamEthanMichaelAlexanderJaydenDanielElijahAidenJamesBenjaminMatthewJacksonLoganDavidAnthonyJosephJoshuaAndrewLucasGabrielSamuelChristopherJohnDylanIsaacRyanNathanCarterCalebLukeChristianHunterHenryOwenLandonJackWyattJonathanEliIsaiahSebastianJaxonBraydenGavinLeviAaronOliverJordanNicholasEvanConnorCharlesJeremiahCameronAdrianThomasRobertTylerColtonAustinJaceAngelDominicJosiahBrandonAydenKevinZacharyParkerBlakeJoseChaseGraysonJasonIanBentleyAdamXavierCooperJustinNolanHudsonEastonJaseCarsonNathanielJaxsonKaydenBrodyLincolnLuisTristanJulianDamianCamdenJuan";
        // Checkstyle: resume
        byte[] a = str.getBytes(StandardCharsets.UTF_8);
        byte[] b;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            b = createDiff(a, length);
        }
    }

    private static byte[] createDiff(byte[] a, int pos) {
        byte[] ret = Arrays.copyOf(a, a.length);
        ret[pos] = '!';
        return ret;
    }

    @Benchmark
    public int clamped(BenchState state) {
        return Arrays.mismatch(state.a, 0, state.length + 1, state.b, 0, state.length + 1);
    }

    @Benchmark
    public int full(BenchState state) {
        return Arrays.mismatch(state.a, state.b);
    }
}
