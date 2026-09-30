/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect.target;

import static com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_ConstantPool_Helper.checkTag;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.RuntimeClassLoading.WithRuntimeClassLoading;
import com.oracle.svm.core.hub.crema.CremaSupport;
import com.oracle.svm.espresso.classfile.ConstantPool;

/**
 * All usages of ConstantPool are substituted to go through
 * {@link com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder.MetadataAccessor}.
 * <p>
 * In Native Image, the constant pool is not used. However, in the context of Layered Image, the
 * constant pool needs to be able to provide the layer number it is associated with. This is because
 * the {@link com.oracle.svm.guest.staging.reflect.RuntimeMetadataDecoder.MetadataAccessor} needs a layer
 * number for retrieving the information in the correct layer and in some cases, only the constant
 * pool can provide this information. The constant pool is only used with Layered Image, and only to
 * provide a layer number.
 */
@TargetClass(className = "jdk.internal.reflect.ConstantPool")
@Substitute
public final class Target_jdk_internal_reflect_ConstantPool {
    /**
     * The layer number associated with this constant pool.
     */
    private final int layerId;

    /**
     * Used only when run-time class loading is enabled and non-null only for run-time-loaded
     * classes.
     */
    ConstantPool constantPool;

    public Target_jdk_internal_reflect_ConstantPool(int layerId, DynamicHub hub) {
        this.layerId = layerId;
        if (RuntimeClassLoading.isSupported() && hub != null && hub.isRuntimeLoaded()) {
            this.constantPool = CremaSupport.singleton().getConstantPool(hub);
        }
    }

    public int getLayerId() {
        return layerId;
    }

    boolean isRuntimeLoaded() {
        return constantPool != null;
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public String getUTF8At(int index) {
        checkTag(constantPool, ConstantPool.Tag.UTF8, index);
        return constantPool.utf8At(index).toString();
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public double getDoubleAt(int index) {
        checkTag(constantPool, ConstantPool.Tag.DOUBLE, index);
        return constantPool.doubleAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public float getFloatAt(int index) {
        checkTag(constantPool, ConstantPool.Tag.FLOAT, index);
        return constantPool.floatAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public long getLongAt(int index) {
        checkTag(constantPool, ConstantPool.Tag.LONG, index);
        return constantPool.longAt(index);
    }

    @Substitute
    @TargetElement(onlyWith = WithRuntimeClassLoading.class)
    public int getIntAt(int index) {
        checkTag(constantPool, ConstantPool.Tag.INTEGER, index);
        return constantPool.intAt(index);
    }
}

final class Target_jdk_internal_reflect_ConstantPool_Helper {
    static void checkTag(ConstantPool constantPool, ConstantPool.Tag expected, int index) {
        if (index < 0 || index >= constantPool.length()) {
            throw new IllegalArgumentException("Constant pool index out of bounds");
        }
        ConstantPool.Tag tag = constantPool.tagAt(index);
        if (tag != expected) {
            throw new IllegalArgumentException("Wrong type at constant pool index");
        }
    }
}
