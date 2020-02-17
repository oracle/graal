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

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@NodeChild(value = "dispatchTarget", type = LLVMLookupDispatchTargetNode.class)
public abstract class LLVMCallNode extends LLVMExpressionNode {

    public static final int USER_ARGUMENT_OFFSET = 1;

    @Children private final LLVMExpressionNode[] argumentNodes;
    @Children private final LLVMPrepareArgumentNode[] prepareArgumentNodes;
    @Child private LLVMDispatchNode dispatchNode;

    // this node is also used to execute inline assembly, which is not instrumentable and should
    // therefore not be considered a function call for the debugger
    private final boolean isSourceCall;

    public static LLVMCallNode create(FunctionType functionType, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes, boolean isSourceCall) {
        return LLVMCallNodeGen.create(functionType, argumentNodes, isSourceCall, LLVMLookupDispatchTargetNodeGen.create(functionNode));
    }

    LLVMCallNode(FunctionType functionType, LLVMExpressionNode[] argumentNodes, boolean isSourceCall) {
        this.argumentNodes = argumentNodes;
        this.prepareArgumentNodes = createPrepareArgumentNodes(argumentNodes);
        this.dispatchNode = LLVMDispatchNodeGen.create(functionType);
        this.isSourceCall = isSourceCall;
    }

    @ExplodeLoop
    @Specialization
    Object doCall(VirtualFrame frame, Object function) {
        Object[] argValues = new Object[argumentNodes.length];
        for (int i = 0; i < argumentNodes.length; i++) {
            argValues[i] = prepareArgumentNodes[i].executeWithTarget(argumentNodes[i].executeGeneric(frame));
        }

        return dispatchNode.executeDispatch(function, argValues);
    }

    private static LLVMPrepareArgumentNode[] createPrepareArgumentNodes(LLVMExpressionNode[] argumentNodes) {
        LLVMPrepareArgumentNode[] nodes = new LLVMPrepareArgumentNode[argumentNodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = LLVMPrepareArgumentNodeGen.create();
        }
        return nodes;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.CallTag.class) {
            return isSourceCall && getSourceLocation() != null;
        } else {
            return super.hasTag(tag);
        }
    }
}
