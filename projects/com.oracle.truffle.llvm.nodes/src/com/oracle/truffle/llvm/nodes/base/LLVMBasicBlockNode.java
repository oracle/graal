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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

/**
 * This node represents a basic block in LLVM. The node contains both sequential statements which do
 * not change the control flow and terminator instructions which let the function return or continue
 * with another basic block.
 *
 * @see <a href="http://llvm.org/docs/LangRef.html#functions">basic blocks in LLVM IR</a>
 */
public class LLVMBasicBlockNode extends LLVMExpressionNode {

    public static final int RETURN_FROM_FUNCTION = -1;
    private static final boolean TRACE = !LLVMLogger.TARGET_NONE.equals(LLVMOptions.DEBUG.traceExecution());

    @Children private final LLVMExpressionNode[] statements;
    @Child public LLVMControlFlowNode termInstruction;

    private final int blockId;
    private final String blockName;

    private final BranchProfile controlFlowExceptionProfile = BranchProfile.create();

    @CompilationFinal private SourceSection sourceSection;

    @CompilationFinal(dimensions = 1) private final long[] successorExecutionCount;
    @CompilationFinal private long totalExecutionCount = 0;

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        CompilerAsserts.neverPartOfCompilation();
        throw new UnsupportedOperationException("Must not be called.");
    }

    public LLVMBasicBlockNode(LLVMExpressionNode[] statements, LLVMControlFlowNode termInstruction, int blockId, String blockName) {
        this.statements = statements;
        this.termInstruction = termInstruction;
        this.blockId = blockId;
        this.blockName = blockName;
        successorExecutionCount = termInstruction.needsBranchProfiling() ? new long[termInstruction.getSuccessorCount()] : null;
    }

    @ExplodeLoop
    public void executeStatements(VirtualFrame frame) {
        for (LLVMExpressionNode statement : statements) {
            try {
                if (TRACE) {
                    trace(statement);
                }
                statement.executeGeneric(frame);
            } catch (ControlFlowException e) {
                controlFlowExceptionProfile.enter();
                throw e;
            } catch (Throwable t) {
                CompilerDirectives.transferToInterpreter();
                SourceSection exceptionSourceSection = statement.getEncapsulatingSourceSection();
                if (exceptionSourceSection == null) {
                    throw t;
                } else {
                    throw new RuntimeException("LLVM error in " + statement.getSourceDescription(), t);
                }
            }
        }
    }

    @TruffleBoundary
    private static void trace(LLVMExpressionNode statement) {
        LLVMLogger.print(LLVMOptions.DEBUG.traceExecution()).accept(("[sulong] " + statement.getSourceDescription()));
    }

    @Override
    public String getSourceDescription() {
        LLVMFunctionStartNode functionStartNode = NodeUtil.findParent(this, LLVMFunctionStartNode.class);
        assert functionStartNode != null : getParent().getClass();
        if (blockId == 0) {
            return String.format("first basic block in %s", functionStartNode.getName());
        } else {
            return String.format("basic block %s in %s", blockName, functionStartNode.getName());
        }
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("basic block %s (#statements: %s, terminating instruction: %s)", blockId, statements.length, termInstruction);
    }

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
    public double getBranchProbability(int successorIndex) {
        assert termInstruction.needsBranchProfiling();
        double successorBranchProbability;
        long succCount = successorExecutionCount[successorIndex];
        if (succCount == 0) {
            successorBranchProbability = 0;
        } else {
            successorBranchProbability = (double) succCount / totalExecutionCount;
        }
        return successorBranchProbability;
    }

    public void increaseBranchProbability(int successorIndex) {
        CompilerAsserts.neverPartOfCompilation();
        if (termInstruction.needsBranchProfiling()) {
            incrementCountAtIndex(successorIndex);
        }
    }

    private void incrementCountAtIndex(int successorIndex) {
        assert termInstruction.needsBranchProfiling();
        if (totalExecutionCount != Long.MAX_VALUE) {
            totalExecutionCount++;
            successorExecutionCount[successorIndex]++;
        }
    }
}
