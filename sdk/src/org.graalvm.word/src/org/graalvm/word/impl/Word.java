/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.word.impl;

import static java.lang.Long.compareUnsigned;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.word.ComparableWord;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

/**
 * A concrete implementation of the various word interface types.
 * <p>
 *
 * @see WordFactory
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
     * Implements {@link WordFactory#zero()}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends WordBase> T zero() {
        return box(0L);
    }

    /**
     * Implements {@link WordFactory#nullPointer()}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.ZERO)
    public static <T extends PointerBase> T nullPointer() {
        return box(0L);
    }

    /**
     * Implements {@link WordFactory#unsigned(long)}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(long val) {
        return box(val);
    }

    /**
     * Implements {@link WordFactory#pointer(long)}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends PointerBase> T pointer(long val) {
        return box(val);
    }

    /**
     * Implements {@link WordFactory#unsigned(int)}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_UNSIGNED)
    public static <T extends UnsignedWord> T unsigned(int val) {
        return box(val & 0xffffffffL);
    }

    /**
     * Implements {@link WordFactory#signed(long)}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(long val) {
        return box(val);
    }

    /**
     * Implements {@link WordFactory#signed(int)}.
     */
    @WordFactoryOperation(opcode = WordFactoryOpcode.FROM_SIGNED)
    public static <T extends SignedWord> T signed(int val) {
        return box(val);
    }

    /// Converts the [Object] value in `val` to a [Pointer] value representing the
    /// object's current address. If the object is subsequently moved (e.g. by the garbage
    /// collector), the returned pointer value is updated accordingly. If a derived pointer value is
    /// obtained by applying pointer arithmetic to the returned pointer value, it will also be
    /// updated accordingly. If derived pointers are not supported, an error is thrown. In the case
    /// of Native Image, the error is thrown at build time.
    ///
    /// Examples of expected usage:
    /// ```
    /// // Pointer value is tracked across a garbage collection
    /// Pointer oop = ObjectAccess.objectToTrackedPointer(obj);
    /// System.gc();
    /// Pointer oop2 = ObjectAccess.objectToTrackedPointer(obj);
    /// assert oop.equal(oop2); // `oop` will have been updated if `obj` was moved
    ///
    /// // Derived pointer value is tracked across a garbage collection
    /// Pointer oop = ObjectAccess.objectToTrackedPointer(obj);
    /// Pointer derived1 = oop.add(1);
    /// Pointer derived2 = oop.add(7);
    /// assert derived1.equal(oop.add(1)); // No safepoint between def and use of derived1
    /// System.gc();
    /// Pointer oop2 = ObjectAccess.objectToTrackedPointer(obj);
    /// assert oop.equal(oop2);
    /// assert derived2.equal(oop2.add(7)); // JIT: ok, Native Image: build failure
    /// ```
    ///
    /// This is an optional operation that will throw an [UnsatisfiedLinkError] if
    /// not supported.
    ///
    /// @see #objectToUntrackedPointer(Object)
    @SuppressWarnings("unused")
    @Operation(opcode = Opcode.OBJECT_TO_TRACKED)
    public static native Pointer objectToTrackedPointer(Object val);

    /**
     * Same as {@link #objectToTrackedPointer(Object)} but with {@link Word} return type.
     */
    @SuppressWarnings("unused")
    @Operation(opcode = Opcode.OBJECT_TO_TRACKED)
    public static native Word objectToTrackedWord(Object val);

    /// Converts the [Object] value in `val` to a [Pointer] value representing the
    /// object's current address. If the object is subsequently moved (e.g. by the garbage
    /// collector), the pointer value is not updated. As such, this method **must only be used when
    /// the address is being used for purely informational purposes** (e.g. printing out the
    /// object's address) or when the **caller guarantees the object will not be moved** (e.g. the
    /// caller is part of the garbage collector implementation or the caller guarantees not to
    /// use the value across a safepoint).
    ///
    /// Examples of expected usage:
    /// ```
    /// // Pointer value is not tracked across a garbage collection
    /// Pointer oop = ObjectAccess.objectToUntrackedPointer(obj);
    /// System.gc();
    /// Pointer oop2 = ObjectAccess.objectToUntrackedPointer(obj);
    /// if (oop.equal(oop2)) System.out.println(obj + " was moved");
    /// ```
    ///
    /// This is an optional operation that will throw an [UnsatisfiedLinkError] if
    /// not supported.
    ///
    /// @see #objectToTrackedPointer(Object)
    @SuppressWarnings("unused")
    @Operation(opcode = Opcode.OBJECT_TO_UNTRACKED)
    public static native Pointer objectToUntrackedPointer(Object val);

    /**
     * Same as {@link #objectToUntrackedPointer(Object)} but with {@link Word} return type.
     */
    @SuppressWarnings("unused")
    @Operation(opcode = Opcode.OBJECT_TO_UNTRACKED)
    public static native Word objectToUntrackedWord(Object val);

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
    public native void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity);

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
    public native void initializeLong(int offset, long val, LocationIdentity locationIdentity);

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
    public native boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Word.Opcode.CAS_POINTER)
    public native boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

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
        WRITE_POINTER,
        WRITE_POINTER_SIDE_EFFECT_FREE,
        WRITE_POINTER_VOLATILE,
        WRITE_OBJECT,
        WRITE_BARRIERED,
        CAS_POINTER,
        INITIALIZE,
        OBJECT_TO_TRACKED,
        OBJECT_TO_UNTRACKED,
        TO_OBJECT,
        TO_TYPED_OBJECT,
        TO_OBJECT_NON_NULL,
        TO_RAW_VALUE,
    }
}
