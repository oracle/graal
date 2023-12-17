/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;

@ExportLibrary(InteropLibrary.class)
public class Vector128 implements TruffleObject {

    // v128 component values are stored in little-endian order
    private static final ByteArraySupport byteArraySupport = ByteArraySupport.littleEndian();

    public static final Vector128 ZERO = new Vector128(new byte[16]);

    private final byte[] bytes;

    public Vector128(byte[] bytes) {
        assert bytes.length == 16;
        this.bytes = bytes;
    }

    public byte[] asBytes() {
        return bytes;
    }

    public static Vector128 ofBytes(byte[] bytes) {
        return new Vector128(bytes);
    }

    @ExplodeLoop
    public short[] asShorts() {
        short[] shorts = new short[8];
        for (int i = 0; i < 8; i++) {
            shorts[i] = byteArraySupport.getShort(bytes, i * 2);
        }
        return shorts;
    }

    @ExplodeLoop
    public static Vector128 ofShorts(short[] shorts) {
        assert shorts.length == 8;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 8; i++) {
            byteArraySupport.putShort(bytes, i * 2, shorts[i]);
        }
        return new Vector128(bytes);
    }

    @ExplodeLoop
    public int[] asInts() {
        int[] ints = new int[4];
        for (int i = 0; i < 4; i++) {
            ints[i] = byteArraySupport.getInt(bytes, i * 4);
        }
        return ints;
    }

    @ExplodeLoop
    public static Vector128 ofInts(int[] ints) {
        assert ints.length == 4;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putInt(bytes, i * 4, ints[i]);
        }
        return new Vector128(bytes);
    }

    @ExplodeLoop
    public long[] asLongs() {
        long[] longs = new long[2];
        for (int i = 0; i < 2; i++) {
            longs[i] = byteArraySupport.getLong(bytes, i * 8);
        }
        return longs;
    }

    @ExplodeLoop
    public static Vector128 ofLongs(long[] longs) {
        assert longs.length == 2;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putLong(bytes, i * 8, longs[i]);
        }
        return new Vector128(bytes);
    }

    @ExplodeLoop
    public float[] asFloats() {
        float[] floats = new float[4];
        for (int i = 0; i < 4; i++) {
            floats[i] = byteArraySupport.getFloat(bytes, i * 4);
        }
        return floats;
    }

    @ExplodeLoop
    public static Vector128 ofFloats(float[] floats) {
        assert floats.length == 4;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 4; i++) {
            byteArraySupport.putFloat(bytes, i * 4, floats[i]);
        }
        return new Vector128(bytes);
    }

    @ExplodeLoop
    public double[] asDoubles() {
        double[] doubles = new double[2];
        for (int i = 0; i < 2; i++) {
            doubles[i] = byteArraySupport.getDouble(bytes, i * 8);
        }
        return doubles;
    }

    @ExplodeLoop
    public static Vector128 ofDoubles(double[] doubles) {
        assert doubles.length == 2;
        byte[] bytes = new byte[16];
        for (int i = 0; i < 2; i++) {
            byteArraySupport.putDouble(bytes, i * 8, doubles[i]);
        }
        return new Vector128(bytes);
    }

    @ExportMessage
    protected boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    protected int getArraySize() {
        return 16;
    }

    @ExportMessage
    protected boolean isArrayElementReadable(long index) {
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
