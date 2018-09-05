/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNode;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMForeignWriteNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@NodeChild(value = "address", type = LLVMExpressionNode.class)
public abstract class LLVMPointerArrayLiteralNode extends LLVMExpressionNode {

    @Children private final LLVMExpressionNode[] values;
    @Children private LLVMToNativeNode[] nativeValues;
    private final int stride;

    public LLVMPointerArrayLiteralNode(LLVMExpressionNode[] values, int stride) {
        this.values = values;
        this.nativeValues = null;
        this.stride = stride;
    }

    public LLVMExpressionNode[] getValues() {
        return values;
    }

    private LLVMToNativeNode[] getNativeValues() {
        if (nativeValues == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            nativeValues = getForceNativeNodes(values);
        }
        return nativeValues;
    }

    @Specialization
    @ExplodeLoop
    protected LLVMNativePointer writeAddress(VirtualFrame frame, LLVMNativePointer addr,
                    @Cached("getLLVMMemory()") LLVMMemory memory) {
        long currentPtr = addr.asNative();
        for (int i = 0; i < values.length; i++) {
            LLVMNativePointer currentValue = getNativeValues()[i].execute(frame);
            memory.putPointer(currentPtr, currentValue);
            currentPtr += stride;
        }
        return addr;
    }

    @Specialization
    @ExplodeLoop
    protected LLVMManagedPointer foreignWriteRef(VirtualFrame frame, LLVMManagedPointer addr,
                    @Cached("createForeignWrites()") LLVMForeignWriteNode[] foreignWrites) {
        LLVMManagedPointer currentPtr = addr;
        for (int i = 0; i < values.length; i++) {
            foreignWrites[i].execute(currentPtr, values[i].executeGeneric(frame));
            currentPtr = currentPtr.increment(stride);
        }
        return addr;
    }

    private static LLVMToNativeNode[] getForceNativeNodes(LLVMExpressionNode[] values) {
        LLVMToNativeNode[] forceToLLVM = new LLVMToNativeNode[values.length];
        for (int i = 0; i < values.length; i++) {
            forceToLLVM[i] = LLVMToNativeNodeGen.create(values[i]);
        }
        return forceToLLVM;
    }

    protected LLVMForeignWriteNode[] createForeignWrites() {
        LLVMForeignWriteNode[] writes = new LLVMForeignWriteNode[values.length];
        for (int i = 0; i < writes.length; i++) {
            writes[i] = LLVMForeignWriteNodeGen.create(ForeignToLLVMType.POINTER);
        }
        return writes;
    }
}
