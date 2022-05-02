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
package com.oracle.truffle.regex.tregex.nodes.input;

import static com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.util.TRegexGuards;

@ImportStatic(TRegexGuards.class)
public abstract class InputEndsWithNode extends Node {

    public static InputEndsWithNode create() {
        return InputEndsWithNodeGen.create();
    }

    public abstract boolean execute(Object input, Object suffix, Object mask, Encoding encoding);

    @Specialization(guards = "mask == null")
    public boolean doBytes(byte[] input, byte[] suffix, @SuppressWarnings("unused") Object mask, @SuppressWarnings("unused") Encoding encoding) {
        return ArrayUtils.regionEqualsWithOrMask(input, input.length - suffix.length, suffix, 0, suffix.length, null);
    }

    @Specialization(guards = "mask != null")
    public boolean doBytesMask(byte[] input, byte[] suffix, byte[] mask, @SuppressWarnings("unused") Encoding encoding) {
        return ArrayUtils.regionEqualsWithOrMask(input, input.length - suffix.length, suffix, 0, mask.length, mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doString(String input, String suffix, @SuppressWarnings("unused") Object mask, @SuppressWarnings("unused") Encoding encoding) {
        return input.endsWith(suffix);
    }

    @Specialization(guards = "mask != null")
    public boolean doStringMask(String input, String suffix, String mask, @SuppressWarnings("unused") Encoding encoding) {
        return ArrayUtils.regionEqualsWithOrMask(input, input.length() - suffix.length(), suffix, 0, mask.length(), mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doTString(TruffleString input, TruffleString suffix, @SuppressWarnings("unused") Object mask, Encoding encoding,
                    @Cached TruffleString.RegionEqualByteIndexNode regionEqualsNode) {
        int len1 = input.byteLength(encoding.getTStringEncoding());
        int len2 = suffix.byteLength(encoding.getTStringEncoding());
        return len1 >= len2 && regionEqualsNode.execute(input, len1 - len2, suffix, 0, len2, encoding.getTStringEncoding());
    }

    @Specialization(guards = "mask != null")
    public boolean doTStringMask(TruffleString input, TruffleString suffix, TruffleString.WithMask mask, Encoding encoding,
                    @Cached TruffleString.RegionEqualByteIndexNode regionEqualsNode) {
        int len1 = input.byteLength(encoding.getTStringEncoding());
        int len2 = suffix.byteLength(encoding.getTStringEncoding());
        return len1 >= len2 && regionEqualsNode.execute(input, len1 - len2, mask, 0, len2, encoding.getTStringEncoding());
    }

    @Specialization(guards = {"neitherByteArrayNorString(input)", "mask == null"})
    public boolean doTruffleObjBytes(Object input, byte[] suffix, @SuppressWarnings("unused") Object mask, Encoding encoding,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return endsWithTruffleObj(input, suffix, null, encoding, lengthNode, charAtNode);
    }

    @Specialization(guards = {"neitherByteArrayNorString(input)", "mask != null"})
    public boolean doTruffleObjBytesMask(Object input, byte[] suffix, byte[] mask, Encoding encoding,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        assert mask.length == suffix.length;
        return endsWithTruffleObj(input, suffix, mask, encoding, lengthNode, charAtNode);
    }

    @Specialization(guards = {"neitherByteArrayNorString(input)", "mask == null"})
    public boolean doTruffleObjString(Object input, String suffix, @SuppressWarnings("unused") Object mask, Encoding encoding,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return endsWithTruffleObj(input, suffix, null, encoding, lengthNode, charAtNode);
    }

    @Specialization(guards = {"neitherByteArrayNorString(input)", "mask != null"})
    public boolean doTruffleObjStringMask(Object input, String suffix, String mask, Encoding encoding,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        assert mask.length() == suffix.length();
        return endsWithTruffleObj(input, suffix, mask, encoding, lengthNode, charAtNode);
    }

    private static boolean endsWithTruffleObj(Object input, byte[] suffix, byte[] mask, Encoding encoding, InputLengthNode lengthNode, InputReadNode charAtNode) {
        final int inputLength = lengthNode.execute(input, encoding);
        if (inputLength < suffix.length) {
            return false;
        }
        final int offset = inputLength - suffix.length;
        for (int i = 0; i < suffix.length; i++) {
            if (InputReadNode.readWithMask(input, offset + i, mask, i, encoding, charAtNode) != Byte.toUnsignedInt(suffix[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWithTruffleObj(Object input, String suffix, String mask, Encoding encoding, InputLengthNode lengthNode, InputReadNode charAtNode) {
        final int inputLength = lengthNode.execute(input, encoding);
        if (inputLength < suffix.length()) {
            return false;
        }
        final int offset = inputLength - suffix.length();
        for (int i = 0; i < suffix.length(); i++) {
            if (InputReadNode.readWithMask(input, offset + i, mask, i, encoding, charAtNode) != suffix.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
