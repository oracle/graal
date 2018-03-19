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
package com.oracle.truffle.llvm.nodes.memory.literal;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.types.PointerType;

@NodeChild(value = "address", type = LLVMExpressionNode.class)
public abstract class LLVMAddressArrayLiteralNode extends LLVMExpressionNode {

    @Children private final LLVMToNativeNode[] values;
    private final int stride;

    public LLVMAddressArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
        this.values = getForceLLVMAddressNodes(values);
        this.stride = stride;
    }

    private static LLVMToNativeNode[] getForceLLVMAddressNodes(LLVMExpressionNode[] values) {
        LLVMToNativeNode[] forceToLLVM = new LLVMToNativeNode[values.length];
        for (int i = 0; i < values.length; i++) {
            forceToLLVM[i] = LLVMToNativeNodeGen.create(values[i]);
        }
        return forceToLLVM;
    }

    public LLVMToNativeNode[] getValues() {
        return values;
    }

    @Specialization
    protected LLVMAddress write(VirtualFrame frame, LLVMGlobal global,
                    @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        return writeAddress(frame, globalAccess.executeWithTarget(frame, global), memory);
    }

    @Specialization
    @ExplodeLoop
    protected LLVMAddress writeAddress(VirtualFrame frame, LLVMAddress addr,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        long currentPtr = addr.getVal();
        for (int i = 0; i < values.length; i++) {
            LLVMAddress currentValue = values[i].execute(frame);
            memory.putAddress(currentPtr, currentValue);
            currentPtr += stride;
        }
        return addr;
    }

    protected LLVMForeignWriteNode[] createForeignWrites() {
        LLVMForeignWriteNode[] writes = new LLVMForeignWriteNode[values.length];
        for (int i = 0; i < writes.length; i++) {
            writes[i] = LLVMForeignWriteNodeGen.create(PointerType.VOID, 8);
        }
        return writes;
    }

    // TODO: work around a DSL bug (GR-6493): remove cached int a and int b
    @SuppressWarnings("unused")
    @Specialization
    @ExplodeLoop
    protected LLVMTruffleObject foreignWriteRef(VirtualFrame frame, LLVMTruffleObject addr,
                    @Cached("0") int a,
                    @Cached("0") int b,
                    @Cached("createForeignWrites()") LLVMForeignWriteNode[] foreignWrites) {
        LLVMTruffleObject currentPtr = addr;
        for (int i = 0; i < values.length; i++) {
            Object currentValue = values[i].execute(frame);
            foreignWrites[i].execute(frame, currentPtr, currentValue);
            currentPtr = currentPtr.increment(stride);
        }
        return addr;
    }
}
