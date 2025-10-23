/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Predicate;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.sdk.staging.layeredimage.ImageLayerBuildingSupport;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueLoader;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueWriter;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredPersistFlags;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTrait;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraitKind;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

public final class KnownOffsets {
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int vtableBaseOffset;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int vtableEntrySize;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int typeIDSlotsOffset;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int javaFrameAnchorLastSPOffset;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int javaFrameAnchorLastIPOffset;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int vmThreadStatusOffset;
    @UnknownPrimitiveField(availability = ReadyForCompilation.class) //
    private int imageCodeInfoCodeStartOffset;

    @Fold
    public static KnownOffsets singleton() {
        return ImageSingletons.lookup(KnownOffsets.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setLazyState(int vtableBaseOffset, int vtableEntrySize, int typeIDSlotsOffset,
                    int javaFrameAnchorLastSPOffset, int javaFrameAnchorLastIPOffset,
                    int vmThreadStatusOffset, int imageCodeInfoCodeStartOffset) {
        assert !isFullyInitialized();

        this.vtableBaseOffset = vtableBaseOffset;
        this.vtableEntrySize = vtableEntrySize;
        this.typeIDSlotsOffset = typeIDSlotsOffset;

        this.javaFrameAnchorLastSPOffset = javaFrameAnchorLastSPOffset;
        this.javaFrameAnchorLastIPOffset = javaFrameAnchorLastIPOffset;
        this.vmThreadStatusOffset = vmThreadStatusOffset;
        this.imageCodeInfoCodeStartOffset = imageCodeInfoCodeStartOffset;

        assert isFullyInitialized();

        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            int[] currentValues = {
                            vtableBaseOffset,
                            vtableEntrySize,
                            typeIDSlotsOffset,

                            javaFrameAnchorLastSPOffset,
                            javaFrameAnchorLastIPOffset,
                            vmThreadStatusOffset,
                            imageCodeInfoCodeStartOffset,
            };
            var numFields = Arrays.stream(KnownOffsets.class.getDeclaredFields()).filter(Predicate.not(Field::isSynthetic)).count();
            VMError.guarantee(numFields == currentValues.length, "Missing fields");

            if (ImageLayerBuildingSupport.buildingInitialLayer()) {
                ImageSingletons.add(PriorKnownOffsets.class, new PriorKnownOffsets(currentValues));
            } else {
                VMError.guarantee(Arrays.equals(currentValues, ImageSingletons.lookup(PriorKnownOffsets.class).priorValues));
            }
        }
    }

    private boolean isFullyInitialized() {
        return vtableEntrySize > 0;
    }

    public int getVTableBaseOffset() {
        return vtableBaseOffset;
    }

    public int getVTableEntrySize() {
        return vtableEntrySize;
    }

    /**
     * Returns of the offset of the index either relative to the start of the vtable
     * ({@code fromDynamicHubStart} == false) or start of the dynamic hub
     * ({@code fromDynamicHubStart} == true).
     */
    public int getVTableOffset(int vTableIndex, boolean fromDynamicHubStart) {
        assert isFullyInitialized();
        if (fromDynamicHubStart) {
            return vtableBaseOffset + vTableIndex * vtableEntrySize;
        } else {
            return vTableIndex * vtableEntrySize;
        }
    }

    public int getTypeIDSlotsOffset() {
        assert isFullyInitialized() && SubstrateOptions.useClosedTypeWorldHubLayout();
        return typeIDSlotsOffset;
    }

    public int getJavaFrameAnchorLastSPOffset() {
        assert isFullyInitialized();
        return javaFrameAnchorLastSPOffset;
    }

    public int getJavaFrameAnchorLastIPOffset() {
        assert isFullyInitialized();
        return javaFrameAnchorLastIPOffset;
    }

    public int getVMThreadStatusOffset() {
        assert isFullyInitialized();
        assert vmThreadStatusOffset != -1;
        return vmThreadStatusOffset;
    }

    public int getImageCodeInfoCodeStartOffset() {
        assert isFullyInitialized();
        return imageCodeInfoCodeStartOffset;
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = PriorKnownOffsets.LayeredCallbacks.class, layeredInstallationKind = Independent.class)
    static class PriorKnownOffsets {
        final int[] priorValues;

        PriorKnownOffsets(int[] priorValues) {
            this.priorValues = priorValues;
        }

        static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
            @Override
            public SingletonTrait getLayeredCallbacksTrait() {
                return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<PriorKnownOffsets>() {
                    @Override
                    public LayeredPersistFlags doPersist(KeyValueWriter writer, PriorKnownOffsets singleton) {
                        writer.writeIntList("priorValues", Arrays.stream(singleton.priorValues).boxed().toList());
                        return LayeredPersistFlags.CREATE;
                    }

                    @Override
                    public Class<? extends SingletonLayeredCallbacks.LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                        return SingletonInstantiator.class;
                    }
                });
            }
        }

        static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<PriorKnownOffsets> {
            @Override
            public PriorKnownOffsets createFromLoader(KeyValueLoader loader) {
                int[] priorValues = loader.readIntList("priorValues").stream().mapToInt(e -> e).toArray();
                return new PriorKnownOffsets(priorValues);
            }
        }
    }
}
