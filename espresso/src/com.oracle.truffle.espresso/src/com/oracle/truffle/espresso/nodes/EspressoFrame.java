/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * Exposes accessors to the Espresso frame e.g. operand stack, locals and current BCI.
 */
public final class EspressoFrame {

    private EspressoFrame() {
        throw EspressoError.shouldNotReachHere();
    }

    /**
     * Bytecode execution frames are built on
     * {@link com.oracle.truffle.api.frame.FrameDescriptor.Builder#addSlot(FrameSlotKind, Object, Object)
     * indexed frame slots}, and contain one slot for the BCI followed by the locals and the stack
     * ("values").
     */
    private static final int BCI_SLOT = 0;
    private static final int VALUES_START = 1;

    public static FrameDescriptor createFrameDescriptor(int locals, int stack) {
        int slotCount = locals + stack;
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder(slotCount + VALUES_START);
        int bciSlot = builder.addSlot(FrameSlotKind.Static, null, null); // BCI
        assert bciSlot == BCI_SLOT;
        int valuesStart = builder.addSlots(slotCount, FrameSlotKind.Static); // locals + stack
        assert valuesStart == VALUES_START;
        return builder.build();
    }

    // region Operand stack accessors

    public static void dup1(Frame frame, int top) {
        // value1 -> value1, value1
        copyStatic(frame, top - 1, top);
    }

    public static void dupx1(Frame frame, int top) {
        // value2, value1 -> value1, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top, top - 2);
    }

    public static void dupx2(Frame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        copyStatic(frame, top - 1, top);
        copyStatic(frame, top - 2, top - 1);
        copyStatic(frame, top - 3, top - 2);
        copyStatic(frame, top, top - 3);
    }

    public static void dup2(Frame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
    }

    public static void swapSingle(Frame frame, int top) {
        // value2, value1 -> value1, value2
        swapStatic(frame, top);
    }

