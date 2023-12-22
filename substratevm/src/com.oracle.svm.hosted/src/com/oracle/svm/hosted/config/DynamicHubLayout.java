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
package com.oracle.svm.hosted.config;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.HubType;
import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.monitor.MultiThreadedMonitorSupport;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.vm.ci.meta.JavaKind;

/**
 * Provides sizes and offsets of the {@link DynamicHub} class. Like {@link Hybrid} layouts,
 * DynamicHubs contain both instance fields and a variable length array to hold the virtual dispatch
 * table. In addition, to save a level of indirection, there is a fixed-length typeid slot directly
 * placed within the dynamic hub.
 *
 * <p>
 * The location of the identity hashcode is configuration-dependent and will follow the same
 * placement convention as an array. See {@link ObjectLayout} for more information on where the
 * identity hash can be placed. DynamicHubs never have a monitor slot; See
 * {@link MultiThreadedMonitorSupport} for more information.
 *
 * <pre>
 *    +--------------------------------------------------+
 *    | object header (same header as for arrays)        |
 *    +--------------------------------------------------+
 *    | vtable length                                    |
 *    +--------------------------------------------------+
 *    | type id slots (i.e., primitive data)             |
 *    |     ...                                          |
 *    +--------------------------------------------------+
 *    | instance fields (i.e., primitive or object data) |
 *    |     ...                                          |
 *    +--------------------------------------------------+
 *    | vtable dispatch addresses (i.e., primitive data) |
 *    |     ...                                          |
 *    +--------------------------------------------------+
 * </pre>
 *
 * <p>
 * Like {@link Hybrid}, DynamicHub objects have an instance {@link HubType}, but a
 * {@link LayoutEncoding} like an array. See the javadoc for {@link Hybrid} more details its
 * implications.
 */
public class DynamicHubLayout {

    private final ObjectLayout layout;
    private final HostedInstanceClass dynamicHubType;
    public final HostedField typeIDSlotsField;
    public final int typeIDSlotsOffset;
    public final int typeIDSlotsSize;
    public final HostedField vTableField;
    public final int vTableSlotSize;
    public final JavaKind vTableSlotStorageKind;

    /*
     * This is calculated lazily, as it requires the dynamicHub's instance fields to be finalized
     * before being calculated.
     */
    private int vTableOffset;

    public DynamicHubLayout(ObjectLayout layout, HostedType dynamicHubType, HostedField typeIDSlotsField, int typeIDSlotsOffset, int typeIDSlotsSize, HostedField vTableField,
                    JavaKind vTableSlotStorageKind, int vTableSlotSize) {
        this.layout = layout;
        this.dynamicHubType = (HostedInstanceClass) dynamicHubType;
        this.typeIDSlotsField = typeIDSlotsField;
        this.typeIDSlotsOffset = typeIDSlotsOffset;
        this.typeIDSlotsSize = typeIDSlotsSize;
        this.vTableField = vTableField;
        this.vTableSlotStorageKind = vTableSlotStorageKind;
        this.vTableSlotSize = vTableSlotSize;
    }

    public static DynamicHubLayout singleton() {
        return ImageSingletons.lookup(DynamicHubLayout.class);
    }

    public JavaKind getVTableSlotStorageKind() {
        return vTableSlotStorageKind;
    }

    public boolean isDynamicHub(HostedType type) {
        return type.equals(dynamicHubType);
    }

    public boolean isInlinedField(HostedField field) {
        return field.equals(typeIDSlotsField) || field.equals(vTableField);
    }

    public int getVTableSlotOffset(int index) {
        return vTableOffset() + index * vTableSlotSize;
    }

    public int getTypeIDSlotsOffset(int index) {
        return typeIDSlotsOffset + index * typeIDSlotsSize;
    }

    public int getVTableLengthOffset() {
        return layout.getArrayLengthOffset();
    }

    public int vTableOffset() {
        if (vTableOffset == 0) {
            vTableOffset = NumUtil.roundUp(dynamicHubType.getAfterFieldsOffset(), vTableSlotSize);
        }
        return vTableOffset;
    }

    public long getTotalSize(int vtableLength) {
        return layout.computeArrayTotalSize(getVTableSlotOffset(vtableLength), true);
    }

    public long getIdentityHashOffset(int vTableLength) {
        return layout.getArrayIdentityHashOffset(getVTableSlotOffset(vTableLength));
    }
}
