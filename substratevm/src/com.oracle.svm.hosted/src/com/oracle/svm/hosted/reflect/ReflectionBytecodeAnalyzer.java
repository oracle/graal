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
package com.oracle.svm.hosted.reflect;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.TypeResult;
import jdk.graal.compiler.java.dataflow.AbstractFrame;
import jdk.graal.compiler.java.dataflow.AbstractInterpreter;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static jdk.graal.compiler.bytecode.Bytecodes.ACONST_NULL;
import static jdk.graal.compiler.bytecode.Bytecodes.ANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.CHECKCAST;

public class ReflectionBytecodeAnalyzer extends AbstractInterpreter<ReflectionBytecodeAnalyzer.ReflectionAnalysisValue> {

    private static final NotACompileTimeConstant NOT_A_COMPILE_TIME_CONSTANT = new NotACompileTimeConstant();

    private final ImageClassLoader classLoader;

    public ReflectionBytecodeAnalyzer(CoreProviders providers, ImageClassLoader classLoader) {
        super(providers);
        this.classLoader = classLoader;
    }

    @Override
    protected ReflectionAnalysisValue top() {
        return NOT_A_COMPILE_TIME_CONSTANT;
    }

    @Override
    protected ReflectionAnalysisValue merge(ReflectionAnalysisValue left, ReflectionAnalysisValue right) {
        return left.equals(right) ? left : top();
    }

    @Override
    protected ReflectionAnalysisValue pushConstant(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, Constant constant) {
        if (opcode == ACONST_NULL) {
            return new CompileTimeValueConstant<>(bci, null);
        }
        if (constant instanceof JavaConstant javaConstant) {
            Object javaValue = switch (javaConstant.getJavaKind().getStackKind()) {
                case Int -> javaConstant.asInt();
                case Long -> javaConstant.asLong();
                case Float -> javaConstant.asFloat();
                case Double -> javaConstant.asDouble();
                case Object -> getProviders().getSnippetReflection().asObject(Object.class, javaConstant);
                default -> null;
            };
            if (javaValue != null) {
                return new CompileTimeValueConstant<>(bci, javaValue);
            }
        }
        return top();
    }

    @Override
    protected ReflectionAnalysisValue pushType(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaType type) {
        if (type instanceof ResolvedJavaType resolvedType) {
            return new CompileTimeValueConstant<>(bci, OriginalClassProvider.getJavaClass(resolvedType));
        } else {
            return top();
        }
    }

