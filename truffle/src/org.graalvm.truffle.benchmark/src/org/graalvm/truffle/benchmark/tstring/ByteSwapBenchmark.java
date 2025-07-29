/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;
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
public class ByteSwapBenchmark extends TStringBenchmarkBase {

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"65"}) int length;
        // Checkstyle: stop
        String strAscii = "NoahLiamJacobMasonWilliamEthanMichaelAlexanderJaydenDanielElijahAidenJamesBenjaminMatthewJacksonLoganDavidAnthonyJosephJoshuaAndrewLucasGabrielSamuelChristopherJohnDylanIsaacRyanNathanCarterCalebLukeChristianHunterHenryOwenLandonJackWyattJonathanEliIsaiahSebastianJaxonBraydenGavinLeviAaronOliverJordanNicholasEvanConnorCharlesJeremiahCameronAdrianThomasRobertTylerColtonAustinJaceAngelDominicJosiahBrandonAydenKevinZacharyParkerBlakeJoseChaseGraysonJasonIanBentleyAdamXavierCooperJustinNolanHudsonEastonJaseCarsonNathanielJaxsonKaydenBrodyLincolnLuisTristanJulianDamianCamdenJuan";
        String strBmp = '\u2020' + strAscii;
        // Checkstyle: resume
        byte[] asciiUTF16 = toStride(strAscii, 1);
        byte[] asciiUTF32 = toStride(strAscii, 2);
        byte[] bmpUTF32 = toStride(strBmp, 2);

        Context context;
        Value utf16;
        Value utf32;
        Value utf16Switch;
        Value utf32Switch;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            context = Context.newBuilder(TStringBenchDummyLanguage.ID).build();
            context.enter();
            utf16 = context.parse(TStringBenchDummyLanguage.ID, "fromByteArrayUTF16FE");
            utf32 = context.parse(TStringBenchDummyLanguage.ID, "fromByteArrayUTF32FE");
            utf16Switch = context.parse(TStringBenchDummyLanguage.ID, "fromByteArrayUTF16FESwitch");
            utf32Switch = context.parse(TStringBenchDummyLanguage.ID, "fromByteArrayUTF32FESwitch");
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    public Value fromByteArrayUTF32(BenchState state) {
        return state.utf32.execute(state.asciiUTF32, state.length * 4);
    }

    @Benchmark
    public Value fromByteArrayUTF32BMP(BenchState state) {
        return state.utf32.execute(state.bmpUTF32, state.length * 4);
    }

    @Benchmark
    public Value fromByteArrayUTF16(BenchState state) {
        return state.utf16.execute(state.asciiUTF16, state.length * 2);
    }

    @Benchmark
    public Value fromByteArrayUTF32Switch(BenchState state) {
        return state.utf32Switch.execute(state.asciiUTF32, state.length * 4);
    }

    @Benchmark
    public Value fromByteArrayUTF32SwitchBMP(BenchState state) {
        return state.utf32Switch.execute(state.bmpUTF32, state.length * 4);
    }

    @Benchmark
    public Value fromByteArrayUTF16Switch(BenchState state) {
        return state.utf16Switch.execute(state.asciiUTF16, state.length * 2);
    }

    private static byte[] toStride(String str, int stride) {
        byte[] ret = new byte[str.length() << stride];
        for (int i = 0; i < str.length(); i++) {
            writeValue(ret, stride, i, str.charAt(i));
        }
        return ret;
    }

    private static void writeValue(byte[] array, int stride, int index, int value) {
        writeValue(array, stride, index, value, ByteOrder.nativeOrder());
    }

    private static void writeValue(byte[] array, int stride, int index, int value, ByteOrder byteOrder) {
        int i = index << stride;
        if (stride == 0) {
            array[i] = (byte) value;
            return;
        }
        if (byteOrder.equals(ByteOrder.BIG_ENDIAN)) {
            if (stride == 1) {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
            } else {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
                array[i + 2] = (byte) (value >> 16);
                array[i + 3] = (byte) (value >> 24);
            }
        } else {
            if (stride == 1) {
                array[i] = (byte) (value >> 8);
                array[i + 1] = (byte) value;
            } else {
                array[i] = (byte) (value >> 24);
                array[i + 1] = (byte) (value >> 16);
                array[i + 2] = (byte) (value >> 8);
                array[i + 3] = (byte) value;
            }
        }
    }
}
