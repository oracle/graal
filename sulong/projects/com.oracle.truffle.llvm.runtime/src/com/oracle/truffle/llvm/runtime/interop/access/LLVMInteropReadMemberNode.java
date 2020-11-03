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

package com.oracle.truffle.llvm.runtime.interop.access;

import java.util.List;

import org.graalvm.collections.Pair;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignReadNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetVirtualMemberPtrNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropReadMemberNode extends LLVMNode {
    public abstract Object execute(LLVMPointer receiver, String ident, LLVMInteropType exportType) throws UnsupportedMessageException, UnknownIdentifierException;

    @Specialization
    public Object doClazzFieldRead(LLVMPointer receiver, String ident, LLVMInteropType.Clazz clazz, @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached LLVMForeignReadNode read, @Cached LLVMForeignGetMemberPointerNode getMemberPtr, @Cached LLVMForeignGetVirtualMemberPtrNode virtualMemberPtr)
                    throws UnsupportedMessageException, UnknownIdentifierException {
        List<Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance>> list = clazz.getMemberAccessList(ident);
        if (list != null && list.size() > 0) {
            if (list.stream().anyMatch(l -> l.getRight().virtual)) {
                LLVMPointer elemPtr = virtualMemberPtr.execute(receiver, clazz.findMember(ident), receiver.getExportType());
                return read.execute(elemPtr, elemPtr.getExportType());
            }

            Object ret = receiver;
            for (Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance> p : list) {
                ret = interop.readMember(ret, p.getLeft().name);
            }
            return interop.readMember(ret, ident);
        }
        return doNormal(receiver, ident, clazz, getMemberPtr, read);
    }

    @Specialization
    public Object doNormal(LLVMPointer receiver, String ident, LLVMInteropType exportType, @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Cached LLVMForeignReadNode read) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(exportType, receiver, ident);
        return read.execute(ptr, ptr.getExportType());
    }
}
