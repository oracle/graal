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
package com.oracle.truffle.wasm.binary;

public abstract class WasmMemory {
    public abstract int load_i32(long address);

    public abstract long load_i64(long address);

    public abstract float load_f32(long address);

    public abstract double load_f64(long address);

    public abstract int load_i32_8s(long address);

    public abstract int load_i32_8u(long address);

    public abstract int load_i32_16s(long address);

    public abstract int load_i32_16u(long address);

    public abstract long load_i64_8s(long address);

    public abstract long load_i64_8u(long address);

    public abstract long load_i64_16s(long address);

    public abstract long load_i64_16u(long address);

    public abstract long load_i64_32s(long address);

    public abstract long load_i64_32u(long address);

    public abstract void store_i32(long address, int value);

    public abstract void store_i64(long address, long value);

    public abstract void store_f32(long address, float value);

    public abstract void store_f64(long address, double value);

    public abstract void store_i32_8(long address, int value);

    public abstract void store_i32_16(long address, int value);

    public abstract void store_i64_8(long address, long value);

    public abstract void store_i64_16(long address, long value);

    public abstract void store_i64_32(long address, long value);
}
