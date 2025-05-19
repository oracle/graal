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
package com.oracle.svm.hosted.strictconstantanalysis;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.java.dataflow.AbstractFrame;
import jdk.graal.compiler.java.dataflow.AbstractInterpreter;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static jdk.graal.compiler.bytecode.Bytecodes.ACONST_NULL;
import static jdk.graal.compiler.bytecode.Bytecodes.ANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.CHECKCAST;

public class ConstantExpressionAnalyzer extends AbstractInterpreter<ConstantExpressionAnalyzer.Value> {

    private static final NotACompileTimeConstant NOT_A_COMPILE_TIME_CONSTANT = new NotACompileTimeConstant();

    private final ImageClassLoader classLoader;

    public ConstantExpressionAnalyzer(CoreProviders providers, ImageClassLoader classLoader) {
        super(providers);
        this.classLoader = classLoader;
    }

    @Override
    protected Value top() {
        return NOT_A_COMPILE_TIME_CONSTANT;
    }

    @Override
    protected Value merge(Value left, Value right) {
        return left.equals(right) ? left : top();
    }

    @Override
    protected Value pushConstant(Context context, AbstractFrame<Value> state, Constant constant) {
        if (context.opcode() == ACONST_NULL) {
            return new CompileTimeValueConstant<>(context.bci(), null);
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
                return new CompileTimeValueConstant<>(context.bci(), javaValue);
            }
        }
        return top();
    }

    @Override
    protected Value pushType(Context context, AbstractFrame<Value> state, JavaType type) {
        if (type instanceof ResolvedJavaType resolvedType) {
            return new CompileTimeValueConstant<>(context.bci(), OriginalClassProvider.getJavaClass(resolvedType));
        } else {
            return top();
        }
    }

    @Override
    protected Value loadVariable(Context context, AbstractFrame<Value> state, int variableIndex, Value value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(context.bci(), constant.getValue());
        } else {
            return top();
        }
    }

    @Override
    protected Value storeVariable(Context context, AbstractFrame<Value> state, int variableIndex, Value value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(context.bci(), constant.getValue());
        } else if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
            return top();
        } else {
            return top();
        }
    }

    @Override
    protected void storeArrayElement(Context context, AbstractFrame<Value> state, Value array, Value index, Value value) {
        if (array instanceof CompileTimeArrayConstant<?> constantArray) {
            if (index instanceof CompileTimeValueConstant<?> constantIndex && value instanceof CompileTimeValueConstant<?> constantValue) {
                CompileTimeArrayConstant<?> newConstantArray = new CompileTimeArrayConstant<>(context.bci(), constantArray);
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
    protected Value loadStaticField(Context context, AbstractFrame<Value> state, JavaField field) {
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
                return new CompileTimeValueConstant<>(context.bci(), primitiveClass);
            }
        }
        return top();
    }

    @Override
    protected void storeStaticField(Context context, AbstractFrame<Value> state, JavaField field, Value value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
        }
    }

    @Override
    protected void storeField(Context context, AbstractFrame<Value> state, JavaField field, Value object, Value value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> top());
        }
    }

    @Override
    protected Value invokeMethod(Context context, AbstractFrame<Value> state, JavaMethod method, List<Value> operands) {
        for (Value operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> top());
            }
        }

        if (methodsMatch(method, getResolvedMethod(Class.class, "forName", String.class))) {
            String className = extractValue(operands.getFirst(), String.class);
            if (className != null) {
                return findClass(context, className);
            } else {
                return top();
            }
        } else if (methodsMatch(method, getResolvedMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class))) {
            String className = extractValue(operands.getFirst(), String.class);
            Integer initialize = extractValue(operands.get(1), Integer.class);
            if (className != null && initialize != null) {
                return findClass(context, className);
            } else {
                return top();
            }
        }

        /* Propagate Lookup and MethodType objects for use in MethodHandle lookups. */
        if (methodsMatch(method, getResolvedMethod(MethodType.class, "methodType", Class.class))) {
            return invokeMethod(context, getMethod(MethodType.class, "methodType", Class.class), operands);
        } else if (methodsMatch(method, getResolvedMethod(MethodType.class, "methodType", Class.class, Class.class))) {
            return invokeMethod(context, getMethod(MethodType.class, "methodType", Class.class, Class.class), operands);
        } else if (methodsMatch(method, getResolvedMethod(MethodType.class, "methodType", Class.class, Class[].class))) {
            return invokeMethod(context, getMethod(MethodType.class, "methodType", Class.class, Class[].class), operands);
        } else if (methodsMatch(method, getResolvedMethod(MethodType.class, "methodType", Class.class, Class.class, Class[].class))) {
            return invokeMethod(context, getMethod(MethodType.class, "methodType", Class.class, Class.class, Class[].class), operands);
        } else if (methodsMatch(method, getResolvedMethod(MethodType.class, "methodType", Class.class, MethodType.class))) {
            return invokeMethod(context, getMethod(MethodType.class, "methodType", Class.class, MethodType.class), operands);
        } else if (methodsMatch(method, getResolvedMethod(MethodHandles.class, "lookup"))) {
            return getLookup(context);
        } else if (methodsMatch(method, getResolvedMethod(MethodHandles.class, "privateLookupIn", Class.class, MethodHandles.Lookup.class))) {
            return invokeMethod(context, getMethod(MethodHandles.class, "privateLookupIn", Class.class, MethodHandles.Lookup.class), operands);
        }

        /*
         * Propagate Constructor objects for use in
         * sun.reflect.ReflectionFactory.newConstructorForSerialization.
         */
        if (methodsMatch(method, getResolvedMethod(Class.class, "getConstructor", Class[].class))) {
            return invokeMethod(context, getMethod(Class.class, "getConstructor", Class[].class), operands);
        } else if (methodsMatch(method, getResolvedMethod(Class.class, "getDeclaredConstructor", Class[].class))) {
            return invokeMethod(context, getMethod(Class.class, "getDeclaredConstructor", Class[].class), operands);
        }

        return top();
    }

    /**
     * Note that boolean, byte, char and short types are all represented as integers when using this
     * method.
     */
    private static <T> T extractValue(Value value, Class<T> type) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            Object extracted = constant.getValue();
            if (extracted != null && type.isAssignableFrom(extracted.getClass())) {
                return type.cast(extracted);
            }
        }
        return null;
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

    private static Method getMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere("Method " + owner.getName() + "." + name + " not found");
        }
    }

    private ResolvedJavaMethod getResolvedMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        return getProviders().getMetaAccess().lookupJavaMethod(getMethod(owner, name, parameterTypes));
    }

    private Value invokeMethod(Context context, Method method, List<Value> operands) {
        boolean hasReceiver = !Modifier.isStatic(method.getModifiers());
        Object receiver = null;
        if (hasReceiver) {
            Value aReceiver = operands.getFirst();
            if (aReceiver instanceof CompileTimeConstant constant) {
                receiver = constant.getValue();
            } else {
                return top();
            }
        }
        Object[] arguments = new Object[method.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            Value aArgument = operands.get(i + (hasReceiver ? 1 : 0));
            if (aArgument instanceof CompileTimeConstant constant) {
                arguments[i] = constant.getValue();
            } else {
                return top();
            }
        }
        try {
            return new CompileTimeValueConstant<>(context.bci(), method.invoke(receiver, arguments));
        } catch (Throwable t) {
            return top();
        }
    }

    private Value findClass(Context context, String className) {
        TypeResult<Class<?>> clazz = classLoader.findClass(className, false);
        if (clazz.isPresent()) {
            return new CompileTimeValueConstant<>(context.bci(), clazz.get());
        } else {
            return top();
        }
    }

    private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = ReflectionUtil.lookupConstructor(MethodHandles.Lookup.class, Class.class);

    private Value getLookup(Context context) {
        Class<?> callerClass = OriginalClassProvider.getJavaClass(context.method().getDeclaringClass());
        try {
            MethodHandles.Lookup lookup = LOOKUP_CONSTRUCTOR.newInstance(callerClass);
            return new CompileTimeValueConstant<>(context.bci(), lookup);
        } catch (Throwable t) {
            return top();
        }
    }

    @Override
    protected void invokeVoidMethod(Context context, AbstractFrame<Value> state, JavaMethod method, List<Value> operands) {
        for (Value operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> top());
            }
        }
    }

    @Override
    protected Value newArray(Context context, AbstractFrame<Value> state, JavaType type, List<Value> counts) {
        if (context.opcode() == ANEWARRAY && counts.getFirst() instanceof CompileTimeValueConstant<?> size && type instanceof ResolvedJavaType) {
            int realSize = ((Number) size.getValue()).intValue();
            return new CompileTimeArrayConstant<>(context.bci(), realSize, OriginalClassProvider.getJavaClass(type));
        } else {
            return top();
        }
    }

    @Override
    protected Value castCheckOperation(Context context, AbstractFrame<Value> state, JavaType type, Value object) {
        if (context.opcode() == CHECKCAST && object instanceof CompileTimeConstant constant && constant.getValue() == null) {
            return new CompileTimeValueConstant<>(context.bci(), null);
        } else {
            return top();
        }
    }

    /*
     * Looking up types/fields/methods through a WrappedConstantPool can result in
     * UnsupportedFeatureException(s) being thrown. To avoid these exceptions being thrown, we
     * essentially simulate the behavior of the WrappedConstantPool when looking up the underlying
     * JVMCI type/field/method, but skip the analysis (or hosted) universe lookup.
     */

    @Override
    protected JavaType lookupType(Bytecode code, int cpi, int opcode) {
        if (code.getMethod() instanceof WrappedJavaMethod wrapper) {
            ConstantPool constantPool = wrapper.getWrapped().getConstantPool();
            tryToResolve(constantPool, cpi, opcode);
            return constantPool.lookupType(cpi, opcode);
        } else {
            return super.lookupType(code, cpi, opcode);
        }
    }

    @Override
    protected JavaField lookupField(Bytecode code, int cpi, int opcode) {
        if (code.getMethod() instanceof WrappedJavaMethod wrapper) {
            ConstantPool constantPool = wrapper.getWrapped().getConstantPool();
            tryToResolve(constantPool, cpi, opcode);
            return constantPool.lookupField(cpi, OriginalMethodProvider.getOriginalMethod(code.getMethod()), opcode);
        } else {
            return super.lookupField(code, cpi, opcode);
        }
    }

    @Override
    protected JavaMethod lookupMethod(Bytecode code, int cpi, int opcode) {
        if (code.getMethod() instanceof WrappedJavaMethod wrapper) {
            ConstantPool constantPool = wrapper.getWrapped().getConstantPool();
            tryToResolve(constantPool, cpi, opcode);
            return constantPool.lookupMethod(cpi, opcode, OriginalMethodProvider.getOriginalMethod(code.getMethod()));
        } else {
            return super.lookupMethod(code, cpi, opcode);
        }
    }

    /**
     * Marker interface for abstract values obtained during bytecode-level constant expression
     * analysis.
     */
    public interface Value {

    }

    public static class NotACompileTimeConstant implements Value {

        @Override
        public String toString() {
            return "Not a compile time constant";
        }
    }

    public abstract static class CompileTimeConstant implements Value {

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
            /*
             * The source BCI (BCI of the instruction that placed the value onto the operand stack
             * or in the local variable table) is the source of truth when comparing two compile
             * time constant values (an equal source BCI implies an equal value).
             */
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
         * Sparse array representation to avoid possible large memory overheads when analyzing an
         * array initialization with a large size.
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
            value.put(index, elementType.cast(element));
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
