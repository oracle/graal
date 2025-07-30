/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Map;

import org.junit.Test;

import com.oracle.truffle.regex.tregex.string.Encodings;

public class RegressionTests extends RegexTestBase {

    @Override
    Map<String, String> getEngineOptions() {
        return Collections.emptyMap();
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.UTF_16_RAW;
    }

    @Test
    public void regress11763() {
        test("^(\"(([^\"]|(\\\\\"))*[^\\\\])?\")", "", "\"../index\"\n         \"../inc/macros\"\n", 0, true, 0, 10, 0, 10, 1, 9, 7, 8, -1, -1);
    }

    @Test
    public void regress23987() {
        test("(^\"\"$)|(^\"([^\"]|\\\\\")*[^\\\\]\"$)", "", "\"./src/core.js\"", 0, true, 0, 15, -1, -1, 0, 15, 12, 13);
    }

    @Test
    public void regress24016() {
        test("(^$)|($)", "g", "", 0, true, 0, 0, 0, 0, -1, -1);
    }

    @Test
    public void regress39580() {
        test("(?=[^\\S\\r\\n]*)'''\\s*<summary>[^\\S\\r\\n]*(?=\\r\\n?|\\n)([\\s\\S]*?)(?=[^\\S\\r\\n]*)'''\\s*<\\/summary>[^\\S\\r\\n]*(?=\\r\\n?|\\n)", "g",
                        "\r\n    Open = 0\r\n    ''' <summary>\r\n    ''' All planets have been closed\r\n    ''' </summary>\r\n    C",
                        0, true, 20, 91, 33, 77);
    }

    @Test
    public void regress39580Reduced() {
        test("(?=[^\\S\\r\\n]*)'", "g", "\n'", 0, true, 1, 2);
    }

    @Test
    public void regress127145() {
        test("([0-9A-F]{8}[-]?([0-9A-F]{4}[-]?){3}[0-9A-F]{12})\\)$", "i",
                        "OData-EntityId: https://url.com/api/data/v8.2/tests(00000000-0000-0000-0000-000000000001)", 0, true, 52, 89, 52, 88, 71, 76);
    }

    @Test
    public void regress185324() {
        test("readme(\\.(md|txt|markdown))?$", "i", "folder/some-readme.md", 0, true, 12, 21, 18, 21, 19, 21);
    }

    @Test
    public void regress235287() {
        test("^(This in the first row(\\s{5}){0,1}){3}\\n(This in the second row(\\s{4}){0,1}){2}$", "",
                        "This in the first row     This in the first row     This in the first row\nThis in the second row    This in the second row", 0, true,
                        0, 122, 52, 73, -1, -1, 100, 122, -1, -1);
    }

    @Test
    public void regress242219() {
        test("((T00:00)?:00)?\\.000Z$", "", "2002-12-14T00:00:00.000Z", 0, true, 10, 24, 10, 19, 10, 16);
    }

    @Test
    public void regress356779() {
        test("\\bapp((\\.js))?$", "", "./src/js/app.js", 0, true, 9, 15, 12, 15, 12, 15);
    }

    @Test
    public void regress359999() {
        test("^(1[012]|0?[1-9])", "", "10a", 0, true, 0, 2, 0, 2);
    }

    @Test
    public void regress409298() {
        test("^(?=.*?\\bfoo\\b)(?=.*?\\bbar\\b).*$", "", "foo bar", 0, true, 0, 7);
    }

    @Test
    public void regress409298Reduced() {
        test("(?=.*?\\bb).*", "", "f b", 0, true, 0, 3);
    }

}
