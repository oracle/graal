/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNode;

public final class LLVMLoopDispatchNode extends LLVMNode implements RepeatingNode {
    private final FrameSlot exceptionValueSlot;
    private final int headerId;
    @Children private final LLVMBasicBlockNode[] bodyNodes;
    @CompilationFinal(dimensions = 1) private final int[] indexMapping;
    @CompilationFinal(dimensions = 1) private final int[] loopSuccessors;
    private final FrameSlot successorSlot;
    @CompilationFinal(dimensions = 1) private final LLVMBasicBlockNode[] originalBodyNodes;

    public LLVMLoopDispatchNode(FrameSlot exceptionValueSlot, LLVMBasicBlockNode[] bodyNodes, LLVMBasicBlockNode[] originalBodyNodes, int headerId, int[] indexMapping, int[] successors,
                    FrameSlot successorSlot) {
        this.exceptionValueSlot = exceptionValueSlot;
        this.bodyNodes = bodyNodes;
        this.originalBodyNodes = originalBodyNodes;
        this.indexMapping = indexMapping;
        this.headerId = headerId;
        this.loopSuccessors = successors;
        this.successorSlot = successorSlot;
    }

    @ExplodeLoop
    private boolean isInLoop(int bci) {
        for (int i : loopSuccessors) {
            if (i == bci) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
        throw new IllegalStateException();
    }

    /**
     * The code in this function is mirrored from {@link LLVMDispatchBasicBlockNode}, any changes
     * need to be done in both places. The block id of the successor block (where to continue after
     * the loop) is stored in a frame slot.
     */
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    @Override
    public Object executeRepeatingWithValue(VirtualFrame frame) {

        CompilerAsserts.partialEvaluationConstant(bodyNodes.length);
        int basicBlockIndex = headerId;
        // do-while loop fails at PE
        outer: while (true) {
            CompilerAsserts.partialEvaluationConstant(basicBlockIndex);
            LLVMBasicBlockNode bb = bodyNodes[indexMapping[basicBlockIndex]];

            // lazily insert the basic block into the AST
            bb.initialize();

            // the newly inserted block may have been instrumented
            bb = bodyNodes[indexMapping[basicBlockIndex]];

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
                    }
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                    LLVMDispatchBasicBlockNode.executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getTrueSuccessor();
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                    if (basicBlockIndex == headerId) {
                        return RepeatingNode.CONTINUE_LOOP_STATUS;
                    }
                    if (!isInLoop(basicBlockIndex)) {
                        frame.setInt(successorSlot, basicBlockIndex);
                        return RepeatingNode.BREAK_LOOP_STATUS;
                    }
                    continue outer;
                } else {
                    if (CompilerDirectives.inInterpreter()) {
                        bb.increaseBranchProbability(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    }
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                    LLVMDispatchBasicBlockNode.executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getFalseSuccessor();
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                    if (basicBlockIndex == headerId) {
                        return RepeatingNode.CONTINUE_LOOP_STATUS;
                    }
                    if (!isInLoop(basicBlockIndex)) {
                        frame.setInt(successorSlot, basicBlockIndex);
                        return RepeatingNode.BREAK_LOOP_STATUS;
                    }
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
                        }
                        LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                        LLVMDispatchBasicBlockNode.executePhis(frame, switchNode, i);
                        basicBlockIndex = successors[i];
                        LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                        if (basicBlockIndex == headerId) {
                            return RepeatingNode.CONTINUE_LOOP_STATUS;
                        }
                        if (!isInLoop(basicBlockIndex)) {
                            frame.setInt(successorSlot, basicBlockIndex);
                            return RepeatingNode.BREAK_LOOP_STATUS;
                        }
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                }
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                LLVMDispatchBasicBlockNode.executePhis(frame, switchNode, i);
                basicBlockIndex = successors[i];
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                if (basicBlockIndex == headerId) {
                    return RepeatingNode.CONTINUE_LOOP_STATUS;
                }
                if (!isInLoop(basicBlockIndex)) {
                    frame.setInt(successorSlot, basicBlockIndex);
                    return RepeatingNode.BREAK_LOOP_STATUS;
                }
                continue outer;
            } else if (controlFlowNode instanceof LLVMLoopNode) {
                LLVMLoopNode loop = (LLVMLoopNode) controlFlowNode;
                loop.executeLoop(frame);
                int successorBasicBlockIndex = FrameUtil.getIntSafe(frame, successorSlot);
                frame.setInt(successorSlot, 0); // null frame
                int[] successors = loop.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    if (successorBasicBlockIndex == successors[i]) {
                        basicBlockIndex = successors[i];
                        if (basicBlockIndex == headerId) {
                            return RepeatingNode.CONTINUE_LOOP_STATUS;
                        }
                        if (!isInLoop(basicBlockIndex)) {
                            frame.setInt(successorSlot, basicBlockIndex);
                            return RepeatingNode.BREAK_LOOP_STATUS;
                        }
                        continue outer;
                    }
                }
                int i = successors.length - 1;
                assert successors[i] == successorBasicBlockIndex : "Could not find loop successor!";
                basicBlockIndex = successors[i];
                if (basicBlockIndex == headerId) {
                    return RepeatingNode.CONTINUE_LOOP_STATUS;
                }
                if (!isInLoop(basicBlockIndex)) {
                    frame.setInt(successorSlot, basicBlockIndex);
                    return RepeatingNode.BREAK_LOOP_STATUS;
                }
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
                        }
                        LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                        LLVMDispatchBasicBlockNode.executePhis(frame, indirectBranchNode, i);
                        basicBlockIndex = successors[i];
                        LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                        if (basicBlockIndex == headerId) {
                            return RepeatingNode.CONTINUE_LOOP_STATUS;
                        }
                        if (!isInLoop(basicBlockIndex)) {
                            frame.setInt(successorSlot, basicBlockIndex);
                            return RepeatingNode.BREAK_LOOP_STATUS;
                        }
                        continue outer;
                    }
                }
                int i = successors.length - 1;
                assert successorBasicBlockIndex == successors[i];
                if (CompilerDirectives.inInterpreter()) {
                    bb.increaseBranchProbability(i);
                }
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                LLVMDispatchBasicBlockNode.executePhis(frame, indirectBranchNode, i);
                basicBlockIndex = successors[i];
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                if (basicBlockIndex == headerId) {
                    return RepeatingNode.CONTINUE_LOOP_STATUS;
                }
                if (!isInLoop(basicBlockIndex)) {
                    frame.setInt(successorSlot, basicBlockIndex);
                    return RepeatingNode.BREAK_LOOP_STATUS;
                }
                continue outer;
            } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                unconditionalNode.execute(frame); // required for instrumentation
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                LLVMDispatchBasicBlockNode.executePhis(frame, unconditionalNode, 0);
                basicBlockIndex = unconditionalNode.getSuccessor();
                LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                if (basicBlockIndex == headerId) {
                    return RepeatingNode.CONTINUE_LOOP_STATUS;
                }
                if (!isInLoop(basicBlockIndex)) {
                    frame.setInt(successorSlot, basicBlockIndex);
                    return RepeatingNode.BREAK_LOOP_STATUS;
                }
                continue outer;
            } else if (controlFlowNode instanceof LLVMInvokeNode) {
                LLVMInvokeNode invokeNode = (LLVMInvokeNode) controlFlowNode;
                try {
                    invokeNode.execute(frame);
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                    LLVMDispatchBasicBlockNode.executePhis(frame, invokeNode, LLVMInvokeNode.NORMAL_SUCCESSOR);
                    basicBlockIndex = invokeNode.getNormalSuccessor();
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                    if (basicBlockIndex == headerId) {
                        return RepeatingNode.CONTINUE_LOOP_STATUS;
                    }
                    if (!isInLoop(basicBlockIndex)) {
                        frame.setInt(successorSlot, basicBlockIndex);
                        return RepeatingNode.BREAK_LOOP_STATUS;
                    }
                    continue outer;
                } catch (LLVMUserException e) {
                    frame.setObject(exceptionValueSlot, e);
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, bb.nullableAfter);
                    LLVMDispatchBasicBlockNode.executePhis(frame, invokeNode, LLVMInvokeNode.UNWIND_SUCCESSOR);
                    basicBlockIndex = invokeNode.getUnwindSuccessor();
                    LLVMDispatchBasicBlockNode.nullDeadSlots(frame, originalBodyNodes[basicBlockIndex].nullableBefore);
                    if (basicBlockIndex == headerId) {
                        return RepeatingNode.CONTINUE_LOOP_STATUS;
                    }
                    if (!isInLoop(basicBlockIndex)) {
                        frame.setInt(successorSlot, basicBlockIndex);
                        return RepeatingNode.BREAK_LOOP_STATUS;
                    }
                    continue outer;
                }
            } else {    // some control flow nodes should be never part of a loop
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw new UnsupportedOperationException("unexpected controlFlowNode type: " + controlFlowNode);
            }
        }
    }
}
