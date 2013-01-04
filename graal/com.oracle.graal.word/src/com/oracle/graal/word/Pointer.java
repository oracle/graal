/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.word;

public interface Pointer extends Unsigned {

    /**
     * Unsafe conversion of this Pointer to a Java language object. No correctness checks or type checks are performed.
     * The caller must ensure that the Pointer contains a valid Java object that can i.e., processed by the garbage
     * collector.
     *
     * @return this Pointer cast to Object.
     */
    Object toObject();

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    byte readByte(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    char readChar(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    short readShort(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    int readInt(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    long readLong(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    float readFloat(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    double readDouble(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Word readWord(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Object readObject(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    byte readByte(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    char readChar(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    short readShort(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    int readInt(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    long readLong(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    float readFloat(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    double readDouble(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Word readWord(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Object readObject(int offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    byte readFinalByte(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    char readFinalChar(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    short readFinalShort(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    int readFinalInt(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    long readFinalLong(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    float readFinalFloat(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    double readFinalDouble(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Word readFinalWord(WordBase offset);

    /**
     * Reads the constant memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The caller guarantees that the memory content is final, i.e., never changing. The compiler can therefore
     * eliminate memory accesses more aggressively. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    Object readFinalObject(WordBase offset);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeByte(WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeChar(WordBase offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeShort(WordBase offset, Short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeInt(WordBase offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeLong(WordBase offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeFloat(WordBase offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeDouble(WordBase offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeWord(WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes. <br>
     * The offset is always treated as a {@link Signed} value. However, the static type is {@link WordBase} to avoid the
     * frequent casts to of {@link Unsigned} values (where the caller knows that the highest-order bit of the unsigned
     * value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeObject(WordBase offset, Object val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeByte(int offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeChar(int offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeShort(int offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeInt(int offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeLong(int offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeFloat(int offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeDouble(int offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeWord(int offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    void writeObject(int offset, Object val);

    // Math functions that are defined in Unsigned, but known to preserve the pointer-characteristics.
    // It is therefore safe that they return a static type of Pointer instead of Unsigned.

    @Override
    Pointer add(Unsigned val);

    @Override
    Pointer add(int val);

    @Override
    Pointer subtract(Unsigned val);

    @Override
    Pointer subtract(int val);

    @Override
    Pointer and(Unsigned val);

    @Override
    Pointer and(int val);

    @Override
    Pointer or(Unsigned val);

    @Override
    Pointer or(int val);
}
