/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.api;

import static java.lang.Math.toIntExact;

import java.nio.ByteOrder;

import org.graalvm.wasm.memory.WasmMemory;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;

@ExportLibrary(InteropLibrary.class)
public class MemoryArrayBuffer implements TruffleObject {
    private final WasmMemory memory;

    public MemoryArrayBuffer(WasmMemory memory) {
        this.memory = memory;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean hasBufferElements() {
        return true;
    }

    @ExportMessage
    final long getBufferSize() {
        return memory.byteSize();
    }

    private void checkOffset(long byteOffset, int opLength, BranchProfile errorBranch) throws InvalidBufferOffsetException {
        if (byteOffset < 0 || getBufferSize() - opLength < byteOffset) {
            errorBranch.enter();
            throw InvalidBufferOffsetException.create(byteOffset, opLength);
        }
    }

    @ExportMessage
    final byte readBufferByte(long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Byte.BYTES, errorBranch);
        return (byte) memory.load_i32_8s(null, (int) byteOffset);
    }

    @ExportMessage
    final short readBufferShort(ByteOrder order, long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Short.BYTES, errorBranch);
        short result = (short) memory.load_i32_16s(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Short.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final int readBufferInt(ByteOrder order, long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Integer.BYTES, errorBranch);
        int result = memory.load_i32(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Integer.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final long readBufferLong(ByteOrder order, long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Long.BYTES, errorBranch);
        long result = memory.load_i64(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Long.reverseBytes(result);
        }
        return result;
    }

    @ExportMessage
    final float readBufferFloat(ByteOrder order, long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Float.BYTES, errorBranch);
        float result = memory.load_f32(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Float.intBitsToFloat(Integer.reverseBytes(Float.floatToRawIntBits(result)));
        }
        return result;
    }

    @ExportMessage
    final double readBufferDouble(ByteOrder order, long byteOffset,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Double.BYTES, errorBranch);
        double result = memory.load_f64(null, (int) byteOffset);
        if (order == ByteOrder.BIG_ENDIAN) {
            result = Double.longBitsToDouble(Long.reverseBytes(Double.doubleToRawLongBits(result)));
        }
        return result;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    final boolean isBufferWritable() {
        return true;
    }

    @ExportMessage
    final void writeBufferByte(long byteOffset, byte value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Byte.BYTES, errorBranch);
        memory.store_i32_8(null, (int) byteOffset, value);
    }

    @ExportMessage
    final void writeBufferShort(ByteOrder order, long byteOffset, short value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Short.BYTES, errorBranch);
        short actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Short.reverseBytes(value);
        memory.store_i32_16(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferInt(ByteOrder order, long byteOffset, int value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Integer.BYTES, errorBranch);
        int actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Integer.reverseBytes(value);
        memory.store_i32(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferLong(ByteOrder order, long byteOffset, long value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Long.BYTES, errorBranch);
        long actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Long.reverseBytes(value);
        memory.store_i64(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferFloat(ByteOrder order, long byteOffset, float value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Float.BYTES, errorBranch);
        float actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Float.intBitsToFloat(Integer.reverseBytes(Float.floatToRawIntBits(value)));
        memory.store_f32(null, (int) byteOffset, actualValue);
    }

    @ExportMessage
    final void writeBufferDouble(ByteOrder order, long byteOffset, double value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidBufferOffsetException {
        checkOffset(byteOffset, Double.BYTES, errorBranch);
        double actualValue = (order == ByteOrder.LITTLE_ENDIAN) ? value : Double.longBitsToDouble(Long.reverseBytes(Double.doubleToRawLongBits(value)));
        memory.store_f64(null, (int) byteOffset, actualValue);
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    boolean hasArrayElements() {
        return true;
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    long getArraySize() {
        return memory.byteSize();
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    boolean isArrayElementReadable(long index) {
        return index >= 0 && index < getArraySize();
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementModifiable(long index) {
        return isArrayElementReadable(index);
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final boolean isArrayElementInsertable(long index) {
        return isArrayElementReadable(index);
    }

    @SuppressWarnings({"unused"})
    @ExportMessage
    public Object readArrayElement(long index,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidArrayIndexException {
        if (!isArrayElementReadable(index)) {
            errorBranch.enter();
            throw InvalidArrayIndexException.create(index);
        }
        return memory.load_i32_8u(null, toIntExact(index));
    }

    @SuppressWarnings({"unused", "static-method"})
    @ExportMessage
    final void writeArrayElement(long index, Object value,
                    @Shared("error") @Cached("create()") BranchProfile errorBranch) throws InvalidArrayIndexException, UnsupportedTypeException {
        if (!isArrayElementModifiable(index)) {
            errorBranch.enter();
            throw InvalidArrayIndexException.create(index);
        }
        try {
            memory.store_i32_8(null, toIntExact(index), InteropLibrary.getFactory().getUncached().asByte(value));
        } catch (UnsupportedMessageException e) {
            errorBranch.enter();
            throw UnsupportedTypeException.create(new Object[]{value}, e.getMessage());
        }
    }
}
