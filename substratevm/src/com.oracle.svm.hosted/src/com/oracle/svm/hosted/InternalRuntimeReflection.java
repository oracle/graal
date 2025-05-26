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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.RuntimeProxyCreationSupport;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeSerializationSupport;

public final class InternalRuntimeReflection implements RuntimeReflection {

    private final RuntimeReflectionSupport rrsInstance;

    InternalRuntimeReflection() {
        rrsInstance = ImageSingletons.lookup(RuntimeReflectionSupport.class);
    }

    @Override
    public void register(AccessCondition condition, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            rrsInstance.register(condition, clazz);
            registerClassMembers(condition, clazz);
        }
    }

    @Override
    public void registerClassLookup(AccessCondition condition, String className) {
        try {
            Class<?> clazz = Class.forName(className, false, ClassLoader.getSystemClassLoader());
            registerClassMembers(condition, clazz);
        } catch (Throwable t) {
        }
        rrsInstance.registerClassLookup(condition, className);
    }

    @Override
    public void registerUnsafeAllocation(AccessCondition condition, Class<?>... classes) {
        rrsInstance.registerUnsafeAllocation(condition, classes);
    }

    @Override
    public void register(AccessCondition condition, Executable... methods) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(methods)
                        .map(Executable::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);

        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, methods);
    }

    @Override
    public void register(AccessCondition condition, Field... fields) {
        Class<?>[] uniqueDeclaringClasses = java.util.Arrays.stream(fields)
                        .map(Field::getDeclaringClass)
                        .distinct()
                        .toArray(Class<?>[]::new);
        register(condition, uniqueDeclaringClasses);
        rrsInstance.register(condition, false, fields);
    }

    @Override
    public void registerForSerialization(AccessCondition condition, Class<?>... classes) {
        register(condition, classes);
        RuntimeSerializationSupport.singleton().register(condition, classes);
    }

    @Override
    public Class<?> registerProxy(AccessCondition condition, Class<?>... interfaces) {
        return ImageSingletons.lookup(RuntimeProxyCreationSupport.class).addProxyClass(AccessCondition.always(), interfaces);
    }

    private void registerClassMembers(AccessCondition condition, Class<?> clazz) {
        rrsInstance.registerAllClassesQuery(condition, clazz);
        rrsInstance.registerAllDeclaredClassesQuery(condition, clazz);
        rrsInstance.registerAllDeclaredMethodsQuery(condition, true, clazz);
        rrsInstance.registerAllMethodsQuery(condition, true, clazz);
        rrsInstance.registerAllDeclaredConstructorsQuery(condition, true, clazz);
        rrsInstance.registerAllConstructorsQuery(condition, true, clazz);
        rrsInstance.registerAllFieldsQuery(condition, true, clazz);
        rrsInstance.registerAllDeclaredFieldsQuery(condition, true, clazz);
        rrsInstance.registerAllNestMembersQuery(condition, clazz);
        rrsInstance.registerAllPermittedSubclassesQuery(condition, clazz);
        rrsInstance.registerAllRecordComponentsQuery(condition, clazz);
        rrsInstance.registerAllSignersQuery(condition, clazz);
    }
}
