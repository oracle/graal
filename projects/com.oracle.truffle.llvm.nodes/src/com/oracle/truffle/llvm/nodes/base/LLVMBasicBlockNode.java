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

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.GuestLanguageRuntimeException;
import com.oracle.truffle.llvm.runtime.SulongRuntimeException;
import com.oracle.truffle.llvm.runtime.SulongStackTrace;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

/**
 * This node represents a basic block in LLVM. The node contains both sequential statements which do
 * not change the control flow and terminator instructions which let the function return or continue
 * with another basic block.
 *
 * @see <a href="http://llvm.org/docs/LangRef.html#functions">basic blocks in LLVM IR</a>
 */
public class LLVMBasicBlockNode extends LLVMExpressionNode {

    public static final int RETURN_FROM_FUNCTION = -1;

    @Children private final LLVMExpressionNode[] statements;
    @Child public LLVMControlFlowNode termInstruction;

    private final int blockId;
    private final String blockName;

    private final BranchProfile controlFlowExceptionProfile = BranchProfile.create();
    private final BranchProfile blockEntered = BranchProfile.create();

    @CompilationFinal(dimensions = 1) private final long[] successorExecutionCount;

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
        blockEntered.enter();
        for (int i = 0; i < statements.length; i++) {
            LLVMExpressionNode statement = statements[i];
            try {
                if (traceEnabled()) {
                    trace(statement);
                }
                statement.executeGeneric(frame);
            } catch (ControlFlowException e) {
                controlFlowExceptionProfile.enter();
                throw e;
            } catch (GuestLanguageRuntimeException e) {
                CompilerDirectives.transferToInterpreter();
                throw e;
            } catch (SulongRuntimeException e) {
                CompilerDirectives.transferToInterpreter();
                fillStackTrace(e.getCStackTrace(), i);
                throw e;
            } catch (Throwable t) {
                CompilerDirectives.transferToInterpreter();
                final SulongStackTrace stackTrace = new SulongStackTrace(t.getMessage());
                fillStackTrace(stackTrace, i);
                throw new SulongRuntimeException(t, stackTrace);
            }
        }
    }

    private void fillStackTrace(SulongStackTrace stackTrace, int errorIndex) {
        final SourceSection s = getLastAvailableSourceSection(errorIndex);
        final LLVMFunctionStartNode f = NodeUtil.findParent(this, LLVMFunctionStartNode.class);
        if (s == null) {
            stackTrace.addStackTraceElement(f.getName(), f.getBcSource().getName(), blockName());
        } else {
            stackTrace.addStackTraceElement(f.getOriginalName(), s.getSource().getName(), f.getName(), f.getBcSource().getName(), blockName(), s.getStartLine(), s.getStartColumn());
        }
    }

    private SourceSection getLastAvailableSourceSection(int i) {
        CompilerAsserts.neverPartOfCompilation();
        SourceSection s = null;
        for (int j = i; j >= 0; j--) {
            Node node = statements[j];
            if (node instanceof InstrumentableFactory.WrapperNode) {
                node = ((InstrumentableFactory.WrapperNode) node).getDelegateNode();
            }
            s = node.getSourceSection();
            if (s != null) {
                break;
            }
        }
        return s;
    }

    public int getBlockId() {
        return blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    @CompilationFinal private boolean traceEnabledFlag;
    @CompilationFinal private PrintStream traceStream;

    private void cacheTrace() {
        if (traceStream == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            traceStream = SulongEngineOption.getStream(getContext().getEnv().getOptions().get(SulongEngineOption.DEBUG));
            traceEnabledFlag = SulongEngineOption.isTrue(getContext().getEnv().getOptions().get(SulongEngineOption.DEBUG));
        }
    }

    private boolean traceEnabled() {
        cacheTrace();
        return traceEnabledFlag;
    }

    private PrintStream traceStream() {
        cacheTrace();
        return traceStream;
    }

    @TruffleBoundary
    private void trace(LLVMExpressionNode statement) {
        traceStream().println(("[sulong] " + statement.getSourceDescription()));
    }

    @Override
    public String getSourceDescription() {
        LLVMFunctionStartNode functionStartNode = NodeUtil.findParent(this, LLVMFunctionStartNode.class);
        assert functionStartNode != null : getParent().getClass();
        return String.format("Function: %s - Block: %s", functionStartNode.getName(), blockName());
    }

    private String blockName() {
        return String.format("id: %d name: %s", blockId, blockName == null ? "N/A" : blockName);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return String.format("basic block %s (#statements: %s, terminating instruction: %s)", blockId, statements.length, termInstruction);
    }

    /**
     * Gets the branch probability of the given successor.
     *
     * @param successorIndex
     * @return the probability between 0 and 1
     */
    @ExplodeLoop
    public double getBranchProbability(int successorIndex) {
        assert termInstruction.needsBranchProfiling();
        double successorBranchProbability;

        /*
         * It is possible to get race conditions (compiler and AST interpeter thread). This avoids a
         * probability > 1.
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
