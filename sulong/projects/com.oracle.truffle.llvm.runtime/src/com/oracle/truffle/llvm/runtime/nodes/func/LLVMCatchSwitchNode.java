/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.func;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions.SulongCanCatchWindowsNode;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException.LLVMUserExceptionWindows;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNodeFactory.CatchPadEntryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNodeFactory.CopyExceptionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNodeFactory.LLVMCatchSwitchImplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

@GenerateWrapper
public abstract class LLVMCatchSwitchNode extends LLVMControlFlowNode {

    public static LLVMCatchSwitchNode create(int exceptionSlot, int[] targetBlocks, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
        return create(exceptionSlot, targetBlocks, -1, getStack, phiNodes);
    }

    public static LLVMCatchSwitchNode create(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
        return LLVMCatchSwitchImplNodeGen.create(exceptionSlot, targetBlocks, unwindBlock, getStack, phiNodes);
    }

    public abstract Object executeCondition(VirtualFrame frame);

    public abstract boolean checkCase(VirtualFrame frame, int i, Object value);

    public abstract int getConditionalSuccessorCount();

    public abstract boolean hasDefaultBlock();

    public abstract void throwException(VirtualFrame frame);

    public abstract void setCatchPadEntryNode(CatchPadEntryNode node);

    @Override
    public WrapperNode createWrapper(ProbeNode probeNode) {
        return new LLVMCatchSwitchNodeWrapper(this, probeNode);
    }

    public abstract static class LLVMCatchSwitchImpl extends LLVMCatchSwitchNode {

        @Children private final LLVMStatementNode[] phiNodes;
        @Child private LLVMExpressionNode getStack;

        @Children private final CatchPadEntryNode[] conditions;
        @CompilationFinal(dimensions = 1) private final int[] targetBlocks;
        @CompilationFinal private final int unwindBlock;
        @CompilationFinal private final int exceptionSlot;
        private int nextNode = 0;

        protected LLVMCatchSwitchImpl(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
            this.targetBlocks = targetBlocks;
            this.phiNodes = phiNodes;
            this.conditions = new CatchPadEntryNode[targetBlocks.length];
            this.unwindBlock = unwindBlock;
            this.getStack = getStack;
            this.exceptionSlot = exceptionSlot;
        }

        @Override
        public void setCatchPadEntryNode(CatchPadEntryNode node) {
            conditions[nextNode++] = node;
        }

        @Specialization
        public Object doCondition(VirtualFrame frame) {
            // by the time we are executing code all the conditions should be set
            assert nextNode == conditions.length;

            return frame.getObject(exceptionSlot);
        }

        @Override
        public void throwException(VirtualFrame frame) {
            throw (LLVMUserException) frame.getObject(exceptionSlot);
        }

        @Override
        public boolean hasDefaultBlock() {
            return unwindBlock >= 0;
        }

        @Override
        public int getConditionalSuccessorCount() {
            return hasDefaultBlock() ? conditions.length - 1 : conditions.length;
        }

        @Override
        public boolean checkCase(VirtualFrame frame, int i, Object value) {
            assert value instanceof LLVMUserExceptionWindows;
            LLVMUserExceptionWindows exception = (LLVMUserExceptionWindows) value;
            LLVMStack stack = (LLVMStack) getStack.executeGeneric(frame);

            return conditions[i].execute(frame, stack, exception.getExceptionObject(), exception.getUnwindHeader(), exception.getImageBase(), exception.getStackPointer());
        }

        @Override
        public int getSuccessorCount() {
            return targetBlocks.length;
        }

        @Override
        public int[] getSuccessors() {
            return targetBlocks;
        }

        @Override
        public LLVMStatementNode getPhiNode(int successorIndex) {
            return phiNodes[successorIndex];
        }
    }

    public abstract static class CopyExceptionNode extends LLVMNode {
        private final PointerType exceptionType;

        protected CopyExceptionNode(PointerType exceptionType) {
            this.exceptionType = exceptionType;
        }

        public abstract void execute(LLVMPointer thrownObject, boolean isReference, Object exceptionSlot);

        @TruffleBoundary
        protected long getSize() {
            Type pointeeType = exceptionType.getPointeeType();
            try {
                return pointeeType.getSize(getDataLayout());
            } catch (TypeOverflowException e) {
                throw new LLVMParserException(this, "Error determining pointee type size.");
            }
        }

        protected PrimitiveKind getPrimitiveKind() {
            Type pointeeType = exceptionType.getPointeeType();
            return pointeeType instanceof PrimitiveType ? ((PrimitiveType) pointeeType).getPrimitiveKind() : null;
        }

        protected boolean isPointer() {
            Type pointeeType = exceptionType.getPointeeType();
            return pointeeType instanceof PointerType;
        }