    @Override
    protected ReflectionAnalysisValue loadVariable(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, int variableIndex, ReflectionAnalysisValue value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(bci, constant.getValue());
        } else {
            return top();
        }
    }

    @Override
    protected ReflectionAnalysisValue storeVariable(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, int variableIndex, ReflectionAnalysisValue value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(bci, constant.getValue());
        } else if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
            return top();
        } else {
            return top();
        }
    }

    @Override
    protected void storeArrayElement(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, ReflectionAnalysisValue array, ReflectionAnalysisValue index, ReflectionAnalysisValue value) {
        if (array instanceof CompileTimeArrayConstant<?> constantArray) {
            if (index instanceof CompileTimeValueConstant<?> constantIndex && value instanceof CompileTimeValueConstant<?> constantValue) {
                CompileTimeArrayConstant<?> newConstantArray = new CompileTimeArrayConstant<>(bci, constantArray);
                try {
                    int realIndex = ((Number) constantIndex.getValue()).intValue();
                    newConstantArray.setElement(realIndex, constantValue.getValue());
                    state.transform(v -> v.equals(constantArray), v -> newConstantArray);
                } catch (Exception e) {
                    state.transform(v -> v.equals(constantArray), v -> top());
                }
            } else {
                state.transform(v -> v.equals(constantArray), v -> top());
            }
        }
    }

    @Override
    protected ReflectionAnalysisValue loadStaticField(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaField field) {
        if (field.getName().equals("TYPE")) {
            Class<?> primitiveClass = switch (field.getDeclaringClass().toJavaName()) {
                case "java.lang.Boolean" -> boolean.class;
                case "java.lang.Character" -> char.class;
                case "java.lang.Float" -> float.class;
                case "java.lang.Double" -> double.class;
                case "java.lang.Byte" -> byte.class;
                case "java.lang.Short" -> short.class;
                case "java.lang.Integer" -> int.class;
                case "java.lang.Long" -> long.class;
                case "java.lang.Void" -> void.class;
                default -> null;
            };
            if (primitiveClass != null) {
                return new CompileTimeValueConstant<>(bci, primitiveClass);
            }
        }
        return top();
    }

    @Override
    protected void storeStaticField(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaField field, ReflectionAnalysisValue value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
        }
    }

    @Override
    protected void storeField(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaField field, ReflectionAnalysisValue object, ReflectionAnalysisValue value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
        }
    }

    @Override
    protected ReflectionAnalysisValue invokeMethod(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaMethod method, List<ReflectionAnalysisValue> operands) {
        for (ReflectionAnalysisValue operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> top());
            }
        }
        if (methodsMatch(method, getMethod(Class.class, "forName", String.class))) {
            if (operands.getFirst() instanceof CompileTimeValueConstant<?> c && c.getValue() instanceof String className) {
                return findClass(bci, className);
            }
        } else if (methodsMatch(method, getMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class))) {
            if (operands.getFirst() instanceof CompileTimeValueConstant<?> c && c.getValue() instanceof String className && operands.get(1) instanceof CompileTimeValueConstant<?>) {
                return findClass(bci, className);
            }
        }
        return top();
    }

    private static boolean methodsMatch(JavaMethod methodOne, JavaMethod methodTwo) {
        if (methodOne == null || methodTwo == null) {
            return false;
        }
        boolean ownerMatches = methodOne.getDeclaringClass().getName().equals(methodTwo.getDeclaringClass().getName());
        boolean nameMatches = methodOne.getName().equals(methodTwo.getName());
        boolean signatureMatches = methodOne.getSignature().toMethodDescriptor().equals(methodTwo.getSignature().toMethodDescriptor());
        return ownerMatches && nameMatches && signatureMatches;
    }

    private ReflectionAnalysisValue findClass(int bci, String className) {
        TypeResult<Class<?>> clazz = classLoader.findClass(className, false);
        if (clazz.isPresent()) {
            return new CompileTimeValueConstant<>(bci, clazz.get());
        } else {
            return top();
        }
    }

    @Override
    protected void invokeVoidMethod(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaMethod method, List<ReflectionAnalysisValue> operands) {
        for (ReflectionAnalysisValue operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> top());
            }
        }
    }

    @Override
    protected ReflectionAnalysisValue newArray(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaType type, List<ReflectionAnalysisValue> counts) {
        if (opcode == ANEWARRAY && counts.getFirst() instanceof CompileTimeValueConstant<?> size && type instanceof ResolvedJavaType) {
            int realSize = ((Number) size.getValue()).intValue();
            return new CompileTimeArrayConstant<>(bci, realSize, OriginalClassProvider.getJavaClass(type));
        } else {
            return top();
        }
    }

    @Override
    protected ReflectionAnalysisValue castCheckOperation(int opcode, int bci, AbstractFrame<ReflectionAnalysisValue> state, JavaType type, ReflectionAnalysisValue object) {
        if (opcode == CHECKCAST && object instanceof CompileTimeConstant constant && constant.getValue() == null) {
            return new CompileTimeValueConstant<>(bci, null);
        } else {
            return top();
        }
    }

    public ResolvedJavaMethod getMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getMethod(name, parameterTypes);
            return getProviders().getMetaAccess().lookupJavaMethod(method);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Marker interface for abstract values obtained during bytecode-level
     * constant reflection analysis.
     */
    public interface ReflectionAnalysisValue {

    }

    public static class NotACompileTimeConstant implements ReflectionAnalysisValue {

        @Override
        public String toString() {
            return "Not a compile time constant";
        }
    }

    public abstract static class CompileTimeConstant implements ReflectionAnalysisValue {

        private final int sourceBci;

        public CompileTimeConstant(int bci) {
            this.sourceBci = bci;
        }

        public int getSourceBci() {
            return sourceBci;
        }

        public abstract Object getValue();

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CompileTimeConstant that = (CompileTimeConstant) o;
            return sourceBci == that.sourceBci;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(sourceBci);
        }
    }

    public static class CompileTimeValueConstant<T> extends CompileTimeConstant {

        private final T value;

        public CompileTimeValueConstant(int bci, T value) {
            super(bci);
            this.value = value;
        }

        @Override
        public T getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "(" + getSourceBci() + ", " + getValue() + ")";
        }
    }

    public static class CompileTimeArrayConstant<T> extends CompileTimeConstant {

        /*
         * Sparse array representation to avoid possible large memory overheads
         * when analyzing an array initialization with a large size.
         */
        private final Map<Integer, T> value;
        private final int size;
        private final Class<T> elementType;

        public CompileTimeArrayConstant(int bci, int size, Class<T> elementType) {
            super(bci);
            this.value = new HashMap<>();
            this.size = size;
            this.elementType = elementType;
        }

        public CompileTimeArrayConstant(int bci, CompileTimeArrayConstant<T> arrayConstant) {
            super(bci);
            this.value = new HashMap<>(arrayConstant.value);
            this.size = arrayConstant.size;
            this.elementType = arrayConstant.elementType;
        }

        public void setElement(int index, Object element) throws ArrayIndexOutOfBoundsException, ClassCastException {
            if (index < 0 || index >= size) {
                throw new ArrayIndexOutOfBoundsException(index);
            }
            if (!elementType.isAssignableFrom(element.getClass())) {
                throw new ClassCastException(element.toString());
            }
            @SuppressWarnings("unchecked")
            T typedElement = (T) element;
            value.put(index, typedElement);
        }

        @Override
        public T[] getValue() {
            @SuppressWarnings("unchecked")
            T[] arrayValue = (T[]) Array.newInstance(elementType, size);
            for (Map.Entry<Integer, T> entry : value.entrySet()) {
                arrayValue[entry.getKey()] = entry.getValue();
            }
            return arrayValue;
        }

        @Override
        public String toString() {
            if (size >= 32) {
                return "(" + getSourceBci() + ", Array[" + size + "])";
            } else {
                return "(" + getSourceBci() + ", " + Arrays.toString(getValue()) + ")";
            }
        }
    }
}
