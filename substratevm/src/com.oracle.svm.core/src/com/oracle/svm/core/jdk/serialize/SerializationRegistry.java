/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Alibaba Group Holding Limited. All Rights Reserved.
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
package com.oracle.svm.core.jdk.serialize;

import com.oracle.svm.core.jdk.Package_jdk_internal_reflect;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public interface SerializationRegistry {
    @Platforms(Platform.HOSTED_ONLY.class)
    void addSerializationConstructorAccessorClass(Class<?> serializationTargetClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions,
                    int modifiers, Class<?> targetConstructorClass);

    @Platforms(Platform.HOSTED_ONLY.class)
    String collectMultiDefinitions();

    Object getSerializationConstructorAccessorClass(Class<?> serializationTargetClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions,
                    int modifiers, Class<?> targetConstructorClass);

    @Platforms(Platform.HOSTED_ONLY.class)
    static Object createSerializationConstructorAccessorClass(Class<?> serializationTargetClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions,
                    int modifiers, Class<?> targetConstructorClass) {
        try {
            Class<?> generatorClass = Class.forName(Package_jdk_internal_reflect.getQualifiedName() + ".MethodAccessorGenerator");
            Constructor<?> c = generatorClass.getDeclaredConstructor();
            c.setAccessible(true);
            Object generator = c.newInstance();
            Method generateMethod = generatorClass.getMethod("generateSerializationConstructor", Class.class, Class[].class, Class[].class, int.class, Class.class);
            generateMethod.setAccessible(true);
            return generateMethod.invoke(generator, serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructorClass);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            throw new UnsupportedOperationException("Cannot create SerializationConstructorAccessor class for " + serializationTargetClass.getName(), e);
        }
    }
}
