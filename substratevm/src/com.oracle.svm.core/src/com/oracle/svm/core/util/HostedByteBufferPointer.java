/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.util;

import java.nio.ByteBuffer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.config.ConfigurationValues;

import jdk.graal.compiler.core.common.NumUtil;

@Platforms(Platform.HOSTED_ONLY.class)
public final class HostedByteBufferPointer implements Pointer {
    private final ByteBuffer buffer;
    private final int baseOffset;

    public HostedByteBufferPointer(ByteBuffer buffer, int baseOffset) {
        assert buffer != null;
        this.buffer = buffer;
        this.baseOffset = baseOffset;
    }

    @Override
    public byte readByte(int offset) {
        return buffer.get(baseOffset + offset);
    }

    @Override
    public byte readByte(int offset, LocationIdentity locationIdentity) {
        return readByte(offset);
    }

    @Override
    public byte readByte(WordBase offset) {
        return readByte(offsetAsInt(offset));
    }

    @Override
    public byte readByte(WordBase offset, LocationIdentity locationIdentity) {
        return readByte(offset);
    }

    @Override
    public void writeByte(int offset, byte val) {
        buffer.put(baseOffset + offset, val);
    }

    @Override
    public void writeByte(WordBase offset, byte val) {
        writeByte(offsetAsInt(offset), val);
    }

    @Override
    public void writeByte(int offset, byte val, LocationIdentity locationIdentity) {
        writeByte(offset, val);
    }

