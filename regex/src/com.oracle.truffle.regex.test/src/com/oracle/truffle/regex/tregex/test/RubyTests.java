/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

public class RubyTests extends RegexTestBase {

    @Override
    String getEngineOptions() {
        return "Flavor=Ruby";
    }

    @Test
    public void gr28693() {
        test("\\A([0-9]+)_([_a-z0-9]*)\\.?([_a-z0-9]*)?\\.rb\\z", "", "20190116152522_enable_postgis_extension.rb", 0, true, 0, 42, 0, 14, 15, 39, 39, 39);
        test("\\A([0-9]+)_([_a-z0-9]*)\\.?([_a-z0-9]*)?\\.rb\\z", "", "20190116152523_create_schools.rb", 0, true, 0, 32, 0, 14, 15, 29, 29, 29);
        test("^0{2}?(00)?(44)?(0)?([1-357-9]\\d{9}|[18]\\d{8}|8\\d{6})$", "", "07123456789", 0, true, 0, 11, -1, -1, -1, -1, 0, 1, 1, 11);
        test("^0{2}?(00)?(44)(0)?([1-357-9]\\d{9}|[18]\\d{8}|8\\d{6})$", "", "447123456789", 0, true, 0, 12, -1, -1, 0, 2, -1, -1, 2, 12);
        test("^0{2}?(00)?44", "", "447123456789", 0, true, 0, 2, -1, -1);

        Assert.assertEquals(5,
                        compileRegex("\n" +
                                        "      ^\n" +
                                        "      ([ ]*) # indentations\n" +
                                        "      (.+) # key\n" +
                                        "      (?::(?=(?:\\s|$))) # :  (without the lookahead the #key includes this when : is present in value)\n" +
                                        "      [ ]?\n" +
                                        "      (['\"]?) # optional opening quote\n" +
                                        "      (.*) # value\n" +
                                        "      \\3 # matching closing quote\n" +
                                        "      $\n" +
                                        "    ", "x").getMember("groupCount").asInt());
    }
}
