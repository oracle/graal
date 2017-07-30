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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.Instrumentable;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.wrappers.LLVMSwitchNodeWrapper;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@Instrumentable(factory = LLVMSwitchNodeWrapper.class)
public abstract class LLVMSwitchNode extends LLVMControlFlowNode {

    public LLVMSwitchNode(SourceSection sourceSection) {
        super(sourceSection);
    }

    private static class LLVMSwitchNodeImpl extends LLVMSwitchNode {
        @Children private final LLVMExpressionNode[] phiNodes;
        @Child protected LLVMExpressionNode cond;
        @Children protected final LLVMExpressionNode[] cases;
        @CompilationFinal(dimensions = 1) private final int[] successors;

        LLVMSwitchNodeImpl(int[] successors, LLVMExpressionNode[] phiNodes, LLVMExpressionNode cond, LLVMExpressionNode[] cases, SourceSection sourceSection) {
            super(sourceSection);
            assert successors.length == cases.length + 1 : "the last entry of the successors array must be the default case";
            this.successors = successors;
            this.phiNodes = phiNodes;
            this.cond = cond;
            this.cases = cases;
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return null;
        }

        @Override
        public int[] getSuccessors() {
            return successors;
        }

        @Override
        public int getSuccessorCount() {
            return successors.length;
        }

        @Override
        public LLVMExpressionNode getPhiNode(int successorIndex) {
            return phiNodes[successorIndex];
        }

        @Override
        public LLVMExpressionNode getCase(int i) {
            return cases[i];
        }
    }

    public abstract Object executeCondition(VirtualFrame frame);

    public abstract int[] getSuccessors();

    public abstract LLVMExpressionNode getCase(int i);

    public static final class LLVMI1SwitchNode extends LLVMSwitchNodeImpl {
        public LLVMI1SwitchNode(LLVMExpressionNode cond, LLVMExpressionNode[] cases, int[] successors, LLVMExpressionNode[] phiWriteNodes, SourceSection source) {
            super(successors, phiWriteNodes, cond, cases, source);
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return cond.executeI1(frame);
        }

    }

    public static final class LLVMI8SwitchNode extends LLVMSwitchNodeImpl {
        public LLVMI8SwitchNode(LLVMExpressionNode cond, LLVMExpressionNode[] cases, int[] successors, LLVMExpressionNode[] phiNodes, SourceSection source) {
            super(successors, phiNodes, cond, cases, source);
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return cond.executeI8(frame);
        }

    }

    public static final class LLVMI16SwitchNode extends LLVMSwitchNodeImpl {
        public LLVMI16SwitchNode(LLVMExpressionNode cond, LLVMExpressionNode[] cases, int[] successors, LLVMExpressionNode[] phiNodes, SourceSection source) {
            super(successors, phiNodes, cond, cases, source);
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return cond.executeI16(frame);
        }

    }

    public static final class LLVMI32SwitchNode extends LLVMSwitchNodeImpl {
        public LLVMI32SwitchNode(LLVMExpressionNode cond, LLVMExpressionNode[] cases, int[] successors, LLVMExpressionNode[] phiNodes, SourceSection source) {
            super(successors, phiNodes, cond, cases, source);
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return cond.executeI32(frame);
        }

    }

    public static final class LLVMI64SwitchNode extends LLVMSwitchNodeImpl {
        public LLVMI64SwitchNode(LLVMExpressionNode cond, LLVMExpressionNode[] cases, int[] successors, LLVMExpressionNode[] phiNodes, SourceSection source) {
            super(successors, phiNodes, cond, cases, source);
        }

        @Override
        public Object executeCondition(VirtualFrame frame) {
            return cond.executeI64(frame);
        }

    }
}
