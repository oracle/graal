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

import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;

import jdk.vm.ci.meta.JavaKind;

/**
 * Exposes accessors to the interpreter frame e.g. operand stack, locals and current BCI.
 */
public final class InterpreterFrameUtil {

    private InterpreterFrameUtil() {
        throw VMError.shouldNotReachHere("private constructor");
    }

    public static InterpreterFrame allocate(int maxLocals, int maxStackSize, Object... arguments) {
        return InterpreterFrame.create(maxLocals + maxStackSize, arguments);
    }

    // region Operand stack accessors

    public static void dup1(InterpreterFrame frame, long top) {
        // value1 -> value1, value1
        copyStatic(frame, top, -1, 0);
    }

    public static void dupx1(InterpreterFrame frame, long top) {
        // value2, value1 -> value1, value2, value1
        copyStatic(frame, top, -1, 0);
        copyStatic(frame, top, -2, -1);
        copyStatic(frame, top, 0, -2);
    }

    public static void dupx2(InterpreterFrame frame, long top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        copyStatic(frame, top, -1, 0);
        copyStatic(frame, top, -2, -1);
        copyStatic(frame, top, -3, -2);
        copyStatic(frame, top, 0, -3);
    }

    public static void dup2(InterpreterFrame frame, long top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        copyStatic(frame, top, -2, 0);
        copyStatic(frame, top, -1, 1);
    }

    public static void swapSingle(InterpreterFrame frame, long top) {
        // value2, value1 -> value1, value2
        swapStatic(frame, top);
    }

    public static void dup2x1(InterpreterFrame frame, long top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        copyStatic(frame, top, -2, 0);
        copyStatic(frame, top, -1, 1);
        copyStatic(frame, top, -3, -1);
        copyStatic(frame, top, 0, -3);
        copyStatic(frame, top, 1, -2);
    }

    public static void dup2x2(InterpreterFrame frame, long top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        copyStatic(frame, top, -1, 1);
        copyStatic(frame, top, -2, 0);
        copyStatic(frame, top, -3, -1);
        copyStatic(frame, top, -4, -2);
        copyStatic(frame, top, 0, -4);
        copyStatic(frame, top, 1, -3);
    }

    private static void swapStatic(InterpreterFrame frame, long top) {
        frame.swapStatic(top, -1, top, -2);
    }

    private static void copyStatic(InterpreterFrame frame, long slot, long srcOffset, long dstOffset) {
        frame.copyStatic(slot, srcOffset, slot, dstOffset);
    }

    /*
     * Match HotSpot's stack convention for primitive slots: popping does not clear the slot,
     * avoiding stores in threaded handlers. Reference slots must still be cleared because the GC
     * scans every non-null entry of the frame's {@code Object[]} references, unlike HotSpot's oop
     * maps, which can distinguish dead stack references.
     */
    public static int popInt(InterpreterFrame frame, long slot) {
        return frame.getIntStatic(slot);
    }

    public static int popInt(InterpreterFrame frame, long slot, long slotOffset) {
        return frame.getIntStatic(slot, slotOffset);
    }

    public static Object peekObject(InterpreterFrame frame, long slot) {
        Object result = frame.getObjectStatic(slot);
        return result;
    }

    public static Object peekObject(InterpreterFrame frame, long slot, long slotOffset) {
        Object result = frame.getObjectStatic(slot, slotOffset);
        return result;
    }

    public static long peekPrimitive(InterpreterFrame frame, long slot) {
        return frame.getLongStatic(slot);
    }

    public static long peekPrimitive(InterpreterFrame frame, long slot, long slotOffset) {
        return frame.getLongStatic(slot, slotOffset);
    }

    public static Object popObject(InterpreterFrame frame, long slot) {
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        assert !(result instanceof ReturnAddress);
        return result;
    }

    public static Object popObject(InterpreterFrame frame, long slot, long slotOffset) {
        Object result = frame.getObjectStatic(slot, slotOffset);
        clearReference(frame, slot, slotOffset);
        assert !(result instanceof ReturnAddress);
        return result;
    }

