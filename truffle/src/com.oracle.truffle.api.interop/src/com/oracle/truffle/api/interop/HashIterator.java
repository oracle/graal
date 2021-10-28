/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
final class HashIterator implements TruffleObject {

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

    static HashIterator keys(Object entriesIterator) {
        return new HashIterator(entriesIterator, 0);
    }

    static HashIterator values(Object entriesIterator) {
        return new HashIterator(entriesIterator, 1);
    }
}
