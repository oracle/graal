/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.shared.Uninterruptible;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;

/**
 * Holds interpreter state that is shared across outlined bytecode handlers without full
 * expansion.
 */
final class InterpreterState {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    final byte[] code;
    final InterpreterFrame frame;
    final long[] primitives;
    final Object[] references;
    final InterpreterResolvedJavaMethod method;
    final Object[] cachedEntries;
    final byte[] constantPoolTags;
    final int[] constantPoolEntries;
    final MethodProfile methodProfile;
    final boolean forceStayInInterpreter;
    int debuggerEventFlags;
    int opcode;
    final int indent;

    InterpreterState(byte[] code, InterpreterFrame frame, InterpreterResolvedJavaMethod method, MethodProfile methodProfile, boolean forceStayInInterpreter, int debuggerEventFlags, int indent) {
        this.code = code;
        this.frame = frame;
        this.primitives = frame.getPrimitives();
        this.references = frame.getReferences();
        this.method = method;
        InterpreterConstantPool constantPool = method.getConstantPool();
        this.cachedEntries = constantPool.uncheckedCachedEntries();
        this.constantPoolTags = constantPool.uncheckedTags();
        this.constantPoolEntries = constantPool.uncheckedEntries();
        this.methodProfile = methodProfile;
        this.forceStayInInterpreter = forceStayInInterpreter;
        this.debuggerEventFlags = debuggerEventFlags;
        this.indent = indent;
        this.opcode = -1;
    }

    int getIntStatic(long slot) {
        return getIntStatic(slot, 0);
    }

    int getIntStatic(long slot, long slotOffset) {
        return (int) getPrimitiveStatic(slot, slotOffset);
    }

    Object getObjectStatic(long slot) {
        return getObjectStatic(slot, 0);
    }

    Object getObjectStatic(long slot, long slotOffset) {
        return getReferenceStatic(slot, slotOffset);
    }

    float getFloatStatic(long slot) {
        return getFloatStatic(slot, 0);
    }

    float getFloatStatic(long slot, long slotOffset) {
        return Float.intBitsToFloat((int) getPrimitiveStatic(slot, slotOffset));
    }

    long getLongStatic(long slot) {
        return getLongStatic(slot, 0);
    }

    long getLongStatic(long slot, long slotOffset) {
        return getPrimitiveStatic(slot, slotOffset);
    }

    double getDoubleStatic(long slot) {
        return getDoubleStatic(slot, 0);
    }