    public static float popFloat(InterpreterFrame frame, long slot) {
        return frame.getFloatStatic(slot);
    }

    public static float popFloat(InterpreterFrame frame, long slot, long slotOffset) {
        return frame.getFloatStatic(slot, slotOffset);
    }

    public static long popLong(InterpreterFrame frame, long slot) {
        return frame.getLongStatic(slot);
    }

    public static long popLong(InterpreterFrame frame, long slot, long slotOffset) {
        return frame.getLongStatic(slot, slotOffset);
    }

    public static double popDouble(InterpreterFrame frame, long slot) {
        return frame.getDoubleStatic(slot);
    }

    public static double popDouble(InterpreterFrame frame, long slot, long slotOffset) {
        return frame.getDoubleStatic(slot, slotOffset);
    }

    static Object popReturnAddressOrObject(InterpreterFrame frame, long slot) {
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        return result;
    }

    static Object popReturnAddressOrObject(InterpreterFrame frame, long slot, long slotOffset) {
        Object result = frame.getObjectStatic(slot, slotOffset);
        clearReference(frame, slot, slotOffset);
        return result;
    }

    static void putReturnAddress(InterpreterFrame frame, long slot, int targetBCI) {
        frame.setObjectStatic(slot, ReturnAddress.create(targetBCI));
    }

    static void putReturnAddress(InterpreterFrame frame, long slot, long slotOffset, int targetBCI) {
        frame.setObjectStatic(slot, slotOffset, ReturnAddress.create(targetBCI));
    }

    public static void putObject(InterpreterFrame frame, long slot, Object value) {
        frame.setObjectStatic(slot, value);
    }

    public static void putObject(InterpreterFrame frame, long slot, long slotOffset, Object value) {
        frame.setObjectStatic(slot, slotOffset, value);
    }

    public static void putInt(InterpreterFrame frame, long slot, int value) {
        frame.setIntStatic(slot, value);
    }

    public static void putInt(InterpreterFrame frame, long slot, long slotOffset, int value) {
        frame.setIntStatic(slot, slotOffset, value);
    }

    public static void putFloat(InterpreterFrame frame, long slot, float value) {
        frame.setFloatStatic(slot, value);
    }

    public static void putFloat(InterpreterFrame frame, long slot, long slotOffset, float value) {
        frame.setFloatStatic(slot, slotOffset, value);
    }

    public static void putLong(InterpreterFrame frame, long slot, long value) {
        frame.setLongStatic(slot + 1, value);
    }

    public static void putLong(InterpreterFrame frame, long slot, long slotOffset, long value) {
        frame.setLongStatic(slot, slotOffset + 1, value);
    }

    public static void putDouble(InterpreterFrame frame, long slot, double value) {
        frame.setDoubleStatic(slot + 1, value);
    }

    public static void putDouble(InterpreterFrame frame, long slot, long slotOffset, double value) {
        frame.setDoubleStatic(slot, slotOffset + 1, value);
    }

    public static void clearReference(InterpreterFrame frame, long slot) {
        frame.clearObjectStatic(slot);
    }

    public static void clearReference(InterpreterFrame frame, long slot, long slotOffset) {
        frame.clearObjectStatic(slot, slotOffset);
    }

    public static void clear(InterpreterFrame frame, long slot) {
        frame.clearStatic(slot);
    }

    public static void clear(InterpreterFrame frame, long slot, long slotOffset) {
        frame.clearStatic(slot, slotOffset);
    }

    // endregion Operand stack accessors

    // region Local accessors

