/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.types;

import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;

public interface Type {

    default LLVMBaseType getLLVMBaseType() {
        throw new AssertionError("Cannot resolve to LLVMBaseType: " + this);
    }

    default int getIndexOffset(@SuppressWarnings("unused") int index, @SuppressWarnings("unused") DataSpecConverter targetDataLayout) {
        throw new UnsupportedOperationException("Cannot index Type: " + this);
    }

    default Type getIndexType(@SuppressWarnings("unused") int index) {
        throw new UnsupportedOperationException("Cannot index Type: " + this);
    }

    default FrameSlotKind getFrameSlotKind() {
        return FrameSlotKind.Object;
    }

    default LLVMFunctionDescriptor.LLVMRuntimeType getRuntimeType() {
        throw new UnsupportedOperationException("Cannot resolve to Runtime Type: " + this);
    }

    default Type getType() {
        return this;
    }

    /**
     * This returns the bitlength of atomic types like integers or floats without consideration for
     * alignment. To get the actual in-memory size of a type use {@link #getSize(DataSpecConverter)}
     * .
     *
     * @return The bitwidth of this atomic type
     */
    default int getBits() {
        throw new UnsupportedOperationException("Not implemented for this Type: " + this);
    }

    default int getAlignment(@SuppressWarnings("unused") DataSpecConverter targetDataLayout) {
        throw new UnsupportedOperationException("Not implemented!");
    }

    default int getSize(@SuppressWarnings("unused") DataSpecConverter targetDataLayout) {
        throw new UnsupportedOperationException("Not implemented!");
    }

    static int getPadding(int offset, int alignment) {
        if (alignment == 0) {
            throw new AssertionError();
        }
        return (alignment - (offset % alignment)) % alignment;
    }

    static int getPadding(int offset, Type type, DataSpecConverter targetDataLayout) {
        final int alignment = type.getAlignment(targetDataLayout);
        return alignment == 0 ? 0 : getPadding(offset, alignment);
    }
}
