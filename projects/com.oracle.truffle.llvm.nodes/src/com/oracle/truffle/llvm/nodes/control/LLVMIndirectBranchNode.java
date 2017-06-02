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
package com.oracle.truffle.llvm.nodes.control;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

public final class LLVMIndirectBranchNode extends LLVMControlFlowNode {

    @Child private LLVMBranchAddressNode branchAddress;
    @Children private final LLVMExpressionNode[] phiWriteNodes;
    @CompilationFinal(dimensions = 1) private final int[] successors;

    public LLVMIndirectBranchNode(LLVMBranchAddressNode branchAddress, int[] indices, LLVMExpressionNode[] phiWriteNodes, SourceSection sourceSection) {
        super(sourceSection);
        assert indices.length > 1;
        this.successors = indices;
        this.branchAddress = branchAddress;
        this.phiWriteNodes = phiWriteNodes;
    }

    @Override
    public int getSuccessorCount() {
        return successors.length;
    }

    @Override
    public LLVMExpressionNode getPhiNode(int successorIndex) {
        return phiWriteNodes[successorIndex];
    }

    public int executeCondition(VirtualFrame frame) {
        return branchAddress.branchAddress(frame);
    }

    public int[] getSuccessors() {
        return successors;
    }

    public abstract static class LLVMBranchAddressNode extends LLVMNode {
        public abstract int branchAddress(VirtualFrame frame);
    }

    public static final class LLVMBasicBranchAddressNode extends LLVMBranchAddressNode {

        @Child private LLVMExpressionNode address;

        public LLVMBasicBranchAddressNode(LLVMExpressionNode address) {
            this.address = address;
        }

        @Override
        public int branchAddress(VirtualFrame frame) {
            try {
                return (int) address.executeLLVMAddress(frame).getVal();
            } catch (UnexpectedResultException e) {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalStateException("should not reach here", e);
            }
        }

    }
}
