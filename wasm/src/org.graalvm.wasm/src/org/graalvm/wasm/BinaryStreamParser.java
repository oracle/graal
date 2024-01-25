/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm;

import static com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN;

import org.graalvm.wasm.api.Vector128;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.util.Arrays;

public abstract class BinaryStreamParser {
    protected static final int SINGLE_RESULT_VALUE = 0;
    protected static final int MULTI_RESULT_VALUE = 1;

    @CompilationFinal(dimensions = 1) protected byte[] data;
    protected int offset;

    public BinaryStreamParser(byte[] data) {
        this.data = data;
        this.offset = 0;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peekUnsignedInt32AndLength(byte[] data, int initialOffset) {
        int result = 0;
        int shift = 0;
        int currentOffset = initialOffset;
        byte b = (byte) 0x80;
        while ((b & 0x80) != 0 && shift != 42) {
            b = peek1(data, currentOffset);
            result |= (b & 0x7F) << shift;
            shift += 7;
            currentOffset++;
        }

        if (shift == 42) {
            throw WasmException.create(Failure.INTEGER_REPRESENTATION_TOO_LONG);
        } else if (shift == 35 && (0b0111_0000 & b) != 0) {
            throw WasmException.create(Failure.INTEGER_TOO_LARGE);
        }

        return packValueAndLength(result, currentOffset - initialOffset);
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peekSignedInt32AndLength(byte[] data, int initialOffset) {
        int result = 0;
        int shift = 0;
        int currentOffset = initialOffset;
        byte b = (byte) 0x80;
        while ((b & 0x80) != 0 && shift != 42) {
            b = peek1(data, currentOffset);
            result |= (b & 0x7F) << shift;
            shift += 7;
            currentOffset++;
        }

        if (shift == 42) {
            throw WasmException.create(Failure.INTEGER_REPRESENTATION_TOO_LONG);
        } else if (shift == 35 && (b & 0b0111_0000) != ((b & 0b1000) == 0 ? 0 : 0b0111_0000)) {
            throw WasmException.create(Failure.INTEGER_TOO_LARGE);
        }

        if (shift != 35 && (b & 0x40) != 0) {
            result |= (~0 << shift);
        }

        return packValueAndLength(result, currentOffset - initialOffset);
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peekUnsignedInt64(byte[] data, int initialOffset, boolean checkValid) {
        long result = 0;
        int shift = 0;
        int currentOffset = initialOffset;
        byte b = (byte) 0x80;
        while ((b & 0x80) != 0 && shift != 77) {
            b = peek1(data, currentOffset);
            result |= ((b & 0x7FL) << shift);
            shift += 7;
            currentOffset++;
        }

        if (checkValid) {
            if (shift == 77) {
                throw WasmException.create(Failure.INTEGER_REPRESENTATION_TOO_LONG);
            } else if (shift == 70 && (b & 0b0111_1110) != 0) {
                throw WasmException.create(Failure.INTEGER_TOO_LARGE);
            }
        }

        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peekSignedInt64(byte[] data, int initialOffset, boolean checkValid) {
        long result = 0;
        int shift = 0;
        int currentOffset = initialOffset;
        byte b = (byte) 0x80;
        while ((b & 0x80) != 0 && shift != 77) {
            b = peek1(data, currentOffset);
            result |= ((b & 0x7FL) << shift);
            shift += 7;
            currentOffset++;
        }

        if (checkValid) {
            if (shift == 77) {
                throw WasmException.create(Failure.INTEGER_REPRESENTATION_TOO_LONG);
            } else if (shift == 70 && (b & 0b0111_1110) != ((b & 1) == 0 ? 0 : 0b0111_1110)) {
                throw WasmException.create(Failure.INTEGER_TOO_LARGE);
            }
        }

        if (shift != 70 && (b & 0x40) != 0) {
            return result | (~0L << shift);
        }
        return result;
    }

    private static long packValueAndLength(int value, int length) {
        return ((long) length << 32) | (value & 0xffff_ffffL);
    }

    public static int value(long bits) {
        return (int) (bits & 0xffff_ffffL);
    }

    public static int length(long bits) {
        return (int) ((bits >>> 32) & 0xffff_ffffL);
    }

    protected int readFloatAsInt32() {
        return read4();
    }

    protected long readFloatAsInt64() {
        return read8();
    }

    protected byte readMutability() {
        final byte mut = peekMutability();
        offset++;
        return mut;
    }

    protected byte peekMutability() {
        final byte mut = peek1();
        if (mut == GlobalModifier.CONSTANT) {
            return mut;
        } else if (mut == GlobalModifier.MUTABLE) {
            return mut;
        } else {
            throw Assert.fail(Failure.MALFORMED_MUTABILITY, "Invalid mutability flag: " + mut);
        }
    }

    protected byte read1() {
        byte value = peek1(data, offset);
        offset++;
        return value;
    }

    protected byte peek1() {
        return peek1(data, offset);
    }

    public static byte peek1(byte[] data, int initialOffset) {
        // Inlined version of Assert.assertUnsignedIntLess(offset, data.length,
        // Failure.UNEXPECTED_END);
        if (initialOffset < 0 || initialOffset >= data.length) {
            throw WasmException.format(Failure.UNEXPECTED_END, "The binary is truncated at: %d", initialOffset);
        }
        return data[initialOffset];
    }

    public static short peek2(byte[] data, int initialOffset) {
        int result = 0;
        for (int i = 0; i != 2; ++i) {
            int x = Byte.toUnsignedInt(peek1(data, initialOffset + i));
            result |= x << 8 * i;
        }
        return (short) result;
    }

    protected int read4() {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = Byte.toUnsignedInt(read1());
            result |= x << 8 * i;
        }
        return result;
    }

    public static int peek4(byte[] data, int initialOffset) {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = Byte.toUnsignedInt(peek1(data, initialOffset + i));
            result |= x << 8 * i;
        }
        return result;
    }

    protected long read8() {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = Byte.toUnsignedLong(read1());
            result |= x << 8 * i;
        }
        return result;
    }

    public static long peek8(byte[] data, int initialOffset) {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = Byte.toUnsignedLong(peek1(data, initialOffset + i));
            result |= x << 8 * i;
        }
        return result;
    }

