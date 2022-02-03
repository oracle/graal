/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.except.LLVMAllocationFailureException;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.except.LLVMStackOverflowError;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMRootNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

/**
 * Implements a stack that grows from the top to the bottom. The stack is allocated lazily when it
 * is accessed for the first time.
 */
public final class LLVMStack {

    public static final int STACK_ID = 0;
    public static final int UNIQUES_REGION_ID = 1;
    public static final int BASE_POINTER_ID = 2;

    private static final long MAX_ALLOCATION_SIZE = Integer.MAX_VALUE;

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    private final LLVMContext context;
    private final long stackSize;

    private long lowerBounds;
    private long upperBounds;

    private long stackPointer; // == 0 means no allocated yet

    public LLVMStack(long stackSize, LLVMContext context) {
        this.context = context;
        this.stackSize = stackSize;

        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
    }

    public LLVMContext getContext() {
        return context;
    }

    private boolean isAllocated() {
        return stackPointer != 0;
    }

    /**
     * Implements a stack region dedicated to values which should be bound to unique frame slots.
     * This needs to collect alignments so that the resulting stack area can be aligned sufficiently
     * for all slots.
     */
    public static final class UniquesRegion {

        private long currentSlotOffset = 0;
        private int alignment = LLVMNode.ADDRESS_SIZE_IN_BYTES;
        private boolean finished;

        public long addSlot(long slotSize, int slotAlignment) {
            CompilerAsserts.neverPartOfCompilation();
            assert !finished : "cannot add slots after size was queried";
            assert Long.bitCount(slotAlignment) == 1 : "alignment must be a power of two";

            // align pointer
            long slotOffset = (currentSlotOffset + slotAlignment - 1) & (-slotAlignment);
            currentSlotOffset = slotOffset + slotSize;
            alignment = Integer.highestOneBit(alignment | slotAlignment); // widen overall alignment
            return slotOffset;
        }

        public long getSize() {
            finished = true;
            return currentSlotOffset;
        }

        public boolean isEmpty() {
            return getSize() == 0;
        }

        public int getAlignment() {
            finished = true;
            return alignment;
        }
    }

    @TruffleBoundary
    private void allocate(Node location, LLVMMemory memory) {
        long stackAllocation = memory.allocateMemory(location, stackSize).asNative();
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + stackSize;
        stackPointer = upperBounds & -LLVMNode.ADDRESS_SIZE_IN_BYTES; // enforce aligned initial
                                                                      // stack pointer
        assert stackPointer != 0;
    }

    @TruffleBoundary
    public void free(LLVMMemory memory) {
        if (isAllocated()) {
            memory.free(null, lowerBounds);
            lowerBounds = 0;
            upperBounds = 0;
            stackPointer = 0;
        }
    }

    public abstract static class LLVMStackAccess extends Node implements GenerateAOT.Provider {

        /**
         * To be called when entering into a new {@link VirtualFrame}. Takes the stack instance from
         * the first argument.
         */
        public abstract void executeEnter(VirtualFrame frame);

        /**
         * To be called when entering into a new {@link VirtualFrame}. Provide the stack instance
         * explicitly.
         */
        public abstract void executeEnter(VirtualFrame frame, LLVMStack llvmStack);

        /**
         * To be called when exiting a {@link VirtualFrame}.
         */
        public abstract void executeExit(VirtualFrame frame);

        /**
         * Get the current stack pointer from the stack provided to {@link #executeEnter}.
         */
        public abstract LLVMPointer executeGet(VirtualFrame frame);

        /**
         * Set the current stack pointer in the stack provided to {@link #executeEnter}.
         */
        public abstract void executeSet(VirtualFrame frame, LLVMPointer pointer);

        public abstract LLVMPointer executeAllocate(VirtualFrame frame, long size, int alignment);

        /**
         * Get the stack instance that was provided to {@link #executeEnter}.
         */
        public abstract LLVMStack executeGetStack(VirtualFrame frame);
    }

    /**
     * This holder class prevents the stackAccess object from being inserted as a cache child node.
     */
    public static final class LLVMStackAccessHolder {
        public final LLVMStackAccess stackAccess;

        public LLVMStackAccessHolder(LLVMStackAccess stackAccess) {
            this.stackAccess = stackAccess;
        }
    }

    /**
     * Only a single instance of this node needs (and is allowed to) exist for each
     * {@link LLVMRootNode}.
     */
    public static final class LLVMNativeStackAccess extends LLVMStackAccess {

