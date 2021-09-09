/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.api;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public class ByteArrayBuffer implements TruffleObject {
    private final byte[] data;
    private final int offset;
    private final int length;

    public ByteArrayBuffer(byte[] data, int offset, int length) {
        assert offset >= 0;
        assert length >= 0;
        assert data.length >= offset + length;
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    public long getArraySize() {
        return length;
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index >= 0 && index < getArraySize();
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementModifiable(long index) {
        return false;
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementInsertable(long index) {
        return false;
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    public Object readArrayElement(long index,
                    @Cached BranchProfile errorBranch) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            errorBranch.enter();
            throw InvalidArrayIndexException.create(index);
        }
        return data[offset + (int) index];
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    @TruffleBoundary
    final void writeArrayElement(long index, Object value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
