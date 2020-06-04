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

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMBrUnconditionalNodeFactory.LLVMBrUnconditionalNodeImplNodeGen;

@GenerateWrapper
@NodeField(name = "successor", type = int.class)
public abstract class LLVMBrUnconditionalNode extends LLVMControlFlowNode {

    public static LLVMBrUnconditionalNode create(int successor, LLVMStatementNode phi) {
        return LLVMBrUnconditionalNodeImplNodeGen.create(phi, successor);
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new LLVMBrUnconditionalNodeWrapper(this, probe);
    }

    public abstract int getSuccessor();

    // we need an execute method so the node can be properly instrumented
    public abstract void execute(VirtualFrame frame);

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

    abstract static class LLVMBrUnconditionalNodeImpl extends LLVMBrUnconditionalNode {

        @Child private LLVMStatementNode phi;

        LLVMBrUnconditionalNodeImpl(LLVMStatementNode phi) {
            this.phi = phi;
        }

        @Override
        public String toString() {
            return getShortString("successor");
        }

        @Override
        public int getSuccessorCount() {
            return 1;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            assert successorIndex == 0;
            return phi;
        }

        @Specialization
        public void doCondition() {
            // nothing to do, since this branch is unconditional
        }

        @Override
        public final int[] getSuccessors() {
            return new int[]{getSuccessor()};
        }
    }
}
