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
package com.oracle.truffle.regex.tregex.nodes.input;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@GenerateUncached
public abstract class InputLengthNode extends Node {

    public static InputLengthNode create() {
        return InputLengthNodeGen.create();
    }

    public abstract int execute(Object input);

    @Specialization
    static int getLength(String input) {
        return input.length();
    }

    @Specialization(guards = "inputs.hasArrayElements(input)", limit = "2")
    static int doBoxedCharArray(Object input,
                    @CachedLibrary("input") InteropLibrary inputs) {
        try {
            long length = inputs.getArraySize(input);
            if (length > Integer.MAX_VALUE) {
                CompilerDirectives.transferToInterpreter();
                // should never be reached
                throw new RuntimeException("should not reach here");
            }
            return (int) length;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            // should never be reached
            throw new RuntimeException(e);
        }
    }
}
