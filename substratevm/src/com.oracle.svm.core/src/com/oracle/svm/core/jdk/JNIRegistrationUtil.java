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
package com.oracle.svm.core.jdk;

/* Checkstyle: allow reflection */
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.util.ReflectionUtil;

/**
 * Utility methods used by features that perform JNI registration.
 */
public class JNIRegistrationUtil {

    protected static boolean isPosix() {
        return Platform.includedIn(InternalPlatform.LINUX_JNI.class) || Platform.includedIn(InternalPlatform.DARWIN_JNI.class);
    }

    protected static boolean isWindows() {
        return Platform.includedIn(Platform.WINDOWS.class);
    }

    protected static void rerunClassInit(FeatureAccess access, String... classNames) {
        RuntimeClassInitializationSupport classInitSupport = ImageSingletons.lookup(RuntimeClassInitializationSupport.class);
        for (String className : classNames) {
            classInitSupport.rerunInitialization(clazz(access, className), "for JDK native code support via JNI");
        }
    }

    protected static Class<?> clazz(FeatureAccess access, String className) {
        return access.findClassByName(className);
    }

    protected static Method method(FeatureAccess access, String className, String methodName, Class<?>... parameterTypes) {
        return ReflectionUtil.lookupMethod(clazz(access, className), methodName, parameterTypes);
    }

    protected static Constructor<?> constructor(FeatureAccess access, String className, Class<?>... parameterTypes) {
        return ReflectionUtil.lookupConstructor(clazz(access, className), parameterTypes);
    }

    protected static Field[] fields(FeatureAccess access, String className, String... fieldNames) {
        Class<?> clazz = clazz(access, className);
        Field[] result = new Field[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            result[i] = ReflectionUtil.lookupField(clazz, fieldNames[i]);
        }
        return result;
    }
}