        private final LLVMMemory memory;
        private final Assumption noBasePointerAssumption;
        @CompilationFinal private boolean hasAllocatedStack;

        public LLVMNativeStackAccess(LLVMMemory memory) {
            this.memory = memory;
            this.noBasePointerAssumption = Truffle.getRuntime().createAssumption("LLVM - no base pointer");
            this.hasAllocatedStack = false;
        }

        @Override
        public void prepareForAOT(TruffleLanguage<?> language, RootNode root) {
            noBasePointerAssumption.invalidate();
        }

        protected void ensureBasePointerSlot(LLVMStack llvmStack) {
            // whenever we access the base pointer, we ensure that the stack was allocated
            if (!llvmStack.isAllocated()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                hasAllocatedStack = true;
                llvmStack.allocate(this, memory);
            }
            if (noBasePointerAssumption.isValid()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                noBasePointerAssumption.invalidate();
            }
        }

        @Override
        public void executeEnter(VirtualFrame frame) {
            executeEnter(frame, (LLVMStack) frame.getArguments()[0]);
        }

        @Override
        public void executeEnter(VirtualFrame frame, LLVMStack llvmStack) {
            frame.setObject(STACK_ID, llvmStack);
            if (hasAllocatedStack && !llvmStack.isAllocated()) {
                /*
                 * If we've ever seen a stack being allocated in this method, then we do an explicit
                 * check on entry. This way, all other checks can remain
                 * transferToInterpreterAndInvalidate.
                 */
                CompilerDirectives.transferToInterpreter();
                llvmStack.allocate(this, memory);
            }
            if (noBasePointerAssumption.isValid()) {
                // stack pointer was never modified, only store the stack itself
            } else {
                // stack pointer was modified, store base pointer as well
                ensureBasePointerSlot(llvmStack);
                frame.setLong(BASE_POINTER_ID, llvmStack.stackPointer);
            }
        }

        @Override
        public void executeExit(VirtualFrame frame) {
            if (noBasePointerAssumption.isValid()) {
                // stack pointer was never modified, nothing to restore
            } else {
                // stack pointer was modified, restore base pointer
                try {
                    LLVMStack llvmStack = (LLVMStack) frame.getObject(STACK_ID);
                    ensureBasePointerSlot(llvmStack);
                    long basePointer = frame.getLong(BASE_POINTER_ID);
                    if (basePointer == 0) {
                        CompilerDirectives.transferToInterpreter();
                        // The assumption was invalidated from the outside while this method
                        // executed,
                        // this should happen only once
                    } else {
                        llvmStack.stackPointer = basePointer;
                    }
                } catch (FrameSlotTypeException e) {
                    throw new LLVMMemoryException(this, e);
                }
            }
        }

        private LLVMStack getStack(VirtualFrame frame) {
            try {
                LLVMStack llvmStack = (LLVMStack) frame.getObject(STACK_ID);
                if (!llvmStack.isAllocated()) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    hasAllocatedStack = true;
                    llvmStack.allocate(this, memory);
                }
                return llvmStack;
            } catch (FrameSlotTypeException e) {
                throw new LLVMMemoryException(this, e);
            }
        }

        private void initializeBasePointer(VirtualFrame frame, LLVMStack llvmStack) {
            ensureBasePointerSlot(llvmStack);
            if (frame.isLong(BASE_POINTER_ID)) {
                long basePointer = frame.getLong(BASE_POINTER_ID);
                if (basePointer != 0) {
                    return;
                }
            }
            CompilerDirectives.transferToInterpreter();
            // The slot was added from the outside while this method executed,
            // this should happen only once
            frame.setLong(BASE_POINTER_ID, llvmStack.stackPointer);
        }

        @Override
        public LLVMPointer executeGet(VirtualFrame frame) {
            return LLVMNativePointer.create(getStack(frame).stackPointer);
        }

