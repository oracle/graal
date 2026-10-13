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

import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Boolean;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Byte;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Char;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Double;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Float;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Int;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Long;
import static com.oracle.svm.espresso.classfile.Constants.JVM_ArrayType_Short;

import com.oracle.svm.interpreter.metadata.InterpreterUnresolvedSignature;
import com.oracle.svm.shared.AlwaysInline;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.meta.JavaKind;

/**
 * Operand-stack overlay used by bytecode handlers. Up to two primitive slots are kept in the
 * threaded-handler state instead of being written to the frame. A long or double occupies two
 * slots, with its payload in the upper slot. Object values are always
 * materialized so that reference-map and stack-walking semantics remain unchanged. Profiling and
 * debugging use the materialized stack without caching primitives.
 *
 * The following diagram shows the slot layout and the state machine for one-slot primitive pushes
 * and pops. The inherited execution modes are defined in {@link InterpreterOperandStack}; this class
 * adds {@link #STATE_TOS_1} and {@link #STATE_TOS_2} for cached execution.
 *
 * <pre>{@code
 * Slots from bottom to top (top is the first slot not stored in the frame):
 *
 * STATE_NORMAL   [ ... frame ... ] | top
 * STATE_TOS_1    [ ... frame ... ] | tosPrimitive0
 * STATE_TOS_2    [ ... frame ... ] | tosPrimitive0 | tosPrimitive1
 *                                ^ top
 *
 * One-slot primitives (int/float):
 *
 *                   +-----+
 *                   | pop |
 *     +---------------+   |
 *     | STATE_NORMAL  |<--+    // memory stack only
 *     +---------------+
 *       |           ^
 *       | push      | pop
 *       v           |
 *     +---------------+
 *     | STATE_TOS_1   |        // tosPrimitive0 is valid
 *     +---------------+
 *       |           ^
 *       | push      | pop
 *       v           |
 *     +---------------+
 * +-->| STATE_TOS_2   |        // tosPrimitive0 and tosPrimitive1 are valid
 * |   +---------------+
 * |     | push
 * +-----+
 * }</pre>
 *
 * Pushing into a full cache spills the oldest slot to the frame. Long/double pushes spill existing
 * cached slots and use both cache slots; pops consume two slots, including a frame slot if only the
 * payload is cached. Flushing writes cached slots to the frame and resets the cache state to
 * {@link #STATE_NORMAL}. Object and return-address pushes flush first.
 */
final class CachedInterpreterOperandStack extends InterpreterOperandStack {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /** Normal execution with one cached primitive slot in {@link #tosPrimitive0}. */
    static final int STATE_TOS_1 = 3;
    /** Normal execution with two cached primitive slots, ordered from bottom to top. */
    static final int STATE_TOS_2 = 4;

    /**
     * Primitive bits for slot {@code top}, used in both cached states. This is the unused lower
     * slot when a complete long or double is cached.
     */
    private long tosPrimitive0;
    /**
     * Primitive bits for slot {@code top + 1}, used only in {@link #STATE_TOS_2}. Holds the payload
     * when a complete long or double is cached. Inactive cache fields must not be observed.
     */
    private long tosPrimitive1;

    @AlwaysInline("Expose the operand-stack overlay to escape analysis in interpreter entry")
    CachedInterpreterOperandStack(long top) {
        super(top);
    }

    /** Accepts the base execution modes and the two cached-slot states. */
    @Override
    boolean isValidState(int candidateState) {
        return candidateState >= STATE_NORMAL && candidateState <= STATE_TOS_2;
    }

    /**
     * Returns the frame stack top after cached operands have been flushed.
     */
    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long topForFrameStackOperation() {
        assert state != STATE_TOS_1 && state != STATE_TOS_2;
        return super.topForFrameStackOperation();
    }

    /** Writes cached operands to the frame before direct frame access or a call. */
    @Override
    @AlwaysInline("Flush cached primitives to the frame")
    void flushCache(InterpreterFrame frame) {
        materialize(frame);
    }

    /** Removes dependencies on inactive cache fields before passing state to the next handler. */
    @Override
    @AlwaysInline("Kill unused cached dependencies before handler dispatch")
    void beforeDispatch() {
        killUnusedFields();
    }

