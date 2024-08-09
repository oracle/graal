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
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import java.nio.ByteOrder;

@ExportLibrary(InteropLibrary.class)
@CompilerDirectives.ValueType
public final class Vector128 implements TruffleObject {

    // v128 component values are stored in little-endian order
    private static final ByteArraySupport byteArraySupport = ByteArraySupport.littleEndian();

    public static final Vector128 ZERO = new Vector128(new byte[16]);

    public static final int BYTES = 16;

    public static final int BYTE_LENGTH = BYTES / Byte.BYTES;
    public static final int SHORT_LENGTH = BYTES / Short.BYTES;
    public static final int INT_LENGTH = BYTES / Integer.BYTES;
    public static final int LONG_LENGTH = BYTES / Long.BYTES;
    public static final int FLOAT_LENGTH = BYTES / Float.BYTES;
    public static final int DOUBLE_LENGTH = BYTES / Double.BYTES;

    @CompilerDirectives.CompilationFinal(dimensions = 1) private final byte[] bytes;

    public Vector128(byte[] bytes) {
        assert bytes.length == BYTES;
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public short[] toShorts() {
        return fromBytesToShorts(bytes);
    }

    public static Vector128 fromShorts(short[] shorts) {
        return new Vector128(fromShortsToBytes(shorts));
    }

    public int[] toInts() {
        return fromBytesToInts(bytes);
    }

    public static Vector128 fromInts(int[] ints) {
        return new Vector128(fromIntsToBytes(ints));
    }

    public long[] toLongs() {
        return fromBytesToLongs(bytes);
    }

    public static Vector128 fromLongs(long[] longs) {
        return new Vector128(fromLongsToBytes(longs));
    }

    public float[] toFloats() {
        return fromBytesToFloats(bytes);
    }

    public static Vector128 fromFloats(float[] floats) {
        return new Vector128(fromFloatsToBytes(floats));
    }

    public double[] toDoubles() {
        return fromBytesToDoubles(bytes);
    }

    public static Vector128 fromDoubles(double[] doubles) {
        return new Vector128(fromDoublesToBytes(doubles));
    }

    @ExplodeLoop
    public static short[] fromBytesToShorts(byte[] bytes) {
        assert bytes.length == BYTES;
        short[] shorts = new short[SHORT_LENGTH];
        for (int i = 0; i < SHORT_LENGTH; i++) {
            shorts[i] = byteArraySupport.getShort(bytes, i * Short.BYTES);
        }
        return shorts;
    }

    @ExplodeLoop
    public static int[] fromBytesToInts(byte[] bytes) {
        assert bytes.length == BYTES;
        int[] ints = new int[INT_LENGTH];
        for (int i = 0; i < INT_LENGTH; i++) {
            ints[i] = byteArraySupport.getInt(bytes, i * Integer.BYTES);
        }
        return ints;
    }

    @ExplodeLoop
    public static long[] fromBytesToLongs(byte[] bytes) {
        assert bytes.length == BYTES;
        long[] longs = new long[LONG_LENGTH];
        for (int i = 0; i < LONG_LENGTH; i++) {
            longs[i] = byteArraySupport.getLong(bytes, i * Long.BYTES);
        }
        return longs;
    }

    @ExplodeLoop
    public static float[] fromBytesToFloats(byte[] bytes) {
        assert bytes.length == BYTES;
        float[] floats = new float[FLOAT_LENGTH];
        for (int i = 0; i < FLOAT_LENGTH; i++) {
            floats[i] = byteArraySupport.getFloat(bytes, i * Float.BYTES);
        }
        return floats;
    }

    @ExplodeLoop
    public static double[] fromBytesToDoubles(byte[] bytes) {
        assert bytes.length == BYTES;
        double[] doubles = new double[DOUBLE_LENGTH];
        for (int i = 0; i < DOUBLE_LENGTH; i++) {
            doubles[i] = byteArraySupport.getDouble(bytes, i * Double.BYTES);
        }
        return doubles;
    }

    @ExplodeLoop
    public static byte[] fromShortsToBytes(short[] shorts) {
        assert shorts.length == SHORT_LENGTH;
        byte[] bytes = new byte[BYTES];
        for (int i = 0; i < SHORT_LENGTH; i++) {
            byteArraySupport.putShort(bytes, i * Short.BYTES, shorts[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] fromIntsToBytes(int[] ints) {
        assert ints.length == INT_LENGTH;
        byte[] bytes = new byte[BYTES];
        for (int i = 0; i < INT_LENGTH; i++) {
            byteArraySupport.putInt(bytes, i * Integer.BYTES, ints[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] fromLongsToBytes(long[] longs) {
        assert longs.length == LONG_LENGTH;
        byte[] bytes = new byte[BYTES];
        for (int i = 0; i < LONG_LENGTH; i++) {
            byteArraySupport.putLong(bytes, i * Long.BYTES, longs[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] fromFloatsToBytes(float[] floats) {
        assert floats.length == FLOAT_LENGTH;
        byte[] bytes = new byte[BYTES];
        for (int i = 0; i < FLOAT_LENGTH; i++) {
            byteArraySupport.putFloat(bytes, i * Float.BYTES, floats[i]);
        }
        return bytes;
    }

    @ExplodeLoop
    public static byte[] fromDoublesToBytes(double[] doubles) {
        assert doubles.length == DOUBLE_LENGTH;
        byte[] bytes = new byte[BYTES];
        for (int i = 0; i < DOUBLE_LENGTH; i++) {
            byteArraySupport.putDouble(bytes, i * Double.BYTES, doubles[i]);
        }
        return bytes;
    }

    private static ByteArraySupport byteArraySupportForByteOrder(ByteOrder byteOrder) {
        if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
            return ByteArraySupport.littleEndian();
        } else {
            assert byteOrder == ByteOrder.BIG_ENDIAN;
            return ByteArraySupport.bigEndian();
        }
    }

    @ExportMessage
    protected static boolean hasBufferElements(@SuppressWarnings("unused") Vector128 receiver) {
        return true;
    }

    @ExportMessage
    protected static int getBufferSize(@SuppressWarnings("unused") Vector128 receiver) {
        return BYTES;
    }

    private void validateReadByteOffset(long byteOffset, int length) throws InvalidBufferOffsetException {
        if (byteOffset < 0 || byteOffset > getBufferSize(this) - length) {
            throw InvalidBufferOffsetException.create(byteOffset, length);
        }
    }

    @ExportMessage
    protected byte readBufferByte(long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Byte.BYTES);
        return bytes[(int) byteOffset];
    }

    protected void readBuffer(long byteOffset, byte[] destination, int destinationOffset, int length) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, length);
        System.arraycopy(bytes, (int) byteOffset, destination, destinationOffset, length);
    }

    @ExportMessage
    protected short readBufferShort(ByteOrder byteOrder, long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Short.BYTES);
        return byteArraySupportForByteOrder(byteOrder).getShort(bytes, (int) byteOffset);
    }

    @ExportMessage
    protected int readBufferInt(ByteOrder byteOrder, long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Integer.BYTES);
        return byteArraySupportForByteOrder(byteOrder).getInt(bytes, (int) byteOffset);
    }

    @ExportMessage
    protected long readBufferLong(ByteOrder byteOrder, long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Long.BYTES);
        return byteArraySupportForByteOrder(byteOrder).getLong(bytes, (int) byteOffset);
    }

    @ExportMessage
    protected float readBufferFloat(ByteOrder byteOrder, long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Float.BYTES);
        return byteArraySupportForByteOrder(byteOrder).getFloat(bytes, (int) byteOffset);
    }

    @ExportMessage
    protected double readBufferDouble(ByteOrder byteOrder, long byteOffset) throws InvalidBufferOffsetException {
        validateReadByteOffset(byteOffset, Double.BYTES);
        return byteArraySupportForByteOrder(byteOrder).getDouble(bytes, (int) byteOffset);
    }
}
