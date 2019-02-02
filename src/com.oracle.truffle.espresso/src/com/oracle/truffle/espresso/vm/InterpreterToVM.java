/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.vm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.impl.ByteString;
import com.oracle.truffle.espresso.impl.ByteString.Name;
import com.oracle.truffle.espresso.impl.ByteString.Signature;
import com.oracle.truffle.espresso.impl.ByteString.Type;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.EspressoRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicRootNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Class;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_ClassLoader;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Object;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Package;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Runtime;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_System;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_reflect_Array;
import com.oracle.truffle.espresso.substitutions.Target_java_security_AccessController;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_Perf;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_Signal;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_URLClassPath;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_Unsafe;
import com.oracle.truffle.espresso.substitutions.Target_sun_misc_VM;
import com.oracle.truffle.espresso.substitutions.Target_sun_reflect_NativeMethodAccessorImpl;

import sun.misc.Unsafe;

public final class InterpreterToVM implements ContextAccess {

    private final EspressoContext context;

    @Override
    public EspressoContext getContext() {
        return context;
    }

    private static Unsafe hostUnsafe;

    static {
        try {
            java.lang.reflect.Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            hostUnsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private final Map<MethodKey, EspressoRootNode> substitutions = new HashMap<>();

    public static List<Class<?>> DEFAULTS = Arrays.asList(
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Object.class,
                    Target_java_lang_Package.class,
                    Target_java_lang_Runtime.class,
                    Target_java_lang_System.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeMethodAccessorImpl.class);

    private InterpreterToVM(EspressoContext context, List<Class<?>> substitutions) {
        this.context = context;
        for (Class<?> clazz : substitutions) {
            registerSubstitutions(clazz);
        }
    }

    public InterpreterToVM(EspressoContext context) {
        this(context, DEFAULTS);
    }

    public StaticObject intern(StaticObject obj) {
        assert obj.getKlass().equals(obj.getKlass().getContext().getMeta().String);
        return obj.getKlass().getContext().getStrings().intern(obj);
    }

    private static MethodKey getMethodKey(Method method) {
        return new MethodKey(
                        method.getDeclaringKlass(),
                        method.getName(),
                        method.getRawSignature());
    }

    @TruffleBoundary
    public EspressoRootNode getSubstitution(Method method) {
        assert method != null;
        return substitutions.get(getMethodKey(method));
    }

    private static final class MethodKey {
        private final String clazz;
        private final String methodName;
        private final String signature;

        public MethodKey(String clazz, String methodName, String signature) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MethodKey methodKey = (MethodKey) o;
            return Objects.equals(clazz, methodKey.clazz) &&
                            Objects.equals(methodName, methodKey.methodName) &&
                            Objects.equals(signature, methodKey.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, methodName, signature);
        }

        @Override
        public String toString() {
            return "MethodKey{" +
                            "clazz='" + clazz + '\'' +
                            ", methodName='" + methodName + '\'' +
                            ", signature='" + signature + '\'' +
                            '}';
        }
    }

    public static String fixTypeName(String type) {
        if ((type.startsWith("L") && type.endsWith(";"))) {
            return type;
        }

        if (type.startsWith("[")) {
            return type.replace('.', '/');
        }

        if (type.endsWith("[]")) {
            return "[" + fixTypeName(type.substring(0, type.length() - 2));
        }

        switch (type) {
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "void":
                return "V";
            default:
                return "L" + type.replace('.', '/') + ";";
        }
    }

    public void registerSubstitutions(Class<?> clazz) {

        String className;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoSubstitutions.class).value();
        if (annotatedClass == EspressoSubstitutions.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            className = MetaUtil.toInternalName(clazz.getSimpleName().substring("Target_".length()).replace('_', '.'));
        } else {
            throw EspressoError.shouldNotReachHere("Substitutions class must be decorated with @" + EspressoSubstitutions.class.getName());
        }
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            Substitution substitution = method.getAnnotation(Substitution.class);
            if (substitution == null) {
                continue;
            }

            RootNode rootNode = createRootNodeForMethod(getContext(), method);
            StringBuilder signature = new StringBuilder("(");
            java.lang.reflect.Parameter[] parameters = method.getParameters();
            for (int i = substitution.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                java.lang.reflect.Parameter parameter = parameters[i];
                String parameterTypeName;
                Host annotatedType = parameter.getAnnotatedType().getAnnotation(Host.class);
                if (annotatedType != null) {
                    parameterTypeName = annotatedType.value().getName();
                } else {
                    parameterTypeName = parameter.getType().getName();
                }
                signature.append(fixTypeName(parameterTypeName));
            }
            signature.append(')');

            Host annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Host.class);
            String returnTypeName;
            if (annotatedReturnType != null) {
                returnTypeName = annotatedReturnType.value().getName();
            } else {
                returnTypeName = method.getReturnType().getName();
            }
            signature.append(fixTypeName(returnTypeName));

