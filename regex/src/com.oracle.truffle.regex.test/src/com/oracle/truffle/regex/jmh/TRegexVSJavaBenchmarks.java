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

import com.oracle.truffle.regex.test.dummylang.TRegexTestDummyLanguage;

@OutputTimeUnit(TimeUnit.NANOSECONDS)
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
                    new ParameterSet("URL", "(((\\w+):\\/\\/)([^\\/:]*)(:(\\d+))?)?([^#?]*)(\\?([^#]*))?(#(.*))?",
                                    "", "https://lafo.ssw.uni-linz.ac.at/?computer=15"),
                    new ParameterSet("vowels", "([aeiouAEIOU]+)", "", "eeeeeeeeeeeeeeiiiiiiiiiiiiiiiiiiieeeeeeeeeeeeeeeeeeeeeeeiiiiiiiiiiiiiiieeeeeeeeeeeee"),
                    new ParameterSet("date",
                                    "((([1-3][0-9])|[1-9])\\/((1[0-2])|0?[1-9])\\/[0-9]{4})|((([1-3][0-9])|[1-9])-((1[0-2])|0?[1-9])-[0-9]{4})|((([1-3][0-9])|[1-9])\\.((1[0-2])|0?[1-9])\\.[0-9]{4})",
                                    "",
                                    "23-09-1998"),
                    new ParameterSet("ipv4",
                                    "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)", "",
                                    "192.168.0.1"),
                    new ParameterSet("ipv6_1", "([A-Fa-f0-9]{1,4}:){7}[A-Fa-f0-9]{1,4}", "",
                                    "2001:db8:3333:4444:5555:6666:7777:8888"),
                    new ParameterSet("ipv6_2",
                                    "([A-Fa-f0-9]{1,4}:){6}(([A-Fa-f0-9]{1,4}:[A-Fa-f0-9]{1,4})|(((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])))",
                                    "",
                                    "2001:db8:3333:4444:5555:6666:1.2.3.4"),
                    new ParameterSet("email",
                                    "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]{0,61}[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]{0,61}[0-9A-Za-z])?)+",
                                    "",
                                    "surname.lastname@somewhere.org"),
                    new ParameterSet("email_dfa",
                                    "([-!#-''*+/-9=?A-Z^-~]+(\\.[-!#-''*+/-9=?A-Z^-~]+)*|\"([ ]!#-[^-~ ]|(\\\\[-~ ]))+\")@[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?(\\.[0-9A-Za-z]([0-9A-Za-z-]*[0-9A-Za-z])?)+",
                                    "",
                                    "surname.lastname@somewhere.org"),
                    new ParameterSet("apache_log",
                                    "(\\S+) (\\S+) (\\S+) \\[([A-Za-z0-9_:/]+\\s[-+]\\d{4})\\] \"(\\S+)\\s?(\\S+)?\\s?(\\S+)?\" (\\d{3}|-) (\\d+|-)\\s?\"?([^\"]*)\"?\\s?\"?([^\"]*)?\"?",
                                    "",
                                    "205.169.39.63 - - [03/Nov/2022:15:28:53 +0100] \"GET / HTTP/1.1\" 200 911 \"-\" \"Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/106.0.0.0 Safari/537.36\"")
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

        // excluded by default:
        // {"vowels", "date", "ipv4", "ipv6_1", "ipv6_2", "email", "email_dfa", "apache_log"}
        @Param({"ignoreCase", "URL"}) String benchName;
        Context context;
        Pattern javaPattern;
        Value tregexBool;
        Value tregexCG;
        String input;

        public BenchState() {
        }

        @Setup
        public void setUp() {
            context = Context.newBuilder(TRegexTestDummyLanguage.ID).build();
            context.enter();
            ParameterSet p = benchmarks.get(benchName);
            javaPattern = Pattern.compile(p.regex, toJavaFlags(p.flags));
            tregexBool = context.parse(TRegexTestDummyLanguage.ID, TRegexTestDummyLanguage.BENCH_PREFIX + "GenerateDFAImmediately=true/" + p.regex + '/' + p.flags);
            tregexCG = context.parse(TRegexTestDummyLanguage.ID, TRegexTestDummyLanguage.BENCH_CG_PREFIX + "GenerateDFAImmediately=true/" + p.regex + '/' + p.flags);
            input = "_".repeat(200) + p.input;
        }

        private static int toJavaFlags(String flags) {
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
        return state.tregexBool.execute(state.input, 0).asBoolean();
    }

    @Benchmark
    public int tregexCG(BenchState state) {
        return state.tregexCG.execute(state.input, 0).asInt();
    }
}
