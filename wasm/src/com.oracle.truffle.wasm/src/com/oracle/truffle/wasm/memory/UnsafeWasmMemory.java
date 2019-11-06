/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.wasm.memory;

import java.lang.reflect.Field;

import com.oracle.truffle.wasm.exception.WasmTrap;
import sun.misc.Unsafe;

import static com.oracle.truffle.wasm.WasmTracing.trace;

public class UnsafeWasmMemory extends WasmMemory {
    private final Unsafe unsafe;
    private long startAddress;
    private long pageSize;
    private final long maxPageSize;

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
    public void validateAddress(long address, int offset) {
        trace("validating memory address: 0x%016X (%d)", address, address);
        if (address < 0 || address + offset >= this.byteSize()) {
            throw new WasmTrap(null, "Requested memory address out-of-bounds");
        }
    }

    @Override
    public void copy(long src, long dst, long n) {
        trace("memcopy from = %d, to = %d, n = %d", src, dst, n);
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

    @Override
    public int load_i32(long address) {
        trace("load.i32 address = %d", address);
        int value = unsafe.getInt(startAddress + address);
        trace("load.i32 value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64(long address) {
        trace("load.i64 address = %d", address);
        long value = unsafe.getLong(startAddress + address);
        trace("load.i64 value = 0x%016X (%d)", value, value);
        return value;
    }

    @Override
    public float load_f32(long address) {
        trace("load.f32 address = %d", address);
        float value = unsafe.getFloat(startAddress + address);
        trace("load.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value);
        return value;
    }

    @Override
    public double load_f64(long address) {
        trace("load.f64 address = %d", address);
        double value = unsafe.getDouble(startAddress + address);
        trace("load.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value);
        return value;
    }

    @Override
    public int load_i32_8s(long address) {
        trace("load.i32_8s address = %d", address);
        int value = unsafe.getByte(startAddress + address);
        trace("load.i32_8s value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_8u(long address) {
        trace("load.i32_8u address = %d", address);
        int value = 0x0000_00ff & unsafe.getByte(startAddress + address);
        trace("load.i32_8u value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_16s(long address) {
        trace("load.i32_16s address = %d", address);
        int value = unsafe.getShort(startAddress + address);
        trace("load.i32_16s value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public int load_i32_16u(long address) {
        trace("load.i32_16u address = %d", address);
        int value = 0x0000_ffff & unsafe.getShort(startAddress + address);
        trace("load.i32_16u value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_8s(long address) {
        trace("load.i64_8s address = %d", address);
        long value = unsafe.getByte(startAddress + address);
        trace("load.i64_8s value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_8u(long address) {
        trace("load.i64_8u address = %d", address);
        long value = 0x0000_0000_0000_00ffL & unsafe.getByte(startAddress + address);
        trace("load.i64_8u value = 0x%02X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_16s(long address) {
        trace("load.i64_16s address = %d", address);
        long value = unsafe.getShort(startAddress + address);
        trace("load.i64_16s value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_16u(long address) {
        trace("load.i64_16u address = %d", address);
        long value = 0x0000_0000_0000_ffffL & unsafe.getShort(startAddress + address);
        trace("load.i64_16u value = 0x%04X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_32s(long address) {
        trace("load.i64_32s address = %d", address);
        long value = unsafe.getInt(startAddress + address);
        trace("load.i64_32s value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public long load_i64_32u(long address) {
        trace("load.i64_32u address = %d", address);
        long value = 0x0000_0000_ffff_ffffL & unsafe.getInt(startAddress + address);
        trace("load.i64_32u value = 0x%08X (%d)", value, value);
        return value;
    }

    @Override
    public void store_i32(long address, int value) {
        trace("store.i32 address = %d, value = 0x%08X (%d)", address, value, value);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public void store_i64(long address, long value) {
        trace("store.i64 address = %d, value = 0x%016X (%d)", address, value, value);
        unsafe.putLong(startAddress + address, value);

    }

    @Override
    public void store_f32(long address, float value) {
        trace("store.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value);
        unsafe.putFloat(startAddress + address, value);

    }

    @Override
    public void store_f64(long address, double value) {
        trace("store.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value);
        unsafe.putDouble(startAddress + address, value);
    }

    @Override
    public void store_i32_8(long address, byte value) {
        trace("store.i32_8 address = %d, value = 0x%02X (%d)", address, value, value);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i32_16(long address, short value) {
        trace("store.i32_16 address = %d, value = 0x%04X (%d)", address, value, value);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_8(long address, byte value) {
        trace("store.i64_8 address = %d, value = 0x%02X (%d)", address, value, value);
        unsafe.putByte(startAddress + address, value);
    }

    @Override
    public void store_i64_16(long address, short value) {
        trace("store.i64_16 address = %d, value = 0x%04X (%d)", address, value, value);
        unsafe.putShort(startAddress + address, value);
    }

    @Override
    public void store_i64_32(long address, int value) {
        trace("store.i64_32 address = %d, value = 0x%08X (%d)", address, value, value);
        unsafe.putInt(startAddress + address, value);
    }

    @Override
    public WasmMemory duplicate() {
        final UnsafeWasmMemory other = new UnsafeWasmMemory(pageSize, maxPageSize);
        unsafe.copyMemory(this.startAddress, other.startAddress, this.byteSize());
        return other;
    }
}
