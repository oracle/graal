/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public abstract class LLVMLoopNode extends LLVMControlFlowNode {
    public static LLVMLoopNode create(LLVMExpressionNode bodyNode, int[] successorIDs) {
        return new LLVMLoopNodeImpl(bodyNode, successorIDs, null);
    }

    public LLVMLoopNode(LLVMSourceLocation source) {
        super(source);
    }

    protected LLVMLoopNode(LLVMLoopNode delegate) {
        super(delegate.getSourceLocation());
    }

    public abstract void executeLoop(VirtualFrame frame);

    public abstract int[] getSuccessors();

    private static final class LLVMLoopNodeImpl extends LLVMLoopNode {
        @Child private LoopNode loop;
        @CompilationFinal(dimensions = 1) private final int[] successors;

        private LLVMLoopNodeImpl(LLVMExpressionNode bodyNode, int[] successorIDs, LLVMSourceLocation sourceSection) {
            super(sourceSection);
            loop = Truffle.getRuntime().createLoopNode(new LLVMRepeatingNode(bodyNode));
            successors = successorIDs;
        }

        @Override
        public int[] getSuccessors() {
            return successors;
        }

        private static final class LLVMRepeatingNode extends Node implements RepeatingNode {
            @Child private LLVMExpressionNode bodyNode;

            LLVMRepeatingNode(LLVMExpressionNode bodyNode) {
                this.bodyNode = bodyNode;
            }

            @Override
            public boolean executeRepeating(VirtualFrame frame) {
                try {
                    return bodyNode.executeI1(frame);
                } catch (UnexpectedResultException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void executeLoop(VirtualFrame frame) {
            loop.executeLoop(frame);
        }

        @Override
        public int getSuccessorCount() {
            return successors.length;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            return null;
        }
    }
}
