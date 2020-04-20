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

import java.util.Arrays;

import com.oracle.truffle.api.ArrayUtils;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class InputEqualsNode extends Node {

    public static InputEqualsNode create() {
        return InputEqualsNodeGen.create();
    }

    public abstract boolean execute(Object input, Object string, Object mask);

    @Specialization(guards = "mask == null")
    public boolean doBytes(byte[] input, byte[] string, @SuppressWarnings("unused") Object mask) {
        return Arrays.equals(input, string);
    }

    @Specialization(guards = "mask != null")
    public boolean doBytesMask(byte[] input, byte[] string, byte[] mask) {
        return input.length == string.length && ArrayUtils.regionEqualsWithOrMask(input, 0, string, 0, mask.length, mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doString(String input, String string, @SuppressWarnings("unused") Object mask) {
        return input.equals(string);
    }

    @Specialization(guards = "mask != null")
    public boolean doStringMask(String input, String string, String mask) {
        return input.length() == string.length() && ArrayUtils.regionEqualsWithOrMask(input, 0, string, 0, mask.length(), mask);
    }

    @Specialization(guards = "mask == null")
    public boolean doTruffleObjBytes(TruffleObject input, byte[] string, @SuppressWarnings("unused") Object mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return equalsTruffleObj(input, string, null, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean doTruffleObjBytesMask(TruffleObject input, byte[] string, byte[] mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return equalsTruffleObj(input, string, mask, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask == null")
    public boolean doTruffleObjString(TruffleObject input, String string, @SuppressWarnings("unused") Object mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return equalsTruffleObj(input, string, null, lengthNode, charAtNode);
    }

    @Specialization(guards = "mask != null")
    public boolean doTruffleObjStringMask(TruffleObject input, String string, String mask,
                    @Cached InputLengthNode lengthNode,
                    @Cached InputReadNode charAtNode) {
        return equalsTruffleObj(input, string, mask, lengthNode, charAtNode);
    }

    private static boolean equalsTruffleObj(TruffleObject input, String string, String mask, InputLengthNode lengthNode, InputReadNode charAtNode) {
        if (lengthNode.execute(input) != string.length()) {
            return false;
        }
        for (int i = 0; i < string.length(); i++) {
            if (InputReadNode.readWithMask(input, i, mask, i, charAtNode) != string.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean equalsTruffleObj(TruffleObject input, byte[] string, byte[] mask, InputLengthNode lengthNode, InputReadNode charAtNode) {
        if (lengthNode.execute(input) != string.length) {
            return false;
        }
        for (int i = 0; i < string.length; i++) {
            if (InputReadNode.readWithMask(input, i, mask, i, charAtNode) != Byte.toUnsignedInt(string[i])) {
                return false;
            }
        }
        return true;
    }
}
