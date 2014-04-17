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

import static com.oracle.graal.graph.UnsafeAccess.*;

import java.lang.annotation.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.nodes.HeapAccess.BarrierType;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;

public abstract class Word implements Signed, Unsigned, Pointer {

    /**
     * Links a method to a canonical operation represented by an {@link Opcode} val.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Operation {

        Class<? extends ValueNode> node() default ValueNode.class;

        boolean rightOperandIsInt() default false;

        Opcode opcode() default Opcode.NODE_CLASS;

        Condition condition() default Condition.EQ;
    }

    /**
     * The canonical {@link Operation} represented by a method in the {@link Word} class.
     */
    // @formatter:off
     public enum Opcode {
         NODE_CLASS,
         COMPARISON,
         NOT,
         READ,
         READ_HEAP,
         WRITE,
         INITIALIZE,
         ZERO,
         FROM_UNSIGNED,
         FROM_SIGNED,
         FROM_OBJECT,
         FROM_ARRAY,
         TO_OBJECT,
         TO_RAW_VALUE,
    }
     // @formatter:on

    /*
     * Outside users should use the different signed() and unsigned() methods to ensure proper
     * expansion of 32-bit values on 64-bit systems.
     */
    private static Word box(long val) {
        return HostedWord.boxLong(val);
    }

    protected abstract long unbox();

    private static Word intParam(int val) {
        return box(val);
    }