            String methodName = substitution.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            registerSubstitution(fixTypeName(className), methodName, signature.toString(), rootNode, false);
        }
    }

    private static RootNode createRootNodeForMethod(java.lang.reflect.Method method) {
        if (!EspressoOptions.RUNNING_ON_SVM && context.getEnv().getOptions().get(EspressoOptions.IntrinsicsViaMethodHandles)) {
            MethodHandle handle;
            try {
                handle = MethodHandles.publicLookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            return new IntrinsicRootNode(language, handle);
        } else {
            return new IntrinsicReflectionRootNode(language, method);
        }
    }

    public void registerSubstitution(ByteString<Type> clazz, ByteString<Name> methodName, ByteString<Signature> signature, RootNode intrinsic, boolean update) {
        MethodKey key = new MethodKey(clazz, methodName, signature);
        assert intrinsic != null;
        if (update || !substitutions.containsKey(key)) {
            // assert !substitutions.containsKey(key) : key + " intrinsic is already registered";
            substitutions.put(key, intrinsic);
        }
    }

    // region Get (array) operations

    public int getArrayInt(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<int[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public StaticObject getArrayObject(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<StaticObject[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public long getArrayLong(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<long[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public float getArrayFloat(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<float[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public double getArrayDouble(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<double[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public byte getArrayByte(int index, StaticObject arr) {
        Object raw = ((StaticObjectArray) arr).unwrap();
        try {
            if (raw instanceof boolean[]) {
                return (byte) (((boolean[]) raw)[index] ? 1 : 0);
            }
            return ((byte[]) raw)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public char getArrayChar(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<char[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public short getArrayShort(int index, StaticObject arr) {
        try {
            return (((StaticObjectArray) arr).<short[]> unwrap())[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }
    // endregion

    // region Set (array) operations
    public void setArrayInt(int value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<int[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayLong(long value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<long[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayFloat(float value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<float[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayDouble(double value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<double[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayByte(byte value, int index, StaticObject arr) {
        Object raw = ((StaticObjectArray) arr).unwrap();
        try {
            if (raw instanceof boolean[]) {
                assert value == 0 || value == 1;
                ((boolean[]) raw)[index] = (value != 0);
            } else {
                ((byte[]) raw)[index] = value;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayChar(char value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<char[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayShort(short value, int index, StaticObject arr) {
        try {
            (((StaticObjectArray) arr).<short[]> unwrap())[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayObject(StaticObject value, int index, StaticObjectArray wrapper) {
        Object[] array = wrapper.unwrap();
        if (index >= 0 && index < array.length) {
            array[index] = arrayStoreExCheck(value, wrapper.getKlass().getComponentType());
        } else {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class);
        }
    }

    private Object arrayStoreExCheck(StaticObject value, Klass componentType) {
        if (StaticObject.isNull(value) || instanceOf(value, componentType)) {
            return value;
        } else {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayStoreException.class);
        }
    }

    // endregion

    // region Monitor enter/exit

    @SuppressWarnings({"deprecation"})
    public void monitorEnter(Object obj) {
        // TODO(peterssen): Nop for single-threaded language + enable on SVM.
        if (!EspressoOptions.RUNNING_ON_SVM) {
            hostUnsafe.monitorEnter(obj);
        }
    }

    @SuppressWarnings({"deprecation"})
    public void monitorExit(Object obj) {
        // TODO(peterssen): Nop for single-threaded language + enable on SVM.
        if (!EspressoOptions.RUNNING_ON_SVM) {
            hostUnsafe.monitorExit(obj);
        }
    }
    // endregion

    public boolean getFieldBoolean(StaticObject obj, Field field) {
        return (boolean) ((StaticObjectImpl) obj).getField(field);
    }

    public int getFieldInt(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Int;
        return (int) ((StaticObjectImpl) obj).getField(field);
    }

    public long getFieldLong(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Long;
        return (long) ((StaticObjectImpl) obj).getField(field);
    }

    public byte getFieldByte(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Byte;
        return (byte) ((StaticObjectImpl) obj).getField(field);
    }

    public short getFieldShort(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Short;
        return (short) ((StaticObjectImpl) obj).getField(field);
    }

    public float getFieldFloat(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Float;
        return (float) ((StaticObjectImpl) obj).getField(field);
    }

    public double getFieldDouble(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Double;
        return (double) ((StaticObjectImpl) obj).getField(field);
    }

    public StaticObject getFieldObject(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Object;
        return (StaticObject) ((StaticObjectImpl) obj).getField(field);
    }

    public char getFieldChar(StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Char;
        return (char) ((StaticObjectImpl) obj).getField(field);
    }

    public void setFieldBoolean(boolean value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Boolean;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldByte(byte value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Byte;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldChar(char value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Char;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldShort(short value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Short;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldInt(int value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Int;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldLong(long value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Long;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldFloat(float value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Float;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldDouble(double value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Double;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldObject(StaticObject value, StaticObject obj, Field field) {
        assert field.getKind() == JavaKind.Object;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public StaticObjectArray newArray(Klass componentType, int length) {
        if (length < 0) {
            throw componentType.getContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        assert !componentType.isPrimitive() : "use allocateNativeArray for primitives";
        assert length >= 0;
        StaticObject[] arr = new StaticObject[length];
        Arrays.fill(arr, StaticObject.NULL);
        return new StaticObjectArray(componentType.getArrayClass(), arr);
    }

    @TruffleBoundary
    public StaticObject newMultiArray(Klass klass, int... dimensions) {
        assert dimensions.length > 1;

        if (Arrays.stream(dimensions).anyMatch(new IntPredicate() {
            @Override
            public boolean test(int i) {
                return i < 0;
            }
        })) {
            throw klass.getMeta().throwEx(NegativeArraySizeException.class);
        }

        Klass componentType = klass.getComponentType();

        if (dimensions.length == 2) {
            assert dimensions[0] >= 0;
            if (componentType.getComponentType().isPrimitive()) {
                return (StaticObject) meta(componentType).allocateArray(dimensions[0],
                                new IntFunction<StaticObject>() {
                                    @Override
                                    public StaticObject apply(int i) {
                                        return allocatePrimitiveArray((byte) componentType.getComponentType().getJavaKind().getBasicType(), dimensions[1]);
                                    }
                                });
            }
            return (StaticObject) meta(componentType).allocateArray(dimensions[0], new IntFunction<StaticObject>() {
                @Override
                public StaticObject apply(int i) {
                    return InterpreterToVM.this.newArray(componentType.getComponentType(), dimensions[1]);
                }
            });
        } else {
            int[] newDimensions = Arrays.copyOfRange(dimensions, 1, dimensions.length);
            return (StaticObject) meta(componentType).allocateArray(dimensions[0], new IntFunction<StaticObject>() {
                @Override
                public StaticObject apply(int i) {
                    return InterpreterToVM.this.newMultiArray(componentType, newDimensions);
                }
            });
        }
    }

    public static StaticObjectArray allocatePrimitiveArray(byte jvmPrimitiveType, int length) {
        // the constants for the cpi are loosely defined and no real cpi indices.
        if (length < 0) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        // @formatter:off
        // Checkstyle: stop
        switch (jvmPrimitiveType) {
            case 4  : return StaticObjectArray.wrap(new boolean[length]);
            case 5  : return StaticObjectArray.wrap(new char[length]);
            case 6  : return StaticObjectArray.wrap(new float[length]);
            case 7  : return StaticObjectArray.wrap(new double[length]);
            case 8  : return StaticObjectArray.wrap(new byte[length]);
            case 9  : return StaticObjectArray.wrap(new short[length]);
            case 10 : return StaticObjectArray.wrap(new int[length]);
            case 11 : return StaticObjectArray.wrap(new long[length]);
            default : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    /**
     * Subtyping among Array Types
     *
     * The following rules define the direct supertype relation among array types:
     *
     * - If S and T are both reference types, then S[] >1 T[] iff S >1 T. - Object >1 Object[] -
     * Cloneable >1 Object[] - java.io.Serializable >1 Object[] - If P is a primitive type, then:
     * Object >1 P[] Cloneable >1 P[] java.io.Serializable >1 P[]
     */
    @TruffleBoundary
    public boolean instanceOf(StaticObject instance, Klass typeToCheck) {
        if (StaticObject.isNull(instance)) {
            return false;
        }
        return typeToCheck.isAssignableFrom(instance.getKlass());
    }

    public StaticObject checkCast(StaticObject instance, Klass klass) {
        if (StaticObject.isNull(instance) || instanceOf(instance, klass)) {
            return instance;
        }
        Meta meta = klass.getContext().getMeta();
        throw meta.throwEx(ClassCastException.class);
    }

    public StaticObject newObject(Klass klass) {
        assert klass != null && !klass.isArray() && !klass.isPrimitive() && !klass.isAbstract();
        klass.initialize();
        return new StaticObjectImpl((ObjectKlass) klass);
    }

    public int arrayLength(StaticObject arr) {
        return ((StaticObjectArray) arr).length();
    }
}
