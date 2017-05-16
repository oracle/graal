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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;

/**
 * Implements a stack that grows from the top to the bottom.
 */
public final class LLVMStack extends LLVMMemory {

    private static final long STACK_SIZE_KB = LLVMOptions.ENGINE.stackSize();

    private static final long STACK_SIZE_BYTE = STACK_SIZE_KB * 1024;

    @CompilationFinal private long lowerBounds;
    @CompilationFinal private long upperBounds;
    private boolean isFreed = true;

    private long stackPointer;

    public void allocate() {
        allocate(STACK_SIZE_BYTE);
    }

    private void allocate(final long stackSize) {
        CompilerAsserts.neverPartOfCompilation();
        if (!isFreed) {
            throw new AssertionError("previously not deallocated");
        }
        final long stackAllocation = UNSAFE.allocateMemory(stackSize);
        lowerBounds = stackAllocation;
        upperBounds = stackAllocation + stackSize;
        isFreed = false;
        stackPointer = upperBounds;
    }

    public boolean isFreed() {
        return isFreed;
    }

    public LLVMAddress getStackPointer() {
        return LLVMAddress.fromLong(stackPointer);
    }

    public void setStackPointer(LLVMAddress pointer) {
        this.stackPointer = pointer.getVal();
    }

    public void free() {
        CompilerAsserts.neverPartOfCompilation();
        if (isFreed) {
            throw new AssertionError("already freed");
        }
        UNSAFE.freeMemory(lowerBounds);
        lowerBounds = 0;
        upperBounds = 0;
        stackPointer = 0;
        isFreed = true;
    }

    public static final int NO_ALIGNMENT_REQUIREMENTS = 1;

    public LLVMAddress allocateStackMemory(final long size, final int alignment) {
        assert size >= 0;
        assert alignment != 0 && powerOfTwo(alignment);
        final long alignedAllocation = (stackPointer - size) & -alignment;
        assert alignedAllocation <= stackPointer;
        stackPointer = alignedAllocation;
        if (stackPointer < lowerBounds) {
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
            throw new AssertionError("Did not free stack memory!");
        }
    }

}
