/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime.dispatch.messages;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.StopIterationException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
public final class ArrayIterator implements TruffleObject {

    final Object array;
    private long currentItemIndex;

    public ArrayIterator(Object array) {
        this.array = array;
        assert InteropLibrary.getUncached().hasArrayElements(array) : "Array must have array elements.";
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isIterator() {
        return true;
    }

    @ExportMessage
    boolean hasIteratorNextElement(@CachedLibrary("this.array") InteropLibrary arrays) {
        try {
            return currentItemIndex < arrays.getArraySize(array);
        } catch (UnsupportedMessageException ume) {
            throw CompilerDirectives.shouldNotReachHere(ume);
        }
    }

    @ExportMessage
    Object getIteratorNextElement(@CachedLibrary("this.array") InteropLibrary arrays) throws UnsupportedMessageException, StopIterationException {
        try {
            long size = arrays.getArraySize(array);
            if (currentItemIndex >= size) {
                throw StopIterationException.create();
            }
            Object res = arrays.readArrayElement(array, currentItemIndex);
            currentItemIndex++;
            return res;
        } catch (UnsupportedMessageException ume) {
            throw CompilerDirectives.shouldNotReachHere(ume);
        } catch (InvalidArrayIndexException iaie) {
            throw UnsupportedMessageException.create();
        }
    }
}
