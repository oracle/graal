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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.llvm.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStackFrameNuller;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.runtime.LLVMException;

public final class LLVMDispatchBasicBlockNode extends LLVMExpressionNode {
    @Children private final LLVMBasicBlockNode[] bodyNodes;
    @CompilationFinal(dimensions = 2) private final LLVMStackFrameNuller[][] beforeSlotNullerNodes;
    @CompilationFinal(dimensions = 2) private final LLVMStackFrameNuller[][] afterSlotNullerNodes;
    private final FrameSlot returnSlot;

    public LLVMDispatchBasicBlockNode(LLVMBasicBlockNode[] bodyNodes, LLVMStackFrameNuller[][] beforeSlotNullerNodes, LLVMStackFrameNuller[][] afterSlotNullerNodes, FrameSlot returnSlot) {
        this.bodyNodes = bodyNodes;
        this.beforeSlotNullerNodes = beforeSlotNullerNodes;
        this.afterSlotNullerNodes = afterSlotNullerNodes;
        this.returnSlot = returnSlot;
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    public Object executeGeneric(VirtualFrame frame) {
        CompilerAsserts.compilationConstant(bodyNodes.length);
        int basicBlockIndex = 0;
        int backEdgeCounter = 0;
        outer: while (basicBlockIndex != LLVMBasicBlockNode.RETURN_FROM_FUNCTION) {
            CompilerAsserts.partialEvaluationConstant(basicBlockIndex);
            LLVMBasicBlockNode bb = bodyNodes[basicBlockIndex];
            // null dead stack frame slots before executing the statements
            nullDeadSlots(frame, basicBlockIndex, beforeSlotNullerNodes);

            // execute all statements
            bb.executeStatements(frame);

            // execute control flow node, write phis, null stack frame slots, and dispatch to
            // the correct successor block
            LLVMControlFlowNode controlFlowNode = bb.termInstruction;
            if (controlFlowNode instanceof LLVMConditionalBranchNode) {
                LLVMConditionalBranchNode conditionalBranchNode = (LLVMConditionalBranchNode) controlFlowNode;
                boolean condition = conditionalBranchNode.executeCondition(frame);
                if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(LLVMConditionalBranchNode.TRUE_SUCCESSOR), condition)) {
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                        if (conditionalBranchNode.getTrueSuccessor() < basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    conditionalBranchNode.writePhis(frame, LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                    basicBlockIndex = conditionalBranchNode.getTrueSuccessor();
                    continue outer;
                } else {
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                        if (conditionalBranchNode.getFalseSuccessor() < basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    conditionalBranchNode.writePhis(frame, LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                    basicBlockIndex = conditionalBranchNode.getFalseSuccessor();
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMSwitchNode) {
                LLVMSwitchNode switchNode = (LLVMSwitchNode) controlFlowNode;
                Object condition = switchNode.executeCondition(frame);
                int[] successors = switchNode.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    Object caseValue = switchNode.cases[i].executeGeneric(frame);
                    assert caseValue.getClass() == condition.getClass() : "must be the same type - otherwise equals might wrongly return false";
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), condition.equals(caseValue))) {
                        if (CompilerDirectives.inInterpreter()) {
                            bb.increaseBranchProbability(i);
                            if (successors[i] < basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        switchNode.writePhis(frame, i);
                        nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                        basicBlockIndex = successors[i];
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                    if (successors[i] < basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                switchNode.writePhis(frame, i);
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                basicBlockIndex = successors[i];
                continue outer;
            } else if (controlFlowNode instanceof LLVMIndirectBranchNode) {
                // TODO (chaeubl): we need a different approach here - this is awfully
                // inefficient (see GR-3664)
                LLVMIndirectBranchNode indirectBranchNode = (LLVMIndirectBranchNode) controlFlowNode;
                int[] successors = indirectBranchNode.getSuccessors();
                int successorBasicBlockIndex = indirectBranchNode.executeCondition(frame);
                for (int i = 0; i < successors.length - 1; i++) {
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), successors[i] == successorBasicBlockIndex)) {
                        if (CompilerDirectives.inInterpreter()) {
                            bb.increaseBranchProbability(i);
                            if (successors[i] < basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        indirectBranchNode.writePhis(frame, i);
                        nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                        basicBlockIndex = successors[i];
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                assert successorBasicBlockIndex == successors[i];
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                    if (successors[i] < basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                indirectBranchNode.writePhis(frame, i);
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                basicBlockIndex = successors[i];
                continue outer;
            } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                if (CompilerDirectives.inInterpreter()) {
                    if (unconditionalNode.getSuccessor() < basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                unconditionalNode.writePhis(frame);
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                basicBlockIndex = unconditionalNode.getSuccessor();
                continue outer;
            } else if (controlFlowNode instanceof LLVMInvokeNode) {
                LLVMInvokeNode invokeNode = (LLVMInvokeNode) controlFlowNode;
                try {
                    invokeNode.execute(frame);
                    if (CompilerDirectives.inInterpreter()) {
                        if (invokeNode.getNormalSuccessor() < basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    invokeNode.writePhis(frame, LLVMInvokeNode.NORMAL_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                    basicBlockIndex = invokeNode.getNormalSuccessor();
                    continue outer;
                } catch (LLVMException e) {
                    invokeNode.handleException(frame, e);
                    if (CompilerDirectives.inInterpreter()) {
                        if (invokeNode.getUnwindSuccessor() < basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    invokeNode.writePhis(frame, LLVMInvokeNode.UNWIND_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                    basicBlockIndex = invokeNode.getUnwindSuccessor();
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMRetNode) {
                LLVMRetNode retNode = (LLVMRetNode) controlFlowNode;
                retNode.execute(frame);
                // writing phis is not necessary
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                basicBlockIndex = retNode.getSuccessor();
                continue outer;
            } else if (controlFlowNode instanceof LLVMResumeNode) {
                LLVMResumeNode resumeNode = (LLVMResumeNode) controlFlowNode;
                // writing phis is not necessary
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                resumeNode.execute(frame);
                CompilerAsserts.neverPartOfCompilation();
                throw new IllegalStateException("must not reach here");
            } else if (controlFlowNode instanceof LLVMUnreachableNode) {
                LLVMUnreachableNode unreachableNode = (LLVMUnreachableNode) controlFlowNode;
                // writing phis is not necessary
                nullDeadSlots(frame, basicBlockIndex, afterSlotNullerNodes);
                unreachableNode.execute();
                CompilerAsserts.neverPartOfCompilation();
                throw new IllegalStateException("must not reach here");
            } else {
                CompilerAsserts.neverPartOfCompilation();
                throw new UnsupportedOperationException("unexpected controlFlowNode type: " + controlFlowNode);
            }
        }
        assert backEdgeCounter >= 0;
        LoopNode.reportLoopCount(this, backEdgeCounter);
        if (returnSlot == null) {
            return null;
        } else {
            return frame.getValue(returnSlot);
        }
    }

    @ExplodeLoop
    private static void nullDeadSlots(VirtualFrame frame, int bci, LLVMStackFrameNuller[][] nuller) {
        LLVMStackFrameNuller[] afterStackNuller = nuller[bci];
        if (afterStackNuller != null) {
            for (int j = 0; j < afterStackNuller.length; j++) {
                afterStackNuller[j].nullifySlot(frame);
            }
        }
    }
}
