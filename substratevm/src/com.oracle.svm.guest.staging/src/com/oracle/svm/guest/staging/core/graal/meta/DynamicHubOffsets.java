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
package com.oracle.svm.guest.staging.core.graal.meta;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.impl.BarrieredAccess;

import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.meta.GuestFold;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/**
 * Collects runtime field offsets for {@code DynamicHub} and {@code DynamicHubCompanion} and allows
 * writing those fields in a way that is hidden to analysis.
 * <p>
 * There are 2 reasons to hide the fact that these fields are written to:
 * <ul>
 * <li>{@code DynamicHub} that are part of the image heap must be stored in the read-only part of
 * the image heap</li>
 * <li>Some of these fields are read in snippets and must be considered "immutable" to respect rules
 * about memory reads appearing during lowering of nodes that didn't previously read those
 * locations. Locations as considered immutable when analysis determines that they are never written
 * to (see {@code com.oracle.svm.core.graal.nodes.SubstrateFieldLocationIdentity}). Runtime class
 * loading creates {@code DynamicHub} and {@code DynamicHubCompanion} and using standard writes to
 * those fields during their initialization would cause those fields to no longer be immutable.
 * Those fields must only be written once during initialization.</li>
 * </ul>
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = PartiallyLayerAware.class)
public class DynamicHubOffsets {
    private static final int UNINITIALIZED = -1;

    private final DynamicHubCompanionOffsets companionOffsets = new DynamicHubCompanionOffsets();

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
    private int numIterableInterfaceTypesOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int interfaceIDOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int openTypeWorldTypeCheckSlotsOffset = UNINITIALIZED;

    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int openTypeWorldInterfaceHashParamOffset = UNINITIALIZED;
    @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
    private int openTypeWorldInterfaceHashTableOffset = UNINITIALIZED;

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

    private static final class DynamicHubCompanionOffsets {
        @UnknownPrimitiveField(availability = BuildPhaseProvider.ReadyForCompilation.class) //
        private int additionalFlagsOffset = UNINITIALIZED;
    }

    @GuestFold
    public static DynamicHubOffsets singleton() {
        return ImageSingletons.lookup(DynamicHubOffsets.class);
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

    public int getNumIterableInterfaceTypesOffset() {
        return numIterableInterfaceTypesOffset;
    }

    public int getInterfaceIDOffset() {
        return interfaceIDOffset;
    }

    public int getOpenTypeWorldTypeCheckSlotsOffset() {
        return openTypeWorldTypeCheckSlotsOffset;
    }

    public int getOpenTypeWorldInterfaceHashParamOffset() {
        return openTypeWorldInterfaceHashParamOffset;
    }

    public int getOpenTypeWorldInterfaceHashTableOffset() {
        return openTypeWorldInterfaceHashTableOffset;
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

    public int getCompanionAdditionalFlagsOffset() {
        return companionOffsets.additionalFlagsOffset;
    }

    /* Restore DynamicHub and DynamicHubCompanion receiver types after they move to guest staging (GR-78110). */
    public static void writeObject(Object hub, int offset, Object value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeObject(hub, offset, value);
    }

    /* Restore DynamicHub and DynamicHubCompanion receiver types after they move to guest staging (GR-78110). */
    public static void writeInt(Object hub, int offset, int value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeInt(hub, offset, value);
    }

    /* Restore DynamicHub and DynamicHubCompanion receiver types after they move to guest staging (GR-78110). */
    public static void writeShort(Object hub, int offset, short value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeShort(hub, offset, value);
    }

    /* Restore DynamicHub and DynamicHubCompanion receiver types after they move to guest staging (GR-78110). */
    public static void writeChar(Object hub, int offset, char value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeChar(hub, offset, value);
    }

    /* Restore DynamicHub and DynamicHubCompanion receiver types after they move to guest staging (GR-78110). */
    public static void writeByte(Object hub, int offset, byte value) {
        if (offset < 0) {
            /* field removed by analysis */
            return;
        }
        BarrieredAccess.writeByte(hub, offset, value);
    }
}
