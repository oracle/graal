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
package com.oracle.svm.hosted.dynamicaccessinference;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.oracle.svm.util.OriginalClassProvider;
import com.oracle.svm.util.OriginalMethodProvider;
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.hosted.dynamicaccessinference.dataflow.AbstractFrame;
import com.oracle.svm.hosted.dynamicaccessinference.dataflow.AbstractInterpreter;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;

import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

import static jdk.graal.compiler.bytecode.Bytecodes.ACONST_NULL;
import static jdk.graal.compiler.bytecode.Bytecodes.INVOKEDYNAMIC;

/**
 * A bytecode-level constant expression analyzer for use in contexts which can affect native image
 * execution semantics, such as build-time inference of reflective calls as done by
 * {@link StrictDynamicAccessInferenceFeature}.
 * <p>
 * The analyzer builds {@link AbstractFrame abstract frames} for each bytecode instruction of the
 * analyzed method. The {@link ConstantExpressionAnalyzer.Value abstract values} stored in the
 * abstract frames can then be safely inferred at the point of execution of the corresponding
 * instruction if they are a subtype of {@link CompileTimeConstant}.
 */
final class ConstantExpressionAnalyzer extends AbstractInterpreter<ConstantExpressionAnalyzer.Value> {

    private static final NotACompileTimeConstant NOT_A_COMPILE_TIME_CONSTANT = new NotACompileTimeConstant();

    private final CoreProviders providers;
    private final ClassLoader classLoader;
    private final Map<Method, Function<InvocationData, Value>> propagatingMethods;

    ConstantExpressionAnalyzer(CoreProviders providers, ClassLoader classLoader) {
        this.providers = providers;
        this.classLoader = classLoader;
        this.propagatingMethods = buildPropagatingMethods();
    }

    @Override
    protected Value defaultValue() {
        return NOT_A_COMPILE_TIME_CONSTANT;
    }

    @Override
    protected Value merge(Value left, Value right) {
        if (left.equals(right)) {
            return left;
        } else {
            /*
             * In case we attempt to merge a compile-time array constant with another value, and
             * that merge fails, the array value can no longer be safely inferred as its reference
             * can no longer be tracked and could escape through the merged (not-a-constant) result.
             *
             * To handle this, a special FailedArrayMergeValue, which carries information on the
             * constant arrays used in the merge operation, is used as the merge result. Immediately
             * after the construction of the merged frame is finished, the failed merge value, as
             * well as the array operands it carries, are all marked as not-a-constant values
             * throughout the entire frame.
             */
            List<CompileTimeArrayConstant<?>> arraysToMerge = extractArrayConstants(left, right);
            if (!arraysToMerge.isEmpty()) {
                return new FailedArrayMergeValue(arraysToMerge);
            } else {
                return defaultValue();
            }
        }
    }

    private static List<CompileTimeArrayConstant<?>> extractArrayConstants(Value... values) {
        ArrayList<CompileTimeArrayConstant<?>> arrayConstants = new ArrayList<>();
        for (Value value : values) {
            if (value instanceof CompileTimeArrayConstant<?> constantArray) {
                arrayConstants.add(constantArray);
            }
        }
        return arrayConstants;
    }

    @Override
    protected AbstractFrame<Value> mergeStates(AbstractFrame<Value> left, AbstractFrame<Value> right) {
        AbstractFrame<Value> mergedStates = super.mergeStates(left, right);
        /*
         * If there were any failed attempts at merging compile-time constant arrays, we mark those
         * arrays as not constant.
         */
        mergedStates.transform(v -> v instanceof FailedArrayMergeValue, v -> {
            var failedArrayMerge = (FailedArrayMergeValue) v;
            for (CompileTimeArrayConstant<?> constantArray : failedArrayMerge.arraysToMerge()) {
                mergedStates.transform(arr -> arr.equals(constantArray), _ -> defaultValue());
            }
            return defaultValue();
        });
        return mergedStates;
    }

