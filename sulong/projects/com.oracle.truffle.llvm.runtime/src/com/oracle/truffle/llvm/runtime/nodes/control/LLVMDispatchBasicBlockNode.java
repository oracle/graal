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
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMFrameNullerUtil;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNode;
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

    private final SulongEngineOption.OSRMode osrMode;
    @CompilerDirectives.CompilationFinal private Object osrMetadata;

    public LLVMDispatchBasicBlockNode(int exceptionValueSlot, LLVMBasicBlockNode[] bodyNodes, int loopSuccessorSlot, LocalVariableDebugInfo debugInfo, SulongEngineOption.OSRMode osrMode) {
        this.exceptionValueSlot = exceptionValueSlot;
        this.bodyNodes = bodyNodes;
        this.loopSuccessorSlot = loopSuccessorSlot;
        this.debugInfo = debugInfo;
        this.osrMode = osrMode;
    }

    @Override
    public String toString() {
        return getRootNode() != null ? getRootNode().toString() : "<OSR>";
    }

    public LocalVariableDebugInfo getDebugInfo() {
        return debugInfo;
    }

    @Specialization
    public Object doDispatch(VirtualFrame frame) {
        return dispatchFromBasicBlock(frame, 0, new Counters());
    }

    /**
     * The code in this function is mirrored in {@link LLVMLoopDispatchNode}, any changes need to be
     * done in both places.
     */
    @ExplodeLoop(kind = LoopExplosionKind.MERGE_EXPLODE)
    private Object dispatchFromBasicBlock(VirtualFrame frame, int bci, Counters counters) {
        Object returnValue = null;
        int basicBlockIndex = bci;

        CompilerAsserts.partialEvaluationConstant(bodyNodes.length);
        outer: while (basicBlockIndex != LLVMBasicBlockNode.RETURN_FROM_FUNCTION) {
            CompilerAsserts.partialEvaluationConstant(basicBlockIndex);
            if (CompilerDirectives.hasNextTier()) {
                if (basicBlockIndex <= counters.previousBasicBlockIndex) {
                    TruffleSafepoint.poll(this);
                    counters.backEdgeCounter++;
                    if (CompilerDirectives.inInterpreter() && osrMode == SulongEngineOption.OSRMode.BYTECODE && BytecodeOSRNode.pollOSRBackEdge(this)) {
                        ensureAllFrameSlotsInitialized(frame);
                        returnValue = BytecodeOSRNode.tryOSR(this, basicBlockIndex, counters, null, frame);
                        if (returnValue != null) {
                            break outer;
                        }
                    }
                }
                counters.previousBasicBlockIndex = basicBlockIndex; // remember this block for next
                // iteration
            }
            LLVMBasicBlockNode bb = bodyNodes[basicBlockIndex];

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
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.TRUE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getTrueSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                } else {
                    bb.enterSuccessor(LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, conditionalBranchNode, LLVMConditionalBranchNode.FALSE_SUCCESSOR);
                    basicBlockIndex = conditionalBranchNode.getFalseSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                }
            } else if (controlFlowNode instanceof LLVMCatchSwitchNode) {
                LLVMCatchSwitchNode switchNode = (LLVMCatchSwitchNode) controlFlowNode;
                Object condition = switchNode.executeCondition(frame);
                int[] successors = switchNode.getSuccessors();
                int successorCount = switchNode.getConditionalSuccessorCount();
                for (int i = 0; i < successorCount; i++) {
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), switchNode.checkCase(frame, i, condition))) {
                        bb.enterSuccessor(i);
                        nullDeadSlots(frame, bb.nullableAfter);
                        executePhis(frame, switchNode, i);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                        continue outer;
                    }
                }

                if (switchNode.hasDefaultBlock()) {
                    int i = successors.length - 1;
                    bb.enterSuccessor(i);
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, switchNode, i);
                    basicBlockIndex = successors[i];
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                } else {
                    switchNode.throwException(frame);
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw new IllegalStateException("must not reach here");
                }
            } else if (controlFlowNode instanceof LLVMSwitchNode) {
                LLVMSwitchNode switchNode = (LLVMSwitchNode) controlFlowNode;
                Object condition = switchNode.executeCondition(frame);
                int[] successors = switchNode.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    if (CompilerDirectives.injectBranchProbability(bb.getBranchProbability(i), switchNode.checkCase(frame, i, condition))) {
                        bb.enterSuccessor(i);
                        nullDeadSlots(frame, bb.nullableAfter);
                        executePhis(frame, switchNode, i);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                bb.enterSuccessor(i);
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, switchNode, i);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMLoopNode) {
                LLVMLoopNode loop = (LLVMLoopNode) controlFlowNode;
                loop.executeLoop(frame);
                int successorBasicBlockIndex = frame.getInt(loopSuccessorSlot);
                frame.setInt(loopSuccessorSlot, 0); // null frame
                int[] successors = loop.getSuccessors();
                for (int i = 0; i < successors.length - 1; i++) {
                    if (successorBasicBlockIndex == successors[i]) {
                        basicBlockIndex = successors[i];
                        continue outer;
                    }
                }
                int i = successors.length - 1;
                assert successors[i] == successorBasicBlockIndex : "Could not find loop successor!";
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
                        bb.enterSuccessor(i);
                        nullDeadSlots(frame, bb.nullableAfter);
                        executePhis(frame, indirectBranchNode, i);
                        basicBlockIndex = successors[i];
                        nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                        continue outer;
                    }
                }

                int i = successors.length - 1;
                assert successorBasicBlockIndex == successors[i];
                bb.enterSuccessor(i);
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, indirectBranchNode, i);
                basicBlockIndex = successors[i];
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMBrUnconditionalNode) {
                LLVMBrUnconditionalNode unconditionalNode = (LLVMBrUnconditionalNode) controlFlowNode;
                unconditionalNode.execute(frame); // required for instrumentation
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, unconditionalNode, 0);
                basicBlockIndex = unconditionalNode.getSuccessor();
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMCatchReturnNode) {
                LLVMCatchReturnNode returnNode = (LLVMCatchReturnNode) controlFlowNode;
                returnNode.execute(frame);
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, returnNode, 0);
                basicBlockIndex = returnNode.getSuccessor();
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMCleanupReturnNode) {
                LLVMCleanupReturnNode returnNode = (LLVMCleanupReturnNode) controlFlowNode;
                returnNode.execute(frame);
                nullDeadSlots(frame, bb.nullableAfter);
                executePhis(frame, returnNode, 0);
                basicBlockIndex = returnNode.getSuccessor();
                nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                continue outer;
            } else if (controlFlowNode instanceof LLVMInvokeNode) {
                LLVMInvokeNode invokeNode = (LLVMInvokeNode) controlFlowNode;
                try {
                    invokeNode.execute(frame);
                    bb.enterSuccessor(LLVMInvokeNode.NORMAL_SUCCESSOR);
                    nullDeadSlots(frame, bb.nullableAfter);
                    executePhis(frame, invokeNode, LLVMInvokeNode.NORMAL_SUCCESSOR);
                    basicBlockIndex = invokeNode.getNormalSuccessor();
                    nullDeadSlots(frame, bodyNodes[basicBlockIndex].nullableBefore);
                    continue outer;
                } catch (LLVMUserException e) {
                    bb.enterSuccessor(LLVMInvokeNode.UNWIND_SUCCESSOR);
                    frame.setObject(exceptionValueSlot, e);
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
        // only report non-zero counters to reduce interpreter overhead
        if (CompilerDirectives.hasNextTier() && counters.backEdgeCounter != 0) {
            LoopNode.reportLoopCount(this, counters.backEdgeCounter > 0 ? counters.backEdgeCounter : Integer.MAX_VALUE);
        }
        assert returnValue != null;
        return returnValue;
    }

    private static void ensureAllFrameSlotsInitialized(VirtualFrame frame) {
        FrameDescriptor frameDescriptor = frame.getFrameDescriptor();
        for (int i = 0; i < frameDescriptor.getNumberOfSlots(); i++) {
            if (frame.getTag(i) == 0 && frameDescriptor.getSlotKind(i).tag != 0) {
                switch (frameDescriptor.getSlotKind(i)) {
                    case Object:
                        frame.setObject(i, null);
                        break;
                    case Long:
                        frame.setLong(i, 0L);
                        break;
                    case Int:
                        frame.setInt(i, 0);
                        break;
                    case Double:
                        frame.setDouble(i, 0d);
                        break;
                    case Float:
                        frame.setFloat(i, 0f);
                        break;
                    case Boolean:
                        frame.setBoolean(i, false);
                        break;
                    case Byte:
                        frame.setByte(i, (byte) 0);
                        break;
                }
            }
        }
    }

    /**
     * Smaller than int[1], does not kill int[] on write and doesn't need bounds checks.
     */
    private static final class Counters {
        /*
         * Maintain backEdgeCounter in Counter so that the compiler does not confuse it with the
         * basicBlockIndex because both are constant within the loop (GR-35072).
         */
        int backEdgeCounter;
        int previousBasicBlockIndex = Integer.MIN_VALUE;
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
        return dispatchFromBasicBlock(osrFrame, target, (Counters) interpreterState);
    }

    @Override
    public final Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
        /*
         * We need to forward the argumnts array since it might be used by va_start.
         */
        Object[] args = parentFrame.getArguments();
        /*
         * The first argument is the stack pointer. This is only used in the method prologue. When
         * we transfer to an OSR compilation, this has already happened, so we can safely reuse the
         * first argument slot for that.
         *
         * Note that the parentFrame comes from the interpreter, so it's not actually virtual.
         * Therefore, no need to materialize the frame, even though it escapes here.
         */
        args[0] = parentFrame;
        return args;
    }

    @Override
    public final Frame restoreParentFrameFromArguments(Object[] arguments) {
        return (Frame) arguments[0];
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
    public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        /*
         * We know we're at the return block index, so the only slots left alive should be the stack
         * related slots.
         */
        parentFrame.setObject(LLVMStack.STACK_ID, osrFrame.getObject(LLVMStack.STACK_ID));
        parentFrame.setObject(LLVMStack.UNIQUES_REGION_ID, osrFrame.getObject(LLVMStack.UNIQUES_REGION_ID));
        if (osrFrame.isLong(LLVMStack.BASE_POINTER_ID)) {
            // might not be initialized at all
            parentFrame.setLong(LLVMStack.BASE_POINTER_ID, osrFrame.getLong(LLVMStack.BASE_POINTER_ID));
        }
        int nr = getRootNode().getFrameDescriptor().getNumberOfSlots();
        for (int i = LLVMStack.BASE_POINTER_ID + 1; i < nr; i++) {
            parentFrame.clear(i);
        }
    }
}
