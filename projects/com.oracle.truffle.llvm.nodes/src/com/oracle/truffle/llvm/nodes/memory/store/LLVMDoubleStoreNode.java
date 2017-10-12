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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

@NodeChild(type = LLVMExpressionNode.class, value = "valueNode")
public abstract class LLVMDoubleStoreNode extends LLVMStoreNode {

    public LLVMDoubleStoreNode(SourceSection source) {
        super(PrimitiveType.DOUBLE, DOUBLE_SIZE_IN_BYTES, source);
    }

    @Specialization
    public Object execute(LLVMGlobalVariable address, double value, @Cached(value = "createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
        globalAccess.putDouble(address, value);
        return null;
    }

    @Specialization
    public Object execute(LLVMAddress address, double value) {
        LLVMMemory.putDouble(address, value);
        return null;
    }

    @Specialization
    public Object execute(LLVMVirtualAllocationAddress address, double value) {
        address.writeDouble(value);
        return null;
    }

    @Specialization
    public Object execute(VirtualFrame frame, LLVMTruffleObject address, double value, @Cached("createForeignWrite()") LLVMForeignWriteNode foreignWrite) {
        foreignWrite.execute(frame, address, value);
        return null;
    }

    @Specialization
    public Object execute(LLVMBoxedPrimitive address, double value) {
        if (address.getValue() instanceof Long) {
            LLVMMemory.putDouble((long) address.getValue(), value);
            return null;
        } else {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalAccessError("Cannot access address: " + address.getValue());
        }
    }
}
