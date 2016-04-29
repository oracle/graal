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
package com.oracle.truffle.llvm.nodes.impl.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;

/**
 * This node represents a basic block in LLVM. The node contains both sequential statements which do
 * not change the control flow and terminator instructions which let the function return or continue
 * with another basic block.
 *
 * @see <a href="http://llvm.org/docs/LangRef.html#functions">basic blocks in LLVM IR</a>
 */
public class LLVMBasicBlockNode extends LLVMNode {

    public static final int DEFAULT_SUCCESSOR = 0;

    @Children private final LLVMNode[] statements;
    @Child private LLVMTerminatorNode termInstruction;

    @CompilationFinal private final long[] successorCount;
    @CompilationFinal private long totalExecutionCount = 0;

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeGetSuccessorIndex(frame);
    }

    public LLVMBasicBlockNode(LLVMNode[] statements, LLVMTerminatorNode termInstruction) {
        this.statements = statements;
        this.termInstruction = termInstruction;
        successorCount = new long[termInstruction.getSuccessors().length];
    }

    @ExplodeLoop
    public int executeGetSuccessorIndex(VirtualFrame frame) {
        if (CompilerDirectives.inInterpreter()) {
            incrementTotalCount();
        }
        for (LLVMNode statement : statements) {
            statement.executeVoid(frame);
        }
        int successorIndex = termInstruction.executeGetSuccessorIndex(frame);
        if (CompilerDirectives.inInterpreter()) {
            incrementSuccessorCount(successorIndex);
        }
        return successorIndex;
    }

    private void incrementTotalCount() {
        if (totalExecutionCount != Long.MAX_VALUE) {
            totalExecutionCount++;
        }
    }

    private void incrementSuccessorCount(int successorIndex) {
        long currentCount = successorCount[successorIndex];
        if (currentCount != Long.MAX_VALUE && totalExecutionCount != Long.MAX_VALUE) {
            successorCount[successorIndex]++;
        }
    }

    /**
     * Gets an array containing the potential successor basic blocks. During execution,
     * {@link #executeGetSuccessorIndex(VirtualFrame)} method returns an index into this array.
     *
     * @return the successors
     */
    public int[] getSuccessors() {
        return termInstruction.getSuccessors();
    }

    /**
     * Gets the branch probability of the given successor.
     *
     * @param successorIndex
     * @return the probability between 0 and 1
     */
    public double getBranchProbability(int successorIndex) {
        if (totalExecutionCount == 0) {
            // this branch was never executed yet, do not compile it
            CompilerDirectives.transferToInterpreterAndInvalidate();
            return 0;
        } else {
            double successorBranchProbability = (double) successorCount[successorIndex] / totalExecutionCount;
            assert Double.isFinite(successorBranchProbability);
            return successorBranchProbability;
        }
    }
}
