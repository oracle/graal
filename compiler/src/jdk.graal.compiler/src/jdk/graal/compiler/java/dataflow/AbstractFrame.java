/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.java.dataflow;

import jdk.graal.compiler.debug.GraalError;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract representation of a bytecode execution frame, i.e., its
 * {@link AbstractFrame#operandStack operand stack} and {@link AbstractFrame#localVariableTable
 * local variable table}.
 *
 * @param <T> The abstract representation of values pushed and popped from the operand stack and
 *            stored in the local variable table.
 */
public class AbstractFrame<T> {

    private final OperandStack<T> operandStack;
    private final LocalVariableTable<T> localVariableTable;

    /**
     * Get the operand stack of this abstract frame.
     */
    public OperandStack<T> operandStack() {
        return operandStack;
    }

    /**
     * Get the local variable table of this abstract frame.
     */
    public LocalVariableTable<T> localVariableTable() {
        return localVariableTable;
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

    AbstractFrame() {
        this.operandStack = new OperandStack<>();
        this.localVariableTable = new LocalVariableTable<>();
    }

    AbstractFrame(AbstractFrame<T> state) {
        this.operandStack = new OperandStack<>(state.operandStack);
        this.localVariableTable = new LocalVariableTable<>(state.localVariableTable);
    }

    void mergeWith(AbstractFrame<T> other, BiFunction<T, T, T> mergeFunction) {
        operandStack.mergeWith(other.operandStack, mergeFunction);
        localVariableTable.mergeWith(other.localVariableTable, mergeFunction);
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

    /**
     * Abstract representation of a bytecode operand stack.
     */
    public static final class OperandStack<T> {

        /*
         * An ArrayList is used in order to allow for efficient lookups at arbitrary stack
         * positions, as well as to preserve memory in comparison to allocating a plain array with a
         * max stack size.
         */
        private final ArrayList<ValueWithSlots<T>> stack;

        /**
         * Get a value at the specified depth of the operand stack. The {@code depth} does not take
         * into account the size of values, i.e., a {@link ValueWithSlots} with size equal to
         * {@link ValueWithSlots.Slots#TWO_SLOTS TWO_SLOTS} contributes only as one value to the
         * depth of the operand stack.
         */
        public T getOperand(int depth) {
            return peek(depth).value;
        }

        /**
         * Get the number of values currently on the operand stack. This does not take into account
         * the size of values, i.e., a {@link ValueWithSlots} with size equal to
         * {@link ValueWithSlots.Slots#TWO_SLOTS TWO_SLOTS} contributes only as one value for this
         * method.
         */
        public int size() {
            return stack.size();
        }

        /**
         * Transform the chosen values on the operand stack.
         *
         * @param filterFunction Values which satisfy this predicate are subject to transformation
         *            with {@code transformFunction}.
         * @param transformFunction The transformation function.
         */
        public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (int i = 0; i < stack.size(); i++) {
                ValueWithSlots<T> value = stack.get(i);
                if (filterFunction.test(value.value())) {
                    stack.set(i, new ValueWithSlots<>(transformFunction.apply(value.value()), value.size()));
                }
            }
        }

        OperandStack() {
            this.stack = new ArrayList<>();
        }

        OperandStack(OperandStack<T> stack) {
            this.stack = new ArrayList<>(stack.stack);
        }

        void push(ValueWithSlots<T> value) {
            stack.add(value);
        }

        ValueWithSlots<T> pop() {
            GraalError.guarantee(!stack.isEmpty(), "Cannot pop from empty stack");
            return stack.removeLast();
        }

        ValueWithSlots<T> peek(int depth) {
            GraalError.guarantee(0 <= depth && depth < size(), "Operand stack doesn't contain enough values");
            return stack.get(stack.size() - depth - 1);
        }

        ValueWithSlots<T> peek() {
            return peek(0);
        }

        void clear() {
            stack.clear();
        }

        void mergeWith(OperandStack<T> other, BiFunction<T, T, T> mergeFunction) {
            GraalError.guarantee(size() == other.size(), "Operand stack size must match upon merging");
            for (int i = 0; i < stack.size(); i++) {
                ValueWithSlots<T> thisValue = stack.get(i);
                ValueWithSlots<T> thatValue = other.stack.get(i);
                GraalError.guarantee(thisValue.size() == thatValue.size(), "The size of operand stack values must match upon merging");
                ValueWithSlots<T> mergedValue = new ValueWithSlots<>(mergeFunction.apply(thisValue.value(), thatValue.value()), thisValue.size());
                stack.set(i, mergedValue);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OperandStack<?> that = (OperandStack<?>) o;
            return Objects.equals(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(stack);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Operand stack:\n");
            for (ValueWithSlots<T> value : stack.reversed()) {
                builder.append("[").append(value.value()).append("]").append(System.lineSeparator());
            }
            return builder.toString();
        }
    }

    /**
     * Abstract representation of a bytecode local variable table.
     */
    public static final class LocalVariableTable<T> {

        private final Map<Integer, ValueWithSlots<T>> variables;

        /**
         * Get the value at the {@code index} slot of the local variable table. The {@code index}
         * must be valid, i.e., must be in the set which would be returned by
         * {@link #getVariableIndices()}.
         */
        public T getVariable(int index) {
            return get(index).value;
        }

        /**
         * Get the local variable table indices of entries currently stored in it.
         */
        public Set<Integer> getVariableIndices() {
            return variables.keySet();
        }

        /**
         * Transform the chosen values in the local variable table.
         *
         * @param filterFunction Values which satisfy this predicate are subject to transformation
         *            with {@code transformFunction}.
         * @param transformFunction The transformation function.
         */
        public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (Map.Entry<Integer, ValueWithSlots<T>> entry : variables.entrySet()) {
                ValueWithSlots<T> value = entry.getValue();
                if (filterFunction.test(value.value())) {
                    entry.setValue(new ValueWithSlots<>(transformFunction.apply(value.value()), value.size()));
                }
            }
        }

        LocalVariableTable() {
            this.variables = new HashMap<>();
        }

        LocalVariableTable(LocalVariableTable<T> localVariableTable) {
            this.variables = new HashMap<>(localVariableTable.variables);
        }

        void put(int index, ValueWithSlots<T> value) {
            variables.put(index, value);
        }

        ValueWithSlots<T> get(int index) {
            GraalError.guarantee(variables.containsKey(index), "Attempted to access non-existent variable in local variable table");
            return variables.get(index);
        }

        void mergeWith(LocalVariableTable<T> other, BiFunction<T, T, T> mergeFunction) {
            for (Map.Entry<Integer, ValueWithSlots<T>> entry : variables.entrySet()) {
                ValueWithSlots<T> thisValue = entry.getValue();
                ValueWithSlots<T> thatValue = other.variables.get(entry.getKey());
                if (thatValue != null && thisValue.size() == thatValue.size()) {
                    /*
                     * We can always merge matching values from the local variable table. If the
                     * merging makes no sense (i.e., the stored variable types do not match), we
                     * still allow it, as the resulting value should not be used during execution
                     * anyway (or else the method would fail bytecode verification).
                     */
                    ValueWithSlots<T> mergedValue = new ValueWithSlots<>(mergeFunction.apply(thisValue.value(), thatValue.value()), thisValue.size());
                    entry.setValue(mergedValue);
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LocalVariableTable<?> that = (LocalVariableTable<?>) o;
            return Objects.equals(variables, that.variables);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(variables);
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Local variable table:\n");
            variables.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(
                            e -> {
                                Integer varIndex = e.getKey();
                                ValueWithSlots<T> value = e.getValue();
                                builder.append(varIndex).append(": ").append(value.value()).append(System.lineSeparator());
                            });
            return builder.toString();
        }
    }

    /**
     * Wrapper which assigns a computational type category to a value, i.e., assigns the number of
     * slots the value takes up in the local variable table or operand stack.
     */
    record ValueWithSlots<T>(T value, Slots size) {
        public enum Slots {
            ONE_SLOT,
            TWO_SLOTS
        }

        public static <T> ValueWithSlots<T> wrap(T value, Slots size) {
            return new ValueWithSlots<>(value, size);
        }
    }
}
