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
import com.oracle.truffle.llvm.nodes.base.LLVMFrameNullerUtil;
import com.oracle.truffle.llvm.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.runtime.LLVMException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class LLVMDispatchBasicBlockNode extends LLVMExpressionNode {
    private final FrameSlot exceptionValueSlot;
    @Children private final LLVMBasicBlockNode[] bodyNodes;
    @CompilationFinal(dimensions = 2) private final FrameSlot[][] beforeBlockNuller;
    @CompilationFinal(dimensions = 2) private final FrameSlot[][] afterBlockNuller;

    public LLVMDispatchBasicBlockNode(FrameSlot exceptionValueSlot, LLVMBasicBlockNode[] bodyNodes, FrameSlot[][] beforeBlockNuller, FrameSlot[][] afterBlockNuller) {
        this.exceptionValueSlot = exceptionValueSlot;
        this.bodyNodes = bodyNodes;
        this.beforeBlockNuller = beforeBlockNuller;
        this.afterBlockNuller = afterBlockNuller;
    }

    @Override
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    public Object executeGeneric(VirtualFrame frame) {
        Object returnValue = null;

        CompilerAsserts.compilationConstant(bodyNodes.length);
        int basicBlockIndex = 0;
        int backEdgeCounter = 0;
        outer: while (basicBlockIndex != LLVMBasicBlockNode.RETURN_FROM_FUNCTION) {
            CompilerAsserts.partialEvaluationConstant(basicBlockIndex);
            LLVMBasicBlockNode bb = bodyNodes[basicBlockIndex];

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
                        if (conditionalBranchNode.getTrueSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                    basicBlockIndex = conditionalBranchNode.getTrueSuccessor();
                    nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                    continue outer;
                } else {
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                        if (conditionalBranchNode.getFalseSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                    basicBlockIndex = conditionalBranchNode.getFalseSuccessor();
                    nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
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
                            if (successors[i] <= basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        executePhis(frame, switchNode, i);
                        nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                    if (successors[i] <= basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                executePhis(frame, switchNode, i);
                nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
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
                            if (successors[i] <= basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        executePhis(frame, indirectBranchNode, i);
                        nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                assert successorBasicBlockIndex == successors[i];
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                    if (successors[i] <= basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                executePhis(frame, indirectBranchNode, i);
                nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                continue outer;
            } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                if (CompilerDirectives.inInterpreter()) {
                    if (unconditionalNode.getSuccessor() <= basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                executePhis(frame, unconditionalNode, 0);
                nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                basicBlockIndex = unconditionalNode.getSuccessor();
                nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                continue outer;
            } else if (controlFlowNode instanceof LLVMInvokeNode) {
                LLVMInvokeNode invokeNode = (LLVMInvokeNode) controlFlowNode;
                try {
                    invokeNode.execute(frame);
                    if (CompilerDirectives.inInterpreter()) {
                        if (invokeNode.getNormalSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    executePhis(frame, invokeNode, LLVMInvokeNode.NORMAL_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                    basicBlockIndex = invokeNode.getNormalSuccessor();
                    nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                    continue outer;
                } catch (LLVMException e) {
                    frame.setObject(exceptionValueSlot, e);
                    if (CompilerDirectives.inInterpreter()) {
                        if (invokeNode.getUnwindSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    executePhis(frame, invokeNode, LLVMInvokeNode.UNWIND_SUCCESSOR);
                    nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                    basicBlockIndex = invokeNode.getUnwindSuccessor();
                    nullDeadSlots(frame, basicBlockIndex, beforeBlockNuller);
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMRetNode) {
                LLVMRetNode retNode = (LLVMRetNode) controlFlowNode;
                returnValue = retNode.execute(frame);
                assert noPhisNecessary(retNode);
                nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                basicBlockIndex = retNode.getSuccessor();
                continue outer;
            } else if (controlFlowNode instanceof LLVMResumeNode) {
                LLVMResumeNode resumeNode = (LLVMResumeNode) controlFlowNode;
                assert noPhisNecessary(resumeNode);
                nullDeadSlots(frame, basicBlockIndex, afterBlockNuller);
                resumeNode.execute(frame);
                CompilerAsserts.neverPartOfCompilation();
                throw new IllegalStateException("must not reach here");
            } else if (controlFlowNode instanceof LLVMUnreachableNode) {
                LLVMUnreachableNode unreachableNode = (LLVMUnreachableNode) controlFlowNode;
                assert noPhisNecessary(unreachableNode);
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
        return returnValue;
    }

    @ExplodeLoop
    private static void executePhis(VirtualFrame frame, LLVMControlFlowNode controlFlowNode, int successorIndex) {
        LLVMExpressionNode[] phiWrites = controlFlowNode.getPhiNodes(successorIndex);
        if (phiWrites != null) {
            for (int i = 0; i < phiWrites.length; i++) {
                phiWrites[i].executeGeneric(frame);
            }
        }
    }

    @ExplodeLoop
    private static void nullDeadSlots(VirtualFrame frame, int bci, FrameSlot[][] blockNullers) {
        FrameSlot[] frameSlotsToNull = blockNullers[bci];
        if (frameSlotsToNull != null) {
            for (int i = 0; i < frameSlotsToNull.length; i++) {
                LLVMFrameNullerUtil.nullFrameSlot(frame, frameSlotsToNull[i]);
            }
        }
    }

    private static boolean noPhisNecessary(LLVMControlFlowNode controlFlowNode) {
        return controlFlowNode.getSuccessorCount() == 0 || controlFlowNode.getSuccessorCount() == 1 && controlFlowNode.getPhiNodes(0) == null;
    }
}
