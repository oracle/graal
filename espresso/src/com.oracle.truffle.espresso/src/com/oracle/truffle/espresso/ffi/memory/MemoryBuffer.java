/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.ffi.memory;

import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.IllegalMemoryAccessException;
import static com.oracle.truffle.espresso.ffi.memory.NativeMemory.MemoryAllocationException;

import java.nio.ByteBuffer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Class representing a MemoryBuffer in the context of {@link NativeMemory}.
 */
public final class MemoryBuffer implements AutoCloseable {
    private final long address;
    private final ByteBuffer buffer;
    private final NativeMemory nativeMemory;

    /**
     * see {@link NativeMemory#allocateMemoryBuffer(int)}.
     */
    MemoryBuffer(
                    int bytes,
                    NativeMemory nativeMemory) throws MemoryAllocationException {
        this.address = nativeMemory.allocateMemory(bytes);
        this.nativeMemory = nativeMemory;
        try {
            this.buffer = nativeMemory.wrapNativeMemory(address, bytes);
        } catch (IllegalMemoryAccessException e) {
            // we should not reach here since we are in control of the parameters
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    /**
     * @return the address of the memory region corresponding to the buffer.
     */
    public long address() {
        return address;
    }

    /**
     * @return the ByteBuffer of the memory region.
     */
    public ByteBuffer buffer() {
        return buffer;
    }

    @Override
    public void close() {
        try {
            nativeMemory.freeMemory(address);
        } catch (IllegalMemoryAccessException e) {
            // we should not reach here as we are i
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.shouldNotReachHere(e);
        }
    }
}
