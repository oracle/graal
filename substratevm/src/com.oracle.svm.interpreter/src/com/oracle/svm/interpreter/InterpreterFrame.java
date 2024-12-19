/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.util.VMError;

public final class InterpreterFrame {
    private final long[] primitives;
    private final Object[] references;

    private final Object[] arguments;
    private Object[] locks;
    private int lockCount;

    private static final Object[] EMPTY = new Object[0];

    private InterpreterFrame(int slotCount, Object[] arguments) {
        this.primitives = new long[slotCount];
        this.references = new Object[slotCount];
        this.arguments = arguments;
        this.lockCount = 0;
        this.locks = EMPTY;
    }

    static InterpreterFrame create(int slotCount, Object... arguments) {
        return new InterpreterFrame(slotCount, arguments);
    }

    Object[] getArguments() {
        return arguments;
    }

    int getIntStatic(int slot) {
        return (int) primitives[slot];
    }

    Object getObjectStatic(int slot) {
        return references[slot];
    }

    float getFloatStatic(int slot) {
        return Float.intBitsToFloat((int) primitives[slot]);
    }

    long getLongStatic(int slot) {
        return primitives[slot];
    }

    double getDoubleStatic(int slot) {
        return Double.longBitsToDouble(primitives[slot]);
    }

    void setObjectStatic(int slot, Object value) {
        references[slot] = value;
    }

    void setIntStatic(int slot, int value) {
        primitives[slot] = value;
    }

    void setFloatStatic(int slot, float value) {
        primitives[slot] = Float.floatToRawIntBits(value);
    }

    void setLongStatic(int slot, long value) {
        primitives[slot] = value;
    }

    void setDoubleStatic(int slot, double value) {
        primitives[slot] = Double.doubleToRawLongBits(value);
    }

    void clearObjectStatic(int slot) {
        references[slot] = null;
    }

    void clearPrimitiveStatic(int slot) {
        primitives[slot] = 0;
    }

    void clearStatic(int slot) {
        clearObjectStatic(slot);
        clearPrimitiveStatic(slot);
    }

    void swapStatic(int src, int dst) {
        long tmp = primitives[src];
        primitives[src] = primitives[dst];
        primitives[dst] = tmp;

        Object otmp = references[src];
        references[src] = references[dst];
        references[dst] = otmp;
    }

    void copyStatic(int src, int dst) {
        primitives[dst] = primitives[src];
        references[dst] = references[src];
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

    void removeLock(Object ref) {
        assert ref != null;
        if (lockCount > 0 && locks[lockCount - 1] == ref) {
            // Fast path, balanced locks.
            locks[--lockCount] = null;
        } else {
            lockCount = -1;
            // Unbalanced locks, linear scan.
            for (int i = 0; i < locks.length; ++i) {
                if (locks[i] == ref) {
                    locks[i] = null;
                    return;
                }
            }
            throw VMError.shouldNotReachHere("lock not found in interpreter frame");
        }
    }

    Object[] getLocks() {
        return locks;
    }
}