    @Override
    public void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity) {
        writeByte(offset, val);
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isNonNull() {
        return true;
    }

    private static int offsetAsInt(WordBase offset) {
        return NumUtil.safeToInt(offset.rawValue());
    }

    private static RuntimeException unsupported() {
        throw VMError.intentionallyUnimplemented();
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw unsupported();
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw unsupported();
    }

    @Override
    public long rawValue() {
        throw unsupported();
    }

    @Override
    public Object toObject() {
        throw unsupported();
    }

    @Override
    public <T> T toObject(Class<T> clazz, boolean nonNull) {
        throw unsupported();
    }

    @Override
    public Object toObjectNonNull() {
        throw unsupported();
    }

    @Override
    public char readChar(WordBase offset, LocationIdentity locationIdentity) {
        return readChar(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public short readShort(WordBase offset, LocationIdentity locationIdentity) {
        return readShort(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public int readInt(WordBase offset, LocationIdentity locationIdentity) {
        return readInt(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public long readLong(WordBase offset, LocationIdentity locationIdentity) {
        return readLong(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public float readFloat(WordBase offset, LocationIdentity locationIdentity) {
        return readFloat(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public double readDouble(WordBase offset, LocationIdentity locationIdentity) {
        return readDouble(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity) {
        return readWord(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public Object readObject(WordBase offset, LocationIdentity locationIdentity) {
        return readObject(offsetAsInt(offset), locationIdentity);
    }

    @Override
    public char readChar(int offset, LocationIdentity locationIdentity) {
        return readChar(offset);
    }

    @Override
    public short readShort(int offset, LocationIdentity locationIdentity) {
        return readShort(offset);
    }

    @Override
    public int readInt(int offset, LocationIdentity locationIdentity) {
        return readInt(offset);
    }

    @Override
    public long readLong(int offset, LocationIdentity locationIdentity) {
        return readLong(offset);
    }

    @Override
    public float readFloat(int offset, LocationIdentity locationIdentity) {
        return readFloat(offset);
    }

    @Override
    public double readDouble(int offset, LocationIdentity locationIdentity) {
        return readDouble(offset);
    }

    @Override
    public <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity) {
        return readWord(offset);
    }

    @Override
    public Object readObject(int offset, LocationIdentity locationIdentity) {
        return readObject(offset);
    }

    @Override
    public <T extends WordBase> T readWordVolatile(int offset, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public void writeChar(WordBase offset, char val, LocationIdentity locationIdentity) {
        writeChar(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeShort(WordBase offset, short val, LocationIdentity locationIdentity) {
        writeShort(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeInt(WordBase offset, int val, LocationIdentity locationIdentity) {
        writeInt(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        writeLong(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity) {
        writeFloat(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity) {
        writeDouble(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity) {
        writeWord(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        initializeLong(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity) {
        writeObject(offsetAsInt(offset), val, locationIdentity);
    }

    @Override
    public void writeChar(int offset, char val, LocationIdentity locationIdentity) {
        writeChar(offset, val);
    }

    @Override
    public void writeShort(int offset, short val, LocationIdentity locationIdentity) {
        writeShort(offset, val);
    }

    @Override
    public void writeInt(int offset, int val, LocationIdentity locationIdentity) {
        writeInt(offset, val);
    }

    @Override
    public void writeLong(int offset, long val, LocationIdentity locationIdentity) {
        writeLong(offset, val);
    }

    @Override
    public void writeFloat(int offset, float val, LocationIdentity locationIdentity) {
        writeFloat(offset, val);
    }

    @Override
    public void writeDouble(int offset, double val, LocationIdentity locationIdentity) {
        writeDouble(offset, val);
    }

    @Override
    public void writeWord(int offset, WordBase val, LocationIdentity locationIdentity) {
        writeWord(offset, val);
    }

    @Override
    public void writeObject(int offset, Object val, LocationIdentity locationIdentity) {
        writeObject(offset, val);
    }

    @Override
    public void initializeLong(int offset, long val, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public char readChar(WordBase offset) {
        return readChar(offsetAsInt(offset));
    }

    @Override
    public short readShort(WordBase offset) {
        return readShort(offsetAsInt(offset));
    }

    @Override
    public int readInt(WordBase offset) {
        return readInt(offsetAsInt(offset));
    }

    @Override
    public long readLong(WordBase offset) {
        return readLong(offsetAsInt(offset));
    }

    @Override
    public float readFloat(WordBase offset) {
        return readFloat(offsetAsInt(offset));
    }

    @Override
    public double readDouble(WordBase offset) {
        return readDouble(offsetAsInt(offset));
    }

    @Override
    public <T extends WordBase> T readWord(WordBase offset) {
        return readWord(offsetAsInt(offset));
    }

    @Override
    public Object readObject(WordBase offset) {
        return readObject(offsetAsInt(offset));
    }

    @Override
    public char readChar(int offset) {
        return buffer.getChar(baseOffset + offset);
    }

    @Override
    public short readShort(int offset) {
        return buffer.getShort(baseOffset + offset);
    }

    @Override
    public int readInt(int offset) {
        return buffer.getInt(baseOffset + offset);
    }

    @Override
    public long readLong(int offset) {
        return buffer.getLong(baseOffset + offset);
    }

    @Override
    public float readFloat(int offset) {
        return buffer.getFloat(baseOffset + offset);
    }

    @Override
    public double readDouble(int offset) {
        return buffer.getDouble(baseOffset + offset);
    }

    @Override
    public <T extends WordBase> T readWord(int offset) {
        throw unsupported();
    }

    @Override
    public Object readObject(int offset) {
        throw unsupported();
    }

    @Override
    public void writeChar(WordBase offset, char val) {
        writeChar(offsetAsInt(offset), val);
    }

    @Override
    public void writeShort(WordBase offset, short val) {
        writeShort(offsetAsInt(offset), val);
    }

    @Override
    public void writeInt(WordBase offset, int val) {
        writeInt(offsetAsInt(offset), val);
    }

    @Override
    public void writeLong(WordBase offset, long val) {
        writeLong(offsetAsInt(offset), val);
    }

    @Override
    public void writeFloat(WordBase offset, float val) {
        writeFloat(offsetAsInt(offset), val);
    }

    @Override
    public void writeDouble(WordBase offset, double val) {
        writeDouble(offsetAsInt(offset), val);
    }

    @Override
    public void writeWord(WordBase offset, WordBase val) {
        writeWord(offsetAsInt(offset), val);
    }

    @Override
    public void writeObject(WordBase offset, Object val) {
        writeObject(offsetAsInt(offset), val);
    }

    @Override
    public int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public void writeChar(int offset, char val) {
        buffer.putChar(baseOffset + offset, val);
    }

    @Override
    public void writeShort(int offset, short val) {
        buffer.putShort(baseOffset + offset, val);
    }

    @Override
    public void writeInt(int offset, int val) {
        buffer.putInt(baseOffset + offset, val);
    }

    @Override
    public void writeLong(int offset, long val) {
        buffer.putLong(baseOffset + offset, val);
    }

    @Override
    public void writeFloat(int offset, float val) {
        buffer.putFloat(baseOffset + offset, val);
    }

    @Override
    public void writeDouble(int offset, double val) {
        buffer.putDouble(baseOffset + offset, val);
    }

    @Override
    public void writeWord(int offset, WordBase val) {
        long value = val.rawValue();
        int wordSize = ConfigurationValues.getTarget().wordSize;
        if (wordSize == Integer.BYTES) {
            buffer.putInt(baseOffset + offset, NumUtil.safeToUInt(value));
        } else {
            assert wordSize == Long.BYTES;
            buffer.putLong(baseOffset + offset, value);
        }
    }

    @Override
    public void writeObject(int offset, Object val) {
        throw unsupported();
    }

    @Override
    public void writeWordVolatile(int offset, WordBase val) {
        throw unsupported();
    }

    @Override
    public int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        throw unsupported();
    }

    @Override
    public Pointer add(UnsignedWord val) {
        return add(NumUtil.safeToInt(val.rawValue()));
    }

    @Override
    public Pointer add(int val) {
        return new HostedByteBufferPointer(buffer, baseOffset + val);
    }

    @Override
    public Pointer subtract(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord multiply(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedDivide(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedRemainder(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord shiftLeft(UnsignedWord n) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedShiftRight(UnsignedWord n) {
        throw unsupported();
    }

    @Override
    public Pointer subtract(int val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord multiply(int val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedDivide(int val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedRemainder(int val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord shiftLeft(int n) {
        throw unsupported();
    }

    @Override
    public UnsignedWord unsignedShiftRight(int n) {
        throw unsupported();
    }

    @Override
    public Pointer and(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public Pointer and(int val) {
        throw unsupported();
    }

    @Override
    public Pointer or(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord xor(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord not() {
        throw unsupported();
    }

    @Override
    public boolean equal(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public boolean notEqual(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public boolean belowThan(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public boolean belowOrEqual(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public boolean aboveThan(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public boolean aboveOrEqual(UnsignedWord val) {
        throw unsupported();
    }

    @Override
    public Pointer or(int val) {
        throw unsupported();
    }

    @Override
    public UnsignedWord xor(int val) {
        throw unsupported();
    }

    @Override
    public boolean equal(int val) {
        throw unsupported();
    }

    @Override
    public boolean notEqual(int val) {
        throw unsupported();
    }

    @Override
    public boolean belowThan(int val) {
        throw unsupported();
    }

    @Override
    public boolean belowOrEqual(int val) {
        throw unsupported();
    }

    @Override
    public boolean aboveThan(int val) {
        throw unsupported();
    }

    @Override
    public boolean aboveOrEqual(int val) {
        throw unsupported();
    }
}
