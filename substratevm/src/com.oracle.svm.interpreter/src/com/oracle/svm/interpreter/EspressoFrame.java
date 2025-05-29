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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;

import jdk.vm.ci.meta.JavaKind;

/**
 * Exposes accessors to the Espresso frame e.g. operand stack, locals and current BCI.
 */
public final class EspressoFrame {

    private EspressoFrame() {
        throw VMError.shouldNotReachHere("private constructor");
    }

    public static InterpreterFrame allocate(int maxLocals, int maxStackSize, Object... arguments) {
        return InterpreterFrame.create(maxLocals + maxStackSize, arguments);
    }

    // region Operand stack accessors

    public static void dup1(InterpreterFrame frame, int top) {
        // value1 -> value1, value1
        copyStatic(frame, top - 1, top);
    }

    public static void dupx1(InterpreterFrame frame, int top) {
        // value2, value1 -> value1, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top, top - 2);
    }

    public static void dupx2(InterpreterFrame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top - 3, top - 2);
        copyStatic(frame, top, top - 3);
    }

    public static void dup2(InterpreterFrame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
    }

    public static void swapSingle(InterpreterFrame frame, int top) {
        // value2, value1 -> value1, value2
        swapStatic(frame, top);
    }

    public static void dup2x1(InterpreterFrame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top, top - 3);
        copyStatic(frame, top + 1, top - 2);
    }

    public static void dup2x2(InterpreterFrame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top - 4, top - 2);
        copyStatic(frame, top, top - 4);
        copyStatic(frame, top + 1, top - 3);
    }

    private static void swapStatic(InterpreterFrame frame, int top) {
        frame.swapStatic(top - 1, top - 2);
    }

    private static void copyStatic(InterpreterFrame frame, int src, int dst) {
        frame.copyStatic(src, dst);
    }

    public static int popInt(InterpreterFrame frame, int slot) {
        int result = frame.getIntStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static Object peekObject(InterpreterFrame frame, int slot) {
        Object result = frame.getObjectStatic(slot);
        return result;
    }

    public static long peekPrimitive(InterpreterFrame frame, int slot) {
        return frame.getLongStatic(slot);
    }

    /**
     * Reads and clear the operand stack slot.
     */
    public static Object popObject(InterpreterFrame frame, int slot) {
        // nulls-out the slot, use peekObject to read only
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        assert !(result instanceof ReturnAddress);
        return result;
    }

    public static float popFloat(InterpreterFrame frame, int slot) {
        float result = frame.getFloatStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static long popLong(InterpreterFrame frame, int slot) {
        long result = frame.getLongStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static double popDouble(InterpreterFrame frame, int slot) {
        double result = frame.getDoubleStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    static Object popReturnAddressOrObject(InterpreterFrame frame, int slot) {
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        return result;
    }

    static void putReturnAddress(InterpreterFrame frame, int slot, int targetBCI) {
        frame.setObjectStatic(slot, ReturnAddress.create(targetBCI));
    }

    public static void putObject(InterpreterFrame frame, int slot, Object value) {
        frame.setObjectStatic(slot, value);
    }

    public static void putInt(InterpreterFrame frame, int slot, int value) {
        frame.setIntStatic(slot, value);
    }

    public static void putFloat(InterpreterFrame frame, int slot, float value) {
        frame.setFloatStatic(slot, value);
    }

    public static void putLong(InterpreterFrame frame, int slot, long value) {
        frame.setLongStatic(slot + 1, value);
    }

    public static void putDouble(InterpreterFrame frame, int slot, double value) {
        frame.setDoubleStatic(slot + 1, value);
    }

    private static void clearReference(InterpreterFrame frame, int slot) {
        frame.clearObjectStatic(slot);
    }

    private static void clearPrimitive(InterpreterFrame frame, int slot) {
        frame.clearPrimitiveStatic(slot);
    }

    public static void clear(InterpreterFrame frame, int slot) {
        frame.clearStatic(slot);
    }

    // endregion Operand stack accessors

    // region Local accessors

    public static void clearLocal(InterpreterFrame frame, int localSlot) {
        clear(frame, localSlot);
    }

    public static void setLocalObject(InterpreterFrame frame, int localSlot, Object value) {
        assert !(value instanceof ReturnAddress);
        frame.setObjectStatic(localSlot, value);
    }

    static void setLocalObjectOrReturnAddress(InterpreterFrame frame, int localSlot, Object value) {
        frame.setObjectStatic(localSlot, value);
    }

    public static void setLocalInt(InterpreterFrame frame, int localSlot, int value) {
        frame.setIntStatic(localSlot, value);
    }

    public static void setLocalFloat(InterpreterFrame frame, int localSlot, float value) {
        frame.setFloatStatic(localSlot, value);
    }

    public static void setLocalLong(InterpreterFrame frame, int localSlot, long value) {
        frame.setLongStatic(localSlot, value);
    }

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

    public static Object[] popArguments(InterpreterFrame frame, int top, boolean hasReceiver, InterpreterUnresolvedSignature signature) {
        int argCount = signature.getParameterCount(false);

        int extraParam = hasReceiver ? 1 : 0;
        final Object[] args = new Object[argCount + extraParam];

        int argAt = top - 1;
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
    public static int putKind(InterpreterFrame frame, int top, Object value, JavaKind returnKind) {
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
