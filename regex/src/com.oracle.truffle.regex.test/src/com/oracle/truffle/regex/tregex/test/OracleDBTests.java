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
package com.oracle.truffle.regex.tregex.test;

import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.test.generated.OracleDBGeneratedTests;

public class OracleDBTests extends RegexTestBase {

    public static final Map<String, String> ENGINE_OPTIONS = Map.of("regexDummyLang.Flavor", "OracleDB");

    @Override
    Map<String, String> getEngineOptions() {
        return ENGINE_OPTIONS;
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_8;
    }

    @Test
    public void generatedTests() {
        runGeneratedTests(OracleDBGeneratedTests.TESTS);
    }

    @Test
    public void gr52933() {
        test("\\z{1,2}a", "", "a", 0, false);
        test("\\z{1,3}a", "", "a", 0, false);
        test("\\z{1,4}a", "", "a", 0, false);
        test("\\z{1,5}a", "", "a", 0, false);
        test("\\z{1,6}a", "", "a", 0, false);
        test("\\z{1,7}a", "", "a", 0, false);
    }

    @Test
    public void testForceLinearExecution() {
        test("(a*)b\\1", "", "_aabaaa_", 0, true, 1, 6, 1, 3);
        expectUnsupported("(a*)b\\1", "", OPT_FORCE_LINEAR_EXECUTION);
        test(".*a{1,65534}.*", "", "_aabaaa_", 0, true, 0, 8);
        expectUnsupported(".*a{1,65534}.*", "", OPT_FORCE_LINEAR_EXECUTION);
    }

    @Test
    public void orcl38190286() {
        test("[[:alpha:]]", "", "\ufffd", 0, true, 0, 3);
        test("[[:alpha:]]", "", "\uD839", 0, false);
        test("[[:alpha:]]", "", "\uDDF2", 0, false);
        test("[[:alpha:]]", "", "\uD839\uDDF2", 0, false);
        test("[[:alpha:]]", "", Encodings.UTF_16, "\ufffd", 0, true, 0, 1);
        test("[[:alpha:]]", "", Encodings.UTF_16, "\uD839", 0, false);
        test("[[:alpha:]]", "", Encodings.UTF_16, "\uDDF2", 0, false);
        test("[[:alpha:]]", "", Encodings.UTF_16, "\uD839\uDDF2", 0, false);
    }

    @Test
    public void bqTransitionExplosion() {
        test("(a(b(b(b(b(b(b(b(b(b(b(b(b(b(b(b(b(b(b(b|)|)|)|)|)|)|)|)|)|)|)|)|)|)|)|)|)|)|){2,2}c)de", "", Map.of("regexDummyLang.QuantifierUnrollLimitGroup", "1"),
                        "abbbbbbbcdebbbbbbbf", 0, true, 0, 11, 0, 9, 8, 8, 2, 8, 3, 8, 4, 8, 5, 8, 6, 8, 7, 8, 8, 8);
    }

    @Test
    public void testNestedQuantifierBailout() {
        expectUnsupported("()?*");
        expectUnsupported("()?*|");
        expectUnsupported("()?*||");
        expectUnsupported("()?*||a");
        expectUnsupported("(a)???");
        expectUnsupported("(a?)*??");
        expectUnsupported("(a???)");
        expectUnsupported("a*??");
        expectUnsupported("a+*?");
        expectUnsupported("a+*??");
        expectUnsupported("a++?");
        expectUnsupported("a+??");
        expectUnsupported("a?*?");
        expectUnsupported("a?*??");
        expectUnsupported("a?+");
        expectUnsupported("a?+?");
        expectUnsupported("a?+??");
        expectUnsupported("a??+");
        expectUnsupported("a???");
        expectUnsupported("a??{0,1}");
        expectUnsupported("a{0,1}??");
        expectUnsupported("a{0,1}?{0,1}");
        expectUnsupported("()?*||^a\\Zb");
        expectUnsupported("\\D|++?");
        expectUnsupported("\\D|++?^");
        expectUnsupported("(\\d)|5+*?|[[:lower:]][[=l=]]^%");
        expectUnsupported("\\S|\\D|++?^(3)");
        expectUnsupported("\\S|\\D|++?^((3)|[R-_\\(/])t[[:alnum:]]c");
        expectUnsupported("x???");
        expectUnsupported("x????");
        expectUnsupported("x?????");
        expectUnsupported("x??????");
        expectUnsupported("x{2}*");
        expectUnsupported("x{2}*?");
        expectUnsupported("x{2}*??");
        expectUnsupported("x{2}*???");
        expectUnsupported("x{2}+");
        expectUnsupported("x{2}??");
    }
}
