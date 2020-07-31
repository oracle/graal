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
package com.oracle.svm.reflect.serialize;

import com.oracle.svm.core.configure.SerializationKey;
import com.oracle.svm.core.jdk.serialize.SerializationRegistry;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SerializationSupport implements SerializationRegistry {
    // Cached SerializationConstructorAccessors for runtime usage
    private Map<String, Object> cachedSerializationConstructorAccessors;

    /*
     * This map is used to track multiple classloader usage Map of serialization target class and a
     * list of class T that stores the parameters to generate its SerializationConstructorAccessor
     * class and other relevant information. Each T entity represents one generation. One target
     * class usually has only one generation, multiple-generations is a suggestion of multiple
     * classloader usage.
     */
    private Map<String, List<SerializationKey<?>>> accessorDefinitions;

    public SerializationSupport() {
        cachedSerializationConstructorAccessors = new ConcurrentHashMap<>();
        accessorDefinitions = new ConcurrentHashMap<>();
    }

    @Override
    public String collectMultiDefinitions() {
        StringBuilder sb = new StringBuilder();
        accessorDefinitions.forEach((targetClass, definitions) -> {
            int size = definitions.size();
            if (size > 1) {
                sb.append("Suspicious multiple-classloader usage is detected:\n");
                sb.append("There are " + size + " SerializationConstructorAccessor classes have been defined for the same serialization target class:\n\n");
                int i = 0;
                while (i < size) {
                    sb.append("(").append((i + 1)).append(")");
                    sb.append(definitions.get(i).toString());
                    sb.append("\n");
                    i++;
                }
            }
        });
        return sb.toString();
    }

    @Platforms({Platform.HOSTED_ONLY.class})
    @Override
    public void addSerializationConstructorAccessorClass(Class<?> serializationTargetClass, Class<?>[] paramterTypes, Class<?>[] checkedExceptions,
                    int modifiers, Class<?> targetConstructorClass) {
        SerializationKey<Class<?>> serializationKey = new SerializationKey<>(serializationTargetClass, paramterTypes, checkedExceptions, modifiers, targetConstructorClass);
        cachedSerializationConstructorAccessors.computeIfAbsent(serializationKey.asKey(), (k) -> SerializationRegistry.createSerializationConstructorAccessorClass(
                        serializationTargetClass, paramterTypes, checkedExceptions, modifiers, targetConstructorClass));
        String targetClassName = serializationTargetClass.getName();
        if (accessorDefinitions.containsKey(targetClassName)) {
            accessorDefinitions.get(targetClassName).add(serializationKey);
        } else {
            List<SerializationKey<?>> value = new ArrayList<>();
            value.add(serializationKey);
            accessorDefinitions.put(targetClassName, value);
        }
    }

    @Override
    public Object getSerializationConstructorAccessorClass(Class<?> serializationTargetClass, Class<?>[] parameterTypes, Class<?>[] checkedExceptions, int modifiers, Class<?> targetConstructorClass) {
        SerializationKey<Class<?>> key = new SerializationKey<>(serializationTargetClass, parameterTypes, checkedExceptions, modifiers, targetConstructorClass);
        Object ret = cachedSerializationConstructorAccessors.get(key.toString());
        if (ret == null) {
            throw VMError.unsupportedFeature("SerializationConstructorAccessor class is not found for SerializationKey:\n" + key.toString() +
                            "Generating SerializationConstructorAccessor classes at runtime is not supported. ");
        }
        return ret;
    }

}
