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

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.word.WordFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support class for {@link JClass} lookup. JClass instances are cached as JNI globals. The cached
 * JNI globals are disposed by {@link JNIClassCache#dispose(JNIEnv)}.
 */
public final class JNIClassCache {

    private static final Map<String, JNIClassData> classesByName = new ConcurrentHashMap<>();

    private JNIClassCache() {
    }

    /**
     * Looks up JClass using a {@link Class}.
     *
     * @return JNI global reference for {@link JClass}
     * @throws JNIExceptionWrapper wrapping the HotSpot {@link LinkageError} is thrown when class is
     *             not found.
     */
    public static JClass lookupClass(JNIEnv env, Class<?> clazz) throws JNIExceptionWrapper {
        return lookupClass(env, clazz.getName());
    }

    /**
     * Looks up JClass using a fully qualified name.
     *
     * @return JNI global reference for {@link JClass}
     * @throws JNIExceptionWrapper wrapping the HotSpot {@link LinkageError} is thrown when class is
     *             not found.
     */
    public static JClass lookupClass(JNIEnv env, String className) throws JNIExceptionWrapper {
        return lookupClassImpl(env, className, true);
    }

    /**
     * Looks up JClass using a {@link Class}.
     *
     * @return JNI global reference for {@link JClass} or {@link WordFactory#nullPointer() NULL}
     *         when class is not found.
     */
    public static JClass lookupOptionalClass(JNIEnv env, Class<?> clazz) {
        return lookupOptionalClass(env, clazz.getName());
    }

    /**
     * Looks up JClass using a fully qualified name.
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
                res = new JNIClassData(JNIUtil.NewGlobalRef(env, jClass, "Class<" + className + ">"));
            }
            return res;
        }).jClassGlobal;
    }

    /**
     * Disposes cached JNI objects and frees JNI globals. The isolate should call this method before
     * disposing to free host classes held by JNI global references.
     */
    public static void dispose(JNIEnv jniEnv) {
        for (Iterator<JNIClassData> iterator = classesByName.values().iterator(); iterator.hasNext();) {
            JNIClassData classData = iterator.next();
            iterator.remove();
            if (classData != JNIClassData.INVALID) {
                JNIUtil.DeleteGlobalRef(jniEnv, classData.jClassGlobal);
            }
        }
    }

    private static final class JNIClassData {

        static final JNIClassData INVALID = new JNIClassData(null);

        private final JClass jClassGlobal;

        JNIClassData(JClass jClassGlobal) {
            this.jClassGlobal = jClassGlobal;
        }
    }
}
