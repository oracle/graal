/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMFrameNullerUtil;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMResumeNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnreachableNode;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;

public abstract class LLVMDispatchBasicBlockNode extends LLVMExpressionNode {

    private final FrameSlot exceptionValueSlot;
    private final LocalVariableDebugInfo debugInfo;

    @Children private final LLVMBasicBlockNode[] bodyNodes;

    private final FrameSlot loopSuccessorSlot;

    public LLVMDispatchBasicBlockNode(FrameSlot exceptionValueSlot, LLVMBasicBlockNode[] bodyNodes, FrameSlot loopSuccessorSlot, LocalVariableDebugInfo debugInfo) {
        this.exceptionValueSlot = exceptionValueSlot;
        this.bodyNodes = bodyNodes;
        this.loopSuccessorSlot = loopSuccessorSlot;
        this.debugInfo = debugInfo;
    }

    public LocalVariableDebugInfo getDebugInfo() {
        return debugInfo;
    }

    /**
     * The code in this function is mirrored in {@link LLVMLoopDispatchNode}, any changes need to be
     * done in both places.
     */
    @Specialization
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    public Object doDispatch(VirtualFrame frame) {
        Object returnValue = null;

        CompilerAsserts.partialEvaluationConstant(bodyNodes.length);
        int basicBlockIndex = 0;
        int backEdgeCounter = 0;
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
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                        if (conditionalBranchNode.getTrueSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getTrueSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                } else {
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                        if (conditionalBranchNode.getFalseSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getFalseSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMSwitchNode) {
                LLVMSwitchNode switchNode = (LLVMSwitchNode) controlFlowNode;
                Object condition = switchNode.executeCondition(frame);
                int[] successors = switchNode.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), switchNode.checkCase(frame, i, condition))) {
                        if (CompilerDirectives.inInterpreter()) {
                            bb.increaseBranchProbability(i);
                            if (successors[i] <= basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        nullDeadSlots(frame, bb.nullableAfter);
                        executePhis(frame, switchNode, i);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
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
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, switchNode, i);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMLoopNode) {
                LLVMLoopNode loop = (LLVMLoopNode) controlFlowNode;
                loop.executeLoop(frame);
                int successorBasicBlockIndex = FrameUtil.getIntSafe(frame, loopSuccessorSlot);
                frame.setInt(loopSuccessorSlot, 0); // null frame
                int[] successors = loop.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    if (successorBasicBlockIndex == successors[i]) {
                        if (CompilerDirectives.inInterpreter()) {
                            if (successors[i] <= basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        basicBlockIndex = successors[i];
                        continue outer;
                    }
                }
                int i = successors.length - 1;
                assert successors[i] == successorBasicBlockIndex : "Could not find loop successor!";
                if (CompilerDirectives.inInterpreter()) {
                    if (successors[i] <= basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
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
                            if (successors[i] <= basicBlockIndex) {
                                backEdgeCounter++;
                            }
                        }
                        nullDeadSlots(frame, bb.nullableAfter);
                        executePhis(frame, indirectBranchNode, i);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
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
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, indirectBranchNode, i);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                if (CompilerDirectives.inInterpreter()) {
                    if (unconditionalNode.getSuccessor() <= basicBlockIndex) {
                        backEdgeCounter++;
                    }
                }
                unconditionalNode.execute(frame); // required for instrumentation
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, unconditionalNode, 0);
                basicBlockIndex = unconditionalNode.getSuccessor();
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
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
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, invokeNode, LLVMInvokeNode.NORMAL_SUCCESSOR);
                    basicBlockIndex = invokeNode.getNormalSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                } catch (LLVMUserException e) {
                    frame.setObject(exceptionValueSlot, e);
                    if (CompilerDirectives.inInterpreter()) {
                        if (invokeNode.getUnwindSuccessor() <= basicBlockIndex) {
                            backEdgeCounter++;
                        }
                    }
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, invokeNode, LLVMInvokeNode.UNWIND_SUCCESSOR);
                    basicBlockIndex = invokeNode.getUnwindSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMRetNode) {
                LLVMRetNode retNode = (LLVMRetNode) controlFlowNode;
                returnValue = retNode.execute(frame);
                assert noPhisNecessary(retNode);
                nullDeadSlots(frame, bb.nullableAfter);
                basicBlockIndex = retNode.getSuccessor();
                continue outer;
            } else if (controlFlowNode instanceof LLVMResumeNode) {
                LLVMResumeNode resumeNode = (LLVMResumeNode) controlFlowNode;
                assert noPhisNecessary(resumeNode);
                nullDeadSlots(frame, bb.nullableAfter);
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
        assert backEdgeCounter >= 0;
        LoopNode.reportLoopCount(this, backEdgeCounter);
        assert returnValue != null;
        return returnValue;
    }

    @ExplodeLoop
    public static void executePhis(VirtualFrame frame, LLVMControlFlowNode controlFlowNode, int successorIndex) {
        LLVMStatementNode phi = controlFlowNode.getPhiNode(successorIndex);
        if (phi != null) {
            phi.execute(frame);
        }
    }

    @ExplodeLoop
    public static void nullDeadSlots(VirtualFrame frame, FrameSlot[] frameSlotsToNull) {
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
}
