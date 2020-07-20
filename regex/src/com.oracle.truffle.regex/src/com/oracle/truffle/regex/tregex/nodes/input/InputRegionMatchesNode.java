/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class InputRegionMatchesNode extends Node {

    public static InputRegionMatchesNode create() {
        return InputRegionMatchesNodeGen.create();
    }

    public abstract boolean execute(Object input, int fromIndex1, Object match, int fromIndex2, int length, Object mask);

    @Specialization(guards = "mask == null")
    public boolean doBytes(byte[] input, int fromIndex1, byte[] match, int fromIndex2, int length, @SuppressWarnings("unused") Object mask) {
        return ArrayUtils.regionEqualsWithOrMask(input, fromIndex1, match, fromIndex2, length, null);
    }

    @Specialization(guards = "mask != null")
    public boolean doBytesMask(byte[] input, int fromIndex1, byte[] match, int fromIndex2, int length, byte[] mask) {
        return ArrayUtils.regionEqualsWithOrMask(input, fromIndex1, match, fromIndex2, length, mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doString(String input, int fromIndex1, String match, int fromIndex2, int length, @SuppressWarnings("unused") Object mask) {
        return input.regionMatches(fromIndex1, match, fromIndex2, length);
    }

    @Specialization(guards = "mask != null")
    public boolean doJavaStringMask(String input, int fromIndex1, String match, int fromIndex2, int length, String mask) {
        return ArrayUtils.regionEqualsWithOrMask(input, fromIndex1, match, fromIndex2, length, mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doTruffleObjBytes(TruffleObject input, int fromIndex1, byte[] match, int fromIndex2, int length, @SuppressWarnings("unused") Object mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, null, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean doTruffleObjBytesMask(TruffleObject input, int fromIndex1, byte[] match, int fromIndex2, int length, byte[] mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        assert match.length == mask.length;
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask == null")
    public boolean doTruffleObjString(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, @SuppressWarnings("unused") Object mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, null, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean doTruffleObjStringMask(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, String mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        assert match.length() == mask.length();
        return regionMatchesTruffleObj(input, fromIndex1, match, fromIndex2, length, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask == null")
    public boolean doTruffleObjTruffleObj(TruffleObject input, int fromIndex1, TruffleObject match, int fromIndex2, int length, @SuppressWarnings("unused") Object mask,
                    @Cached InputLengthNode lengthNode1,
                    @Cached InputReadNode charAtNode1,
                    @Cached InputLengthNode lengthNode2,
                    @Cached InputReadNode charAtNode2) {
        if (fromIndex1 + length > lengthNode1.execute(input) || fromIndex2 + length > lengthNode2.execute(match)) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (charAtNode1.execute(input, fromIndex1 + i) != charAtNode2.execute(match, fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionMatchesTruffleObj(TruffleObject input, int fromIndex1, byte[] match, int fromIndex2, int length, byte[] mask,
                    InputLengthNode lengthNode,
                    InputReadNode charAtNode) {
        if (fromIndex1 + length > lengthNode.execute(input) || fromIndex2 + length > match.length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (InputReadNode.readWithMask(input, fromIndex1 + i, mask, i, charAtNode) != Byte.toUnsignedInt(match[fromIndex2 + i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionMatchesTruffleObj(TruffleObject input, int fromIndex1, String match, int fromIndex2, int length, String mask,
                    InputLengthNode lengthNode,
                    InputReadNode charAtNode) {
        if (fromIndex1 + length > lengthNode.execute(input) || fromIndex2 + length > match.length()) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            if (InputReadNode.readWithMask(input, fromIndex1 + i, mask, i, charAtNode) != match.charAt(fromIndex2 + i)) {
                return false;
            }
        }
        return true;
    }
}
