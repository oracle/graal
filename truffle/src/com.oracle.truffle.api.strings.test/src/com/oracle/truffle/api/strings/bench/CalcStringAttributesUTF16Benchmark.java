/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.strings.bench;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class CalcStringAttributesUTF16Benchmark extends TStringBenchmarkBase {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"64"}) int length;
        // Checkstyle: stop
        String strAscii = "NoahLiamJacobMasonWilliamEthanMichaelAlexanderJaydenDanielElijahAidenJamesBenjaminMatthewJacksonLoganDavidAnthonyJosephJoshuaAndrewLucasGabrielSamuelChristopherJohnDylanIsaacRyanNathanCarterCalebLukeChristianHunterHenryOwenLandonJackWyattJonathanEliIsaiahSebastianJaxonBraydenGavinLeviAaronOliverJordanNicholasEvanConnorCharlesJeremiahCameronAdrianThomasRobertTylerColtonAustinJaceAngelDominicJosiahBrandonAydenKevinZacharyParkerBlakeJoseChaseGraysonJasonIanBentleyAdamXavierCooperJustinNolanHudsonEastonJaseCarsonNathanielJaxsonKaydenBrodyLincolnLuisTristanJulianDamianCamdenJuan";
        // Checkstyle: resume
        final byte[] latin1 = strAscii.getBytes(StandardCharsets.UTF_16LE);
        final byte[] bmp = ('\u2020' + strAscii).getBytes(StandardCharsets.UTF_16LE);
        final byte[] astral = new StringBuilder().appendCodePoint(Character.MAX_CODE_POINT).append(strAscii).toString().getBytes(StandardCharsets.UTF_16LE);
        final byte[] broken = ('0' + strAscii).getBytes(StandardCharsets.UTF_16LE);
        Context context;
        Value bench;

        public BenchState() {
            broken[0] = (byte) (Character.MIN_LOW_SURROGATE);
            broken[1] = (byte) (Character.MIN_LOW_SURROGATE >>> 8);
        }

        @Setup
        public void setUp() {
            context = Context.newBuilder(TStringTestDummyLanguage.ID).build();
            context.enter();
            bench = context.parse(TStringTestDummyLanguage.ID, "calcStringAttributesUTF16");
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    public Value latin1(BenchState state) {
        return state.bench.execute(state.latin1, state.length * 2);
    }

    @Benchmark
    public Value bmp(BenchState state) {
        return state.bench.execute(state.bmp, state.length * 2);
    }

    @Benchmark
    public Value astral(BenchState state) {
        return state.bench.execute(state.astral, state.length * 2);
    }

    @Benchmark
    public Value broken(BenchState state) {
        return state.bench.execute(state.broken, state.length * 2);
    }
}
