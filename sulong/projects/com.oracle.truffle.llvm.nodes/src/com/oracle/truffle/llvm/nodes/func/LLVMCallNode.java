/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.func;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNodeFactory.ArgumentNodeGen;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

public final class LLVMCallNode extends LLVMExpressionNode {

    public static final int USER_ARGUMENT_OFFSET = 1;

    @Children private final LLVMExpressionNode[] argumentNodes;
    @Children private final ArgumentNode[] prepareArgumentNodes;
    @Child private LLVMLookupDispatchTargetNode dispatchTargetNode;
    @Child private LLVMDispatchNode dispatchNode;

    private final LLVMSourceLocation source;

    public LLVMCallNode(FunctionType functionType, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes, LLVMSourceLocation source) {
        this.argumentNodes = argumentNodes;
        this.prepareArgumentNodes = createPrepareArgumentNodes(argumentNodes);
        this.dispatchTargetNode = LLVMLookupDispatchTargetNodeGen.create(functionNode);
        this.dispatchNode = LLVMDispatchNodeGen.create(functionType);
        this.source = source;
    }

    @ExplodeLoop
    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object function = dispatchTargetNode.executeGeneric(frame);

        Object[] argValues = new Object[argumentNodes.length];
        for (int i = 0; i < argumentNodes.length; i++) {
            argValues[i] = prepareArgumentNodes[i].executeWithTarget(argumentNodes[i].executeGeneric(frame));
        }

        return dispatchNode.executeDispatch(function, argValues);
    }

    private static ArgumentNode[] createPrepareArgumentNodes(LLVMExpressionNode[] argumentNodes) {
        ArgumentNode[] nodes = new ArgumentNode[argumentNodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = ArgumentNodeGen.create();
        }
        return nodes;
    }

    protected abstract static class ArgumentNode extends LLVMNode {

        protected abstract Object executeWithTarget(Object value);

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer address) {
            return address.copy();
        }

        @Fallback
        protected Object doOther(Object value) {
            return value;
        }
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return source;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.StatementTag.class || tag == StandardTags.CallTag.class || super.hasTag(tag);
    }
}
