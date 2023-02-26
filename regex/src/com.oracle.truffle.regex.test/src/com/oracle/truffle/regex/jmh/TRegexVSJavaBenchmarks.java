/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.jmh;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import com.oracle.truffle.regex.tregex.test.TRegexTestDummyLanguage;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class TRegexVSJavaBenchmarks extends BenchmarkBase {

    private static final class ParameterSet {
        private final String name;
        private final String regex;
        private final String flags;
        private final String input;

        private ParameterSet(String name, String regex, String flags, String input) {
            this.name = name;
            this.regex = regex;
            this.flags = flags;
            this.input = input;
        }
    }

    // Checkstyle: stop
    private static final String names = "NoahLiamJacobMasonWilliamEthanMichaelAlexanderJaydenDanielElijahAidenJamesBenjaminMatthewJacksonLoganDavidAnthonyJosephJoshuaAndrewLucasGabrielSamuelChristopherJohnDylanIsaacRyanNathanCarterCalebLukeChristianHunterHenryOwenLandonJackWyattJonathanEliIsaiahSebastianJaxonBraydenGavinLeviAaronOliverJordanNicholasEvanConnorCharlesJeremiahCameronAdrianThomasRobertTylerColtonAustinJaceAngelDominicJosiahBrandonAydenKevinZacharyParkerBlakeJoseChaseGraysonJasonIanBentleyAdamXavierCooperJustinNolanHudsonEastonJaseCarsonNathanielJaxsonKaydenBrodyLincolnLuisTristanJulianDamianCamdenJuan";
    // Checkstyle: resume

    private static final Map<String, ParameterSet> benchmarks = createMap(new ParameterSet[]{
                    new ParameterSet("ignoreCase", "Julian", "i", names),
                    new ParameterSet("URL", "(((\\w+):\\/\\/)([^\\/:]*)(:(\\d+))?)?([^#?]*)(\\?([^#]*))?(#(.*))?", "", "https://lafo.ssw.uni-linz.ac.at/?computer=15"),
    });

    private static Map<String, ParameterSet> createMap(ParameterSet[] parameterSets) {
        HashMap<String, ParameterSet> ret = new HashMap<>();
        for (ParameterSet p : parameterSets) {
            ret.put(p.name, p);
        }
        return ret;
    }

    @State(Scope.Benchmark)
    public static class BenchState {

        @Param({"ignoreCase", "URL"}) String benchName;
        Context context;
        Pattern javaPattern;
        Value tregexPattern;
        String input;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            context = Context.newBuilder(TRegexTestDummyLanguage.ID).build();
            context.enter();
            ParameterSet p = benchmarks.get(benchName);
            javaPattern = Pattern.compile(p.regex, toJavaFlags(p.flags));
            tregexPattern = context.eval(TRegexTestDummyLanguage.ID, '/' + p.regex + '/' + p.flags);
            input = p.input;
        }

        private int toJavaFlags(String flags) {
            int javaFlags = 0;
            for (int i = 0; i < flags.length(); i++) {
                javaFlags |= toJavaFlag(flags.charAt(i));
            }
            return javaFlags;
        }

        private static int toJavaFlag(char c) {
            switch (c) {
                case 'i':
                    return Pattern.CASE_INSENSITIVE;
                case 'm':
                    return Pattern.MULTILINE;
                default:
                    throw new UnsupportedOperationException("unknown flag " + c);
            }
        }

        @TearDown
        public void tearDown() {
            context.leave();
            context.close();
        }
    }

    @Benchmark
    public boolean javaPattern(BenchState state) {
        return state.javaPattern.matcher(state.input).find();
    }

    @Benchmark
    public boolean tregex(BenchState state) {
        return state.tregexPattern.invokeMember("exec", state.input, 0).getMember("isMatch").asBoolean();
    }
}
