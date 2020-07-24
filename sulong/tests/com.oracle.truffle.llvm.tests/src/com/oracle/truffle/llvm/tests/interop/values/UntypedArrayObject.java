/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop.values;

import java.lang.reflect.Array;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("static-method")
public abstract class UntypedArrayObject<T> implements TruffleObject {

    private final Object array;

    protected UntypedArrayObject(Object array) {
        this.array = array;
    }

    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    boolean inBounds(long idx) {
        return 0 <= idx && idx < getArraySize();
    }

    @ExportMessage
    boolean isArrayElementInsertable(@SuppressWarnings("unused") long idx) {
        return false;
    }

    @ExportMessage(name = "readArrayElement")
    public Object get(long i) throws InvalidArrayIndexException {
        if (!inBounds(i)) {
            throw InvalidArrayIndexException.create(i);
        }
        return Array.get(array, (int) i);
    }

    @ExportMessage(limit = "3")
    void writeArrayElement(long idx, Object value,
                    @CachedLibrary("value") InteropLibrary interop) throws InvalidArrayIndexException, UnsupportedMessageException {
        if (!inBounds(idx)) {
            throw InvalidArrayIndexException.create(idx);
        }
        Array.set(array, (int) idx, convertValue(value, interop));
    }

    protected abstract T convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException;

    @ExportMessage
    long getArraySize() {
        return Array.getLength(array);
    }

    public static final class UntypedByteArrayObject extends UntypedArrayObject<Byte> {

        public UntypedByteArrayObject(byte... args) {
            super(args);
        }

        @Override
        protected Byte convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asByte(value);
        }
    }

    public static final class UntypedFloatArrayObject extends UntypedArrayObject<Float> {
        public UntypedFloatArrayObject(float... args) {
            super(args);
        }

        @Override
        protected Float convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asFloat(value);
        }
    }

    public static final class UntypedIntegerArrayObject extends UntypedArrayObject<Integer> {
        public UntypedIntegerArrayObject(int... args) {
            super(args);
        }

        @Override
        protected Integer convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asInt(value);
        }
    }

    public static final class UntypedLongArrayObject extends UntypedArrayObject<Long> {
        public UntypedLongArrayObject(long... args) {
            super(args);
        }

        @Override
        protected Long convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asLong(value);
        }
    }

    public static final class UntypedDoubleArrayObject extends UntypedArrayObject<Double> {
        public UntypedDoubleArrayObject(double... args) {
            super(args);
        }

        @Override
        protected Double convertValue(Object value, InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asDouble(value);
        }
    }
}
