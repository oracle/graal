/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import org.graalvm.wasm.exception.WasmTrap;
import org.graalvm.wasm.WasmTracing;
import sun.misc.Unsafe;

public class UnsafeWasmMemory extends WasmMemory {
    private final Unsafe unsafe;
    private long startAddress;
    private long pageSize;
    private final long maxPageSize;
    private final ConditionProfile outOfBoundsAccesses = ConditionProfile.create();

    public UnsafeWasmMemory(long initPageSize, long maxPageSize) {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.pageSize = initPageSize;
        this.maxPageSize = maxPageSize;
        long byteSize = byteSize();
        this.startAddress = unsafe.allocateMemory(byteSize);
        unsafe.setMemory(startAddress, byteSize, (byte) 0);
    }

    @Override
    public void validateAddress(Node node, long address, long offset) {
        WasmTracing.trace("validating memory address: 0x%016X (%d)", address, address);
        if (outOfBoundsAccesses.profile(address < 0 || address + offset > this.byteSize())) {
            trapOutOfBounds(node, address, offset);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private void trapOutOfBounds(Node node, long address, long offset) {
        String message = String.format("%d-byte memory access at address 0x%016X (%d) is out-of-bounds (memory size %d bytes).",
                        offset, address, address, byteSize());
        throw new WasmTrap(node, message);
    }

    @Override
    public void copy(Node node, long src, long dst, long n) {
        WasmTracing.trace("memcopy from = %d, to = %d, n = %d", src, dst, n);
        validateAddress(node, src, n);
        validateAddress(node, dst, n);
        unsafe.copyMemory(startAddress + src, startAddress + dst, n);
    }

    @Override
    public void clear() {
        unsafe.setMemory(startAddress, byteSize(), (byte) 0);
    }

    @Override
    public long pageSize() {
        return pageSize;
    }

    @Override
    public long byteSize() {
        return pageSize * PAGE_SIZE;
    }

    @Override
    public long maxPageSize() {
        return maxPageSize;
    }

    @Override
    public boolean grow(long extraPageSize) {
        if (extraPageSize < 0) {
            throw new WasmTrap(null, "Extra size cannot be negative.");
        }
        long targetSize = byteSize() + extraPageSize * PAGE_SIZE;
        if (maxPageSize >= 0 && targetSize > maxPageSize * PAGE_SIZE) {
            // Cannot grow the memory beyond maxPageSize bytes.
            return false;
        }
        if (targetSize * PAGE_SIZE == byteSize()) {
            return true;
        }
        long updatedStartAddress = unsafe.allocateMemory(targetSize);
        unsafe.copyMemory(startAddress, updatedStartAddress, byteSize());
        unsafe.setMemory(updatedStartAddress + byteSize(), targetSize - byteSize(), (byte) 0);
        unsafe.freeMemory(startAddress);
        startAddress = updatedStartAddress;
        pageSize += extraPageSize;
        return true;
    }

    // Checkstyle: stop
    @Override
    public int load_i32(Node node, long address) {
        WasmTracing.trace("load.i32 address = %d", address);
        validateAddress(node, address, 4);
        int value = unsafe.getInt(startAddress + address);
        WasmTracing.trace("load.i32 value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64(Node node, long address) {
        WasmTracing.trace("load.i64 address = %d", address);
        validateAddress(node, address, 8);
        long value = unsafe.getLong(startAddress + address);
        WasmTracing.trace("load.i64 value = 0x%016X (%d)", value, value);
        return value;
    }

    @Override
    public float load_f32(Node node, long address) {
        WasmTracing.trace("load.f32 address = %d", address);
        validateAddress(node, address, 4);
        float value = unsafe.getFloat(startAddress + address);
        WasmTracing.trace("load.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value);
        return value;
    }

    @Override
    public double load_f64(Node node, long address) {
        WasmTracing.trace("load.f64 address = %d", address);
        validateAddress(node, address, 8);
        double value = unsafe.getDouble(startAddress + address);
        WasmTracing.trace("load.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value);
        return value;
    }

    @Override
    public int load_i32_8s(Node node, long address) {
        WasmTracing.trace("load.i32_8s address = %d", address);
        validateAddress(node, address, 1);
        int value = unsafe.getByte(startAddress + address);
        WasmTracing.trace("load.i32_8s value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_8u(Node node, long address) {
        WasmTracing.trace("load.i32_8u address = %d", address);
        validateAddress(node, address, 1);
        int value = 0x0000_00ff & unsafe.getByte(startAddress + address);
        WasmTracing.trace("load.i32_8u value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_16s(Node node, long address) {
        WasmTracing.trace("load.i32_16s address = %d", address);
        validateAddress(node, address, 2);
        int value = unsafe.getShort(startAddress + address);
        WasmTracing.trace("load.i32_16s value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_16u(Node node, long address) {
        WasmTracing.trace("load.i32_16u address = %d", address);
        validateAddress(node, address, 2);
        int value = 0x0000_ffff & unsafe.getShort(startAddress + address);
        WasmTracing.trace("load.i32_16u value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_8s(Node node, long address) {
        WasmTracing.trace("load.i64_8s address = %d", address);
        validateAddress(node, address, 1);
        long value = unsafe.getByte(startAddress + address);
        WasmTracing.trace("load.i64_8s value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_8u(Node node, long address) {
        WasmTracing.trace("load.i64_8u address = %d", address);
        validateAddress(node, address, 1);
        long value = 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
        WasmTracing.trace("load.i64_8u value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_16s(Node node, long address) {
        WasmTracing.trace("load.i64_16s address = %d", address);
        validateAddress(node, address, 2);
        long value = unsafe.getShort(startAddress + address);
        WasmTracing.trace("load.i64_16s value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_16u(Node node, long address) {
        WasmTracing.trace("load.i64_16u address = %d", address);
        validateAddress(node, address, 2);
        long value = 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
        WasmTracing.trace("load.i64_16u value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_32s(Node node, long address) {
        WasmTracing.trace("load.i64_32s address = %d", address);
        validateAddress(node, address, 4);
        long value = unsafe.getInt(startAddress + address);
        WasmTracing.trace("load.i64_32s value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_32u(Node node, long address) {
        WasmTracing.trace("load.i64_32u address = %d", address);
        validateAddress(node, address, 4);
        long value = 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
        WasmTracing.trace("load.i64_32u value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public void store_i32(Node node, long address, int value) {
        WasmTracing.trace("store.i32 address = %d, value = 0x%08X (%d)", address, value, value);
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public void store_i64(Node node, long address, long value) {
        WasmTracing.trace("store.i64 address = %d, value = 0x%016X (%d)", address, value, value);
        validateAddress(node, address, 8);
        unsafe.putLong(startAddress + address, value);

    }

    @Override
    public void store_f32(Node node, long address, float value) {
        WasmTracing.trace("store.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value);
        validateAddress(node, address, 4);
        unsafe.putFloat(startAddress + address, value);

    }

    @Override
    public void store_f64(Node node, long address, double value) {
        WasmTracing.trace("store.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value);
        validateAddress(node, address, 8);
        unsafe.putDouble(startAddress + address, value);
    }

    @Override
    public void store_i32_8(Node node, long address, byte value) {
        WasmTracing.trace("store.i32_8 address = %d, value = 0x%02X (%d)", address, value, value);
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i32_16(Node node, long address, short value) {
        WasmTracing.trace("store.i32_16 address = %d, value = 0x%04X (%d)", address, value, value);
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_8(Node node, long address, byte value) {
        WasmTracing.trace("store.i64_8 address = %d, value = 0x%02X (%d)", address, value, value);
        validateAddress(node, address, 1);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i64_16(Node node, long address, short value) {
        WasmTracing.trace("store.i64_16 address = %d, value = 0x%04X (%d)", address, value, value);
        validateAddress(node, address, 2);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_32(Node node, long address, int value) {
        WasmTracing.trace("store.i64_32 address = %d, value = 0x%08X (%d)", address, value, value);
        validateAddress(node, address, 4);
        unsafe.putInt(startAddress + address, value);
    }
    // Checkstyle: resume

    @Override
    public WasmMemory duplicate() {
        final UnsafeWasmMemory other = new UnsafeWasmMemory(pageSize, maxPageSize);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }
}
