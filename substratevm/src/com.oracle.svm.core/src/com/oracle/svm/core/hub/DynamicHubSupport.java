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

import java.util.EnumSet;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.BuildPhaseProvider.AfterHostedUniverse;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.UnsavedSingleton;

@AutomaticallyRegisteredImageSingleton
public final class DynamicHubSupport implements MultiLayeredImageSingleton, UnsavedSingleton {

    @UnknownPrimitiveField(availability = AfterHostedUniverse.class) private int maxTypeId;
    @UnknownObjectField(availability = AfterHostedUniverse.class) private byte[] referenceMapEncoding;

    @Platforms(Platform.HOSTED_ONLY.class)
    public static DynamicHubSupport currentLayer() {
        return LayeredImageSingletonSupport.singleton().lookup(DynamicHubSupport.class, false, true);
    }

    @AlwaysInline("Performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static DynamicHubSupport forLayer(int layerIndex) {
        return MultiLayeredImageSingleton.getForLayer(DynamicHubSupport.class, layerIndex);
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

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public NonmovableArray<Byte> getReferenceMapEncoding() {
        return NonmovableArrays.fromImageHeap(referenceMapEncoding);
    }

    @Override
    public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
        return LayeredImageSingletonBuilderFlags.ALL_ACCESS;
    }
}
