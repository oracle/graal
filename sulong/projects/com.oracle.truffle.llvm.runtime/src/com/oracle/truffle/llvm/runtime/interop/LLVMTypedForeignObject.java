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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMAsForeignLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedReadLibrary;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@ValueType
@ExportLibrary(value = InteropLibrary.class, delegateTo = "foreign")
@ExportLibrary(LLVMManagedReadLibrary.class)
@ExportLibrary(LLVMManagedWriteLibrary.class)
@ExportLibrary(NativeTypeLibrary.class)
@ExportLibrary(LLVMAsForeignLibrary.class)
public final class LLVMTypedForeignObject extends LLVMInternalTruffleObject {

    final TypedForeignWrapper foreign;

    public static LLVMTypedForeignObject create(Object foreign, LLVMInteropType.Structured type) {
        return new LLVMTypedForeignObject(foreign, type);
    }

    private LLVMTypedForeignObject(Object foreign, LLVMInteropType.Structured type) {
        this.foreign = new TypedForeignWrapper(foreign, type);
    }

    public Object getForeign() {
        return foreign.delegate;
    }

    public LLVMInteropType.Structured getType() {
        return foreign.type;
    }

    @Override
    public boolean equals(Object obj) {
        // ignores the type explicitly
        if (obj instanceof LLVMTypedForeignObject) {
            LLVMTypedForeignObject other = (LLVMTypedForeignObject) obj;
            return foreign.delegate.equals(other.foreign.delegate);
        }
        return foreign.delegate == obj;
    }

    @Override
    public int hashCode() {
        // ignores the type explicitly
        return foreign.delegate.hashCode();
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean hasNativeType(@CachedLibrary("this.foreign.delegate") NativeTypeLibrary nativeTypes) {
        return foreign.hasNativeType(nativeTypes);
    }

    @ExportMessage
    static class GetNativeType {

        @Specialization(guards = "typeLibrary.hasNativeType(object.foreign.delegate)")
        static Object getType(LLVMTypedForeignObject object,
                        @CachedLibrary("object.foreign.delegate") NativeTypeLibrary typeLibrary) {
            return typeLibrary.getNativeType(object.foreign.delegate);
        }

        @Specialization(guards = "!typeLibrary.hasNativeType(object.foreign.delegate)")
        static LLVMInteropType.Structured doFallback(LLVMTypedForeignObject object,
                        @SuppressWarnings("unused") @CachedLibrary("object.foreign.delegate") NativeTypeLibrary typeLibrary) {
            return object.getType();
        }
    }

    @ExportMessage(name = "isReadable")
    @ExportMessage(name = "isWritable")
    @SuppressWarnings("static-method")
    boolean isAccessible() {
        return true;
    }

    @ExportMessage
    byte readI8(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readI8(foreign, offset);
    }

    @ExportMessage
    short readI16(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readI16(foreign, offset);
    }

    @ExportMessage
    int readI32(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readI32(foreign, offset);
    }

    @ExportMessage
    float readFloat(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readFloat(foreign, offset);
    }

    @ExportMessage
    double readDouble(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readDouble(foreign, offset);
    }

    @ExportMessage
    LLVMPointer readPointer(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readPointer(foreign, offset);
    }

    @ExportMessage
    long readI64(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) throws UnexpectedResultException {
        return readLibrary.readI64(foreign, offset);
    }

    @ExportMessage
    Object readGenericI64(long offset, @CachedLibrary("this.foreign") LLVMManagedReadLibrary readLibrary) {
        return readLibrary.readGenericI64(foreign, offset);
    }

    @ExportMessage
    void writeI8(long offset, byte value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeI8(foreign, offset, value);
    }

    @ExportMessage
    void writeI16(long offset, short value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeI16(foreign, offset, value);
    }

    @ExportMessage
    void writeI32(long offset, int value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeI32(foreign, offset, value);
    }

    @ExportMessage
    void writeI64(long offset, long value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeI64(foreign, offset, value);
    }

    @ExportMessage
    void writeFloat(long offset, float value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeFloat(foreign, offset, value);
    }

    @ExportMessage
    public void writeGenericI64(long offset, Object value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeGenericI64(foreign, offset, value);
    }

    @ExportMessage
    public void writeDouble(long offset, double value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writeDouble(foreign, offset, value);
    }

    @ExportMessage
    public void writePointer(long offset, LLVMPointer value, @CachedLibrary("this.foreign") LLVMManagedWriteLibrary writeLibrary) {
        writeLibrary.writePointer(foreign, offset, value);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean isForeign() {
        return true;
    }

    @ExportMessage
    public Object asForeign() {
        return foreign.delegate;
    }

    @ExportLibrary(NativeTypeLibrary.class)
    @ExportLibrary(LLVMAsForeignLibrary.class)
    @ExportLibrary(value = InteropLibrary.class, delegateTo = "delegate")
    public static class TypedForeignWrapper implements TruffleObject {
        final Object delegate;
        final LLVMInteropType.Structured type;

        public TypedForeignWrapper(Object delegate, LLVMInteropType.Structured type) {
            this.delegate = delegate;
            this.type = type;
        }

        @ExportMessage
        public boolean hasNativeType(@CachedLibrary("this.delegate") NativeTypeLibrary nativeTypes) {
            return type != null || nativeTypes.hasNativeType(this.delegate);
        }

        @ExportMessage
        public Object getNativeType(@CachedLibrary("this.delegate") NativeTypeLibrary nativeTypes) {
            if (nativeTypes.hasNativeType(this.delegate)) {
                return nativeTypes.getNativeType(this.delegate);
            } else {
                return type;
            }
        }

        @ExportMessage
        public boolean isForeign() {
            return true;
        }

        @ExportMessage
        public Object asForeign() {
            return delegate;
        }
    }

}