    double getDoubleStatic(long slot, long slotOffset) {
        return Double.longBitsToDouble(getPrimitiveStatic(slot, slotOffset));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setObjectStatic(long slot, Object value) {
        setObjectStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setObjectStatic(long slot, long slotOffset, Object value) {
        setReferenceStatic(slot, slotOffset, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setIntStatic(long slot, int value) {
        setIntStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setIntStatic(long slot, long slotOffset, int value) {
        setPrimitiveStatic(slot, slotOffset, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setFloatStatic(long slot, float value) {
        setFloatStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setFloatStatic(long slot, long slotOffset, float value) {
        setPrimitiveStatic(slot, slotOffset, Float.floatToRawIntBits(value));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLongStatic(long slot, long value) {
        setLongStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLongStatic(long slot, long slotOffset, long value) {
        setPrimitiveStatic(slot, slotOffset, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setDoubleStatic(long slot, double value) {
        setDoubleStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setDoubleStatic(long slot, long slotOffset, double value) {
        setPrimitiveStatic(slot, slotOffset, Double.doubleToRawLongBits(value));
    }

    void clearReference(long slot, long slotOffset) {
        setReferenceStatic(slot, slotOffset, null);
    }

    void clear(long slot) {
        clear(slot, 0);
    }

    void clear(long slot, long slotOffset) {
        setReferenceStatic(slot, slotOffset, null);
        setPrimitiveStatic(slot, slotOffset, 0);
    }

    void swapStatic(long src, long srcOffset, long dst, long dstOffset) {
        long primitive = getPrimitiveStatic(src, srcOffset);
        setPrimitiveStatic(src, srcOffset, getPrimitiveStatic(dst, dstOffset));
        setPrimitiveStatic(dst, dstOffset, primitive);

        Object reference = getReferenceStatic(src, srcOffset);
        setReferenceStatic(src, srcOffset, getReferenceStatic(dst, dstOffset));
        setReferenceStatic(dst, dstOffset, reference);
    }

    void copyStatic(long src, long srcOffset, long dst, long dstOffset) {
        setPrimitiveStatic(dst, dstOffset, getPrimitiveStatic(src, srcOffset));
        setReferenceStatic(dst, dstOffset, getReferenceStatic(src, srcOffset));
    }

    int popInt(long slot, long slotOffset) {
        return getIntStatic(slot, slotOffset);
    }

    Object peekObject(long slot, long slotOffset) {
        return getObjectStatic(slot, slotOffset);
    }

    long peekPrimitive(long slot, long slotOffset) {
        return getLongStatic(slot, slotOffset);
    }

    Object popObject(long slot, long slotOffset) {
        Object result = getObjectStatic(slot, slotOffset);
        clearReference(slot, slotOffset);
        return result;
    }

    float popFloat(long slot, long slotOffset) {
        return getFloatStatic(slot, slotOffset);
    }

    long popLong(long slot, long slotOffset) {
        return getLongStatic(slot, slotOffset);
    }

    double popDouble(long slot, long slotOffset) {
        return getDoubleStatic(slot, slotOffset);
    }

    void putReturnAddress(long slot, int targetBCI) {
        setObjectStatic(slot, ReturnAddress.create(targetBCI));
    }

    void putObject(long slot, Object value) {
        setObjectStatic(slot, value);
    }

    void putObject(long slot, long slotOffset, Object value) {
        setObjectStatic(slot, slotOffset, value);
    }

    void putInt(long slot, int value) {
        setIntStatic(slot, value);
    }

    void putInt(long slot, long slotOffset, int value) {
        setIntStatic(slot, slotOffset, value);
    }

    void putFloat(long slot, float value) {
        setFloatStatic(slot, value);
    }

    void putFloat(long slot, long slotOffset, float value) {
        setFloatStatic(slot, slotOffset, value);
    }

    void putLong(long slot, long value) {
        setLongStatic(slot + 1, value);
    }

    void putLong(long slot, long slotOffset, long value) {
        setLongStatic(slot, slotOffset + 1, value);
    }

    void putDouble(long slot, double value) {
        setDoubleStatic(slot + 1, value);
    }

    void putDouble(long slot, long slotOffset, double value) {
        setDoubleStatic(slot, slotOffset + 1, value);
    }

    int getLocalInt(int localSlot) {
        return getIntStatic(localSlot);
    }

    Object getLocalObject(int localSlot) {
        return getObjectStatic(localSlot);
    }

    int getLocalReturnAddress(int localSlot) {
        Object result = getObjectStatic(localSlot);
        assert result != null;
        return ((ReturnAddress) result).bci();
    }

    float getLocalFloat(int localSlot) {
        return getFloatStatic(localSlot);
    }

    long getLocalLong(int localSlot) {
        return getLongStatic(localSlot);
    }

    double getLocalDouble(int localSlot) {
        return getDoubleStatic(localSlot);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLocalObject(int localSlot, Object value) {
        assert !(value instanceof ReturnAddress);
        setObjectStatic(localSlot, value);
    }

    void setLocalObjectOrReturnAddress(int localSlot, Object value) {
        setObjectStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLocalInt(int localSlot, int value) {
        setIntStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void incrementLocalInt(int localSlot, int increment) {
        long offset = primitiveOffset(localSlot, 0);
        UNSAFE.putInt(primitives, offset, UNSAFE.getInt(primitives, offset) + increment);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLocalFloat(int localSlot, float value) {
        setFloatStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLocalLong(int localSlot, long value) {
        setLongStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLocalDouble(int localSlot, double value) {
        setDoubleStatic(localSlot, value);
    }

    private long getPrimitiveStatic(long slot, long slotOffset) {
        return UNSAFE.getLong(primitives, primitiveOffset(slot, slotOffset));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setPrimitiveStatic(long slot, long slotOffset, long value) {
        UNSAFE.putLong(primitives, primitiveOffset(slot, slotOffset), value);
    }

    private Object getReferenceStatic(long slot, long slotOffset) {
        return UNSAFE.getReference(references, referenceOffset(slot, slotOffset));
    }

    Object uncheckedCachedEntryAt(long cpi) {
        return UNSAFE.getReference(cachedEntries, referenceOffset(cpi, 0));
    }

    byte uncheckedTagValueAt(long cpi) {
        return UNSAFE.getByte(constantPoolTags, byteOffset(cpi));
    }

    int uncheckedIntAt(long cpi) {
        Object entry = uncheckedCachedEntryAt(cpi);
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Int;
            return primitiveConstant.asInt();
        }
        return UNSAFE.getInt(constantPoolEntries, intOffset(cpi));
    }

    float uncheckedFloatAt(long cpi) {
        Object entry = uncheckedCachedEntryAt(cpi);
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Float;
            return primitiveConstant.asFloat();
        }
        return Float.intBitsToFloat(UNSAFE.getInt(constantPoolEntries, intOffset(cpi)));
    }

    long uncheckedLongAt(long cpi) {
        Object entry = uncheckedCachedEntryAt(cpi);
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Long;
            return primitiveConstant.asLong();
        }
        long hiBytes = UNSAFE.getInt(constantPoolEntries, intOffset(cpi));
        long loBytes = UNSAFE.getInt(constantPoolEntries, intOffset(cpi + 1));
        return (hiBytes << 32) | (loBytes & 0xFFFFFFFFL);
    }

    double uncheckedDoubleAt(long cpi) {
        Object entry = uncheckedCachedEntryAt(cpi);
        assert entry == null || entry instanceof PrimitiveConstant;
        if (entry instanceof PrimitiveConstant primitiveConstant) {
            assert primitiveConstant.getJavaKind() == JavaKind.Double;
            return primitiveConstant.asDouble();
        }
        long hiBytes = UNSAFE.getInt(constantPoolEntries, intOffset(cpi));
        long loBytes = UNSAFE.getInt(constantPoolEntries, intOffset(cpi + 1));
        return Double.longBitsToDouble((hiBytes << 32) | (loBytes & 0xFFFFFFFFL));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setReferenceStatic(long slot, long slotOffset, Object value) {
        UNSAFE.putReference(references, referenceOffset(slot, slotOffset), value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long primitiveOffset(long slot, long slotOffset) {
        return Unsafe.ARRAY_LONG_BASE_OFFSET + ((slot + slotOffset) * Unsafe.ARRAY_LONG_INDEX_SCALE);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long referenceOffset(long slot, long slotOffset) {
        return Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((slot + slotOffset) * Unsafe.ARRAY_OBJECT_INDEX_SCALE);
    }

    private static long byteOffset(long index) {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET + index * Unsafe.ARRAY_BYTE_INDEX_SCALE;
    }

    private static long intOffset(long index) {
        return Unsafe.ARRAY_INT_BASE_OFFSET + index * Unsafe.ARRAY_INT_INDEX_SCALE;
    }

    public long readCPI1(long bci) {
        /*
         * Keep the unsigned one-byte CPI behind one opaque boundary. Otherwise lowering can
         * create separate zero- and sign-extended CPI intervals, increasing register pressure and
         * potentially causing stack spills in bytecode handlers.
         */
        return GraalDirectives.opaque((long) BytecodeStream.uncheckedReadCPI1(code, bci));
    }

    public long readCPI2(long bci) {
        /*
         * Keep the unsigned two-byte CPI behind one opaque boundary. Otherwise lowering can
         * create separate zero- and sign-extended CPI intervals, increasing register pressure and
         * potentially causing stack spills in bytecode handlers.
         */
        return GraalDirectives.opaque((long) BytecodeStream.uncheckedReadCPI2(code, bci));
    }
}
