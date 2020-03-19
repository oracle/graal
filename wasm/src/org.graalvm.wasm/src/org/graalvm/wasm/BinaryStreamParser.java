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
package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import org.graalvm.wasm.constants.GlobalModifier;
import org.graalvm.wasm.exception.BinaryParserException;

import static com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind.FULL_EXPLODE_UNTIL_RETURN;

public abstract class BinaryStreamParser {
    @CompilationFinal(dimensions = 1) protected byte[] data;
    protected int offset;

    public BinaryStreamParser(byte[] data) {
        this.data = data;
        this.offset = 0;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static int peekSignedInt32(byte[] data, int initialOffset) {
        int result = 0;
        int shift = 0;
        int offset = initialOffset;
        byte b;
        do {
            b = peek1(data, offset);
            offset++;
            result |= ((b & 0x7F) << shift);
            shift += 7;
        } while ((b & 0x80) != 0);

        if ((shift < 32) && (b & 0x40) != 0) {
            result |= (~0 << shift);
        }
        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static int peekUnsignedInt32(byte[] data, int initialOffset) {
        int result = 0;
        int shift = 0;
        int offset = initialOffset;
        do {
            byte b = peek1(data, offset);
            offset++;
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
        } while (shift < 35);
        if (shift == 35) {
            Assert.fail("Unsigned LEB128 overflow");
        }

        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public int peekUnsignedInt32(int ahead) {
        int result = 0;
        int shift = 0;
        int i = 0;
        do {
            byte b = peek1(i + ahead);
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                break;
            }
            shift += 7;
            i++;
        } while (shift < 35);
        if (shift == 35) {
            Assert.fail("Unsigned LEB128 overflow");
        }
        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peekSignedInt64(byte[] data, int initialOffset) {
        long result = 0;
        int shift = 0;
        int offset = initialOffset;
        byte b;
        do {
            b = peek1(data, offset);
            result |= ((b & 0x7FL) << shift);
            shift += 7;
            offset++;
        } while ((b & 0x80) != 0 && shift < 70);

        if ((shift < 64) && (b & 0x40) != 0) {
            result |= (~0L << shift);
        }

        return result;
    }

    protected static int peekFloatAsInt32(byte[] data, int offset) {
        return peek4(data, offset);
    }

    protected int readFloatAsInt32() {
        return read4();
    }

    protected static long peekFloatAsInt64(byte[] data, int offset) {
        return peek8(data, offset);
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
            throw Assert.fail("Invalid mutability flag: " + mut);
        }
    }

    protected byte read1() {
        byte value = peek1(data, offset);
        offset++;
        return value;
    }

    public static byte peek1(byte[] data, int offset) {
        return data[offset];
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static int peek4(byte[] data, int offset) {
        int result = 0;
        for (int i = 0; i != 4; ++i) {
            int x = peek1(data, offset + i) & 0xFF;
            result |= x << 8 * i;
        }
        return result;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static long peek8(byte[] data, int offset) {
        long result = 0;
        for (int i = 0; i != 8; ++i) {
            long x = peek1(data, offset + i) & 0xFF;
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

    protected byte peek1() {
        return data[offset];
    }

    protected byte peek1(int ahead) {
        return data[offset + ahead];
    }

    protected int offset() {
        return offset;
    }

    protected static byte peekBlockType(byte[] data, int offset) {
        byte type = peek1(data, offset);
        switch (type) {
            case 0x00:
            case ValueTypes.VOID_TYPE:
                return ValueTypes.VOID_TYPE;
            default:
                return peekValueType(data, offset);
        }
    }

    protected byte readBlockType() {
        byte type = peekBlockType(data, offset);
        offset++;
        return type;
    }

    protected static byte peekValueType(byte[] data, int offset) {
        byte b = peek1(data, offset);
        switch (b) {
            case ValueTypes.I32_TYPE:
            case ValueTypes.I64_TYPE:
            case ValueTypes.F32_TYPE:
            case ValueTypes.F64_TYPE:
                break;
            default:
                Assert.fail(String.format("Invalid value type: 0x%02X", b));
        }
        return b;
    }

    protected byte readValueType() {
        byte b = peekValueType(data, offset);
        offset++;
        return b;
    }

    @ExplodeLoop(kind = FULL_EXPLODE_UNTIL_RETURN)
    public static byte peekLeb128Length(byte[] data, int initialOffset) {
        int offset = initialOffset;
        byte length = 0;
        byte b;
        do {
            b = data[offset];
            offset++;
            length++;
        } while ((b & 0x80) != 0 && length < 12);

        return length;
    }

    public static boolean mustPoolLeb128(byte[] data, int offset, WasmOptions.StoreConstantsPolicyEnum storeConstantsInPool) {
        switch (storeConstantsInPool) {
            case ALL:
                return true;
            case LARGE_ONLY:
                return (data[offset] & 0x80) != 0;
            case NONE:
                return false;
            default:
                throw new BinaryParserException("Invalid StoreConstantsInPoolChoice");
        }
    }
}
