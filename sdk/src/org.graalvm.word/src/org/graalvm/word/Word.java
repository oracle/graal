/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.word.impl.WordFactoryOpcode;
import org.graalvm.word.impl.WordFactoryOperation;

/**
 * A concrete implementation of the various word interface types.
 */
public final class Word implements SignedWord, UnsignedWord, Pointer {

    private final long rawValue;

    private Word(long val) {
        this.rawValue = val;
    }

    @SuppressWarnings("unchecked")
    static <T extends WordBase> T box(long val) {
        return (T) new Word(val);
    }

    /**
     * The constant 0, i.e., the word with no bits set. There is no difference between a signed and
     * unsigned zero.
     *
     * @return the constant 0.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends WordBase> T zero() {
        return box(0L);
    }

    /**
     * The null pointer, i.e., a word with all bits set to 0. There is no difference between a
     * signed or unsigned {@link #zero}.
     *
     * @return a word value representing the null pointer
     * @see WordFactoryOpcode#ZERO
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends PointerBase> T nullPointer() {
        return box(0L);
    }

    /**
     * Creates a word from a {@code long}.
     *
     * @param val a 64-bit unsigned value
     * @return the value cast to Word
     * @see WordFactoryOpcode#FROM_UNSIGNED
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(long val) {
        return box(val);
    }

    /**
     * Unsafe conversion from a Java long value to a {@link PointerBase pointer}. The parameter is
     * treated as an unsigned 64-bit value (in contrast to the semantics of a Java long).
     * <p>
     * In an execution environment where this method returns a boxed value (e.g. not in Native
     * Image), the returned value will throw {@link UnsatisfiedLinkError} if any of the
     * {@link Pointer} memory access operations (i.e., read, write, compare-and-swap etc.) are
     * invoked on it.
     *
     * @param val a 64-bit unsigned value
     * @return the value cast to PointerBase
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends PointerBase> T pointer(long val) {
        return box(val);
    }

    /**
     * Creates a word from an {@code int}.
     *
     * @param val a 32-bit unsigned value
     * @return the value cast to Word
     * @see WordFactoryOpcode#FROM_UNSIGNED
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(int val) {
        return box(val & 0xffffffffL);
    }

    /**
     * Creates a word from a {@code long}.
     *
     * @param val a 64-bit signed value
     * @return the value cast to Word
     * @see WordFactoryOpcode#FROM_SIGNED
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(long val) {
        return box(val);
    }

    /**
     * Creates a word from an {@code int}.
     *
     * @param val a 32-bit signed value
     * @return the value cast to Word
     * @see WordFactoryOpcode#FROM_SIGNED
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(int val) {
        return box(val);
    }

    @Override
    @Operation(opcode = Word.Opcode.TO_OBJECT)
    public native Object toObject();

    @Override
    @Operation(opcode = Opcode.TO_TYPED_OBJECT)
    public native <T> T toObject(Class<T> clazz, boolean nonNull);

    @Override
    @Operation(opcode = Opcode.TO_OBJECT_NON_NULL)
    public native Object toObjectNonNull();

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native byte readByte(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native char readChar(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native short readShort(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native int readInt(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native long readLong(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native float readFloat(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native double readDouble(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native Object readObject(WordBase offset, LocationIdentity locationIdentity);

    @Operation(opcode = Word.Opcode.READ_HEAP)
    public native Object readObject(WordBase offset, BarrierType barrierType);

    @Operation(opcode = Word.Opcode.READ_HEAP)
    public native Object readObject(WordBase offset, BarrierType barrierType, LocationIdentity locationIdentity);

    @Operation(opcode = Word.Opcode.READ_HEAP)
    public Object readObject(int offset, BarrierType barrierType) {
        return readObject(signed(offset), barrierType);
    }

    @Operation(opcode = Word.Opcode.READ_HEAP)
    public Object readObject(int offset, BarrierType barrierType, LocationIdentity locationIdentity) {
        return readObject(signed(offset), barrierType, locationIdentity);
    }

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native byte readByte(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native char readChar(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native short readShort(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native int readInt(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native long readLong(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native float readFloat(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native double readDouble(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native Object readObject(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER_VOLATILE)
    public native <T extends WordBase> T readWordVolatile(int offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeChar(WordBase offset, char val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeShort(WordBase offset, short val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeInt(WordBase offset, int val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.INITIALIZE)
    public void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity) {

    }

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeByte(int offset, byte val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeChar(int offset, char val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeShort(int offset, short val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeInt(int offset, int val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeLong(int offset, long val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeFloat(int offset, float val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeDouble(int offset, double val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeWord(int offset, WordBase val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.INITIALIZE)
    public void initializeLong(int offset, long val, LocationIdentity locationIdentity) {

    }

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeObject(int offset, Object val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native byte readByte(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native char readChar(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native short readShort(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native int readInt(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native long readLong(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native float readFloat(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native double readDouble(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native <T extends WordBase> T readWord(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native Object readObject(WordBase offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native byte readByte(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native char readChar(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native short readShort(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native int readInt(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native long readLong(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native float readFloat(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native double readDouble(int offset);

    @Override
    @Operation(opcode = Word.Opcode.READ_POINTER)
    public native <T extends WordBase> T readWord(int offset);

    @Operation(opcode = Word.Opcode.READ_POINTER)
    @Override
    public native Object readObject(int offset);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeByte(WordBase offset, byte val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeChar(WordBase offset, char val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeShort(WordBase offset, short val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeInt(WordBase offset, int val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeLong(WordBase offset, long val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeFloat(WordBase offset, float val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeDouble(WordBase offset, double val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeWord(WordBase offset, WordBase val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeObject(WordBase offset, Object val);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity) {
        return false;
    }

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeByte(int offset, byte val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeChar(int offset, char val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeShort(int offset, short val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeInt(int offset, int val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeLong(int offset, long val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeFloat(int offset, float val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeDouble(int offset, double val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeWord(int offset, WordBase val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER)
    public native void writeObject(int offset, Object val);

    @Override
    @Operation(opcode = Word.Opcode.WRITE_POINTER_VOLATILE)
    public native void writeWordVolatile(int offset, WordBase val);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    @Override
    @Operation
    public Word add(UnsignedWord val) {
        return box(this.rawValue + val.rawValue());
    }

    @Operation
    public Word add(Word val) {
        return box(this.rawValue + val.rawValue);
    }

    @Override
    @Operation
    public Word subtract(UnsignedWord val) {
        return box(this.rawValue - val.rawValue());
    }

    @Operation
    public Word subtract(Word val) {
        return box(this.rawValue - val.rawValue);
    }

    @Override
    @Operation
    public UnsignedWord multiply(UnsignedWord val) {
        return box(this.rawValue * val.rawValue());
    }

    @Operation
    public Word multiply(Word val) {
        return box(this.rawValue * val.rawValue);
    }

    @Override
    @Operation
    public Word unsignedDivide(UnsignedWord val) {
        return box(Long.divideUnsigned(this.rawValue, val.rawValue()));
    }

    @Operation
    public Word unsignedDivide(Word val) {
        return box(Long.divideUnsigned(this.rawValue, val.rawValue));
    }

    @Override
    @Operation
    public Word unsignedRemainder(UnsignedWord val) {
        return box(Long.remainderUnsigned(this.rawValue, val.rawValue()));
    }

    @Operation
    public Word unsignedRemainder(Word val) {
        return box(Long.remainderUnsigned(this.rawValue, val.rawValue));
    }

    @Override
    @Operation
    public Pointer and(UnsignedWord val) {
        return box(this.rawValue & val.rawValue());
    }

    @Operation
    public Word and(Word val) {
        return box(this.rawValue & val.rawValue);
    }

    @Override
    @Operation
    public Word or(UnsignedWord val) {
        return box(this.rawValue | val.rawValue());
    }

    @Operation
    public Word or(Word val) {
        return box(this.rawValue | val.rawValue());
    }

    @Override
    @Operation
    public UnsignedWord xor(UnsignedWord val) {
        return box(this.rawValue ^ val.rawValue());
    }

    @Operation
    public Word xor(Word val) {
        return box(this.rawValue ^ val.rawValue);
    }

    @Override
    @Operation(opcode = Word.Opcode.IS_NULL)
    public boolean isNull() {
        return this.rawValue == 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.IS_NON_NULL)
    public boolean isNonNull() {
        return this.rawValue != 0;
    }

    @Override
    @Operation
    public Word add(SignedWord val) {
        return box(this.rawValue + val.rawValue());
    }

    @Override
    @Operation
    public SignedWord subtract(SignedWord val) {
        return box(this.rawValue - val.rawValue());
    }

    @Override
    @Operation
    public SignedWord multiply(SignedWord val) {
        return box(this.rawValue * val.rawValue());
    }

    @Override
    @Operation
    public Word signedDivide(SignedWord val) {
        return box(this.rawValue / val.rawValue());
    }

    @Operation
    public Word signedDivide(Word val) {
        return box(this.rawValue / val.rawValue);
    }

    @Override
    @Operation
    public Word signedRemainder(SignedWord val) {
        return box(this.rawValue % val.rawValue());
    }

    @Operation
    public Word signedRemainder(Word val) {
        return box(this.rawValue % val.rawValue);
    }

    @Override
    @Operation
    public Word shiftLeft(UnsignedWord n) {
        return box(this.rawValue << n.rawValue());
    }

    @Operation
    public Word shiftLeft(Word n) {
        return box(this.rawValue << n.rawValue);
    }

    @Override
    @Operation
    public UnsignedWord unsignedShiftRight(UnsignedWord n) {
        return box(this.rawValue >>> n.rawValue());
    }

    @Operation
    public Word unsignedShiftRight(Word n) {
        return box(this.rawValue >>> n.rawValue);
    }

    @Override
    @Operation
    public SignedWord signedShiftRight(UnsignedWord n) {
        return box(this.rawValue >> n.rawValue());
    }

    @Operation
    public SignedWord signedShiftRight(Word n) {
        return box(this.rawValue >> n.rawValue);
    }

    @Override
    @Operation
    public Word and(SignedWord val) {
        return box(this.rawValue & val.rawValue());
    }

    @Override
    @Operation
    public SignedWord or(SignedWord val) {
        return box(this.rawValue | val.rawValue());
    }

    @Override
    @Operation
    public SignedWord xor(SignedWord val) {
        return box(this.rawValue ^ val.rawValue());
    }

    @Override
    @Operation(opcode = Word.Opcode.NOT)
    public Word not() {
        return box(~this.rawValue);
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean equal(UnsignedWord val) {
        return this.rawValue == val.rawValue();
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean notEqual(UnsignedWord val) {
        return this.rawValue != val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean notEqual(Word val) {
        return this.rawValue != val.rawValue;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowThan(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) < 0;
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowThan(Word val) {
        return compareUnsigned(this.rawValue, val.rawValue) < 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowOrEqual(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) <= 0;
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowOrEqual(Word val) {
        return compareUnsigned(this.rawValue, val.rawValue) <= 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveThan(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) > 0;
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveThan(Word val) {
        return compareUnsigned(this.rawValue, val.rawValue) > 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveOrEqual(UnsignedWord val) {
        return compareUnsigned(this.rawValue, val.rawValue()) >= 0;
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveOrEqual(Word val) {
        return compareUnsigned(this.rawValue, val.rawValue) >= 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean equal(SignedWord val) {
        return this.rawValue == val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean equal(Word val) {
        return this.rawValue == val.rawValue;
    }

    /**
     * This is deprecated because of the easy to mistype name collision between {@code equals} and
     * the other equals routines like {@link #equal(Word)}.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @Override
    public boolean equals(Object obj) {
        throw new IllegalArgumentException("equals must not be called on words");
    }

    @Override
    public int hashCode() {
        throw new IllegalArgumentException("hashCode must not be called on words");
    }

    @Override
    public String toString() {
        return "Word<" + rawValue + ">";
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean notEqual(SignedWord val) {
        return this.rawValue != val.rawValue();
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessThan(SignedWord val) {
        return this.rawValue < val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessThan(Word val) {
        return this.rawValue < val.rawValue;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessOrEqual(SignedWord val) {
        return this.rawValue <= val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessOrEqual(Word val) {
        return this.rawValue <= val.rawValue;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterThan(SignedWord val) {
        return this.rawValue > val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterThan(Word val) {
        return this.rawValue > val.rawValue;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterOrEqual(SignedWord val) {
        return this.rawValue >= val.rawValue();
    }

    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterOrEqual(Word val) {
        return this.rawValue >= val.rawValue;
    }

    @Override
    @Operation
    public Word add(int val) {
        return box(this.rawValue + val);
    }

    @Override
    @Operation
    public Word subtract(int val) {
        return box(this.rawValue - val);
    }

    @Override
    @Operation
    public Word multiply(int val) {
        return box(this.rawValue * val);
    }

    @Override
    @Operation
    public UnsignedWord unsignedDivide(int val) {
        return box(Long.divideUnsigned(this.rawValue, val));
    }

    @Override
    @Operation
    public UnsignedWord unsignedRemainder(int val) {
        return box(Long.remainderUnsigned(this.rawValue, val));
    }

    @Override
    @Operation
    public SignedWord signedDivide(int val) {
        return box(this.rawValue / val);
    }

    @Override
    @Operation
    public SignedWord signedRemainder(int val) {
        return box(this.rawValue % val);
    }

    @Override
    @Operation
    public Word shiftLeft(int n) {
        return box(this.rawValue << n);
    }

    @Override
    @Operation
    public UnsignedWord unsignedShiftRight(int n) {
        return box(this.rawValue >>> n);
    }

    @Override
    @Operation
    public SignedWord signedShiftRight(int n) {
        return box(this.rawValue >> n);
    }

    @Override
    @Operation
    public Word and(int val) {
        return box(this.rawValue & val);
    }

    @Override
    @Operation
    public Word or(int val) {
        return box(this.rawValue | val);
    }

    @Override
    @Operation
    public Word xor(int val) {
        return box(this.rawValue ^ val);
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean equal(int val) {
        return this.rawValue == val;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean notEqual(int val) {
        return this.rawValue != 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowThan(int val) {
        return compareUnsigned(this.rawValue, val) < 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean belowOrEqual(int val) {
        return compareUnsigned(this.rawValue, val) <= 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveThan(int val) {
        return compareUnsigned(this.rawValue, val) > 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean aboveOrEqual(int val) {
        return compareUnsigned(this.rawValue, val) >= 0;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessThan(int val) {
        return this.rawValue < val;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean lessOrEqual(int val) {
        return this.rawValue <= val;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterThan(int val) {
        return this.rawValue > val;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean greaterOrEqual(int val) {
        return this.rawValue >= val;
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean equal(ComparableWord val) {
        return this.rawValue == val.rawValue();
    }

    @Override
    @Operation(opcode = Word.Opcode.COMPARISON)
    public boolean notEqual(ComparableWord val) {
        return this.rawValue != val.rawValue();
    }

    @Override
    @Operation(opcode = Word.Opcode.TO_RAW_VALUE)
    public long rawValue() {
        return rawValue;
    }

    /**
     * Links a method to a canonical operation represented by an {@link Word.Opcode} val.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Operation {
        Word.Opcode opcode() default Word.Opcode.ARITHMETIC;
    }

    /**
     * The canonical {@link Operation} represented by a method in the {@link Word} class.
     */
    public enum Opcode {
        ARITHMETIC,
        COMPARISON,
        IS_NULL,
        IS_NON_NULL,
        NOT,
        READ_POINTER,
        READ_POINTER_VOLATILE,
        READ_OBJECT,
        READ_BARRIERED,
        READ_BARRIERED_VOLATILE,
        READ_HEAP,
        WRITE_POINTER,
        WRITE_POINTER_SIDE_EFFECT_FREE,
        WRITE_POINTER_VOLATILE,
        WRITE_OBJECT,
        WRITE_BARRIERED,
        CAS_POINTER,
        INITIALIZE,
        FROM_ADDRESS,
        OBJECT_TO_TRACKED,
        OBJECT_TO_UNTRACKED,
        TO_OBJECT,
        TO_TYPED_OBJECT,
        TO_OBJECT_NON_NULL,
        TO_RAW_VALUE,
    }
}
