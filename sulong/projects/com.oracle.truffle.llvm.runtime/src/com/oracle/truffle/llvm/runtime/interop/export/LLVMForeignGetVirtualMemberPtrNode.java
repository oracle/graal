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
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

import java.util.List;
import org.graalvm.collections.Pair;

@GenerateUncached
public abstract class LLVMForeignGetVirtualMemberPtrNode extends LLVMNode {

    public abstract LLVMPointer execute(LLVMPointer receiver, LLVMInteropType.StructMember structMember, List<Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance>> accessList)
                    throws UnsupportedMessageException, UnknownIdentifierException;

    @Specialization
    public LLVMPointer doResolve(LLVMPointer receiver, LLVMInteropType.StructMember structMember, List<Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance>> accessList,
                    @Cached LLVMForeignReadNode read) {
        LLVMPointer curReceiver = receiver;
        for (Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance> p : accessList) {
            if (p.getRight().virtual) {
                Object vtablePointer = read.execute(curReceiver, LLVMInteropType.ValueKind.POINTER.type);
                LLVMPointer parent = LLVMPointer.cast(vtablePointer).increment(-p.getRight().offset);
                Object parentOffset = read.execute(parent, LLVMInteropType.ValueKind.I64.type);
                curReceiver = curReceiver.increment((long) parentOffset);
            } else {
                curReceiver = curReceiver.increment(p.getLeft().startOffset);
            }
        }
        LLVMPointer elemPtr = curReceiver.increment(structMember.startOffset).export(structMember.type);
        return elemPtr;

    }

}
