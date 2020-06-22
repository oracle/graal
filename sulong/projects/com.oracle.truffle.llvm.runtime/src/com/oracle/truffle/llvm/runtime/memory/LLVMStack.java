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
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.except.LLVMStackOverflowError;

/**
 * Implements a stack that grows from the top to the bottom. The stack is allocated lazily when it
 * is accessed for the first time.
 */
public final class LLVMStack {

    public static final String FRAME_ID = "<stackpointer>";
    private static final long MAX_ALLOCATION_SIZE = Integer.MAX_VALUE;

    private final long stackSize;

    private long lowerBounds;
    private long upperBounds;
    private boolean isAllocated;

    private long stackPointer;
    private long uniquesRegionPointer;

    public LLVMStack(long stackSize) {
        this.stackSize = stackSize;

        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        uniquesRegionPointer = 0;
        isAllocated = false;
    }

    public final class StackPointer implements AutoCloseable {
        private long basePointer;
        private final long uniquesRegionBasePointer;

        private StackPointer(long basePointer, long uniquesRegionBasePointer) {
            this.basePointer = basePointer;
            this.uniquesRegionBasePointer = uniquesRegionBasePointer;
        }

        public long get(Node location, LLVMMemory memory) {
            if (basePointer == 0) {
                basePointer = getStackPointer(location, memory);
                stackPointer = basePointer;
            }
            return stackPointer;
        }

        public void set(long sp) {
            stackPointer = sp;
        }

        public long getUniquesRegionPointer() {
            return uniquesRegionPointer;
        }

        public void setUniquesRegionPointer(long urp) {
            uniquesRegionPointer = urp;
        }

        @Override
        public void close() {
            if (basePointer != 0) {
                stackPointer = basePointer;
                uniquesRegionPointer = uniquesRegionBasePointer;
            }
        }

        public StackPointer newFrame() {
            return new StackPointer(stackPointer, uniquesRegionPointer);
        }

        @TruffleBoundary
        @Override
        public String toString() {
            return String.format("StackPointer 0x%x (Bounds: 0x%x - 0x%x%s)", stackPointer, lowerBounds, upperBounds, isAllocated ? "" : " not allocated");
        }
    }

    /**
     * Implements a stack region dedicated to values which should be bound to unique frame slots.
     *
     * Adding a slot to the region will extend it according to the specified slot size and slot
     * alignment. The {@link #addSlot} method will return a handle to the new slot in the form of a
     * {@link UniqueSlot}. In combination with a stack pointer this handle can be resolved to an
     * address pointing to the corresponding pre-allocated slot on the current stack frame. This
     * requires that the region has already been allocated on the current stack frame through the
     * {@link #allocate} method of its {@link UniquesRegionAllocator}. Aside from allocating memory,
     * this method is responsible for setting the region's base pointer for the current stack frame.
     * A {@link UniqueSlot} uses the region's base pointer to resolve its internal unique relative
     * address to an absolute address.
     */
    public static final class UniquesRegion {
        private long currentSlotPointer = 0;
        private int alignment = 1;

        public UniqueSlot addSlot(long slotSize, int slotAlignment) {
            CompilerAsserts.neverPartOfCompilation();
            currentSlotPointer = getAlignedAllocation(currentSlotPointer, slotSize, slotAlignment);
            // maximum of current alignment, slot alignment and the alignment masking slot size
            alignment = Math.toIntExact(Long.highestOneBit(alignment | slotAlignment | Long.highestOneBit(slotSize) << 1));
            return new UniqueSlot(currentSlotPointer);
        }

        public UniquesRegionAllocator build() {
            return new UniquesRegionAllocator(-currentSlotPointer, alignment);
        }

        public static final class UniquesRegionAllocator {
            private final long uniquesRegionSize;
            private final int uniquesRegionAlignment;

            private UniquesRegionAllocator(long uniquesRegionSize, int uniquesRegionAlignment) {
                this.uniquesRegionSize = uniquesRegionSize;
                this.uniquesRegionAlignment = uniquesRegionAlignment;
            }

            void allocate(Node location, VirtualFrame frame, LLVMMemory memory, FrameSlot stackPointerSlot) {
                if (uniquesRegionSize == 0) {
                    // UniquesRegion is empty - nothing to allocate
                    return;
                }
                StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
                long stackPointer = basePointer.get(location, memory);
                assert stackPointer != 0;
                long uniquesRegionPointer = getAlignedBasePointer(stackPointer);
                basePointer.setUniquesRegionPointer(uniquesRegionPointer);
                long alignedAllocation = getAlignedAllocation(uniquesRegionPointer, uniquesRegionSize, NO_ALIGNMENT_REQUIREMENTS);
                basePointer.set(alignedAllocation);
            }

            long getAlignedBasePointer(long address) {
                assert uniquesRegionAlignment != 0 && powerOfTwo(uniquesRegionAlignment);
                return address & -uniquesRegionAlignment;
            }
        }

        public static final class UniqueSlot {
            private final long address;

            private UniqueSlot(long address) {
                this.address = address;
            }

            public long toPointer(VirtualFrame frame, FrameSlot stackPointerSlot) {
                StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
                long uniquesRegionPointer = basePointer.getUniquesRegionPointer();
                assert uniquesRegionPointer != 0;
                return uniquesRegionPointer + address;
            }
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

    public StackPointer newFrame() {
        return new StackPointer(stackPointer, uniquesRegionPointer);
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
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        long alignedAllocation = (address - size) & -alignment;
        assert alignedAllocation <= address;
        return alignedAllocation;
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }
}
