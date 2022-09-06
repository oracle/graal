/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.base;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

/**
 * This node represents a basic block in LLVM. The node contains both sequential statements which do
 * not change the control flow and terminator instructions which let the function return or continue
 * with another basic block.
 *
 * @see <a href="http://llvm.org/docs/LangRef.html#functions">basic blocks in LLVM IR</a>
 */
@GenerateWrapper
public abstract class LLVMBasicBlockNode extends LLVMStatementNode {

    public static final int RETURN_FROM_FUNCTION = -1;

    public static LLVMBasicBlockNode createBasicBlockNode(LLVMStatementNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
        return new InitializedBlockNode(statements, termInstruction, blockId, blockName);
    }

    private final int blockId;
    private final String blockName;

    @CompilationFinal(dimensions = 1) public int[] nullableBefore;
    @CompilationFinal(dimensions = 1) public int[] nullableAfter;

    public LLVMBasicBlockNode(int blockId, String blockName) {
        this.blockId = blockId;
        this.blockName = blockName;
    }

    protected LLVMBasicBlockNode(LLVMBasicBlockNode other) {
        this.blockId = other.blockId;
        this.blockName = other.blockName;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new LLVMBasicBlockNodeWrapper(this, this, probeNode);
    }

    public void setNullableFrameSlots(int[] nullableBefore, int[] nullableAfter) {
        this.nullableBefore = nullableBefore;
        this.nullableAfter = nullableAfter;
    }

    public abstract LLVMStatementNode[] getStatements();

    @Override
    public abstract void execute(VirtualFrame frame);

    public abstract LLVMControlFlowNode getTerminatingInstruction();

    public int getBlockId() {
        return blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    /**
     * Gets the branch probability of the given successor.
     *
     * @param successorIndex
     * @return the probability between 0 and 1
     */
    public abstract double getBranchProbability(int successorIndex);

    public abstract void enterSuccessor(int trueSuccessor);

    @Override
    public String toString() {
        return getShortString("blockId", "nullableBefore", "nullableAfter");
    }

    private static final class InitializedBlockNode extends LLVMBasicBlockNode implements GenerateAOT.Provider {

        private final BranchProfile controlFlowExceptionProfile = BranchProfile.create();

        @CompilationFinal(dimensions = 1) private long[] successorExecutionCount;

        @Children private final LLVMStatementNode[] statements;
        @Child public LLVMControlFlowNode termInstruction;

        @CompilationFinal private boolean aot;
        @CompilationFinal private double aotBranchProbability;

        InitializedBlockNode(LLVMStatementNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
            super(blockId, blockName);
            this.successorExecutionCount = termInstruction.getSuccessorCount() > 1 ? new long[termInstruction.getSuccessorCount()] : null;
            this.statements = statements;
            this.termInstruction = termInstruction;
        }

        @Override
        public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
            aot = true;
            aotBranchProbability = successorExecutionCount != null ? (1d / successorExecutionCount.length) : 1d;
        }

        @Override
        public LLVMStatementNode[] getStatements() {
            return statements;
        }

        @Override
        @ExplodeLoop
        public void execute(VirtualFrame frame) {
            for (int i = 0; i < statements.length; i++) {
                LLVMStatementNode statement = statements[i];
                try {
                    statement.execute(frame);
                } catch (ControlFlowException e) {
                    controlFlowExceptionProfile.enter();
                    throw e;
                }
            }
        }

        @Override
        public LLVMControlFlowNode getTerminatingInstruction() {
            return termInstruction;
        }

        @Override
        @ExplodeLoop
        public double getBranchProbability(int successorIndex) {
            if (aot) {
                return aotBranchProbability;
            }

            if (successorExecutionCount == null) {
                // only one successor
                return 1;
            }
            double successorBranchProbability;

            /*
             * It is possible to get race conditions (compiler and AST interpreter thread). This
             * avoids a probability > 1.
             *
             * We make sure that we read each element only once. We also make sure that the compiler
             * reduces the conditions to constants.
             */
            long succCount = 0;
            long totalExecutionCount = 0;
            for (int i = 0; i < successorExecutionCount.length; i++) {
                long v = successorExecutionCount[i];
                if (successorIndex == i) {
                    succCount = v;
                }
                totalExecutionCount += v;
            }
            if (succCount == 0) {
                successorBranchProbability = 0;
            } else {
                assert totalExecutionCount > 0;
                successorBranchProbability = (double) succCount / totalExecutionCount;
            }
            assert !Double.isNaN(successorBranchProbability) && successorBranchProbability >= 0 && successorBranchProbability <= 1;
            return successorBranchProbability;
        }

        @Override
        public void enterSuccessor(int successorIndex) {
            if (!aot && CompilerDirectives.inCompiledCode() && successorExecutionCount != null) {
                if (successorExecutionCount[successorIndex] == 0) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
            }
            if (CompilerDirectives.inInterpreter()) {
                if (aot) {
                    aot = false;
                }
                if (successorExecutionCount != null) {
                    successorExecutionCount[successorIndex]++;
                }
            }
        }
    }
}
