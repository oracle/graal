/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.InjectAccessors;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public class DynamicHubOffsets {
    private static final int UNINITIALIZED = -1;
    /* defining order in DynamicHub */

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int nameOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int hubTypeOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int referenceTypeOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int layoutEncodingOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int typeIDOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int typeIDDepthOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int numClassTypesOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int numInterfaceTypesOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int openTypeWorldTypeCheckSlotsOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int monitorOffsetOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int identityHashOffsetOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int flagsOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int componentTypeOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int referenceMapCompressedOffsetOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int layerIdOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int companionOffset = UNINITIALIZED;

    @Fold
    public static DynamicHubOffsets singleton() {
        return ImageSingletons.lookup(DynamicHubOffsets.class);
    }

    private static final String[] SKIPPED_FIELDS = new String[]{
                    /* closed world only */
                    "typeCheckStart", "typeCheckRange", "typeCheckSlot", "closedTypeWorldTypeCheckSlots",

                    /* handled by KnownOffsets */
                    "vtable"
    };

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initializeOffsets(MetaAccessProvider metaAccess) {
        for (ResolvedJavaField field : metaAccess.lookupJavaType(DynamicHub.class).getInstanceFields(true)) {
            if (Arrays.stream(SKIPPED_FIELDS).anyMatch(field.getName()::equals)) {
                continue;
            }

            if (AnnotationAccess.isAnnotationPresent(field, InjectAccessors.class)) {
                continue;
            }

            try {
                Field offsetField = ReflectionUtil.lookupField(DynamicHubOffsets.class, field.getName() + "Offset");
                offsetField.setInt(singleton(), field.getOffset());
            } catch (IllegalAccessException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
        // Ensure the expected fields exist
        for (Field field : DynamicHubOffsets.class.getDeclaredFields()) {
            String name = field.getName();
            if (!name.endsWith("Offset")) {
                continue;
            }
            try {
                DynamicHub.class.getDeclaredField(name.substring(0, name.length() - "Offset".length()));
            } catch (NoSuchFieldException e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    public int getNameOffset() {
        return nameOffset;
    }

    public int getHubTypeOffset() {
        return hubTypeOffset;
    }

    public int getReferenceTypeOffset() {
        return referenceTypeOffset;
    }

    public int getLayoutEncodingOffset() {
        return layoutEncodingOffset;
    }

    public int getTypeIDOffset() {
        return typeIDOffset;
    }

    public int getTypeIDDepthOffset() {
        return typeIDDepthOffset;
    }

    public int getNumClassTypesOffset() {
        return numClassTypesOffset;
    }

    public int getNumInterfaceTypesOffset() {
        return numInterfaceTypesOffset;
    }

    public int getOpenTypeWorldTypeCheckSlotsOffset() {
        return openTypeWorldTypeCheckSlotsOffset;
    }

    public int getMonitorOffsetOffset() {
        return monitorOffsetOffset;
    }

    public int getIdentityHashOffsetOffset() {
        return identityHashOffsetOffset;
    }

    public int getFlagsOffset() {
        return flagsOffset;
    }

    public int getComponentTypeOffset() {
        return componentTypeOffset;
    }

    public int getReferenceMapCompressedOffsetOffset() {
        return referenceMapCompressedOffsetOffset;
    }

    public int getLayerIdOffset() {
        return layerIdOffset;
    }

    public int getCompanionOffset() {
        return companionOffset;
    }

    public static void writeObject(DynamicHub hub, int offset, Object value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeObject(hub, offset, value);
    }

    public static void writeInt(DynamicHub hub, int offset, int value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeInt(hub, offset, value);
    }

    public static void writeShort(DynamicHub hub, int offset, short value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeShort(hub, offset, value);
    }

    public static void writeChar(DynamicHub hub, int offset, char value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeChar(hub, offset, value);
    }

    public static void writeByte(DynamicHub hub, int offset, byte value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeByte(hub, offset, value);
    }
}
