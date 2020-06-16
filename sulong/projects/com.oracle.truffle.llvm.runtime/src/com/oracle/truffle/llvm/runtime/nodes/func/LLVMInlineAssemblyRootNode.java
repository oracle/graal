/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.base.LLVMInlineAssemblyBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.base.LLVMInlineAssemblyBlockNodeGen;

public class LLVMInlineAssemblyRootNode extends RootNode {

    @Child private LLVMInlineAssemblyBlockNode prologue;
    @Child private LLVMInlineAssemblyBlockNode block;

    @Child private LLVMExpressionNode result;

    public LLVMInlineAssemblyRootNode(LLVMLanguage language, FrameDescriptor frameDescriptor,
                    List<LLVMStatementNode> statements, List<LLVMStatementNode> writeNodes, LLVMExpressionNode result) {
        super(language, frameDescriptor);
        this.prologue = LLVMInlineAssemblyBlockNodeGen.create(writeNodes);
        this.block = LLVMInlineAssemblyBlockNodeGen.create(statements);
        this.result = result;
    }

    @Override
    public boolean isInternal() {
        // this is an artificial root node for an inline assembly block
        // we don't want this to show up in stack traces
        return true;
    }

    @Override
    protected boolean isInstrumentable() {
        // Sulong does not keep source locations for inline assembly nodes
        return false;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        prologue.execute(frame);
        block.execute(frame);
        return result == null ? 0 : result.executeGeneric(frame);
    }
}
