/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Idempotent;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypes;

/**
 * Common base interface for all pointer representations. An {@link LLVMPointer} is either a
 * {@link LLVMNativePointer} or a {@link LLVMManagedPointer}.
 *
 * Important: Java type checks or casts for the pointer interface type will not work because all
 * interfaces are implemented by a single implementation class for efficiency reasons. Use the
 * static methods {@link #isInstance} and {@link #cast} instead.
 *
 * All nodes that use specializations on pointer interfaces need to extend from {@link LLVMNode}, or
 * at least use the {@link LLVMTypes} type system.
 */
public interface LLVMPointer extends TruffleObject {

    /**
     * Check whether this pointer is null.
     */
    @Idempotent
    boolean isNull();

    /**
     * Create an exact copy of this pointer. This method should be used whenever a pointer is read
     * from or written to the Java heap, to help escape analysis.
     *
     * Note that {@link LLVMPointer} and its sub-interfaces are considered {@link ValueType}, so
     * reference comparison with {@code ==} is undefined. Therefore, depending on compiler
     * optimizations, {@link #copy} might not really create a new instance.
     */
    LLVMPointer copy();

    /**
     * Increment this pointer. The {@link #getExportType export type} of the result pointer is reset
     * to {@code null}.
     */
    LLVMPointer increment(long offset);

    /**
     * Get the {@link LLVMInteropType} of this pointer. This type is used to determine access
     * semantics from other languages.
     */
    LLVMInteropType getExportType();

    /**
     * Create a copy of this pointer with a new {@link LLVMInteropType}.
     */
    LLVMPointer export(LLVMInteropType newType);

    /**
     * Check whether two pointers refer to the same target. For managed pointers, this will do a
     * shallow comparison. If both pointers point to the different managed objects that are wrappers
     * for the same underlying object, this will return false.
     */
    boolean isSame(LLVMPointer other);

    /**
     * Deep equality comparison.
     *
     * @deprecated Should not be used!
     */
    @Override
    @Deprecated
    boolean equals(Object obj);

    /**
     * Check whether an object is a {@link LLVMPointer}. This method must be used instead of the
     * regular Java {@code instanceof} operator.
     */
    static boolean isInstance(Object object) {
        return object instanceof LLVMPointerImpl;
    }

    /**
     * Cast an object to a {@link LLVMPointer}. This method must be used instead of the regular Java
     * typecast operator.
     */
    static LLVMPointer cast(Object object) {
        return (LLVMPointerImpl) object;
    }
}
