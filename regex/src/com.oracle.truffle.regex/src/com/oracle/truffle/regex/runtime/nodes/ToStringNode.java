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
package com.oracle.truffle.regex.runtime.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class ToStringNode extends Node {

    public static ToStringNode create() {
        return ToStringNodeGen.create();
    }

    public abstract String execute(Object input) throws UnsupportedTypeException;

    @Specialization
    static String doString(String input) {
        return input;
    }

    @Specialization(guards = "inputs.isString(input)", limit = "2")
    static String doBoxedString(Object input, @CachedLibrary("input") InteropLibrary inputs) throws UnsupportedTypeException {
        try {
            return inputs.asString(input);
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{input});
        }
    }

    @Specialization(guards = "inputs.hasArrayElements(input)", limit = "2")
    static String doBoxedCharArray(Object input,
                    @CachedLibrary("input") InteropLibrary inputs,
                    @Cached ToCharNode toCharNode) throws UnsupportedTypeException {
        try {
            final long inputLength = inputs.getArraySize(input);
            if (inputLength > Integer.MAX_VALUE) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw UnsupportedTypeException.create(new Object[]{input});
            }
            StringBuilder sb = createStringBuilder((int) inputLength);
            for (int i = 0; i < inputLength; i++) {
                stringBuilderAppend(sb, toCharNode.execute(inputs.readArrayElement(input, i)));
            }
            return stringBuilderToString(sb);
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw UnsupportedTypeException.create(new Object[]{input});
        }
    }

    @TruffleBoundary
    private static StringBuilder createStringBuilder(int inputLength) {
        return new StringBuilder(inputLength);
    }

    @TruffleBoundary
    private static void stringBuilderAppend(StringBuilder stringBuilder, char c) {
        stringBuilder.append(c);
    }

    @TruffleBoundary
    private static String stringBuilderToString(StringBuilder stringBuilder) {
        return stringBuilder.toString();
    }
}
