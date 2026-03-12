/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.runners.Parameterized.Parameter;

import java.lang.ref.Reference;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringFromZeroTerminatedNativePointerTest extends TStringTestBase {

    @Parameter public TruffleString.FromZeroTerminatedNativePointerNode node;

    @Parameters(name = "{0}")
    public static Iterable<Object> data() {
        return Arrays.asList(TruffleString.FromZeroTerminatedNativePointerNode.create(), TruffleString.FromZeroTerminatedNativePointerNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        byte[] array = new byte[1024];
        for (int i = 0; i < array.length; i++) {
            array[i] = (byte) (((byte) i) | 1);
        }
        PointerObject nativeObj = PointerObject.create(array);
        try {
            forAllEncodings((encoding) -> {
                int naturalStride = getNaturalStride(encoding);
                for (int byteOffset : new int[]{0, 1 << naturalStride}) {
                    for (boolean copy : new boolean[]{true, false}) {
                        for (int byteLength : new int[]{4 << naturalStride, 40 << naturalStride}) {
                            testZeroTerminatedNode(array, node::execute8Bit, nativeObj, byteOffset, byteLength, encoding, copy, 1);
                            testZeroTerminatedNode(array, node::execute16Bit, nativeObj, byteOffset, byteLength, encoding, copy, 2);
                            testZeroTerminatedNode(array, node::execute32Bit, nativeObj, byteOffset, byteLength, encoding, copy, 4);
                        }
                    }
                }
            });
        } finally {
            Reference.reachabilityFence(nativeObj);
        }
    }

    private static void testZeroTerminatedNode(byte[] array, CallFromZeroTerminatedNode call, PointerObject nativeObj, int byteOffset, int byteLength, TruffleString.Encoding encoding, boolean copy,
                    int nBytes) {
        int iTerm = byteOffset + byteLength;
        for (int i = 0; i < nBytes; i++) {
            nativeObj.writeByte(iTerm + i, (byte) 0);
        }
        TruffleString a = call.call(nativeObj, byteOffset, encoding, copy);
        Assert.assertEquals(byteLength, a.byteLength(encoding));
        assertBytesEqual(a, encoding, array, byteOffset, byteLength);
        for (int i = 0; i < nBytes; i++) {
            nativeObj.writeByte(iTerm + i, array[iTerm + i]);
        }
    }

    @FunctionalInterface
    private interface CallFromZeroTerminatedNode {
        TruffleString call(Object pointerObject, int byteOffset, TruffleString.Encoding encoding, boolean copy);
    }
}
