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
public final class HashIterator implements TruffleObject {

    final Object entriesIterator;
    private final long index;

    private HashIterator(Object entriesIterator, long index) {
        assert InteropLibrary.getUncached().isIterator(entriesIterator) : "EntriesIterator must be an iterator.";
        assert index >= 0 && index < 2;
        this.entriesIterator = entriesIterator;
        this.index = index;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isIterator() {
        return true;
    }

    @ExportMessage
    boolean hasIteratorNextElement(@CachedLibrary("this.entriesIterator") InteropLibrary iterators) {
        try {
            return iterators.hasIteratorNextElement(entriesIterator);
        } catch (UnsupportedMessageException ume) {
            throw CompilerDirectives.shouldNotReachHere(ume);
        }
    }

    @ExportMessage
    Object getIteratorNextElement(
                    @CachedLibrary("this.entriesIterator") InteropLibrary iterators,
                    @CachedLibrary(limit = "1") InteropLibrary arrays) throws UnsupportedMessageException, StopIterationException {
        try {
            Object entry = iterators.getIteratorNextElement(entriesIterator);
            return arrays.readArrayElement(entry, index);
        } catch (InvalidArrayIndexException e) {
            throw CompilerDirectives.shouldNotReachHere("Hash entry must have two array elements.", e);
        }
    }

    public static HashIterator keys(Object entriesIterator) {
        return new HashIterator(entriesIterator, 0);
    }

    public static HashIterator values(Object entriesIterator) {
        return new HashIterator(entriesIterator, 1);
    }
}
