/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.memory;

import static java.lang.Integer.compareUnsigned;
import static java.lang.StrictMath.addExact;
import static java.lang.StrictMath.multiplyExact;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_DECLARATION_SIZE;
import static org.graalvm.wasm.constants.Sizes.MAX_MEMORY_INSTANCE_SIZE;
import static org.graalvm.wasm.constants.Sizes.MEMORY_PAGE_SIZE;

import java.lang.reflect.Field;

import org.graalvm.wasm.constants.Sizes;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

import sun.misc.Unsafe;

public final class UnsafeWasmMemory extends WasmMemory implements AutoCloseable {
    /**
     * @see #declaredMinSize()
     */
    private final int declaredMinSize;

    /**
     * @see #declaredMaxSize()
     */
    private final int declaredMaxSize;
    private long startAddress;
    private int size;

    /**
     * The maximum practical size of this memory instance (measured in number of
     * {@link Sizes#MEMORY_PAGE_SIZE pages}).
     * <p>
     * It is the minimum between {@link #declaredMaxSize the limit defined in the module binary},
     * {@link Sizes#MAX_MEMORY_INSTANCE_SIZE the GraalWasm limit} and any additional limit (the JS
     * API for example has lower limits).
     * <p>
     * This is different from {@link #declaredMaxSize()}, which can be higher.
     */
    private final int maxAllowedSize;

    private static final Unsafe unsafe;

    private UnsafeWasmMemory(int declaredMinSize, int declaredMaxSize, int initialSize, int maxAllowedSize) {
        assert compareUnsigned(declaredMinSize, initialSize) <= 0;
        assert compareUnsigned(declaredMaxSize, MAX_MEMORY_DECLARATION_SIZE) <= 0;
        assert compareUnsigned(initialSize, maxAllowedSize) <= 0;
        assert compareUnsigned(maxAllowedSize, MAX_MEMORY_INSTANCE_SIZE) <= 0;
        assert compareUnsigned(maxAllowedSize, declaredMaxSize) <= 0;

        this.declaredMinSize = declaredMinSize;
        this.declaredMaxSize = declaredMaxSize;
        this.size = declaredMinSize;
        this.maxAllowedSize = maxAllowedSize;
        final long byteSize = byteSize();
        try {
            this.startAddress = unsafe.allocateMemory(byteSize);
        } catch (OutOfMemoryError error) {
            CompilerDirectives.transferToInterpreter();
            throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
        }
        unsafe.setMemory(startAddress, byteSize, (byte) 0);
    }

    public UnsafeWasmMemory(int declaredMinSize, int declaredMaxSize, int maxAllowedSize) {
        this(declaredMinSize, declaredMaxSize, declaredMinSize, maxAllowedSize);
    }

