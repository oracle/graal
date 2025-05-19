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
import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.infrastructure.WrappedJavaMethod;
import com.oracle.svm.core.hub.ClassForNameSupport;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.TypeResult;
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
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static jdk.graal.compiler.bytecode.Bytecodes.ACONST_NULL;
import static jdk.graal.compiler.bytecode.Bytecodes.ANEWARRAY;
import static jdk.graal.compiler.bytecode.Bytecodes.CHECKCAST;

/**
 * A bytecode-level constant expression analyzer for use in contexts which can affect native image
 * execution semantics, such as build-time folding of reflective calls
 * {@link StrictConstantAnalysisFeature}.
 * <p>
 * The analyzer builds {@link AbstractFrame abstract frames} for each bytecode instruction of the
 * analyzed method. The {@link ConstantExpressionAnalyzer.Value abstract values} in stored in the
 * abstract frames can be either:
 * <ul>
 * <li>{@link NotACompileTimeConstant} - represents a value which cannot be inferred by the
 * analysis</li>
 * <li>{@link CompileTimeConstant} - a value inferrable by the analysis</li>
 * </ul>
 * Furthermore, the inferrable values can be either:
 * <ul>
 * <li>{@link CompileTimeValueConstant} - an inferrable value which is not an array</li>
 * <li>{@link CompileTimeArrayConstant} - an inferrable array</li>
 * </ul>
 * Each {@link CompileTimeConstant} is represented by a pair (source BCI, inferred value). The
 * source BCI represents the BCI (bytecode offset) of the instruction which last placed the constant
 * into the abstract frame, while the inferred value represents the actual Java value which would be
 * observed during the run-time execution of the corresponding instruction.
 */
public final class ConstantExpressionAnalyzer extends AbstractInterpreter<ConstantExpressionAnalyzer.Value> {

    private static final NotACompileTimeConstant NOT_A_COMPILE_TIME_CONSTANT = new NotACompileTimeConstant();

    private final ImageClassLoader classLoader;
    private final Map<Method, Function<InvocationData, Value>> propagatingMethods;

    public ConstantExpressionAnalyzer(CoreProviders providers, ImageClassLoader classLoader) {
        super(providers);
        this.classLoader = classLoader;
        this.propagatingMethods = buildPropagatingMethods();
    }

    @Override
    protected Value bottom() {
        return NOT_A_COMPILE_TIME_CONSTANT;
    }

    @Override
    protected Value merge(Value left, Value right) {
        return left.equals(right) ? left : bottom();
    }