    /**
     * The constant 0, i.e., the word with no bits set. There is no difference between a signed and
     * unsigned zero.
     * 
     * @return the constant 0.
     */
    @Operation(opcode = Opcode.ZERO)
    public static Word zero() {
        return box(0L);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as an unsigned
     * 64-bit value (in contrast to the semantics of a Java long).
     * 
     * @param val a 64 bit unsigned value
     * @return the value cast to Word
     */
    @Operation(opcode = Opcode.FROM_UNSIGNED)
    public static Word unsigned(long val) {
        return box(val);
    }

    /**
     * Unsafe conversion from a Java long value to a {@link PointerBase pointer}. The parameter is
     * treated as an unsigned 64-bit value (in contrast to the semantics of a Java long).
     * 
     * @param val a 64 bit unsigned value
     * @return the value cast to PointerBase
     */
    @Operation(opcode = Opcode.FROM_UNSIGNED)
    @SuppressWarnings("unchecked")
    public static <T extends PointerBase> T pointer(long val) {
        return (T) box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as an unsigned
     * 32-bit value (in contrast to the semantics of a Java int).
     * 
     * @param val a 32 bit unsigned value
     * @return the value cast to Word
     */
    @Operation(opcode = Opcode.FROM_UNSIGNED)
    public static Word unsigned(int val) {
        return box(val & 0xffffffffL);
    }

    /**
     * Unsafe conversion from a Java long value to a Word. The parameter is treated as a signed
     * 64-bit value (unchanged semantics of a Java long).
     * 
     * @param val a 64 bit signed value
     * @return the value cast to Word
     */
    @Operation(opcode = Opcode.FROM_SIGNED)
    public static Word signed(long val) {
        return box(val);
    }

    /**
     * Unsafe conversion from a Java int value to a Word. The parameter is treated as a signed
     * 32-bit value (unchanged semantics of a Java int).
     * 
     * @param val a 32 bit signed value
     * @return the value cast to Word
     */
    @Operation(opcode = Opcode.FROM_SIGNED)
    public static Word signed(int val) {
        return box(val);
    }

    @Override
    @Operation(opcode = Opcode.TO_RAW_VALUE)
    public long rawValue() {
        return unbox();
    }

    @Operation(opcode = Opcode.FROM_OBJECT)
    public static native Pointer fromObject(Object val);

    @Operation(opcode = Opcode.FROM_ARRAY)
    public static native Pointer fromArray(Object oop, Object location);

    @Override
    @Operation(opcode = Opcode.TO_OBJECT)
    public native Object toObject();

    @Override
    @Operation(node = IntegerAddNode.class)
    public Word add(Signed val) {
        return add((Word) val);
    }

    @Override
    @Operation(node = IntegerAddNode.class)
    public Word add(Unsigned val) {
        return add((Word) val);
    }

    @Override
    @Operation(node = IntegerAddNode.class)
    public Word add(int val) {
        return add(intParam(val));
    }

    @Operation(node = IntegerAddNode.class)
    public Word add(Word val) {
        return box(unbox() + val.unbox());
    }

    @Override
    @Operation(node = IntegerSubNode.class)
    public Word subtract(Signed val) {
        return subtract((Word) val);
    }

    @Override
    @Operation(node = IntegerSubNode.class)
    public Word subtract(Unsigned val) {
        return subtract((Word) val);
    }

    @Override
    @Operation(node = IntegerSubNode.class)
    public Word subtract(int val) {
        return subtract(intParam(val));
    }

    @Operation(node = IntegerSubNode.class)
    public Word subtract(Word val) {
        return box(unbox() - val.unbox());
    }

    @Override
    @Operation(node = IntegerMulNode.class)
    public Word multiply(Signed val) {
        return multiply((Word) val);
    }

    @Override
    @Operation(node = IntegerMulNode.class)
    public Word multiply(Unsigned val) {
        return multiply((Word) val);
    }

    @Override
    @Operation(node = IntegerMulNode.class)
    public Word multiply(int val) {
        return multiply(intParam(val));
    }

    @Operation(node = IntegerMulNode.class)
    public Word multiply(Word val) {
        return box(unbox() * val.unbox());
    }

    @Override
    @Operation(node = IntegerDivNode.class)
    public Word signedDivide(Signed val) {
        return signedDivide((Word) val);
    }

    @Override
    @Operation(node = IntegerDivNode.class)
    public Word signedDivide(int val) {
        return signedDivide(intParam(val));
    }

    @Operation(node = IntegerDivNode.class)
    public Word signedDivide(Word val) {
        return box(unbox() / val.unbox());
    }

    @Override
    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(Unsigned val) {
        return unsignedDivide((Word) val);
    }

    @Override
    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(int val) {
        return signedDivide(intParam(val));
    }

    @Operation(node = UnsignedDivNode.class)
    public Word unsignedDivide(Word val) {
        return box(UnsignedMath.divide(unbox(), val.unbox()));
    }

    @Override
    @Operation(node = IntegerRemNode.class)
    public Word signedRemainder(Signed val) {
        return signedRemainder((Word) val);
    }

    @Override
    @Operation(node = IntegerRemNode.class)
    public Word signedRemainder(int val) {
        return signedRemainder(intParam(val));
    }

    @Operation(node = IntegerRemNode.class)
    public Word signedRemainder(Word val) {
        return box(unbox() % val.unbox());
    }

    @Override
    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(Unsigned val) {
        return unsignedRemainder((Word) val);
    }

    @Override
    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(int val) {
        return signedRemainder(intParam(val));
    }

    @Operation(node = UnsignedRemNode.class)
    public Word unsignedRemainder(Word val) {
        return box(UnsignedMath.remainder(unbox(), val.unbox()));
    }

    @Override
    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(Unsigned val) {
        return shiftLeft((Word) val);
    }

    @Override
    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(int val) {
        return shiftLeft(intParam(val));
    }

    @Operation(node = LeftShiftNode.class, rightOperandIsInt = true)
    public Word shiftLeft(Word val) {
        return box(unbox() << val.unbox());
    }

    @Override
    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(Unsigned val) {
        return signedShiftRight((Word) val);
    }

    @Override
    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(int val) {
        return signedShiftRight(intParam(val));
    }

    @Operation(node = RightShiftNode.class, rightOperandIsInt = true)
    public Word signedShiftRight(Word val) {
        return box(unbox() >> val.unbox());
    }

    @Override
    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(Unsigned val) {
        return unsignedShiftRight((Word) val);
    }

    @Override
    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(int val) {
        return unsignedShiftRight(intParam(val));
    }

    @Operation(node = UnsignedRightShiftNode.class, rightOperandIsInt = true)
    public Word unsignedShiftRight(Word val) {
        return box(unbox() >>> val.unbox());
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(Signed val) {
        return and((Word) val);
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(Unsigned val) {
        return and((Word) val);
    }

    @Override
    @Operation(node = AndNode.class)
    public Word and(int val) {
        return and(intParam(val));
    }

    @Operation(node = AndNode.class)
    public Word and(Word val) {
        return box(unbox() & val.unbox());
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(Signed val) {
        return or((Word) val);
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(Unsigned val) {
        return or((Word) val);
    }

    @Override
    @Operation(node = OrNode.class)
    public Word or(int val) {
        return or(intParam(val));
    }

    @Operation(node = OrNode.class)
    public Word or(Word val) {
        return box(unbox() | val.unbox());
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(Signed val) {
        return xor((Word) val);
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(Unsigned val) {
        return xor((Word) val);
    }

    @Override
    @Operation(node = XorNode.class)
    public Word xor(int val) {
        return xor(intParam(val));
    }

    @Operation(node = XorNode.class)
    public Word xor(Word val) {
        return box(unbox() ^ val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.NOT)
    public Word not() {
        return box(~unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(ComparableWord val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(Signed val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(Unsigned val) {
        return equal((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(int val) {
        return equal(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.EQ)
    public boolean equal(Word val) {
        return unbox() == val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(ComparableWord val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(Signed val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(Unsigned val) {
        return notEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(int val) {
        return notEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.NE)
    public boolean notEqual(Word val) {
        return unbox() != val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(Signed val) {
        return lessThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(int val) {
        return lessThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LT)
    public boolean lessThan(Word val) {
        return unbox() < val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(Signed val) {
        return lessOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(int val) {
        return lessOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.LE)
    public boolean lessOrEqual(Word val) {
        return unbox() <= val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(Signed val) {
        return greaterThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(int val) {
        return greaterThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GT)
    public boolean greaterThan(Word val) {
        return unbox() > val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(Signed val) {
        return greaterOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(int val) {
        return greaterOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.GE)
    public boolean greaterOrEqual(Word val) {
        return unbox() >= val.unbox();
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(Unsigned val) {
        return belowThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(int val) {
        return belowThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BT)
    public boolean belowThan(Word val) {
        return UnsignedMath.belowThan(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(Unsigned val) {
        return belowOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(int val) {
        return belowOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.BE)
    public boolean belowOrEqual(Word val) {
        return UnsignedMath.belowOrEqual(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(Unsigned val) {
        return aboveThan((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(int val) {
        return aboveThan(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AT)
    public boolean aboveThan(Word val) {
        return UnsignedMath.aboveThan(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(Unsigned val) {
        return aboveOrEqual((Word) val);
    }

    @Override
    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(int val) {
        return aboveOrEqual(intParam(val));
    }

    @Operation(opcode = Opcode.COMPARISON, condition = Condition.AE)
    public boolean aboveOrEqual(Word val) {
        return UnsignedMath.aboveOrEqual(unbox(), val.unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public byte readByte(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getByte(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public char readChar(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getChar(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public short readShort(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getShort(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public int readInt(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getInt(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public long readLong(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getLong(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public float readFloat(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getFloat(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public double readDouble(WordBase offset, LocationIdentity locationIdentity) {
        return unsafe.getDouble(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Word readWord(WordBase offset, LocationIdentity locationIdentity) {
        return box(unsafe.getAddress(add((Word) offset).unbox()));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public native Object readObject(WordBase offset, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.READ)
    public byte readByte(int offset, LocationIdentity locationIdentity) {
        return readByte(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public char readChar(int offset, LocationIdentity locationIdentity) {
        return readChar(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public short readShort(int offset, LocationIdentity locationIdentity) {
        return readShort(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public int readInt(int offset, LocationIdentity locationIdentity) {
        return readInt(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public long readLong(int offset, LocationIdentity locationIdentity) {
        return readLong(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public float readFloat(int offset, LocationIdentity locationIdentity) {
        return readFloat(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public double readDouble(int offset, LocationIdentity locationIdentity) {
        return readDouble(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Word readWord(int offset, LocationIdentity locationIdentity) {
        return readWord(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Object readObject(int offset, LocationIdentity locationIdentity) {
        return readObject(signed(offset), locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeByte(WordBase offset, byte val, LocationIdentity locationIdentity) {
        unsafe.putByte(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeChar(WordBase offset, char val, LocationIdentity locationIdentity) {
        unsafe.putChar(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeShort(WordBase offset, short val, LocationIdentity locationIdentity) {
        unsafe.putShort(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeInt(WordBase offset, int val, LocationIdentity locationIdentity) {
        unsafe.putInt(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        unsafe.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeFloat(WordBase offset, float val, LocationIdentity locationIdentity) {
        unsafe.putFloat(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeDouble(WordBase offset, double val, LocationIdentity locationIdentity) {
        unsafe.putDouble(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeWord(WordBase offset, WordBase val, LocationIdentity locationIdentity) {
        unsafe.putAddress(add((Word) offset).unbox(), ((Word) val).unbox());
    }

    @Override
    @Operation(opcode = Opcode.INITIALIZE)
    public void initializeLong(WordBase offset, long val, LocationIdentity locationIdentity) {
        unsafe.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public native void writeObject(WordBase offset, Object val, LocationIdentity locationIdentity);

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeByte(int offset, byte val, LocationIdentity locationIdentity) {
        writeByte(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeChar(int offset, char val, LocationIdentity locationIdentity) {
        writeChar(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeShort(int offset, short val, LocationIdentity locationIdentity) {
        writeShort(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeInt(int offset, int val, LocationIdentity locationIdentity) {
        writeInt(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeLong(int offset, long val, LocationIdentity locationIdentity) {
        writeLong(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeFloat(int offset, float val, LocationIdentity locationIdentity) {
        writeFloat(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeDouble(int offset, double val, LocationIdentity locationIdentity) {
        writeDouble(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeWord(int offset, WordBase val, LocationIdentity locationIdentity) {
        writeWord(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.INITIALIZE)
    public void initializeLong(int offset, long val, LocationIdentity locationIdentity) {
        initializeLong(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeObject(int offset, Object val, LocationIdentity locationIdentity) {
        writeObject(signed(offset), val, locationIdentity);
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public byte readByte(WordBase offset) {
        return unsafe.getByte(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public char readChar(WordBase offset) {
        return unsafe.getChar(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public short readShort(WordBase offset) {
        return unsafe.getShort(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public int readInt(WordBase offset) {
        return unsafe.getInt(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public long readLong(WordBase offset) {
        return unsafe.getLong(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public float readFloat(WordBase offset) {
        return unsafe.getFloat(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public double readDouble(WordBase offset) {
        return unsafe.getDouble(add((Word) offset).unbox());
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Word readWord(WordBase offset) {
        return box(unsafe.getAddress(add((Word) offset).unbox()));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public native Object readObject(WordBase offset);

    @Operation(opcode = Opcode.READ_HEAP)
    public native Object readObject(WordBase offset, BarrierType barrierType);

    @Override
    @Operation(opcode = Opcode.READ)
    public byte readByte(int offset) {
        return readByte(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public char readChar(int offset) {
        return readChar(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public short readShort(int offset) {
        return readShort(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public int readInt(int offset) {
        return readInt(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public long readLong(int offset) {
        return readLong(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public float readFloat(int offset) {
        return readFloat(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public double readDouble(int offset) {
        return readDouble(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Word readWord(int offset) {
        return readWord(signed(offset));
    }

    @Override
    @Operation(opcode = Opcode.READ)
    public Object readObject(int offset) {
        return readObject(signed(offset));
    }

    @Operation(opcode = Opcode.READ_HEAP)
    public Object readObject(int offset, BarrierType barrierType) {
        return readObject(signed(offset), barrierType);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeByte(WordBase offset, byte val) {
        unsafe.putByte(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeChar(WordBase offset, char val) {
        unsafe.putChar(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeShort(WordBase offset, short val) {
        unsafe.putShort(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeInt(WordBase offset, int val) {
        unsafe.putInt(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeLong(WordBase offset, long val) {
        unsafe.putLong(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeFloat(WordBase offset, float val) {
        unsafe.putFloat(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeDouble(WordBase offset, double val) {
        unsafe.putDouble(add((Word) offset).unbox(), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeWord(WordBase offset, WordBase val) {
        unsafe.putAddress(add((Word) offset).unbox(), ((Word) val).unbox());
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public native void writeObject(WordBase offset, Object val);

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeByte(int offset, byte val) {
        writeByte(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeChar(int offset, char val) {
        writeChar(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeShort(int offset, short val) {
        writeShort(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeInt(int offset, int val) {
        writeInt(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeLong(int offset, long val) {
        writeLong(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeFloat(int offset, float val) {
        writeFloat(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeDouble(int offset, double val) {
        writeDouble(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeWord(int offset, WordBase val) {
        writeWord(signed(offset), val);
    }

    @Override
    @Operation(opcode = Opcode.WRITE)
    public void writeObject(int offset, Object val) {
        writeObject(signed(offset), val);
    }

    @Override
    public final boolean equals(Object obj) {
        throw GraalInternalError.shouldNotReachHere("equals must not be called on words");
    }

    @Override
    public final int hashCode() {
        throw GraalInternalError.shouldNotReachHere("hashCode must not be called on words");
    }

    @Override
    public String toString() {
        throw GraalInternalError.shouldNotReachHere("toString must not be called on words");
    }
}

final class HostedWord extends Word {

    private static final int SMALL_FROM = -1;
    private static final int SMALL_TO = 100;

    private static final HostedWord[] smallCache = new HostedWord[SMALL_TO - SMALL_FROM + 1];

    static {
        for (int i = SMALL_FROM; i <= SMALL_TO; i++) {
            smallCache[i - SMALL_FROM] = new HostedWord(i);
        }
    }

    private final long rawValue;

    private HostedWord(long rawValue) {
        this.rawValue = rawValue;
    }

    protected static Word boxLong(long val) {
        if (val >= SMALL_FROM && val <= SMALL_TO) {
            return smallCache[(int) val - SMALL_FROM];
        }
        return new HostedWord(val);
    }

    @Override
    protected long unbox() {
        return rawValue;
    }

    @Override
    public String toString() {
        return "Word<" + rawValue + ">";
    }
}
