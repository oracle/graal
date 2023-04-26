/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Objects;

import com.oracle.svm.core.util.VMError;

public final class RecordUtils {

    public static Method[] getRecordComponentAccessorMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getRecordComponents())
                        .map(RecordComponent::getAccessor)
                        .filter(Objects::nonNull)
                        .toArray(Method[]::new);
    }

    public static Constructor<?> getCanonicalRecordConstructor(Class<?> clazz) {
        Class<?>[] paramTypes = Arrays.stream(clazz.getRecordComponents())
                        .map(RecordComponent::getType)
                        .toArray(Class<?>[]::new);
        try {
            Constructor<?> ctr = clazz.getDeclaredConstructor(paramTypes);
            ctr.setAccessible(true);
            return ctr;
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere("Malformed record class that does not declare a canonical constructor: " + clazz.getTypeName());
        }
    }
}
