/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@ExportLibrary(InteropLibrary.class)
@CompilerDirectives.ValueType
public final class Vector128 implements TruffleObject {

    // v128 component values are stored in little-endian order
    private static final ByteArraySupport byteArraySupport = ByteArraySupport.littleEndian();

    public static final Vector128 ZERO = Vector128.ofBytes(new byte[16]);

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final byte[] bytes;

    private Vector128(byte[] bytes) {
        assert bytes.length == 16;
        this.bytes = bytes;
    }

    public byte[] asBytes() {
        return bytes;
    }

    public static Vector128 ofBytes(byte[] bytes) {
        return new Vector128(bytes);
    }

    public short[] asShorts() {
        return bytesAsShorts(bytes);
    }

    public static Vector128 ofShorts(short[] shorts) {
        return new Vector128(shortsAsBytes(shorts));
    }

    public int[] asInts() {
        return bytesAsInts(bytes);
    }

    public static Vector128 ofInts(int[] ints) {
        return new Vector128(intsAsBytes(ints));
    }

    public long[] asLongs() {
        return bytesAsLongs(bytes);
    }

    public static Vector128 ofLongs(long[] longs) {
        return new Vector128(longsAsBytes(longs));
    }

    public float[] asFloats() {
        return bytesAsFloats(bytes);
    }

    public static Vector128 ofFloats(float[] floats) {
        return new Vector128(floatsAsBytes(floats));
    }

    public double[] asDoubles() {
        return bytesAsDoubles(bytes);
    }

    public static Vector128 ofDoubles(double[] doubles) {
        return new Vector128(doublesAsBytes(doubles));
    }

    @ExplodeLoop
    public static short[] bytesAsShorts(byte[] bytes) {
        assert bytes.length == 16;
        short[] shorts = new short[8];
        for (int i = 0; i < 8; i++) {
            shorts[i] = byteArraySupport.getShort(bytes, i * 2);
        }
        return shorts;
    }

    @ExplodeLoop
    public static int[] bytesAsInts(byte[] bytes) {
        assert bytes.length == 16;
        int[] ints = new int[4];
        for (int i = 0; i < 4; i++) {
            ints[i] = byteArraySupport.getInt(bytes, i * 4);
        }
        return ints;
    }

    @ExplodeLoop
    public static long[] bytesAsLongs(byte[] bytes) {
        assert bytes.length == 16;
        long[] longs = new long[2];
        for (int i = 0; i < 2; i++) {
            longs[i] = byteArraySupport.getLong(bytes, i * 8);
        }
        return longs;
    }

    @ExplodeLoop
    public static float[] bytesAsFloats(byte[] bytes) {
        assert bytes.length == 16;
        float[] floats = new float[4];
        for (int i = 0; i < 4; i++) {
            floats[i] = byteArraySupport.getFloat(bytes, i * 4);
        }
        return floats;
    }

    @ExplodeLoop
    public static double[] bytesAsDoubles(byte[] bytes) {
        assert bytes.length == 16;
        double[] doubles = new double[2];
        for (int i = 0; i < 2; i++) {
            doubles[i] = byteArraySupport.getDouble(bytes, i * 8);
        }
        return doubles;
    }

    @ExplodeLoop
    public static byte[] shortsAsBytes(short[] shorts) {
        assert shorts.length == 8;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            byteArraySupport.putShort(bytes, i * 2, shorts[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] intsAsBytes(int[] ints) {
        assert ints.length == 4;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putInt(bytes, i * 4, ints[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] longsAsBytes(long[] longs) {
        assert longs.length == 2;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putLong(bytes, i * 8, longs[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] floatsAsBytes(float[] floats) {
        assert floats.length == 4;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putFloat(bytes, i * 4, floats[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] doublesAsBytes(double[] doubles) {
        assert doubles.length == 2;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putDouble(bytes, i * 8, doubles[i]);
        }
        return bytes;
    }

    @ExportMessage
    protected static boolean hasArrayElements(@SuppressWarnings("unused") Vector128 receiver) {
        return true;
    }

    @ExportMessage
    protected static int getArraySize(@SuppressWarnings("unused") Vector128 receiver) {
        return 16;
    }

    @ExportMessage
    protected static boolean isArrayElementReadable(@SuppressWarnings("unused") Vector128 receiver, long index) {
        return index < 16;
    }

    @ExportMessage
    protected byte readArrayElement(long index) throws InvalidArrayIndexException {
        if (index >= 16) {
            throw InvalidArrayIndexException.create(index);
        }
        return bytes[(int) index];
    }
}
