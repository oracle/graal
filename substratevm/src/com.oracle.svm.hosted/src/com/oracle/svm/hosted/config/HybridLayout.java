/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Hybrid;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMetaAccess;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Defines the layout for a hybrid class.
 *
 * @see Hybrid
 *
 * @param <T> The class which has a layout in hybrid form. It must be annotated with the
 *            {@link Hybrid} annotation.
 */
public class HybridLayout<T> {

    public static boolean isHybrid(ResolvedJavaType clazz) {
        return ImageSingletons.lookup(HybridLayoutSupport.class).isHybrid(clazz);
    }

    public static boolean isHybridField(ResolvedJavaField field) {
        return ImageSingletons.lookup(HybridLayoutSupport.class).isHybridField(field);
    }

    private final ObjectLayout layout;
    private final HostedField arrayField;
    private final HostedField bitsetField;
    private final int instanceSize;

    public HybridLayout(Class<T> hybridClass, ObjectLayout layout, HostedMetaAccess metaAccess) {
        this((HostedInstanceClass) metaAccess.lookupJavaType(hybridClass), layout);
    }

    public HybridLayout(HostedInstanceClass hybridClass, ObjectLayout layout) {
        this.layout = layout;
        HybridLayoutSupport utils = ImageSingletons.lookup(HybridLayoutSupport.class);
        Pair<HostedField, HostedField> arrayAndBitsetFields = utils.findHybridFields(hybridClass);
        arrayField = arrayAndBitsetFields.getLeft();
        bitsetField = arrayAndBitsetFields.getRight();
        instanceSize = hybridClass.getInstanceSize();
    }

    public JavaKind getArrayElementStorageKind() {
        return arrayField.getType().getComponentType().getStorageKind();
    }

    public int getArrayBaseOffset() {
        return NumUtil.roundUp(instanceSize, layout.sizeInBytes(getArrayElementStorageKind()));
    }

    public long getArrayElementOffset(int index) {
        return getArrayBaseOffset() + index * layout.sizeInBytes(getArrayElementStorageKind());
    }

    public long getTotalSize(int length) {
        return layout.alignUp(getArrayElementOffset(length));
    }

    public HostedField getArrayField() {
        return arrayField;
    }

    public HostedField getBitsetField() {
        return bitsetField;
    }

    public int getInstanceSize() {
        return instanceSize;
    }

    public int getBitFieldOffset() {
        return layout.getArrayLengthOffset() + layout.sizeInBytes(JavaKind.Int);
    }
}
