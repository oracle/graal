/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.library.LLVMNativeLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

/**
 * This object is used to wrap primitive values that enter LLVM code via interop at points where a
 * pointer is expected. Semantically this behaves similar to pointer tagging. The resulting pointer
 * can not be dereferenced, and in pointer comparisons, the same primitive value will result in the
 * same pointer value.
 */
@ExportLibrary(LLVMNativeLibrary.class)
@ExportLibrary(InteropLibrary.class)
public final class LLVMBoxedPrimitive implements TruffleObject {

    final Object value;

    public LLVMBoxedPrimitive(Object value) {
        this.value = value;
    }

    public Object getValue() {
        return value;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "<boxed value: " + getValue() + ">";
    }

    @SuppressWarnings("static-method")
    @ExportMessage(library = LLVMNativeLibrary.class)
    boolean isPointer() {
        return true;
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    long asPointer(@Shared("toLLVM") @Cached ToLLVM toLLVM) {
        return (long) toLLVM.executeWithType(getValue(), null, ForeignToLLVMType.I64);
    }

    @ExportMessage(library = LLVMNativeLibrary.class)
    LLVMNativePointer toNativePointer(@Shared("toLLVM") @Cached ToLLVM toLLVM) {
        return LLVMNativePointer.create(asPointer(toLLVM));
    }

    @ExportMessage
    boolean isBoolean(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.isBoolean(value);
    }

    @ExportMessage
    boolean asBoolean(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asBoolean(value);
    }

    @ExportMessage
    boolean isNumber(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.isNumber(value);
    }

    @ExportMessage
    boolean fitsInByte(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInByte(value);
    }

    @ExportMessage
    boolean fitsInShort(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInShort(value);
    }

    @ExportMessage
    boolean fitsInInt(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInInt(value);
    }

    @ExportMessage
    boolean fitsInLong(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInLong(value);
    }

    @ExportMessage
    boolean fitsInFloat(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInFloat(value);
    }

    @ExportMessage
    boolean fitsInDouble(@CachedLibrary("this.value") InteropLibrary interop) {
        return interop.fitsInDouble(value);
    }

    @ExportMessage
    byte asByte(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asByte(value);
    }

    @ExportMessage
    short asShort(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asShort(value);
    }

    @ExportMessage
    int asInt(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asInt(value);
    }

    @ExportMessage
    long asLong(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asLong(value);
    }

    @ExportMessage
    float asFloat(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asFloat(value);
    }

    @ExportMessage
    double asDouble(@CachedLibrary("this.value") InteropLibrary interop) throws UnsupportedMessageException {
        return interop.asDouble(value);
    }
}
