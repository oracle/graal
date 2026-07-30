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
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.core.monitor.MonitorSupport;

import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodeFrame;

/// Stores JVM locals and operand stack slots for one interpreted frame.
///
/// Each logical JVM slot has parallel primitive and reference storage:
///
/// * [#primitives] stores primitive values as raw `long` bits.
/// * [#references] stores object references.
///
/// The `Static` suffix on methods such as [#getObjectStatic(int)] and
/// [#setIntStatic(int, int)] does **not** refer to Java static fields or static methods. It means
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

    int getIntStatic(int slot) {
        return (int) getPrimitiveStatic(slot);
    }

    Object getObjectStatic(int slot) {
        return getReferenceStatic(slot);
    }

    float getFloatStatic(int slot) {
        return Float.intBitsToFloat((int) getPrimitiveStatic(slot));
    }

    long getLongStatic(int slot) {
        return getPrimitiveStatic(slot);
    }

    double getDoubleStatic(int slot) {
        return Double.longBitsToDouble(getPrimitiveStatic(slot));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setObjectStatic(int slot, Object value) {
        setReferenceStatic(slot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setIntStatic(int slot, int value) {
        setPrimitiveStatic(slot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setFloatStatic(int slot, float value) {
        setPrimitiveStatic(slot, Float.floatToRawIntBits(value));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setLongStatic(int slot, long value) {
        setPrimitiveStatic(slot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setDoubleStatic(int slot, double value) {
        setPrimitiveStatic(slot, Double.doubleToRawLongBits(value));
    }

    void clearObjectStatic(int slot) {
        setReferenceStatic(slot, null);
    }

    void clearPrimitiveStatic(int slot) {
        setPrimitiveStatic(slot, 0);
    }

    void clearStatic(int slot) {
        clearObjectStatic(slot);
        clearPrimitiveStatic(slot);
    }

    void swapStatic(int src, int dst) {
        long tmp = getPrimitiveStatic(src);
        setPrimitiveStatic(src, getPrimitiveStatic(dst));
        setPrimitiveStatic(dst, tmp);

        Object otmp = getReferenceStatic(src);
        setReferenceStatic(src, getReferenceStatic(dst));
        setReferenceStatic(dst, otmp);
    }

    void copyStatic(int src, int dst) {
        setPrimitiveStatic(dst, getPrimitiveStatic(src));
        setReferenceStatic(dst, getReferenceStatic(src));
    }

    private long getPrimitiveStatic(int slot) {
        return UNSAFE.getLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + ((long) slot * Unsafe.ARRAY_LONG_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setPrimitiveStatic(int slot, long value) {
        UNSAFE.putLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + ((long) slot * Unsafe.ARRAY_LONG_INDEX_SCALE), value);
    }

    private Object getReferenceStatic(int slot) {
        return UNSAFE.getReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((long) slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setReferenceStatic(int slot, Object value) {
        UNSAFE.putReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + ((long) slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE), value);
    }

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
