/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.debug;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;

@ExportLibrary(InteropLibrary.class)
public abstract class LLVMDebuggerValue implements TruffleObject {

    protected static final String[] NO_KEYS = new String[0];

    protected abstract int getElementCountForDebugger();

    protected abstract String[] getKeysForDebugger();

    protected abstract Object getElementForDebugger(String key);

    @SuppressWarnings("static-method")
    @ExportMessage
    public final boolean hasMembers() {
        return true;
    }

    @TruffleBoundary
    public Object resolveMetaObject() {
        InteropLibrary debuggerInterop = InteropLibrary.getFactory().getUncached(this);
        try {
            return debuggerInterop.hasMetaObject(this) ? debuggerInterop.getMetaObject(this) : null;
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected unsupported message.", e);
        }
    }

    @ExportMessage
    @TruffleBoundary
    public final Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        if (getElementCountForDebugger() == 0) {
            return SubElements.EMPTY;
        }

        String[] keys = getKeysForDebugger();
        return new SubElements(keys);
    }

    @ExportMessage
    @TruffleBoundary
    public final boolean isMemberReadable(String key) {
        Object element = getElementForDebugger(key);
        return element != null;
    }

    @ExportMessage
    @TruffleBoundary
    public final Object readMember(String key,
                    @Cached BranchProfile exception) throws UnknownIdentifierException {
        Object element = getElementForDebugger(key);
        if (element != null) {
            return element;
        } else {
            exception.enter();
            throw UnknownIdentifierException.create(key);
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class SubElements implements TruffleObject {

        private static final SubElements EMPTY = new SubElements(LLVMDebuggerValue.NO_KEYS);

        private final String[] keys;

        SubElements(String[] keys) {
            this.keys = keys;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean hasArrayElements() {
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return keys.length;
        }

        @ExportMessage
        boolean isArrayElementReadable(long idx) {
            return Long.compareUnsigned(idx, keys.length) < 0;
        }

        @ExportMessage
        String readArrayElement(long idx,
                        @Cached BranchProfile exception) throws InvalidArrayIndexException {
            if (isArrayElementReadable(idx)) {
                return keys[(int) idx];
            } else {
                exception.enter();
                throw InvalidArrayIndexException.create(idx);
            }
        }
    }

    @ExportMessage
    @SuppressWarnings({"unused", "static-method"})
    public final boolean hasLanguage() {
        return true;
    }

    @ExportMessage
    @SuppressWarnings({"static-method"})
    public final Class<? extends TruffleLanguage<?>> getLanguage() {
        return LLVMLanguage.class;
    }

    @ExportMessage
    @TruffleBoundary
    public final String toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return toString();
    }
}
