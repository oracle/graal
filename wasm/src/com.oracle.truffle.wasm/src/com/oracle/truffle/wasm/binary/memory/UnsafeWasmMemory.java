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
package com.oracle.truffle.wasm.binary.memory;

import java.lang.reflect.Field;

import com.oracle.truffle.api.TruffleLogger;
import sun.misc.Unsafe;

public class UnsafeWasmMemory implements WasmMemory {
    private static final TruffleLogger logger = TruffleLogger.getLogger("wasm");

    private final Unsafe unsafe;
    private final long start;
    private final long memorySize;

    public UnsafeWasmMemory(long memorySize) {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.memorySize = memorySize;
        this.start = unsafe.allocateMemory(memorySize);
        unsafe.setMemory(start, memorySize, (byte) 0);
    }

    @Override
    public void validateAddress(long address, int size) {
        if (address + size > memorySize) {
            throw new WasmMemoryException("Requested memory address out-of-bounds");
        }
    }

    @Override
    public long startAddress() {
        return start;
    }

    @Override
    public int load_i32(long address) {
        int value = unsafe.getInt(start + address);
        logger.finest(() -> String.format("load.i32 address = %d, value = 0x%08X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64(long address) {
        long value = unsafe.getLong(start + address);
        logger.finest(() -> String.format("load.i64 address = %d, value = 0x%016X (%d)", address, value, value));
        return value;
    }

    @Override
    public float load_f32(long address) {
        float value = unsafe.getFloat(start + address);
        logger.finest(() -> String.format("load.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value));
        return value;
    }

    @Override
    public double load_f64(long address) {
        double value = unsafe.getDouble(start + address);
        logger.finest(() -> String.format("load.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value));
        return value;
    }

    @Override
    public int load_i32_8s(long address) {
        int value = unsafe.getByte(start + address);
        logger.finest(() -> String.format("load.i32_8s address = %d, value = 0x%02X (%d)", address, value, value));
        return value;
    }

    @Override
    public int load_i32_8u(long address) {
        int value = 0x0000_00ff & unsafe.getByte(start + address);
        logger.finest(() -> String.format("load.i32_8u address = %d, value = 0x%02X (%d)", address, value, value));
        return value;
    }

    @Override
    public int load_i32_16s(long address) {
        int value = unsafe.getShort(start + address);
        logger.finest(() -> String.format("load.i32_16s address = %d, value = 0x%04X (%d)", address, value, value));
        return value;
    }

    @Override
    public int load_i32_16u(long address) {
        int value = 0x0000_ffff & unsafe.getShort(start + address);
        logger.finest(() -> String.format("load.i32_16u address = %d, value = 0x%04X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_8s(long address) {
        long value = unsafe.getByte(start + address);
        logger.finest(() -> String.format("load.i64_8s address = %d, value = 0x%02X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_8u(long address) {
        long value = 0x0000_0000_0000_00ffL & unsafe.getByte(start + address);
        logger.finest(() -> String.format("load.i64_8u address = %d, value = 0x%02X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_16s(long address) {
        long value = unsafe.getShort(start + address);
        logger.finest(() -> String.format("load.i64_16s address = %d, value = 0x%04X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_16u(long address) {
        long value = 0x0000_0000_0000_ffffL & unsafe.getShort(start + address);
        logger.finest(() -> String.format("load.i64_16u address = %d, value = 0x%04X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_32s(long address) {
        long value = unsafe.getInt(start + address);
        logger.finest(() -> String.format("load.i64_32s address = %d, value = 0x%08X (%d)", address, value, value));
        return value;
    }

    @Override
    public long load_i64_32u(long address) {
        long value = 0x0000_0000_ffff_ffffL & unsafe.getInt(start + address);
        logger.finest(() -> String.format("load.i64_32u address = %d, value = 0x%08X (%d)", address, value, value));
        return value;
    }

    @Override
    public void store_i32(long address, int value) {
        logger.finest(() -> String.format("store.i32 address = %d, value = 0x%08X (%d)", address, value, value));
        unsafe.putInt(start + address, value);
    }

    @Override
    public void store_i64(long address, long value) {
        logger.finest(() -> String.format("store.i64 address = %d, value = 0x%016X (%d)", address, value, value));
        unsafe.putLong(start + address, value);

    }

    @Override
    public void store_f32(long address, float value) {
        logger.finest(() -> String.format("store.f32 address = %d, value = 0x%08X (%f)", address, Float.floatToRawIntBits(value), value));
        unsafe.putFloat(start + address, value);

    }

    @Override
    public void store_f64(long address, double value) {
        logger.finest(() -> String.format("store.f64 address = %d, value = 0x%016X (%f)", address, Double.doubleToRawLongBits(value), value));
        unsafe.putDouble(start + address, value);
    }

    @Override
    public void store_i32_8(long address, byte value) {
        logger.finest(() -> String.format("store.i32_8 address = %d, value = 0x%02X (%d)", address, value, value));
        unsafe.putByte(start + address, value);
    }

    @Override
    public void store_i32_16(long address, short value) {
        logger.finest(() -> String.format("store.i32_16 address = %d, value = 0x%04X (%d)", address, value, value));
        unsafe.putShort(start + address, value);
    }

    @Override
    public void store_i64_8(long address, byte value) {
        logger.finest(() -> String.format("store.i64_8 address = %d, value = 0x%02X (%d)", address, value, value));
        unsafe.putByte(start + address, value);
    }

    @Override
    public void store_i64_16(long address, short value) {
        logger.finest(() -> String.format("store.i64_16 address = %d, value = 0x%04X (%d)", address, value, value));
        unsafe.putShort(start + address, value);
    }

    @Override
    public void store_i64_32(long address, int value) {
        logger.finest(() -> String.format("store.i64_32 address = %d, value = 0x%08X (%d)", address, value, value));
        unsafe.putInt(start + address, value);
    }
}
