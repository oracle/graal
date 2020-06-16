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
package com.oracle.truffle.llvm.runtime.pointer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;

/**
 * Represents a raw pointer to the native heap.
 *
 * Important: Java type checks or cast for the pointer interface types will not work because all
 * interfaces are implemented by a single implementation class for efficiency reasons. Use the
 * static methods {@link #isInstance} and {@link #cast} instead.
 *
 * All nodes that use specializations on pointer interfaces need to extend from {@link LLVMNode}, or
 * at least use the {@link LLVMTypes} type system.
 */
public interface LLVMNativePointer extends LLVMPointer {

    /**
     * Get the native address of this pointer.
     */
    long asNative();

    @Override
    LLVMNativePointer copy();

    @Override
    @Deprecated
    @SuppressWarnings("deprecation")
    boolean equals(Object obj);

    /**
     * Increment this pointer. The result is determined by simply adding the offset to the
     * {@link #asNative} address.
     *
     * The {@link #getExportType export type} of the result pointer is reset to {@code null}.
     */
    @Override
    LLVMNativePointer increment(long offset);

    @Override
    LLVMNativePointer export(LLVMInteropType newType);

    /**
     * Create a null pointer.
     */
    static LLVMNativePointer createNull() {
        if (CompilerDirectives.inCompiledCode()) {
            return new LLVMPointerImpl(null, 0, null);
        } else {
            return LLVMPointerImpl.NULL;
        }
    }

    /**
     * Create a native pointer.
     */
    static LLVMNativePointer create(long ptr) {
        return new LLVMPointerImpl(null, ptr, null);
    }

    /**
     * Check whether an object is a {@link LLVMNativePointer}. This method must be used instead of
     * the regular Java {@code instanceof} operator.
     */
    static boolean isInstance(Object object) {
        if (object instanceof LLVMPointerImpl) {
            return ((LLVMPointerImpl) object).isNative();
        } else {
            return false;
        }
    }

    /**
     * Cast an object to a {@link LLVMNativePointer}. This method must be used instead of the
     * regular Java typecast operator.
     */
    static LLVMNativePointer cast(Object object) {
        assert isInstance(object);
        return (LLVMPointerImpl) object;
    }
}
