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
package com.oracle.truffle.llvm.runtime.nodes.base;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNodeFactory.InitializedBlockNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNodeFactory.LazyBlockNodeGen;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

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

    public static LLVMBasicBlockNode createBasicBlockNode(OptionValues options, LLVMStatementNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
        if (options.get(SulongEngineOption.LAZY_PARSING)) {
            return LazyBlockNodeGen.create(statements, termInstruction, blockId, blockName);
        } else {
            return InitializedBlockNodeGen.create(statements, termInstruction, blockId, blockName);
        }
    }

    private final int blockId;
    private final String blockName;

    @CompilationFinal(dimensions = 1) public FrameSlot[] nullableBefore;
    @CompilationFinal(dimensions = 1) public FrameSlot[] nullableAfter;

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

    public void setNullableFrameSlots(FrameSlot[] nullableBefore, FrameSlot[] nullableAfter) {
        this.nullableBefore = nullableBefore;
        this.nullableAfter = nullableAfter;
    }

    public abstract LLVMBasicBlockNode initialize();

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

    public abstract void increaseBranchProbability(int successorIndex);

    @Override
    public String toString() {
        return getShortString("blockId", "nullableBefore", "nullableAfter");
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

    abstract static class InitializedBlock extends LLVMBasicBlockNode {

        private final BranchProfile controlFlowExceptionProfile = BranchProfile.create();
        private final BranchProfile blockEntered = BranchProfile.create();

        @CompilationFinal(dimensions = 1) private final long[] successorExecutionCount;

        @Children private final LLVMStatementNode[] statements;
        @Child public LLVMControlFlowNode termInstruction;

        InitializedBlock(LLVMStatementNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
            super(blockId, blockName);
            this.successorExecutionCount = termInstruction.needsBranchProfiling() ? new long[termInstruction.getSuccessorCount()] : null;
            this.statements = statements;
            this.termInstruction = termInstruction;
        }

        @Override
        public LLVMBasicBlockNode initialize() {
            // this block is already initialized
            return this;
        }

        @Override
        public final LLVMStatementNode[] getStatements() {
            return statements;
        }

        @Specialization
        @ExplodeLoop
        public void doBlock(VirtualFrame frame) {
            blockEntered.enter();
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
            assert termInstruction.needsBranchProfiling();
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
        public void increaseBranchProbability(int successorIndex) {
            CompilerAsserts.neverPartOfCompilation();
            if (termInstruction.needsBranchProfiling()) {
                incrementCountAtIndex(successorIndex);
            }
        }

        private void incrementCountAtIndex(int successorIndex) {
            assert termInstruction.needsBranchProfiling();
            successorExecutionCount[successorIndex]++;
        }
    }

    abstract static class LazyBlock extends LLVMBasicBlockNode {

        // explicitly not an @Child to prevent Truffle from inlining the node and thereby causing an
        // unnecessarily large AST
        @CompilationFinal(dimensions = 1) private final LLVMStatementNode[] statements;
        private final LLVMControlFlowNode termInstruction;

        LazyBlock(LLVMStatementNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
            super(blockId, blockName);
            this.statements = statements;
            this.termInstruction = termInstruction;
        }

        @Override
        public void setNullableFrameSlots(FrameSlot[] nullableBefore, FrameSlot[] nullableAfter) {
            this.nullableBefore = nullableBefore;
            this.nullableAfter = nullableAfter;
        }

        @Override
        public LLVMBasicBlockNode initialize() {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            final LLVMBasicBlockNode materializedBlock = InitializedBlockNodeGen.create(statements, termInstruction, getBlockId(), getBlockName());
            materializedBlock.setNullableFrameSlots(nullableBefore, nullableAfter);
            materializedBlock.setSourceLocation(this.getSourceLocation());
            materializedBlock.setHasStatementTag(this.hasStatementTag());
            replace(materializedBlock, "Lazily Inserting LLVM Basic Block");
            notifyInserted(materializedBlock);
            return materializedBlock;
        }

        @Override
        public final LLVMStatementNode[] getStatements() {
            return statements;
        }

        @Specialization
        public void doFail() {
            CompilerDirectives.transferToInterpreter();
            throw new IllegalStateException("Lazy block should have been materialized");
        }

        @Override
        public LLVMControlFlowNode getTerminatingInstruction() {
            return termInstruction;
        }

        @Override
        public double getBranchProbability(int successorIndex) {
            return 0;
        }

        @Override
        public void increaseBranchProbability(int successorIndex) {
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return String.format("uninitialized basic block %s (#statements: %s, terminating instruction: %s)", getBlockId(), statements.length, termInstruction);
        }
    }
}
