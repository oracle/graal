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
package com.oracle.truffle.llvm.types.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.types.LLVMAddress;

/**
 * Implements a stack that grows from the top to the bottom.
 */
public class LLVMStack extends LLVMMemory {

    private static final int STACK_SIZE_KB = 8192 * 10;

    private static final int STACK_SIZE_BYTE = STACK_SIZE_KB * 1024;

    private static long stackPointer;

    @CompilationFinal private static long lowerBounds;
    @CompilationFinal private static long upperBounds;

    /**
     * Allocates the stack memory.
     */
    public static void allocate() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final long stackAllocation = UNSAFE.allocateMemory(STACK_SIZE_BYTE);
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + STACK_SIZE_BYTE;
        stackPointer = upperBounds;
    }

    /**
     * Deallocates the stack memory.
     */
    public static void free() {
        UNSAFE.freeMemory(lowerBounds);
        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    /**
     * Allocates stack memory.
     *
     * @param size the size of the memory to be allocated, must be greater equals zero
     * @param alignment the alignment, either {@link #NO_ALIGNMENT_REQUIREMENTS} or a power of two.
     * @return the allocated memory, satisfying the alignment requirements
     */
    public static LLVMAddress allocateMemory(final long size, final int alignment) {
        assert size >= 0;
        assert alignment != 0 && powerOfTo(alignment);
        final long alignedAllocation = (stackPointer - size) & -alignment;
        stackPointer = alignedAllocation;
        if (stackPointer < lowerBounds) {
            CompilerDirectives.transferToInterpreter();
            throw new StackOverflowError("stack overflow");
        }
        final LLVMAddress allocatedMemory = LLVMAddress.fromLong(alignedAllocation);
        return allocatedMemory;
    }

    private static boolean powerOfTo(int value) {
        return (value & -value) == value;
    }

    public static LLVMAddress getStackPointer() {
        return LLVMAddress.fromLong(stackPointer);
    }

    public static void setStackPointer(final LLVMAddress addr) {
        assert stackPointer <= addr.getVal();
        assert lowerBounds < addr.getVal();
        assert upperBounds >= addr.getVal();
        stackPointer = addr.getVal();
    }

}
