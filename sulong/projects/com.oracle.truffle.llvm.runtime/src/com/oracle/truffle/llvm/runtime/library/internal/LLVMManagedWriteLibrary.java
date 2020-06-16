/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.library.internal;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.GenerateLibrary.Abstract;
import com.oracle.truffle.api.library.GenerateLibrary.DefaultExport;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Library for container objects that behave like raw memory that can be written.
 */
@GenerateLibrary
@DefaultExport(LLVMManagedAccessDefaults.VirtualAlloc.class)
@DefaultExport(LLVMManagedAccessDefaults.FallbackWrite.class)
public abstract class LLVMManagedWriteLibrary extends Library {

    @Abstract
    public boolean isWritable(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Write one byte.
     */
    public abstract void writeI8(Object receiver, long offset, byte value);

    /**
     * Write two bytes.
     */
    public abstract void writeI16(Object receiver, long offset, short value);

    /**
     * Write four bytes.
     */
    public abstract void writeI32(Object receiver, long offset, int value);

    /**
     * Write four bytes.
     */
    public void writeFloat(Object receiver, long offset, float value) {
        writeI32(receiver, offset, Float.floatToRawIntBits(value));
    }

    /**
     * Write eight bytes.
     */
    public void writeI64(Object receiver, long offset, long value) {
        writeGenericI64(receiver, offset, value);
    }

    /**
     * Write eight bytes.
     */
    public abstract void writeGenericI64(Object receiver, long offset, Object value);

    /**
     * Write eight bytes.
     */
    public void writeDouble(Object receiver, long offset, double value) {
        writeI64(receiver, offset, Double.doubleToRawLongBits(value));
    }

    /**
     * Write eight bytes.
     */
    public void writePointer(Object receiver, long offset, LLVMPointer value) {
        writeGenericI64(receiver, offset, value);
    }

    private static final LibraryFactory<LLVMManagedWriteLibrary> FACTORY = LibraryFactory.resolve(LLVMManagedWriteLibrary.class);

    public static LibraryFactory<LLVMManagedWriteLibrary> getFactory() {
        return FACTORY;
    }
}
