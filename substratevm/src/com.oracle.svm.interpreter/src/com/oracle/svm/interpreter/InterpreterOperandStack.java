/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.NeverInline;

import jdk.vm.ci.meta.JavaKind;

/**
 * Provides an operand-stack overlay on an {@link InterpreterFrame}. This object defines the stack
 * operations required by the interpreter and owns the logical stack top, while primitive and
 * reference values remain stored directly in the frame supplied to each operation.
 *
 * In normal bytecode handlers, this overlay is expected to remain virtual and be expanded into its
 * {@link #top} field. Handlers therefore must not let it escape across calls that would force
 * materialization. At a frame-stack operation boundary, {@link #topForFrameStackOperation()}
 * exposes only the stack top to code that operates directly on the frame, and
 * {@link #applyFrameStackOperationDelta(int)} applies any resulting stack-slot delta. The
 * operation does not need to be outlined; the boundary prevents this overlay from being passed to
 * it and materialized.
 *
 * Frame-stack operations may create their own overlay to reuse these stack semantics. In that
 * context, remaining virtual is an optimization rather than a requirement of the abstraction.
 *
 * When a handler passes an operand to a call, it should peek the operand and pop it only after the
 * call returns. Updating {@link #top} before the call makes the derived {@code top - 1} value part
 * of the call's frame state. Keeping the canonical top across the call avoids that intermediate
 * frame-state value.
 */
final class InterpreterOperandStack {
    /** First stack slot above the operand stack. */
    private long top;

    InterpreterOperandStack(long top) {
        this.top = top;
    }

    /**
     * Returns the stack top for an operation that accesses operand-stack slots directly through
     * the frame. Passing only the returned value across this boundary keeps this operand-stack
     * overlay from being materialized. The operation does not need to be outlined.
     *
     * @return the first stack slot above the operand stack
     */
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long topForFrameStackOperation() {
        return top;
    }

    /**
     * Applies the net operand-stack size change produced by a frame-stack operation.
     *
     * @param slotDelta the number of slots added by the operation, or a negative value for slots
     *            removed by the operation
     */
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void applyFrameStackOperationDelta(int slotDelta) {
        top += slotDelta;
    }

