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
package com.oracle.truffle.llvm.runtime.memory;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Implements a stack that grows from the top to the bottom. The stack is allocated lazily when it
 * is accessed for the first time.
 */
public final class LLVMStack {

    public static final String FRAME_ID = "<stackpointer>";

    private final int stackSize;

    private long lowerBounds;
    private long upperBounds;
    private boolean isAllocated;

    private long stackPointer;

    public LLVMStack(int stackSize) {
        this.stackSize = stackSize;

        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        isAllocated = false;
    }

    public final class StackPointer implements AutoCloseable {

        private long basePointer;

        private StackPointer(long basePointer) {
            this.basePointer = basePointer;
        }

        public long get(LLVMMemory memory) {
            if (basePointer == 0) {
                basePointer = getStackPointer(memory);
                stackPointer = basePointer;
            }
            return stackPointer;
        }

        public void set(long sp) {
            stackPointer = sp;
        }

        @Override
        public void close() {
            if (basePointer != 0) {
                stackPointer = basePointer;
            }
        }

        public StackPointer newFrame() {
            return new StackPointer(stackPointer);
        }
    }

    @TruffleBoundary
    private void allocate(LLVMMemory memory) {
        final long stackAllocation = memory.allocateMemory(stackSize * 1024).getVal();
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + stackSize * 1024;
        isAllocated = true;
        stackPointer = upperBounds;
    }

    private long getStackPointer(LLVMMemory memory) {
        if (!isAllocated) {
            allocate(memory);
        }
        return this.stackPointer;
    }

    public StackPointer newFrame() {
        return new StackPointer(stackPointer);
    }

    @TruffleBoundary
    public void free(LLVMMemory memory) {
        if (isAllocated) {
            /*
             * It can be that the stack was never allocated.
             */
            memory.free(lowerBounds);
            lowerBounds = 0;
            upperBounds = 0;
            stackPointer = 0;
            isAllocated = false;
        }
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    public static long allocateStackMemory(VirtualFrame frame, LLVMMemory memory, FrameSlot stackPointerSlot, final long size, final int alignment) {
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        StackPointer basePointer = (StackPointer) FrameUtil.getObjectSafe(frame, stackPointerSlot);
        long stackPointer = basePointer.get(memory);
        assert stackPointer != 0;
        final long alignedAllocation = (stackPointer - size) & -alignment;
        assert alignedAllocation <= stackPointer;
        basePointer.set(alignedAllocation);
        return alignedAllocation;
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }

}
