/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * Translation from an array of {@link InteropLibrary#isMember(java.lang.Object) members} to an
 * array of {@link InteropLibrary#isString(java.lang.Object) strings}.
 */
@ExportLibrary(InteropLibrary.class)
class MembersToStrings implements TruffleObject {

    final Object members;

    MembersToStrings(Object members) {
        this.members = members;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    final boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    final long getArraySize(@CachedLibrary("this.members") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.getArraySize(members);
    }

    @ExportMessage
    final boolean isArrayElementReadable(long index, //
                    @CachedLibrary("this.members") InteropLibrary interop) {
        return interop.isArrayElementReadable(members, index);
    }

    @ExportMessage
    final Object readArrayElement(long index, //
                    @CachedLibrary("this.members") InteropLibrary interop,
                    @CachedLibrary(limit = "3") InteropLibrary memberInterop) throws UnsupportedMessageException, InvalidArrayIndexException {
        return memberInterop.getMemberSimpleName(interop.readArrayElement(members, index));
    }

    @ExportMessage
    final boolean isArrayElementRemovable(long index, //
                    @CachedLibrary("this.members") InteropLibrary interop) {
        return interop.isArrayElementRemovable(members, index);
    }

    @ExportMessage
    final void removeArrayElement(long index, //
                    @CachedLibrary("this.members") InteropLibrary interop) throws UnsupportedMessageException, InvalidArrayIndexException {
        interop.removeArrayElement(members, index);
    }
}
