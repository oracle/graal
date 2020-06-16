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
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNodeFactory.LLVMInvokeNodeImplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.runtime.types.FunctionType;

@GenerateWrapper
public abstract class LLVMInvokeNode extends LLVMControlFlowNode {

    public static LLVMInvokeNode create(FunctionType type, LLVMWriteNode writeResult, LLVMExpressionNode functionNode, LLVMExpressionNode[] argumentNodes,
                    int normalSuccessor, int unwindSuccessor,
                    LLVMStatementNode normalPhiNode, LLVMStatementNode unwindPhiNode) {
        LLVMLookupDispatchTargetNode dispatchTargetNode = LLVMLookupDispatchTargetNodeGen.create(functionNode);
        return LLVMInvokeNodeImplNodeGen.create(type, writeResult, argumentNodes, normalSuccessor, unwindSuccessor, normalPhiNode, unwindPhiNode, dispatchTargetNode);
    }

    @NodeChild(value = "dispatchTarget", type = LLVMLookupDispatchTargetNode.class)
    abstract static class LLVMInvokeNodeImpl extends LLVMInvokeNode {

        @Child protected LLVMStatementNode normalPhiNode;
        @Child protected LLVMStatementNode unwindPhiNode;
        @Child protected LLVMValueProfilingNode returnValueProfile;

        @Children private final LLVMExpressionNode[] argumentNodes;
        @Child private LLVMDispatchNode dispatchNode;
        @Child private LLVMWriteNode writeResult;

        protected final FunctionType type;

        private final int normalSuccessor;
        private final int unwindSuccessor;

        LLVMInvokeNodeImpl(FunctionType type, LLVMWriteNode writeResult, LLVMExpressionNode[] argumentNodes,
                        int normalSuccessor, int unwindSuccessor,
                        LLVMStatementNode normalPhiNode, LLVMStatementNode unwindPhiNode) {
            this.writeResult = writeResult;
            this.normalSuccessor = normalSuccessor;
            this.unwindSuccessor = unwindSuccessor;
            this.type = type;
            this.normalPhiNode = normalPhiNode;
            this.unwindPhiNode = unwindPhiNode;
            this.returnValueProfile = (LLVMValueProfilingNode) LLVMValueProfilingNode.create(null, type.getReturnType());

            this.argumentNodes = argumentNodes;
            this.dispatchNode = LLVMDispatchNodeGen.create(type);
        }

        @Override
        public int getSuccessorCount() {
            return 2;
        }

        @Override
        public int getNormalSuccessor() {
            return normalSuccessor;
        }

        @Override
        public int getUnwindSuccessor() {
            return unwindSuccessor;
        }

        @Override
        public int[] getSuccessors() {
            return new int[]{normalSuccessor, unwindSuccessor};
        }

        @Specialization
        public void doInvoke(VirtualFrame frame, Object function) {
            Object[] argValues = prepareArguments(frame);
            Object result = dispatchNode.executeDispatch(function, argValues);
            if (writeResult != null) {
                writeResult.executeWithTarget(frame, result);
            }
        }

        @ExplodeLoop
        private Object[] prepareArguments(VirtualFrame frame) {
            Object[] argValues = new Object[argumentNodes.length];
            for (int i = 0; i < argumentNodes.length; i++) {
                argValues[i] = argumentNodes[i].executeGeneric(frame);
            }
            return argValues;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            if (successorIndex == NORMAL_SUCCESSOR) {
                return normalPhiNode;
            } else {
                assert successorIndex == UNWIND_SUCCESSOR;
                return unwindPhiNode;
            }
        }
    }

    public static final int NORMAL_SUCCESSOR = 0;
    public static final int UNWIND_SUCCESSOR = 1;

    public LLVMInvokeNode() {
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMInvokeNodeWrapper(this, probe);
    }

    public abstract int getNormalSuccessor();

    public abstract int getUnwindSuccessor();

    public abstract void execute(VirtualFrame frame);

    @Override
    public boolean needsBranchProfiling() {
        // we can't use branch profiling because the control flow happens via exception handling
        return false;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.CallTag.class) {
            return getSourceLocation() != null;
        } else {
            return super.hasTag(tag);
        }
    }

    /**
     * Override to allow access from generated wrapper.
     */
    @Override
    protected abstract boolean isStatement();

    /**
     * Override to allow access from generated wrapper.
     */
    @Override
    protected abstract void setStatement(boolean statementTag);
}
