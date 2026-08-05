/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodeFrame;

/// Stores JVM locals and operand stack slots for one interpreted frame.
///
/// Each logical JVM slot has parallel primitive and reference storage:
///
/// * [#primitives] stores primitive values as raw `long` bits.
/// * [#references] stores object references.
///
/// The `Static` suffix on methods such as [#getObjectStatic(long)] and
/// [#setIntStatic(long, int)] does **not** refer to Java static fields or static methods. It means
/// the caller statically knows which storage kind is valid for the slot and wants direct typed
/// access to the underlying arrays. The slot must be within the frame bounds established from the
/// verified method metadata because these raw accesses do not perform array bounds checks.
/// Higher-level helpers in [InterpreterFrameUtil] provide the semantic local-variable and operand
/// stack operations built on top of these raw slot accessors.
public final class InterpreterFrame {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private final long[] primitives;
    private final Object[] references;

    private final Object[] arguments;
    private Object[] locks;
    private int lockCount;
    private InterpreterFrameSourceInfo syntheticStackTraceCallerInfo;
    private boolean hiddenFromStackWalking;
    /**
     * BCI reported while delivering a debugger event. Threaded dispatch delivers the event while
     * the enclosing bytecode handler still carries the preceding BCI, so stack walking uses this
     * value as a temporary override. The value is {@link BytecodeFrame#UNKNOWN_BCI} outside the
     * event callback.
     */
    private int debuggerEventBCI;

    private static final Object[] EMPTY = new Object[0];

    private InterpreterFrame(int slotCount, Object[] arguments) {
        this.primitives = new long[slotCount];
        this.references = new Object[slotCount];
        this.arguments = arguments;
        this.lockCount = 0;
        this.locks = EMPTY;
        this.hiddenFromStackWalking = false;
        this.debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
    }

    static InterpreterFrame create(int slotCount, Object... arguments) {
        return new InterpreterFrame(slotCount, arguments);
    }

    Object[] getArguments() {
        return arguments;
    }

    void publishDebuggerEventBCI(int bci) {
        assert debuggerEventBCI == BytecodeFrame.UNKNOWN_BCI;
        debuggerEventBCI = bci;
    }

    void clearDebuggerEventBCI() {
        debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int getDebuggerEventBCI() {
        return debuggerEventBCI;
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

    void clearObjectStatic(long slot) {
        clearObjectStatic(slot, 0);
    }

    void clearObjectStatic(long slot, long slotOffset) {
        setReferenceStatic(slot, slotOffset, null);
    }

    void clearPrimitiveStatic(long slot) {
        clearPrimitiveStatic(slot, 0);
    }

    void clearPrimitiveStatic(long slot, long slotOffset) {
        setPrimitiveStatic(slot, slotOffset, 0);
    }

    void clearStatic(long slot) {
        clearStatic(slot, 0);
    }

    void clearStatic(long slot, long slotOffset) {
        clearObjectStatic(slot, slotOffset);
        clearPrimitiveStatic(slot, slotOffset);
    }

    void swapStatic(long src, long dst) {
        swapStatic(src, 0, dst, 0);
    }

    void swapStatic(long src, long srcOffset, long dst, long dstOffset) {
        long tmp = getPrimitiveStatic(src, srcOffset);
        setPrimitiveStatic(src, srcOffset, getPrimitiveStatic(dst, dstOffset));
        setPrimitiveStatic(dst, dstOffset, tmp);

        Object otmp = getReferenceStatic(src, srcOffset);
        setReferenceStatic(src, srcOffset, getReferenceStatic(dst, dstOffset));
        setReferenceStatic(dst, dstOffset, otmp);
    }

    void copyStatic(long src, long dst) {
        copyStatic(src, 0, dst, 0);
    }

    void copyStatic(long src, long srcOffset, long dst, long dstOffset) {
        setPrimitiveStatic(dst, dstOffset, getPrimitiveStatic(src, srcOffset));
        setReferenceStatic(dst, dstOffset, getReferenceStatic(src, srcOffset));
    }

    private long getPrimitiveStatic(long slot, long slotOffset) {
        return UNSAFE.getLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + (slot * Unsafe.ARRAY_LONG_INDEX_SCALE) + (slotOffset * Unsafe.ARRAY_LONG_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setPrimitiveStatic(long slot, long value) {
        setPrimitiveStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setPrimitiveStatic(long slot, long slotOffset, long value) {
        UNSAFE.putLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + (slot * Unsafe.ARRAY_LONG_INDEX_SCALE) + (slotOffset * Unsafe.ARRAY_LONG_INDEX_SCALE), value);
    }

    private Object getReferenceStatic(long slot, long slotOffset) {
        return UNSAFE.getReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + (slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE) + (slotOffset * Unsafe.ARRAY_OBJECT_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setReferenceStatic(long slot, Object value) {
        setReferenceStatic(slot, 0, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setReferenceStatic(long slot, long slotOffset, Object value) {
        UNSAFE.putReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + (slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE) + (slotOffset * Unsafe.ARRAY_OBJECT_INDEX_SCALE), value);
    }

    @NeverInline("Keep lock-array growth out of bytecode-handler stubs")
    private void ensureLocksCapacity(int capacity) {
        int oldLength = locks.length;
        Object[] newLocks = Arrays.copyOf(locks, Math.max(capacity, (oldLength * 2) + 1));
        this.locks = newLocks;
    }

    void addLock(Object ref) {
        assert ref != null;
        assert MonitorSupport.singleton().isLockedByCurrentThread(ref);
        if (lockCount >= 0) {
            // Fast path, balanced locks.
            if (lockCount >= locks.length) {
                ensureLocksCapacity(lockCount + 1);
            }
            locks[lockCount++] = ref;
        } else {
            // Unbalanced locks, linear scan.
            for (int i = 0; i < locks.length; ++i) {
                if (locks[i] == null) {
                    locks[i] = ref;
                    return;
                }
            }
            // No free slot found.
            int oldLockCount = locks.length;
            ensureLocksCapacity(oldLockCount + 1);
            assert locks[oldLockCount] == null;
            locks[oldLockCount] = ref;
        }
    }

    /// Removes one frame-local monitor acquisition for `ref`, if this frame recorded one.
    boolean removeLock(Object ref) {
        assert ref != null;
        if (lockCount > 0 && locks[lockCount - 1] == ref) {
            // Fast path, balanced locks.
            locks[--lockCount] = null;
            return true;
        } else {
            lockCount = -1;
            // Unbalanced locks, linear scan.
            for (int i = locks.length - 1; i >= 0; --i) {
                if (locks[i] == ref) {
                    locks[i] = null;
                    return true;
                }
            }
            return false;
        }
    }

    Object[] getLocks() {
        return locks;
    }

    public Object getLock(int index) {
        return locks[index];
    }

    boolean isHiddenFromStackWalking() {
        return hiddenFromStackWalking;
    }

    public void hideFromStackWalking() {
        hiddenFromStackWalking = true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    InterpreterFrameSourceInfo getStackTraceCallerInfo() {
        return syntheticStackTraceCallerInfo;
    }

    /**
     * Sets the synthetic outer caller chain used when stack walking a deopt-resumed interpreter
     * frame.
     *
     * @param callerInfo virtual caller frames peeled out of the compiled inlining stack, or
     *            {@code null} to clear the synthetic caller chain
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackTraceCallerInfo(InterpreterFrameSourceInfo callerInfo) {
        this.syntheticStackTraceCallerInfo = callerInfo;
    }
}
