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
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetMemberPointerNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.runtime.interop.export.LLVMForeignGetVirtualMemberPtrNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMInteropWriteMemberNode extends LLVMNode {
    public abstract void execute(LLVMPointer receiver, String ident, Object value, LLVMInteropType exportType) throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException;

    @Specialization
    public void doClazzFieldRead(LLVMPointer receiver, String ident, Object value, LLVMInteropType.Clazz clazz, @CachedLibrary(limit = "5") InteropLibrary interop,
                    @Cached LLVMForeignGetVirtualMemberPtrNode virtualMemberPtrNode, @Cached LLVMForeignGetMemberPointerNode getMemberPtr, @Cached LLVMForeignWriteNode write)
                    throws UnsupportedMessageException, UnknownIdentifierException, UnsupportedTypeException {
        List<Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance>> list = clazz.getMemberAccessList(ident);
        if (list != null && list.size() > 0) {
            Object ret = receiver;
            for (Pair<LLVMInteropType.StructMember, LLVMInteropType.ClazzInheritance> p : list) {
                if (p.getRight().virtual) {
                    LLVMPointer elemPtr = virtualMemberPtrNode.execute(LLVMPointer.cast(ret), clazz.findMember(ident), LLVMPointer.cast(ret).getExportType());
                    write.execute(elemPtr, elemPtr.getExportType(), value);
                    return;
                } else {
                    ret = interop.readMember(ret, p.getLeft().name);
                }
            }
            interop.writeMember(ret, ident, value);
        } else {
            doNormal(receiver, ident, value, clazz, getMemberPtr, write);
        }
    }

    @Specialization
    public void doNormal(LLVMPointer receiver, String ident, Object value, LLVMInteropType exportType, @Cached LLVMForeignGetMemberPointerNode getElementPointer,
                    @Cached LLVMForeignWriteNode write) throws UnsupportedMessageException, UnknownIdentifierException {
        LLVMPointer ptr = getElementPointer.execute(exportType, receiver, ident);
        write.execute(ptr, ptr.getExportType(), value);
    }
}
