/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignGetMemberPointerNode extends LLVMNode {

    public abstract LLVMPointer execute(LLVMInteropType type, LLVMPointer pointer, String ident) throws UnsupportedMessageException, UnknownIdentifierException;

    @Specialization(guards = {"cachedMember != null", "cachedMember.getStruct() == struct", "cachedIdent.equals(ident)"})
    LLVMPointer doCached(@SuppressWarnings("unused") LLVMInteropType.Struct struct, LLVMPointer pointer, @SuppressWarnings("unused") String ident,
                    @Cached("ident") @SuppressWarnings("unused") String cachedIdent,
                    @Cached("struct.findMember(cachedIdent)") LLVMInteropType.StructMember cachedMember) {
        return pointer.increment(cachedMember.getStartOffset()).export(cachedMember.getType());
    }

    @Specialization(replaces = "doCached")
    static LLVMPointer doGeneric(LLVMInteropType.Struct struct, LLVMPointer pointer, String ident,
                    @Cached BranchProfile exception) throws UnknownIdentifierException {
        LLVMInteropType.StructMember member = struct.findMember(ident);
        if (member == null) {
            exception.enter();
            throw UnknownIdentifierException.create(ident);
        }
        return pointer.increment(member.getStartOffset()).export(member.getType());
    }

    @Fallback
    @SuppressWarnings("unused")
    static LLVMPointer doError(LLVMInteropType type, LLVMPointer object, String ident) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }
}
