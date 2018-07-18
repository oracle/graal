/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(value = "fromNode", type = LLVMExpressionNode.class)
public abstract class LLVMToAddressNode extends LLVMExpressionNode {

    @Specialization
    protected LLVMNativePointer doI1(boolean from) {
        return LLVMNativePointer.create(from ? 1 : 0);
    }

    @Specialization
    protected LLVMNativePointer doI8(byte from) {
        return LLVMNativePointer.create(LLVMExpressionNode.I8_MASK & (long) from);
    }

    @Specialization
    protected LLVMNativePointer doI16(short from) {
        return LLVMNativePointer.create(LLVMExpressionNode.I16_MASK & (long) from);
    }

    @Specialization
    protected LLVMNativePointer doI32(int from) {
        return LLVMNativePointer.create(LLVMExpressionNode.I32_MASK & from);
    }

    @Specialization
    protected LLVMNativePointer doI64(long from) {
        return LLVMNativePointer.create(from);
    }

    @Specialization
    protected LLVMPointer doLLVMPointer(LLVMPointer from) {
        return from;
    }

    @Specialization
    protected LLVMNativePointer doFunctionDescriptor(LLVMFunctionDescriptor from,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode toNative) {
        return toNative.executeWithTarget(from);
    }

    @Specialization
    protected LLVMVirtualAllocationAddress doVirtualAllocationAddress(LLVMVirtualAllocationAddress from) {
        return from;
    }

    @Specialization
    protected LLVMInteropType doInteropType(LLVMInteropType from) {
        return from;
    }

    @Specialization
    protected String doString(String from) {
        return from;
    }

    @Specialization
    protected LLVMBoxedPrimitive doLLVMBoxedPrimitive(LLVMBoxedPrimitive from) {
        return from;
    }
}
