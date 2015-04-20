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
package com.oracle.truffle.interop;

import com.oracle.truffle.api.*;

public final class ForeignAccessArguments {
    public static final Object[] EMPTY_ARGUMENTS_ARRAY = new Object[0];
    public static final int RECEIVER_INDEX = 0;
    public static final int RUNTIME_ARGUMENT_COUNT = 1;

    public static Object[] create(Object receiver) {
        return new Object[]{receiver};
    }

    public static Object[] create(Object receiver, Object[] arguments) {
        Object[] objectArguments = new Object[RUNTIME_ARGUMENT_COUNT + arguments.length];
        objectArguments[RECEIVER_INDEX] = receiver;
        arraycopy(arguments, 0, objectArguments, RUNTIME_ARGUMENT_COUNT, arguments.length);
        return objectArguments;
    }

    public static Object getArgument(Object[] arguments, int index) {
        return arguments[RUNTIME_ARGUMENT_COUNT + index];
    }

    public static Object getReceiver(Object[] arguments) {
        return arguments[RECEIVER_INDEX];
    }

    public static Object[] extractUserArguments(Object[] arguments) {
        return copyOfRange(arguments, RUNTIME_ARGUMENT_COUNT, arguments.length);
    }

    public static int getUserArgumentCount(Object[] arguments) {
        return arguments.length - RUNTIME_ARGUMENT_COUNT;
    }

    private static Object[] copyOfRange(Object[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalArgumentException(from + " > " + to);
        }
        Object[] copy = new Object[newLength];
        arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
        return copy;
    }

    private static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        for (int i = 0; i < length; i++) {
            dest[destPos + i] = src[srcPos + i];
        }
    }

}