    protected int offset() {
        return offset;
    }

    /**
     * Reads the block type at the current location. The result is provided as two values. The first
     * is the actual value of the block type. The second is an indicator if it is a single result
     * type or a multi-value result.
     *
     * @param result The array used for returning the result.
     *
     */
    protected void readBlockType(int[] result, boolean allowRefTypes, boolean allowVecType) {
        byte type = peek1(data, offset);
        switch (type) {
            case WasmType.VOID_TYPE:
            case WasmType.I32_TYPE:
            case WasmType.I64_TYPE:
            case WasmType.F32_TYPE:
            case WasmType.F64_TYPE:
                offset++;
                result[0] = type;
                result[1] = SINGLE_RESULT_VALUE;
                break;
            case WasmType.V128_TYPE:
                Assert.assertTrue(allowVecType, Failure.MALFORMED_VALUE_TYPE);
                offset++;
                result[0] = type;
                result[1] = SINGLE_RESULT_VALUE;
                break;
            case WasmType.FUNCREF_TYPE:
            case WasmType.EXTERNREF_TYPE:
                Assert.assertTrue(allowRefTypes, Failure.MALFORMED_VALUE_TYPE);
                offset++;
                result[0] = type;
                result[1] = SINGLE_RESULT_VALUE;
                break;
            default:
                long valueAndLength = peekSignedInt32AndLength(data, offset);
                result[0] = value(valueAndLength);
                Assert.assertIntGreaterOrEqual(result[0], 0, Failure.UNSPECIFIED_MALFORMED);
                result[1] = MULTI_RESULT_VALUE;
                offset += length(valueAndLength);
        }
    }

    protected static byte peekValueType(byte[] data, int offset, boolean allowRefTypes, boolean allowVecType) {
        byte b = peek1(data, offset);
        switch (b) {
            case WasmType.I32_TYPE:
            case WasmType.I64_TYPE:
            case WasmType.F32_TYPE:
            case WasmType.F64_TYPE:
                break;
            case WasmType.V128_TYPE:
                Assert.assertTrue(allowVecType, Failure.MALFORMED_VALUE_TYPE);
                break;
            case WasmType.FUNCREF_TYPE:
            case WasmType.EXTERNREF_TYPE:
                Assert.assertTrue(allowRefTypes, Failure.MALFORMED_VALUE_TYPE);
                break;
            default:
                Assert.fail(Failure.MALFORMED_VALUE_TYPE, String.format("Invalid value type: 0x%02X", b));
        }
        return b;
    }

