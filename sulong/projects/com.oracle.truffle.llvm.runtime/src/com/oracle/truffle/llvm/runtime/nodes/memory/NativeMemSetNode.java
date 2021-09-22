/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Struct;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.StructMember;
import com.oracle.truffle.llvm.runtime.library.internal.LLVMManagedWriteLibrary;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.spi.NativeTypeLibrary;

@GenerateUncached
public abstract class NativeMemSetNode extends LLVMMemSetNode {
    static long getAccessLength(LLVMManagedPointer pointer, long length, NativeTypeLibrary nativeTypes) {
        Object object = pointer.getObject();
        Object type = nativeTypes.getNativeType(object);
        if (type instanceof LLVMInteropType.Array) {
            long elementSize = ((LLVMInteropType.Array) type).elementSize;
            if (length % elementSize == 0) {
                return elementSize;
            }
        }
        if (type instanceof LLVMInteropType.Struct) {
            StructMember member = findMember((Struct) type, pointer.getOffset());
            /*
             * That's a bit of a guess: We assume that this tries to set 'n' members of the same
             * size. So, we just take the size of the first member as the access length. If that
             * isn't true, we will fail afterwards when doing actual access.
             */
            if (member != null && member.type instanceof LLVMInteropType.Value && length % member.type.getSize() == 0) {
                return member.type.getSize();
            }
        }

        /*
         * Fallback to byte-wise copy if either the type is unknown, not an array, or the length is
         * not a multiple of the array element size.
         */
        return 1;
    }

    private static StructMember findMember(LLVMInteropType.Struct struct, long offset) {
        for (int i = 0; i < struct.getMemberCount(); i++) {
            StructMember m = struct.getMember(i);
            if (m.startOffset == offset) {
                return m;
            }
        }
        return null;
    }

    @Specialization(guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 1"})
    protected void memsetManagedI8(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        for (int i = 0; i < length; i++) {
            nativeWrite.writeI8(object.getObject(), object.getOffset() + i, value);
        }
    }

    @Specialization(guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 2"})
    protected void memsetManagedI16(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        int bValue = value & 0xFF;
        int sValue = (bValue << 8) | bValue;
        for (int i = 0; i < length; i += 2) {
            nativeWrite.writeI16(object.getObject(), object.getOffset() + i, (short) sValue);
        }
    }

    @Specialization(guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 4"})
    protected void memsetManagedI32(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        int bValue = value & 0xFF;
        int sValue = (bValue << 8) | bValue;
        int iValue = (sValue << 16) | sValue;
        for (int i = 0; i < length; i += 4) {
            nativeWrite.writeI32(object.getObject(), object.getOffset() + i, iValue);
        }
    }

    @Specialization(guards = {"nativeWrite.isWritable(object.getObject())", "getAccessLength(object, length, nativeTypes) == 8"})
    protected void memsetManagedI64(LLVMManagedPointer object, byte value, long length,
                    @SuppressWarnings("unused") @CachedLibrary(limit = "3") NativeTypeLibrary nativeTypes,
                    @CachedLibrary(limit = "3") LLVMManagedWriteLibrary nativeWrite) {
        long bValue = value & 0xFFL;
        long sValue = (bValue << 8) | bValue;
        long iValue = (sValue << 16) | sValue;
        long lValue = (iValue << 32) | iValue;
        for (int i = 0; i < length; i += 8) {
            nativeWrite.writeI64(object.getObject(), object.getOffset() + i, lValue);
        }
    }

    protected static final long MAX_JAVA_LEN = 256;

    @Specialization(guards = "length <= MAX_JAVA_LEN")
    protected void nativeInJavaMemset(LLVMNativePointer object, byte value, long length,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        LLVMMemory memory = getLanguage().getLLVMMemory();

        long current = globalAccess.executeWithTarget(object).asNative();
        long i64ValuesToWrite = length >> 3;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, i64ValuesToWrite > 0)) {
            long v16 = ((((long) value) & 0xFF) << 8) | ((long) value & 0xFF);
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
    }

    @Specialization(replaces = "nativeInJavaMemset")
    @SuppressWarnings("deprecation")
    protected void nativeMemset(LLVMNativePointer object, byte value, long length,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
        LLVMMemory memory = getLanguage().getLLVMMemory();
        memory.memset(this, globalAccess.executeWithTarget(object), length, value);
    }
}
