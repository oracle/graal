/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;

import com.oracle.svm.hosted.reflect.ReflectionDataBuilder;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeProxyRegistrySupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;
import org.graalvm.nativeimage.dynamicaccess.ReflectiveAccess;

public final class InternalReflectiveAccess implements ReflectiveAccess {

    private final ReflectionDataBuilder rrsInstance;
    private static InternalReflectiveAccess instance;

    private InternalReflectiveAccess() {
        rrsInstance = (ReflectionDataBuilder) ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    public static InternalReflectiveAccess singleton() {
        if (instance == null) {
            instance = new InternalReflectiveAccess();
        }
        return instance;
    }

    @Override
    public void register(AccessCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            rrsInstance.register(condition, clazz);
            rrsInstance.registerClassMetadata(condition, clazz);
        }
    }

    @Override
    public void registerForUnsafeAllocation(AccessCondition condition, Class<?>... classes) {
        rrsInstance.registerUnsafeAllocation(condition, false, classes);
    }

    @Override
    public void register(AccessCondition condition, Executable... executables) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(executables)
                        .map(Executable::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);

        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, false, executables);
    }

    @Override
    public void register(AccessCondition condition, Field... fields) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(fields)
                        .map(Field::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);
        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, false, fields);
    }

    @Override
    public void registerForSerialization(AccessCondition condition, Class<?>... classes) {
        RuntimeSerializationSupport.singleton().register(condition, classes);
        for (Class<?> clazz : classes) {
            rrsInstance.registerClassMetadata(condition, clazz);
        }
    }

    @Override
    public Class<?> registerProxy(AccessCondition condition, Class<?>... interfaces) {
        Class<?> proxy = ImageSingletons.lookup(RuntimeProxyRegistrySupport.class).registerProxy(condition, false, interfaces);
        register(condition, proxy);
        return proxy;
    }
}
