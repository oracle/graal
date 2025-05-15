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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Abstract representation of a bytecode execution frame.
 *
 * @param <T> The abstract representation of values pushed and popped from the operand stack and stored in the local variable table.
 */
public class AbstractFrame<T> {

    private final OperandStack<T> operandStack;
    private final LocalVariableTable<T> localVariableTable;

    public AbstractFrame() {
        this.operandStack = new OperandStack<>();
        this.localVariableTable = new LocalVariableTable<>();
    }

    public AbstractFrame(AbstractFrame<T> state) {
        this.operandStack = new OperandStack<>(state.operandStack);
        this.localVariableTable = new LocalVariableTable<>(state.localVariableTable);
    }

    public void mergeWith(AbstractFrame<T> other, BiFunction<T, T, T> mergeFunction) throws DataFlowAnalysisException {
        operandStack.mergeWith(other.operandStack, mergeFunction);
        localVariableTable.mergeWith(other.localVariableTable, mergeFunction);
    }

    public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
        operandStack.transform(filterFunction, transformFunction);
        localVariableTable.transform(filterFunction, transformFunction);
    }

    public OperandStack<T> getOperandStack() {
        return operandStack;
    }

    public LocalVariableTable<T> getLocalVariableTable() {
        return localVariableTable;
    }

    public T getOperand(int depth) {
        try {
            return operandStack.peek(depth).value;
        } catch (DataFlowAnalysisException e) {
            throw new RuntimeException(e);
        }
    }

    public T getVariable(int index) {
        try {
            return localVariableTable.get(index).value;
        } catch (DataFlowAnalysisException e) {
            throw new RuntimeException(e);
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
        AbstractFrame<?> that = (AbstractFrame<?>) o;
        return Objects.equals(operandStack, that.operandStack) && Objects.equals(localVariableTable, that.localVariableTable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operandStack, localVariableTable);
    }

    @Override
    public String toString() {
        return operandStack + "" + localVariableTable;
    }

    public static final class OperandStack<T> {

        private final ArrayList<SizedValue<T>> stack;

        public OperandStack() {
            this.stack = new ArrayList<>();
        }

        public OperandStack(OperandStack<T> stack) {
            this.stack = new ArrayList<>(stack.stack);
        }

        public void push(SizedValue<T> value) {
            stack.add(value);
        }

        public SizedValue<T> pop() throws DataFlowAnalysisException {
            if (stack.isEmpty()) {
                throw new DataFlowAnalysisException("Operand stack is empty");
            }
            return stack.removeLast();
        }

        public SizedValue<T> peek(int depth) throws DataFlowAnalysisException {
            if (size() <= depth) {
                throw new DataFlowAnalysisException("Operand stack doesn't contain enough values");
            }
            return stack.get(stack.size() - depth - 1);
        }

        public SizedValue<T> peek() throws DataFlowAnalysisException {
            return peek(0);
        }

        public void clear() {
            stack.clear();
        }

        public int size() {
            return stack.size();
        }

        public void mergeWith(OperandStack<T> other, BiFunction<T, T, T> mergeFunction) throws DataFlowAnalysisException {
            if (size() != other.size()) {
                throw new DataFlowAnalysisException("Operand stack size mismatch upon merging");
            }
            for (int i = 0; i < stack.size(); i++) {
                SizedValue<T> thisValue = stack.get(i);
                SizedValue<T> thatValue = other.stack.get(i);
                if (thisValue.size() != thatValue.size()) {
                    throw new DataFlowAnalysisException("Operand stack value size mismatch upon merging");
                }
                SizedValue<T> mergedValue = new SizedValue<>(mergeFunction.apply(thisValue.value(), thatValue.value()), thisValue.size());
                stack.set(i, mergedValue);
            }
        }

        public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (int i = 0; i < stack.size(); i++) {
                SizedValue<T> value = stack.get(i);
                if (filterFunction.test(value.value())) {
                    stack.set(i, new SizedValue<>(transformFunction.apply(value.value()), value.size()));
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
            for (SizedValue<T> value : stack.reversed()) {
                builder.append("[").append(value.value()).append("]").append("\n");
            }
            return builder.toString();
        }
    }

    public static final class LocalVariableTable<T> {

        private final Map<Integer, SizedValue<T>> variables;

        public LocalVariableTable() {
            this.variables = new HashMap<>();
        }

        public LocalVariableTable(LocalVariableTable<T> localVariableTable) {
            this.variables = new HashMap<>(localVariableTable.variables);
        }

        public void put(int index, SizedValue<T> value) {
            variables.put(index, value);
        }

        public SizedValue<T> get(int index) throws DataFlowAnalysisException {
            if (!variables.containsKey(index)) {
                throw new DataFlowAnalysisException("Variable table doesn't contain entry for index " + index);
            }
            return variables.get(index);
        }

        public void mergeWith(LocalVariableTable<T> other, BiFunction<T, T, T> mergeFunction) {
            for (Map.Entry<Integer, SizedValue<T>> entry : variables.entrySet()) {
                SizedValue<T> thisValue = entry.getValue();
                SizedValue<T> thatValue = other.variables.get(entry.getKey());
                if (thatValue != null) {
                    SizedValue<T> mergedValue = new SizedValue<>(mergeFunction.apply(thisValue.value(), thatValue.value()), thisValue.size());
                    entry.setValue(mergedValue);
                }
            }
        }

        public void transform(Predicate<T> filterFunction, Function<T, T> transformFunction) {
            for (Map.Entry<Integer, SizedValue<T>> entry : variables.entrySet()) {
                SizedValue<T> value = entry.getValue();
                if (filterFunction.test(value.value())) {
                    entry.setValue(new SizedValue<>(transformFunction.apply(value.value()), value.size()));
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
                    SizedValue<T> value = e.getValue();
                    builder.append(varIndex).append(": ").append(value.value()).append("\n");
                }
            );
            return builder.toString();
        }
    }

    public record SizedValue<T>(T value, Slots size) {
        public enum Slots {ONE_SLOT, TWO_SLOTS}

        public static <T> SizedValue<T> wrap(T value, Slots size) {
            return new SizedValue<>(value, size);
        }
    }
}
