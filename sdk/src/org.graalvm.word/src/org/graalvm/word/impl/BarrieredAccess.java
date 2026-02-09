/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

/**
 * Medium-level memory access for Objects. Similarly to the readXxx and writeXxx methods defined for
 * {@link Pointer} and {@link ObjectAccess}, these methods access the memory without any null
 * checks. However, these methods use read- or write barriers. In addition, readXxxVolatile variants
 * also adhere to volatile semantics. When the VM uses compressed pointers, then readObject and
 * writeObject methods access compressed pointers.
 * <p>
 * Like {@link Word}, operations in this class not supported by the current execution environment
 * throws {@link UnsatisfiedLinkError}.
 */
public final class BarrieredAccess {

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native char readChar(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native short readShort(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native int readInt(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native long readLong(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native char readChar(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native short readShort(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native int readInt(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native long readLong(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, int offset, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, WordBase offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, WordBase offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, WordBase offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, WordBase offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, WordBase offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, WordBase offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, WordBase offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, int offset, Object val, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native char readChar(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native short readShort(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native int readInt(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native long readLong(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native byte readByte(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native char readChar(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native short readShort(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native int readInt(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native long readLong(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native float readFloat(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native double readDouble(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native <T extends WordBase> T readWord(Object object, int offset);

    /**
     * Reads the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED)
    public static native Object readObject(Object object, int offset);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, WordBase offset, char val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, WordBase offset, short val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, WordBase offset, int val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, WordBase offset, long val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, WordBase offset, float val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, WordBase offset, double val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, WordBase offset, Object val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeByte(Object object, int offset, byte val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeChar(Object object, int offset, char val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeShort(Object object, int offset, short val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeInt(Object object, int offset, int val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeLong(Object object, int offset, long val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeFloat(Object object, int offset, float val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeDouble(Object object, int offset, double val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeWord(Object object, int offset, WordBase val);

    /**
     * Writes the memory at address {@code (object + offset)}. The offset is in bytes.
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Word.Operation(opcode = Word.Opcode.WRITE_BARRIERED)
    public static native void writeObject(Object object, int offset, Object val);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native byte readByteVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native char readCharVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native short readShortVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native int readIntVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native long readLongVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native float readFloatVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native double readDoubleVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native <T extends WordBase> T readWordVolatile(Object object, WordBase offset);

    /**
     * Reads the memory at address {@code (object + offset)} in accordance with the volatile
     * semantics. The offset is in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param object the base object for the memory access
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Word.Operation(opcode = Word.Opcode.READ_BARRIERED_VOLATILE)
    public static native Object readObjectVolatile(Object object, WordBase offset);
}
