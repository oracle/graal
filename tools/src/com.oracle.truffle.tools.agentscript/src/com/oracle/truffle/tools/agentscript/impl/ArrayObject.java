/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import java.util.Collection;

@SuppressWarnings({"static-method"})
@ExportLibrary(InteropLibrary.class)
final class ArrayObject implements TruffleObject {
    private final Object[] arr;
    private final boolean convertToString;

    private ArrayObject(Object[] arr, boolean convertToString) {
        this.arr = arr;
        this.convertToString = convertToString;
    }

    @ExportMessage
    Object readArrayElement(long index) {
        Object value = arr[(int) index];
        if (convertToString) {
            return toString(value);
        } else {
            return value;
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static String toString(Object value) {
        return value.toString();
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @SuppressWarnings("unused")
    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return true;
    }

    @ExportMessage
    long getArraySize() {
        return arr.length;
    }

    @ExplodeLoop
    boolean contains(String name) {
        if (name == null) {
            return false;
        }
        for (int i = 0; i < arr.length; i++) {
            if (name.equals(readArrayElement(i))) {
                return true;
            }
        }
        return false;
    }

    static ArrayObject array(String... arr) {
        return new ArrayObject(arr, false);
    }

    static ArrayObject wrap(Collection<?> arr) {
        return new ArrayObject(arr.toArray(), false);
    }

    static ArrayObject wrap(Enum<?>[] arr) {
        return new ArrayObject(arr, true);
    }
}
