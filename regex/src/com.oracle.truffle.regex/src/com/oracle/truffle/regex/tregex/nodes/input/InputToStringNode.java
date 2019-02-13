/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

public abstract class InputToStringNode extends Node {

    public static InputToStringNode create() {
        return InputToStringNodeGen.create();
    }

    public abstract String execute(Object input);

    @Specialization
    public String doString(String input) {
        return input;
    }

    @Specialization
    public String doTruffleObject(TruffleObject input,
                    @Cached("create()") InputLengthNode lengthNode,
                    @Cached("create()") InputCharAtNode charAtNode) {
        final int inputLength = lengthNode.execute(input);
        StringBuilder sb = createStringBuilder(inputLength);
        for (int i = 0; i < inputLength; i++) {
            stringBuilderAppend(sb, charAtNode.execute(input, i));
        }
        return stringBuilderToString(sb);
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
