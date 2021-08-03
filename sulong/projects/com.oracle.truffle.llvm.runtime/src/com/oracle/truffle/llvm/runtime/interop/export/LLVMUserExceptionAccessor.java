/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop.export;

import java.util.function.Supplier;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@ExportLibrary(InteropLibrary.class)
public class LLVMUserExceptionAccessor implements TruffleObject {
    final Supplier<LLVMPointer> supplier;
    private LLVMPointer exceptionObject;

    public LLVMUserExceptionAccessor(Supplier<LLVMPointer> supplier) {
        this.supplier = supplier;
        this.exceptionObject = null;
    }

    protected LLVMPointer get() {
        if (exceptionObject == null) {
            this.exceptionObject = supplier.get();
        }
        return exceptionObject;
    }

    @ExportMessage(limit = "3")
    final boolean hasMembers(@CachedLibrary("this.get()") InteropLibrary interop) {
        return interop.hasMembers(get());
    }

    @ExportMessage(limit = "3")
    public Object getMembers(boolean includeInternal,
                    @CachedLibrary("this.get()") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.getMembers(get(), includeInternal);
    }

    @ExportMessage(limit = "3")
    public Object readMember(String ident,
                    @CachedLibrary("this.get()") InteropLibrary interop) throws UnsupportedMessageException, UnknownIdentifierException {
        return interop.readMember(get(), ident);
    }

    @ExportMessage(limit = "3")
    public boolean isMemberInvocable(String ident,
                    @CachedLibrary("this.get()") InteropLibrary interop) {
        return interop.isMemberInvocable(get(), ident);
    }

    @ExportMessage(limit = "3")
    public boolean hasArrayElements(@CachedLibrary("this.get()") InteropLibrary interop) {
        return interop.hasArrayElements(get());
    }

    @ExportMessage(limit = "3")
    public boolean isMemberReadable(String member,
                    @CachedLibrary("this.get()") InteropLibrary interop) {
        return interop.isMemberReadable(get(), member);
    }

    @ExportMessage(limit = "3")
    public Object invokeMember(String member, Object[] arguments,
                    @CachedLibrary("this.get()") InteropLibrary interop) throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
        return interop.invokeMember(get(), member, arguments);
    }

    @ExportMessage(limit = "3")
    public Object readArrayElement(long index,
                    @CachedLibrary("this.get()") InteropLibrary interop) throws UnsupportedMessageException, InvalidArrayIndexException {
        return interop.readArrayElement(get(), index);
    }

    @ExportMessage(limit = "3")
    public long getArraySize(@CachedLibrary("this.get()") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.getArraySize(get());
    }

    @ExportMessage(limit = "3")
    public boolean isArrayElementReadable(long index,
                    @CachedLibrary("this.get()") InteropLibrary interop) {
        return interop.isArrayElementReadable(get(), index);
    }

}
