/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.word;

import static java.lang.Long.compareUnsigned;

/**
 * A box for words that supports all operations except memory accesses (see
 * {@link WordFactory#pointer(long)}).
 */
final class Word implements SignedWord, UnsignedWord, Pointer {

    private final long rawValue;

    Word(long val) {
        this.rawValue = val;
    }

    @SuppressWarnings("unchecked")
    static <T extends WordBase> T box(long val) {
        return (T) new Word(val);
    }

    @Override
    public native Object toObject();

    @Override
    public native <T> T toObject(Class<T> clazz, boolean nonNull);

    @Override
    public native Object toObjectNonNull();

    @Override
    public native byte readByte(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native char readChar(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native short readShort(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native int readInt(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native long readLong(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native float readFloat(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native double readDouble(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native Object readObject(WordBase offset, LocationIdentity locationIdentity);

    @Override
    public native byte readByte(int offset, LocationIdentity locationIdentity);

    @Override
    public native char readChar(int offset, LocationIdentity locationIdentity);

    @Override
    public native short readShort(int offset, LocationIdentity locationIdentity);

    @Override
    public native int readInt(int offset, LocationIdentity locationIdentity);

    @Override
    public native long readLong(int offset, LocationIdentity locationIdentity);

    @Override
    public native float readFloat(int offset, LocationIdentity locationIdentity);

    @Override
    public native double readDouble(int offset, LocationIdentity locationIdentity);

    @Override
    public native <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity);

    @Override
    public native Object readObject(int offset, LocationIdentity locationIdentity);

    @Override
    public native <T extends WordBase> T readWordVolatile(int offset, LocationIdentity locationIdentity);

    @Override
    public native void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity);

    @Override
    public native void writeChar(WordBase offset, char val, LocationIdentity locationIdentity);

    @Override
    public native void writeShort(WordBase offset, short val, LocationIdentity locationIdentity);

    @Override
    public native void writeInt(WordBase offset, int val, LocationIdentity locationIdentity);

    @Override
    public native void writeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    @Override
    public native void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity);

    @Override
    public native void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity);

    @Override
    public native void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity);

    @Override
    public void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity) {

    }

    @Override
    public native void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    @Override
    public native void writeByte(int offset, byte val, LocationIdentity locationIdentity);

    @Override
    public native void writeChar(int offset, char val, LocationIdentity locationIdentity);

    @Override
    public native void writeShort(int offset, short val, LocationIdentity locationIdentity);

    @Override
    public native void writeInt(int offset, int val, LocationIdentity locationIdentity);

    @Override
    public native void writeLong(int offset, long val, LocationIdentity locationIdentity);

    @Override
    public native void writeFloat(int offset, float val, LocationIdentity locationIdentity);

    @Override
    public native void writeDouble(int offset, double val, LocationIdentity locationIdentity);

    @Override
    public native void writeWord(int offset, WordBase val, LocationIdentity locationIdentity);

    @Override
    public void initializeLong(int offset, long val, LocationIdentity locationIdentity) {

    }

    @Override
    public native void writeObject(int offset, Object val, LocationIdentity locationIdentity);

    @Override
    public native byte readByte(WordBase offset);

    @Override
    public native char readChar(WordBase offset);

    @Override
    public native short readShort(WordBase offset);

    @Override
    public native int readInt(WordBase offset);

    @Override
    public native long readLong(WordBase offset);

    @Override
    public native float readFloat(WordBase offset);

    @Override
    public native double readDouble(WordBase offset);

    @Override
    public native <T extends WordBase> T readWord(WordBase offset);

    @Override
    public native Object readObject(WordBase offset);

    @Override
    public native byte readByte(int offset);

    @Override
    public native char readChar(int offset);

    @Override
    public native short readShort(int offset);

    @Override
    public native int readInt(int offset);

    @Override
    public native long readLong(int offset);

    @Override
    public native float readFloat(int offset);

    @Override
    public native double readDouble(int offset);

    @Override
    public native <T extends WordBase> T readWord(int offset);

    @Override
    public native Object readObject(int offset);

    @Override
    public native void writeByte(WordBase offset, byte val);

    @Override
    public native void writeChar(WordBase offset, char val);

    @Override
    public native void writeShort(WordBase offset, short val);

    @Override
    public native void writeInt(WordBase offset, int val);

    @Override
    public native void writeLong(WordBase offset, long val);

    @Override
    public native void writeFloat(WordBase offset, float val);

    @Override
    public native void writeDouble(WordBase offset, double val);

    @Override
    public native void writeWord(WordBase offset, WordBase val);

    @Override
    public native void writeObject(WordBase offset, Object val);

    @Override
    public native int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    public native long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    public native <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    @Override
    public native Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    public boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    public boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    public boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    public boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    public native void writeByte(int offset, byte val);

    @Override
    public native void writeChar(int offset, char val);

    @Override
    public native void writeShort(int offset, short val);

    @Override
    public native void writeInt(int offset, int val);

    @Override
    public native void writeLong(int offset, long val);

    @Override
    public native void writeFloat(int offset, float val);

    @Override
    public native void writeDouble(int offset, double val);

    @Override
    public native void writeWord(int offset, WordBase val);

    @Override
    public native void writeObject(int offset, Object val);

    @Override
    public native void writeWordVolatile(int offset, WordBase val);

    @Override
    public native int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    public native long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    public native <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    @Override
    public native Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    public native boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    public native boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    public native boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    @Override
    public native boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    public Pointer add(UnsignedWord val) {
        return box(this.rawValue + val.rawValue());
    }

    @Override
    public Pointer subtract(UnsignedWord val) {
        return box(this.rawValue - val.rawValue());
    }

    @Override
    public UnsignedWord multiply(UnsignedWord val) {
        return box(this.rawValue * val.rawValue());
    }

    @Override
    public UnsignedWord unsignedDivide(UnsignedWord val) {
        return box(Long.divideUnsigned(this.rawValue, val.rawValue()));
    }

    @Override
    public UnsignedWord unsignedRemainder(UnsignedWord val) {
        return box(Long.remainderUnsigned(this.rawValue, val.rawValue()));
    }

    @Override
    public Pointer and(UnsignedWord val) {
        return box(this.rawValue & val.rawValue());
    }

    @Override
    public Pointer or(UnsignedWord val) {
        return box(this.rawValue | val.rawValue());
    }

    @Override
    public UnsignedWord xor(UnsignedWord val) {
        return box(this.rawValue ^ val.rawValue());
    }

    @Override
    public boolean isNull() {
        return this.rawValue == 0;
    }

    @Override
    public boolean isNonNull() {
        return this.rawValue != 0;
    }

    @Override
    public SignedWord add(SignedWord val) {
        return box(this.rawValue + val.rawValue());
    }

    @Override
    public SignedWord subtract(SignedWord val) {
        return box(this.rawValue - val.rawValue());
    }

    @Override
    public SignedWord multiply(SignedWord val) {
        return box(this.rawValue * val.rawValue());
    }

    @Override
    public SignedWord signedDivide(SignedWord val) {
        return box(this.rawValue / val.rawValue());
    }

    @Override
    public SignedWord signedRemainder(SignedWord val) {
        return box(this.rawValue % val.rawValue());
    }

    @Override
    public Word shiftLeft(UnsignedWord n) {
        return box(this.rawValue << n.rawValue());
    }

    @Override
    public UnsignedWord unsignedShiftRight(UnsignedWord n) {
        return box(this.rawValue >>> n.rawValue());
    }

    @Override
    public SignedWord signedShiftRight(UnsignedWord n) {
        return box(this.rawValue >> n.rawValue());
    }

    @Override
    public SignedWord and(SignedWord val) {
        return box(this.rawValue & val.rawValue());
    }

    @Override
    public SignedWord or(SignedWord val) {
        return box(this.rawValue | val.rawValue());
    }

    @Override
    public SignedWord xor(SignedWord val) {
        return box(this.rawValue ^ val.rawValue());
    }

    @Override
    public Word not() {
        return box(~this.rawValue);
    }

    @Override
    public boolean equal(UnsignedWord val) {
        return this.rawValue == val.rawValue();
    }

    @Override
    public boolean notEqual(UnsignedWord val) {
        return this.rawValue != val.rawValue();
    }

    @Override
    public boolean belowThan(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) < 0;
    }

