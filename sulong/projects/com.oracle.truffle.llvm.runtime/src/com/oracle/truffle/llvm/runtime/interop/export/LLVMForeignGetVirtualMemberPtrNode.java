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

package com.oracle.truffle.llvm.runtime.interop.export;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignGetVirtualMemberPtrNode extends LLVMNode {

    public abstract LLVMPointer execute(LLVMPointer receiver, LLVMInteropType.StructMember structMember, LLVMInteropType type) throws UnsupportedMessageException, UnknownIdentifierException;

    @Specialization
    public LLVMPointer doResolve(LLVMPointer receiver, LLVMInteropType.StructMember structMember, LLVMInteropType.Clazz clazz, @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMInteropType.StructMember vtable = clazz.findMember(0);
        Object o = interop.readMember(receiver, vtable.name);
        while (vtable.type instanceof LLVMInteropType.Clazz) {
            vtable = ((LLVMInteropType.Clazz) vtable.type).findMember(0);
            o = interop.readMember(o, vtable.name);
        }// o == vtable address
        if (LLVMPointer.isInstance(o)) {
            // long int this_offset = tableAddr[-24 Bytes]
            LLVMPointer thisOffsetElementPtr = LLVMPointer.cast(o).increment(-24);
            Object thisOffsetObj = read.execute(thisOffsetElementPtr, LLVMInteropType.ValueKind.I64.type);
            // void* base = (byte*) &derived + this_offset
            if (thisOffsetObj instanceof Long) {
                Object basePtr = receiver.increment((long) thisOffsetObj);
                // return &(base[fieldOffset])
                LLVMPointer elemPtr = LLVMPointer.cast(basePtr).increment(structMember.startOffset).export(structMember.type);
                return elemPtr;
            }
        }
        throw UnsupportedMessageException.create();
    }

}
