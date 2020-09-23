/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.except.LLVMMemoryException;
import com.oracle.truffle.llvm.runtime.except.LLVMStackOverflowError;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;
import com.oracle.truffle.llvm.runtime.types.PointerType;

/**
 * Implements a stack that grows from the top to the bottom. The stack is allocated lazily when it
 * is accessed for the first time.
 */
public final class LLVMStack {

    private static final String FRAME_ID = "<stackpointer>";
    private static final String UNIQUES_REGION_ID = "<uniquesregion>";
    private static final long MAX_ALLOCATION_SIZE = Integer.MAX_VALUE;

    private final long stackSize;

    private long lowerBounds;
    private long upperBounds;
    private boolean isAllocated;

    private long stackPointer;

    public LLVMStack(long stackSize) {
        this.stackSize = stackSize;

        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        isAllocated = false;
    }

    public static FrameSlot getStackPointerSlot(FrameDescriptor frameDescriptor) {
        return frameDescriptor.findOrAddFrameSlot(FRAME_ID, PointerType.VOID, FrameSlotKind.Object);
    }

    public static FrameSlot getUniquesRegionSlot(FrameDescriptor frameDescriptor) {
        return frameDescriptor.findOrAddFrameSlot(UNIQUES_REGION_ID, PointerType.VOID, FrameSlotKind.Object);
    }

    public abstract static class StackCloseable implements AutoCloseable {

        @Override
        public abstract void close();
    }

    public final class StackPointer extends StackCloseable {
        private long basePointer;

        private StackPointer(long basePointer) {
            this.basePointer = basePointer;
        }

        long get(Node location, LLVMMemory memory) {
            if (basePointer == 0) {
                basePointer = getStackPointer(location, memory);
                stackPointer = basePointer;
            }
            return stackPointer;
        }

        void set(long sp) {
            stackPointer = sp;
        }

        @Override
        public void close() {
            if (basePointer != 0) {
                stackPointer = basePointer;
            }
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return String.format("StackPointer 0x%x (Bounds: 0x%x - 0x%x%s)", stackPointer, lowerBounds, upperBounds, isAllocated ? "" : " not allocated");
        }

        public LLVMStack getLLVMStack() {
            return LLVMStack.this;
        }
    }

    /**
     * Implements a stack region dedicated to values which should be bound to unique frame slots.
     * This needs to collect alignments so that the resulting stack area can be aligned sufficiently
     * for all slots.
     */
    public static final class UniquesRegion {

        private long currentSlotOffset = 0;
        private int alignment = Long.BYTES;
        private boolean finished; //

        public long addSlot(long slotSize, int slotAlignment) {
            CompilerAsserts.neverPartOfCompilation();
            assert !finished : "cannot add slots after size was queried";
            assert Long.bitCount(slotAlignment) == 1 : "alignment must be a power of two";

            // align pointer
            long slotOffset = (currentSlotOffset + slotAlignment - 1) & (-slotAlignment);
            currentSlotOffset = slotOffset + slotSize;
            alignment = Integer.highestOneBit(alignment | slotAlignment); // widen general alignment
            return slotOffset;
        }

        public long getSize() {
            finished = true;
            return currentSlotOffset;
        }

        public boolean isEmpty() {
            finished = true;
            return currentSlotOffset == 0;
        }

        public int getAlignment() {
            finished = true;
            return alignment;
        }
    }

    @TruffleBoundary
    private void allocate(Node location, LLVMMemory memory) {
        long size = stackSize;
        long stackAllocation = memory.allocateMemory(location, size).asNative();
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + size;
        isAllocated = true;
        stackPointer = upperBounds;
    }

    private long getStackPointer(Node location, LLVMMemory memory) {
        if (!isAllocated) {
            CompilerDirectives.transferToInterpreter();
            allocate(location, memory);
        }
        return this.stackPointer;
    }