        @Override
        public void executeSet(VirtualFrame frame, LLVMPointer pointer) {
            LLVMStack llvmStack = getStack(frame);
            initializeBasePointer(frame, llvmStack);
            if (!(LLVMNativePointer.isInstance(pointer))) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMMemoryException(this, "invalid stack pointer");
            }
            llvmStack.stackPointer = LLVMNativePointer.cast(pointer).asNative();
        }

        @Override
        public LLVMPointer executeAllocate(VirtualFrame frame, long size, int alignment) {
            LLVMStack llvmStack = getStack(frame);
            initializeBasePointer(frame, llvmStack);
            long stackPointer = llvmStack.stackPointer;
            assert stackPointer != 0;
            long alignedAllocation = getAlignedAllocation(stackPointer, size, Math.max(alignment, LLVMNode.ADDRESS_SIZE_IN_BYTES));
            assert (alignedAllocation & (LLVMNode.ADDRESS_SIZE_IN_BYTES - 1)) == 0 : "misaligned stack";
            llvmStack.stackPointer = alignedAllocation;
            return LLVMNativePointer.create(alignedAllocation);
        }

        private static long getAlignedAllocation(long address, long size, int alignment) {
            if (Long.compareUnsigned(size, MAX_ALLOCATION_SIZE) > 0) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMStackOverflowError(String.format("Stack allocation of %s bytes exceeds limit of %s", Long.toUnsignedString(size), Long.toUnsignedString(MAX_ALLOCATION_SIZE)));
            }
            assert alignment >= 8 && powerOfTwo(alignment);
            long alignedAllocation = (address - size) & -alignment; // align allocated address
            assert alignedAllocation <= address;
            return alignedAllocation;
        }

        private static boolean powerOfTwo(int value) {
            return (value & -value) == value;
        }

        @Override
        public LLVMStack executeGetStack(VirtualFrame frame) {
            try {
                return (LLVMStack) frame.getObject(STACK_ID);
            } catch (FrameSlotTypeException e) {
                throw new LLVMMemoryException(this, e);
            }
        }
    }

    public abstract static class LLVMGetStackSpaceInstruction extends LLVMExpressionNode {

        protected final long size;
        protected final int alignment;
        @CompilationFinal private LLVMStackAccess stackAccess;

        public LLVMGetStackSpaceInstruction(long size, int alignment) {
            this.size = size;
            this.alignment = alignment;
        }

        public void setStackAccess(LLVMStackAccess stackAccess) {
            this.stackAccess = stackAccess;
        }

        protected final LLVMStackAccessHolder createStackAccessHolder() {
            if (stackAccess == null) {
                return new LLVMStackAccessHolder(((LLVMRootNode) getRootNode()).getStackAccess());
            }
            // This branch should be executed when this node is used as an uncached one only. See
            // uses of setStackAccess.
            assert getRootNode() == null;
            return new LLVMStackAccessHolder(stackAccess);
        }

        @Override
        public String toString() {
            return getShortString("size", "alignment", "stackAccess");
        }
    }

    public abstract static class LLVMAllocaConstInstruction extends LLVMGetStackSpaceInstruction {

        public LLVMAllocaConstInstruction(long size, int alignment) {
            super(size, alignment);
        }

        @Specialization
        protected LLVMPointer doOp(VirtualFrame frame, @Cached("createStackAccessHolder()") LLVMStackAccessHolder stackAccessHolder) {
            return stackAccessHolder.stackAccess.executeAllocate(frame, size, alignment);
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAllocaInstruction extends LLVMGetStackSpaceInstruction {

        public LLVMAllocaInstruction(long size, int alignment) {
            super(size, alignment);
        }

        public abstract LLVMPointer executeWithTarget(VirtualFrame frame, long sizeInBytes);

        @Specialization
        protected LLVMPointer doOp(VirtualFrame frame, int nr, @Cached("createStackAccessHolder()") LLVMStackAccessHolder stackAccessHolder) {
            return doOp(frame, (long) nr, stackAccessHolder);
        }

        @Specialization
        protected LLVMPointer doOp(VirtualFrame frame, long nr, @Cached("createStackAccessHolder()") LLVMStackAccessHolder stackAccessHolder) {
            return stackAccessHolder.stackAccess.executeAllocate(frame, size * nr, alignment);
        }
    }

    public abstract static class LLVMGetUniqueStackSpaceInstruction extends LLVMExpressionNode {

        private final long slotOffset;

        public LLVMGetUniqueStackSpaceInstruction(long slotOffset) {
            this.slotOffset = slotOffset;
        }

        @Override
        public String toString() {
            return getShortString("slotOffset", "uniquesRegionFrameSlot");
        }

        @Specialization
        protected LLVMPointer doOp(VirtualFrame frame) {
            try {
                return LLVMPointer.cast(frame.getObject(UNIQUES_REGION_ID)).increment(slotOffset);
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMAllocationFailureException(this, e);
            }
        }
    }
}
