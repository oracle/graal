/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.RegexSyntaxException;
import com.oracle.truffle.regex.flavor.js.ECMAScriptFlavor;
import com.oracle.truffle.regex.flavor.python.PythonFlavor;
import com.oracle.truffle.regex.flavor.ruby.RubyFlavor;
import com.oracle.truffle.regex.test.dummylang.TRegexTestDummyLanguage;
import com.oracle.truffle.regex.tregex.parser.MatchingMode;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class RegexOptionsTest extends RegexTestBase {

    @BeforeClass
    public static void forceInitialization() throws IOException {
        // ideally, this should be context.initializeLanguage("regex"), but that doesn't work for
        // internal languages.
        context.parse(org.graalvm.polyglot.Source.newBuilder(TRegexTestDummyLanguage.ID, "/./", "test").build());
    }

    private static RegexOptions parse(String options) {
        Source src = Source.newBuilder(RegexLanguage.ID, options + "/./", "test").build();
        RegexOptions.Builder builder = RegexOptions.builder(src, src.getOptions(RegexLanguage.get(null)));
        builder.parseOptions();
        return builder.build();
    }

    private static RegexOptions parse(Map<String, String> options) {
        Source.LiteralBuilder sourceBuilder = Source.newBuilder(RegexLanguage.ID, "/./", "test");
        for (Map.Entry<String, String> entry : options.entrySet()) {
            sourceBuilder.option("regex." + entry.getKey(), entry.getValue());
        }
        Source src = sourceBuilder.build();
        RegexOptions.Builder builder = RegexOptions.builder(src, src.getOptions(RegexLanguage.get(null)));
        builder.parseOptions();
        return builder.build();
    }

    @Override
    Map<String, String> getEngineOptions() {
        return Collections.emptyMap();
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16_RAW;
    }

    private static String setBool(String... name) {
        return Arrays.stream(name).map(s -> s + "=true").collect(Collectors.joining(","));
    }

    private static String setVal(String key, String val) {
        return key + "=" + val;
    }

    @Test
    public void testParseOptions() {
        assertTrue(parse(setBool(RegexOptions.ALWAYS_EAGER_NAME)).isAlwaysEager());
        assertTrue(parse(setBool(RegexOptions.BOOLEAN_MATCH_NAME)).isBooleanMatch());
        assertTrue(parse(setBool(RegexOptions.DUMP_AUTOMATA_NAME)).isDumpAutomata());
        assertTrue(parse(setBool(RegexOptions.GENERATE_INPUT_NAME)).isGenerateInput());
        assertTrue(parse(setBool(RegexOptions.MUST_ADVANCE_NAME)).isMustAdvance());
        assertTrue(parse(setBool(RegexOptions.REGRESSION_TEST_MODE_NAME)).isRegressionTestMode());
        assertTrue(parse(setBool(RegexOptions.STEP_EXECUTION_NAME)).isStepExecution());
        assertTrue(parse(setBool(RegexOptions.U180E_WHITESPACE_NAME)).isU180EWhitespace());
        assertTrue(parse(setBool(RegexOptions.UTF_16_EXPLODE_ASTRAL_SYMBOLS_NAME)).isUTF16ExplodeAstralSymbols());
        assertTrue(parse(setBool(RegexOptions.VALIDATE_NAME)).isValidate());
        assertEquals(ECMAScriptFlavor.INSTANCE, parse(setVal(RegexOptions.FLAVOR_NAME, RegexOptions.FLAVOR_ECMASCRIPT)).getFlavor());
        assertEquals(PythonFlavor.INSTANCE, parse(setVal(RegexOptions.FLAVOR_NAME, RegexOptions.FLAVOR_PYTHON)).getFlavor());
        assertEquals(RubyFlavor.INSTANCE, parse(setVal(RegexOptions.FLAVOR_NAME, RegexOptions.FLAVOR_RUBY)).getFlavor());
        RegexOptions opt = parse(setBool(RegexOptions.ALWAYS_EAGER_NAME, RegexOptions.DUMP_AUTOMATA_NAME, RegexOptions.REGRESSION_TEST_MODE_NAME));
        assertTrue(opt.isAlwaysEager());
        assertTrue(opt.isDumpAutomata());
        assertTrue(opt.isRegressionTestMode());
        assertEquals(MatchingMode.match, parse(setVal(RegexOptions.PYTHON_METHOD_NAME, RegexOptions.MATCHING_MODE_MATCH)).getMatchingMode());
        assertEquals(MatchingMode.fullmatch, parse(setVal(RegexOptions.PYTHON_METHOD_NAME, RegexOptions.MATCHING_MODE_FULLMATCH)).getMatchingMode());
        assertEquals(MatchingMode.search, parse(setVal(RegexOptions.PYTHON_METHOD_NAME, RegexOptions.MATCHING_MODE_SEARCH)).getMatchingMode());
        assertEquals(MatchingMode.match, parse(setVal(RegexOptions.MATCHING_MODE_NAME, RegexOptions.MATCHING_MODE_MATCH)).getMatchingMode());
        assertEquals(MatchingMode.fullmatch, parse(setVal(RegexOptions.MATCHING_MODE_NAME, RegexOptions.MATCHING_MODE_FULLMATCH)).getMatchingMode());
        assertEquals(MatchingMode.search, parse(setVal(RegexOptions.MATCHING_MODE_NAME, RegexOptions.MATCHING_MODE_SEARCH)).getMatchingMode());
        assertEquals("abc", parse(setVal(RegexOptions.PYTHON_LOCALE_NAME, "abc")).getPythonLocale());
        assertEquals(123, parse(RegexOptions.MAX_DFA_SIZE_NAME + "=123").getMaxDFASize());
        assertEquals(123, parse(RegexOptions.MAX_BACK_TRACKER_SIZE_NAME + "=123").getMaxBackTrackerCompileSize());
    }

    @Test
    public void testSourceOptions() {
        assertTrue(parse(Map.of("AlwaysEager", "true")).isAlwaysEager());
        assertTrue(parse(Map.of("BooleanMatch", "true")).isBooleanMatch());
        assertTrue(parse(Map.of("DumpAutomata", "true")).isDumpAutomata());
        assertTrue(parse(Map.of("GenerateInput", "true")).isGenerateInput());
        assertTrue(parse(Map.of("MustAdvance", "true")).isMustAdvance());
        assertTrue(parse(Map.of("RegressionTestMode", "true")).isRegressionTestMode());
        assertTrue(parse(Map.of("DumpAutomataExecution", "true")).isStepExecution());
        assertTrue(parse(Map.of("U180EWhitespace", "true")).isU180EWhitespace());
        assertTrue(parse(Map.of("Validate", "true")).isValidate());
        assertTrue(parse(Map.of("ForceLinearExecution", "true")).isForceLinearExecution());
        assertEquals(ECMAScriptFlavor.INSTANCE, parse(Map.of("Flavor", "ECMAScript")).getFlavor());
        assertEquals(PythonFlavor.INSTANCE, parse(Map.of("Flavor", "Python")).getFlavor());
        assertEquals(RubyFlavor.INSTANCE, parse(Map.of("Flavor", "Ruby")).getFlavor());
        RegexOptions opt = parse(setBool("AlwaysEager", "DumpAutomata", "RegressionTestMode"));
        assertTrue(opt.isAlwaysEager());
        assertTrue(opt.isDumpAutomata());
        assertTrue(opt.isRegressionTestMode());
        assertEquals(MatchingMode.match, parse(Map.of("MatchingMode", "match")).getMatchingMode());
        assertEquals(MatchingMode.fullmatch, parse(Map.of("MatchingMode", "fullmatch")).getMatchingMode());
        assertEquals(MatchingMode.search, parse(Map.of("MatchingMode", "search")).getMatchingMode());
        assertEquals("abc", parse(Map.of("PythonLocale", "abc")).getPythonLocale());
        assertEquals(123, parse(Map.of("MaxDFASize", "123")).getMaxDFASize());
        assertEquals(123, parse(Map.of("MaxBackTrackerJITSize", "123")).getMaxBackTrackerCompileSize());
        assertEquals(123, parse(Map.of("QuantifierUnrollLimitSingleCC", "123")).quantifierUnrollLimitSingleCC);
        assertEquals(123, parse(Map.of("QuantifierUnrollLimitGroup", "123")).quantifierUnrollLimitGroup);
    }

    @Test(expected = RegexSyntaxException.class)
    public void testNegative() {
        parse(RegexOptions.MAX_DFA_SIZE_NAME + "=-123");
    }

    @Test(expected = RegexSyntaxException.class)
    public void testOverflow() {
        parse(RegexOptions.MAX_DFA_SIZE_NAME + "=" + (Short.MAX_VALUE + 1));
    }
}
