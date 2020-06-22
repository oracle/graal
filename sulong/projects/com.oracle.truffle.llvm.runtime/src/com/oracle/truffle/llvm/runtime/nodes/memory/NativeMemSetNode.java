/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

public abstract class NativeMemSetNode extends LLVMMemSetNode {

    protected static final long MAX_JAVA_LEN = 256;

    @CompilationFinal private boolean inJava = true;

    @Specialization
    protected void memset(LLVMNativePointer address, byte value, long length,
                    @CachedLanguage LLVMLanguage language) {
        LLVMMemory memory = language.getLLVMMemory();
        if (inJava) {
            if (length <= MAX_JAVA_LEN) {
                long current = address.asNative();
                long i64ValuesToWrite = length >> 3;
                if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i64ValuesToWrite > 0)) {
                    long v16 = ((long) value) << 8 | ((long) value & 0xFF);
                    long v32 = v16 << 16 | v16;
                    long v64 = v32 << 32 | v32;

                    for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i64ValuesToWrite); i++) {
                        memory.putI64(this, current, v64);
                        current += 8;
                    }
                }

                long i8ValuesToWrite = length & 0x07;
                for (long i = 0; CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i < i8ValuesToWrite); i++) {
                    memory.putI8(this, current, value);
                    current++;
                }
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                inJava = false;
            }
        }

        nativeMemSet(memory, address, value, length);
    }

    @SuppressWarnings("deprecation")
    private void nativeMemSet(LLVMMemory memory, LLVMNativePointer address, byte value, long length) {
        memory.memset(this, address, length, value);
    }

    long getAccessLength(LLVMManagedPointer pointer, long length, NativeTypeLibrary nativeTypes) {
        Object object = pointer.getObject();
        Object type = nativeTypes.getNativeType(object);
        if (type instanceof LLVMInteropType.Array) {
            long elementSize = ((LLVMInteropType.Array) type).getElementSize();
            if (length % elementSize == 0) {
                return elementSize;
            }
        }

        /*
         * Fallback to byte-wise copy if either the type is unknown, not an array, or the length is
         * not a multiple of the array element size.
         */
        return 1;
    }

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 1"})
    protected void memsetManagedI8(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary("object.getObject()") NativeTypeLibrary nativeTypes,
                    @CachedLibrary("object.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        for (int i = 0; i < length; i++) {
            nativeWrite.writeI8(object.getObject(), object.getOffset() + i, value);
        }
    }

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 2"})
    protected void memsetManagedI16(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary("object.getObject()") NativeTypeLibrary nativeTypes,
                    @CachedLibrary("object.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        int bValue = value & 0xFF;
        int sValue = (bValue << 8) | bValue;
        for (int i = 0; i < length; i += 2) {
            nativeWrite.writeI16(object.getObject(), object.getOffset() + i, (short) sValue);
        }
    }

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 4"})
    protected void memsetManagedI32(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary("object.getObject()") NativeTypeLibrary nativeTypes,
                    @CachedLibrary("object.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        int bValue = value & 0xFF;
        int sValue = (bValue << 8) | bValue;
        int iValue = (sValue << 16) | sValue;
        for (int i = 0; i < length; i += 4) {
            nativeWrite.writeI32(object.getObject(), object.getOffset() + i, iValue);
        }
    }

    @Specialization(limit = "3", guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 8"})
    protected void memsetManagedI64(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary("object.getObject()") NativeTypeLibrary nativeTypes,
                    @CachedLibrary("object.getObject()") LLVMManagedWriteLibrary nativeWrite) {
        long bValue = value & 0xFFL;
        long sValue = (bValue << 8) | bValue;
        long iValue = (sValue << 16) | sValue;
        long lValue = (iValue << 32) | iValue;
        for (int i = 0; i < length; i += 8) {
            nativeWrite.writeI64(object.getObject(), object.getOffset() + i, lValue);
        }
    }

    @Specialization(limit = "3", guards = {"!nativeWrite.isWritable(object.getObject())"})
    protected void memset(LLVMManagedPointer object, byte value, long length,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @SuppressWarnings("unused") @CachedLibrary("object.getObject()") LLVMManagedWriteLibrary nativeWrite,
                    @CachedLanguage LLVMLanguage language) {
        memset(globalAccess.executeWithTarget(object), value, length, language);
    }
}
