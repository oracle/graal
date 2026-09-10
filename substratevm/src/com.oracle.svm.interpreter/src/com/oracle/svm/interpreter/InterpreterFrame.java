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

import java.nio.ByteOrder;
import java.util.Arrays;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.interpreter.debug.DebuggerEvents;
import com.oracle.svm.interpreter.debug.EventKind;
import com.oracle.svm.interpreter.debug.SteppingControl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.JavaKind;

/// Stores JVM locals, operand stack slots, and execution state for one interpreted frame.
///
/// Each logical JVM slot has parallel primitive and reference storage:
///
/// * [#primitives] stores primitive values as raw `long` bits.
/// * [#references] stores object references.
///
/// The typed accessors directly access the underlying primitive or reference array. The slot must
/// be within the frame bounds established from the verified method metadata because these raw
/// accesses do not perform array bounds checks. Accessors that omit the offset operate on the
/// specified slot; accessors with an offset operate on `slot + constantOffset`.
/// Classes in this package use these accessors directly. Code outside this package uses the public
/// semantic accessors for locals, operand-stack slots, locks, and stack-walking state.
/// The profiling and debugger state is installed when interpretation starts because frames
/// reconstructed for deoptimization exist before their interpreter execution begins.
public final class InterpreterFrame {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private final long[] primitives;
    private final Object[] references;

    final InterpreterResolvedJavaMethod method;
    final byte[] code;
    MethodProfile methodProfile;
    boolean forceStayInInterpreter;
    DebugState debugState;

    private final Object[] arguments;
    private Object[] locks;
    private int lockCount;
    private InterpreterFrameSourceInfo syntheticStackTraceCallerInfo;
    private boolean hiddenFromStackWalking;

    private static final Object[] EMPTY = new Object[0];

    // region Frame lifecycle and arguments

    private InterpreterFrame(InterpreterResolvedJavaMethod method, Object[] arguments) {
        int slotCount = method.getMaxLocals() + method.getMaxStackSize();
        this.method = method;
        this.code = method.getInterpretedCode();
        this.primitives = new long[slotCount];
        this.references = new Object[slotCount];
        this.arguments = arguments;
        this.lockCount = 0;
        this.locks = EMPTY;
        this.hiddenFromStackWalking = false;
    }

    /**
     * Creates an interpreter frame for a method.
     *
     * @param method the interpreted method
     * @param arguments the method arguments
     * @return the new interpreter frame
     */
    public static InterpreterFrame create(InterpreterResolvedJavaMethod method, Object... arguments) {
        return new InterpreterFrame(method, arguments);
    }

    /**
     * Initializes the local slots from this frame's invocation arguments.
     */
    public void initializeLocals() {
        boolean hasReceiver = !method.isStatic();
        int receiverSlot = hasReceiver ? 1 : 0;
        int curSlot = 0;
        if (hasReceiver) {
            Object receiver = uncheckedArgumentAt(0);
            InterpreterUtil.assertion(receiver != null, "null receiver in init arguments !");
            setLocalObject(curSlot, receiver);
            curSlot += JavaKind.Object.getSlotCount();
        }

        InterpreterUnresolvedSignature methodSignature = method.getSignature();
        for (int i = 0; i < methodSignature.getParameterCount(false); ++i) {
            JavaKind argType = methodSignature.getParameterKind(i);
            Object argument = uncheckedArgumentAt(i + receiverSlot);
            // @formatter:off
            switch (argType) {
                case Boolean: setLocalInt(curSlot, ((boolean) argument) ? 1 : 0);    break;
                case Byte:    setLocalInt(curSlot, (byte) argument);                 break;
                case Short:   setLocalInt(curSlot, (short) argument);                break;
                case Char:    setLocalInt(curSlot, (char) argument);                 break;
                case Int:     setLocalInt(curSlot, (int) argument);                  break;
                case Float:   setLocalFloat(curSlot, (float) argument);              break;
                case Long:    setLocalLong(curSlot, (long) argument);     ++curSlot; break;
                case Double:  setLocalDouble(curSlot, (double) argument); ++curSlot; break;
                case Object:  setLocalObject(curSlot, argument);                     break;
                default:
                    throw VMError.shouldNotReachHereAtRuntime();
            }
            // @formatter:on
            ++curSlot;
        }
    }

