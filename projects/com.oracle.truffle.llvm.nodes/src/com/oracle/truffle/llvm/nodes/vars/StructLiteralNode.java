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
package com.oracle.truffle.llvm.nodes.vars;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

@NodeChild(type = LLVMExpressionNode.class)
public abstract class StructLiteralNode extends LLVMExpressionNode {

    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @CompilationFinal(dimensions = 1) private final Type[] types;
    @Children private final LLVMStoreNode[] elementWriteNodes;
    @Children private final LLVMExpressionNode[] values;

    public StructLiteralNode(int[] offsets, Type[] types, LLVMStoreNode[] elementWriteNodes, LLVMExpressionNode[] values) {
        assert offsets.length == elementWriteNodes.length && elementWriteNodes.length == values.length;
        this.offsets = offsets;
        this.types = types;
        this.elementWriteNodes = elementWriteNodes;
        this.values = values;
    }

    @ExplodeLoop
    @Specialization
    public LLVMAddress doLLVMAddress(VirtualFrame frame, LLVMAddress address) {
        for (int i = 0; i < offsets.length; i++) {
            LLVMAddress currentAddr = address.increment(offsets[i]);
            Object value = values[i] == null ? null : values[i].executeGeneric(frame);
            elementWriteNodes[i].executeWithTarget(frame, currentAddr, value);
        }
        return address;
    }

    @ExplodeLoop
    @Specialization
    public LLVMTruffleObject doLLVMTruffleObject(VirtualFrame frame, LLVMTruffleObject address) {
        for (int i = 0; i < offsets.length; i++) {
            LLVMTruffleObject currentAddr = address.increment(offsets[i], types[i]);
            Object value = values[i] == null ? null : values[i].executeGeneric(frame);
            elementWriteNodes[i].executeWithTarget(frame, currentAddr, value);
        }
        return address;
    }

}
