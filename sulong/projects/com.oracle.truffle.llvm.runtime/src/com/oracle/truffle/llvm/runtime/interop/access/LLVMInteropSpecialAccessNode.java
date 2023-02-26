/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.except.LLVMPolyglotException;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.SpecialStruct;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.SpecialStructAccessor;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Structured;
import com.oracle.truffle.llvm.runtime.interop.convert.ToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

@GenerateUncached
public abstract class LLVMInteropSpecialAccessNode extends LLVMNode {
    public abstract Object execute(Object foreign, ForeignToLLVMType accessType, Structured type, long offset);

    protected SpecialStructAccessor notNullOrException(SpecialStruct type, long offset, SpecialStructAccessor accessor) {
        if (accessor == null) {
            throw new LLVMPolyglotException(this, "The type '%s' has no node at offset %d.", type.toDisplayString(false), offset);
        }
        return accessor;
    }

    protected Object doAccess(Object foreign, ForeignToLLVMType accessType, SpecialStruct type, long offset, SpecialStructAccessor accessor, ToLLVM toLLVM, BranchProfile exception1,
                    BranchProfile exception2, BranchProfile exception3, InteropLibrary interop) {
        if (accessor == null) {
            exception1.enter();
            throw new LLVMPolyglotException(this, "The type '%s' has no node at offset %d.", type.toDisplayString(false), offset);
        }
        if (accessor.type instanceof LLVMInteropType.Value) {
            try {
                return toLLVM.executeWithType(accessor.getter.get(foreign, interop), (LLVMInteropType.Value) accessor.type, accessType);
            } catch (UnsupportedMessageException ex) {
                exception3.enter();
                throw new LLVMPolyglotException(this, "Special read failed with unsupported message exception.");
            }
        } else {
            exception2.enter();
            throw new LLVMPolyglotException(this, "Special read of type '%s' is not supported.", accessor.type.toDisplayString(false));
        }
    }

    @TruffleBoundary
    SpecialStructAccessor findAccessor(SpecialStruct type, long offset) {
        return type.findAccessor(offset);
    }

    @Specialization(guards = {"type == cachedType", "offset == cachedOffset"})
    @GenerateAOT.Exclude
    public Object doSpecialized(Object foreign, ForeignToLLVMType accessType,
                    @SuppressWarnings("unused") SpecialStruct type, @SuppressWarnings("unused") long offset,
                    @Cached("type") SpecialStruct cachedType,
                    @Cached("offset") long cachedOffset,
                    @Cached("findAccessor(cachedType, cachedOffset)") SpecialStructAccessor accessor,
                    @Cached ToLLVM toLLVM,
                    @Cached BranchProfile exception1,
                    @Cached BranchProfile exception2,
                    @Cached BranchProfile exception3,
                    @CachedLibrary(limit = "3") InteropLibrary interop) {
        return doAccess(foreign, accessType, cachedType, cachedOffset, accessor, toLLVM, exception1, exception2, exception3, interop);
    }

    @Specialization(replaces = "doSpecialized")
    @GenerateAOT.Exclude
    public Object doUnspecialized(Object foreign, ForeignToLLVMType accessType,
                    SpecialStruct type, long offset,
                    @Cached ToLLVM toLLVM,
                    @Cached BranchProfile exception1,
                    @Cached BranchProfile exception2,
                    @Cached BranchProfile exception3,
                    @CachedLibrary(limit = "3") InteropLibrary interop) {
        SpecialStructAccessor accessor = findAccessor(type, offset);
        return doAccess(foreign, accessType, type, offset, accessor, toLLVM, exception1, exception2, exception3, interop);
    }

    public static LLVMInteropSpecialAccessNode create() {
        return LLVMInteropSpecialAccessNodeGen.create();
    }
}
