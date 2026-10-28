/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.hub;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform.NATIVE_ONLY;

import com.oracle.svm.guest.staging.GuestStagingDependencyBridge;
import com.oracle.svm.guest.staging.core.c.NonmovableArray;
import com.oracle.svm.guest.staging.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.guest.staging.core.heap.InstanceReferenceMapDecoder.InstanceReferenceMap;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.guest.staging.core.heap.UnknownPrimitiveField;
import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.LayeredImageSingletonSupport;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.MultiLayer;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = MultiLayer.class)
public final class DynamicHubSupport {

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class) private int maxTypeId;
    @UnknownPrimitiveField(availability = AfterHostedUniverse.class) private int maxInterfaceId;
    @UnknownObjectField(availability = AfterHostedUniverse.class) private byte[] referenceMapEncoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static DynamicHubSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(DynamicHubSupport.class, false, true);
    }

    /**
     * Returns the instance reference map for a hub's compressed reference-map offset.
     * <p>
     * This temporary offset-based API is used while {@code DynamicHub} remains core-owned for
     * GR-78110. When {@code DynamicHub} moves to guest staging, restore its parameter here and
     * remove the forwarding method from {@code DynamicHubUtils}.
     *
     * @param referenceMapCompressedOffset the compressed offset stored in the hub
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static InstanceReferenceMap getInstanceReferenceMap(int referenceMapCompressedOffset) {
        if (Platform.includedIn(NATIVE_ONLY.class)) {
            return InstanceReferenceMapDecoder.getReferenceMap(referenceMapCompressedOffset);
        } else {
            /* Remove once a heap base is supported, see GR-68847. */
            byte[] referenceMapEncoding = MultiLayeredImageSingleton.getForLayer(DynamicHubSupport.class, 0).getReferenceMapEncoding();
            NonmovableArray<Byte> nonmovableReferenceMapEncoding = GuestStagingDependencyBridge.singleton().nonmovableByteArrayFromImageHeap(referenceMapEncoding);
            return InstanceReferenceMapDecoder.getReferenceMap(nonmovableReferenceMapEncoding, referenceMapCompressedOffset);
        }
    }

    /**
     * Returns whether the instance reference map for a hub is empty.
     * <p>
     * This temporary offset-based API is used while {@code DynamicHub} remains core-owned for
     * GR-78110. When {@code DynamicHub} moves to guest staging, restore its parameter here and
     * remove the forwarding method from {@code DynamicHubUtils}.
     *
     * @param referenceMapCompressedOffset the compressed offset stored in the hub
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasEmptyReferenceMap(int referenceMapCompressedOffset) {
        InstanceReferenceMap referenceMap = DynamicHubSupport.getInstanceReferenceMap(referenceMapCompressedOffset);
        return InstanceReferenceMapDecoder.isEmpty(referenceMap);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHubSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMaxTypeIds(int maxTypeId, int maxInterfaceId) {
        this.maxTypeId = maxTypeId;
        this.maxInterfaceId = maxInterfaceId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxTypeId() {
        return maxTypeId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxInterfaceId() {
        return maxInterfaceId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReferenceMapEncoding(NonmovableArray<Byte> referenceMapEncoding) {
        this.referenceMapEncoding = GuestStagingDependencyBridge.singleton().getHostedArray(referenceMapEncoding);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public byte[] getReferenceMapEncoding() {
        return referenceMapEncoding;
    }
}
