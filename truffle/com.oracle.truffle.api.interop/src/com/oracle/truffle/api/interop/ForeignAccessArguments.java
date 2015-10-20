/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

final class ForeignAccessArguments extends Node {

    static final Object[] EMPTY_ARGUMENTS_ARRAY = new Object[0];
    static final int RECEIVER_INDEX = 0;
    static final int RUNTIME_ARGUMENT_COUNT = 1;

    @CompilationFinal private int previousLength = -2;

    public Object[] executeCreate(Object receiver, Object... arguments) {
        int length = profileLength(arguments.length);
        Object[] objectArguments = new Object[RUNTIME_ARGUMENT_COUNT + length];
        objectArguments[RECEIVER_INDEX] = receiver;
        arraycopy(arguments, 0, objectArguments, RUNTIME_ARGUMENT_COUNT, length);
        return objectArguments;
    }

    private int profileLength(int length) {
        int returnLength = length;
        if (previousLength != -1) {
            if (previousLength == length) {
                returnLength = previousLength;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (previousLength == -2) {
                    previousLength = length;
                } else {
                    previousLength = -1;
                }
            }
        }
        return returnLength;
    }

    private static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        for (int i = 0; i < length; i++) {
            dest[destPos + i] = src[srcPos + i];
        }
    }

}
