/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import org.graalvm.jniutils.HotSpotCalls.JNIMethod;
import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JFieldID;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.word.WordFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support class for {@link JClass} and {@link JNIMethod} lookup. JClass and JNIMethod instances are
 * cached as JNI globals and disposed when the isolate is cleared.
 */
public final class JNICache {

    private static final Map<String, JNIClassData> classesByName = new ConcurrentHashMap<>();
    private static final Map<Long, JNIClassData> classesByAddr = new ConcurrentHashMap<>();

    private JNICache() {
    }

    /**
     * Looks up JClass using {@link Class}.
     *
     * @return JNI global reference for {@link JClass}
     * @throws JNIExceptionWrapper is thrown when class is not found.
     */
    public static JClass lookupClass(JNIEnv env, Class<?> clazz) throws JNIExceptionWrapper {
        return lookupClass(env, clazz.getName());
    }

    /**
     * Looks up JClass using fully qualified name.
     *
     * @return JNI global reference for {@link JClass}
     * @throws JNIExceptionWrapper is thrown when class is not found.
     */
    public static JClass lookupClass(JNIEnv env, String className) throws JNIExceptionWrapper {
        return lookupClassImpl(env, className, true);
    }

    /**
     * Looks up JClass using {@link Class}.
     *
     * @return JNI global reference for {@link JClass} or {@link WordFactory#nullPointer() NULL}
     *         when class is not found.
     */
    public static JClass lookupOptionalClass(JNIEnv env, Class<?> clazz) {
        return lookupOptionalClass(env, clazz.getName());
    }

    /**
     * Looks up JClass using fully qualified name.
     *
     * @return JNI global reference for {@link JClass} or {@link WordFactory#nullPointer() NULL}
     *         when class is not found.
     */
    public static JClass lookupOptionalClass(JNIEnv env, String className) {
        return lookupClassImpl(env, className, false);
    }

    private static JClass lookupClassImpl(JNIEnv env, String className, boolean required) {
        return classesByName.computeIfAbsent(className, (cn) -> {
            JClass jClass = JNIUtil.findClass(env, WordFactory.nullPointer(), JNIUtil.getBinaryName(className), required);
            JNIClassData res;
            if (jClass.isNull()) {
                res = JNIClassData.INVALID;
            } else {
                res = new JNIClassData(JNIUtil.NewGlobalRef(env, jClass, className));
                classesByAddr.put(res.jClassGlobal.rawValue(), res);
            }
            return res;
        }).jClassGlobal;
    }

    /**
     * Looks up non-static method in given {@link JClass} by name and signature.
     *
     * @return resolved JNIMethod
     * @throws JNIExceptionWrapper wrapping a {@link NoSuchMethodError} is thrown when method is not
     *             found.
     */
    public static JNIMethod lookupMethod(JNIEnv env, JClass clazz, String name, Class<?> returnType, Class<?>... parameterTypes) throws JNIExceptionWrapper {
        return lookupMethodImpl(env, true, clazz, false, name, returnType, parameterTypes);
    }

    /**
     * Looks up non-static method in given {@link JClass} by name and signature.
     *
     * @return resolved JNIMethod or {@link WordFactory#nullPointer() NULL} when method is not
     *         found.
     */
    public static JNIMethod lookupOptionalMethod(JNIEnv env, JClass clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return lookupMethodImpl(env, false, clazz, false, name, returnType, parameterTypes);
    }

    /**
     * Looks up static method in given {@link JClass} by name and signature.
     *
     * @return resolved JNIMethod
     * @throws JNIExceptionWrapper wrapping a {@link NoSuchMethodError} is thrown when method is not
     *             found.
     */
    public static JNIMethod lookupStaticMethod(JNIEnv env, JClass clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return lookupMethodImpl(env, true, clazz, true, name, returnType, parameterTypes);
    }

    /**
     * Looks up static method in given {@link JClass} by name and signature.
     *
     * @return resolved JNIMethod or {@link WordFactory#nullPointer() NULL} when method is not
     *         found.
     */
    public static JNIMethod lookupOptionalStaticMethod(JNIEnv env, JClass clazz, String name, Class<?> returnType, Class<?>... parameterTypes) {
        return lookupMethodImpl(env, false, clazz, true, name, returnType, parameterTypes);
    }