    public static void clearLocal(InterpreterFrame frame, int localSlot) {
        clear(frame, localSlot);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLocalObject(InterpreterFrame frame, int localSlot, Object value) {
        assert !(value instanceof ReturnAddress);
        frame.setObjectStatic(localSlot, value);
    }

    static void setLocalObjectOrReturnAddress(InterpreterFrame frame, int localSlot, Object value) {
        frame.setObjectStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLocalInt(InterpreterFrame frame, int localSlot, int value) {
        frame.setIntStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLocalFloat(InterpreterFrame frame, int localSlot, float value) {
        frame.setFloatStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLocalLong(InterpreterFrame frame, int localSlot, long value) {
        frame.setLongStatic(localSlot, value);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void setLocalDouble(InterpreterFrame frame, int localSlot, double value) {
        frame.setDoubleStatic(localSlot, value);
    }

    public static int getLocalInt(InterpreterFrame frame, int localSlot) {
        return frame.getIntStatic(localSlot);
    }

    public static Object getLocalObject(InterpreterFrame frame, int localSlot) {
        Object result = frame.getObjectStatic(localSlot);
        return result;
    }

    public static Object getThis(InterpreterFrame frame) {
        return getLocalObject(frame, 0);
    }

    static int getLocalReturnAddress(InterpreterFrame frame, int localSlot) {
        Object result = frame.getObjectStatic(localSlot);
        assert result != null;
        return ((ReturnAddress) result).bci();
    }

    public static float getLocalFloat(InterpreterFrame frame, int localSlot) {
        return frame.getFloatStatic(localSlot);
    }

    public static long getLocalLong(InterpreterFrame frame, int localSlot) {
        return frame.getLongStatic(localSlot);
    }

    public static double getLocalDouble(InterpreterFrame frame, int localSlot) {
        return frame.getDoubleStatic(localSlot);
    }

    // endregion Local accessors

    public static int startingStackOffset(int maxLocals) {
        return maxLocals;
    }

    @NeverInline("Keep argument array allocation and filling out of bytecode-handler stubs")
    public static Object[] popArguments(InterpreterFrame frame, long top, boolean hasReceiver, InterpreterUnresolvedSignature signature) {
        int argCount = signature.getParameterCount(false);

        int extraParam = hasReceiver ? 1 : 0;
        final Object[] args = new Object[argCount + extraParam];

        long argAt = top - 1;
        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind argKind = signature.getParameterKind(i);
            // @formatter:off
            switch (argKind) {
                case Boolean: args[i + extraParam] = (popInt(frame, argAt) != 0);      break;
                case Byte:    args[i + extraParam] = (byte) popInt(frame, argAt);      break;
                case Short:   args[i + extraParam] = (short) popInt(frame, argAt);     break;
                case Char:    args[i + extraParam] = (char) popInt(frame, argAt);      break;
                case Int:     args[i + extraParam] = popInt(frame, argAt);             break;
                case Float:   args[i + extraParam] = popFloat(frame, argAt);           break;
                case Long:    args[i + extraParam] = popLong(frame, argAt);   --argAt; break;
                case Double:  args[i + extraParam] = popDouble(frame, argAt); --argAt; break;
                case Object:  args[i + extraParam] = popObject(frame, argAt);          break;
                default:
                    throw VMError.shouldNotReachHere("implement me: " + argKind);

            }
            // @formatter:on
            --argAt;
        }
        if (hasReceiver) {
            args[0] = popObject(frame, argAt);
        }
        return args;
    }

    /**
     * Puts a value in the operand stack. This method follows the JVM spec, where sub-word types (<
     * int) are always treated as int.
     *
     * Returns the number of used slots.
     *
     * @param value value to push
     * @param returnKind kind to push
     */
    public static int putKind(InterpreterFrame frame, long top, Object value, JavaKind returnKind) {
        // @formatter:off
        switch (returnKind) {
            case Boolean : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case Byte    : putInt(frame, top, (byte) value);              break;
            case Short   : putInt(frame, top, (short) value);             break;
            case Char    : putInt(frame, top, (char) value);              break;
            case Int     : putInt(frame, top, (int) value);               break;
            case Float   : putFloat(frame, top, (float) value);           break;
            case Long    : putLong(frame, top, (long) value);             break;
            case Double  : putDouble(frame, top, (double) value);         break;
            case Object  : putObject(frame, top, value);                  break;
            case Void    : /* ignore */                                   break;
            default      :
                throw VMError.shouldNotReachHereAtRuntime();
        }
        // @formatter:on
        return returnKind.getSlotCount();
    }
}
