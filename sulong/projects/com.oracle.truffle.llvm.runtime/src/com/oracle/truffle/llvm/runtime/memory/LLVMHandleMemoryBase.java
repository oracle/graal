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

/**
 * Stores constants and provides checking functions for {@link LLVMMemory} implementations that
 * support handle objects.
 */
public abstract class LLVMHandleMemoryBase extends LLVMMemory {

    protected static final int HANDLE_OBJECT_SIZE_BITS = 30;
    protected static final long HANDLE_OBJECT_SIZE = 1L << HANDLE_OBJECT_SIZE_BITS; // 0.5 GB
    protected static final int HANDLE_OBJECT_ADDRESS_BITS = Integer.SIZE; // use int cast as mask
    protected static final long HANDLE_HEADER_MASK = -1L << (HANDLE_OBJECT_SIZE_BITS + HANDLE_OBJECT_ADDRESS_BITS);
    protected static final long HANDLE_OFFSET_MASK = HANDLE_OBJECT_SIZE - 1;

    protected static final long HANDLE_SPACE_START = 0x8000000000000000L;
    protected static final long HANDLE_SPACE_END = 0xC000000000000000L;
    protected static final long DEREF_HANDLE_SPACE_START = HANDLE_SPACE_END;
    protected static final long DEREF_HANDLE_SPACE_END = 0x0000000000000000L;

    /**
     * A fast bit-check if the provided address is within the handle space.
     */
    public static boolean isHandleMemory(long address) {
        return (address & HANDLE_SPACE_START) != 0;
    }

    /**
     * A fast bit-check if the provided address is within the normal handle space.
     */
    public static boolean isCommonHandleMemory(long address) {
        return ((address & HANDLE_HEADER_MASK) == HANDLE_SPACE_START);
    }

    /**
     * A fast bit-check if the provided address is within the auto-deref handle space.
     */
    public static boolean isDerefHandleMemory(long address) {
        return ((address & HANDLE_HEADER_MASK) == DEREF_HANDLE_SPACE_START);
    }
}
