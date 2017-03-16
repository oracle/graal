/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMTruffleNull;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
@NodeField(name = "type", type = Type.class)
public abstract class LLVMToAddressNode extends LLVMExpressionNode {

    public abstract Type getType();

    @Specialization
    public LLVMAddress executeI1(boolean from) {
        return LLVMAddress.fromLong(from ? 1 : 0);
    }

    @Specialization
    public LLVMAddress executeI8(byte from) {
        return LLVMAddress.fromLong(from);
    }

    @Specialization
    public LLVMAddress executeI64(long from) {
        return LLVMAddress.fromLong(from);
    }

    @Specialization
    public LLVMAddress executeI64(LLVMFunctionDescriptor from) {
        return LLVMAddress.fromLong(from.getFunctionIndex());
    }

    @Specialization
    public LLVMAddress executeI64(LLVMFunctionHandle from) {
        return LLVMAddress.fromLong(from.getFunctionIndex());
    }

    @Specialization
    public LLVMAddress executeI64(@SuppressWarnings("unused") LLVMTruffleNull from) {
        return LLVMAddress.NULL_POINTER;
    }

    @Specialization(guards = "notLLVM(from)")
    public LLVMTruffleObject executeTruffleObject(TruffleObject from) {
        return new LLVMTruffleObject(from, getType());
    }

}