    private static JNIMethod lookupMethodImpl(JNIEnv env, boolean required, JClass clazz, boolean staticMethod, String name, Class<?> returnType, Class<?>... parameterTypes) {
        JNIClassData classData = classesByAddr.get(clazz.rawValue());
        if (classData == null) {
            throw new IllegalArgumentException("Class 0x" + Long.toHexString(clazz.rawValue()) + " was not resolved using JNIMethodCache.");
        }
        Key key = new Key(staticMethod, name, JNIUtil.encodeMethodSignature(returnType, parameterTypes));
        return classData.lookupMethod(env, key, required);
    }

    /**
     * Looks up field in given {@link JClass} by name and signature.
     *
     * @return resolved {@link JFieldID}
     * @throws JNIExceptionWrapper wrapping {@link NoSuchFieldError} is thrown when field is not
     *             found.
     */
    public static JFieldID lookupField(JNIEnv env, JClass clazz, String name, Class<?> type) throws JNIExceptionWrapper {
        return lookupFieldImpl(env, clazz, false, name, type);
    }

    /**
     * Looks up static field in given {@link JClass} by name and signature.
     *
     * @return resolved {@link JFieldID}
     * @throws JNIExceptionWrapper wrapping {@link NoSuchFieldError} is thrown when field is not
     *             found.
     */
    public static JFieldID lookupStaticField(JNIEnv env, JClass clazz, String name, Class<?> type) throws JNIExceptionWrapper {
        return lookupFieldImpl(env, clazz, true, name, type);
    }

    private static JFieldID lookupFieldImpl(JNIEnv env, JClass clazz, boolean staticField, String name, Class<?> type) {
        JNIClassData classData = classesByAddr.get(clazz.rawValue());
        if (classData == null) {
            throw new IllegalArgumentException("Class 0x" + Long.toHexString(clazz.rawValue()) + " was not resolved using JNIMethodCache.");
        }
        Key key = new Key(staticField, name, JNIUtil.encodeFieldSignature(type));
        return classData.lookupField(env, key);
    }

    /**
     * Disposes cached JNI objects and frees JNI globals.
     */
    public static void dispose(JNIEnv jniEnv) {
        for (Iterator<JNIClassData> iterator = classesByName.values().iterator(); iterator.hasNext();) {
            JNIClassData classData = iterator.next();
            iterator.remove();
            JNIUtil.DeleteGlobalRef(jniEnv, classData.jClassGlobal);
        }
        classesByAddr.clear();
    }

    private static final class Key {
        private final boolean staticElement;
        private final String name;
        private final String signature;

        Key(boolean staticElement, String name, String signature) {
            this.staticElement = staticElement;
            this.name = name;
            this.signature = signature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Key methodKey = (Key) o;
            return staticElement == methodKey.staticElement && Objects.equals(name, methodKey.name) && Objects.equals(signature, methodKey.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(staticElement, name, signature);
        }
    }

    private static final class JNIClassData {

        static final JNIClassData INVALID = new JNIClassData(null);

        private final JClass jClassGlobal;
        private final Map<Key, JNIMethod> resolvedMethods;
        private final Map<Key, Long> resolvedFields;

        JNIClassData(JClass jClassGlobal) {
            this.jClassGlobal = jClassGlobal;
            this.resolvedMethods = new ConcurrentHashMap<>();
            this.resolvedFields = new ConcurrentHashMap<>();
        }

        JNIMethod lookupMethod(JNIEnv env, Key key, boolean required) {
            return resolvedMethods.computeIfAbsent(key,
                            (k) -> JNIMethod.findMethod(env, jClassGlobal, key.staticElement, required, key.name, key.signature));
        }

        JFieldID lookupField(JNIEnv env, Key key) {
            return WordFactory.pointer(resolvedFields.computeIfAbsent(key,
                            (k) -> JNIUtil.findField(env, jClassGlobal, key.staticElement, key.name, key.signature).rawValue()));
        }
    }
}
