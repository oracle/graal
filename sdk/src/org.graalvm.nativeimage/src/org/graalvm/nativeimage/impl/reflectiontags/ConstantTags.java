/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.impl.reflectiontags;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Used for representing constant reflection calls caught by {@link com.oracle.svm.reflectionagent}.
 * <p>
 * Due to build-time initialization, the calls must implement the equivalent logic of their corresponding
 * reflection method. Since these calls will be folded into constants, they will never be executed during
 * image run-time.
 */
public final class ConstantTags {

    public static final Map<Method, Method> TAG_TO_ORIGINAL_MAPPING;

    static {
        Map<Method, Method> mapping = new HashMap<>();
        try {
            mapping.put(ConstantTags.class.getDeclaredMethod("forName", String.class), Class.class.getDeclaredMethod("forName", String.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("forName", String.class, boolean.class, ClassLoader.class), Class.class.getDeclaredMethod("forName", String.class, boolean.class, ClassLoader.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getField", Class.class, String.class), Class.class.getDeclaredMethod("getField", String.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredField", Class.class, String.class), Class.class.getDeclaredMethod("getDeclaredField", String.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getConstructor", Class.class, Class[].class), Class.class.getDeclaredMethod("getConstructor", Class[].class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredConstructor", Class.class, Class[].class), Class.class.getDeclaredMethod("getDeclaredConstructor", Class[].class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getMethod", Class.class, String.class, Class[].class), Class.class.getDeclaredMethod("getMethod", String.class, Class[].class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredMethod", Class.class, String.class, Class[].class), Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class));
            mapping.put(ConstantTags.class.getDeclaredMethod("getFields", Class.class), Class.class.getDeclaredMethod("getFields"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredFields", Class.class), Class.class.getDeclaredMethod("getDeclaredFields"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getConstructors", Class.class), Class.class.getDeclaredMethod("getConstructors"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredConstructors", Class.class), Class.class.getDeclaredMethod("getDeclaredConstructors"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getMethods", Class.class), Class.class.getDeclaredMethod("getMethods"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredMethods", Class.class), Class.class.getDeclaredMethod("getDeclaredMethods"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getClasses", Class.class), Class.class.getDeclaredMethod("getClasses"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getDeclaredClasses", Class.class), Class.class.getDeclaredMethod("getDeclaredClasses"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getNestMembers", Class.class), Class.class.getDeclaredMethod("getNestMembers"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getPermittedSubclasses", Class.class), Class.class.getDeclaredMethod("getPermittedSubclasses"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getRecordComponents", Class.class), Class.class.getDeclaredMethod("getRecordComponents"));
            mapping.put(ConstantTags.class.getDeclaredMethod("getSigners", Class.class), Class.class.getDeclaredMethod("getSigners"));
            mapping.put(ConstantTags.class.getDeclaredMethod("findClass", MethodHandles.Lookup.class, String.class), MethodHandles.Lookup.class.getDeclaredMethod("findClass", String.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findVirtual", MethodHandles.Lookup.class, Class.class, String.class, MethodType.class), MethodHandles.Lookup.class.getDeclaredMethod("findVirtual", Class.class, String.class, MethodType.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findStatic", MethodHandles.Lookup.class, Class.class, String.class, MethodType.class), MethodHandles.Lookup.class.getDeclaredMethod("findStatic", Class.class, String.class, MethodType.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findConstructor", MethodHandles.Lookup.class, Class.class, MethodType.class), MethodHandles.Lookup.class.getDeclaredMethod("findConstructor", Class.class, MethodType.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findGetter", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findGetter", Class.class, String.class, Class.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findStaticGetter", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findStaticGetter", Class.class, String.class, Class.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findSetter", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findSetter", Class.class, String.class, Class.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findStaticSetter", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findStaticSetter", Class.class, String.class, Class.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findVarHandle", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findVarHandle", Class.class, String.class, Class.class));
            mapping.put(ConstantTags.class.getDeclaredMethod("findStaticVarHandle", MethodHandles.Lookup.class, Class.class, String.class, Class.class), MethodHandles.Lookup.class.getDeclaredMethod("findStaticVarHandle", Class.class, String.class, Class.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        TAG_TO_ORIGINAL_MAPPING = Collections.unmodifiableMap(mapping);
    }

    private static final StackWalker stackWalker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    public static Class<?> forName(String className) throws ClassNotFoundException {
        return Class.forName(className, true, stackWalker.getCallerClass().getClassLoader());
    }

    public static Class<?> forName(String className, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
        return Class.forName(className, initialize, classLoader);
    }

    public static Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return clazz.getField(fieldName);
    }

    public static Field getDeclaredField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        return clazz.getDeclaredField(fieldName);
    }

    public static Constructor<?> getConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getConstructor(parameterTypes);
    }

    public static Constructor<?> getDeclaredConstructor(Class<?> clazz, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredConstructor(parameterTypes);
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getMethod(methodName, parameterTypes);
    }

    public static Method getDeclaredMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        return clazz.getDeclaredMethod(methodName, parameterTypes);
    }

    public static Field[] getFields(Class<?> clazz) {
        return clazz.getFields();
    }

    public static Field[] getDeclaredFields(Class<?> clazz) {
        return clazz.getDeclaredFields();
    }

    public static Constructor<?>[] getConstructors(Class<?> clazz) {
        return clazz.getConstructors();
    }

    public static Constructor<?>[] getDeclaredConstructors(Class<?> clazz) {
        return clazz.getDeclaredConstructors();
    }

    public static Method[] getMethods(Class<?> clazz) {
        return clazz.getMethods();
    }

    public static Method[] getDeclaredMethods(Class<?> clazz) {
        return clazz.getDeclaredMethods();
    }

    public static Class<?>[] getClasses(Class<?> clazz) {
        return clazz.getClasses();
    }

    public static Class<?>[] getDeclaredClasses(Class<?> clazz) {
        return clazz.getDeclaredClasses();
    }

    public static Class<?>[] getNestMembers(Class<?> clazz) {
        return clazz.getNestMembers();
    }

    public static Class<?>[] getPermittedSubclasses(Class<?> clazz) {
        return clazz.getPermittedSubclasses();
    }

    public static RecordComponent[] getRecordComponents(Class<?> clazz) {
        return clazz.getRecordComponents();
    }

    public static Object[] getSigners(Class<?> clazz) {
        return clazz.getSigners();
    }

    public static Class<?> findClass(MethodHandles.Lookup lookup, String className) throws ClassNotFoundException, IllegalAccessException {
        return lookup.findClass(className);
    }

    public static MethodHandle findVirtual(MethodHandles.Lookup lookup, Class<?> clazz, String methodName, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return lookup.findVirtual(clazz, methodName, methodType);
    }

    public static MethodHandle findStatic(MethodHandles.Lookup lookup, Class<?> clazz, String methodName, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return lookup.findStatic(clazz, methodName, methodType);
    }

    public static MethodHandle findConstructor(MethodHandles.Lookup lookup, Class<?> clazz, MethodType methodType) throws NoSuchMethodException, IllegalAccessException {
        return lookup.findConstructor(clazz, methodType);
    }

    public static MethodHandle findGetter(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findGetter(clazz, name, type);
    }

    public static MethodHandle findStaticGetter(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findStaticGetter(clazz, name, type);
    }

    public static MethodHandle findSetter(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findSetter(clazz, name, type);
    }

    public static MethodHandle findStaticSetter(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findStaticSetter(clazz, name, type);
    }

    public static VarHandle findVarHandle(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findVarHandle(clazz, name, type);
    }

    public static VarHandle findStaticVarHandle(MethodHandles.Lookup lookup, Class<?> clazz, String name, Class<?> type) throws NoSuchFieldException, IllegalAccessException {
        return lookup.findStaticVarHandle(clazz, name, type);
    }
}
