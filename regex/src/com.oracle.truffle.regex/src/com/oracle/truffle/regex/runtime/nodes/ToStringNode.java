/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
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
                throw UnsupportedTypeException.create(new Object[]{input});
            }
            StringBuilder sb = createStringBuilder((int) inputLength);
            for (int i = 0; i < inputLength; i++) {
                stringBuilderAppend(sb, toCharNode.execute(inputs.readArrayElement(input, i)));
            }
            return stringBuilderToString(sb);
        } catch (UnsupportedMessageException | InvalidArrayIndexException e) {
            CompilerDirectives.transferToInterpreter();
            throw UnsupportedTypeException.create(new Object[]{input});
        }
    }

    @TruffleBoundary
    private static StringBuilder createStringBuilder(int inputLength) {
        return new StringBuilder(inputLength);
    }

    private static void stringBuilderAppend(StringBuilder stringBuilder, char c) {
        stringBuilder.append(c);
    }

    @TruffleBoundary
    private static String stringBuilderToString(StringBuilder stringBuilder) {
        return stringBuilder.toString();
    }
}