    public void validateAddress(Node node, int address, int offset) {
        assert offset >= 1;
        if (address < 0 || address > this.byteSize() - offset) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw trapOutOfBounds(node, address, offset);
        }
    }

    @TruffleBoundary
    private WasmException trapOutOfBounds(Node node, int address, int offset) {
        throw WasmException.format(Failure.OUT_OF_BOUNDS_MEMORY_ACCESS, node, "%d-byte memory access at address 0x%016X (%d) is out-of-bounds (memory size %d bytes).",
                        offset, address, address, byteSize());
    }

    @Override
    public void copy(Node node, int src, int dst, int n) {
        validateAddress(node, src, n);
        validateAddress(node, dst, n);
        unsafe.copyMemory(startAddress + src, startAddress + dst, n);
    }

    @Override
    public void reset() {
        size = declaredMinSize;
        unsafe.freeMemory(startAddress);
        startAddress = unsafe.allocateMemory(byteSize());
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int byteSize() {
        return size * MEMORY_PAGE_SIZE;
    }

    @Override
    public int declaredMinSize() {
        return declaredMinSize;
    }

    @Override
    public int declaredMaxSize() {
        return declaredMaxSize;
    }

    @Override
    @TruffleBoundary
    public boolean grow(int extraPageSize) {
        if (extraPageSize == 0) {
            return true;
        } else if (compareUnsigned(extraPageSize, maxAllowedSize) <= 0 && compareUnsigned(size() + extraPageSize, maxAllowedSize) <= 0) {
            // Condition above and limit on maxPageSize (see ModuleLimits#MAX_MEMORY_SIZE) ensure
            // computation of targetByteSize does not overflow.
            final int targetByteSize = multiplyExact(addExact(size(), extraPageSize), MEMORY_PAGE_SIZE);
            try {
                final long updatedStartAddress = unsafe.allocateMemory(targetByteSize);
                unsafe.copyMemory(startAddress, updatedStartAddress, byteSize());
                unsafe.setMemory(updatedStartAddress + byteSize(), targetByteSize - byteSize(), (byte) 0);
                unsafe.freeMemory(startAddress);
                startAddress = updatedStartAddress;
                size += extraPageSize;
                return true;
            } catch (OutOfMemoryError error) {
                throw WasmException.create(Failure.MEMORY_ALLOCATION_FAILED);
            }
        } else {
            return false;
        }
    }

    @Override
    public int load_i32(Node node, int address) {
        validateAddress(node, address, 4);
        final int value = unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public long load_i64(Node node, int address) {
        validateAddress(node, address, 8);
        final long value = unsafe.getLong(startAddress + address);
        return value;
    }

    @Override
    public float load_f32(Node node, int address) {
        validateAddress(node, address, 4);
        final float value = unsafe.getFloat(startAddress + address);
        return value;
    }

    @Override
    public double load_f64(Node node, int address) {
        validateAddress(node, address, 8);
        final double value = unsafe.getDouble(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_8s(Node node, int address) {
        validateAddress(node, address, 1);
        final int value = unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_8u(Node node, int address) {
        validateAddress(node, address, 1);
        final int value = 0x0000_00ff & unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_16s(Node node, int address) {
        validateAddress(node, address, 2);
        final int value = unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public int load_i32_16u(Node node, int address) {
        validateAddress(node, address, 2);
        final int value = 0x0000_ffff & unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_8s(Node node, int address) {
        validateAddress(node, address, 1);
        final long value = unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_8u(Node node, int address) {
        validateAddress(node, address, 1);
        final long value = 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_16s(Node node, int address) {
        validateAddress(node, address, 2);
        final long value = unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_16u(Node node, int address) {
        validateAddress(node, address, 2);
        final long value = 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_32s(Node node, int address) {
        validateAddress(node, address, 4);
        final long value = unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public long load_i64_32u(Node node, int address) {
        validateAddress(node, address, 4);
        final long value = 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
        return value;
    }

    @Override
    public void store_i32(Node node, int address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public void store_i64(Node node, int address, long value) {
        validateAddress(node, address, 8);
        unsafe.putLong(startAddress + address, value);

    }

    @Override
    public void store_f32(Node node, int address, float value) {
        validateAddress(node, address, 4);
        unsafe.putFloat(startAddress + address, value);

    }

    @Override
    public void store_f64(Node node, int address, double value) {
        validateAddress(node, address, 8);
        unsafe.putDouble(startAddress + address, value);
    }

    @Override
    public void store_i32_8(Node node, int address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i32_16(Node node, int address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_8(Node node, int address, byte value) {
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i64_16(Node node, int address, short value) {
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_32(Node node, int address, int value) {
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public WasmMemory duplicate() {
        final UnsafeWasmMemory other = new UnsafeWasmMemory(declaredMinSize, declaredMaxSize, size, maxAllowedSize);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }

    public void free() {
        unsafe.freeMemory(this.startAddress);
        startAddress = 0;
        size = 0;
    }

    public boolean freed() {
        return startAddress == 0;
    }

    @Override
    public void close() {
        if (!freed()) {
            free();
        }
    }

    static {
        try {
            final Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw CompilerDirectives.shouldNotReachHere(e);
        }
    }
}
