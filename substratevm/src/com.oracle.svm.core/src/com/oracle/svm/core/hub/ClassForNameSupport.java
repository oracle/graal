/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.ImageHeapMap;

public final class ClassForNameSupport {

    static ClassForNameSupport singleton() {
        return ImageSingletons.lookup(ClassForNameSupport.class);
    }

    /** The map used to collect registered classes. */
    private final EconomicMap<String, Class<?>> knownClasses = ImageHeapMap.create();
    /** Store class name and checksum byte array as key-value pair. */
    private final EconomicMap<String, byte[]> dynamicGeneratedClasses = ImageHeapMap.create();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(Class<?> clazz) {
        singleton().knownClasses.put(clazz.getName(), clazz);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerDynamicGeneratedClass(Class<?> generatedClazz, String definedClassName, String checksum) {
        registerClass(generatedClazz);
        ImageSingletons.lookup(ClassForNameSupport.class).dynamicGeneratedClasses.put(definedClassName, checksum.getBytes());
    }

    public static Class<?> forNameOrNull(String className, boolean initialize) {
        Class<?> result = singleton().knownClasses.get(className);
        if (result == null) {
            return null;
        }
        if (initialize) {
            DynamicHub.fromClass(result).ensureInitialized();
        }
        return result;
    }

    public static Class<?> forName(String className, boolean initialize) throws ClassNotFoundException {
        Class<?> result = forNameOrNull(className, initialize);
        if (result == null) {
            throw new ClassNotFoundException(className);
        }
        return result;
    }

    public static String getDynamicClassChecksum(String className) throws ClassNotFoundException {
        byte[] storedValue = ImageSingletons.lookup(ClassForNameSupport.class).dynamicGeneratedClasses.get(className);
        if (storedValue == null) {
            throw new ClassNotFoundException(className);
        }
        return new String(storedValue);
    }
}

@AutomaticFeature
final class ClassForNameFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ClassForNameSupport.class, new ClassForNameSupport());
    }
}
