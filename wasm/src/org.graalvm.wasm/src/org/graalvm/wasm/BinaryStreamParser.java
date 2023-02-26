/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.Failure;
import org.graalvm.wasm.exception.WasmException;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;

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

    /**
     * Unchecked and manually unrolled version of {@link #peekUnsignedInt32AndLength}.
     */
    public static long rawPeekUnsignedInt32AndLength(byte[] data, int initialOffset) {
        int result = 0;
        byte b = data[initialOffset];
        result |= (b & 0x7F);
        if ((b & 0x80) == 0) {
            return packValueAndLength(result, 1);
        }
        b = data[initialOffset + 1];
        result |= (b & 0x7F) << 7;
        if ((b & 0x80) == 0) {
            return packValueAndLength(result, 2);
        }
        b = data[initialOffset + 2];
        result |= (b & 0x7F) << 14;
        if ((b & 0x80) == 0) {
            return packValueAndLength(result, 3);
        }
        b = data[initialOffset + 3];
        result |= (b & 0x7F) << 21;
        if ((b & 0x80) == 0) {
            return packValueAndLength(result, 4);
        }
        b = data[initialOffset + 4];
        result |= (b & 0x7F) << 28;
        return packValueAndLength(result, 5);
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

    /**
     * Unchecked and manually unrolled version of {@link #peekSignedInt32AndLength}.
     */
    public static long rawPeekSignedInt32AndLength(byte[] data, int initialOffset) {
        int result = 0;
        byte b = data[initialOffset];
        result |= (b & 0x7F);
        if ((b & 0x80) == 0) {
            if ((b & 0x40) != 0) {
                result |= (~0 << 7);
            }
            return packValueAndLength(result, 1);
        }
        b = data[initialOffset + 1];
        result |= (b & 0x7F) << 7;
        if ((b & 0x80) == 0) {
            if ((b & 0x40) != 0) {
                result |= (~0 << 14);
            }
            return packValueAndLength(result, 2);
        }
        b = data[initialOffset + 2];
        result |= (b & 0x7F) << 14;
        if ((b & 0x80) == 0) {
            if ((b & 0x40) != 0) {
                result |= (~0 << 21);
            }
            return packValueAndLength(result, 3);
        }
        b = data[initialOffset + 3];
        result |= (b & 0x7F) << 21;
        if ((b & 0x80) == 0) {
            if ((b & 0x40) != 0) {
                result |= (~0 << 28);
            }
            return packValueAndLength(result, 4);
        }
        b = data[initialOffset + 4];
        result |= (b & 0x7F) << 28;
        return packValueAndLength(result, 5);
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

    public static byte rawPeek1(byte[] data, int initialOffset) {
        return data[initialOffset];
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static int peek4(byte[] data, int initialOffset) {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = peek1(data, initialOffset + i) & 0xFF;
            result |= x << 8 * i;
        }
        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peek8(byte[] data, int initialOffset) {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = peek1(data, initialOffset + i) & 0xFF;
            result |= x << 8 * i;
        }
        return result;
    }

    protected int read4() {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = Byte.toUnsignedInt(read1());
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
    protected void readBlockType(int[] result, boolean allowRefTypes) {
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

    protected static byte peekValueType(byte[] data, int offset, boolean allowRefTypes) {
        byte b = peek1(data, offset);
        switch (b) {
            case WasmType.I32_TYPE:
            case WasmType.I64_TYPE:
            case WasmType.F32_TYPE:
            case WasmType.F64_TYPE:
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

    protected byte readValueType(boolean allowRefTypes) {
        byte b = peekValueType(data, offset, allowRefTypes);
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

    /**
     * Manually unrolled version of {@link #peekLeb128Length(byte[], int)}.
     */
    public static byte rawPeekLeb128IntLength(byte[] data, int initialOffset) {
        byte b = data[initialOffset];
        if ((b & 0x80) == 0) {
            return 1;
        }
        b = data[initialOffset + 1];
        if ((b & 0x80) == 0) {
            return 2;
        }
        b = data[initialOffset + 2];
        if ((b & 0x80) == 0) {
            return 3;
        }
        b = data[initialOffset + 3];
        if ((b & 0x80) == 0) {
            return 4;
        }
        return 5;
    }

    @TruffleBoundary
    protected void removeSection(int startOffset, int size) {
        final int endOffset = startOffset + size;
        final byte[] updatedData = new byte[data.length - size];
        System.arraycopy(data, 0, updatedData, 0, startOffset);
        final int remainingLength = data.length - endOffset;
        if (remainingLength != 0) {
            System.arraycopy(data, endOffset, updatedData, startOffset, remainingLength);
        }
        data = updatedData;
    }

    protected void replaceInstruction(int instructionOffset, byte newInstruction) {
        if (instructionOffset < 0 || instructionOffset >= data.length) {
            throw WasmException.format(Failure.UNSPECIFIED_INTERNAL, "Cannot replace out of bounds opcode");
        }
        data[instructionOffset] = newInstruction;
    }
}