    /**
     * Returns the net operand-stack size change since {@code initialTop}.
     *
     * @param initialTop the stack top before the operations
     */
    @AlwaysInline("Keep InterpreterOperandStack access in the caller")
    int slotDeltaFrom(long initialTop) {
        return (int) (top - initialTop);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushInt(InterpreterFrame frame, int value) {
        frame.setPrimitive(top, 0, value);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushFloat(InterpreterFrame frame, float value) {
        pushInt(frame, Float.floatToRawIntBits(value));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushLong(InterpreterFrame frame, long value) {
        frame.setStackLong(top, value);
        top += 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushDouble(InterpreterFrame frame, double value) {
        frame.setStackDouble(top, value);
        top += 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushObject(InterpreterFrame frame, Object value) {
        frame.setReference(top, 0, value);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushReturnAddress(InterpreterFrame frame, int targetBCI) {
        Object value = ReturnAddress.create(targetBCI);
        frame.setReference(top, 0, value);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int popInt(InterpreterFrame frame) {
        int value = (int) frame.getPrimitive(top, -1);
        top--;
        return value;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float popFloat(InterpreterFrame frame) {
        float value = Float.intBitsToFloat((int) frame.getPrimitive(top, -1));
        top--;
        return value;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long popLong(InterpreterFrame frame) {
        long value = frame.getPrimitive(top, -1);
        top -= 2;
        return value;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double popDouble(InterpreterFrame frame) {
        return Double.longBitsToDouble(popLong(frame));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object popObject(InterpreterFrame frame) {
        Object value = frame.getReference(top, -1);
        frame.setReference(top, -1, null);
        top--;
        return value;
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private Object popKind(InterpreterFrame frame, JavaKind kind) {
        return switch (kind) {
            case Boolean -> popInt(frame) != 0;
            case Byte -> (byte) popInt(frame);
            case Short -> (short) popInt(frame);
            case Char -> (char) popInt(frame);
            case Int -> popInt(frame);
            case Float -> popFloat(frame);
            case Long -> popLong(frame);
            case Double -> popDouble(frame);
            case Object -> popObject(frame);
            default -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
        };
    }

    @AlwaysInline("Keep invocation return stack transitions in bytecode-handler stubs")
    void pushKind(InterpreterFrame frame, Object value, JavaKind kind) {
        switch (kind) {
            case Boolean -> pushInt(frame, (boolean) value ? 1 : 0);
            case Byte -> pushInt(frame, (byte) value);
            case Short -> pushInt(frame, (short) value);
            case Char -> pushInt(frame, (char) value);
            case Int -> pushInt(frame, (int) value);
            case Float -> pushFloat(frame, (float) value);
            case Long -> pushLong(frame, (long) value);
            case Double -> pushDouble(frame, (double) value);
            case Object -> pushObject(frame, value);
            case Void -> {
            }
            default -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
        }
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    Object[] popArguments(InterpreterFrame frame, boolean hasReceiver, InterpreterUnresolvedSignature signature, Object appendix) {
        return popArguments(frame, hasReceiver, signature, appendix, appendix != null);
    }

    @AlwaysInline("Keep materialized invocation argument stack transitions together")
    Object[] popArgumentsWithAppendix(InterpreterFrame frame, boolean hasReceiver, InterpreterUnresolvedSignature signature, Object appendix) {
        return popArguments(frame, hasReceiver, signature, appendix, true);
    }

    @AlwaysInline("Keep invocation argument stack transitions in bytecode-handler stubs")
    private Object[] popArguments(InterpreterFrame frame, boolean hasReceiver, InterpreterUnresolvedSignature signature, Object appendix, boolean hasAppendix) {
        int argumentCount = signature.getParameterCount(false);
        int receiverCount = hasReceiver ? 1 : 0;
        Object[] arguments = allocateArguments(argumentCount + receiverCount);

        int lastStackArgument = argumentCount - 1;
        if (hasAppendix) {
            assert signature.getParameterKind(lastStackArgument) == JavaKind.Object;
            arguments[lastStackArgument + receiverCount] = appendix;
            lastStackArgument--;
        }
        for (int index = lastStackArgument; index >= 0; index--) {
            arguments[index + receiverCount] = popKind(frame, signature.getParameterKind(index));
        }
        if (hasReceiver) {
            arguments[0] = popObject(frame);
        }
        return arguments;
    }

    @NeverInline("Keep invocation argument array allocation out of bytecode-handler stubs")
    private static Object[] allocateArguments(int argumentCount) {
        return new Object[argumentCount];
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop1(InterpreterFrame frame) {
        pop1(frame, true);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop1(InterpreterFrame frame, boolean clear) {
        if (clear) {
            frame.setReference(top, -1, null);
        }
        top--;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame) {
        pop2(frame, true);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame, boolean clear) {
        if (clear) {
            frame.setReference(top, -1, null);
            frame.setReference(top, -2, null);
        }
        top -= 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int peekInt(InterpreterFrame frame, long offset) {
        return (int) frame.getPrimitive(top, offset);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float peekFloat(InterpreterFrame frame, long offset) {
        return Float.intBitsToFloat((int) frame.getPrimitive(top, offset));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long peekLong(InterpreterFrame frame, long offset) {
        return frame.getPrimitive(top, offset);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    double peekDouble(InterpreterFrame frame, long offset) {
        return Double.longBitsToDouble(frame.getPrimitive(top, offset));
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object peekObject(InterpreterFrame frame, long offset) {
        return frame.getReference(top, offset);
    }

    @AlwaysInline("Keep cached return values in the interpreter root")
    Object peekKindAsObject(InterpreterFrame frame, JavaKind kind) {
        // @formatter:off
        return switch (kind) {
            case Boolean -> (peekInt(frame, -1) & 1) != 0;
            case Byte    -> (byte) peekInt(frame, -1);
            case Short   -> (short) peekInt(frame, -1);
            case Char    -> (char) peekInt(frame, -1);
            case Int     -> peekInt(frame, -1);
            case Long    -> peekLong(frame, -1);
            case Float   -> peekFloat(frame, -1);
            case Double  -> peekDouble(frame, -1);
            case Void    -> null;
            case Object  -> peekObject(frame, -1);
            default      -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
        };
        // @formatter:on
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup1(InterpreterFrame frame) {
        copySlot(frame, -1, 0);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx1(InterpreterFrame frame) {
        copySlot(frame, -1, 0);
        copySlot(frame, -2, -1);
        copySlot(frame, 0, -2);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx2(InterpreterFrame frame) {
        copySlot(frame, -1, 0);
        copySlot(frame, -2, -1);
        copySlot(frame, -3, -2);
        copySlot(frame, 0, -3);
        top++;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2(InterpreterFrame frame) {
        copySlot(frame, -2, 0);
        copySlot(frame, -1, 1);
        top += 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x1(InterpreterFrame frame) {
        copySlot(frame, -2, 0);
        copySlot(frame, -1, 1);
        copySlot(frame, -3, -1);
        copySlot(frame, 0, -3);
        copySlot(frame, 1, -2);
        top += 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x2(InterpreterFrame frame) {
        copySlot(frame, -1, 1);
        copySlot(frame, -2, 0);
        copySlot(frame, -3, -1);
        copySlot(frame, -4, -2);
        copySlot(frame, 0, -4);
        copySlot(frame, 1, -3);
        top += 2;
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void swap(InterpreterFrame frame) {
        long primitive = frame.getPrimitive(top, -1);
        long value = frame.getPrimitive(top, -2);
        frame.setPrimitive(top, -1, value);
        frame.setPrimitive(top, -2, primitive);

        Object reference = frame.getReference(top, -1);
        Object value1 = frame.getReference(top, -2);
        frame.setReference(top, -1, value1);
        frame.setReference(top, -2, reference);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void copySlot(InterpreterFrame frame, long constantSrcOffset, long constantDstOffset) {
        long value = frame.getPrimitive(top, constantSrcOffset);
        frame.setPrimitive(top, constantDstOffset, value);
        Object value1 = frame.getReference(top, constantSrcOffset);
        frame.setReference(top, constantDstOffset, value1);
    }

    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void clearOperandStack(InterpreterFrame frame) {
        frame.clearOperandStack(top);
        top = frame.method.getMaxLocals();
    }
}
