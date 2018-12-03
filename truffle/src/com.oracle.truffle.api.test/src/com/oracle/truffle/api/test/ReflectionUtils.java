/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtils {

    public static Class<?> loadRelative(Class<?> testClass, String className) {
        String pack = testClass.getPackage().getName();
        try {
            pack = pack.replace("test.", "");
            return testClass.getClassLoader().loadClass(pack + "." + className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static <T> T newInstance(Class<T> clazz, Object... args) {
        return newInstance(clazz, inferTypes(args), args);
    }

    public static Object getField(Object value, String name) {
        try {
            Field f = value.getClass().getDeclaredField(name);
            setAccessible(f, true);
            return f.get(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object getStaticField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            setAccessible(f, true);
            return f.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static <T> T newInstance(Class<T> clazz, Class<?>[] argTypes, Object... args) {
        try {
            Constructor<T> m = clazz.getDeclaredConstructor(argTypes);
            setAccessible(m, true);
            return m.newInstance(args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object invokeStatic(Class<?> object, String name, Object... args) {
        return invokeStatic(object, name, inferTypes(args), args);
    }

    public static Object invokeStatic(Class<?> clazz, String name, Class<?>[] argTypes, Object... args) {
        try {
            Method m = clazz.getDeclaredMethod(name, argTypes);
            setAccessible(m, true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Calls {@link AccessibleObject#setAccessible(boolean)} on {@code field} with the value
     * {@code flag}.
     */
    public static void setAccessible(Field field, boolean flag) {
        if (!Java8OrEarlier) {
            openForReflectionTo(field.getDeclaringClass(), ReflectionUtils.class);
        }
        field.setAccessible(flag);
    }

    public static final boolean Java8OrEarlier = System.getProperty("java.specification.version").compareTo("1.9") < 0;

    /**
     * Calls {@link AccessibleObject#setAccessible(boolean)} on {@code executable} with the value
     * {@code flag}.
     */
    public static void setAccessible(Executable executable, boolean flag) {
        if (!Java8OrEarlier) {
            openForReflectionTo(executable.getDeclaringClass(), ReflectionUtils.class);
        }
        executable.setAccessible(flag);
    }

    /**
     * Opens {@code declaringClass}'s package to allow a method declared in {@code accessor} to call
     * {@link AccessibleObject#setAccessible(boolean)} on an {@link AccessibleObject} representing a
     * field or method declared by {@code declaringClass}.
     */
    private static void openForReflectionTo(Class<?> declaringClass, Class<?> accessor) {
        try {
            Method getModule = Class.class.getMethod("getModule");
            Class<?> moduleClass = getModule.getReturnType();
            Class<?> modulesClass = Class.forName("jdk.internal.module.Modules");
            Method addOpens = maybeGetAddOpensMethod(moduleClass, modulesClass);
            if (addOpens != null) {
                Object moduleToOpen = getModule.invoke(declaringClass);
                Object accessorModule = getModule.invoke(accessor);
                if (moduleToOpen != accessorModule) {
                    addOpens.invoke(null, moduleToOpen, declaringClass.getPackage().getName(), accessorModule);
                }
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Method maybeGetAddOpensMethod(Class<?> moduleClass, Class<?> modulesClass) {
        try {
            return modulesClass.getDeclaredMethod("addOpens", moduleClass, String.class, moduleClass);
        } catch (NoSuchMethodException e) {
            // This method was introduced by JDK-8169069
            return null;
        }
    }

    public static Object invoke(Object object, String name, Class<?>[] argTypes, Object... args) {
        try {
            Method m = object.getClass().getDeclaredMethod(name, argTypes);
            setAccessible(m, true);
            return m.invoke(object, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object invoke(Object object, String name, Object... args) {
        return invoke(object, name, inferTypes(args), args);
    }

    private static Class<?>[] inferTypes(Object... args) {
        Class<?>[] argTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                argTypes[i] = Object.class;
            } else {
                argTypes[i] = args[i].getClass();
            }
        }
        return argTypes;
    }

}
