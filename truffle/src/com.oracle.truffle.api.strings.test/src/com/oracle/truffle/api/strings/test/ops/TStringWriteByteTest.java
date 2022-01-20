/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringWriteByteTest extends TStringTestBase {

    @Parameter public MutableTruffleString.WriteByteNode node;

    @Parameters(name = "{0}")
    public static Iterable<MutableTruffleString.WriteByteNode> data() {
        return Arrays.asList(MutableTruffleString.WriteByteNode.create(), MutableTruffleString.WriteByteNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        byte v = (byte) 0x81;
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            byte[] modified = Arrays.copyOf(array, array.length);
            checkNotifyExternal(MutableTruffleString.fromByteArrayUncached(modified, 0, modified.length, encoding, false), encoding, () -> {
                modified[0] = v;
            });
            PointerObject pointerObject = PointerObject.create(array);
            checkNotifyExternal(MutableTruffleString.fromNativePointerUncached(pointerObject, 0, array.length, encoding, false), encoding, () -> {
                pointerObject.writeByte(0, v);
            });
            if (a instanceof MutableTruffleString) {
                TruffleString[] immutable = {
                                a.asTruffleStringUncached(encoding),
                                a.substringUncached(0, codepoints.length, encoding, true),
                                a.substringByteIndexUncached(0, array.length, encoding, true),
                                a.concatUncached(TruffleString.fromByteArrayUncached(new byte[0], 0, 0, encoding, false), encoding, true),
                };
                node.execute((MutableTruffleString) a, 0, v, encoding);
                assertBytesEqual(a, encoding, modified);
                for (TruffleString b : immutable) {
                    assertBytesEqual(b, encoding, array);
                }
            } else {
                MutableTruffleString[] mutable = {
                                a.asMutableTruffleStringUncached(encoding),
                                MutableTruffleString.SubstringNode.getUncached().execute(a, 0, codepoints.length, encoding),
                                MutableTruffleString.SubstringByteIndexNode.getUncached().execute(a, 0, array.length, encoding),
                                MutableTruffleString.ConcatNode.getUncached().execute(a, TruffleString.fromByteArrayUncached(new byte[0], 0, 0, encoding, false), encoding),
                };
                for (MutableTruffleString b : mutable) {
                    node.execute(b, 0, v, encoding);
                    assertBytesEqual(b, encoding, modified);
                }
                assertBytesEqual(a, encoding, array);
            }
        });
    }

    private void checkNotifyExternal(MutableTruffleString string, TruffleString.Encoding encoding, Runnable mutate) {
        TruffleString.CodeRange codeRangeBeforeMutate = string.getCodeRangeUncached(encoding);
        mutate.run();
        TruffleString.CodeRange codeRangeAfterMutate = string.getCodeRangeUncached(encoding);
        Assert.assertSame(codeRangeBeforeMutate, codeRangeAfterMutate);
        string.notifyExternalMutation();
        TruffleString.CodeRange codeRangeAfterNotify = string.getCodeRangeUncached(encoding);
        if (encoding == UTF_8) {
            Assert.assertSame(TruffleString.CodeRange.BROKEN, codeRangeAfterNotify);
        } else if (encoding == UTF_16 || encoding == UTF_32) {
            Assert.assertTrue(codeRangeAfterNotify.isSupersetOf(TruffleString.CodeRange.LATIN_1));
        }
        Assert.assertTrue(codeRangeAfterNotify.isSupersetOf(codeRangeBeforeMutate));
    }

    @Test
    public void testNull() throws Exception {
        expectNullPointerException(() -> node.execute(null, 0, (byte) 0, UTF_8));
        expectNullPointerException(() -> node.execute(MutableTruffleString.fromByteArrayUncached(new byte[0], 0, 0, UTF_8, false), 0, (byte) 0, null));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            if (a instanceof MutableTruffleString) {
                forOutOfBoundsIndices(array.length, false, i -> expectOutOfBoundsException(() -> ((MutableTruffleString) a).writeByteUncached(i, (byte) 0, encoding)));
            }
        });
    }
}
