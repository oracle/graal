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
package com.oracle.svm.core.jdk;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.VMError;

/**
 * Abstracts the information about Record classes, which are not available in Java 11 and Java 8.
 * This class provides all information about Record classes without exposing any JDK types and
 * methods that are not yet present in the old JDKs.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public abstract class RecordSupport {

    public static RecordSupport singleton() {
        return ImageSingletons.lookup(RecordSupport.class);
    }

    /** Same as {@code Class.isRecord()}. */
    public abstract boolean isRecord(Class<?> clazz);

    /**
     * Same as {@code Class.getRecordComponents()}.
     *
     * Must only be called when {@link #isRecord} returns true.
     */
    public abstract Object[] getRecordComponents(Class<?> clazz);

    /**
     * Returns the {@code RecordComponent.getAccessor} method for each of the
     * {@code Class.getRecordComponents()}.
     *
     * Must only be called when {@link #isRecord} returns true.
     */
    public abstract Method[] getRecordComponentAccessorMethods(Class<?> clazz);

    /**
     * Returns the canonical record constructor that is present in every record class and sets all
     * record components.
     *
     * Must only be called when {@link #isRecord} returns true.
     */
    public abstract Constructor<?> getCanonicalRecordConstructor(Class<?> clazz);
}

/**
 * Placeholder implementation for JDK versions that do not have Record classes. Since
 * {@link #isRecord} always returns false, the other methods must never be invoked.
 */
final class RecordSupportJDK11OrEarlier extends RecordSupport {
    @Override
    public boolean isRecord(Class<?> clazz) {
        return false;
    }

    @Override
    public Object[] getRecordComponents(Class<?> clazz) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public Method[] getRecordComponentAccessorMethods(Class<?> clazz) {
        throw VMError.shouldNotReachHere();
    }

    @Override
    public Constructor<?> getCanonicalRecordConstructor(Class<?> clazz) {
        throw VMError.shouldNotReachHere();
    }
}

@AutomaticFeature
final class RecordFeatureBeforeJDK17 implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC <= 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RecordSupport.class, new RecordSupportJDK11OrEarlier());
    }
}
