/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

public abstract class InputIndexOfNode extends Node {

    public static InputIndexOfNode create() {
        return InputIndexOfNodeGen.create();
    }

    public abstract int execute(Object input, int fromIndex, int maxIndex, Object chars, Encoding encoding);

    @Specialization
    public int doBytes(byte[] input, int fromIndex, int maxIndex, byte[] bytes, @SuppressWarnings("unused") Encoding encoding) {
        return ArrayUtils.indexOf(input, fromIndex, maxIndex, bytes);
    }

    @Specialization
    public int doChars(String input, int fromIndex, int maxIndex, char[] chars, @SuppressWarnings("unused") Encoding encoding) {
        return ArrayUtils.indexOf(input, fromIndex, maxIndex, chars);
    }

    @Specialization
    public int doTStringBytes(TruffleString input, int fromIndex, int maxIndex, byte[] bytes, Encoding encoding,
                    @Cached TruffleString.ByteIndexOfAnyByteNode indexOfRawValueNode) {
        return indexOfRawValueNode.execute(input, fromIndex, maxIndex, bytes, encoding.getTStringEncoding());
    }

    @Specialization
    public int doTStringChars(TruffleString input, int fromIndex, int maxIndex, char[] chars, Encoding encoding,
                    @Cached TruffleString.CharIndexOfAnyCharUTF16Node indexOfRawValueNode) {
        assert encoding == Encodings.UTF_16 || encoding == Encodings.UTF_16_RAW;
        return indexOfRawValueNode.execute(input, fromIndex, maxIndex, chars);
    }

    @Specialization(guards = "neitherByteArrayNorString(input)")
    public int doTruffleObjBytes(Object input, int fromIndex, int maxIndex, byte[] bytes, Encoding encoding,
                    @Cached InputReadNode charAtNode) {
        for (int i = fromIndex; i < maxIndex; i++) {
            int c = charAtNode.execute(input, i, encoding);
            for (byte v : bytes) {
                if (c == Byte.toUnsignedInt(v)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Specialization(guards = "neitherByteArrayNorString(input)")
    public int doTruffleObjChars(Object input, int fromIndex, int maxIndex, char[] chars, Encoding encoding,
                    @Cached InputReadNode charAtNode) {
        for (int i = fromIndex; i < maxIndex; i++) {
            int c = charAtNode.execute(input, i, encoding);
            for (char v : chars) {
                if (c == v) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected static boolean neitherByteArrayNorString(Object obj) {
        return !(obj instanceof byte[]) && !(obj instanceof String) && !(obj instanceof TruffleString);
    }
}