    Object[] getArguments() {
        return arguments;
    }

    Object uncheckedArgumentAt(long index) {
        return UNSAFE.getReference(arguments, Unsafe.ARRAY_OBJECT_BASE_OFFSET + (index * Unsafe.ARRAY_OBJECT_INDEX_SCALE));
    }

    // endregion Frame lifecycle and arguments

    // region Debugger state

    void installState(MethodProfile newMethodProfile, boolean newForceStayInInterpreter, int debuggerEventFlags, int indent) {
        this.methodProfile = newMethodProfile;
        this.forceStayInInterpreter = newForceStayInInterpreter;
        this.debugState = new DebugState(debuggerEventFlags, indent);
    }

    /** Holds debugger and tracing state installed when interpretation starts. */
    static final class DebugState {
        private SteppingControl steppingControl;
        private boolean stepEventDisabled;
        int debuggerEventFlags;
        int opcode;
        final int indent;
        /**
         * BCI reported while delivering a debugger event. Threaded dispatch delivers the event
         * while the enclosing bytecode handler still carries the preceding BCI, so stack walking
         * uses this value as a temporary override. The value is
         * {@link BytecodeFrame#UNKNOWN_BCI} outside the event callback.
         */
        private int debuggerEventBCI;

        DebugState(int debuggerEventFlags, int indent) {
            this.debuggerEventFlags = debuggerEventFlags;
            this.indent = indent;
            this.opcode = -1;
            this.debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
        }

        void publishDebuggerEventBCI(int bci) {
            assert debuggerEventBCI == BytecodeFrame.UNKNOWN_BCI;
            debuggerEventBCI = bci;
        }

        void clearDebuggerEventBCI() {
            debuggerEventBCI = BytecodeFrame.UNKNOWN_BCI;
        }

