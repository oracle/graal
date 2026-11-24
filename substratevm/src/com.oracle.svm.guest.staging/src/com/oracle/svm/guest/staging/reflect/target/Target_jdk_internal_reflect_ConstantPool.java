/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.reflect.target;

import java.util.function.BooleanSupplier;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.guest.staging.reflect.RuntimeConstantPool;

/**
 * Runtime access is required for Crema-loaded classes; layered-image users also use this facade to
 * carry the layer number associated with reflection metadata.
 */
@TargetClass(className = "jdk.internal.reflect.ConstantPool")
@Substitute
public final class Target_jdk_internal_reflect_ConstantPool {
    private final int layerId;

    /** Non-null only for run-time-loaded classes. */
    private final RuntimeConstantPool constantPool;

    public Target_jdk_internal_reflect_ConstantPool(int layerId, Class<?> declaringClass) {
        this.layerId = layerId;
        this.constantPool = declaringClass == null ? null : GuestStagingDependencyBridge.singleton().getRuntimeConstantPool(declaringClass);
    }

    public int getLayerId() {
        return layerId;
    }

    public boolean isRuntimeLoaded() {
        return constantPool != null;
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public String getUTF8At(int index) {
        return constantPool.getUTF8At(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public double getDoubleAt(int index) {
        return constantPool.getDoubleAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public float getFloatAt(int index) {
        return constantPool.getFloatAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public long getLongAt(int index) {
        return constantPool.getLongAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public int getIntAt(int index) {
        return constantPool.getIntAt(index);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static final class WithRuntimeClassLoading implements BooleanSupplier {
        @Override
        public boolean getAsBoolean() {
            return GuestStagingDependencyBridge.singleton().isRuntimeClassLoadingSupported();
        }
    }
}
