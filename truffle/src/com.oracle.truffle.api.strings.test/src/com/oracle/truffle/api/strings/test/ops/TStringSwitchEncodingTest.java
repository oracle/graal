/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.test.TStringTestUtil.byteArray;
import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;
import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.api.strings.test.TStringTestBase;
import com.oracle.truffle.api.strings.test.TStringTestUtil;

@RunWith(Parameterized.class)
public class TStringSwitchEncodingTest extends TStringTestBase {

    @Parameter public TruffleString.SwitchEncodingNode node;
    @Parameter(1) public MutableTruffleString.SwitchEncodingNode nodeMutable;

    @Parameters(name = "{0}, {1}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                        new Object[]{TruffleString.SwitchEncodingNode.create(), MutableTruffleString.SwitchEncodingNode.create()},
                        new Object[]{TruffleString.SwitchEncodingNode.getUncached(), MutableTruffleString.SwitchEncodingNode.getUncached()});
    }

    @Test
    public void testCustomErrorHandler() {
        String brokenStr = "ab" + Character.MIN_SURROGATE;
        TruffleString a = TruffleString.fromJavaStringUncached(brokenStr, TruffleString.Encoding.UTF_16);
        TruffleString b = node.execute(a, TruffleString.Encoding.UTF_8, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
        Assert.assertEquals(brokenStr, a.toJavaStringUncached());
        Assert.assertEquals(brokenStr, node.execute(b, TruffleString.Encoding.UTF_16, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8).toJavaStringUncached());
        Assert.assertEquals("abX", node.execute(a, TruffleString.Encoding.UTF_8, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_8, 2)).toJavaStringUncached());
        Assert.assertEquals("abX", node.execute(b, TruffleString.Encoding.UTF_16, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_16, 3)).toJavaStringUncached());
        Assert.assertEquals("abXXX", node.execute(b, TruffleString.Encoding.UTF_16, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_16, -1)).toJavaStringUncached());
        Assert.assertEquals("abXXX", node.execute(b, TruffleString.Encoding.UTF_16, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_16, 1)).toJavaStringUncached());
        TruffleString c = TruffleString.fromByteArrayUncached(byteArray('a', 'b', 0xff), TruffleString.Encoding.ISO_8859_1);
        Assert.assertEquals("ab?", node.execute(c, TruffleString.Encoding.US_ASCII, TranscodingErrorHandler.DEFAULT).toJavaStringUncached());
        Assert.assertEquals("ab?", node.execute(c, TruffleString.Encoding.US_ASCII, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8).toJavaStringUncached());
        Assert.assertEquals("abX", node.execute(c, TruffleString.Encoding.US_ASCII, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.US_ASCII, 1)).toJavaStringUncached());

        // incomplete UTF-8 sequence:
        TruffleString d = TruffleString.fromByteArrayUncached(byteArray(0xf0, 0x90, 0x80), TruffleString.Encoding.UTF_8);
        Assert.assertEquals("\ufffd", node.execute(d, TruffleString.Encoding.UTF_16, TranscodingErrorHandler.DEFAULT).toJavaStringUncached());
        TruffleString d2 = TruffleString.fromCodePointUncached(0x10ffff, TruffleString.Encoding.UTF_8).concatUncached(d, TruffleString.Encoding.UTF_8, false);
        Assert.assertEquals("\udbff\udfff\ufffd", node.execute(d2, TruffleString.Encoding.UTF_16, TranscodingErrorHandler.DEFAULT).toJavaStringUncached());
        Assert.assertEquals("X", node.execute(d, TruffleString.Encoding.UTF_16, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_16, -1)).toJavaStringUncached());
        Assert.assertEquals("X", node.execute(d, TruffleString.Encoding.UTF_16, (string, index, length, src, dst) -> result("X", TruffleString.Encoding.UTF_16, 3)).toJavaStringUncached());

        TruffleString e = TruffleString.fromByteArrayUncached(byteArray(0xff), TruffleString.Encoding.CESU_8);
        Assert.assertEquals("?", node.execute(e, TruffleString.Encoding.US_ASCII, TranscodingErrorHandler.DEFAULT).toJavaStringUncached());
        Assert.assertEquals("X",
                        node.execute(e, TruffleString.Encoding.US_ASCII, (string, index, length, src, dst) -> {
                            Assert.assertEquals(0, index);
                            Assert.assertEquals(1, length);
                            Assert.assertEquals(TruffleString.Encoding.CESU_8, src);
                            Assert.assertEquals(TruffleString.Encoding.US_ASCII, dst);
                            Assert.assertEquals(0xff, string.readByteUncached(0, TruffleString.Encoding.CESU_8));
                            return result("X", TruffleString.Encoding.US_ASCII, -1);
                        }).toJavaStringUncached());
    }

    private static TranscodingErrorHandler.ReplacementString result(String s, TruffleString.Encoding encoding, int len) {
        return new TranscodingErrorHandler.ReplacementString(TruffleString.fromJavaStringUncached(s, encoding), len);
    }

    @Test
    public void testAll() throws Exception {
        EnumSet<TruffleString.Encoding> reducedEncodingSet = EnumSet.allOf(TruffleString.Encoding.class);
        reducedEncodingSet.removeIf(e -> e.name().startsWith("IBM") || e.name().startsWith("Windows") ||
                        e.name().startsWith("ISO_8859_"));
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            if (encoding == TruffleString.Encoding.UTF8_SoftBank || encoding == TruffleString.Encoding.CP51932) {
                // TODO: these encodings crash in JCodings (GR-34837)
                // https://github.com/jruby/jcodings/issues/42
                return;
            }
            for (TruffleString.Encoding targetEncoding : reducedEncodingSet) {
                boolean bothUTF = isUTF(encoding) && isUTF(targetEncoding);
                for (TranscodingErrorHandler errorHandler : bothUTF ? new TranscodingErrorHandler[]{TranscodingErrorHandler.DEFAULT,
                                TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8}
                                : new TranscodingErrorHandler[]{TranscodingErrorHandler.DEFAULT}) {
                    TruffleString b = node.execute(a, targetEncoding, errorHandler);
                    MutableTruffleString bMutable = nodeMutable.execute(a, targetEncoding, errorHandler);
                    if (a instanceof TruffleString &&
                                    (encoding == targetEncoding || !isDebugStrictEncodingChecks() && codeRange == TruffleString.CodeRange.ASCII && isAsciiCompatible(targetEncoding))) {
                        Assert.assertSame(a, b);
                    }
                    if (a instanceof MutableTruffleString && encoding == targetEncoding) {
                        Assert.assertSame(a, bMutable);
                    }
                    if (bothUTF) {
                        for (AbstractTruffleString target : new AbstractTruffleString[]{b, bMutable}) {
                            if (encoding == targetEncoding || isValid) {
                                assertCodePointsEqual(target, targetEncoding, codepoints);
                            } else {
                                TruffleStringIterator it = target.createCodePointIteratorUncached(targetEncoding);
                                for (int codepoint : codepoints) {
                                    int expected = codepoint;
                                    if (codepoint > Character.MAX_CODE_POINT) {
                                        expected = 0xfffd;
                                    } else if (targetEncoding == TruffleString.Encoding.UTF_8 && codepoint <= 0xffff &&
                                                    Character.isSurrogate((char) codepoint)) {
                                        if (errorHandler == TranscodingErrorHandler.DEFAULT) {
                                            expected = 0xfffd;
                                        } else if (errorHandler == TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8) {
                                            expected = 0xfffd;
                                            Assert.assertEquals(codepoint, TStringTestUtil.utf8DecodeValid(b, it.getByteIndex()));
                                        } else {
                                            Assert.fail();
                                        }
                                    }
                                    Assert.assertEquals(expected, it.nextUncached());
                                }
                            }
                        }
                    }
                }
            }
        });
    }

    @Test
    public void testNull() throws Exception {
        checkNullSE((s, e) -> node.execute(s, e));
    }
}
