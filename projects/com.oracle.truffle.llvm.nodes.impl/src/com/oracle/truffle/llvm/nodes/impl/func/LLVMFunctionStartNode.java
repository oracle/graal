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
package com.oracle.truffle.llvm.nodes.impl.func;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;

public class LLVMFunctionStartNode extends RootNode {

    @Child private LLVMExpressionNode node;
    @Children private final LLVMNode[] beforeFunction;
    @Children private final LLVMNode[] afterFunction;
    private final String functionName;
    @CompilationFinal private LLVMStackFrameNuller[] nullers;

    public LLVMFunctionStartNode(LLVMExpressionNode node, LLVMNode[] beforeFunction, LLVMNode[] afterFunction, SourceSection sourceSection, FrameDescriptor frameDescriptor, String functionName,
                    LLVMStackFrameNuller[] initNullers) {
        super(LLVMLanguage.class, sourceSection, frameDescriptor);
        this.node = node;
        this.beforeFunction = beforeFunction;
        this.afterFunction = afterFunction;
        this.functionName = functionName;
        this.nullers = initNullers;
    }

    @Override
    @ExplodeLoop
    public Object execute(VirtualFrame frame) {
        for (LLVMStackFrameNuller nuller : nullers) {
            nuller.nullifySlot(frame);
        }
        CompilerAsserts.compilationConstant(beforeFunction);
        for (LLVMNode before : beforeFunction) {
            before.executeVoid(frame);
        }
        Object result = node.executeGeneric(frame);
        CompilerAsserts.compilationConstant(afterFunction);
        for (LLVMNode after : afterFunction) {
            after.executeVoid(frame);
        }
        return result;
    }

    @Override
    public String toString() {
        return functionName;
    }

    public String getFunctionName() {
        return functionName;
    }

    @Override
    public String getName() {
        return functionName;
    }

}
