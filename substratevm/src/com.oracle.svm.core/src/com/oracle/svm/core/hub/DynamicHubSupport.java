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
package com.oracle.svm.core.hub;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.InternalPlatform.NATIVE_ONLY;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder;
import com.oracle.svm.core.heap.InstanceReferenceMapDecoder.InstanceReferenceMap;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;

public final class DynamicHubSupport implements MultiLayeredImageSingleton, UnsavedSingleton {

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class) private int maxTypeId;
    @UnknownObjectField(availability = AfterHostedUniverse.class) private byte[] referenceMapEncoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static DynamicHubSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(DynamicHubSupport.class, false, true);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static InstanceReferenceMap getInstanceReferenceMap(DynamicHub hub) {
        if (Platform.includedIn(NATIVE_ONLY.class)) {
            return InstanceReferenceMapDecoder.getReferenceMap(hub.getReferenceMapCompressedOffset());
        } else {
            /* Remove once a heap base is supported, see GR-68847. */
            byte[] referenceMapEncoding = MultiLayeredImageSingleton.getForLayer(DynamicHubSupport.class, 0).referenceMapEncoding;
            return InstanceReferenceMapDecoder.getReferenceMap(NonmovableArrays.fromImageHeap(referenceMapEncoding), hub.getReferenceMapCompressedOffset());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasEmptyReferenceMap(DynamicHub hub) {
        InstanceReferenceMap referenceMap = DynamicHubSupport.getInstanceReferenceMap(hub);
        return InstanceReferenceMapDecoder.isEmpty(referenceMap);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public DynamicHubSupport() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setMaxTypeId(int maxTypeId) {
        this.maxTypeId = maxTypeId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public int getMaxTypeId() {
        return maxTypeId;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setReferenceMapEncoding(NonmovableArray<Byte> referenceMapEncoding) {
        this.referenceMapEncoding = NonmovableArrays.getHostedArray(referenceMapEncoding);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public byte[] getReferenceMapEncoding() {
        return referenceMapEncoding;
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
