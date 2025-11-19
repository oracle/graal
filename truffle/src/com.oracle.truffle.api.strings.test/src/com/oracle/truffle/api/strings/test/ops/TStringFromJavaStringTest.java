/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test.ops;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringFromJavaStringTest extends TStringTestBase {

    private final String[] strings = {
                    "ascii_foo",
                    "latin1_asdf\u00E4\u00F6\u00FC",
                    "bmp_\u1234__",
                    "valid_\ud800\udc00__",
                    "broken_\ud800__",
    };

    @Parameter public TruffleString.FromJavaStringNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.FromJavaStringNode> data() {
        return Arrays.asList(TruffleString.FromJavaStringNode.create(), TruffleString.FromJavaStringNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        for (int i = 0; i < strings.length; i++) {
            String string = strings[i];
            TruffleString a = node.execute(string, UTF_16);
            Assert.assertEquals(TruffleString.CodeRange.values()[i], a.getCodeRangeUncached(UTF_16));
            for (int j = 0; j < string.length(); j++) {
                Assert.assertEquals(string.charAt(j), a.readCharUTF16Uncached(j));
                Assert.assertEquals(string.charAt(j), node.execute(string, j, 1, UTF_16, true).readCharUTF16Uncached(0));
                Assert.assertEquals(string.charAt(j), node.execute(string, j, 1, UTF_16, false).readCharUTF16Uncached(0));
                if (j < string.length() - 1) {
                    for (boolean copy : new boolean[]{false, true}) {
                        TruffleString substring = node.execute(string, j, 2, UTF_16, copy);
                        Assert.assertEquals(string.charAt(j), substring.readCharUTF16Uncached(0));
                        Assert.assertEquals(string.charAt(j + 1), substring.readCharUTF16Uncached(1));
                    }
                }
            }
        }
    }

    @Test
    public void testNull() throws Exception {
        expectNullPointerException(() -> node.execute(null, UTF_8));
        expectNullPointerException(() -> node.execute(strings[0], null));
    }
}
