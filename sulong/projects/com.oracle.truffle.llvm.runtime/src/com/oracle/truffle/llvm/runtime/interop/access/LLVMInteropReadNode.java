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
package com.oracle.truffle.llvm.runtime.interop.access;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropAccessNode.AccessLocation;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public abstract class LLVMInteropReadNode extends LLVMNode {

    public static LLVMInteropReadNode create() {
        return LLVMInteropReadNodeGen.create();
    }

    @Child ToLLVM toLLVM;

    protected LLVMInteropReadNode() {
        this.toLLVM = ToLLVMNodeGen.create();
    }

    public abstract Object execute(LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType);

    @Specialization(guards = "type != null")
    Object doKnownType(LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType,
                    @Cached LLVMInteropAccessNode access,
                    @CachedLibrary(limit = "3") InteropLibrary interop,
                    @Cached BranchProfile exception) {
        AccessLocation location = access.execute(type, foreign, offset);
        return read(interop, location, accessType, exception);
    }

    @Specialization(guards = "type == null", limit = "3")
    Object doUnknownType(@SuppressWarnings("unused") LLVMInteropType.Structured type, Object foreign, long offset, ForeignToLLVMType accessType,
                    @CachedLibrary("foreign") InteropLibrary interop,
                    @Cached BranchProfile exception) {
        // type unknown: fall back to "array of unknown value type"
        AccessLocation location = new AccessLocation(foreign, Long.divideUnsigned(offset, accessType.getSizeInBytes()), null);
        return read(interop, location, accessType, exception);
    }

    private Object read(InteropLibrary interop, AccessLocation location, ForeignToLLVMType accessType, BranchProfile exception) {
        Object ret;
        if (location.identifier instanceof String) {
            String name = (String) location.identifier;
            try {
                ret = interop.readMember(location.base, name);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Member '%s' not found.", name);
            } catch (UnknownIdentifierException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not read member '%s'.", name);
            }
        } else {
            long idx = (Long) location.identifier;
            try {
                ret = interop.readArrayElement(location.base, idx);
            } catch (InvalidArrayIndexException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Invalid array index %d.", idx);
            } catch (UnsupportedMessageException ex) {
                exception.enter();
                throw new LLVMPolyglotException(this, "Can not read array element %d.", idx);
            }
        }
        return toLLVM.executeWithType(ret, location.type, accessType);
    }
}