        @NeverInline("Keep debugger stepping setup out of bytecode-handler stubs")
        boolean beforeInvoke() {
            steppingControl = null;
            stepEventDisabled = false;

            boolean preferStayInInterpreter = false;
            Thread currentThread = Thread.currentThread();
            if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                // Disable stepping for inner frames, except for step into, where we must force
                // interpreter execution.
                steppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
                if (steppingControl != null) {
                    steppingControl.pushFrame();
                    if (!steppingControl.isActiveAtCurrentFrameDepth()) {
                        DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, false);
                        stepEventDisabled = true;
                    }
                    if (steppingControl.getDepth() == SteppingControl.STEP_INTO) {
                        // For now force the callee to stay in interpreter.
                        preferStayInInterpreter = true;
                    }
                }
            }
            return preferStayInInterpreter;
        }

        @NeverInline("Keep debugger stepping cleanup out of bytecode-handler stubs")
        void afterInvoke() {
            Thread currentThread = Thread.currentThread();
            SteppingControl newSteppingControl = DebuggerEvents.singleton().getSteppingControl(currentThread);
            if (newSteppingControl != null) {
                if (DebuggerEvents.singleton().isEventEnabled(currentThread, EventKind.SINGLE_STEP)) {
                    newSteppingControl.popFrame();
                } else if (steppingControl == newSteppingControl && stepEventDisabled) {
                    // Re-enable stepping events that could have been disabled by step outer/out
                    // into inner frames.
                    DebuggerEvents.singleton().setEventEnabled(currentThread, EventKind.SINGLE_STEP, true);
                    newSteppingControl.popFrame();
                }
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    int getDebuggerEventBCI() {
        DebugState state = debugState;
        return state != null ? state.debuggerEventBCI : BytecodeFrame.UNKNOWN_BCI;
    }

    // endregion Debugger state

    // region Raw slot accessors

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    long getPrimitive(long slot, long constantOffset) {
        return UNSAFE.getLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + (constantOffset * Unsafe.ARRAY_LONG_INDEX_SCALE) + (slot * Unsafe.ARRAY_LONG_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setPrimitive(long slot, long constantOffset, long value) {
        UNSAFE.putLong(primitives, Unsafe.ARRAY_LONG_BASE_OFFSET + (constantOffset * Unsafe.ARRAY_LONG_INDEX_SCALE) + (slot * Unsafe.ARRAY_LONG_INDEX_SCALE), value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    Object getReference(long slot, long constantOffset) {
        return UNSAFE.getReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + (constantOffset * Unsafe.ARRAY_OBJECT_INDEX_SCALE) + (slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void setReference(long slot, long constantOffset, Object value) {
        UNSAFE.putReference(references, Unsafe.ARRAY_OBJECT_BASE_OFFSET + (constantOffset * Unsafe.ARRAY_OBJECT_INDEX_SCALE) + (slot * Unsafe.ARRAY_OBJECT_INDEX_SCALE), value);
    }

    // endregion Raw slot accessors

    // region Local accessors

    /**
     * Returns the receiver in local slot zero.
     *
     * @return the receiver
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object getThis() {
        return getReference(0, 0);
    }

    /**
     * Returns the int in a local slot.
     *
     * @param localSlot the local slot
     * @return the int in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public int getLocalInt(long localSlot) {
        return (int) getPrimitive(localSlot, 0);
    }

    /**
     * Stores an int in a local slot.
     *
     * @param localSlot the local slot
     * @param value the int to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLocalInt(long localSlot, int value) {
        setPrimitive(localSlot, 0, value);
    }

    /**
     * Increments the int in a local slot.
     *
     * @param localSlot the local slot
     * @param increment the value to add
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void incrementLocalInt(long localSlot, int increment) {
        /*
         * IINC operates on a 32-bit int. Access the low 32 bits of the long-backed primitive slot
         * directly, accounting for their target-dependent position within the slot. The folded
         * offset avoids loading and storing the unused upper half.
         */
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET + (localSlot * Unsafe.ARRAY_LONG_INDEX_SCALE) + intOffsetWithinLong();
        UNSAFE.putInt(primitives, offset, UNSAFE.getInt(primitives, offset) + increment);
    }

    @Fold
    static int intOffsetWithinLong() {
        return SubstrateTarget.getArchitecture().getByteOrder() == ByteOrder.BIG_ENDIAN ? Long.BYTES - Integer.BYTES : 0;
    }

    /**
     * Returns the float in a local slot.
     *
     * @param localSlot the local slot
     * @return the float in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public float getLocalFloat(long localSlot) {
        return Float.intBitsToFloat((int) getPrimitive(localSlot, 0));
    }

    /**
     * Stores a float in a local slot.
     *
     * @param localSlot the local slot
     * @param value the float to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLocalFloat(long localSlot, float value) {
        setPrimitive(localSlot, 0, Float.floatToRawIntBits(value));
    }

    /**
     * Returns the long in a local slot.
     *
     * @param localSlot the local slot
     * @return the long in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getLocalLong(long localSlot) {
        return getPrimitive(localSlot, 0);
    }

    /**
     * Stores a long in a local slot.
     *
     * @param localSlot the local slot
     * @param value the long to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLocalLong(long localSlot, long value) {
        setPrimitive(localSlot, 0, value);
    }

    /**
     * Returns the double in a local slot.
     *
     * @param localSlot the local slot
     * @return the double in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public double getLocalDouble(long localSlot) {
        return Double.longBitsToDouble(getPrimitive(localSlot, 0));
    }

    /**
     * Stores a double in a local slot.
     *
     * @param localSlot the local slot
     * @param value the double to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLocalDouble(long localSlot, double value) {
        setPrimitive(localSlot, 0, Double.doubleToRawLongBits(value));
    }

    /**
     * Returns the object in a local slot.
     *
     * @param localSlot the local slot
     * @return the object in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object getLocalObject(long localSlot) {
        return getReference(localSlot, 0);
    }

    int getLocalReturnAddress(long localSlot) {
        Object result = getReference(localSlot, 0);
        assert result != null;
        return ((ReturnAddress) result).bci();
    }

    /**
     * Stores an object in a local slot.
     *
     * @param localSlot the local slot
     * @param value the object to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setLocalObject(long localSlot, Object value) {
        setReference(localSlot, 0, value);
    }

    // endregion Local accessors

    // region Operand stack accessors

    /**
     * Returns the first operand stack slot.
     *
     * @return the number of local slots in this frame
     */
    public int getOperandStackStart() {
        return method.getMaxLocals();
    }

    /**
     * Stores an int in an operand stack slot.
     *
     * @param slot the operand stack slot
     * @param value the int to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackInt(long slot, int value) {
        setPrimitive(slot, 0, value);
    }

    /**
     * Stores a float in an operand stack slot.
     *
     * @param slot the operand stack slot
     * @param value the float to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackFloat(long slot, float value) {
        setPrimitive(slot, 0, Float.floatToRawIntBits(value));
    }

    /**
     * Stores a long in two consecutive operand stack slots.
     * <p>
     * The value is written to {@code slot + 1}; {@code slot} is the first slot occupied by the
     * category-2 value.
     *
     * @param slot the first operand stack slot
     * @param value the long to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackLong(long slot, long value) {
        setPrimitive(slot, 1, value);
    }

    /**
     * Stores a double in two consecutive operand stack slots.
     * <p>
     * The value is written to {@code slot + 1}; {@code slot} is the first slot occupied by the
     * category-2 value.
     *
     * @param slot the first operand stack slot
     * @param value the double to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackDouble(long slot, double value) {
        setPrimitive(slot, 1, Double.doubleToRawLongBits(value));
    }

    /**
     * Returns the object in an operand stack slot.
     *
     * @param slot the operand stack slot
     * @return the object in the slot
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Object getStackObject(long slot) {
        return getReference(slot, 0);
    }

    /**
     * Stores an object in an operand stack slot.
     *
     * @param slot the operand stack slot
     * @param value the object to store
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setStackObject(long slot, Object value) {
        setReference(slot, 0, value);
    }

    /**
     * Clears the primitive and reference values in an operand stack slot.
     *
     * @param slot the operand stack slot to clear
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void clearStackSlot(long slot) {
        setReference(slot, 0, null);
        setPrimitive(slot, 0, 0);
    }

    /**
     * Clears the active operand stack slots in this frame.
     *
     * @param top the exclusive upper bound of the active operand stack
     */
    public void clearOperandStack(long top) {
        long stackStart = method.getMaxLocals();
        for (long slot = top - 1; slot >= stackStart; --slot) {
            clearStackSlot(slot);
        }
    }

    // endregion Operand stack accessors

    // region Lock accessors

    /**
     * Returns the object stored in the specified lock slot.
     *
     * @param index the lock slot index
     * @return the lock object, or {@code null} if the slot is empty
     */
    public Object getLock(int index) {
        return locks[index];
    }

    @NeverInline("Keep lock-array growth out of bytecode-handler stubs")
    private void ensureLocksCapacity(int capacity) {
        int oldLength = locks.length;
        this.locks = Arrays.copyOf(locks, Math.max(capacity, (oldLength * 2) + 1));
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

    Object getSynchronizedMethodLock() {
        assert method.isSynchronized();
        return method.isStatic() ? method.getDeclaringClass().getJavaClass() : getThis();
    }

    // endregion Lock accessors

    // region Stack walking

    /**
     * Marks this frame so that stack walking omits it.
     */
    public void hideFromStackWalking() {
        hiddenFromStackWalking = true;
    }

    boolean isHiddenFromStackWalking() {
        return hiddenFromStackWalking;
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    InterpreterFrameSourceInfo getStackTraceCallerInfo() {
        return syntheticStackTraceCallerInfo;
    }

    // endregion Stack walking

}
