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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions;
import com.oracle.truffle.llvm.runtime.LLVMBitcodeLibraryFunctions.SulongCanCatchWindowsNode;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException;
import com.oracle.truffle.llvm.runtime.except.LLVMUserException.LLVMUserExceptionWindows;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMInstrumentableNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMSwitchNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNodeFactory.CatchPadEntryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNodeFactory.CopyExceptionNodeGen;
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

public class LLVMCatchSwitchNode extends LLVMSwitchNode {

    @Children private final LLVMStatementNode[] phiNodes;
    @Child private LLVMExpressionNode getStack;

    @Children private final CatchPadEntryNode[] conditions;
    @CompilationFinal(dimensions = 1) private final int[] targetBlocks;
    @CompilationFinal private final int unwindBlock;
    @CompilationFinal private final int exceptionSlot;
    private int nextNode = 0;

    protected LLVMCatchSwitchNode(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
        this.targetBlocks = targetBlocks;
        this.phiNodes = phiNodes;
        this.conditions = new CatchPadEntryNode[targetBlocks.length];
        this.unwindBlock = unwindBlock;
        this.getStack = getStack;
        this.exceptionSlot = exceptionSlot;
    }

    public static LLVMCatchSwitchNode create(int exceptionSlot, int[] targetBlocks, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
        return create(exceptionSlot, targetBlocks, -1, getStack, phiNodes);
    }

    public static LLVMCatchSwitchNode create(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiNodes) {
        return new LLVMCatchSwitchNode(exceptionSlot, targetBlocks, unwindBlock, getStack, phiNodes);
    }

    public void setCatchPadEntryNode(CatchPadEntryNode node) {
        conditions[nextNode++] = node;
    }

    @Override
    public Object executeCondition(VirtualFrame frame) {
        // by the time we are executing code all the conditions should be set
        assert nextNode == conditions.length;

        return frame.getObject(exceptionSlot);
    }

    public void throwException(VirtualFrame frame) {
        throw (LLVMUserException) frame.getObject(exceptionSlot);
    }

    public boolean hasDefaultBlock() {
        return unwindBlock >= 0;
    }

    public int getConditionalSuccessorCount() {
        return hasDefaultBlock() ? conditions.length - 1 : conditions.length;
    }

    @Override
    public boolean checkCase(VirtualFrame frame, int i, Object value) {
        assert value instanceof LLVMUserExceptionWindows;
        LLVMUserExceptionWindows exception = (LLVMUserExceptionWindows) value;
        LLVMStack stack = (LLVMStack) getStack.executeGeneric(frame);

        return conditions[i].execute(frame, stack, exception.getExceptionObject(), exception.getUnwindHeader(), exception.getImageBase());
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

    public abstract static class CopyExceptionNode extends LLVMNode {
        private final PointerType exceptionType;

        protected CopyExceptionNode(PointerType exceptionType) {
            this.exceptionType = exceptionType;
        }

        public abstract void execute(LLVMPointer thrownObject, Object exceptionSlot);

        protected long getSize() {
            Type pointeeType = exceptionType.getPointeeType();
            try {
                return pointeeType.getSize(getDataLayout());
            } catch (TypeOverflowException e) {
                return 16;
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

        @Specialization(guards = "getSize() == 1")
        protected void doByte(LLVMPointer thrownObject, Object exceptionSlot,
                        @Cached LLVMI8LoadNode loadNode, @Cached LLVMI8StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = "getSize() == 2")
        protected void doShort(LLVMPointer thrownObject, Object exceptionSlot,
                        @Cached LLVMI16LoadNode loadNode, @Cached LLVMI16StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = "getSize() == 4")
        protected void doInt(LLVMPointer thrownObject, Object exceptionSlot,
                        @Cached LLVMI32LoadNode loadNode, @Cached LLVMI32StoreNode storeNode) {
            storeNode.executeWithTarget(exceptionSlot, loadNode.executeWithTargetGeneric(thrownObject));
        }

        @Specialization(guards = {"getSize() == 8", "!isPointer()"})
        protected void doLong(LLVMPointer thrownObject, LLVMPointer exceptionSlot,
                        @Cached LLVMI64LoadNode loadNode, @Cached LLVMI64StoreNode storeNode) {
            Object value = loadNode.executeWithTargetGeneric(thrownObject);
            storeNode.executeWithTarget(exceptionSlot, value);
        }

        @Specialization(guards = {"getSize() >= 8", "isPointer()"})
        protected void doPointer(LLVMPointer thrownObject, Object exceptionSlot,
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
    public abstract static class CatchPadEntryNode extends LLVMInstrumentableNode {
        protected final PointerType exceptionType;
        protected final long offset;

        protected CatchPadEntryNode(PointerType exceptionType, long offset) {
            this.exceptionType = exceptionType;
            this.offset = offset;
        }

        protected CatchPadEntryNode(CatchPadEntryNode node) {
            this(node.exceptionType, node.offset);
        }

        @Child private SulongCanCatchWindowsNode canCatch;
        @Child private SulongCanCatchWindowsNode exceptionSlot;

        abstract boolean execute(VirtualFrame frame, LLVMStack stack, LLVMPointer thrownObject, LLVMPointer throwInfo, LLVMPointer imageBase);

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

        @Specialization
        public boolean doExecute(LLVMStack stack, LLVMPointer thrownObject, LLVMPointer throwInfo, LLVMPointer imageBase, LLVMPointer catchType, LLVMPointer exceptionSlotPointer,
                        @Cached("createExceptionNode()") CopyExceptionNode copyException) {
            if (catchType.isNull() || getCanCatch().canCatch(stack, thrownObject, throwInfo, catchType, imageBase)) {
                copyException.execute(thrownObject, exceptionSlotPointer);
                return true;
            }

            return false;
        }
    }

    public static CatchPadEntryNode createCatchPadEntryNode(LLVMExpressionNode catchType, long offset, LLVMExpressionNode exceptionSlot, PointerType exceptionType) {
        return CatchPadEntryNodeGen.create(exceptionType, offset, null, null, null, null, catchType, exceptionSlot);
    }
}
