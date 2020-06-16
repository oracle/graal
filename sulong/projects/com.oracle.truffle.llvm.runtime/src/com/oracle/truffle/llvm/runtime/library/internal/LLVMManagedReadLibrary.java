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
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Library for container objects that behave like raw memory that can be read.
 */
@GenerateLibrary
@DefaultExport(LLVMManagedAccessDefaults.VirtualAlloc.class)
@DefaultExport(LLVMManagedAccessDefaults.FallbackRead.class)
public abstract class LLVMManagedReadLibrary extends Library {

    @Abstract
    public boolean isReadable(@SuppressWarnings("unused") Object receiver) {
        return false;
    }

    /**
     * Read one byte, and interpret the result as an integer.
     */
    public abstract byte readI8(Object receiver, long offset);

    /**
     * Read two bytes, and interpret the result as an integer.
     */
    public abstract short readI16(Object receiver, long offset);

    /**
     * Read four bytes, and interpret the result as an integer.
     */
    public abstract int readI32(Object receiver, long offset);

    /**
     * Read four bytes, and interpret the result as a floating point number.
     */
    public float readFloat(Object receiver, long offset) {
        return Float.intBitsToFloat(readI32(receiver, offset));
    }

    /**
     * Read eight bytes, and interpret the result as an integer.
     *
     * @throws UnexpectedResultException if the result is not a primitive
     */
    public long readI64(Object receiver, long offset) throws UnexpectedResultException {
        return LLVMTypesGen.expectLong(readGenericI64(receiver, offset));
    }

    /**
     * Read eight bytes, and interpret the result as a floating point number.
     */
    public double readDouble(Object receiver, long offset) {
        try {
            return Double.longBitsToDouble(readI64(receiver, offset));
        } catch (UnexpectedResultException e) {
            return (double) e.getResult();
        }
    }

    /**
     * Read eight bytes, and interpret the result as a pointer.
     */
    public abstract LLVMPointer readPointer(Object receiver, long offset);

    /**
     * Read eight bytes. The return value can be either a primitive or a pointer.
     */
    public abstract Object readGenericI64(Object receiver, long offset);

    private static final LibraryFactory<LLVMManagedReadLibrary> FACTORY = LibraryFactory.resolve(LLVMManagedReadLibrary.class);

    public static LibraryFactory<LLVMManagedReadLibrary> getFactory() {
        return FACTORY;
    }
}
