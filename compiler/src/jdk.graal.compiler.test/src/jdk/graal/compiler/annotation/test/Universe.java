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
package jdk.graal.compiler.annotation.test;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.AbstractCollection;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.junit.Test;

import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaRecordComponent;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;

/**
 * Collections of classes, fields, methods and record components.
 */
public class Universe {

    public static final MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
    public static final Set<Class<?>> classes = new EconomicHashSet<>();
    public static final Set<ResolvedJavaType> javaTypes;
    public static final ResolvedJavaType predicateType;
    public static final Map<Class<?>, Class<?>> arrayClasses = new EconomicHashMap<>();
    public static final Map<Method, ResolvedJavaMethod> methods = new EconomicHashMap<>();
    public static final Map<Constructor<?>, ResolvedJavaMethod> constructors = new EconomicHashMap<>();
    public static final Map<Field, ResolvedJavaField> fields = new EconomicHashMap<>();
    public static final Map<RecordComponent, ResolvedJavaRecordComponent> recordComponents = new EconomicHashMap<>();

    // Define a type-use annotation
    @Target(ElementType.TYPE_USE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface TypeQualifier {
        String comment() default "";

        int id() default -1;
    }

    // Define a parameter annotation
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    @interface ParameterQualifier {
        String value() default "";

        int tag() default -1;
    }

    public class InnerClass {
        public class InnerInnerClass {
        }
    }

    public static class InnerStaticClass {

    }

    public static final class InnerStaticFinalClass {

    }

    private final class PrivateInnerClass {

    }

    protected class ProtectedInnerClass {

    }

    static {
        Class<?>[] initialClasses = {void.class, boolean.class, byte.class, short.class, char.class, int.class, float.class, long.class, double.class, Object.class, Class.class, boolean[].class,
                        byte[].class, short[].class, char[].class, int[].class, float[].class, long[].class, double[].class, Object[].class, Class[].class, List[].class, boolean[][].class,
                        byte[][].class, short[][].class, char[][].class, int[][].class, float[][].class, long[][].class, double[][].class, Object[][].class, Class[][].class, List[][].class,
                        ClassLoader.class, String.class, Serializable.class, Cloneable.class, Test.class, List.class, Collection.class, Map.class, Queue.class,
                        HashMap.class, LinkedHashMap.class, IdentityHashMap.class, AbstractCollection.class, AbstractList.class, ArrayList.class, InnerClass.class, InnerStaticClass.class,
                        InnerStaticFinalClass.class, PrivateInnerClass.class, ProtectedInnerClass.class, ScopedMemoryAccess.class};
        for (Class<?> c : initialClasses) {
            addClass(c);
        }
        Predicate<String> predicate = s -> s.length() == 1;
        addClass(predicate.getClass());
        predicateType = metaAccess.lookupJavaType(predicate.getClass());

        javaTypes = classes.stream().map(metaAccess::lookupJavaType).collect(Collectors.toUnmodifiableSet());

        for (Class<?> c : classes) {
            for (Method m : c.getDeclaredMethods()) {
                ResolvedJavaMethod method = metaAccess.lookupJavaMethod(m);
                methods.put(m, method);
            }
            for (Constructor<?> m : c.getDeclaredConstructors()) {
                constructors.put(m, metaAccess.lookupJavaMethod(m));
            }
            for (Field f : c.getDeclaredFields()) {
                ResolvedJavaField field = metaAccess.lookupJavaField(f);
                fields.put(f, field);
            }
            if (c.isRecord()) {
                for (RecordComponent rc : Objects.requireNonNull(c.getRecordComponents())) {
                    recordComponents.put(rc, metaAccess.lookupJavaRecordComponent(rc));
                }
            }
        }
    }

    // Annotates the class type String
    public @TypeQualifier String[][] typeAnnotatedField1;
    // Annotates the array type String[][]
    public String @TypeQualifier [][] typeAnnotatedField2;
    // Annotates the array type String[]
    String[] @TypeQualifier [] typeAnnotatedField3;

    public @TypeQualifier(comment = "comment1", id = 42) Universe.InnerClass.InnerInnerClass typeAnnotatedField4;
    public @TypeQualifier InnerClass.InnerInnerClass typeAnnotatedField5;
    public InnerClass.@TypeQualifier(comment = "47", id = -10) InnerInnerClass typeAnnotatedField6;

    public @TypeQualifier(comment = "comment2", id = 52) Universe.InnerClass.InnerInnerClass typeAnnotatedMethod1() {
        return null;
    }

    public @TypeQualifier InnerClass.InnerInnerClass typeAnnotatedMethod2() {
        return null;
    }

    public InnerClass.@TypeQualifier(comment = "57", id = -20) InnerInnerClass typeAnnotatedMethod3() {
        return null;
    }

    public void annotatedParameters1(
                    @TypeQualifier(comment = "comment3", id = 62) Universe this,
                    @TypeQualifier(comment = "comment4", id = 72) @ParameterQualifier String annotatedParam1,
                    int notAnnotatedParam2,
                    @ParameterQualifier(value = "foo", tag = 123) Thread annotatedParam3) {
    }

    public synchronized Class<?> getArrayClass(Class<?> componentType) {
        Class<?> arrayClass = arrayClasses.get(componentType);
        if (arrayClass == null) {
            arrayClass = Array.newInstance(componentType, 0).getClass();
            arrayClasses.put(componentType, arrayClass);
        }
        return arrayClass;
    }

    public static int dimensions(Class<?> c) {
        if (c.getComponentType() != null) {
            return 1 + dimensions(c.getComponentType());
        }
        return 0;
    }

    private static void addClass(Class<?> c) {
        if (classes.add(c)) {
            if (c.getSuperclass() != null) {
                addClass(c.getSuperclass());
            }
            for (Class<?> sc : c.getInterfaces()) {
                addClass(sc);
            }
            for (Class<?> enclosing = c.getEnclosingClass(); enclosing != null; enclosing = enclosing.getEnclosingClass()) {
                addClass(enclosing);
            }
            for (Class<?> dc : c.getDeclaredClasses()) {
                addClass(dc);
            }
            for (Method m : c.getDeclaredMethods()) {
                addClass(m.getReturnType());
                for (Class<?> p : m.getParameterTypes()) {
                    addClass(p);
                }
            }

            if (c != void.class && dimensions(c) < 2) {
                Class<?> arrayClass = Array.newInstance(c, 0).getClass();
                arrayClasses.put(c, arrayClass);
                addClass(arrayClass);
            }
        }
    }

    public static Class<?> lookupClass(String className) {
        try {
            return Class.forName(className, false, Universe.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError cause) {
            throw new AssertionError(cause);
        }
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException | LinkageError cause) {
            throw new AssertionError(cause);
        }
    }

    public static Field lookupField(Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException | LinkageError cause) {
            throw new AssertionError(cause);
        }
    }

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    @SuppressWarnings("unchecked")
    public static <T> T invokeMethod(Method method, Object receiver, Object... arguments) {
        try {
            method.setAccessible(true);
            return (T) method.invoke(receiver, arguments);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause != null) {
                throw rethrow(cause);
            }
            throw new AssertionError(ex);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Field field, Object receiver) {
        field.setAccessible(true);
        try {
            return (T) field.get(receiver);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
