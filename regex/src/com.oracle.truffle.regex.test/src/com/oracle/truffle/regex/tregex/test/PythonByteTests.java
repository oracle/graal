/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.string.Encodings;
import org.junit.Test;

public class PythonByteTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=Python";
    }

    @Override
    Encodings.Encoding getTRegexEncoding() {
        return Encodings.LATIN_1;
    }

    @Test
    public void gr23871() {
        test("[^:\\s][^:\\r\\n]*", "s", "PythonMethod=match", "\u00a0NonbreakSpace", 0, true, 0, 14);
    }

    @Test
    public void asciiWhitespace() {
        test("\\s*", "", " \t\n\r\f\u000B", 0, true, 0, 6);
    }

    @Test
    public void asciiNonWhitespace() {
        test("\\S", "", " \t\n\r\f\u000B", 0, false);
    }

    @Test
    public void localeSensitive() {
        // in ISO-8859-2:
        // f8 = lowercase r with caron
        // d8 = uppercase R with caron
        // ed = lowercase i with acute accent
        // cd = uppercase I with acute accent
        // b9 = lowercase s with caron
        // a9 = uppercase S with caron

        // in ISO-8859-1:
        // b9 = superscript one
        // a9 = copyright symbol

        // case-folding
        test("ji\u00f8\u00ed mar\u00b9\u00edk", "Li", "PythonLocale=cs_CZ.ISO-8859-2", "JI\u00d8\u00cd MAR\u00a9\u00cdK", 0, true, 0, 11, -1);
        test("ji\u00f8\u00ed mar\u00b9\u00edk", "Li", "PythonLocale=en_US.ISO-8859-1", "JI\u00d8\u00cd MAR\u00a9\u00cdK", 0, false);
        test("ji\u00f8\u00ed mar\u00b9\u00edk", "i", "JI\u00d8\u00cd MAR\u00a9\u00cdK", 0, false);

        // word characters
        test("\\w+", "L", "PythonLocale=cs_CZ.ISO-8859-2", "A\u00b9", 0, true, 0, 2, -1);
        test("\\w+", "L", "PythonLocale=en_US.ISO-8859-1", "A\u00b9", 0, true, 0, 1, -1);
        test("\\w+", "", "A\u00b9", 0, true, 0, 1, -1);

        // word boundaries
        test("\\b", "L", "PythonLocale=cs_CZ.ISO-8859-2", "\u00a9", 0, true, 0, 0, -1);
        test("\\b", "L", "PythonLocale=en_US.ISO-8859-1", "\u00a9", 0, false);
        test("\\b", "", "\u00a9", 0, false);

        // turkish I
        // in ISO-8859-9:
        // dd = uppercase dotted I
        // fd = lowercase dotless i
        test("i", "iL", "PythonLocale=tr_TR.ISO-8859-9", "I", 0, false);
        test("i", "iL", "PythonLocale=tr_TR.ISO-8859-9", "\u00dd", 0, true, 0, 1, -1);
        test("I", "iL", "PythonLocale=tr_TR.ISO-8859-9", "i", 0, false);
        test("I", "iL", "PythonLocale=tr_TR.ISO-8859-9", "\u00fd", 0, true, 0, 1, -1);
        test("\u00dd", "iL", "PythonLocale=tr_TR.ISO-8859-9", "\u00fd", 0, false);
        test("\u00dd", "iL", "PythonLocale=tr_TR.ISO-8859-9", "i", 0, true, 0, 1, -1);
        test("\u00fd", "iL", "PythonLocale=tr_TR.ISO-8859-9", "\u00dd", 0, false);
        test("\u00fd", "iL", "PythonLocale=tr_TR.ISO-8859-9", "I", 0, true, 0, 1, -1);
    }

    @Test
    public void unsupportedLocale() {
        expectUnsupported("foo", "iL", "PythonLocale=foo");
        expectUnsupported("foo", "iL", "PythonLocale=foo.");
        expectUnsupported("foo", "iL", "PythonLocale=foo.!");
        expectUnsupported("foo", "iL", "PythonLocale=ab_XY.ISO-8859-42");
    }
}