    /**
     * Propagation of constants usually begins with instructions which push constants onto the
     * operand stack, such as ACONST_NULL, LDC or ICONST_2.
     * <p>
     * For example, an LDC instruction at BCI 6 and referencing a String constant pool entry
     * "SomeValue" would push a (6, "SomeValue") {@link CompileTimeValueConstant compile-time
     * constant} onto the abstract operand stack.
     */
    @Override
    protected Value pushConstant(Context context, AbstractFrame<Value> state, Constant constant) {
        if (context.opcode() == ACONST_NULL) {
            return new CompileTimeValueConstant<>(context.bci(), null);
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
                case Object -> getProviders().getSnippetReflection().asObject(Object.class, javaConstant);
                default -> null;
            };
            if (javaValue != null) {
                return new CompileTimeValueConstant<>(context.bci(), javaValue);
            }
        }
        return bottom();
    }

    /**
     * As with {@link ConstantExpressionAnalyzer#pushConstant(Context, AbstractFrame, Constant)},
     * load constant instructions referencing types in the constant pool push the appropriate
     * {@link java.lang.Class} objects onto the abstract operand stack. If the reference to the type
     * cannot be resolved, a {@link ConstantExpressionAnalyzer.NotACompileTimeConstant} is pushed
     * onto the stack instead.
     */
    @Override
    protected Value pushType(Context context, AbstractFrame<Value> state, JavaType type) {
        if (type instanceof ResolvedJavaType resolvedType) {
            return new CompileTimeValueConstant<>(context.bci(), OriginalClassProvider.getJavaClass(resolvedType));
        } else {
            return bottom();
        }
    }

    /**
     * Variable load instructions, e.g., ALOAD, propagate constants in the local variable table
     * entry they point to hold a {@link CompileTimeValueConstant non-array type compile-time
     * constant}. The inferred value is the same as of the local variable table entry, but the
     * source BCI is changed to the BCI of the load instruction.
     * <p>
     * For example, an ALOAD instruction at BCI 37 referencing a local variable table entry holding
     * a compile-time constant (13, "SomeValue") would push a (37, "SomeValue") compile-time
     * constant onto the abstract operand stack.
     */
    @Override
    protected Value loadVariable(Context context, AbstractFrame<Value> state, int variableIndex, Value value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(context.bci(), constant.getValue());
        } else {
            return bottom();
        }
    }

    /**
     * Variable store instructions, e.g., ASTORE, propagate constants if their operand is a
     * {@link CompileTimeValueConstant non-array type compile-time constant}. The inferred value is
     * the same as of its operand, but the source BCI is changed to the BCI of the store
     * instruction.
     * <p>
     * For example, an ASTORE instruction at BCI 13 with compile-time constant operand (9,
     * "SomeValue") would store a (13, "SomeValue") compile-time constant into the abstract local
     * variable table.
     * <p>
     * In case the operand of the store instruction is an {@link CompileTimeArrayConstant array type
     * compile-time constant}, all references to that array on in the abstract frame are marked as
     * non-compile-time constants.
     * <p>
     * For example, if an array type compile-time constant (14, [int.class, String.class]) is the
     * operand of the store instruction, all compile-time constants with source BCI of 14 in the
     * {@code state} are transformed to non-compile-time constants.
     */
    @Override
    protected Value storeVariable(Context context, AbstractFrame<Value> state, int variableIndex, Value value) {
        if (value instanceof CompileTimeValueConstant<?> constant) {
            return new CompileTimeValueConstant<>(context.bci(), constant.getValue());
        } else if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> bottom());
            return bottom();
        } else {
            return bottom();
        }
    }

    /**
     * In case of an array store instruction with all compile-time constant operands (the array
     * reference, the array index and the element to store), all the compile-time constants in the
     * abstract frame which correspond to the array reference, i.e., have the same source BCI as the
     * array reference compile-time constant, are modified by storing the value of the constant
     * element into the appropriate position of their underlying inferred value array.
     * <p>
     * Likewise, having a compile-time constant array reference operand, but with either the array
     * index or element operand not being compile-time constants, all the corresponding compile-time
     * constant arrays in the abstract frame are marked as {@link NotACompileTimeConstant not a
     * compile time constant}.
     */
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
                    state.transform(v -> v.equals(constantArray), v -> bottom());
                }
            } else {
                state.transform(v -> v.equals(constantArray), v -> bottom());
            }
        }
    }

    /**
     * In order to propagate {@link java.lang.Class} instances of primitive types, GETSTATIC
     * instructions referencing the TYPE field of the appropriate wrapper class, e.g.,
     * {@link java.lang.Integer#TYPE}, push a compile-time constant carrying the corresponding
     * {@link java.lang.Class} object.
     * <p>
     * For example, a GETSTATIC instruction at BCI 42 and referencing the
     * {@link java.lang.Integer#TYPE} field would push a (42, int.class)
     * {@link CompileTimeValueConstant compile-time constant} onto the abstract operand stack.
     */
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
        return bottom();
    }

    /**
     * To prevent escaping of array references and their modification through fields interfering
     * with the analysis, having a PUTSTATIC instruction with a {@link CompileTimeArrayConstant
     * compile-time array constant} operand marks all the corresponding compile-time constant arrays
     * in the abstract frame as {@link NotACompileTimeConstant not a compile time constant}.
     */
    @Override
    protected void storeStaticField(Context context, AbstractFrame<Value> state, JavaField field, Value value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> bottom());
        }
    }

    /**
     * To prevent escaping of array references and their modification through fields interfering
     * with the analysis, having a PUTFIELD instruction with a {@link CompileTimeArrayConstant
     * compile-time array constant} operand marks all the corresponding compile-time constant arrays
     * in the abstract frame as {@link NotACompileTimeConstant not a compile time constant}.
     */
    @Override
    protected void storeField(Context context, AbstractFrame<Value> state, JavaField field, Value object, Value value) {
        if (value instanceof CompileTimeArrayConstant<?> constantArray) {
            state.transform(v -> v.equals(constantArray), v -> bottom());
        }
    }

    /**
     * The results of certain method invocations, if they are successful, i.e., they do not throw an
     * exception, are propagated by pushing the result onto the abstract operand stack as a
     * compile-time constant. The methods which are propagated through are defined by
     * {@link ConstantExpressionAnalyzer#propagatingMethods}.
     * <p>
     * To prevent escaping of array references and their modification in different methods
     * interfering with the analysis, having a method invocation instruction
     * {@link CompileTimeArrayConstant compile-time array constant} operand marks all the
     * corresponding compile-time constant arrays in the abstract frame as
     * {@link NotACompileTimeConstant not a compile time constant}.
     */
    @Override
    protected Value invokeMethod(Context context, AbstractFrame<Value> state, JavaMethod method, List<Value> operands) {
        for (Value operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> bottom());
            }
        }

        Method javaMethod = getJavaMethod(method);
        if (javaMethod == null) {
            /* The method is either unresolved, or is actually a constructor. */
            return bottom();
        }

        Function<InvocationData, Value> handler = propagatingMethods.get(javaMethod);
        return handler != null
                        ? handler.apply(new InvocationData(javaMethod, context, operands))
                        : bottom();
    }

    /**
     * To prevent escaping of array references and their modification in different methods
     * interfering with the analysis, having a method invocation instruction
     * {@link CompileTimeArrayConstant compile-time array constant} operand marks all the
     * corresponding compile-time constant arrays in the abstract frame as
     * {@link NotACompileTimeConstant not a compile time constant}.
     */
    @Override
    protected void invokeVoidMethod(Context context, AbstractFrame<Value> state, JavaMethod method, List<Value> operands) {
        for (Value operand : operands) {
            if (operand instanceof CompileTimeArrayConstant<?> constantArray) {
                state.transform(v -> v.equals(constantArray), v -> bottom());
            }
        }
    }

    /**
     * An ANEWARRAY instruction with a {@link CompileTimeValueConstant compile-time constant} array
     * size operand creates a {@link CompileTimeArrayConstant compile-time array constant} and
     * pushes it onto the abstract operand stack.
     */
    @Override
    protected Value newArray(Context context, AbstractFrame<Value> state, JavaType type, List<Value> counts) {
        if (context.opcode() == ANEWARRAY && counts.getFirst() instanceof CompileTimeValueConstant<?> size && type instanceof ResolvedJavaType) {
            int realSize = ((Number) size.getValue()).intValue();
            return new CompileTimeArrayConstant<>(context.bci(), realSize, OriginalClassProvider.getJavaClass(type));
        } else {
            return bottom();
        }
    }

    /**
     * CHECKCAST instructions with a {@link CompileTimeConstant compile-time constant} operand which
     * are inferred to be null propagate that null value compile-time constant.
     * <p>
     * For example, a CHECKCAST instruction at BCI 8 with a compile-time constant operand (7, null)
     * will push a (8, null) compile-time constant onto the abstract operand stack.
     */
    @Override
    protected Value castCheckOperation(Context context, AbstractFrame<Value> state, JavaType type, Value object) {
        if (context.opcode() == CHECKCAST && object instanceof CompileTimeConstant constant && constant.getValue() == null) {
            return new CompileTimeValueConstant<>(context.bci(), null);
        } else {
            return bottom();
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

    private record InvocationData(Method method, Context context, List<Value> operands) {

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
        if (value instanceof CompileTimeValueConstant<?> constant) {
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
            Value aReceiver = invocationData.operands.getFirst();
            if (aReceiver instanceof CompileTimeConstant constant) {
                receiver = constant.getValue();
            } else {
                return bottom();
            }
        }
        Object[] arguments = new Object[invocationData.method.getParameterCount()];
        for (int i = 0; i < arguments.length; i++) {
            Value aArgument = invocationData.operands.get(i + (hasReceiver ? 1 : 0));
            if (aArgument instanceof CompileTimeConstant constant) {
                arguments[i] = constant.getValue();
            } else {
                return bottom();
            }
        }
        try {
            return new CompileTimeValueConstant<>(invocationData.context.bci(), invocationData.method.invoke(receiver, arguments));
        } catch (Throwable t) {
            return bottom();
        }
    }

    private Value invokeForNameOne(Context context, List<Value> operands) {
        String className = extractValue(operands.getFirst(), String.class);
        if (className == null) {
            return bottom();
        }
        ClassLoader loader = ClassForNameSupport.respectClassLoader()
                        ? OriginalClassProvider.getJavaClass(context.method().getDeclaringClass()).getClassLoader()
                        : classLoader.getClassLoader();
        return findClass(context, className, loader);
    }

    private Value invokeForNameThree(Context context, List<Value> operands) {
        String className = extractValue(operands.getFirst(), String.class);
        Integer initialize = extractValue(operands.get(1), Integer.class);
        if (className == null || initialize == null) {
            return bottom();
        }
        ClassLoader loader;
        if (ClassForNameSupport.respectClassLoader()) {
            if (operands.get(2) instanceof CompileTimeValueConstant<?> constant) {
                loader = (ClassLoader) constant.getValue();
            } else {
                return bottom();
            }
        } else {
            loader = classLoader.getClassLoader();
        }
        return findClass(context, className, loader);
    }

    private Value findClass(Context context, String className, ClassLoader loader) {
        TypeResult<Class<?>> clazz = ImageClassLoader.findClass(className, false, loader);
        if (clazz.isPresent()) {
            return new CompileTimeValueConstant<>(context.bci(), clazz.get());
        } else {
            return bottom();
        }
    }

    private static final Constructor<MethodHandles.Lookup> LOOKUP_CONSTRUCTOR = ReflectionUtil.lookupConstructor(MethodHandles.Lookup.class, Class.class);

    private Value getLookup(Context context) {
        Class<?> callerClass = OriginalClassProvider.getJavaClass(context.method().getDeclaringClass());
        try {
            MethodHandles.Lookup lookup = LOOKUP_CONSTRUCTOR.newInstance(callerClass);
            return new CompileTimeValueConstant<>(context.bci(), lookup);
        } catch (Throwable t) {
            return bottom();
        }
    }

    /**
     * Looking up constants/types/fields/methods through a {@link WrappedConstantPool} can result in
     * UnsupportedFeatureException(s) being thrown. To avoid these exceptions, we essentially
     * simulate the behavior of the WrappedConstantPool when looking up the underlying JVMCI
     * constant/type/field/method, but skip the analysis (or hosted) universe lookup.
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
        return super.lookupConstant(unwrapIfWrapped(constantPool), cpi, opcode);
    }

    @Override
    protected JavaType lookupType(ConstantPool constantPool, int cpi, int opcode) {
        return super.lookupType(unwrapIfWrapped(constantPool), cpi, opcode);
    }

    @Override
    protected JavaField lookupField(ConstantPool constantPool, ResolvedJavaMethod method, int cpi, int opcode) {
        return super.lookupField(unwrapIfWrapped(constantPool), getOriginalIfWrapped(method), cpi, opcode);
    }

    @Override
    protected JavaMethod lookupMethod(ConstantPool constantPool, ResolvedJavaMethod method, int cpi, int opcode) {
        return super.lookupMethod(unwrapIfWrapped(constantPool), getOriginalIfWrapped(method), cpi, opcode);
    }

    @Override
    protected JavaConstant lookupAppendix(ConstantPool constantPool, int cpi, int opcode) {
        return super.lookupAppendix(unwrapIfWrapped(constantPool), cpi, opcode);
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
            return "Not a compile-time constant";
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

        /**
         * The source BCI (BCI of the instruction that placed the value onto the operand stack or in
         * the local variable table) is the source of truth when comparing two compile time constant
         * values (an equal source BCI implies an equal value).
         */
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
