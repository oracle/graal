/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import java.lang.reflect.Constructor;
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

    public static Object newInstance(Class<?> clazz, Object... args) {
        return newInstance(clazz, inferTypes(args), args);
    }

    public static Object getField(Object value, String name) {
        try {
            Field f = value.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(value);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object getStaticField(Class<?> clazz, String name) {
        try {
            Field f = clazz.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object newInstance(Class<?> clazz, Class<?>[] argTypes, Object... args) {
        try {
            Constructor<?> m = clazz.getDeclaredConstructor(argTypes);
            m.setAccessible(true);
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
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Object invoke(Object object, String name, Class<?>[] argTypes, Object... args) {
        try {
            Method m = object.getClass().getDeclaredMethod(name, argTypes);
            m.setAccessible(true);
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