    @Override
    public boolean belowOrEqual(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) <= 0;
    }

    @Override
    public boolean aboveThan(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) > 0;
    }

    @Override
    public boolean aboveOrEqual(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) >= 0;
    }

    @Override
    public boolean equal(SignedWord val) {
        return this.rawValue == val.rawValue();
    }

    @Override
    public boolean notEqual(SignedWord val) {
        return this.rawValue != val.rawValue();
    }

    @Override
    public boolean lessThan(SignedWord val) {
        return this.rawValue < val.rawValue();
    }

    @Override
    public boolean lessOrEqual(SignedWord val) {
        return this.rawValue <= val.rawValue();
    }

    @Override
    public boolean greaterThan(SignedWord val) {
        return this.rawValue > val.rawValue();
    }

    @Override
    public boolean greaterOrEqual(SignedWord val) {
        return this.rawValue >= val.rawValue();
    }

    @Override
    public Word add(int val) {
        return box(this.rawValue + val);
    }

    @Override
    public Word subtract(int val) {
        return box(this.rawValue - val);
    }

    @Override
    public Word multiply(int val) {
        return box(this.rawValue * val);
    }

    @Override
    public UnsignedWord unsignedDivide(int val) {
        return box(Long.divideUnsigned(this.rawValue, val));
    }

    @Override
    public UnsignedWord unsignedRemainder(int val) {
        return box(Long.remainderUnsigned(this.rawValue, val));
    }

    @Override
    public SignedWord signedDivide(int val) {
        return box(this.rawValue / val);
    }

    @Override
    public SignedWord signedRemainder(int val) {
        return box(this.rawValue % val);
    }

    @Override
    public Word shiftLeft(int n) {
        return box(this.rawValue << n);
    }

    @Override
    public UnsignedWord unsignedShiftRight(int n) {
        return box(this.rawValue >>> n);
    }

    @Override
    public SignedWord signedShiftRight(int n) {
        return box(this.rawValue >> n);
    }

    @Override
    public Word and(int val) {
        return box(this.rawValue & val);
    }

    @Override
    public Word or(int val) {
        return box(this.rawValue | val);
    }

    @Override
    public Word xor(int val) {
        return box(this.rawValue ^ val);
    }

    @Override
    public boolean equal(int val) {
        return this.rawValue == 0;
    }

    @Override
    public boolean notEqual(int val) {
        return this.rawValue != 0;
    }

    @Override
    public boolean belowThan(int val) {
        return compareUnsigned(this.rawValue, val) < 0;
    }

    @Override
    public boolean belowOrEqual(int val) {
        return compareUnsigned(this.rawValue, val) <= 0;
    }

    @Override
    public boolean aboveThan(int val) {
        return compareUnsigned(this.rawValue, val) > 0;
    }

    @Override
    public boolean aboveOrEqual(int val) {
        return compareUnsigned(this.rawValue, val) >= 0;
    }

    @Override
    public boolean lessThan(int val) {
        return this.rawValue < val;
    }

    @Override
    public boolean lessOrEqual(int val) {
        return this.rawValue <= val;
    }

    @Override
    public boolean greaterThan(int val) {
        return this.rawValue > val;
    }

    @Override
    public boolean greaterOrEqual(int val) {
        return this.rawValue >= val;
    }

    @Override
    public boolean equal(ComparableWord val) {
        return this.rawValue == val.rawValue();
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        return this.rawValue != val.rawValue();
    }

    @Override
    public long rawValue() {
        return rawValue;
    }
}