    public static void dup2x1(Frame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top, top - 3);
        copyStatic(frame, top + 1, top - 2);
    }

    public static void dup2x2(Frame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        copyStatic(frame, top - 1, top + 1);
        copyStatic(frame, top - 2, top);
        copyStatic(frame, top - 3, top - 1);
        copyStatic(frame, top - 4, top - 2);
        copyStatic(frame, top, top - 4);
        copyStatic(frame, top + 1, top - 3);
    }

    private static void swapStatic(Frame frame, int top) {
        assert top >= 2;
        frame.swapStatic(top - 1, top - 2);
    }

    private static void copyStatic(Frame frame, int src, int dst) {
        assert src >= 0 && dst >= 0;
        frame.copyStatic(src, dst);
    }

    public static int popInt(Frame frame, int slot) {
        assert slot >= 0;
        int result = frame.getIntStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static StaticObject peekObject(Frame frame, int slot) {
        assert slot >= 0;
        Object result = frame.getObjectStatic(slot);
        assert result != null;
        return (StaticObject) result;
    }

    /**
     * Reads and clear the operand stack slot.
     */
    public static StaticObject popObject(Frame frame, int slot) {
        assert slot >= 0;
        // nulls-out the slot, use peekObject to read only
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        return (StaticObject) result;
    }

    public static float popFloat(Frame frame, int slot) {
        assert slot >= 0;
        float result = frame.getFloatStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static long popLong(Frame frame, int slot) {
        assert slot >= 0;
        long result = frame.getLongStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    public static double popDouble(Frame frame, int slot) {
        assert slot >= 0;
        double result = frame.getDoubleStatic(slot);
        // Avoid keeping track of popped slots in FrameStates.
        clearPrimitive(frame, slot);
        return result;
    }

    static Object popReturnAddressOrObject(Frame frame, int slot) {
        assert slot >= 0;
        Object result = frame.getObjectStatic(slot);
        clearReference(frame, slot);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    static void putReturnAddress(Frame frame, int slot, int targetBCI) {
        assert slot >= 0;
        frame.setObjectStatic(slot, ReturnAddress.create(targetBCI));
    }

    public static void putObject(Frame frame, int slot, StaticObject value) {
        assert slot >= 0;
        assert value != null;
        frame.setObjectStatic(slot, value);
    }

    public static void putInt(Frame frame, int slot, int value) {
        assert slot >= 0;
        frame.setIntStatic(slot, value);
    }

    public static void putFloat(Frame frame, int slot, float value) {
        assert slot >= 0;
        frame.setFloatStatic(slot, value);
    }

    public static void putLong(Frame frame, int slot, long value) {
        assert slot >= 0;
        frame.setLongStatic(slot + 1, value);
    }

    public static void putDouble(Frame frame, int slot, double value) {
        assert slot >= 0;
        frame.setDoubleStatic(slot + 1, value);
    }

    private static void clearReference(Frame frame, int slot) {
        assert slot >= 0;
        frame.clearObjectStatic(slot);
    }

    private static void clearPrimitive(Frame frame, int slot) {
        assert slot >= 0;
        frame.clearPrimitiveStatic(slot);
    }

    public static void clear(Frame frame, int slot) {
        assert slot >= 0;
        frame.clearStatic(slot);
    }

    // endregion Operand stack accessors

    // region Local accessors

    public static void clearLocal(Frame frame, int localSlot) {
        assert localSlot >= 0;
        clear(frame, VALUES_START + localSlot);
    }

    public static void setLocalObject(Frame frame, int localSlot, StaticObject value) {
        assert localSlot >= 0;
        assert value != null;
        frame.setObjectStatic(VALUES_START + localSlot, value);
    }

    static void setLocalObjectOrReturnAddress(Frame frame, int localSlot, Object value) {
        assert value instanceof StaticObject || value instanceof ReturnAddress;
        frame.setObjectStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalInt(Frame frame, int localSlot, int value) {
        assert localSlot >= 0;
        frame.setIntStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalFloat(Frame frame, int localSlot, float value) {
        assert localSlot >= 0;
        frame.setFloatStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalLong(Frame frame, int localSlot, long value) {
        assert localSlot >= 0;
        frame.setLongStatic(VALUES_START + localSlot, value);
    }

    public static void setLocalDouble(Frame frame, int localSlot, double value) {
        assert localSlot >= 0;
        frame.setDoubleStatic(VALUES_START + localSlot, value);
    }

    public static int getLocalInt(Frame frame, int localSlot) {
        assert localSlot >= 0;
        return frame.getIntStatic(VALUES_START + localSlot);
    }

    public static StaticObject getLocalObject(Frame frame, int localSlot) {
        assert localSlot >= 0;
        Object result = frame.getObjectStatic(VALUES_START + localSlot);
        assert result != null;
        return (StaticObject) result;
    }

    static int getLocalReturnAddress(Frame frame, int localSlot) {
        Object result = frame.getObjectStatic(VALUES_START + localSlot);
        assert result != null;
        return ((ReturnAddress) result).getBci();
    }

    public static float getLocalFloat(Frame frame, int localSlot) {
        assert localSlot >= 0;
        return frame.getFloatStatic(VALUES_START + localSlot);
    }

    public static long getLocalLong(Frame frame, int localSlot) {
        assert localSlot >= 0;
        return frame.getLongStatic(VALUES_START + localSlot);
    }

    public static double getLocalDouble(Frame frame, int localSlot) {
        assert localSlot >= 0;
        return frame.getDoubleStatic(VALUES_START + localSlot);
    }

    // endregion Local accessors

    static void setBCI(Frame frame, int bci) {
        frame.setIntStatic(BCI_SLOT, bci);
    }

    static int getBCI(Frame frame) {
        return frame.getIntStatic(BCI_SLOT);
    }

    public static int startingStackOffset(int maxLocals) {
        return VALUES_START + maxLocals;
    }

    public static StaticObject peekReceiver(VirtualFrame frame, int top, Method m) {
        assert !m.isStatic();
        int skipSlots = Signatures.slotsForParameters(m.getParsedSignature());
        int slot = top - skipSlots - 1;
        assert slot >= 0;
        StaticObject result = peekObject(frame, slot);
        assert result != null;
        return result;
    }

    @ExplodeLoop
    public static Object[] popArguments(VirtualFrame frame, int top, boolean hasReceiver, final Symbol<Type>[] signature) {
        int argCount = Signatures.parameterCount(signature);

        int extraParam = hasReceiver ? 1 : 0;
        final Object[] args = new Object[argCount + extraParam];

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);
        CompilerAsserts.partialEvaluationConstant(hasReceiver);

        int argAt = top - 1;
        for (int i = argCount - 1; i >= 0; --i) {
            Symbol<Type> argType = Signatures.parameterType(signature, i);
            // @formatter:off
            switch (argType.byteAt(0)) {
                case 'Z' : args[i + extraParam] = (popInt(frame, argAt) != 0);  break;
                case 'B' : args[i + extraParam] = (byte) popInt(frame, argAt);  break;
                case 'S' : args[i + extraParam] = (short) popInt(frame, argAt); break;
                case 'C' : args[i + extraParam] = (char) popInt(frame, argAt);  break;
                case 'I' : args[i + extraParam] = popInt(frame, argAt);         break;
                case 'F' : args[i + extraParam] = popFloat(frame, argAt);       break;
                case 'J' : args[i + extraParam] = popLong(frame, argAt);   --argAt; break;
                case 'D' : args[i + extraParam] = popDouble(frame, argAt); --argAt; break;
                case '[' : // fall through
                case 'L' : args[i + extraParam] = popObject(frame, argAt);      break;
                default  :
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            --argAt;

        }
        if (hasReceiver) {
            args[0] = popObject(frame, argAt);
        }
        return args;
    }

    // Effort to prevent double copies. Erases sub-word primitive types.
    @ExplodeLoop
    public static Object[] popBasicArgumentsWithArray(VirtualFrame frame, int top, final Symbol<Type>[] signature, boolean hasReceiver, Object[] args) {
        // Use basic types
        CompilerAsserts.partialEvaluationConstant(Signatures.parameterCount(signature));
        CompilerAsserts.partialEvaluationConstant(signature);
        int extraParam = hasReceiver ? 1 : 0;
        int argAt = top - 1;
        for (int i = Signatures.parameterCount(signature) - 1; i >= 0; --i) {
            Symbol<Type> argType = Signatures.parameterType(signature, i);
            // @formatter:off
            switch (argType.byteAt(0)) {
                case 'Z' : // fall through
                case 'B' : // fall through
                case 'S' : // fall through
                case 'C' : // fall through
                case 'I' : args[i + extraParam] = popInt(frame, argAt);    break;
                case 'F' : args[i + extraParam] = popFloat(frame, argAt);  break;
                case 'J' : args[i + extraParam] = popLong(frame, argAt);   --argAt; break;
                case 'D' : args[i + extraParam] = popDouble(frame, argAt); --argAt; break;
                case '[' : // fall through
                case 'L' : args[i + extraParam] = popObject(frame, argAt); break;
                default  :
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
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
     * @param kind kind to push
     */
    public static int putKind(VirtualFrame frame, int top, Object value, JavaKind kind) {
        assert top >= 0;
        // @formatter:off
        switch (kind) {
            case Boolean : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case Byte    : putInt(frame, top, (byte) value);              break;
            case Short   : putInt(frame, top, (short) value);             break;
            case Char    : putInt(frame, top, (char) value);              break;
            case Int     : putInt(frame, top, (int) value);               break;
            case Float   : putFloat(frame, top, (float) value);           break;
            case Long    : putLong(frame, top, (long) value);             break;
            case Double  : putDouble(frame, top, (double) value);         break;
            case Object  : putObject(frame, top, (StaticObject) value);   break;
            case Void    : /* ignore */                                   break;
            default      :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        return kind.getSlotCount();
    }

    /**
     * Peeks at a value in the operand stack, without clearing it.
     *
     * Returns the peeked value.
     *
     * @param kind kind to push
     */
    public static Object peekKind(VirtualFrame frame, int top, JavaKind kind) {
        assert top >= 0;
        // @formatter:off
        switch (kind) {
            case Boolean : return frame.getIntStatic(top) != 0;
            case Byte    : return (byte) frame.getIntStatic(top);
            case Short   : return (short) frame.getIntStatic(top);
            case Char    : return (char) frame.getIntStatic(top);
            case Int     : return frame.getIntStatic(top);
            case Float   : return frame.getFloatStatic(top);
            case Long    : return frame.getLongStatic(top);
            case Double  : return frame.getDoubleStatic(top);
            case Object  : return frame.getObjectStatic(top);
            case Void    : /* ignore */
            default      :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    /**
     * Puts a value in the operand stack. This method follows the JVM spec, where sub-word types (<
     * int) are always treated as int.
     *
     * Returns the number of used slots.
     *
     * @param value value to push
     * @param type type to push
     */
    public static int putType(VirtualFrame frame, int top, Object value, Symbol<Type> type) {
        assert top >= 0;
        // @formatter:off
        switch (type.byteAt(0)) {
            case 'Z' : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case 'B' : putInt(frame, top, (byte) value);              break;
            case 'S' : putInt(frame, top, (short) value);             break;
            case 'C' : putInt(frame, top, (char) value);              break;
            case 'I' : putInt(frame, top, (int) value);               break;
            case 'F' : putFloat(frame, top, (float) value);           break;
            case 'J' : putLong(frame, top, (long) value);             break;
            case 'D' : putDouble(frame, top, (double) value);         break;
            case '[' : // fall through
            case 'L' : putObject(frame, top, (StaticObject) value);   break;
            case 'V' : /* ignore */                                   break;
            default      :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        return Types.slotCount(type);
    }
}
