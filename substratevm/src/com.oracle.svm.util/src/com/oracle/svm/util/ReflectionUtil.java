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
package com.oracle.svm.util;

// Checkstyle: allow reflection

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * This class contains utility methods for commonly used reflection functionality.
 */
public final class ReflectionUtil {

    @SuppressWarnings("serial")
    public static final class ReflectionUtilError extends Error {
        private ReflectionUtilError(Throwable cause) {
            super(cause);
        }
    }

    private ReflectionUtil() {
    }

    /**
     * Ensure that this class is allowed to call setAccessible for an element of the provided
     * declaring class.
     */
    private static void openModule(Class<?> declaringClass) {
        ModuleSupport.openModule(declaringClass, ReflectionUtil.class);
    }

    public static Method lookupMethod(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
        try {
            Method result = declaringClass.getDeclaredMethod(methodName, parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static <T> Constructor<T> lookupConstructor(Class<T> declaringClass, Class<?>... parameterTypes) {
        try {
            Constructor<T> result = declaringClass.getDeclaredConstructor(parameterTypes);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static <T> T newInstance(Class<T> declaringClass) {
        try {
            return lookupConstructor(declaringClass).newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new ReflectionUtilError(ex);
        }
    }

    public static Field lookupField(Class<?> declaringClass, String fieldName) {
        try {
            Field result = declaringClass.getDeclaredField(fieldName);
            openModule(declaringClass);
            result.setAccessible(true);
            return result;
        } catch (ReflectiveOperationException ex) {
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
}