        @Specialization(guards = {"getSize() == 1", "!isReference"})
        protected void doByte(LLVMPointer thrownObject, @SuppressWarnings("unused") boolean isReference, Object exceptionSlot,
                        @Cached LLVMI8LoadNode loadNode, @Cached LLVMI8StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = {"getSize() == 2", "!isReference"})
        protected void doShort(LLVMPointer thrownObject, @SuppressWarnings("unused") boolean isReference, Object exceptionSlot,
                        @Cached LLVMI16LoadNode loadNode, @Cached LLVMI16StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = {"getSize() == 4", "!isReference"})
        protected void doInt(LLVMPointer thrownObject, @SuppressWarnings("unused") boolean isReference, Object exceptionSlot,
                        @Cached LLVMI32LoadNode loadNode, @Cached LLVMI32StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = {"getSize() == 8", "!isReference"})
        protected void doLong(LLVMPointer thrownObject, @SuppressWarnings("unused") boolean isReference, LLVMPointer exceptionSlot,
                        @Cached LLVMI64LoadNode loadNode, @Cached LLVMI64StoreNode storeNode) {
            Object value = loadNode.executeWithTargetGeneric(thrownObject);
            storeNode.executeWithTarget(exceptionSlot, value);
        }

        @Specialization(guards = {"getSize() > 8 || isReference"})
        protected void doPointer(LLVMPointer thrownObject, @SuppressWarnings("unused") boolean isReference, Object exceptionSlot,
                        @Cached LLVMPointerStoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, thrownObject);
        }

        public static CopyExceptionNode create(PointerType exceptionType) {
            return CopyExceptionNodeGen.create(exceptionType);
        }
    }

    @GenerateWrapper
    @NodeChild(value = "stack", type = LLVMExpressionNode.class)
    @NodeChild(value = "thrownObject", type = LLVMExpressionNode.class)
    @NodeChild(value = "throwInfo", type = LLVMExpressionNode.class)
    @NodeChild(value = "imageBase", type = LLVMExpressionNode.class)
    @NodeChild(value = "catchType", type = LLVMExpressionNode.class)
    @NodeChild(value = "exceptionSlot", type = LLVMExpressionNode.class)
    @NodeChild(value = "stackPointer", type = LLVMExpressionNode.class)
    public abstract static class CatchPadEntryNode extends LLVMInstrumentableNode {
        protected final PointerType exceptionType;
        // See ehdata_values.h lines 81 and following for the meaning of the attributes
        protected final long attributes;

        protected CatchPadEntryNode(PointerType exceptionType, long attributes) {
            this.exceptionType = exceptionType;
            this.attributes = attributes;
        }

        protected CatchPadEntryNode(CatchPadEntryNode node) {
            this(node.exceptionType, node.attributes);
        }

        @Child private SulongCanCatchWindowsNode canCatch;
        @Child private SulongCanCatchWindowsNode exceptionSlot;

        abstract boolean execute(VirtualFrame frame, LLVMStack stack, LLVMPointer thrownObject, LLVMPointer throwInfo, LLVMPointer imageBase, long stackPointer);

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new CatchPadEntryNodeWrapper(this, this, probe);
        }

        public LLVMBitcodeLibraryFunctions.SulongCanCatchWindowsNode getCanCatch() {
            if (canCatch == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                this.canCatch = insert(new LLVMBitcodeLibraryFunctions.SulongCanCatchWindowsNode(getContext()));
            }
            return canCatch;
        }

        protected CopyExceptionNode createExceptionNode() {
            return CopyExceptionNode.create(exceptionType);
        }

        public boolean isConstant() {
            return (attributes & 0x01) != 0;
        }

        public boolean isVolatile() {
            return (attributes & 0x02) != 0;
        }

        public boolean isUnaligned() {
            return (attributes & 0x04) != 0;
        }

        public boolean isReference() {
            return (attributes & 0x08) != 0;
        }

        @Specialization
        public boolean doExecute(LLVMStack stack, LLVMPointer thrownObject, LLVMPointer throwInfo, LLVMPointer imageBase, long stackPointer, LLVMPointer catchType, LLVMPointer exceptionSlotPointer,
                        @Cached("createExceptionNode()") CopyExceptionNode copyException) {
            if (catchType.isNull() || getCanCatch().canCatch(stack, thrownObject, throwInfo, catchType, imageBase)) {
                if (!exceptionSlotPointer.isNull()) {
                    copyException.execute(thrownObject, isReference(), exceptionSlotPointer);
                }
                stack.setStackPointer(stackPointer);
                return true;
            }

            return false;
        }
    }

    public static CatchPadEntryNode createCatchPadEntryNode(LLVMExpressionNode catchType, long attributes, LLVMExpressionNode exceptionSlot, PointerType exceptionType) {
        return CatchPadEntryNodeGen.create(exceptionType, attributes, null, null, null, null, null, catchType, exceptionSlot);
    }
}
