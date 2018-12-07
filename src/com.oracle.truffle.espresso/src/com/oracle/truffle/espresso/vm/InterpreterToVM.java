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

import static com.oracle.truffle.espresso.meta.Meta.meta;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.intrinsics.EspressoIntrinsics;
import com.oracle.truffle.espresso.intrinsics.Intrinsic;
import com.oracle.truffle.espresso.intrinsics.Surrogate;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Class;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_ClassLoader;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Package;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Runtime;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Thread;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_reflect_Array;
import com.oracle.truffle.espresso.intrinsics.Target_java_security_AccessController;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Perf;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Signal;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_URLClassPath;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Unsafe;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_VM;
import com.oracle.truffle.espresso.intrinsics.Target_sun_reflect_NativeMethodAccessorImpl;
import com.oracle.truffle.espresso.intrinsics.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.IntrinsicReflectionRootNode;
import com.oracle.truffle.espresso.nodes.IntrinsicRootNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import sun.misc.Unsafe;

public class InterpreterToVM {

    private static Unsafe hostUnsafe;

    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            hostUnsafe = (Unsafe) f.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private final Map<MethodKey, RootNode> intrinsics = new HashMap<>();

    public static List<Class<?>> DEFAULTS = Arrays.asList(
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Package.class,
                    Target_java_lang_Runtime.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeMethodAccessorImpl.class);

    private InterpreterToVM(EspressoLanguage language, List<Class<?>> intrinsics) {
        for (Class<?> clazz : intrinsics) {
            registerIntrinsics(clazz, language);
        }
    }

    public InterpreterToVM(EspressoLanguage language) {
        this(language, DEFAULTS);
    }

    public StaticObject intern(StaticObject obj) {
        assert obj.getKlass().getTypeDescriptor().equals(obj.getKlass().getContext().getTypeDescriptors().STRING);
        return obj.getKlass().getContext().getStrings().intern(obj);
    }

    private static MethodKey getMethodKey(MethodInfo method) {
        return new MethodKey(
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        method.getSignature().toString());
    }

