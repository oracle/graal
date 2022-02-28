/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMFrameNullerUtil;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;

public abstract class LLVMDispatchBasicBlockNode extends LLVMExpressionNode implements BytecodeOSRNode {

    private final int exceptionValueSlot;
    private final LocalVariableDebugInfo debugInfo;

    @Children private final LLVMBasicBlockNode[] bodyNodes;

    private final int loopSuccessorSlot;

    @CompilerDirectives.CompilationFinal private Object osrMetadata;

    public LLVMDispatchBasicBlockNode(int exceptionValueSlot, LLVMBasicBlockNode[] bodyNodes, int loopSuccessorSlot, LocalVariableDebugInfo debugInfo) {
        this.exceptionValueSlot = exceptionValueSlot;
        this.bodyNodes = bodyNodes;
        this.loopSuccessorSlot = loopSuccessorSlot;
        this.debugInfo = debugInfo;
    }

    public LocalVariableDebugInfo getDebugInfo() {
        return debugInfo;
    }

    @Specialization
    public Object doDispatch(VirtualFrame frame) {
        return dispatchFromBasicBlock(frame, 0, new Counter());
    }

    /**
     * The code in this function is mirrored in {@link LLVMLoopDispatchNode}, any changes need to be
     * done in both places.
     */
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private Object dispatchFromBasicBlock(VirtualFrame frame, int bci, Counter counter) {
        assert counter != null;

        Object returnValue = null;
        int basicBlockIndex = bci;

        CompilerAsserts.partialEvaluationConstant(bodyNodes.length);
        try {
            outer: while (basicBlockIndex != LLVMBasicBlockNode.RETURN_FROM_FUNCTION) {
                CompilerAsserts.partialEvaluationConstant(basicBlockIndex);
                LLVMBasicBlockNode bb = bodyNodes[basicBlockIndex];

                // lazily insert the basic block into the AST
                bb.initialize();

                // the newly inserted block may have been instrumented
                bb = bodyNodes[basicBlockIndex];

                // execute all statements
                bb.execute(frame);

                // execute control flow node, write phis, null stack frame slots, and dispatch to
                // the correct successor block
                LLVMControlFlowNode controlFlowNode = bb.getTerminatingInstruction();
                if (controlFlowNode instanceof LLVMConditionalBranchNode) {
                    LLVMConditionalBranchNode conditionalBranchNode = (LLVMConditionalBranchNode) controlFlowNode;
                    boolean condition = conditionalBranchNode.executeCondition(frame);
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(LLVMConditionalBranchNode.TRUE_SUCCESSOR), condition)) {
                        bb.enterSuccessor(LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                        basicBlockIndex = beforeJumpChecks(basicBlockIndex, conditionalBranchNode.getTrueSuccessor(), LLVMConditionalBranchNode.TRUE_SUCCESSOR, counter, controlFlowNode, frame);
                        // continue outer;
                    } else {
                        bb.enterSuccessor(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                        basicBlockIndex = beforeJumpChecks(basicBlockIndex, conditionalBranchNode.getFalseSuccessor(), LLVMConditionalBranchNode.FALSE_SUCCESSOR, counter, controlFlowNode, frame);
                    }
                } else if (controlFlowNode instanceof LLVMSwitchNode) {
                    LLVMSwitchNode switchNode = (LLVMSwitchNode) controlFlowNode;
                    Object condition = switchNode.executeCondition(frame);
                    int[] successors = switchNode.getSuccessors();
                    for (int i = 0; i < successors.length - 1; i++) {
                        if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), switchNode.checkCase(frame, i, condition))) {
                            bb.enterSuccessor(i);
                            basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], i, counter, controlFlowNode, frame);
                            continue outer;
                        }
                    }

                    int i = successors.length - 1;
                    bb.enterSuccessor(i);
                    basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], i, counter, controlFlowNode, frame);
                } else if (controlFlowNode instanceof LLVMLoopNode) {
                    LLVMLoopNode loop = (LLVMLoopNode) controlFlowNode;
                    loop.executeLoop(frame);
                    int successorBasicBlockIndex = frame.getInt(loopSuccessorSlot);
                    frame.setInt(loopSuccessorSlot, 0); // null frame
                    int[] successors = loop.getSuccessors();
                    for (int i = 0; i < successors.length - 1; i++) {
                        if (successorBasicBlockIndex == successors[i]) {
                            basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], -1, counter, null, frame);
                            continue outer;
                        }
                    }
                    int i = successors.length - 1;
                    assert successors[i] == successorBasicBlockIndex : "Could not find loop successor!";
                    basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], -1, counter, null, frame);
                } else if (controlFlowNode instanceof LLVMIndirectBranchNode) {
                    // TODO (chaeubl): we need a different approach here - this is awfully
                    // inefficient (see GR-3664)
                    LLVMIndirectBranchNode indirectBranchNode = (LLVMIndirectBranchNode) controlFlowNode;
                    int[] successors = indirectBranchNode.getSuccessors();
                    int successorBasicBlockIndex = indirectBranchNode.executeCondition(frame);
                    for (int i = 0; i < successors.length - 1; i++) {
                        if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), successors[i] == successorBasicBlockIndex)) {
                            bb.enterSuccessor(i);
                            basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], i, counter, controlFlowNode, frame);
                            continue outer;
                        }
                    }

                    int i = successors.length - 1;
                    assert successorBasicBlockIndex == successors[i];
                    bb.enterSuccessor(i);
                    basicBlockIndex = beforeJumpChecks(basicBlockIndex, successors[i], i, counter, indirectBranchNode, frame);
                } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                    LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                    unconditionalNode.execute(frame); // required for instrumentation
                    basicBlockIndex = beforeJumpChecks(basicBlockIndex, unconditionalNode.getSuccessor(), 0, counter, controlFlowNode, frame);
                } else if (controlFlowNode instanceof LLVMInvokeNode) {
                    LLVMInvokeNode invokeNode = (LLVMInvokeNode) controlFlowNode;
                    try {
                        invokeNode.execute(frame);
                        bb.enterSuccessor(LLVMInvokeNode.NORMAL_SUCCESSOR);
                        basicBlockIndex = beforeJumpChecks(basicBlockIndex, invokeNode.getNormalSuccessor(), LLVMInvokeNode.NORMAL_SUCCESSOR, counter, controlFlowNode, frame);
                    } catch (LLVMUserException e) {
                        bb.enterSuccessor(LLVMInvokeNode.UNWIND_SUCCESSOR);
                        frame.setObject(exceptionValueSlot, e);
                        basicBlockIndex = beforeJumpChecks(basicBlockIndex, invokeNode.getUnwindSuccessor(), LLVMInvokeNode.UNWIND_SUCCESSOR, counter, controlFlowNode, frame);
                    }
                } else if (controlFlowNode instanceof LLVMRetNode) {
                    LLVMRetNode retNode = (LLVMRetNode) controlFlowNode;
                    returnValue = retNode.execute(frame);
                    assert noPhisNecessary(retNode);
                    nullDeadSlots(frame, bb.nullableAfter);
                    basicBlockIndex = beforeJumpChecks(basicBlockIndex, retNode.getSuccessor(), -1, counter, null, frame);
                } else if (controlFlowNode instanceof LLVMResumeNode) {
                    LLVMResumeNode resumeNode = (LLVMResumeNode) controlFlowNode;
                    assert noPhisNecessary(resumeNode);
                    resumeNode.execute(frame);
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("must not reach here");
                } else if (controlFlowNode instanceof LLVMUnreachableNode) {
                    LLVMUnreachableNode unreachableNode = (LLVMUnreachableNode) controlFlowNode;
                    assert noPhisNecessary(unreachableNode);
                    unreachableNode.execute(frame);
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("must not reach here");
                } else {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new UnsupportedOperationException("unexpected controlFlowNode type: " + controlFlowNode);
                }
            }
        } catch (OSRReturnException e) {
            returnValue = e.getResult();
        }
        // only report non-zero counters to reduce interpreter overhead
        int value = counter.value;
        if (CompilerDirectives.hasNextTier() && value != 0) {
            LoopNode.reportLoopCount(this, value > 0 ? value : Integer.MAX_VALUE);
        }

        assert returnValue != null;
        return returnValue;
    }

    int beforeJumpChecks(int currentBCI, int nextBCI, int successorIndex, Counter counter, LLVMControlFlowNode controlFlowNode, VirtualFrame frame) {
        if (controlFlowNode != null) {
            nullDeadSlots(frame, bodyNodes[currentBCI].nullableAfter);
            executePhis(frame, controlFlowNode, successorIndex);
            nullDeadSlots(frame, bodyNodes[nextBCI].nullableBefore);
        }

        if (CompilerDirectives.hasNextTier()) {
            if (nextBCI <= currentBCI) {
                counter.value++;
                if (CompilerDirectives.inInterpreter() && LLVMContext.get(this).getOSRMode() == SulongEngineOption.OSRMode.BYTECODE && BytecodeOSRNode.pollOSRBackEdge(this)) {
                    Object returnValue = BytecodeOSRNode.tryOSR(this, nextBCI, counter, null, frame);
                    if (returnValue != null) {
                        throw new OSRReturnException(returnValue);
                    }
                }
            }
        }
        return nextBCI;
    }

    private static final class Counter {
        private int value;
    }

    private static final class OSRReturnException extends ControlFlowException {
        private static final long serialVersionUID = 9137598429747678409L;
        private final Object result;

        OSRReturnException(Object result) {
            this.result = result;
        }

        Object getResult() {
            return result;
        }
    }

    @ExplodeLoop
    public static void executePhis(VirtualFrame frame, LLVMControlFlowNode controlFlowNode, int successorIndex) {
        LLVMStatementNode phi = controlFlowNode.getPhiNode(successorIndex);
        if (phi != null) {
            phi.execute(frame);
        }
    }

    @ExplodeLoop
    public static void nullDeadSlots(VirtualFrame frame, int[] frameSlotsToNull) {
        if (frameSlotsToNull != null) {
            assert frameSlotsToNull.length > 0;
            for (int i = 0; i < frameSlotsToNull.length; i++) {
                LLVMFrameNullerUtil.nullFrameSlot(frame, frameSlotsToNull[i]);
            }
        }
    }

    private static boolean noPhisNecessary(LLVMControlFlowNode controlFlowNode) {
        return controlFlowNode.getSuccessorCount() == 0 || controlFlowNode.getSuccessorCount() == 1 && controlFlowNode.getPhiNode(0) == null;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.StatementTag.class) {
            return false;
        } else if (tag == StandardTags.RootBodyTag.class) {
            return true;
        } else {
            return super.hasTag(tag);
        }
    }

    @Override
    public final Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        return dispatchFromBasicBlock(osrFrame, target, (Counter) interpreterState);
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    @Override
    public void prepareOSR(int target) {
        // Force initialization to prevent OSR from deoptimizing once it hits new code.
        for (LLVMBasicBlockNode basicBlock : bodyNodes) {
            basicBlock.initialize();
        }
    }
}
