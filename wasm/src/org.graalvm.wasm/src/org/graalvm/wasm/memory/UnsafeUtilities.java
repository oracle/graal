/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteOrder;

final class UnsafeUtilities {
    private static final Unsafe unsafe = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    private UnsafeUtilities() {
        // no instantiation allowed
    }

    static byte compareAndExchangeByte(long startAddress, long byteOffset, byte expected, byte x) {
        long wordOffset = byteOffset & ~3;
        int shift = (int) (byteOffset & 3) << 3;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            shift = 24 - shift;
        }
        int mask = 0xFF << shift;
        int maskedExpected = (expected & 0xFF) << shift;
        int maskedX = (x & 0xFF) << shift;
        int fullWord;
        do {
            fullWord = unsafe.getIntVolatile(null, startAddress + wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (byte) ((fullWord & mask) >> shift);
            }
        } while (!unsafe.compareAndSwapInt(null, startAddress + wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    static short compareAndExchangeShort(long startAddress, long byteOffset, short expected, short x) {
        if ((byteOffset & 3) == 3) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~3;
        int shift = (int) (byteOffset & 3) << 3;
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            shift = 16 - shift;
        }
        int mask = 0xFFFF << shift;
        int maskedExpected = (expected & 0xFFFF) << shift;
        int maskedX = (x & 0xFFFF) << shift;
        int fullWord;
        do {
            fullWord = unsafe.getIntVolatile(null, startAddress + wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (short) ((fullWord & mask) >> shift);
            }
        } while (!unsafe.compareAndSwapInt(null, startAddress + wordOffset,
                        fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    static int compareAndExchangeInt(long startAddress, long byteOffset, int expected, int x) throws IndexOutOfBoundsException {
        if ((byteOffset & 3) != 0) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~3;
        int fullWord;
        do {
            fullWord = unsafe.getIntVolatile(null, startAddress + wordOffset);
            if (fullWord != expected) {
                return fullWord;
            }
        } while (!unsafe.compareAndSwapInt(null, startAddress + wordOffset, fullWord, x));
        return expected;
    }

    static long compareAndExchangeLong(long startAddress, long byteOffset, long expected, long x) throws IndexOutOfBoundsException {
        if ((byteOffset & 7) != 0) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = byteOffset & ~7;
        long fullWord;
        do {
            fullWord = unsafe.getLongVolatile(null, startAddress + wordOffset);
            if (fullWord != expected) {
                return fullWord;
            }
        } while (!unsafe.compareAndSwapLong(null, startAddress + wordOffset, fullWord, x));
        return expected;
    }
}
