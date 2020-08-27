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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.runtime.nodes.ToCharNode;
import com.oracle.truffle.regex.tregex.util.Exceptions;

@GenerateUncached
public abstract class InputReadNode extends Node {

    public static InputReadNode create() {
        return InputReadNodeGen.create();
    }

    public abstract int execute(Object input, int index);

    @Specialization
    static int doBytes(byte[] input, int index) {
        return Byte.toUnsignedInt(input[index]);
    }

    @Specialization
    static int doString(String input, int index) {
        return input.charAt(index);
    }

    @Specialization(guards = "inputs.hasArrayElements(input)", limit = "2")
    static int doBoxedCharArray(Object input, int index,
                    @CachedLibrary("input") InteropLibrary inputs,
                    @Cached ToCharNode toCharNode) {
        try {
            return toCharNode.execute(inputs.readArrayElement(input, index));
        } catch (UnsupportedMessageException | InvalidArrayIndexException | UnsupportedTypeException e) {
            throw Exceptions.shouldNotReachHere();
        }
    }

    public static int readWithMask(TruffleObject input, int indexInput, String mask, int indexMask, InputReadNode charAtNode) {
        CompilerAsserts.partialEvaluationConstant(mask == null);
        int c = charAtNode.execute(input, indexInput);
        return (mask == null ? c : (c | mask.charAt(indexMask)));
    }

    public static int readWithMask(TruffleObject input, int indexInput, byte[] mask, int indexMask, InputReadNode charAtNode) {
        CompilerAsserts.partialEvaluationConstant(mask == null);
        int c = charAtNode.execute(input, indexInput);
        return (mask == null ? c : (c | Byte.toUnsignedInt(mask[indexMask])));
    }
}
