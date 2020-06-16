/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Lowest-level memory access of native C memory.
 * <p>
 * Do not use these methods to access Java objects. These methods access the raw memory without any
 * null checks, read- or write barriers. Even when the VM uses compressed pointers, then readObject
 * and writeObject methods access uncompressed pointers.
 *
 * @since 19.0
 */
public interface Pointer extends UnsignedWord, PointerBase {

    /**
     * Unsafe conversion of this Pointer to a Java language object. No correctness checks or type
     * checks are performed. The caller must ensure that the Pointer contains a valid Java object
     * that can i.e., processed by the garbage collector.
     *
     * @return this Pointer cast to Object.
     *
     * @since 19.0
     */
    Object toObject();

    /**
     * Unsafe conversion of this Pointer to a Java language object. No correctness checks or type
     * checks are performed. The caller must ensure that the Pointer contains a valid Java object
     * that can i.e., processed by the garbage collector and the Pointer does not contain 0.
     *
     * @return this Pointer cast to non-null Object.
     *
     * @since 19.0
     */
    Object toObjectNonNull();

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    byte readByte(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    char readChar(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    short readShort(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    int readInt(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    long readLong(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    float readFloat(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    double readDouble(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    <T extends WordBase> T readWord(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    Object readObject(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    byte readByte(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    char readChar(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    short readShort(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    int readInt(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    long readLong(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    float readFloat(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    double readDouble(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    <T extends WordBase> T readWord(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     *
     * @since 19.0
     */
    Object readObject(int offset, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeChar(WordBase offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeShort(WordBase offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeInt(WordBase offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Initializes the memory at address {@code (this + offset)}. Both the base address and offset
     * are in bytes. The memory must be uninitialized or zero prior to this operation.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeByte(int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeChar(int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeShort(int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeInt(int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeLong(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeFloat(int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeDouble(int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeWord(int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Initializes the memory at address {@code (this + offset)}. Both the base address and offset
     * are in bytes. The memory must be uninitialized or zero prior to this operation.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void initializeLong(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeObject(int offset, Object val, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    byte readByte(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    char readChar(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    short readShort(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    int readInt(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    long readLong(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    float readFloat(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    double readDouble(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    <T extends WordBase> T readWord(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    Object readObject(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    byte readByte(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    char readChar(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    short readShort(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    int readInt(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    long readLong(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    float readFloat(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    double readDouble(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    <T extends WordBase> T readWord(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     *
     * @since 19.0
     */
    Object readObject(int offset);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeByte(WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeChar(WordBase offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeShort(WordBase offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeInt(WordBase offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeLong(WordBase offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeFloat(WordBase offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeDouble(WordBase offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeWord(WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeObject(WordBase offset, Object val);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    int compareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    long compareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    <T extends WordBase> T compareAndSwapWord(WordBase offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    Object compareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapInt(WordBase offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapLong(WordBase offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapWord(WordBase offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapObject(WordBase offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeByte(int offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeChar(int offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeShort(int offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeInt(int offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeLong(int offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeFloat(int offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeDouble(int offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeWord(int offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     *
     * @since 19.0
     */
    void writeObject(int offset, Object val);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    int compareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    long compareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    <T extends WordBase> T compareAndSwapWord(int offset, T expectedValue, T newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return The value that was read for comparison, which is {@code expectedValue} if the
     *         exchange was performed.
     *
     * @since 19.0
     */
    Object compareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapInt(int offset, int expectedValue, int newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapLong(int offset, long expectedValue, long newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapWord(int offset, WordBase expectedValue, WordBase newValue, LocationIdentity locationIdentity);

    /**
     * In a single atomic step, compares the memory at address {@code (this + offset)} to the
     * expected value, and if equal, exchanges it for the new value. Both the base address and
     * offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param expectedValue the expected current value at the memory address
     * @param newValue the new value for the atomic exchange
     * @param locationIdentity the identity of the memory location
     * @return {@code true} if successful. False return indicates that the actual value was not
     *         equal to the expected value.
     *
     * @since 19.0
     */
    boolean logicCompareAndSwapObject(int offset, Object expectedValue, Object newValue, LocationIdentity locationIdentity);

    // Math functions that are defined in Unsigned, but known to preserve the
    // pointer-characteristics.
    // It is therefore safe that they return a static type of Pointer instead of Unsigned.

    /**
     * Returns a Pointer whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Pointer.
     * @return {@code this + val}
     *
     * @since 19.0
     */
    @Override
    Pointer add(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this + val)}.
     *
     * @param val value to be added to this Pointer.
     * @return {@code this + val}
     *
     * @since 19.0
     */
    @Override
    Pointer add(int val);

    /**
     * Returns a Pointer whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Pointer.
     * @return {@code this - val}
     *
     * @since 19.0
     */
    @Override
    Pointer subtract(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this - val)}.
     *
     * @param val value to be subtracted from this Pointer.
     * @return {@code this - val}
     *
     * @since 19.0
     */
    @Override
    Pointer subtract(int val);

    /**
     * Returns a Pointer whose value is {@code (this & val)}.
     *
     * @param val value to be AND'ed with this Pointer.
     * @return {@code this & val}
     *
     * @since 19.0
     */
    @Override
    Pointer and(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this & val)}.
     *
     * @param val value to be AND'ed with this Pointer.
     * @return {@code this & val}
     *
     * @since 19.0
     */
    @Override
    Pointer and(int val);

    /**
     * Returns a Pointer whose value is {@code (this | val)}.
     *
     * @param val value to be OR'ed with this Pointer.
     * @return {@code this | val}
     *
     * @since 19.0
     */
    @Override
    Pointer or(UnsignedWord val);

    /**
     * Returns a Pointer whose value is {@code (this | val)}.
     *
     * @param val value to be OR'ed with this Pointer.
     * @return {@code this | val}
     *
     * @since 19.0
     */
    @Override
    Pointer or(int val);
}
