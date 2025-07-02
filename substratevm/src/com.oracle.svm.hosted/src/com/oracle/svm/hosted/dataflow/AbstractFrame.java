/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dataflow;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Abstract representation of a bytecode execution frame for an instruction, i.e., its
 * {@link AbstractFrame#operandStack operand stack} and {@link AbstractFrame#localVariableTable
 * local variable table}, right before the execution of said instruction.
 *
 * @param <T> The abstract representation of values stored in the frame.
 */
public final class AbstractFrame<T> {

    /**
     * Second slot marker for values requiring two slots on the operand stack and in the local
     * variable table (i.e., values of type long and double).
     */
    private static final ValueFrame<Object> TWO_SLOT_MARKER = new ValueFrame<>(null);

    @SuppressWarnings("unchecked")
    private static <T> ValueFrame<T> twoSlotMarker() {
        return (ValueFrame<T>) TWO_SLOT_MARKER;
    }

    final OperandStack operandStack;
    final LocalVariableTable localVariableTable;

    AbstractFrame(ResolvedJavaMethod method) {
        this.operandStack = new OperandStack(method.getMaxStackSize());
        this.localVariableTable = new LocalVariableTable(method.getMaxLocals());
    }

    AbstractFrame(AbstractFrame<T> other) {
        this.operandStack = new OperandStack(other.operandStack);
        this.localVariableTable = new LocalVariableTable(other.localVariableTable);
    }

    private AbstractFrame(OperandStack operandStack, LocalVariableTable localVariableTable) {
        this.operandStack = operandStack;
        this.localVariableTable = localVariableTable;
    }

    /**
     * Get the value on the operand stack at the specified {@code depth}. The {@code depth}
     * parameter corresponds to actual values on the operand stack, and not the frames. This means
     * that a value occupying two stack frames contributes only once to the operand depth.
     */
    public T getOperand(int depth) {
        int currentDepth = 0;
        int frameFromTop = 0;
        while (currentDepth <= depth) {
            ValueFrame<T> frame = operandStack.peekFrame(frameFromTop);
            if (frame != TWO_SLOT_MARKER) {
                currentDepth++;
            }
            frameFromTop++;
        }
        return operandStack.peekFrame(frameFromTop - 1).value;
    }