    /**
     * Writes cached slots to the frame and advances {@code top} past them, preserving the logical
     * operand stack. Resets the cache state to {@link #STATE_NORMAL} and discards both cached values.
     * Profiling and debugging modes already use the frame directly and retain their state.
     */
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void materialize(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                top++;
                state = STATE_NORMAL;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                frame.setPrimitive(top, 1, tosPrimitive1);
                top += 2;
                state = STATE_NORMAL;
            }
            default -> {
            }
        }
        killUnusedFields();
    }

    /**
     * Replaces unused cache fields with unconstrained values to remove dependencies on their previous
     * contents. This lets the compiler leave their handler return registers unchanged, avoiding moves
     * to preserve stale values or initialize unused registers. These values must not be observed.
     */
    @AlwaysInline("Kill dependencies on unused cached primitive values")
    void killUnusedFields() {
        switch (state) {
            case STATE_TOS_1 -> tosPrimitive1 = GraalDirectives.unconstrainedValue();
            case STATE_TOS_2 -> {
            }
            default -> {
                tosPrimitive0 = GraalDirectives.unconstrainedValue();
                tosPrimitive1 = GraalDirectives.unconstrainedValue();
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushInt(InterpreterFrame frame, int value) {
        switch (state) {
            case STATE_TOS_1 -> {
                tosPrimitive1 = value;
                state = STATE_TOS_2;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                tosPrimitive0 = tosPrimitive1;
                tosPrimitive1 = value;
                top++;
            }
            case STATE_PROFILING, STATE_DEBUGGING -> super.pushInt(frame, value);
            default -> {
                tosPrimitive0 = value;
                state = STATE_TOS_1;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushLong(InterpreterFrame frame, long value) {
        switch (state) {
            case STATE_TOS_1 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                tosPrimitive1 = value;
                top++;
                state = STATE_TOS_2;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                frame.setPrimitive(top, 1, tosPrimitive1);
                tosPrimitive1 = value;
                top += 2;
            }
            case STATE_PROFILING, STATE_DEBUGGING -> super.pushLong(frame, value);
            default -> {
                tosPrimitive0 = GraalDirectives.unconstrainedValue();
                tosPrimitive1 = value;
                state = STATE_TOS_2;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushObject(InterpreterFrame frame, Object value) {
        materialize(frame);
        super.pushObject(frame, value);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pushReturnAddress(InterpreterFrame frame, int targetBCI) {
        materialize(frame);
        super.pushReturnAddress(frame, targetBCI);
    }

    /**
     * Pops arguments from right to left, consuming cached slots before entering the frame-only loop.
     * Materializes any cached operands below the arguments and preserves profiling or debugging mode.
     */
    @Override
    @AlwaysInline("Keep the materialized state constant in the argument loop")
    void popArguments(InterpreterFrame frame, byte[] argumentKinds, Object[] arguments, long initialArgumentIndex) {
        long argumentIndex = initialArgumentIndex;
        if (state == STATE_TOS_1) {
            if (argumentIndex >= 0) {
                popArgument(frame, argumentKinds, arguments, argumentIndex);
                argumentIndex--;
                assert state == STATE_NORMAL;
            } else {
                materialize(frame);
                return;
            }
        } else if (state == STATE_TOS_2) {
            if (argumentIndex >= 0) {
                // Keep later decrements based on the index, not the original allocation length.
                argumentIndex = GraalDirectives.opaque(argumentIndex);
                int basicType = UNSAFE.getByte(argumentKinds, Unsafe.ARRAY_BYTE_BASE_OFFSET + argumentIndex * Unsafe.ARRAY_BYTE_INDEX_SCALE);
                // Only merge category-1 cases before the second pop: they all leave STATE_TOS_1.
                switch (basicType) {
                    case JVM_ArrayType_Boolean, JVM_ArrayType_Byte, JVM_ArrayType_Short, JVM_ArrayType_Char, JVM_ArrayType_Int, JVM_ArrayType_Float -> {
                        Object value = switch (basicType) {
                            case JVM_ArrayType_Boolean -> popInt(frame) != 0;
                            case JVM_ArrayType_Byte -> (byte) popInt(frame);
                            case JVM_ArrayType_Short -> (short) popInt(frame);
                            case JVM_ArrayType_Char -> (char) popInt(frame);
                            case JVM_ArrayType_Int -> popInt(frame);
                            case JVM_ArrayType_Float -> popFloat(frame);
                            default -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
                        };
                        UNSAFE.putReference(arguments, Unsafe.ARRAY_OBJECT_BASE_OFFSET + argumentIndex * Unsafe.ARRAY_OBJECT_INDEX_SCALE, value);
                        argumentIndex--;
                        assert state == STATE_TOS_1;
                        if (argumentIndex >= 0) {
                            popArgument(frame, argumentKinds, arguments, argumentIndex);
                            argumentIndex--;
                            assert state == STATE_NORMAL;
                        } else {
                            materialize(frame);
                            return;
                        }
                    }
                    case JVM_ArrayType_Long -> {
                        UNSAFE.putReference(arguments, Unsafe.ARRAY_OBJECT_BASE_OFFSET + argumentIndex * Unsafe.ARRAY_OBJECT_INDEX_SCALE, popLong(frame));
                        if (argumentIndex == 0) {
                            killUnusedFields();
                            return;
                        }
                        argumentIndex--;
                        assert state == STATE_NORMAL;
                    }
                    case JVM_ArrayType_Double -> {
                        UNSAFE.putReference(arguments, Unsafe.ARRAY_OBJECT_BASE_OFFSET + argumentIndex * Unsafe.ARRAY_OBJECT_INDEX_SCALE, popDouble(frame));
                        if (argumentIndex == 0) {
                            killUnusedFields();
                            return;
                        }
                        argumentIndex--;
                        assert state == STATE_NORMAL;
                    }
                    default -> throw InterpreterUtil.shouldNotReachHereAtRuntime();
                }
            } else {
                materialize(frame);
                return;
            }
        }
        killUnusedFields();
        assert state == STATE_NORMAL || state == STATE_PROFILING || state == STATE_DEBUGGING;
        // Restore the entry mode before each pop so the compiler sees a fixed, non-cached state
        // inside the loop rather than merging cache-state transitions across iterations.
        int invocationState = state;
        for (; GraalDirectives.injectBranchProbability(0.5, argumentIndex >= 0); argumentIndex--) {
            state = invocationState;
            popArgument(frame, argumentKinds, arguments, argumentIndex);
        }
    }

    /**
     * Collects arguments from an already materialized stack, using the supplied appendix as the last
     * argument. Keeps the entry mode constant across pops so the loop does not merge cached states.
     */
    @Override
    @AlwaysInline("Keep materialized invocation argument stack transitions together")
    void popArgumentsWithAppendix(InterpreterFrame frame, boolean hasReceiver, InterpreterUnresolvedSignature signature, Object[] arguments, Object appendix) {
        assert state == STATE_NORMAL || state == STATE_PROFILING || state == STATE_DEBUGGING;
        int invocationState = state;
        int argumentCount = signature.getParameterCount(false);
        int receiverCount = hasReceiver ? 1 : 0;

        int lastStackArgument = argumentCount - 1;
        assert signature.getParameterKind(lastStackArgument) == JavaKind.Object;
        arguments[lastStackArgument + receiverCount] = appendix;
        lastStackArgument--;
        for (int index = lastStackArgument; index >= 0; index--) {
            // Keep the entry mode constant across virtual pops in the loop.
            setState(invocationState);
            arguments[index + receiverCount] = popKind(frame, signature.getParameterKind(index));
        }
        if (hasReceiver) {
            arguments[0] = popObject(frame);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int popInt(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                state = STATE_NORMAL;
                return (int) tosPrimitive0;
            }
            case STATE_TOS_2 -> {
                state = STATE_TOS_1;
                return (int) tosPrimitive1;
            }
            default -> {
                int value = (int) frame.getPrimitive(top, -1);
                top--;
                return value;
            }
        }
    }

    /**
     * Peels cached ints before entering the frame-only loop, avoiding cache-state transitions in the
     * loop. Materializes any cached operands below the requested ints before returning.
     */
    @Override
    @AlwaysInline("Keep the cache state constant in the bulk int loop")
    void popInts(InterpreterFrame frame, int[] values) {
        int index = values.length - 1;
        if (state == STATE_TOS_2) {
            if (index < 0) {
                materialize(frame);
                return;
            }
            values[index] = popInt(frame);
            index--;
            assert state == STATE_TOS_1;
        }
        if (state == STATE_TOS_1) {
            if (index < 0) {
                materialize(frame);
                return;
            }
            values[index] = popInt(frame);
            index--;
            assert state == STATE_NORMAL;
        }
        killUnusedFields();
        assert state == STATE_NORMAL || state == STATE_PROFILING || state == STATE_DEBUGGING;
        // Restore the entry mode on each iteration to keep the state fixed inside the loop.
        int invocationState = state;
        for (; index >= 0; index--) {
            state = invocationState;
            values[index] = super.popInt(frame);
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float popFloat(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                state = STATE_NORMAL;
                return Float.intBitsToFloat((int) tosPrimitive0);
            }
            case STATE_TOS_2 -> {
                state = STATE_TOS_1;
                return Float.intBitsToFloat((int) tosPrimitive1);
            }
            default -> {
                float value = Float.intBitsToFloat((int) frame.getPrimitive(top, -1));
                top--;
                return value;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long popLong(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                top--;
                state = STATE_NORMAL;
                return tosPrimitive0;
            }
            case STATE_TOS_2 -> {
                state = STATE_NORMAL;
                return tosPrimitive1;
            }
            default -> {
                long value = frame.getPrimitive(top, -1);
                top -= 2;
                return value;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object popObject(InterpreterFrame frame) {
        if (state == STATE_TOS_1 || state == STATE_TOS_2) {
            // Objects are never cached, so verified bytecode cannot enter this variant.
            GraalDirectives.unreachable();
        }
        return super.popObject(frame);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop1(InterpreterFrame frame, boolean clear) {
        switch (state) {
            case STATE_TOS_1 -> {
                state = STATE_NORMAL;
            }
            case STATE_TOS_2 -> {
                state = STATE_TOS_1;
            }
            default -> {
                if (clear) {
                    frame.setReference(top, -1, null);
                }
                top--;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void pop2(InterpreterFrame frame, boolean clear) {
        switch (state) {
            case STATE_TOS_1 -> {
                if (clear) {
                    frame.setReference(top, -1, null);
                }
                top--;
                state = STATE_NORMAL;
            }
            case STATE_TOS_2 -> {
                state = STATE_NORMAL;
            }
            default -> {
                if (clear) {
                    frame.setReference(top, -1, null);
                    frame.setReference(top, -2, null);
                }
                top -= 2;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    int peekInt(InterpreterFrame frame, long initialOffset) {
        long offset = initialOffset;
        switch (state) {
            case STATE_TOS_2 -> {
                if (offset == -1) {
                    return (int) tosPrimitive1;
                }
                if (offset == -2) {
                    return (int) tosPrimitive0;
                }
                offset += 2;
            }
            case STATE_TOS_1 -> {
                if (offset == -1) {
                    return (int) tosPrimitive0;
                }
                offset++;
            }
            default -> {
            }
        }
        assert offset < 0;
        return (int) frame.getPrimitive(top, offset);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    float peekFloat(InterpreterFrame frame, long initialOffset) {
        long offset = initialOffset;
        switch (state) {
            case STATE_TOS_2 -> {
                if (offset == -1) {
                    return Float.intBitsToFloat((int) tosPrimitive1);
                }
                if (offset == -2) {
                    return Float.intBitsToFloat((int) tosPrimitive0);
                }
                offset += 2;
            }
            case STATE_TOS_1 -> {
                if (offset == -1) {
                    return Float.intBitsToFloat((int) tosPrimitive0);
                }
                offset++;
            }
            default -> {
            }
        }
        assert offset < 0;
        return Float.intBitsToFloat((int) frame.getPrimitive(top, offset));
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    long peekLong(InterpreterFrame frame, long initialOffset) {
        long offset = initialOffset;
        switch (state) {
            case STATE_TOS_2 -> {
                if (offset == -1) {
                    return tosPrimitive1;
                }
                if (offset == -2) {
                    return tosPrimitive0;
                }
                offset += 2;
            }
            case STATE_TOS_1 -> {
                if (offset == -1) {
                    return tosPrimitive0;
                }
                offset++;
            }
            default -> {
            }
        }
        if (offset >= 0) {
            GraalDirectives.unreachable();
        }
        return frame.getPrimitive(top, offset);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    Object peekObject(InterpreterFrame frame, long initialOffset) {
        long offset = initialOffset;
        offset += switch (state) {
            case STATE_TOS_1 -> 1;
            case STATE_TOS_2 -> 2;
            default -> 0;
        };
        if (offset >= 0) {
            GraalDirectives.unreachable();
        }
        return frame.getReference(top, offset);
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup1(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                tosPrimitive1 = tosPrimitive0;
                state = STATE_TOS_2;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                tosPrimitive0 = tosPrimitive1;
                top++;
            }
            default -> {
                copySlot(frame, -1, 0);
                top++;
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx1(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                copySlot(frame, -1, 0);
                overwriteSlot(frame, -1, tosPrimitive0);
                top++;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive1);
                top++;
            }
            default -> {
                super.dupx1(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dupx2(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                copySlot(frame, -1, 0);
                copySlot(frame, -2, -1);
                overwriteSlot(frame, -2, tosPrimitive0);
                top++;
            }
            case STATE_TOS_2 -> {
                copySlot(frame, -1, 0);
                overwriteSlot(frame, -1, tosPrimitive1);
                top++;
            }
            default -> {
                super.dupx2(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                copySlot(frame, -1, 1);
                top += 2;
            }
            case STATE_TOS_2 -> {
                frame.setPrimitive(top, 0, tosPrimitive0);
                frame.setPrimitive(top, 1, tosPrimitive1);
                top += 2;
            }
            default -> {
                super.dup2(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x1(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                copySlot(frame, -1, 1);
                copySlot(frame, -2, 0);
                copySlot(frame, -1, -2);
                overwriteSlot(frame, -1, tosPrimitive0);
                top += 2;
            }
            case STATE_TOS_2 -> {
                copySlot(frame, -1, 1);
                overwriteSlot(frame, -1, tosPrimitive0);
                overwriteSlot(frame, 0, tosPrimitive1);
                top += 2;
            }
            default -> {
                super.dup2x1(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void dup2x2(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                copySlot(frame, -1, 1);
                copySlot(frame, -2, 0);
                copySlot(frame, -3, -1);
                copySlot(frame, 1, -3);
                overwriteSlot(frame, -2, tosPrimitive0);
                top += 2;
            }
            case STATE_TOS_2 -> {
                copySlot(frame, -1, 1);
                copySlot(frame, -2, 0);
                overwriteSlot(frame, -2, tosPrimitive0);
                overwriteSlot(frame, -1, tosPrimitive1);
                top += 2;
            }
            default -> {
                super.dup2x2(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void swap(InterpreterFrame frame) {
        switch (state) {
            case STATE_TOS_1 -> {
                copySlot(frame, -1, 0);
                overwriteSlot(frame, -1, tosPrimitive0);
                top++;
                state = STATE_NORMAL;
            }
            case STATE_TOS_2 -> {
                long value = tosPrimitive0;
                tosPrimitive0 = tosPrimitive1;
                tosPrimitive1 = value;
            }
            default -> {
                super.swap(frame);
            }
        }
    }

    @Override
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    void clearOperandStack(InterpreterFrame frame) {
        tosPrimitive0 = 0;
        tosPrimitive1 = 0;
        if (state == STATE_TOS_1 || state == STATE_TOS_2) {
            state = STATE_NORMAL;
        }
        super.clearOperandStack(frame);
    }

    /**
     * Copies both the primitive and reference parts of a frame slot. Offsets are relative to
     * {@code top}, excluding cached slots; neither the stack top nor the cache is changed.
     */
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void copySlot(InterpreterFrame frame, long srcOffset, long dstOffset) {
        frame.setPrimitive(top, dstOffset, frame.getPrimitive(top, srcOffset));
        frame.setReference(top, dstOffset, frame.getReference(top, srcOffset));
    }

    /**
     * Replaces a frame slot with a primitive value, clearing any reference previously held there.
     * The offset is relative to {@code top}, excluding cached slots; the top and cache are unchanged.
     */
    @AlwaysInline("Keep InterpreterOperandStack virtual-expanded")
    private void overwriteSlot(InterpreterFrame frame, long offset, long value) {
        frame.setReference(top, offset, null);
        frame.setPrimitive(top, offset, value);
    }
}
