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

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Provides sizes and offsets of a hybrid class.
 *
 * @see Hybrid
 */
public class HybridLayout {

    public static boolean isHybrid(ResolvedJavaType clazz) {
        return HybridLayoutSupport.singleton().isHybrid(clazz);
    }

    public static boolean isHybridField(HostedField field) {
        return HybridLayoutSupport.singleton().isHybridField(field);
    }

    /**
     * See {@link HybridLayoutSupport#canHybridFieldsBeDuplicated(HostedType)} for explanation.
     */
    public static boolean canHybridFieldsBeDuplicated(HostedType clazz) {
        return HybridLayoutSupport.singleton().canHybridFieldsBeDuplicated(clazz);
    }

    /**
     * See {@link HybridLayoutSupport#canInstantiateAsInstance(HostedType)} for explanation.
     */
    public static boolean canInstantiateAsInstance(HostedType clazz) {
        return HybridLayoutSupport.singleton().canInstantiateAsInstance(clazz);
    }

    private final ObjectLayout layout;
    private final HostedType arrayComponentType;
    private final HostedField arrayField;
    private final int arrayBaseOffset;

    @SuppressWarnings("this-escape")
    public HybridLayout(HostedInstanceClass hybridClass, ObjectLayout layout, MetaAccessProvider metaAccess) {
        this.layout = layout;
        HybridLayoutSupport.HybridInfo hybridInfo = HybridLayoutSupport.singleton().inspectHybrid(hybridClass, metaAccess);
        this.arrayComponentType = hybridInfo.arrayComponentType;
        this.arrayField = hybridInfo.arrayField;
        this.arrayBaseOffset = NumUtil.roundUp(hybridClass.getAfterFieldsOffset(), layout.sizeInBytes(getArrayElementStorageKind()));
    }

    public HostedType getArrayComponentType() {
        return arrayComponentType;
    }

    public JavaKind getArrayElementStorageKind() {
        return arrayComponentType.getStorageKind();
    }

    public int getArrayBaseOffset() {
        return arrayBaseOffset;
    }

    public long getArrayElementOffset(int index) {
        return getArrayBaseOffset() + ((long) index) * layout.sizeInBytes(getArrayElementStorageKind());
    }

    public long getTotalSize(int length, boolean withOptionalIdHashField) {
        return layout.computeArrayTotalSize(getArrayElementOffset(length), withOptionalIdHashField);
    }

    public long getIdentityHashOffset(int length) {
        return layout.getArrayIdentityHashOffset(getArrayElementOffset(length));
    }

    public HostedField getArrayField() {
        return arrayField;
    }
}
