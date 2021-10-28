/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.ValueKind;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@GenerateUncached
public abstract class LLVMForeignWriteNode extends LLVMNode {

    static final int VALUE_KIND_COUNT = ValueKind.values().length;

    public abstract void execute(LLVMPointer ptr, LLVMInteropType type, Object value) throws UnsupportedMessageException;

    @Specialization(guards = "type.kind == cachedKind", limit = "VALUE_KIND_COUNT")
    @GenerateAOT.Exclude
    static void doValue(LLVMPointer ptr, LLVMInteropType.Value type, Object value,
                    @Cached(value = "type.kind", allowUncached = true) @SuppressWarnings("unused") LLVMInteropType.ValueKind cachedKind,
                    @Cached(parameters = "cachedKind") LLVMOffsetStoreNode store,
                    @Cached(value = "createForeignToLLVM(type)", uncached = "getSlowPath()") ForeignToLLVM toLLVM) {
        Object llvmValue = toLLVM.executeWithForeignToLLVMType(value, type.baseType, cachedKind.foreignToLLVMType);
        store.executeWithTargetGeneric(ptr, 0, llvmValue);
    }

    /**
     * @param ptr
     * @param type
     * @param value
     * @see #execute(LLVMPointer, LLVMInteropType, Object)
     */
    @Specialization
    static void doStructured(LLVMPointer ptr, LLVMInteropType.Structured type, Object value) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

    protected ForeignToLLVM createForeignToLLVM(LLVMInteropType.Value type) {
        return CommonNodeFactory.createForeignToLLVM(type);
    }
}