    @Override
    protected Value loadConstant(InstructionContext<Value> context, Constant constant) {
        if (context.opcode() == ACONST_NULL) {
            return new CompileTimeImmutableConstant<>(context.bci(), null);
        }
        if (constant instanceof JavaConstant javaConstant) {
            /*
             * The analyzer does not differentiate between boolean, byte, char, short and int
             * primitive type values.
             */
            Object javaValue = switch (javaConstant.getJavaKind().getStackKind()) {
                case Int -> javaConstant.asInt();
                case Long -> javaConstant.asLong();
                case Float -> javaConstant.asFloat();
                case Double -> javaConstant.asDouble();
                case Object -> providers.getSnippetReflection().asObject(Object.class, javaConstant);
                default -> null;
            };
            if (javaValue != null) {
                return new CompileTimeImmutableConstant<>(context.bci(), javaValue);
            }
        }
        return defaultValue();
    }

    @Override
    protected Value loadType(InstructionContext<Value> context, JavaType type) {
        if (type instanceof ResolvedJavaType resolvedType) {
            return new CompileTimeImmutableConstant<>(context.bci(), OriginalClassProvider.getJavaClass(resolvedType));
        } else {
            return defaultValue();
        }
    }

    @Override
    protected Value loadVariable(InstructionContext<Value> context, Value value) {
        if (value instanceof CompileTimeImmutableConstant<?> constant) {
            return new CompileTimeImmutableConstant<>(context.bci(), constant.getValue());
        } else {
            return defaultValue();
        }
    }

    @Override
    protected Value loadStaticField(InstructionContext<Value> context, JavaField field) {
        /*
         * Instead of compiling to an LDC instruction, class literals for primitive types (e.g.,
         * int.class) get compiled GETSTATIC instructions which reference the TYPE field of the
         * appropriate primitive type wrapper class.
         */
        if (field.getName().equals("TYPE")) {
            Class<?> primitiveClass = switch (field.getDeclaringClass().toJavaName()) {
                case "java.lang.Boolean" -> boolean.class;
                case "java.lang.Byte" -> byte.class;
                case "java.lang.Short" -> short.class;
                case "java.lang.Character" -> char.class;
                case "java.lang.Integer" -> int.class;
                case "java.lang.Long" -> long.class;
                case "java.lang.Float" -> float.class;
                case "java.lang.Double" -> double.class;
                case "java.lang.Void" -> void.class;
                default -> null;
            };
            if (primitiveClass != null) {
                return new CompileTimeImmutableConstant<>(context.bci(), primitiveClass);
            }
        }
        return defaultValue();
    }

