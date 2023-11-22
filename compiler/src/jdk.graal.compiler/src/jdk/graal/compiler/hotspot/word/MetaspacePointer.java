/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.word;

import static jdk.graal.compiler.hotspot.word.HotSpotOperation.HotspotOpcode.FROM_POINTER;
import static jdk.graal.compiler.hotspot.word.HotSpotOperation.HotspotOpcode.IS_NULL;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.word.Word;
import jdk.graal.compiler.word.Word.Opcode;
import jdk.graal.compiler.word.Word.Operation;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

/**
 * Marker type for a metaspace pointer.
 */
public abstract class MetaspacePointer {

    @HotSpotOperation(opcode = IS_NULL)
    public abstract boolean isNull();

    @HotSpotOperation(opcode = FROM_POINTER)
    public abstract Word asWord();

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract byte readByte(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract char readChar(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract short readShort(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract int readInt(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract long readLong(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract float readFloat(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract double readDouble(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Word readWord(WordBase offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(WordBase offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract byte readByte(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract char readChar(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract short readShort(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract int readInt(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract long readLong(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract float readFloat(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract double readDouble(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Word readWord(int offset, LocationIdentity locationIdentity);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the read
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(int offset, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeChar(WordBase offset, char val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeShort(WordBase offset, short val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeInt(WordBase offset, int val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeLong(WordBase offset, long val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.INITIALIZE)
    public abstract void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeByte(int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeChar(int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeShort(int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeInt(int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeLong(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeFloat(int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeDouble(int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeWord(int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeObject(int offset, Object val, LocationIdentity locationIdentity);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract byte readByte(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract char readChar(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract short readShort(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract int readInt(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract long readLong(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract float readFloat(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract double readDouble(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Word readWord(WordBase offset);

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
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(WordBase offset);

    /**
     * Reads the memory at address {@code (this + offset)}. This access will decompress the oop if
     * the VM uses compressed oops, and it can be parameterized to allow read barriers (G1 referent
     * field).
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param barrierType the type of the read barrier to be added
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(WordBase offset, BarrierType barrierType);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract byte readByte(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract char readChar(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract short readShort(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract int readInt(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract long readLong(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract float readFloat(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract double readDouble(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Word readWord(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(int offset);

    /**
     * Reads the memory at address {@code (this + offset)}. This access will decompress the oop if
     * the VM uses compressed oops, and it can be parameterized to allow read barriers (G1 referent
     * field).
     *
     * @param offset the signed offset for the memory access
     * @param barrierType the type of the read barrier to be added
     * @return the result of the memory access
     */
    @Operation(opcode = Opcode.READ_POINTER)
    public abstract Object readObject(int offset, BarrierType barrierType);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeByte(WordBase offset, byte val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeChar(WordBase offset, char val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeShort(WordBase offset, short val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeInt(WordBase offset, int val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeLong(WordBase offset, long val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeFloat(WordBase offset, float val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeDouble(WordBase offset, double val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeWord(WordBase offset, WordBase val);

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
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeObject(WordBase offset, Object val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeByte(int offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeChar(int offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeShort(int offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeInt(int offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeLong(int offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeFloat(int offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeDouble(int offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeWord(int offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER)
    public abstract void writeObject(int offset, Object val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeByteSideEffectFree(WordBase offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeCharSideEffectFree(WordBase offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeShortSideEffectFree(WordBase offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeIntSideEffectFree(WordBase offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeLongSideEffectFree(WordBase offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeFloatSideEffectFree(WordBase offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeDoubleSideEffectFree(WordBase offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeWordSideEffectFree(WordBase offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeObjectSideEffectFree(WordBase offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeByteSideEffectFree(int offset, byte val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeCharSideEffectFree(int offset, char val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeShortSideEffectFree(int offset, short val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeIntSideEffectFree(int offset, int val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeLongSideEffectFree(int offset, long val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeFloatSideEffectFree(int offset, float val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeDoubleSideEffectFree(int offset, double val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeWordSideEffectFree(int offset, WordBase val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param locationIdentity the identity of the write
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeObjectSideEffectFree(int offset, Object val, LocationIdentity locationIdentity);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeByteSideEffectFree(WordBase offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeCharSideEffectFree(WordBase offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeShortSideEffectFree(WordBase offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeIntSideEffectFree(WordBase offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeLongSideEffectFree(WordBase offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeFloatSideEffectFree(WordBase offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeDoubleSideEffectFree(WordBase offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeWordSideEffectFree(WordBase offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     * <p>
     * The offset is always treated as a {@link SignedWord} value. However, the static type is
     * {@link WordBase} to avoid the frequent casts of {@link UnsignedWord} values (where the caller
     * knows that the highest-order bit of the unsigned value is never used).
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeObjectSideEffectFree(WordBase offset, Object val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeByteSideEffectFree(int offset, byte val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeCharSideEffectFree(int offset, char val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeShortSideEffectFree(int offset, short val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeIntSideEffectFree(int offset, int val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeLongSideEffectFree(int offset, long val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeFloatSideEffectFree(int offset, float val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeDoubleSideEffectFree(int offset, double val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeWordSideEffectFree(int offset, WordBase val);

    /**
     * Writes the memory at address {@code (this + offset)}. Both the base address and offset are in
     * bytes. The write is side-effect free, it is not a part of the JVM state and can be repeated
     * after deoptimization.
     *
     * @param offset the signed offset for the memory access
     * @param val the value to be written to memory
     */
    @Operation(opcode = Opcode.WRITE_POINTER_SIDE_EFFECT_FREE)
    public abstract void writeObjectSideEffectFree(int offset, Object val);

}
