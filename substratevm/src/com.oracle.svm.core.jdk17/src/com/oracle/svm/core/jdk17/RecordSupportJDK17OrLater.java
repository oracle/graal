/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk17;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jdk.RecordSupport;
import com.oracle.svm.core.util.VMError;

final class RecordSupportJDK17OrLater extends RecordSupport {
    @Override
    public boolean isRecord(Class<?> clazz) {
        return clazz.isRecord();
    }

    @Override
    public Object[] getRecordComponents(Class<?> clazz) {
        return clazz.getRecordComponents();
    }

    @Override
    public Method[] getRecordComponentAccessorMethods(Class<?> clazz) {
        return Arrays.stream(clazz.getRecordComponents())
                        .map(RecordComponent::getAccessor)
                        .toArray(Method[]::new);
    }

    @Override
    public Constructor<?> getCanonicalRecordConstructor(Class<?> clazz) {
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

@AutomaticFeature
final class RecordFeatureJDK17OrLater implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 17;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RecordSupport.class, new RecordSupportJDK17OrLater());
    }
}
