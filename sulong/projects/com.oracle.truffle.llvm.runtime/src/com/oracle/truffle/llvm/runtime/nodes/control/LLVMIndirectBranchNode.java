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
package com.oracle.truffle.llvm.runtime.nodes.control;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMIndirectBranchNodeFactory.LLVMIndirectBranchNodeImplNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

@GenerateWrapper
@NodeChild(value = "branchAddress", type = LLVMExpressionNode.class)
public abstract class LLVMIndirectBranchNode extends LLVMControlFlowNode {

    public static LLVMIndirectBranchNode create(LLVMExpressionNode branchAddress, int[] indices, LLVMStatementNode[] phiWriteNodes) {
        return LLVMIndirectBranchNodeImplNodeGen.create(indices, phiWriteNodes, branchAddress);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMIndirectBranchNodeWrapper(this, probe);
    }

    public abstract int executeCondition(VirtualFrame frame);

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

    abstract static class LLVMIndirectBranchNodeImpl extends LLVMIndirectBranchNode {

        @Children private final LLVMStatementNode[] phiWriteNodes;

        // can't use @NodeField since we need this to be @CompilationFinal
        @CompilationFinal(dimensions = 1) private final int[] successors;

        LLVMIndirectBranchNodeImpl(int[] indices, LLVMStatementNode[] phiWriteNodes) {
            assert indices.length > 1;
            this.successors = indices;
            this.phiWriteNodes = phiWriteNodes;
        }

        @Override
        public int getSuccessorCount() {
            return successors.length;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            return phiWriteNodes[successorIndex];
        }

        @Specialization
        public int doCondition(LLVMNativePointer branchAddress) {
            return (int) branchAddress.asNative();
        }

        @Override
        public int[] getSuccessors() {
            return successors;
        }
    }
}