    /**
     * Transform the chosen values in the abstract frame. This affects both the values on the
     * operand stack and in the local variable table.
     *
     * @param filterFunction Values which satisfy this predicate are subject to transformation with
     *            {@code transformFunction}.
     * @param transformFunction The transformation function.
     */
    public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
        operandStack.transform(filterFunction, transformFunction);
        localVariableTable.transform(filterFunction, transformFunction);
    }

    AbstractFrame<T> merge(AbstractFrame<T> other, BiFunction<T, T, T> mergeFunction) {
        OperandStack mergedOperandStack = operandStack.merge(other.operandStack, mergeFunction);
        LocalVariableTable mergedLocalVariableTable = localVariableTable.merge(other.localVariableTable, mergeFunction);
        return new AbstractFrame<>(mergedOperandStack, mergedLocalVariableTable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractFrame<?> that = (AbstractFrame<?>) o;
        return Objects.equals(operandStack, that.operandStack) && Objects.equals(localVariableTable, that.localVariableTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operandStack, localVariableTable);
    }

    @Override
    public String toString() {
        return operandStack + System.lineSeparator() + localVariableTable;
    }

    final class OperandStack {

        private final ValueFrame<T>[] stack;
        private int size;

        @SuppressWarnings("unchecked")
        OperandStack(int maxStackSize) {
            this.stack = new ValueFrame[maxStackSize];
            this.size = 0;
        }

        OperandStack(OperandStack other) {
            this.stack = other.stack.clone();
            this.size = other.size;
        }

        void push(T value, boolean needsTwoSlots) {
            pushFrame(new ValueFrame<>(value));
            if (needsTwoSlots) {
                pushFrame(twoSlotMarker());
            }
        }

        T pop() {
            ValueFrame<T> frame = popFrame();
            if (frame == TWO_SLOT_MARKER) {
                return popFrame().value;
            } else {
                return frame.value;
            }
        }

        void clear() {
            Arrays.fill(stack, null);
            size = 0;
        }

        void applyPop() {
            ValueFrame<T> f1 = popFrame();
            assert f1 != TWO_SLOT_MARKER;
        }

        void applyPop2() {
            popFrame();
            ValueFrame<T> f2 = popFrame();
            assert f2 != TWO_SLOT_MARKER;
        }

        void applyDup() {
            ValueFrame<T> f1 = peekFrame();
            assert f1 != TWO_SLOT_MARKER;
            pushFrame(f1);
        }

        void applyDupX1() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            assert f1 != TWO_SLOT_MARKER && f2 != TWO_SLOT_MARKER;
            pushFrame(f1);
            pushFrame(f2);
            pushFrame(f1);
        }

        void applyDupX2() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            ValueFrame<T> f3 = popFrame();
            assert f1 != TWO_SLOT_MARKER && f3 != TWO_SLOT_MARKER;
            pushFrame(f1);
            pushFrame(f3);
            pushFrame(f2);
            pushFrame(f1);
        }

        void applyDup2() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            assert f2 != TWO_SLOT_MARKER;
            pushFrame(f2);
            pushFrame(f1);
            pushFrame(f2);
            pushFrame(f1);
        }

        void applyDup2X1() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            ValueFrame<T> f3 = popFrame();
            assert f2 != TWO_SLOT_MARKER && f3 != TWO_SLOT_MARKER;
            pushFrame(f2);
            pushFrame(f1);
            pushFrame(f3);
            pushFrame(f2);
            pushFrame(f1);
        }

        void applyDup2X2() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            ValueFrame<T> f3 = popFrame();
            ValueFrame<T> f4 = popFrame();
            pushFrame(f2);
            pushFrame(f1);
            pushFrame(f4);
            pushFrame(f3);
            pushFrame(f2);
            pushFrame(f1);
        }

        void applySwap() {
            ValueFrame<T> f1 = popFrame();
            ValueFrame<T> f2 = popFrame();
            assert f1 != TWO_SLOT_MARKER && f2 != TWO_SLOT_MARKER;
            pushFrame(f1);
            pushFrame(f2);
        }

        private void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (int i = 0; i < size; i++) {
                ValueFrame<T> frame = stack[i];
                if (frame != null && frame != TWO_SLOT_MARKER && filterFunction.test(frame.value)) {
                    stack[i] = new ValueFrame<>(transformFunction.apply(frame.value));
                }
            }
        }

        private OperandStack merge(OperandStack other, BiFunction<T, T, T> mergeFunction) {
            assert size == other.size;
            OperandStack merged = new OperandStack(operandStack);
            for (int i = 0; i < size; i++) {
                ValueFrame<T> thisFrame = stack[i];
                ValueFrame<T> otherFrame = other.stack[i];
                if (thisFrame == TWO_SLOT_MARKER) {
                    assert otherFrame == TWO_SLOT_MARKER;
                    merged.stack[i] = twoSlotMarker();
                } else {
                    assert otherFrame != TWO_SLOT_MARKER;
                    T mergedValue = mergeFunction.apply(thisFrame.value, otherFrame.value);
                    merged.stack[i] = new ValueFrame<>(mergedValue);
                }
            }
            return merged;
        }

        private void pushFrame(ValueFrame<T> frame) {
            assert size < stack.length;
            stack[size++] = frame;
        }

        private ValueFrame<T> popFrame() {
            assert size > 0;
            ValueFrame<T> popped = stack[--size];
            stack[size] = null;
            return popped;
        }

        private ValueFrame<T> peekFrame() {
            return peekFrame(0);
        }

        private ValueFrame<T> peekFrame(int depth) {
            assert 0 <= depth && depth < size;
            return stack[size - depth - 1];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            @SuppressWarnings("unchecked")
            OperandStack that = (OperandStack) o;
            return size == that.size && Objects.deepEquals(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(stack), size);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Operand stack:").append(System.lineSeparator());
            for (int i = size - 1; i >= 0; i--) {
                ValueFrame<T> frame = stack[i];
                if (frame != TWO_SLOT_MARKER) {
                    sb.append("[").append(frame.value).append("]").append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }

    final class LocalVariableTable {

        private final ValueFrame<T>[] variables;

        @SuppressWarnings("unchecked")
        LocalVariableTable(int maxLocals) {
            this.variables = new ValueFrame[maxLocals];
        }

        LocalVariableTable(LocalVariableTable other) {
            this.variables = other.variables.clone();
        }

        void put(T value, int index, boolean needsTwoSlots) {
            if (variables[index] == TWO_SLOT_MARKER) {
                /*
                 * Store operations into a local variable slot occupied by the second half of a two
                 * slot value is a legal operation, but it invalidates the variable previously
                 * occupying two slots.
                 */
                putFrame(null, index - 1);
            }
            int nextIndex = index + 1;
            if (nextIndex < variables.length && variables[nextIndex] == TWO_SLOT_MARKER) {
                putFrame(null, nextIndex);
            }
            putFrame(new ValueFrame<>(value), index);
            if (needsTwoSlots) {
                putFrame(twoSlotMarker(), nextIndex);
            }
        }

        T get(int index) {
            assert 0 <= index && index < variables.length;
            ValueFrame<T> frame = variables[index];
            assert frame != null && frame != TWO_SLOT_MARKER;
            return frame.value;
        }

        private void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (int i = 0; i < variables.length; i++) {
                ValueFrame<T> frame = variables[i];
                if (frame != null && frame != TWO_SLOT_MARKER && filterFunction.test(frame.value)) {
                    variables[i] = new ValueFrame<>(transformFunction.apply(frame.value));
                }
            }
        }

        private LocalVariableTable merge(LocalVariableTable other, BiFunction<T, T, T> mergeFunction) {
            LocalVariableTable merged = new LocalVariableTable(localVariableTable);
            for (int i = 0; i < variables.length; i++) {
                ValueFrame<T> thisFrame = variables[i];
                ValueFrame<T> otherFrame = other.variables[i];
                if (thisFrame != null && otherFrame != null) {
                    /*
                     * We can always merge matching values from the local variable table. If the
                     * merging makes no sense (i.e., the stored variable types do not match), we can
                     * still allow it, as the resulting value should not be used during execution
                     * anyway (or else the method would fail bytecode verification).
                     */
                    if (thisFrame == TWO_SLOT_MARKER && otherFrame == TWO_SLOT_MARKER) {
                        merged.variables[i] = twoSlotMarker();
                    } else if (thisFrame != TWO_SLOT_MARKER && otherFrame != TWO_SLOT_MARKER) {
                        T mergedValue = mergeFunction.apply(thisFrame.value, otherFrame.value);
                        merged.variables[i] = new ValueFrame<>(mergedValue);
                    }
                }
            }
            return merged;
        }

        private void putFrame(ValueFrame<T> frame, int index) {
            assert 0 <= index && index < variables.length;
            variables[index] = frame;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            @SuppressWarnings("unchecked")
            LocalVariableTable that = (LocalVariableTable) o;
            return Objects.deepEquals(variables, that.variables);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(variables);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Local variable table:").append(System.lineSeparator());
            for (int i = 0; i < variables.length; i++) {
                ValueFrame<T> frame = variables[i];
                if (frame != TWO_SLOT_MARKER && frame != null) {
                    sb.append(i).append(": ").append(frame.value).append(System.lineSeparator());
                }
            }
            return sb.toString();
        }
    }

    /**
     * A single frame/slot wrapper over a {@code value}. In case of values requiring two slots
     * (i.e., values of type long and double), a {@link AbstractFrame#TWO_SLOT_MARKER} is used to
     * represent the second slot.
     */
    private record ValueFrame<T>(T value) {

    }
}