    @TruffleBoundary
    public void free(LLVMMemory memory) {
        if (isAllocated) {
            /*
             * It can be that the stack was never allocated.
             */
            memory.free(null, lowerBounds);
            lowerBounds = 0;
            upperBounds = 0;
            stackPointer = 0;
            isAllocated = false;
        }
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    public static long allocateStackMemory(Node location, VirtualFrame frame, LLVMMemory memory, FrameSlot stackPointerSlot, final long size, final int alignment) {
        StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
        long stackPointer = basePointer.get(location, memory);
        assert stackPointer != 0;
        long alignedAllocation = getAlignedAllocation(stackPointer, size, alignment);
        basePointer.set(alignedAllocation);
        return alignedAllocation;
    }

    private static long getAlignedAllocation(long address, long size, int alignment) {
        if (Long.compareUnsigned(size, MAX_ALLOCATION_SIZE) > 0) {
            CompilerDirectives.transferToInterpreter();
            throw new LLVMStackOverflowError(String.format(String.format("Stack allocation of %s bytes exceeds limit of %s",
                            Long.toUnsignedString(size), Long.toUnsignedString(MAX_ALLOCATION_SIZE))));
        }
        long alignedSize = (size + Long.BYTES - 1) & -Long.BYTES; // align allocation size
        assert alignedSize >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        long alignedAllocation = (address - alignedSize) & -alignment; // align allocated address
        assert alignedAllocation <= address;
        return alignedAllocation;
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }

    public abstract static class LLVMInitializeStackFrameNode extends LLVMNode {

        public abstract StackCloseable execute(VirtualFrame frame);

        public abstract StackCloseable execute(VirtualFrame frame, LLVMStack llvmStack);

        public abstract StackCloseable execute(LLVMStack llvmStack);
    }

    public static class LLVMInitializeNativeStackFrameNode extends LLVMInitializeStackFrameNode {

        private final FrameSlot stackPointerSlot;

        public LLVMInitializeNativeStackFrameNode(FrameDescriptor frameDescriptor) {
            this.stackPointerSlot = frameDescriptor.findFrameSlot(FRAME_ID);
        }

        @Override
        public StackCloseable execute(VirtualFrame frame) {
            return execute(frame, (LLVMStack) frame.getArguments()[0]);
        }

        @Override
        public StackCloseable execute(VirtualFrame frame, LLVMStack llvmStack) {
            StackCloseable stackPointer = execute(llvmStack);
            frame.setObject(stackPointerSlot, stackPointer);
            return stackPointer;
        }

        @Override
        public StackCloseable execute(LLVMStack llvmStack) {
            return llvmStack.new StackPointer(llvmStack.stackPointer);
        }
    }

    public abstract static class LLVMAccessStackPointerNode extends LLVMNode {

        public abstract LLVMPointer executeGet(VirtualFrame frame);

        public abstract void executeSet(VirtualFrame frame, LLVMPointer pointer);
    }

    public static final class LLVMAccessNativeStackPointerNode extends LLVMAccessStackPointerNode {

        private final LLVMMemory memory;
        private final FrameSlot stackPointerSlot;

        public LLVMAccessNativeStackPointerNode(LLVMLanguage language, FrameDescriptor frameDescriptor) {
            this.memory = language.getLLVMMemory();
            this.stackPointerSlot = getStackPointerSlot(frameDescriptor);
        }

        private StackPointer getStackPointer(VirtualFrame frame) {
            try {
                return ((StackPointer) frame.getObject(stackPointerSlot));
            } catch (FrameSlotTypeException e) {
                CompilerDirectives.transferToInterpreter();
                throw new LLVMMemoryException(this, e);
            }
        }

        @Override
        public LLVMPointer executeGet(VirtualFrame frame) {
            return LLVMNativePointer.create(getStackPointer(frame).get(this, memory));
        }

        @Override
        public void executeSet(VirtualFrame frame, LLVMPointer pointer) {
            if (!(LLVMNativePointer.isInstance(pointer))) {
                throw new LLVMMemoryException(this, "invalid stack pointer");
            }
            getStackPointer(frame).set(LLVMNativePointer.cast(pointer).asNative());
        }
    }
}
