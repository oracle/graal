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

import static com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils.throwMissingRegistrationErrors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;

@AutomaticallyRegisteredImageSingleton
public final class ClassForNameSupport {

    static ClassForNameSupport singleton() {
        return ImageSingletons.lookup(ClassForNameSupport.class);
    }

    /** The map used to collect registered classes. */
    private final EconomicMap<String, Object> knownClasses = ImageHeapMap.create();

    private static final Object NEGATIVE_QUERY = new Object();

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerClass(Class<?> clazz) {
        assert !clazz.isPrimitive() : "primitive classes cannot be looked up by name";
        if (PredefinedClassesSupport.isPredefined(clazz)) {
            return; // must be defined at runtime before it can be looked up
        }
        String name = clazz.getName();
        if (!singleton().knownClasses.containsKey(name) || !(singleton().knownClasses.get(name) instanceof Throwable)) {
            /*
             * If the class has already been seen as throwing an error, we don't overwrite this
             * error
             */
            VMError.guarantee(!singleton().knownClasses.containsKey(name) || singleton().knownClasses.get(name) == clazz);
            singleton().knownClasses.put(name, clazz);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerExceptionForClass(String className, Throwable t) {
        singleton().knownClasses.put(className, t);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerNegativeQuery(String className) {
        singleton().knownClasses.put(className, NEGATIVE_QUERY);
    }

    public static Class<?> forNameOrNull(String className, ClassLoader classLoader) {
        try {
            return forName(className, classLoader, true);
        } catch (ClassNotFoundException e) {
            throw VMError.shouldNotReachHere("ClassForNameSupport.forNameOrNull should not throw", e);
        }
    }

    public static Class<?> forName(String className, ClassLoader classLoader) throws ClassNotFoundException {
        return forName(className, classLoader, false);
    }

    private static Class<?> forName(String className, ClassLoader classLoader, boolean returnNullOnException) throws ClassNotFoundException {
        if (className == null) {
            return null;
        }
        Object result = singleton().knownClasses.get(className);
        if (result == NEGATIVE_QUERY) {
            result = new ClassNotFoundException(className);
        }
        if (result == null) {
            result = PredefinedClassesSupport.getLoadedForNameOrNull(className, classLoader);
        }
        // Note: for non-predefined classes, we (currently) don't need to check the provided loader
        // TODO rewrite stack traces (GR-42813)
        if (result instanceof Class<?>) {
            return (Class<?>) result;
        } else if (result instanceof Throwable) {
            if (returnNullOnException) {
                return null;
            }

            if (result instanceof Error) {
                throw (Error) result;
            } else if (result instanceof ClassNotFoundException) {
                throw (ClassNotFoundException) result;
            }
        } else if (result == null) {
            if (throwMissingRegistrationErrors()) {
                MissingReflectionRegistrationUtils.forClass(className);
            }

            if (returnNullOnException) {
                return null;
            } else {
                throw new ClassNotFoundException(className);
            }
        }
        throw VMError.shouldNotReachHere("Class.forName result should be Class, ClassNotFoundException or Error: " + result);
    }

    public static int count() {
        return singleton().knownClasses.size();
    }
}
