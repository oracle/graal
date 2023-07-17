/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.jniutils.JNI.JClass;
import org.graalvm.jniutils.JNI.JNIEnv;
import org.graalvm.jniutils.JNIExceptionWrapper;
import org.graalvm.jniutils.JNIUtil;
import org.graalvm.word.WordFactory;

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

    private static JClass lookupClassImpl(JNIEnv env, String className, boolean required) {
        Function<String, JNIClassData> createClassData = new Function<>() {
            @Override
            public JNIClassData apply(String cn) {
                JClass jClass = JNIUtil.findClass(env, WordFactory.nullPointer(), JNIUtil.getBinaryName(className), required);
                if (jClass.isNull()) {
                    return JNIClassData.INVALID;
                }
                return new JNIClassData(JNIUtil.NewGlobalRef(env, jClass, "Class<" + className + ">"));
            }
        };
        return classesByName.computeIfAbsent(className, createClassData).jClassGlobal;
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