    @CompilerDirectives.TruffleBoundary
    public RootNode getIntrinsic(MethodInfo method) {
        assert method != null;
        return intrinsics.get(getMethodKey(method));
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

    public void registerIntrinsics(Class<?> clazz, EspressoLanguage language) {

        String className;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoIntrinsics.class).value();
        if (annotatedClass == EspressoIntrinsics.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            className = MetaUtil.toInternalName(clazz.getSimpleName().substring("Target_".length()).replace('_', '.'));
        } else {
            Surrogate surrogate = annotatedClass.getAnnotation(Surrogate.class);
            if (surrogate != null) {
                className = MetaUtil.toInternalName(surrogate.value());
            } else {
                className = MetaUtil.toInternalName(annotatedClass.getName());
            }
        }
        for (Method method : clazz.getDeclaredMethods()) {
            Intrinsic intrinsic = method.getAnnotation(Intrinsic.class);
            if (intrinsic == null) {
                continue;
            }

            RootNode rootNode = createRootNodeForMethod(language, method);
            StringBuilder signature = new StringBuilder("(");
            Parameter[] parameters = method.getParameters();
            for (int i = intrinsic.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                String parameterTypeName;
                Type annotatedType = parameter.getAnnotatedType().getAnnotation(Type.class);
                if (annotatedType != null) {
                    Surrogate surrogate = annotatedType.value().getAnnotation(Surrogate.class);
                    if (surrogate != null) {
                        parameterTypeName = surrogate.value();
                    } else {
                        parameterTypeName = annotatedType.value().getName();
                    }
                } else {
                    parameterTypeName = parameter.getType().getName();
                }
                signature.append(fixTypeName(parameterTypeName));
            }
            signature.append(')');

            Type annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Type.class);
            String returnTypeName;
            if (annotatedReturnType != null) {
                Surrogate surrogate = annotatedReturnType.value().getAnnotation(Surrogate.class);
                if (surrogate != null) {
                    returnTypeName = surrogate.value();
                } else {
                    returnTypeName = annotatedReturnType.value().getName();
                }
            } else {
                returnTypeName = method.getReturnType().getName();
            }
            signature.append(fixTypeName(returnTypeName));

            String methodName = intrinsic.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            registerIntrinsic(fixTypeName(className), methodName, signature.toString(), rootNode, false);
        }
    }

    private static RootNode createRootNodeForMethod(EspressoLanguage language, Method method) {
        if (!EspressoOptions.RUNNING_ON_SVM && language.getContextReference().get().getEnv().getOptions().get(EspressoOptions.IntrinsicsViaMethodHandles)) {
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

    public void registerIntrinsic(String clazz, String methodName, String signature, RootNode intrinsic, boolean update) {
        MethodKey key = new MethodKey(clazz, methodName, signature);
        assert intrinsic != null;
        if (update || !intrinsics.containsKey(key)) {
            // assert !intrinsics.containsKey(key) : key + " intrinsic is already registered";
            intrinsics.put(key, intrinsic);
        }
    }

    // region Get (array) operations

    public int getArrayInt(int index, Object arr) {
        try {
            return ((int[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public Object getArrayObject(int index, Object arr) {
        try {
            return ((StaticObjectArray) arr).getWrapped()[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public long getArrayLong(int index, Object arr) {
        try {
            return ((long[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public float getArrayFloat(int index, Object arr) {
        try {
            return ((float[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public double getArrayDouble(int index, Object arr) {
        try {
            return ((double[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public byte getArrayByte(int index, Object arr) {
        try {
            if (arr instanceof boolean[]) {
                return (byte) (((boolean[]) arr)[index] ? 1 : 0);
            }
            return ((byte[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public char getArrayChar(int index, Object arr) {
        try {
            return ((char[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public short getArrayShort(int index, Object arr) {
        try {
            return ((short[]) arr)[index];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }
    // endregion

    // region Set (array) operations
    public void setArrayInt(int value, int index, Object arr) {
        try {
            ((int[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayLong(long value, int index, Object arr) {
        try {
            ((long[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayFloat(float value, int index, Object arr) {
        try {
            ((float[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayDouble(double value, int index, Object arr) {
        try {
            ((double[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayByte(byte value, int index, Object arr) {
        try {
            if (arr instanceof boolean[]) {
                assert value == 0 || value == 1;
                ((boolean[]) arr)[index] = (value != 0);
            } else {
                ((byte[]) arr)[index] = value;
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayChar(char value, int index, Object arr) {
        try {
            ((char[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayShort(short value, int index, Object arr) {
        try {
            ((short[]) arr)[index] = value;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class, e.getMessage());
        }
    }

    public void setArrayObject(Object value, int index, StaticObjectArray wrapper) {
        Object[] array = wrapper.getWrapped();
        if (index >= 0 && index < array.length) {
            array[index] = arrayStoreExCheck(value, wrapper.getKlass().getComponentType());
        } else {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(ArrayIndexOutOfBoundsException.class);
        }
    }

    private Object arrayStoreExCheck(Object value, Klass componentType) {
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

    public boolean getFieldBoolean(StaticObject obj, FieldInfo field) {
        return (boolean) ((StaticObjectImpl) obj).getField(field);
    }

    public int getFieldInt(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Int;
        return (int) ((StaticObjectImpl) obj).getField(field);
    }

    public long getFieldLong(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Long;
        return (long) ((StaticObjectImpl) obj).getField(field);
    }

    public byte getFieldByte(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Byte;
        return (byte) ((StaticObjectImpl) obj).getField(field);
    }

    public short getFieldShort(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Short;
        return (short) ((StaticObjectImpl) obj).getField(field);
    }

    public float getFieldFloat(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Float;
        return (float) ((StaticObjectImpl) obj).getField(field);
    }

    public double getFieldDouble(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Double;
        return (double) ((StaticObjectImpl) obj).getField(field);
    }

    public Object getFieldObject(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Object;
        return ((StaticObjectImpl) obj).getField(field);
    }

    public char getFieldChar(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Char;
        return (char) ((StaticObjectImpl) obj).getField(field);
    }

    public void setFieldBoolean(boolean value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Boolean;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldByte(byte value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Byte;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldChar(char value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Char;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldShort(short value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Short;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldInt(int value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Int;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldLong(long value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Long;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldFloat(float value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Float;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldDouble(double value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Double;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldObject(Object value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Object;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public StaticObject newArray(Klass componentType, int length) {
        if (length < 0) {
            throw componentType.getContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        assert !componentType.isPrimitive() : "use allocateNativeArray for primitives";
        assert length >= 0;
        Object[] arr = new Object[length];
        Arrays.fill(arr, StaticObject.NULL);
        return new StaticObjectArray(componentType, arr);
    }

    public StaticObject newMultiArray(Klass klass, int[] dimensions) {
        assert dimensions.length > 1;

        if (Arrays.stream(dimensions).anyMatch(i -> i < 0)) {
            throw meta(klass).getMeta().throwEx(NegativeArraySizeException.class);
        }

        Klass componentType = klass.getComponentType();

        if (dimensions.length == 2) {
            assert dimensions[0] >= 0;
            if (componentType.getComponentType().isPrimitive()) {
                return (StaticObject) meta(componentType).allocateArray(dimensions[0],
                                i -> allocateNativeArray((byte) componentType.getComponentType().getJavaKind().getBasicType(), dimensions[1]));
            }
            return (StaticObject) meta(componentType).allocateArray(dimensions[0], i -> newArray(componentType.getComponentType(), dimensions[1]));
        } else {
            int[] newDimensions = Arrays.copyOfRange(dimensions, 1, dimensions.length);
            return (StaticObject) meta(componentType).allocateArray(dimensions[0], i -> newMultiArray(componentType, newDimensions));
        }
    }

    public static Object allocateNativeArray(byte jvmPrimitiveType, int length) {
        // the constants for the cpi are loosely defined and no real cpi indices.
        if (length < 0) {
            throw EspressoLanguage.getCurrentContext().getMeta().throwEx(NegativeArraySizeException.class);
        }
        switch (jvmPrimitiveType) {
            case 4:
                return new boolean[length];
            case 5:
                return new char[length];
            case 6:
                return new float[length];
            case 7:
                return new double[length];
            case 8:
                return new byte[length];
            case 9:
                return new short[length];
            case 10:
                return new int[length];
            case 11:
                return new long[length];
            default:
                throw EspressoError.shouldNotReachHere();
        }
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
    public boolean instanceOf(Object instance, Klass typeToCheck) {
        assert instance != null : "use StaticObject.NULL";
        if (StaticObject.isNull(instance)) {
            return false;
        }
        Meta meta = meta(typeToCheck).getMeta();
        return meta(typeToCheck).isAssignableFrom(meta.meta(instance));
    }

    public Object checkCast(Object instance, Klass klass) {
        if (StaticObject.isNull(instance) || instanceOf(instance, klass)) {
            return instance;
        }
        Meta meta = klass.getContext().getMeta();
        throw meta.throwEx(ClassCastException.class);
    }

    public StaticObject newObject(Klass klass) {
        assert klass != null && !klass.isArray();
        klass.initialize();
        return new StaticObjectImpl((ObjectKlass) klass);
    }

    public int arrayLength(Object arr) {
        if (arr instanceof StaticObjectArray) {
            return ((StaticObjectArray) arr).getWrapped().length;
        } else {
            assert arr.getClass().isArray();
            // Primitive arrays are shared in the guest/host.
            return Array.getLength(arr);
        }
    }
}
