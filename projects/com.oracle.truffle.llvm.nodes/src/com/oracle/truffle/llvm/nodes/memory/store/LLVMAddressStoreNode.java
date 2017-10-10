/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.store;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMAddressStoreNode extends LLVMStoreNode {

    public LLVMAddressStoreNode(Type type, SourceSection source) {
        super(type, ADDRESS_SIZE_IN_BYTES, source);
    }

    @Specialization
    public Object doAddress(LLVMAddress address, LLVMAddress value) {
        LLVMMemory.putAddress(address, value);
        return null;
    }

    @Specialization
    public Object doAddress(LLVMVirtualAllocationAddress address, LLVMAddress value) {
        address.writeI64(value.getVal());
        return null;
    }

    @Specialization
    public Object execute(LLVMBoxedPrimitive address, LLVMAddress value) {
        if (address.getValue() instanceof Long) {
            LLVMMemory.putAddress((long) address.getValue(), value);
            return null;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + address.getValue());
        }
    }

    @Specialization
    public Object execute(LLVMBoxedPrimitive address, LLVMGlobalVariable value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        if (address.getValue() instanceof Long) {
            LLVMMemory.putAddress(LLVMAddress.fromLong((long) address.getValue()), globalAccess.getNativeLocation(value));
            return null;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + address.getValue());
        }
    }

    @Specialization
    public Object doAddress(LLVMGlobalVariable address, LLVMAddress value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        globalAccess.putAddress(address, value);
        return null;
    }

    @Specialization
    public Object doAddress(LLVMAddress address, LLVMGlobalVariable value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        LLVMMemory.putAddress(address, globalAccess.getNativeLocation(value));
        return null;
    }

    @Specialization
    public Object doAddress(LLVMGlobalVariable address, LLVMGlobalVariable value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                    @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
        LLVMMemory.putAddress(globalAccess1.getNativeLocation(address), globalAccess2.getNativeLocation(value));
        return null;
    }

    @Specialization
    public Object doAddress(LLVMGlobalVariable address, LLVMBoxedPrimitive value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        globalAccess.putBoxedPrimitive(address, value);
        return null;
    }

    @Specialization
    public Object doAddress(LLVMGlobalVariable address, LLVMTruffleObject value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        globalAccess.putLLVMTruffleObject(address, value);
        return null;
    }

    @Specialization
    public Object execute(LLVMAddress address, LLVMTruffleObject value, @Cached(value = "getForceLLVMAddressNode()") LLVMForceLLVMAddressNode toLLVMAddress) {
        LLVMMemory.putAddress(address, toLLVMAddress.executeWithTarget(value));
        return null;
    }

    @Specialization
    public Object execute(LLVMBoxedPrimitive address, LLVMTruffleObject value, @Cached(value = "getForceLLVMAddressNode()") LLVMForceLLVMAddressNode convertAddress,
                    @Cached(value = "getForceLLVMAddressNode()") LLVMForceLLVMAddressNode convertValue) {
        LLVMMemory.putAddress(convertAddress.executeWithTarget(address), convertValue.executeWithTarget(value));
        return null;
    }

    @Specialization
    public Object execute(LLVMTruffleObject address, Object value, @Cached("createForeignWrite()") LLVMForeignWriteNode foreignWrite) {
        foreignWrite.execute(address, value);
        return null;
    }
}
