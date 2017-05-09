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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Implements a stack that grows from the top to the bottom.
 */
public final class LLVMStack extends LLVMMemory {

    private static final long STACK_SIZE_KB = LLVMOptions.ENGINE.stackSize();

    private static final long STACK_SIZE_BYTE = STACK_SIZE_KB * 1024;

    @CompilationFinal private long lowerBounds;
    @CompilationFinal private long upperBounds;
    private boolean isFreed = true;

    /**
     * Allocates the stack memory.
     */
    public LLVMAddress allocate() {
        return allocate(STACK_SIZE_BYTE);
    }

    /**
     * Allocates the stack memory.
     */
    public LLVMAddress allocate(final long stackSize) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (!isFreed) {
            throw new AssertionError("previously not deallocated");
        }
        final long stackAllocation = UNSAFE.allocateMemory(stackSize);
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + stackSize;
        isFreed = false;
        return LLVMAddress.fromLong(upperBounds);
    }

    public boolean isFreed() {
        return isFreed;
    }

    /**
     * Deallocates the stack memory.
     */
    public void free() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (isFreed) {
            throw new AssertionError("already freed");
        }
        UNSAFE.freeMemory(lowerBounds);
        lowerBounds = 0;
        upperBounds = 0;
        isFreed = true;
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    /**
     * Allocates stack memory and associates it with a type.
     *
     * @param size the size of the memory to be allocated, must be greater equals zero
     * @param alignment the alignment, either {@link #NO_ALIGNMENT_REQUIREMENTS} or a power of two.
     * @param type the type of the object for which memory is to be allocated
     * @return the allocated memory, satisfying the alignment requirements
     */
    public LLVMAddress allocateMemory(final LLVMAddress stackPointer, final long size, final int alignment, final Type type) {
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        final long alignedAllocation = (stackPointer.getVal() - size) & -alignment;
        LLVMAddress newStackPointer = LLVMAddress.fromLong(alignedAllocation);
        if (newStackPointer.unsignedLessThan(lowerBounds)) {
            CompilerDirectives.transferToInterpreter();
            throw new StackOverflowError("stack overflow");
        }
        return LLVMAddress.fromLong(alignedAllocation);
    }

    private static boolean powerOfTwo(int value) {
        return (value & -value) == value;
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!isFreed) {
            throw new AssertionError("did not free stack memory!");
        }
    }

    public LLVMAddress getUpperBounds() {
        return LLVMAddress.fromLong(upperBounds);
    }

}