    @Override
    protected Value storeVariable(InstructionContext<Value> context, Value value) {
        if (value instanceof CompileTimeImmutableConstant<?> constant) {
            return new CompileTimeImmutableConstant<>(context.bci(), constant.getValue());
        }
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            /*
             * Even though storing an array reference in a local variable doesn't cause it to
             * possibly escape to another method, we still disallow this when inferring constants in
             * order to avoid complicated Java-level definitions of when an array is considered
             * constant.
             *
             * Due to this rule, the only arrays we consider constant are the ones where their
             * initialization is directly used.
             */
            context.state().transform(v -> v.equals(constantArray), _ -> defaultValue());
        }
        return defaultValue();
    }

    @Override
    protected void storeArrayElement(InstructionContext<Value> context, Value array, Value index, Value value) {
        if (array instanceof CompileTimeArrayConstant<?> constantArray) {
            if (index instanceof CompileTimeImmutableConstant<?> constantIndex && value instanceof CompileTimeImmutableConstant<?> constantValue) {
                CompileTimeArrayConstant<?> newConstantArray = new CompileTimeArrayConstant<>(context.bci(), constantArray);
                try {
                    int realIndex = ((Number) constantIndex.getValue()).intValue();
                    newConstantArray.setElement(realIndex, constantValue.getValue());
                    context.state().transform(v -> v.equals(constantArray), _ -> newConstantArray);
                } catch (Exception e) {
                    context.state().transform(v -> v.equals(constantArray), _ -> defaultValue());
                }
            } else {
                context.state().transform(v -> v.equals(constantArray), _ -> defaultValue());
            }
        }
    }

    @Override
    protected Value invokeNonVoidMethod(InstructionContext<Value> context, JavaMethod method, Value receiver, List<Value> operands) {
        Method javaMethod = getJavaMethod(method);
        if (javaMethod == null) {
            /* The method is either unresolved, or is actually a constructor. */
            return defaultValue();
        }

        Function<InvocationData, Value> handler = propagatingMethods.get(javaMethod);
        return handler != null
                        ? handler.apply(new InvocationData(javaMethod, context, receiver, operands))
                        : defaultValue();
    }

    @Override
    protected Value newObjectArray(InstructionContext<Value> context, JavaType type, Value size) {
        if (size instanceof CompileTimeImmutableConstant<?> constantSize && type instanceof ResolvedJavaType) {
            int realSize = ((Number) constantSize.getValue()).intValue();
            return new CompileTimeArrayConstant<>(context.bci(), realSize, OriginalClassProvider.getJavaClass(type));
        } else {
            return defaultValue();
        }
    }

    @Override
    protected Value checkCast(InstructionContext<Value> context, JavaType type, Value object) {
        /*
         * A CHECKCAST instruction on a null value always succeeds and leaves the operand stack
         * unchanged. It is useful to consider this case a compile-time constant in order to be able
         * to infer code patterns such as "SomeClass.class.getMethod("someMethod", (Class[]) null);"
         * which are sometimes used.
         */
        if (object instanceof CompileTimeImmutableConstant<?> c && c.getValue() == null) {
            return new CompileTimeImmutableConstant<>(context.bci(), null);
        } else {
            return defaultValue();
        }
    }

    @Override
    protected void onValueEscape(InstructionContext<Value> context, Value value) {
        /*
         * Arrays are mutable, making any guarantees on the inferred value of the array void if a
         * reference to the array escapes to a different method. This can happen explicitly, i.e.,
         * by using a compile-time constant array as an argument to a method (from which it can
         * possibly be modified), or storing the reference in a field or array (and then get
         * accessed in other methods through those).
         */
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            context.state().transform(v -> v.equals(constantArray), _ -> defaultValue());
        }
    }

    private Map<Method, Function<InvocationData, Value>> buildPropagatingMethods() {
        return Map.ofEntries(
                        /* Propagate results of Class.forName invocations. */
                        Map.entry(ReflectionUtil.lookupMethod(Class.class, "forName", String.class), d -> invokeForNameOne(d.context, d.operands)),
                        Map.entry(ReflectionUtil.lookupMethod(Class.class, "forName", String.class, boolean.class, ClassLoader.class), d -> invokeForNameThree(d.context, d.operands)),

                        /*
                         * Propagate Class.getClassLoader for use in Class.forName(String, boolean,
                         * ClassLoader).
                         */
                        Map.entry(ReflectionUtil.lookupMethod(Class.class, "getClassLoader"), this::invokeMethod),

                        /* Propagate MethodType objects for use in MethodHandle lookups. */
                        Map.entry(ReflectionUtil.lookupMethod(MethodType.class, "methodType", Class.class), this::invokeMethod),
                        Map.entry(ReflectionUtil.lookupMethod(MethodType.class, "methodType", Class.class, Class.class), this::invokeMethod),
                        Map.entry(ReflectionUtil.lookupMethod(MethodType.class, "methodType", Class.class, Class[].class), this::invokeMethod),
                        Map.entry(ReflectionUtil.lookupMethod(MethodType.class, "methodType", Class.class, Class.class, Class[].class), this::invokeMethod),
                        Map.entry(ReflectionUtil.lookupMethod(MethodType.class, "methodType", Class.class, MethodType.class), this::invokeMethod),

                        /* Propagate Lookup objects for use in MethodHandle lookups. */
                        Map.entry(ReflectionUtil.lookupMethod(MethodHandles.class, "lookup"), d -> getLookup(d.context)),
                        Map.entry(ReflectionUtil.lookupMethod(MethodHandles.class, "privateLookupIn", Class.class, MethodHandles.Lookup.class), this::invokeMethod));
    }

    private record InvocationData(Method method, InstructionContext<Value> context, Value receiver, List<Value> operands) {

    }

    private static Method getJavaMethod(JavaMethod method) {
        if (method instanceof ResolvedJavaMethod resolved) {
            Executable executable = OriginalMethodProvider.getJavaMethod(resolved);
            if (executable instanceof Method m) {
                return m;
            }
        }
        return null;
    }

    /**
     * Note that boolean, byte, char and short types are all represented as int when using this
     * method.
     */
    private static <T> T extractValue(Value value, Class<T> type) {
        if (value instanceof CompileTimeImmutableConstant<?> constant) {
            Object extracted = constant.getValue();
            if (extracted != null && type.isAssignableFrom(extracted.getClass())) {
                return type.cast(extracted);
            }
        }
        return null;
    }

    private Value invokeMethod(InvocationData invocationData) {
        boolean hasReceiver = !Modifier.isStatic(invocationData.method.getModifiers());
        Object receiver = null;
        if (hasReceiver) {
            if (invocationData.receiver() instanceof CompileTimeConstant constant) {
                receiver = constant.getValue();
            } else {
                return defaultValue();
            }
        }
        assert invocationData.method().getParameterCount() == invocationData.operands.size();
        Object[] arguments = new Object[invocationData.method.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            if (invocationData.operands.get(i) instanceof CompileTimeConstant constant) {
                arguments[i] = constant.getValue();
            } else {
                return defaultValue();
            }
        }
        try {
            Object result = invocationData.method.invoke(receiver, arguments);
            return new CompileTimeImmutableConstant<>(invocationData.context.bci(), result);
        } catch (Throwable t) {
            return defaultValue();
        }
    }

    private Value invokeForNameOne(InstructionContext<Value> context, List<Value> operands) {
        String className = extractValue(operands.getFirst(), String.class);
        if (className == null) {
            return defaultValue();
        }
        ClassLoader loader = ClassForNameSupport.respectClassLoader()
                        ? OriginalClassProvider.getJavaClass(context.method().getDeclaringClass()).getClassLoader()
                        : classLoader;
        return findClass(context, className, loader);
    }

    private Value invokeForNameThree(InstructionContext<Value> context, List<Value> operands) {
        String className = extractValue(operands.getFirst(), String.class);
        Integer initialize = extractValue(operands.get(1), Integer.class);
        if (className == null || initialize == null) {
            return defaultValue();
        }
        ClassLoader loader;
        if (ClassForNameSupport.respectClassLoader()) {
            if (operands.get(2) instanceof CompileTimeImmutableConstant<?> constant) {
                loader = (ClassLoader) constant.getValue();
            } else {
                return defaultValue();
            }
        } else {
            loader = classLoader;
        }
        return findClass(context, className, loader);
    }

    private Value findClass(InstructionContext<Value> context, String className, ClassLoader loader) {
        TypeResult<Class<?>> clazz = ImageClassLoader.findClass(className, false, loader);
        if (clazz.isPresent()) {
            return new CompileTimeImmutableConstant<>(context.bci(), clazz.get());
        } else {
            return defaultValue();
        }
    }

    private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = ReflectionUtil.lookupConstructor(MethodHandles.Lookup.class, Class.class);

    private Value getLookup(InstructionContext<Value> context) {
        Class<?> callerClass = OriginalClassProvider.getJavaClass(context.method().getDeclaringClass());
        try {
            MethodHandles.Lookup lookup = LOOKUP_CONSTRUCTOR.newInstance(callerClass);
            return new CompileTimeImmutableConstant<>(context.bci(), lookup);
        } catch (Throwable t) {
            return defaultValue();
        }
    }

    /**
     * Looking up constants/types/fields/methods through a {@link WrappedConstantPool} can result in
     * {@link com.oracle.graal.pointsto.constraints.UnsupportedFeatureException
     * UnsupportedFeatureException(s)} being thrown. To avoid this, we simulate the behavior of the
     * {@link WrappedConstantPool} when looking up the underlying JVMCI constant/type/field/method,
     * but skip the analysis (or hosted) universe lookup.
     */
    private static ConstantPool unwrapIfWrapped(ConstantPool constantPool) {
        return constantPool instanceof WrappedConstantPool wrapper
                        ? wrapper.getWrapped()
                        : constantPool;
    }

    private static ResolvedJavaMethod getOriginalIfWrapped(ResolvedJavaMethod method) {
        return method instanceof WrappedJavaMethod
                        ? OriginalMethodProvider.getOriginalMethod(method)
                        : method;
    }

    @Override
    protected Object lookupConstant(ConstantPool constantPool, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return unwrapIfWrapped(constantPool).lookupConstant(cpi, false);
    }

    @Override
    protected JavaType lookupType(ConstantPool constantPool, int cpi, int opcode) {
        tryToResolve(constantPool, cpi, opcode);
        return unwrapIfWrapped(constantPool).lookupType(cpi, opcode);
    }

    @Override
    protected JavaMethod lookupMethod(ConstantPool constantPool, int cpi, int opcode, ResolvedJavaMethod caller) {
        /*
         * Resolving the call site reference for an indy can result in the bootstrap method being
         * executed at build-time, which should be avoided in the general case.
         */
        if (opcode != INVOKEDYNAMIC) {
            tryToResolve(constantPool, cpi, opcode);
        }
        return unwrapIfWrapped(constantPool).lookupMethod(cpi, opcode, getOriginalIfWrapped(caller));
    }

    @Override
    protected JavaConstant lookupAppendix(ConstantPool constantPool, int cpi, int opcode) {
        return unwrapIfWrapped(constantPool).lookupAppendix(cpi, opcode);
    }

    @Override
    protected JavaField lookupField(ConstantPool constantPool, int cpi, int opcode, ResolvedJavaMethod caller) {
        return unwrapIfWrapped(constantPool).lookupField(cpi, getOriginalIfWrapped(caller), opcode);
    }

    private static void tryToResolve(ConstantPool constantPool, int cpi, int opcode) {
        try {
            constantPool.loadReferencedType(cpi, opcode, false);
        } catch (Throwable t) {
            // Ignore and leave the type unresolved.
        }
    }

    /**
     * Marker interface for abstract values obtained during bytecode-level constant expression
     * inference.
     */
    interface Value {

    }

    /**
     * A value for which the value can be inferred at build-time is considered a
     * {@link CompileTimeConstant compile-time constant}. Each such value is represented by a pair
     * (source BCI, inferred value), where the BCI component represents the bytecode offset of the
     * instruction which last placed/modified the value in the abstract frame, and the inferred
     * value component represents the actual Java value as would be observed at run-time.
     * <p>
     * An example of such a value would be a String {@code "SomeString"} pushed onto the operand
     * stack by an LDC instruction at BCI 42 - the corresponding compile-time constant in that case
     * would be the pair {@code (42, "SomeString")}.
     */
    abstract static class CompileTimeConstant implements Value {

        private final int sourceBci;

        CompileTimeConstant(int bci) {
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

    /**
     * A special value representing unsuccessful merging (into a compile-time constant) of one or
     * more {@link CompileTimeArrayConstant constant arrays} with other values.
     */
    private record FailedArrayMergeValue(List<CompileTimeArrayConstant<?>> arraysToMerge) implements Value {

    }

    private static final class NotACompileTimeConstant implements Value {

        @Override
        public String toString() {
            return "Not a compile-time constant";
        }
    }

    /**
     * Values of certain types (e.g., strings) are immutable, meaning that their inferred value is
     * guaranteed to remain valid even if a reference to such a value escapes the analyzed method
     * (by using it as an argument to a method, storing it in a field, etc.).
     */
    private static final class CompileTimeImmutableConstant<T> extends CompileTimeConstant {

        private final T value;

        CompileTimeImmutableConstant(int bci, T value) {
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

    /**
     * Unlike {@link CompileTimeImmutableConstant immutable constants}, arrays are always mutable.
     * If a reference to an array escapes the analyzed method, it can arbitrarily be modified
     * outside of it. Because of this, inferred array values require special handling and are
     * subject to certain restrictions in the inference scheme, such as only being able to be used
     * as method argument once before no longer being considered a compile-time constant.
     */
    private static final class CompileTimeArrayConstant<T> extends CompileTimeConstant {

        /*
         * Sparse array representation to avoid possible large memory overhead when analyzing an
         * array initialization with a large size.
         */
        private final Map<Integer, T> value;
        private final int size;
        private final Class<T> elementType;

        CompileTimeArrayConstant(int bci, int size, Class<T> elementType) {
            super(bci);
            this.value = new HashMap<>();
            this.size = size;
            this.elementType = elementType;
        }

        CompileTimeArrayConstant(int bci, CompileTimeArrayConstant<T> arrayConstant) {
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