    protected byte readValueType(boolean allowRefTypes, boolean allowVecType) {
        byte b = peekValueType(data, offset, allowRefTypes, allowVecType);
        offset++;
        return b;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static byte peekLeb128Length(byte[] data, int initialOffset) {
        int currentOffset = initialOffset;
        byte length = 0;
        byte b = (byte) 0x80;
        while ((b & 0x80) != 0 && length < 12) {
            b = data[currentOffset];
            currentOffset++;
            length++;
        }
        return length;
    }

    // region bytecode

    /**
     * Reads the unsigned byte value at the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @return the unsigned byte value at the given bytecode offset.
     */
    public static int rawPeekU8(byte[] bytecode, int offset) {
        return bytecode[offset] & 0xFF;
    }

    /**
     * Reads the signed byte value at the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @return The signed byte value at the given bytecode offset.
     */
    public static byte rawPeekI8(byte[] bytecode, int offset) {
        return bytecode[offset];
    }

    /**
     * Reads the unsigned short value at the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @return The unsigned short value at the given bytecode offset.
     */
    public static int rawPeekU16(byte[] bytecode, int offset) {
        return ((bytecode[offset] & 0xFF) | ((bytecode[offset + 1] & 0xFF) << 8));
    }

    /**
     * Writes the unsigned short value to the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @param value The value that should be written
     */
    public static void writeU16(byte[] bytecode, int offset, int value) {
        final byte low = (byte) (value & 0xFF);
        final byte high = (byte) ((value >> 8) & 0xFF);
        bytecode[offset] = low;
        bytecode[offset + 1] = high;
    }

    /**
     * Reads the unsigned integer value at the given bytecode offset.
     *
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @return The unsigned integer value at the given bytecode offset.
     */
    public static long rawPeekU32(byte[] bytecode, int offset) {
        return (bytecode[offset] & 0xFFL) |
                        ((bytecode[offset + 1] & 0xFFL) << 8) |
                        ((bytecode[offset + 2] & 0xFFL) << 16) |
                        ((bytecode[offset + 3] & 0xFFL) << 24);
    }

    /**
     * Reads the signed integer value at the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode.
     * @return The signed integer value at the given bytecode offset.
     */
    public static int rawPeekI32(byte[] bytecode, int offset) {
        return (bytecode[offset] & 0xFF) |
                        ((bytecode[offset + 1] & 0xFF) << 8) |
                        ((bytecode[offset + 2] & 0xFF) << 16) |
                        ((bytecode[offset + 3] & 0xFF) << 24);
    }

    /**
     * Reads the signed long value at the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode.
     * @return The signed long value at the given bytecode offset.
     */
    public static long rawPeekI64(byte[] bytecode, int offset) {
        return (bytecode[offset] & 0xFFL) |
                        ((bytecode[offset + 1] & 0xFFL) << 8) |
                        ((bytecode[offset + 2] & 0xFFL) << 16) |
                        ((bytecode[offset + 3] & 0xFFL) << 24) |
                        ((bytecode[offset + 4] & 0xFFL) << 32) |
                        ((bytecode[offset + 5] & 0xFFL) << 40) |
                        ((bytecode[offset + 6] & 0xFFL) << 48) |
                        ((bytecode[offset + 7] & 0xFFL) << 56);
    }

    /**
     * Writes the signed long value to the given bytecode offset.
     * 
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode
     * @param value The value that should be written
     */
    public static void writeI64(byte[] bytecode, int offset, long value) {
        bytecode[offset] = (byte) (value & 0xFF);
        bytecode[offset + 1] = (byte) ((value >> 8) & 0xFF);
        bytecode[offset + 2] = (byte) ((value >> 16) & 0xFF);
        bytecode[offset + 3] = (byte) ((value >> 24) & 0xFF);
        bytecode[offset + 4] = (byte) ((value >> 32) & 0xFF);
        bytecode[offset + 5] = (byte) ((value >> 40) & 0xFF);
        bytecode[offset + 6] = (byte) ((value >> 48) & 0xFF);
        bytecode[offset + 7] = (byte) ((value >> 56) & 0xFF);
    }

    /**
     * Reads the {@link Vector128} value at the given bytecode offset.
     *
     * @param bytecode The bytecode
     * @param offset The offset in the bytecode.
     * @return The {@link Vector128} value at the given bytecode offset.
     */
    public static Vector128 rawPeekI128(byte[] bytecode, int offset) {
        byte[] bytes = Arrays.copyOfRange(bytecode, offset, offset + 16);
        return Vector128.ofBytes(bytes);
    }

    // endregion
}
