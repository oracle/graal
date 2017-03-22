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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;

public class StructLiteralNode extends LLVMExpressionNode {

    @Child private LLVMExpressionNode address;
    @CompilationFinal(dimensions = 1) private final int[] offsets;
    @Children private final LLVMStructWriteNode[] elementWriteNodes;
    @Children private final LLVMExpressionNode[] values;

    public StructLiteralNode(int[] offsets, LLVMStructWriteNode[] elementWriteNodes, LLVMExpressionNode[] values, LLVMExpressionNode address) {
        assert offsets.length == elementWriteNodes.length && elementWriteNodes.length == values.length;
        this.offsets = offsets;
        this.elementWriteNodes = elementWriteNodes;
        this.address = address;
        this.values = values;
    }

    @Override
    @ExplodeLoop
    public LLVMAddress executeLLVMAddress(VirtualFrame frame) {
        try {
            LLVMAddress addr = address.executeLLVMAddress(frame);
            for (int i = 0; i < offsets.length; i++) {
                LLVMAddress currentAddr = addr.increment(offsets[i]);
                Object value = values[i] == null ? null : values[i].executeGeneric(frame);
                elementWriteNodes[i].executeWrite(frame, currentAddr, value);
            }
            return addr;
        } catch (UnexpectedResultException e) {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeLLVMAddress(frame);
    }

}
