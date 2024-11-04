/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot.libgraal;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.oracle.svm.util.ModuleSupport;

/**
 * This is a copy of {@code com.oracle.svm.util.ReflectionUtil}.
 */
public final class LibGraalReflectionUtil {

    @SuppressWarnings("serial")
    public static final class ReflectionUtilError extends Error {
        private ReflectionUtilError(Throwable cause) {
            super(cause);
        }
    }

    private LibGraalReflectionUtil() {
    }

    /**
     * Ensure that this class is allowed to call setAccessible for an element of the provided
     * declaring class.
     */
    private static void openModule(Class<?> declaringClass) {
        ModuleSupport.accessModuleByClass(ModuleSupport.Access.OPEN, LibGraalReflectionUtil.class, declaringClass);
    }

    public static Class<?> lookupClass(String className) {
        return lookupClass(false, className);
    }

    public static Class<?> lookupClass(boolean optional, String className) {
        return lookupClass(optional, className, LibGraalReflectionUtil.class.getClassLoader());
    }

    public static Class<?> lookupClass(boolean optional, String className, ClassLoader loader) {
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException ex) {
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        return lookupMethod(false, declaringClass, methodName, parameterTypes);
    }

    public static Method lookupMethod(boolean optional, Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    public static Method lookupPublicMethodInClassHierarchy(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        return lookupPublicMethodInClassHierarchy(false, clazz, methodName, parameterTypes);
    }

    public static Method lookupPublicMethodInClassHierarchy(boolean optional, Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = clazz.getMethod(methodName, parameterTypes);
            openModule(result.getDeclaringClass());
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    public static <T> Constructor<T> lookupConstructor(Class<T> declaringClass, Class<?>... parameterTypes) {
        return lookupConstructor(false, declaringClass, parameterTypes);
    }

    public static <T> Constructor<T> lookupConstructor(boolean optional, Class<T> declaringClass, Class<?>... parameterTypes) {
        try {
            Constructor<T> result = declaringClass.getDeclaredConstructor(parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    /**
     * Invokes the provided method, and unwraps a possible {@link InvocationTargetException} so that
     * it appears as if the method had been invoked directly without the use of reflection.
     */
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
            throw new ReflectionUtilError(ex);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    public static <T> T newInstance(Class<T> declaringClass) {
        try {
            return lookupConstructor(declaringClass).newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static <T> T newInstance(Constructor<T> constructor, Object... initArgs) {
        try {
            return constructor.newInstance(initArgs);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static VarHandle unreflectField(Class<?> declaringClass, String fieldName, MethodHandles.Lookup lookup) {
        try {
            Field field = LibGraalReflectionUtil.lookupField(declaringClass, fieldName);
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(declaringClass, lookup);
            return privateLookup.unreflectVarHandle(field);
        } catch (IllegalAccessException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static Field lookupField(Class<?> declaringClass, String fieldName) {
        return lookupField(false, declaringClass, fieldName);
    }

    private static final Method fieldGetDeclaredFields0 = LibGraalReflectionUtil.lookupMethod(Class.class, "getDeclaredFields0", boolean.class);

    public static Field lookupField(boolean optional, Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            /* Try to get hidden field */
            try {
                Field[] allFields = (Field[]) fieldGetDeclaredFields0.invoke(declaringClass, false);
                for (Field field : allFields) {
                    if (field.getName().equals(fieldName)) {
                        openModule(declaringClass);
                        field.setAccessible(true);
                        return field;
                    }
                }
            } catch (ReflectiveOperationException e) {
                // ignore
            }
            if (optional) {
                return null;
            }
            throw new ReflectionUtilError(ex);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T readField(Class<?> declaringClass, String fieldName, Object receiver) {
        try {
            return (T) lookupField(declaringClass, fieldName).get(receiver);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static <T> T readStaticField(Class<?> declaringClass, String fieldName) {
        return readField(declaringClass, fieldName, null);
    }

    public static void writeField(Class<?> declaringClass, String fieldName, Object receiver, Object value) {
        try {
            lookupField(declaringClass, fieldName).set(receiver, value);
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static void writeStaticField(Class<?> declaringClass, String fieldName, Object value) {
        writeField(declaringClass, fieldName, null, value);
    }
}
